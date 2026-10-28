package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.messaging.EventPublisher;
import com.fishmatrix.common.messaging.NoopEventPublisher;
import com.fishmatrix.common.messaging.RocketMqEventPublisher;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FishMessagingAutoConfigurationTest {

    private final DefaultMQProducer producer = mock(DefaultMQProducer.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, FishMessagingAutoConfiguration.class));

    @Test
    void usesNoopPublisherByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context.getBean(EventPublisher.class)).isInstanceOf(NoopEventPublisher.class);
            assertThat(context).doesNotHaveBean(DefaultMQProducer.class);
        });
    }

    @Test
    void keepsNoopWhenExplicitlyDisabled() {
        runner.withPropertyValues("fm.messaging.enabled=false",
                        "fm.messaging.name-server=127.0.0.1:9876")
                .run(context -> {
                    assertThat(context.getBean(EventPublisher.class))
                            .isInstanceOf(NoopEventPublisher.class);
                    assertThat(context).doesNotHaveBean(DefaultMQProducer.class);
                });
    }

    @Test
    void failsFastWhenEnabledWithoutNameServer() {
        runner.withPropertyValues("fm.messaging.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("fm.messaging.enabled=true 但 fm.messaging.name-server 未配置");
                });
    }

    @Test
    void createsRocketMqPublisherWhenEnabled() {
        runner.withPropertyValues("fm.messaging.enabled=true",
                        "fm.messaging.name-server=127.0.0.1:9876",
                        "fm.messaging.topic-prefix=FM_TEST_")
                .withBean(DefaultMQProducer.class, () -> producer)
                .run(context -> {
                    assertThat(context).hasSingleBean(EventPublisher.class);
                    assertThat(context.getBean(EventPublisher.class))
                            .isInstanceOf(RocketMqEventPublisher.class);
                });
    }

    @Test
    void publishesStartupProbeWhenDiagnosticsEnabled() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(new SendResult());
        runner.withPropertyValues("fm.messaging.enabled=true",
                        "fm.messaging.name-server=127.0.0.1:9876",
                        "fm.messaging.diagnostics-on-startup=true",
                        "spring.application.name=tf-game")
                .withBean(DefaultMQProducer.class, () -> producer)
                .run(context -> {
                    ApplicationRunner diagnostics = context.getBean(
                            "fishMessagingDiagnosticsRunner", ApplicationRunner.class);
                    diagnostics.run(null);

                    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
                    verify(producer).send(captor.capture());
                    Message message = captor.getValue();
                    assertThat(message.getTopic()).isEqualTo("FM_SYSTEM_PROBE");
                    assertThat(message.getTags()).isEqualTo("SYSTEM_PROBE");
                    assertThat(new String(message.getBody())).contains("\"source\":\"tf-game\"");
                });
    }
}
