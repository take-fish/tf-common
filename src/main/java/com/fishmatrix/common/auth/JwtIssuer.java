package com.fishmatrix.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 签发 JWT 访问令牌（EdDSA / Ed25519）。
 *
 * <p>只有用户中台持有私钥；各业务服务和网关只用 {@link JwtVerifier} 的公钥验签。</p>
 */
public class JwtIssuer {

    private final PrivateKey privateKey;
    private final String keyId;
    private final String issuer;
    private final Duration accessTtl;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtIssuer(FishAuthProperties properties) {
        FishAuthProperties.Jwt jwt = properties.getJwt();
        this.keyId = jwt.getKeyId() == null || jwt.getKeyId().isBlank()
                ? "default" : jwt.getKeyId().trim();
        this.issuer = jwt.getIssuer() == null || jwt.getIssuer().isBlank()
                ? "fishmatrix" : jwt.getIssuer().trim();
        this.accessTtl = jwt.getAccessTtl();
        this.privateKey = parsePrivateKey(jwt.getPrivateKey());
    }

    /** 是否配置了私钥。未配置时用户中台不应签发 JWT。 */
    public boolean isConfigured() {
        return privateKey != null;
    }

    public String keyId() {
        return keyId;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String issue(CurrentUser user) {
        return issue(user, accessTtl);
    }

    public String issue(CurrentUser user, Duration ttl) {
        if (!isConfigured()) {
            throw ApiException.unavailable("AUTH_CONFIG_MISSING", "JWT 私钥未配置");
        }
        if (user == null || user.id() <= 0) {
            throw ApiException.unauthorized("请先登录");
        }
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + Math.max(60, ttl.toSeconds());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "EdDSA");
        header.put("typ", "JWT");
        header.put("kid", keyId);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", user.id());
        claims.put("usr", user.username());
        claims.put("nick", user.nickname());
        claims.put("role", user.role());
        claims.put("roles", new ArrayList<>(user.roles()));
        claims.put("perms", new ArrayList<>(user.permissions()));
        claims.put("iat", now);
        claims.put("exp", expiresAt);
        claims.put("jti", UUID.randomUUID().toString());

        try {
            String signingInput = encode(header) + "." + encode(claims);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception exception) {
            throw ApiException.internal("签发访问令牌失败", exception);
        }
    }

    /** 把一批权限码组装成可签发身份的当前用户。 */
    public static CurrentUser currentUser(long id, String username, String nickname, String role,
            List<String> roles, List<String> permissions) {
        return new CurrentUser(id, username, nickname, role, roles,
                permissions == null ? java.util.Set.of() : java.util.Set.copyOf(permissions));
    }

    private String encode(Map<String, Object> value) {
        try {
            return base64Url(mapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw ApiException.internal("序列化令牌失败", exception);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey parsePrivateKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s", "")
                .replace("-----BEGINPRIVATEKEY-----", "")
                .replace("-----ENDPRIVATEKEY-----", "")
                .replace('-', '+')
                .replace('_', '/');
        int padding = (4 - normalized.length() % 4) % 4;
        normalized = normalized + "=".repeat(padding);
        try {
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("fm.auth.jwt.private-key 解析失败，应为 PKCS#8 DER 的 base64",
                    exception);
        }
    }
}
