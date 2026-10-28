package com.fishmatrix.common.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认发布器：只记录事件摘要，不建立任何网络连接。
 * 生产接入消息队列后由真实适配器覆盖。
 */
@Slf4j
@RequiredArgsConstructor
public class NoopEventPublisher implements EventPublisher {

    private final boolean logEvents;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) return;
        if (logEvents) {
            log.info("事件发布口未启用，忽略事件 type={} key={} eventId={}",
                    event.type(), event.key(), event.eventId());
        }
    }
}
