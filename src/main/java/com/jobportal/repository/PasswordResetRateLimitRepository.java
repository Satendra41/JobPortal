package com.jobportal.repository;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PasswordResetRateLimitRepository {

    private static final String UPSERT_AND_INCREMENT = """
            INSERT INTO password_reset_rate_limits
                (limit_scope, identifier_hash, window_started_at, request_count)
            VALUES (?, ?, ?, 1)
            ON DUPLICATE KEY UPDATE
                request_count = CASE
                    WHEN window_started_at <= ? THEN 1
                    WHEN request_count <= ? THEN request_count + 1
                    ELSE request_count
                END,
                window_started_at = CASE
                    WHEN window_started_at <= ? THEN ?
                    ELSE window_started_at
                END
            """;

    private final JdbcTemplate jdbcTemplate;

    public PasswordResetRateLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically reserves one attempt. The unique scope/hash key serializes
     * concurrent upserts for the same identifier in MySQL and H2 MySQL mode.
     */
    @Transactional
    public boolean tryConsume(String scope, String identifierHash, Instant now, Instant windowCutoff, int limit) {
        // The unique-key upsert obtains the row lock. Concurrent callers
        // serialize here; count is capped at limit + 1 to record denial state.
        jdbcTemplate.update(UPSERT_AND_INCREMENT, scope, identifierHash, now, windowCutoff, limit,
                windowCutoff, now);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT request_count FROM password_reset_rate_limits
                WHERE limit_scope = ? AND identifier_hash = ?
                """, Integer.class, scope, identifierHash);
        return count != null && count <= limit;
    }

    @Transactional
    public int deleteExpiredBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM password_reset_rate_limits WHERE window_started_at < ?", cutoff);
    }
}
