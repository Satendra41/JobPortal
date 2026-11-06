package com.jobportal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Validates production-only password-reset settings without logging secret values. */
@Component
@Profile("prod")
public class ProductionPasswordResetConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ProductionPasswordResetConfiguration.class);

    public ProductionPasswordResetConfiguration(
            @Value("${app.password-reset.rate-limit-hash-key:}") String rateLimitHashKey) {
        if (rateLimitHashKey == null || rateLimitHashKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("PASSWORD_RESET_RATE_LIMIT_HASH_KEY must contain at least 32 bytes.");
        }
        logger.info("Password reset rate-limit key is configured (value hidden).");
    }
}
