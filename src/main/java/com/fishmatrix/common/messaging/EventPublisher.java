package com.fishmatrix.common.messaging;

/**
 * 领域事件发布口子。当前默认实现不连接任何中间件；
 * 打开 {@code fm.messaging.enabled=true} 后可替换为 RocketMQ 适配器。
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}
