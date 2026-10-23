package com.fishmatrix.common.auth;

import com.fishmatrix.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 用户中台 introspection 客户端。只缓存成功的身份快照，失败一律 fail-closed。
 * 缓存键为 token 的 SHA-256，内存中不保留明文 token。
 */
public class AuthClient {

    public record Identity(
            long id,
            String username,
            String nickname,
            String role,
            List<String> roles,
            List<String> permissions) {
    }

    private final RestClient client;
    private final Duration cacheTtl;
    private final int cacheMaxEntries;
    private final Map<String, CacheEntry> cache;

    public AuthClient(RestClient.Builder builder, String userServiceUrl,
            Duration cacheTtl, int cacheMaxEntries) {
        String baseUrl = userServiceUrl == null || userServiceUrl.isBlank()
                ? "http://127.0.0.1:8101" : userServiceUrl.trim();
        this.client = builder.clone()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .build();
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? Duration.ofSeconds(30) : cacheTtl;
        this.cacheMaxEntries = Math.max(1, cacheMaxEntries);
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > AuthClient.this.cacheMaxEntries;
            }
        });
    }

    public CurrentUser introspect(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("请先登录");
        }
        String key = sha256(token);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.valid()) {
            return cached.user();
        }
        if (cached != null) {
            cache.remove(key);
        }

        Identity identity;
        try {
            identity = client.get()
                    .uri("/api/v1/user/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Identity.class);
        } catch (RestClientResponseException exception) {
            cache.remove(key);
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                throw ApiException.unauthorized("登录已失效，请重新登录");
            }
            throw unavailable();
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RuntimeException exception) {
            throw unavailable();
        }

        if (identity == null || identity.id() <= 0) {
            throw unavailable();
        }
        CurrentUser user = new CurrentUser(identity.id(), identity.username(), identity.nickname(),
                identity.role(), identity.roles(),
                identity.permissions() == null ? Set.of() : new java.util.HashSet<>(identity.permissions()));
        cache.put(key, new CacheEntry(user, System.nanoTime() + cacheTtl.toNanos()));
        return user;
    }

    public void evict(String token) {
        if (token != null && !token.isBlank()) cache.remove(sha256(token));
    }

    public Optional<CurrentUser> cached(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        CacheEntry entry = cache.get(sha256(token));
        return entry != null && entry.valid() ? Optional.of(entry.user()) : Optional.empty();
    }

    private static ApiException unavailable() {
        return ApiException.unavailable("AUTH_UNAVAILABLE", "登录校验服务暂不可用，请稍后重试");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record CacheEntry(CurrentUser user, long expiresAtNanos) {
        boolean valid() {
            return System.nanoTime() < expiresAtNanos;
        }
    }
}
