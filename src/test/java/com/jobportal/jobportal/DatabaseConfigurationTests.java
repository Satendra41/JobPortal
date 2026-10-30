package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class DatabaseConfigurationTests {

    @Test
    void localAndProductionDatabaseUrlsAreConfiguredSeparately() throws IOException {
        Properties common = load("application.properties");
        Properties local = load("application-local.properties");
        Properties production = load("application-prod.properties");

        assertTrue("local".equals(common.getProperty("spring.profiles.default")),
                "Local should be the default profile for development.");
        assertTrue(common.getProperty("spring.datasource.url") == null,
                "The shared configuration must not override profile-specific database URLs.");
        assertTrue(local.getProperty("spring.datasource.url").startsWith("${DB_URL:jdbc:mysql://"),
                "Local should allow DB_URL override and otherwise use a local MySQL JDBC URL.");
        assertTrue("${DB_URL}".equals(production.getProperty("spring.datasource.url")),
                "Production must require its environment-provided DB_URL without a local fallback.");
        assertTrue("${DB_USERNAME}".equals(common.getProperty("spring.datasource.username")),
                "The database username must remain environment-based.");
        assertTrue("${DB_PASSWORD}".equals(common.getProperty("spring.datasource.password")),
                "The database password must remain environment-based.");
    }

    private Properties load(String fileName) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of("src/main/resources", fileName))) {
            properties.load(input);
        }
        return properties;
    }
}
