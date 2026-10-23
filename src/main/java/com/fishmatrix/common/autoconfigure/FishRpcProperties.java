package com.fishmatrix.common.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间 RPC 骨架配置。第一阶段只保留开关与命名空间，
 * 不引入 Dubbo 依赖、不建立连接；启用时由独立适配模块读取这些配置。
 */
@ConfigurationProperties(prefix = "fm.rpc")
public class FishRpcProperties {

    private boolean enabled = false;

    private String nacosNamespace = "fm-prod";

    private String group = "FM";

    private int timeoutMs = 3000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNacosNamespace() {
        return nacosNamespace;
    }

    public void setNacosNamespace(String nacosNamespace) {
        this.nacosNamespace = nacosNamespace;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
