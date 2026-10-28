package com.junzhou.config;

import com.junzhou.properties.AliOssProperties;
import com.junzhou.utils.AliOssUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置。
 */
@Configuration
public class OssConfiguration {

    @Bean
    public AliOssUtil aliOssUtil(AliOssProperties properties) {
        return new AliOssUtil(properties.getEndpoint(), properties.getRegion(), properties.getAccessKeyId(),
                properties.getAccessKeySecret(), properties.getBucketName());
    }
}
