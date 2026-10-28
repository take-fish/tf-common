package com.fishmatrix.common.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息与 RPC 骨架开关。默认全关，不依赖、不连接任何新增中间件。
 */
@ConfigurationProperties(prefix = "fm.messaging")
public class FishMessagingProperties {

    private boolean enabled = false;

    /** 是否把被忽略的事件写进日志，默认关闭避免刷屏。 */
    private boolean logEvents = false;

    private String nameServer = "";

    private String topicPrefix = "FM_";

    /** 生产者组；为空时按 fm-{spring.application.name} 推导。 */
    private String producerGroup = "";

    private int sendTimeoutMs = 3000;

    /** 启用后是否在启动完成时发布一条 SYSTEM_PROBE 诊断事件，默认关闭。 */
    private boolean diagnosticsOnStartup = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogEvents() {
        return logEvents;
    }

    public void setLogEvents(boolean logEvents) {
        this.logEvents = logEvents;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(int sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public boolean isDiagnosticsOnStartup() {
        return diagnosticsOnStartup;
    }

    public void setDiagnosticsOnStartup(boolean diagnosticsOnStartup) {
        this.diagnosticsOnStartup = diagnosticsOnStartup;
    }
}
