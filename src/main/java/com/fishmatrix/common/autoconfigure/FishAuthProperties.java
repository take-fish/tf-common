package com.fishmatrix.common.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
}
