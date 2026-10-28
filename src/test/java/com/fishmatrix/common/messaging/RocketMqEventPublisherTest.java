package com.fishmatrix.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqEventPublisherTest {

    private final DefaultMQProducer producer = mock(DefaultMQProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RocketMqEventPublisher publisher =
            new RocketMqEventPublisher(producer, objectMapper, "FM_TEST_", false);

    @Test
    void publishesTopicTagsKeysAndJsonBody() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(new SendResult());
        DomainEvent event = new DomainEvent("event-1", "MATCH_FINISHED", "tf-game",
                Instant.parse("2026-10-27T10:00:00Z"), "trace-1", "match-9",
                Map.of("winner", "u1"));

        publisher.publish(event);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(captor.capture());
        Message message = captor.getValue();
        assertThat(message.getTopic()).isEqualTo("FM_TEST_MATCH_FINISHED");
        assertThat(message.getTags()).isEqualTo("MATCH_FINISHED");
        assertThat(message.getKeys()).isEqualTo("match-9");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"eventId\":\"event-1\"")
                .contains("\"type\":\"MATCH_FINISHED\"")
                .contains("\"source\":\"tf-game\"")
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"winner\":\"u1\"");
    }

    @Test
    void fallsBackToEventIdWhenKeyIsBlank() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(new SendResult());
        DomainEvent event = DomainEvent.of("SYSTEM_PROBE", "tf-game", null, null, Map.of());

        publisher.publish(event);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(captor.capture());
        assertThat(captor.getValue().getKeys()).isEqualTo(event.eventId());
    }

    @Test
    void rejectsInvalidTypeWithoutSending() throws Exception {
        DomainEvent event = DomainEvent.of("bad type", "tf-game", null, "k", Map.of());

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad type");
        verify(producer, never()).send(any(Message.class));
    }

    @Test
    void wrapsSendFailureAsEventPublishException() throws Exception {
        when(producer.send(any(Message.class)))
                .thenThrow(new MQClientException("broker unavailable", null));
        DomainEvent event = DomainEvent.of("MATCH_FINISHED", "tf-game", null, "match-9", Map.of());

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining(event.eventId())
                .hasCauseInstanceOf(MQClientException.class);
    }

    @Test
    void ignoresNullEvent() throws Exception {
        publisher.publish(null);

        verify(producer, never()).send(any(Message.class));
    }
}
