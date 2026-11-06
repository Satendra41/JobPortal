package com.jobportal.services;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.repository.PasswordResetRateLimitRepository;

@Service
public class PasswordResetRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final Duration RETENTION = Duration.ofDays(2);
    private final PasswordResetRateLimitRepository repository;
    private final Clock clock;
    private final byte[] hashKey;
    private final int accountLimit;
    private final int clientLimit;

    @Autowired
    public PasswordResetRateLimiter(PasswordResetRateLimitRepository repository,
            @Value("${app.password-reset.rate-limit-hash-key:}") String hashKey,
            @Value("${app.password-reset.rate-limit.account-max:5}") int accountLimit,
            @Value("${app.password-reset.rate-limit.client-max:20}") int clientLimit) {
        this(repository, hashKey, accountLimit, clientLimit, Clock.systemUTC());
    }

    PasswordResetRateLimiter(PasswordResetRateLimitRepository repository, String hashKey,
            int accountLimit, int clientLimit, Clock clock) {
        this.repository = repository;
        this.hashKey = hashKey == null ? new byte[0] : hashKey.getBytes(StandardCharsets.UTF_8);
        this.accountLimit = accountLimit;
        this.clientLimit = clientLimit;
        this.clock = clock;
    }

    @Transactional
    public boolean allowClientRequest(String clientAddress) {
        if (hashKey.length < 32 || accountLimit < 1 || clientLimit < 1
                || clientAddress == null || clientAddress.isBlank()) {
            return false;
        }

        Instant now = clock.instant();
        Instant windowCutoff = now.minus(WINDOW);
        repository.deleteExpiredBefore(now.minus(RETENTION));
        return repository.tryConsume("CLIENT", keyedHash("CLIENT", clientAddress),
                now, windowCutoff, clientLimit);
    }

    @Transactional
    public boolean allowAccountRequest(String normalizedEmail) {
        if (hashKey.length < 32 || accountLimit < 1 || normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        Instant windowCutoff = now.minus(WINDOW);
        repository.deleteExpiredBefore(now.minus(RETENTION));
        return repository.tryConsume("ACCOUNT", keyedHash("ACCOUNT", normalizedEmail),
                now, windowCutoff, accountLimit);
    }

    String keyedHash(String scope, String identifier) {
        if (hashKey.length < 32) {
            throw new IllegalStateException("Password reset rate-limit hashing is not configured.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((scope + ":" + identifier).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Password reset rate-limit hashing is unavailable.");
        }
    }
}
