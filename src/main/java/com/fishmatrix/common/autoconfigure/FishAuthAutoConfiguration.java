package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.auth.AuthClient;
import com.fishmatrix.common.auth.AuthInterceptor;
import com.fishmatrix.common.auth.IdentitySigner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一鉴权入口。默认开启；用户中台通过 {@code fm.auth.enabled=false} 关闭。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "fm.auth", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(FishAuthProperties.class)
public class FishAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdentitySigner fishIdentitySigner(FishAuthProperties properties) {
        return new IdentitySigner(properties.getIdentitySecret(),
                properties.getClockSkewSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthClient fishAuthClient(RestClient.Builder builder, FishAuthProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return new AuthClient(builder.clone().requestFactory(factory),
                properties.getUserServiceUrl(), properties.getCacheTtl(),
                properties.getCacheMaxEntries());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor fishAuthInterceptor(IdentitySigner signer, AuthClient authClient,
            FishAuthProperties properties) {
        return new AuthInterceptor(signer, authClient, properties);
    }

    @Bean
    public WebMvcConfigurer fishAuthWebMvcConfigurer(AuthInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 必须先于业务服务的身份适配器执行，保证 CurrentUser 已就绪。
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/api/**")
                        .order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
            }
        };
    }
}
