package com.jobportal.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "app.password-reset.rate-limit-hash-key=integration-test-only-key-with-at-least-32-bytes",
        "app.password-reset.rate-limit.account-max=2",
        "app.password-reset.rate-limit.client-max=2"
})
class PasswordResetRateLimiterIntegrationTests {
    @Autowired private PasswordResetRateLimiter limiter;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void limitsAccountAndClientIndependentlyAndPersistsOnlyHmacIdentifiers() {
        String id = UUID.randomUUID().toString();
        String email = id + "@example.test";
        String client = "203.0.113." + (10 + Math.abs(id.hashCode() % 200));

        assertTrue(limiter.allowAccountRequest(email));
        assertTrue(limiter.allowAccountRequest(email));
        assertFalse(limiter.allowAccountRequest(email));
        assertTrue(limiter.allowClientRequest(client));
        assertTrue(limiter.allowClientRequest(client));
        assertFalse(limiter.allowClientRequest(client));

        String emailHash = limiter.keyedHash("ACCOUNT", email);
        String clientHash = limiter.keyedHash("CLIENT", client);
        assertEquals(64, emailHash.length());
        assertEquals(64, clientHash.length());
        assertTrue(!emailHash.contains(email));
        assertTrue(!clientHash.contains(client));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM password_reset_rate_limits
                WHERE limit_scope = 'ACCOUNT' AND identifier_hash = ?
                """, Integer.class, emailHash));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM password_reset_rate_limits
                WHERE limit_scope = 'CLIENT' AND identifier_hash = ?
                """, Integer.class, clientHash));
    }

    @Test
    void concurrentClientRequestsCannotExceedAtomicLimit() throws Exception {
        String client = "198.51.100." + (10 + Math.abs(UUID.randomUUID().hashCode() % 200));
        int workers = 10;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.allowClientRequest(client);
                }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) if (result.get()) allowed++;
            assertEquals(2, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAccountRequestsCannotExceedAtomicLimit() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.allowAccountRequest(email);
                }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) if (result.get()) allowed++;
            assertEquals(2, allowed);
        } finally {
            executor.shutdownNow();
        }
    }
}
