package com.fishmatrix.common.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地校验 JWT 访问令牌（EdDSA / Ed25519）。
 *
 * <p>只做验签和声明校验，不访问用户中台，也不依赖网关。公钥由配置（Nacos）下发，
 * 支持 {@code kid} 多把公钥并存，便于轮换：先发新公钥，再换签发方私钥。</p>
 */
public class JwtVerifier {

    /** SubjectPublicKeyInfo 中 Ed25519 的固定前缀，后接 32 字节裸公钥。 */
    private static final byte[] ED25519_SPKI_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private volatile KeySet current;
    private final FishAuthProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtVerifier(FishAuthProperties properties) {
        this.properties = properties;
        this.current = buildKeys();
    }

    /** 当前生效的公钥集合与配置指纹。 */
    private record KeySet(Map<String, PublicKey> keys, String keyId, String issuer, String signature) {
    }

    /**
     * 每次验签前对配置做一次廉价比对：Nacos 改了公钥（轮换）后无需重启即可生效。
     * 新配置解析失败时保留上一份可用公钥，避免一个手抖的配置把所有人挡在门外。
     */
    private KeySet keys() {
        KeySet snapshot = current;
        if (snapshot.signature.equals(signature())) {
            return snapshot;
        }
        try {
            KeySet rebuilt = buildKeys();
            current = rebuilt;
            return rebuilt;
        } catch (RuntimeException exception) {
            // 配置写错时保留上一份可用公钥
            return snapshot;
        }
    }

    private String signature() {
        FishAuthProperties.Jwt jwt = properties.getJwt();
        return String.valueOf(jwt.getKeyId()) + '|' + jwt.getIssuer() + '|'
                + String.valueOf(jwt.getPublicKey()) + '|' + String.valueOf(jwt.getKeys());
    }

    private KeySet buildKeys() {
        FishAuthProperties.Jwt jwt = properties.getJwt();
        String keyId = jwt.getKeyId() == null || jwt.getKeyId().isBlank()
                ? "default" : jwt.getKeyId().trim();
        String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().trim();

        Map<String, PublicKey> parsed = new LinkedHashMap<>();
        if (jwt.getPublicKey() != null && !jwt.getPublicKey().isBlank()) {
            parsed.put(keyId, parseKey(keyId, jwt.getPublicKey()));
        }
        if (jwt.getKeys() != null) {
            jwt.getKeys().forEach((kid, value) -> {
                if (kid != null && !kid.isBlank() && value != null && !value.isBlank()) {
                    parsed.put(kid.trim(), parseKey(kid, value));
                }
            });
        }
        return new KeySet(
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parsed)),
                keyId, issuer, signature());
    }

    /** 是否配置了至少一把公钥。未配置时不应走本地验签。 */
    public boolean isConfigured() {
        return !keys().keys().isEmpty();
    }

    /** 已配置的 kid 列表，用于启动自检日志。 */
    public List<String> keyIds() {
        return List.copyOf(keys().keys().keySet());
    }

    /**
     * 校验令牌并返回身份。篡改、算法不符、未知 kid、过期都直接拒绝，不降级为匿名。
     */
    public CurrentUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("请先登录");
        }
        if (!isConfigured()) {
            throw ApiException.unavailable("AUTH_CONFIG_MISSING", "JWT 公钥未配置");
        }
        KeySet snapshot = keys();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidToken();
        }

        JsonNode header = readJson(parts[0]);
        String alg = header.path("alg").asText("");
        // 只接受非对称签名算法，显式拒绝 none / HS256 等降级手段。
        if (!"EdDSA".equals(alg)) {
            throw invalidToken();
        }
        String kid = header.path("kid").asText("");
        if (kid.isBlank()) {
            kid = snapshot.keyId();
        }
        PublicKey key = snapshot.keys().get(kid);
        if (key == null) {
            throw invalidToken();
        }
        if (!verifySignature(key, parts)) {
            throw invalidToken();
        }

        JsonNode claims = readJson(parts[1]);
        long now = Instant.now().getEpochSecond();
        long clockSkewSeconds = Math.max(0, properties.getClockSkewSeconds());

        long exp = claims.path("exp").asLong(0);
        if (exp <= 0) {
            throw invalidToken();
        }
        if (exp + clockSkewSeconds < now) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "登录状态已过期，请重新登录");
        }
        long nbf = claims.path("nbf").asLong(0);
        if (nbf > 0 && nbf - clockSkewSeconds > now) {
            throw invalidToken();
        }
        if (!snapshot.issuer().isEmpty() && !snapshot.issuer().equals(claims.path("iss").asText(""))) {
            throw invalidToken();
        }

        long userId = parseUserId(claims.path("sub"));
        if (userId <= 0) {
            throw invalidToken();
        }
        return new CurrentUser(
                userId,
                text(claims, "usr"),
                text(claims, "nick"),
                text(claims, "role"),
                values(claims.get("roles")),
                Set.copyOf(values(claims.get("perms"))));
    }

    /** 粗判是否像 JWT：恰好两段点分隔。用于决定走本地验签还是回退 introspection。 */
    public static boolean looksLikeJwt(String token) {
        if (token == null) return false;
        int dots = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.' && ++dots > 2) return false;
        }
        return dots == 2;
    }

    private boolean verifySignature(PublicKey key, String[] parts) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return signature.verify(base64UrlDecode(parts[2]));
        } catch (RuntimeException exception) {
            throw invalidToken();
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private JsonNode readJson(String segment) {
        try {
            return mapper.readTree(base64UrlDecode(segment));
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private static long parseUserId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return 0L;
        if (node.isNumber()) return node.asLong(0);
        try {
            return Long.parseLong(node.asText("").trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String text(JsonNode claims, String field) {
        JsonNode node = claims.get(field);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private static List<String> values(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        Set<String> items = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isEmpty()) items.add(value);
            });
        } else {
            String raw = node.asText("").trim();
            if (!raw.isEmpty()) {
                for (String item : raw.split("[,\\s]+")) {
                    if (!item.isBlank()) items.add(item.trim());
                }
            }
        }
        return new ArrayList<>(items);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static PublicKey parseKey(String kid, String value) {
        String normalized = value.replaceAll("\\s", "")
                .replace('-', '+')
                .replace('_', '/');
        int padding = (4 - normalized.length() % 4) % 4;
        normalized = normalized + "=".repeat(padding);
        byte[] raw = Base64.getDecoder().decode(normalized);
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "fm.auth.jwt 公钥 " + kid + " 不是 32 字节 Ed25519 裸公钥");
        }
        byte[] encoded = new byte[ED25519_SPKI_PREFIX.length + raw.length];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, encoded, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(raw, 0, encoded, ED25519_SPKI_PREFIX.length, raw.length);
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("fm.auth.jwt 公钥 " + kid + " 解析失败", exception);
        }
    }

    private static ApiException invalidToken() {
        return ApiException.unauthorized("INVALID_TOKEN", "登录状态无效，请重新登录");
    }
}
