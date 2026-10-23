package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.storage.FileStorage;
import com.fishmatrix.common.storage.LocalFileStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 只有显式设置 {@code fm.storage.enabled=true} 的服务才会创建存储 Bean。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fm.storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FishStorageProperties.class)
public class FishStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FileStorage fishFileStorage(FishStorageProperties properties) {
        return new LocalFileStorage(properties.getBaseDir(), properties.getMaxFileBytes());
    }
}
