package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.error.GlobalExceptionHandler;
import com.fishmatrix.common.trace.RestClientTraceInterceptor;
import com.fishmatrix.common.trace.TraceContextFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * 引入 tf-common 后自动生效，业务服务无需再写 @ComponentScan 或重复的异常处理器。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FishCommonProperties.class)
public class FishCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler fishGlobalExceptionHandler(FishCommonProperties properties) {
        return new GlobalExceptionHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "fishTraceContextFilter")
    public FilterRegistrationBean<TraceContextFilter> fishTraceContextFilter(
            FishCommonProperties properties) {
        FilterRegistrationBean<TraceContextFilter> registration =
                new FilterRegistrationBean<>(new TraceContextFilter(properties));
        registration.setName("fishTraceContextFilter");
        registration.setOrder(TraceContextFilter.ORDER);
        return registration;
    }

    @Bean
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnMissingBean(name = "fishTraceRestClientCustomizer")
    public RestClientCustomizer fishTraceRestClientCustomizer(ObjectProvider<Tracer> tracerProvider) {
        return builder -> builder.requestInterceptor(new RestClientTraceInterceptor(tracerProvider.getIfAvailable()));
    }
}
