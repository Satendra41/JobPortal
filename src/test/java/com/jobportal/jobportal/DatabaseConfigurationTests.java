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
    void mainDatabaseCredentialsRequireEnvironmentVariablesWithoutFallbacks() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }

        assertTrue("${DB_URL}".equals(properties.getProperty("spring.datasource.url")),
                "The main database URL must require DB_URL without a fallback.");
        assertTrue("${DB_USERNAME}".equals(properties.getProperty("spring.datasource.username")),
                "The main database username must require DB_USERNAME without a fallback.");
        assertTrue("${DB_PASSWORD}".equals(properties.getProperty("spring.datasource.password")),
                "The main database password must require DB_PASSWORD without a fallback.");
    }
}
