package com.fishmatrix.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认发布器：只记录事件摘要，不建立任何网络连接。
 * 生产接入消息队列后由真实适配器覆盖。
 */
public class NoopEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopEventPublisher.class);

    private final boolean logEvents;

    public NoopEventPublisher(boolean logEvents) {
        this.logEvents = logEvents;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) return;
        if (logEvents) {
            log.info("事件发布口未启用，忽略事件 type={} key={} eventId={}",
                    event.type(), event.key(), event.eventId());
        }
    }
}
