package com.fishmatrix.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmatrix.common.messaging.DomainEvent;
import com.fishmatrix.common.messaging.EventPublisher;
import com.fishmatrix.common.messaging.NoopEventPublisher;
import com.fishmatrix.common.messaging.RocketMqEventPublisher;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 事件发布：默认使用无副作用的 Noop 实现；只有显式打开
 * {@code fm.messaging.enabled=true} 且配置了 name-server 时才创建 RocketMQ 生产者。
 */
@AutoConfiguration
@EnableConfigurationProperties({FishMessagingProperties.class, FishRpcProperties.class})
public class FishMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fm.messaging", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    public EventPublisher fishNoopEventPublisher(FishMessagingProperties properties) {
        return new NoopEventPublisher(properties.isLogEvents());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "fm.messaging", name = "enabled", havingValue = "true")
    static class RocketMqConfiguration {

        private static final Logger log = LoggerFactory.getLogger(RocketMqConfiguration.class);

        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean
        public DefaultMQProducer fishMqProducer(FishMessagingProperties properties,
                Environment environment) {
            String nameServer = properties.getNameServer();
            if (!StringUtils.hasText(nameServer)) {
                throw new IllegalStateException(
                        "fm.messaging.enabled=true 但 fm.messaging.name-server 未配置");
            }
            String producerGroup = properties.getProducerGroup();
            if (!StringUtils.hasText(producerGroup)) {
                producerGroup = "fm-" + environment.getProperty("spring.application.name", "app");
            }
            DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
            producer.setNamesrvAddr(nameServer.trim());
            producer.setSendMsgTimeout(properties.getSendTimeoutMs());
            try {
                producer.start();
            } catch (MQClientException e) {
                throw new IllegalStateException("RocketMQ 生产者启动失败 group=" + producerGroup
                        + " name-server=" + nameServer, e);
            }
            log.info("RocketMQ 生产者已启动 group={} name-server={}", producerGroup, nameServer);
            return producer;
        }

        @Bean
        @ConditionalOnMissingBean
        public EventPublisher fishEventPublisher(FishMessagingProperties properties,
                DefaultMQProducer producer, ObjectMapper objectMapper) {
            return new RocketMqEventPublisher(producer, objectMapper,
                    properties.getTopicPrefix(), properties.isLogEvents());
        }

        @Bean
        @ConditionalOnProperty(prefix = "fm.messaging", name = "diagnostics-on-startup",
                havingValue = "true")
        public ApplicationRunner fishMessagingDiagnosticsRunner(EventPublisher publisher,
                Environment environment) {
            return args -> {
                String source = environment.getProperty("spring.application.name", "fishmatrix");
                DomainEvent event = DomainEvent.of("SYSTEM_PROBE", source, null, null,
                        Map.of("version", environment.getProperty("APP_VERSION", "unknown"),
                                "appEnv", environment.getProperty("APP_ENV", "unknown")));
                publisher.publish(event);
                log.info("MQ 启动探针已发布 type={} eventId={}", event.type(), event.eventId());
            };
        }
    }
}
