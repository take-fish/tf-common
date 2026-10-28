package com.fishmatrix.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.util.regex.Pattern;

/**
 * RocketMQ 事件发布器。topic 由 {@code topicPrefix + type} 组成，
 * 生产与测试共享 broker 时依靠前缀（FM_ / FM_TEST_）隔离；
 * tag 为事件类型，keys 为业务聚合 ID，消息体是 DomainEvent 的 JSON。
 */
@Slf4j
public class RocketMqEventPublisher implements EventPublisher {

    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final boolean logEvents;

    public RocketMqEventPublisher(DefaultMQProducer producer, ObjectMapper objectMapper,
            String topicPrefix, boolean logEvents) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topicPrefix = topicPrefix == null ? "" : topicPrefix;
        this.logEvents = logEvents;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        String type = event.type();
        if (type == null || !TYPE_PATTERN.matcher(type).matches()) {
            throw new IllegalArgumentException("非法事件类型: " + type);
        }
        String topic = topicPrefix + type;
        String keys = event.key() == null || event.key().isBlank() ? event.eventId() : event.key();
        try {
            byte[] body = objectMapper.writeValueAsBytes(event);
            Message message = new Message(topic, type, keys, body);
            long started = System.nanoTime();
            SendResult result = producer.send(message);
            if (logEvents) {
                log.info("事件已发布 topic={} type={} key={} eventId={} traceId={} msgId={} cost={}ms",
                        topic, type, event.key(), event.eventId(), event.traceId(), result.getMsgId(),
                        (System.nanoTime() - started) / 1_000_000);
            }
        } catch (JsonProcessingException e) {
            throw new EventPublishException("事件序列化失败 eventId=" + event.eventId(), e);
        } catch (Exception e) {
            log.error("事件发布失败 topic={} type={} key={} eventId={} traceId={}",
                    topic, type, event.key(), event.eventId(), event.traceId(), e);
            throw new EventPublishException("事件发布失败 eventId=" + event.eventId(), e);
        }
    }
}
