package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.jobportal.model.PasswordResetToken;
import com.jobportal.model.User;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;

@DataJpaTest
class PasswordResetTokenRepositoryTests {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsOnlyTokenDigestAndResetLifecycleFields() {
        User user = userRepository.saveAndFlush(user("token-fields"));
        String digest = "0123456789abcdef".repeat(4);
        Instant createdAt = Instant.parse("2026-11-05T10:15:30.123Z");
        Instant expiresAt = Instant.parse("2026-11-05T10:20:30.123Z");

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenDigest(digest);
        token.setCreatedAt(createdAt);
        token.setExpiresAt(expiresAt);
        token.setConsumed(true);
        token.setFailedAttempts(2);
        PasswordResetToken saved = tokenRepository.saveAndFlush(token);

        PasswordResetToken persisted = tokenRepository.findById(saved.getId()).orElseThrow();
        assertEquals(digest, persisted.getTokenDigest());
        assertEquals(user.getId(), persisted.getUser().getId());
        assertEquals(createdAt, persisted.getCreatedAt());
        assertEquals(expiresAt, persisted.getExpiresAt());
        assertTrue(persisted.isConsumed());
        assertEquals(2, persisted.getFailedAttempts());
    }

    @Test
    void findsResetTokenByDigestAndSupportsTransactionalLockingLookup() {
        User user = userRepository.saveAndFlush(user("token-lock"));
        String digest = "fedcba9876543210".repeat(4);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenDigest(digest);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(300));
        token = tokenRepository.saveAndFlush(token);

        Optional<PasswordResetToken> found = tokenRepository.findByTokenDigest(digest);
        Optional<PasswordResetToken> locked = tokenRepository.findByTokenDigestForUpdate(digest);

        assertTrue(found.isPresent());
        assertNotNull(locked.orElseThrow().getId());
        assertEquals(token.getId(), locked.orElseThrow().getId());
    }

    @Test
    void credentialVersionDefaultsToZeroAndCanBeIncremented() {
        User user = user("credential-version");
        assertEquals(0, user.getCredentialVersion());

        User saved = userRepository.saveAndFlush(user);
        User persisted = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(0, persisted.getCredentialVersion());
        assertEquals(1, persisted.incrementCredentialVersion());
        userRepository.saveAndFlush(persisted);

        assertEquals(1, userRepository.findById(saved.getId()).orElseThrow().getCredentialVersion());
    }

    private User user(String label) {
        User user = new User();
        user.setName(label);
        user.setEmail(label + "@example.test");
        return user;
    }
}
