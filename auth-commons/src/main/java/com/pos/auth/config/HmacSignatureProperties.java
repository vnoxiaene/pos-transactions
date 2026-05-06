package com.pos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for HMAC signature validation.
 *
 * Example application.yml:
 * <code>
 * security:
 *   hmac:
 *     secret: my-super-secret-key-change-in-production
 *     enabled: true
 * </code>
 */
@Component
@ConfigurationProperties(prefix = "security.hmac")
public class HmacSignatureProperties {

    /** HMAC secret key for signature calculation. Should be set via environment variable in production. */
    private String secret = "default-secret-change-in-production";

    /** Enable/disable HMAC signature validation globally. */
    private boolean enabled = true;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
