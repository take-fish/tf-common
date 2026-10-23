package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.messaging.EventPublisher;
import com.fishmatrix.common.messaging.NoopEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 事件发布的第一阶段骨架：始终提供无副作用实现，待消息队列正式接入后替换。
 */
@AutoConfiguration
@EnableConfigurationProperties({FishMessagingProperties.class, FishRpcProperties.class})
public class FishMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher fishEventPublisher(FishMessagingProperties properties) {
        return new NoopEventPublisher(properties.isLogEvents());
    }
}
