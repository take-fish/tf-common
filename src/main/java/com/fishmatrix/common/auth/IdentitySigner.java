package com.fishmatrix.common.auth;

import com.fishmatrix.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网关身份头的签名校验。规范串固定为：
 * {@code uid|b64url(username)|b64url(nickname)|b64url(role)|roles|permissions|exp}。
 * 文本字段使用 Base64URL 无填充编码，避免昵称中的分隔符造成歧义。
 */
public class IdentitySigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long clockSkewSeconds;

    public IdentitySigner(String secret, long clockSkewSeconds) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.clockSkewSeconds = Math.max(0, clockSkewSeconds);
    }

    public boolean isConfigured() {
        return secret.length > 0;
    }

    public String canonical(long userId, String username, String nickname, String role,
            List<String> roles, List<String> permissions, long expiresAt) {
        return userId + "|" + encode(username) + "|" + encode(nickname) + "|" + encode(role)
                + "|" + String.join(",", roles == null ? List.of() : roles)
                + "|" + String.join(",", permissions == null ? List.of() : permissions)
                + "|" + expiresAt;
    }

    public String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return B64.encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("身份签名失败", exception);
        }
    }

    /**
     * 读取并校验身份头。仅当所有身份头存在时返回用户；
     * 任一字段缺失、签名错误或已过期都直接拒绝，绝不降级为匿名。
     */
    public CurrentUser verify(HttpServletRequest request) {
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String username = request.getHeader(IdentityHeaders.USERNAME);
        String nickname = request.getHeader(IdentityHeaders.NICKNAME);
        String role = request.getHeader(IdentityHeaders.ROLE);
        String roles = request.getHeader(IdentityHeaders.ROLES);
        String permissions = request.getHeader(IdentityHeaders.PERMISSIONS);
        String expiresAt = request.getHeader(IdentityHeaders.EXPIRES_AT);
        String signature = request.getHeader(IdentityHeaders.SIGNATURE);

        if (userId == null) return null;
        if (username == null || nickname == null || role == null || roles == null
                || permissions == null || expiresAt == null || signature == null) {
            throw invalidIdentity();
        }
        if (!isConfigured()) {
            throw ApiException.unavailable("AUTH_CONFIG_MISSING", "身份校验密钥未配置");
        }

        long id;
        long exp;
        try {
            id = Long.parseLong(userId);
            exp = Long.parseLong(expiresAt);
        } catch (NumberFormatException exception) {
            throw invalidIdentity();
        }
        if (exp + clockSkewSeconds < Instant.now().getEpochSecond()) {
            throw ApiException.unauthorized("IDENTITY_EXPIRED", "登录状态已过期，请重新登录");
        }
        if (exp > Instant.now().getEpochSecond() + IdentityHeaders.MAX_TTL_SECONDS
                + clockSkewSeconds) {
            throw invalidIdentity();
        }

        List<String> roleList = split(roles);
        List<String> permissionList = split(permissions);
        String canonical = canonical(id, decode(username), decode(nickname), decode(role),
                roleList, permissionList, exp);
        String expected = sign(canonical);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw invalidIdentity();
        }

        return new CurrentUser(id, decode(username), decode(nickname), decode(role), roleList,
                Set.copyOf(permissionList));
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        Set<String> items = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) items.add(trimmed);
        }
        return new ArrayList<>(items);
    }

    private static String encode(String value) {
        return B64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(B64D.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidIdentity();
        }
    }

    private static ApiException invalidIdentity() {
        return ApiException.unauthorized("INVALID_IDENTITY", "身份信息无效，请重新登录");
    }
}
