package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class SessionCookieConfigurationTests {

    @Test
    void localDefaultsPreserveHttpDevelopmentAndCookieProtections() throws IOException {
        Properties properties = load("src/main/resources/application.properties");

        assertTrue("true".equals(properties.getProperty("server.servlet.session.cookie.http-only")),
                "HttpOnly must stay enabled.");
        assertTrue("lax".equalsIgnoreCase(properties.getProperty("server.servlet.session.cookie.same-site")),
                "The existing SameSite=Lax policy must stay enabled.");
        assertTrue("${SESSION_COOKIE_SECURE:false}".equals(
                        properties.getProperty("server.servlet.session.cookie.secure")),
                "Local HTTP development must default Secure to false while allowing configuration.");
    }

    @Test
    void productionProfileRequiresSecureSessionCookies() throws IOException {
        Properties properties = load("src/main/resources/application-prod.properties");

        assertTrue("true".equals(properties.getProperty("server.servlet.session.cookie.secure")),
                "The production profile must require Secure session cookies.");
        assertTrue("native".equals(properties.getProperty("server.forward-headers-strategy")),
                "The production profile must honor proxy HTTPS headers for OAuth callback URLs.");
    }

    private Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            properties.load(input);
        }
        return properties;
    }
}
