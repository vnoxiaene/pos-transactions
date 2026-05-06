package com.pos.auth.autoconfigure;

import com.pos.auth.config.HmacSignatureProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot auto-configuration for Auth Commons.
 *
 * Automatically registers HMAC signature validation filter and properties
 * when auth-commons is added to the classpath.
 *
 * To disable, set: spring.autoconfigure.exclude=com.pos.auth.autoconfigure.AuthCommonsAutoConfiguration
 */
@AutoConfiguration
@EnableConfigurationProperties(HmacSignatureProperties.class)
public class AuthCommonsAutoConfiguration {

    // HmacSignatureFilter is auto-registered as @Component
    // HmacSignatureProperties is auto-configured via @EnableConfigurationProperties
}
