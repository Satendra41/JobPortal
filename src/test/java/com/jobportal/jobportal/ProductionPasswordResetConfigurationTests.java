package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.services.PasswordResetOutboxCipher;

class ProductionPasswordResetConfigurationTests {
    @Test
    void productionCipherRejectsMissingKeyWithVariableName() {
        var exception = assertThrows(IllegalStateException.class, () -> new PasswordResetOutboxCipher(
                new ObjectMapper(), "", "outbox-2026-01",
                productionEnvironment()));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage()
                .contains("PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS"));
    }

    @Test
    void productionCipherRejectsAnActiveKeyIdOutsideTheKeyRing() {
        var exception = assertThrows(IllegalStateException.class, () -> new PasswordResetOutboxCipher(
                new ObjectMapper(), "old-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "outbox-2026-01", productionEnvironment()));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage()
                .contains("PASSWORD_RESET_OUTBOX_ACTIVE_KEY_ID"));
    }

    @Test
    void missingRenderEncryptionVariableResolvesToEmptyForActionableValidation() throws Exception {
        var properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-prod.properties"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new PropertiesPropertySource("prod-file", properties));
        var resolver = new PropertySourcesPropertyResolver(environment.getPropertySources());

        assertEquals("", resolver.getProperty("app.password-reset.outbox.encryption-keys"));
    }

    @Test
    void environmentVariableOverridesProductionPlaceholder() throws Exception {
        var properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-prod.properties"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new PropertiesPropertySource("prod-file", properties));
        environment.getPropertySources().addFirst(new MapPropertySource("render-env", Map.of(
                "PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS", "outbox-2026-01="
                        + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")));
        var resolver = new PropertySourcesPropertyResolver(environment.getPropertySources());

        assertEquals("outbox-2026-01=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                resolver.getProperty("app.password-reset.outbox.encryption-keys"));
    }

    private StandardEnvironment productionEnvironment() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
