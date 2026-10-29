package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.auth.AuthClient;
import com.fishmatrix.common.auth.AuthInterceptor;
import com.fishmatrix.common.auth.IdentitySigner;
import com.fishmatrix.common.auth.JwtVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FishAuthAutoConfiguration.class);

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

    /**
     * 本地验签器：公钥由 Nacos 下发，服务不持有私钥，也不需要调用用户中台。
     * 未配置公钥时仍会创建实例，由 {@code isConfigured()} 决定是否走本地验签。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtVerifier fishJwtVerifier(FishAuthProperties properties) {
        JwtVerifier verifier = new JwtVerifier(properties);
        if (verifier.isConfigured()) {
            log.info("JWT 本地验签已启用，kid={}", verifier.keyIds());
        } else {
            log.warn("JWT 本地验签未配置公钥（fm.auth.jwt.public-key），将回退到 introspection");
        }
        return verifier;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor fishAuthInterceptor(IdentitySigner signer, JwtVerifier jwtVerifier,
            AuthClient authClient, FishAuthProperties properties) {
        return new AuthInterceptor(signer, jwtVerifier, authClient, properties);
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
