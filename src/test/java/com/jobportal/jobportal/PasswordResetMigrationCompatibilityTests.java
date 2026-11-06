package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PasswordResetMigrationCompatibilityTests {

    @Test
    void additiveMigrationAppliesToAnIsolatedLegacyH2Schema() throws Exception {
        String url = "jdbc:h2:mem:password_reset_migration_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(255))");
            statement.execute("INSERT INTO users (id, email) VALUES (9001, 'migration-fixture@example.test')");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        assertEquals(3, flyway.migrate().migrationsExecuted);

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            try (ResultSet columns = statement.executeQuery("""
                    SELECT COLUMN_DEFAULT, IS_NULLABLE
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'USERS'
                      AND COLUMN_NAME = 'CREDENTIAL_VERSION'
                    """)) {
                assertTrue(columns.next());
                assertEquals("0", columns.getString("COLUMN_DEFAULT"));
                assertEquals("NO", columns.getString("IS_NULLABLE"));
            }
            try (ResultSet existingUser = statement.executeQuery(
                    "SELECT credential_version FROM users WHERE id = 9001")) {
                assertTrue(existingUser.next());
                assertEquals(0, existingUser.getInt("credential_version"));
            }
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet indexes = metadata.getIndexInfo(null, "PUBLIC", "PASSWORD_RESET_TOKENS", true, false)) {
                boolean hasUniqueDigestIndex = false;
                while (indexes.next()) {
                    if ("UX_PASSWORD_RESET_TOKENS_DIGEST".equals(indexes.getString("INDEX_NAME"))) {
                        hasUniqueDigestIndex = true;
                    }
                }
                assertTrue(hasUniqueDigestIndex);
            }
            try (ResultSet foreignKey = statement.executeQuery("""
                    SELECT COUNT(*) AS FK_COUNT
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_TOKENS'
                      AND CONSTRAINT_NAME = 'FK_PASSWORD_RESET_TOKENS_USER'
                      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                    """)) {
                assertTrue(foreignKey.next());
                assertEquals(1, foreignKey.getInt("FK_COUNT"));
            }
            try (ResultSet tokenTable = statement.executeQuery("""
                    SELECT COUNT(*) AS TABLE_COUNT
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_TOKENS'
                    """)) {
                assertTrue(tokenTable.next());
                assertEquals(1, tokenTable.getInt("TABLE_COUNT"));
            }
            try (ResultSet rawTokenColumn = statement.executeQuery("""
                    SELECT COUNT(*) AS COLUMN_COUNT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_TOKENS'
                      AND COLUMN_NAME = 'RAW_TOKEN'
                    """)) {
                assertTrue(rawTokenColumn.next());
                assertFalse(rawTokenColumn.getInt("COLUMN_COUNT") > 0);
            }
            try (ResultSet rateLimitTable = statement.executeQuery("""
                    SELECT COUNT(*) AS TABLE_COUNT
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_RATE_LIMITS'
                    """)) {
                assertTrue(rateLimitTable.next());
                assertEquals(1, rateLimitTable.getInt("TABLE_COUNT"));
            }
            try (ResultSet rateLimitColumns = statement.executeQuery("""
                    SELECT COUNT(*) AS COLUMN_COUNT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_RATE_LIMITS'
                      AND COLUMN_NAME IN ('LIMIT_SCOPE', 'IDENTIFIER_HASH', 'WINDOW_STARTED_AT', 'REQUEST_COUNT')
                    """)) {
                assertTrue(rateLimitColumns.next());
                assertEquals(4, rateLimitColumns.getInt("COLUMN_COUNT"));
            }
            try (ResultSet outboxTable = statement.executeQuery("""
                    SELECT COUNT(*) AS TABLE_COUNT
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_EMAIL_OUTBOX'
                    """)) {
                assertTrue(outboxTable.next());
                assertEquals(1, outboxTable.getInt("TABLE_COUNT"));
            }
            try (ResultSet outboxColumns = statement.executeQuery("""
                    SELECT COUNT(*) AS COLUMN_COUNT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_EMAIL_OUTBOX'
                      AND COLUMN_NAME IN ('ENCRYPTED_PAYLOAD', 'NONCE', 'ENCRYPTION_KEY_ID', 'STATUS',
                          'ATTEMPT_COUNT', 'NEXT_ATTEMPT_AT', 'LOCKED_UNTIL')
                    """)) {
                assertTrue(outboxColumns.next());
                assertEquals(7, outboxColumns.getInt("COLUMN_COUNT"));
            }
            try (ResultSet plaintextColumns = statement.executeQuery("""
                    SELECT COUNT(*) AS COLUMN_COUNT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_NAME = 'PASSWORD_RESET_EMAIL_OUTBOX'
                      AND COLUMN_NAME IN ('EMAIL', 'RESET_URL', 'RAW_TOKEN', 'SECRET')
                    """)) {
                assertTrue(plaintextColumns.next());
                assertEquals(0, plaintextColumns.getInt("COLUMN_COUNT"));
            }
        }
    }
}
