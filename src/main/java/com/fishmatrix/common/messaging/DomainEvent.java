package com.fishmatrix.common.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 跨服务领域事件契约。payload 只放可序列化的浅层结构；
 * 事件一旦发布就视为外部契约，type 和字段语义不能随意更改。
 */
public record DomainEvent(
        String eventId,
        String type,
        String source,
        Instant occurredAt,
        String traceId,
        String key,
        Map<String, Object> payload) {

    public DomainEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static DomainEvent of(String type, String source, String traceId, String key,
            Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID().toString(), type, source, Instant.now(),
                traceId, key, payload);
    }
}
