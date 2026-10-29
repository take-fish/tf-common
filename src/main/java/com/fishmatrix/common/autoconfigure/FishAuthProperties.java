package com.fishmatrix.common.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fm.auth")
public class FishAuthProperties {

    /** 是否启用统一登录校验。用户中台自身必须关闭，避免自调用。 */
    private boolean enabled = true;

    /** 用户中台地址，服务内网直连，不经过网关。 */
    private String userServiceUrl = "http://127.0.0.1:8101";

    /** 网关签发身份头使用的 HMAC 密钥，必须与网关一致。 */
    private String identitySecret = "";

    /** 允许无签名身份头时用 Bearer 直接 introspection，便于本地直连与滚动升级。 */
    private boolean allowBearerFallback = true;

    /** 身份签名允许的时钟偏差。 */
    private long clockSkewSeconds = 30;

    /** 成功 introspection 的内存缓存时间。 */
    private Duration cacheTtl = Duration.ofSeconds(30);

    private int cacheMaxEntries = 10_000;

    /** 用户中台调用超时。 */
    private Duration connectTimeout = Duration.ofMillis(800);

    private Duration readTimeout = Duration.ofMillis(2000);

    /**
     * 无需登录即可访问的 API 路径（Ant 风格）。各服务在配置中追加自己的健康检查、
     * 登录和公开元数据路径；默认值只覆盖通用端点。
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/health",
            "/healthz",
            "/readyz",
            "/actuator/**",
            "/error",
            "/api/v1/*/health",
            "/api/v1/*/meta"));

    /** JWT 访问令牌的本地验签配置。密钥由 Nacos 下发，服务不持有私钥。 */
    private Jwt jwt = new Jwt();

    public static class Jwt {

        /** 是否启用本地 JWT 验签。关闭后只能走身份头或 introspection 回退。 */
        private boolean enabled = true;

        /** 期望的签发方；为空表示不校验 iss。 */
        private String issuer = "fishmatrix";

        /** 默认公钥对应的 kid。 */
        private String keyId = "default";

        /** 默认公钥：32 字节 Ed25519 裸公钥，base64url 或 base64。 */
        private String publicKey = "";

        /** 轮换用：kid -> 公钥。可同时配置新旧两把，先发新钥再换签发方。 */
        private Map<String, String> keys = new LinkedHashMap<>();

        /** 签发方私钥（PKCS#8 DER 的 base64）。只有用户中台需要配置，业务服务不持有。 */
        private String privateKey = "";

        /** 访问令牌有效期。撤销窗口等于这个值。 */
        private Duration accessTtl = Duration.ofMinutes(60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public Map<String, String> getKeys() {
            return keys;
        }

        public void setKeys(Map<String, String> keys) {
            this.keys = keys == null ? new LinkedHashMap<>() : keys;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public Duration getAccessTtl() {
            return accessTtl;
        }

        public void setAccessTtl(Duration accessTtl) {
            this.accessTtl = accessTtl == null ? Duration.ofMinutes(60) : accessTtl;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserServiceUrl() {
        return userServiceUrl;
    }

    public void setUserServiceUrl(String userServiceUrl) {
        this.userServiceUrl = userServiceUrl;
    }

    public String getIdentitySecret() {
        return identitySecret;
    }

    public void setIdentitySecret(String identitySecret) {
        this.identitySecret = identitySecret;
    }

    public boolean isAllowBearerFallback() {
        return allowBearerFallback;
    }

    public void setAllowBearerFallback(boolean allowBearerFallback) {
        this.allowBearerFallback = allowBearerFallback;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt == null ? new Jwt() : jwt;
    }
}
