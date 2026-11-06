package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jobportal.model.PasswordResetToken;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.OAuthAccountService;
import com.jobportal.services.PasswordResetService;
import com.jobportal.services.UserService;

@SpringBootTest
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=",
        "GITHUB_CLIENT_SECRET="
})
class PasswordResetServiceIntegrationTests {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String OLD_PASSWORD = "existing-local-password";
    private static final String NEW_PASSWORD = "replacement-local-password";

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private OAuthAccountService oauthAccountService;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordResetTokenRepository tokenRepositorySpy;

    private final List<Long> createdUserIds = new ArrayList<>();
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long userId : createdUserIds) {
                tokenRepository.deleteAllByUserId(userId);
                oauthAccountRepository.deleteAllByUser_Id(userId);
                userRepository.deleteById(userId);
            }
        });
        createdUserIds.clear();
    }

    @Test
    void validTokenStoresOnlyDigestAndResetsPasswordAndCredentialVersion() throws Exception {
        User user = createUser("valid-reset");
        String originalEmail = user.getEmail();
        boolean originalVerified = user.isEmailVerified();
        Instant issuedAt = Instant.now();
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();

        assertEquals(Duration.ofMinutes(15), PasswordResetService.TOKEN_LIFETIME);
        assertTrue(issued.expiresAt().isAfter(issuedAt));
        assertTrue(issued.rawToken().length() >= 43);

        String digest = sha256(issued.rawToken());
        PasswordResetToken stored = tokenRepository.findByTokenDigest(digest).orElseThrow();
        assertEquals(digest, stored.getTokenDigest());
        assertTrue(tokenRepository.findByTokenDigest(issued.rawToken()).isEmpty());
        assertFalse(stored.getTokenDigest().equals(issued.rawToken()));
        assertEquals(Duration.ofMinutes(15), Duration.between(stored.getCreatedAt(), stored.getExpiresAt()));
        assertFalse(stored.isConsumed());

        passwordResetService.resetPassword(issued.rawToken(), NEW_PASSWORD);

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(userService.authenticateUser(user.getEmail(), NEW_PASSWORD));
        assertNull(userService.authenticateUser(user.getEmail(), OLD_PASSWORD));
        assertEquals(1, updated.getCredentialVersion());
        assertEquals(originalEmail, updated.getEmail());
        assertEquals(originalVerified, updated.isEmailVerified());
        assertTrue(tokenRepository.findByTokenDigest(digest).orElseThrow().isConsumed());
        assertEquals(1, updated.getCredentialVersion(), "A reset must increment the version exactly once.");
    }

    @Test
    void expiredTokenIsRejectedWithoutChangingPasswordVersionOrConsumedState() throws Exception {
        User user = createUser("expired-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        String digest = sha256(issued.rawToken());
        PasswordResetToken token = tokenRepository.findByTokenDigest(digest).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        tokenRepository.saveAndFlush(token);

        assertInvalidToken(issued.rawToken());

        assertTrue(PASSWORD_ENCODER.matches(OLD_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword()));
        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
        assertFalse(tokenRepository.findByTokenDigest(digest).orElseThrow().isConsumed());
    }

    @Test
    void consumedTokenCannotBeReused() throws Exception {
        User user = createUser("reuse-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        String digest = sha256(issued.rawToken());

        passwordResetService.resetPassword(issued.rawToken(), NEW_PASSWORD);
        assertInvalidToken(issued.rawToken());

        assertTrue(tokenRepository.findByTokenDigest(digest).orElseThrow().isConsumed());
        assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
    }

    @Test
    void malformedAndUnknownTokensAreRejectedWithTheSameSafeMessage() throws Exception {
        User user = createUser("unknown-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        String malformedMessage = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword("not-a-reset-token", NEW_PASSWORD)).getMessage();
        String unknownMessage = assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword("A".repeat(43), NEW_PASSWORD)).getMessage();

        assertEquals("The password reset token is invalid or expired.", malformedMessage);
        assertEquals(malformedMessage, unknownMessage);
        assertTrue(tokenRepository.findByTokenDigest(sha256(issued.rawToken())).isPresent());
        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
    }

    @Test
    void invalidPasswordDoesNotConsumeTokenOrPartiallyChangeAccount() throws Exception {
        User user = createUser("invalid-password-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        String digest = sha256(issued.rawToken());

        assertEquals("Password must be at least 8 characters long.", assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(issued.rawToken(), "short")).getMessage());

        assertFalse(tokenRepository.findByTokenDigest(digest).orElseThrow().isConsumed());
        assertTrue(PASSWORD_ENCODER.matches(OLD_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword()));
        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
    }

    @Test
    void disabledAndUnverifiedAccountsCannotRedeemResetTokens() throws Exception {
        User disabled = createUser("disabled-reset");
        PasswordResetService.IssuedResetToken disabledToken = passwordResetService.issueResetToken(disabled.getId())
                .orElseThrow();
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);
        assertInvalidToken(disabledToken.rawToken());
        assertTrue(PASSWORD_ENCODER.matches(OLD_PASSWORD,
                userRepository.findById(disabled.getId()).orElseThrow().getPassword()));
        assertEquals(0, userRepository.findById(disabled.getId()).orElseThrow().getCredentialVersion());

        User unverified = createUser("unverified-reset");
        PasswordResetService.IssuedResetToken unverifiedToken = passwordResetService
                .issueResetToken(unverified.getId()).orElseThrow();
        unverified.setEmailVerified(false);
        userRepository.saveAndFlush(unverified);
        assertInvalidToken(unverifiedToken.rawToken());
        assertTrue(PASSWORD_ENCODER.matches(OLD_PASSWORD,
                userRepository.findById(unverified.getId()).orElseThrow().getPassword()));
        assertEquals(0, userRepository.findById(unverified.getId()).orElseThrow().getCredentialVersion());
    }

    @Test
    void disabledUnverifiedAndOauthOnlyUsersCannotBeIssuedResetTokens() {
        User disabled = createUser("not-issued-disabled");
        disabled.setEnabled(false);
        userRepository.saveAndFlush(disabled);
        assertTrue(passwordResetService.issueResetToken(disabled.getId()).isEmpty());

        User unverified = createUser("not-issued-unverified");
        unverified.setEmailVerified(false);
        userRepository.saveAndFlush(unverified);
        assertTrue(passwordResetService.issueResetToken(unverified.getId()).isEmpty());

        User legacyNullVerification = createUser("not-issued-null-verification");
        jdbcTemplate.update("UPDATE users SET email_verified = NULL WHERE id = ?", legacyNullVerification.getId());
        assertTrue(passwordResetService.issueResetToken(legacyNullVerification.getId()).isEmpty());

        String email = runId + ".oauth-only@example.test";
        User oauthUser = oauthAccountService.authenticateOrCreate(
                "google", "password-reset-" + runId, email, true, "OAuth reset test");
        createdUserIds.add(oauthUser.getId());
        assertNull(oauthUser.getPassword());
        assertTrue(passwordResetService.issueResetToken(oauthUser.getId()).isEmpty());

        User stillOAuthOnly = oauthAccountService.authenticateOrCreate(
                "google", "password-reset-" + runId, email, true, "OAuth reset test");
        assertEquals(oauthUser.getId(), stillOAuthOnly.getId());
        assertNull(stillOAuthOnly.getPassword());
        assertEquals(0, stillOAuthOnly.getCredentialVersion());
        assertTrue(oauthAccountRepository.findByProviderAndProviderUserId(
                "google", "password-reset-" + runId).isPresent());
    }

    @Test
    void issuingAnotherTokenInvalidatesThePreviousOutstandingToken() throws Exception {
        User user = createUser("replace-token");
        PasswordResetService.IssuedResetToken first = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        PasswordResetService.IssuedResetToken second = passwordResetService.issueResetToken(user.getId()).orElseThrow();

        PasswordResetToken replaced = tokenRepository.findByTokenDigest(sha256(first.rawToken())).orElseThrow();
        assertTrue(replaced.isConsumed());
        assertTrue(tokenRepository.findByTokenDigest(sha256(second.rawToken())).isPresent());
        assertInvalidToken(first.rawToken());
        passwordResetService.resetPassword(second.rawToken(), NEW_PASSWORD);
        assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
    }

    @Test
    void concurrentRedemptionAllowsAtMostOneSuccessfulReset() throws Exception {
        User user = createUser("concurrent-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> attemptReset(issued.rawToken(), NEW_PASSWORD, ready, start));
            Future<Boolean> second = executor.submit(() -> attemptReset(issued.rawToken(),
                    "another-replacement-password", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
            assertFalse(userService.authenticateUser(user.getEmail(), OLD_PASSWORD) != null);
            assertTrue(userService.authenticateUser(user.getEmail(), NEW_PASSWORD) != null
                    || userService.authenticateUser(user.getEmail(), "another-replacement-password") != null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistenceFailureRollsBackPasswordVersionAndTokenConsumption() throws Exception {
        User user = createUser("rollback-reset");
        PasswordResetService.IssuedResetToken issued = passwordResetService.issueResetToken(user.getId()).orElseThrow();
        String digest = sha256(issued.rawToken());
        doThrow(new DataIntegrityViolationException("simulated token persistence failure"))
                .when(tokenRepositorySpy).saveAndFlush(any(PasswordResetToken.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> passwordResetService.resetPassword(issued.rawToken(), NEW_PASSWORD));

        assertTrue(PASSWORD_ENCODER.matches(OLD_PASSWORD,
                userRepository.findById(user.getId()).orElseThrow().getPassword()));
        assertEquals(0, userRepository.findById(user.getId()).orElseThrow().getCredentialVersion());
        assertFalse(tokenRepository.findByTokenDigest(digest).orElseThrow().isConsumed());
    }

    private boolean attemptReset(String rawToken, String password, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            passwordResetService.resetPassword(rawToken, password);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private User createUser(String label) {
        User user = new User();
        user.setName("Password reset " + label);
        user.setEmail(runId + "." + label + "@example.test");
        user.setPassword(PASSWORD_ENCODER.encode(OLD_PASSWORD));
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        User saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private void assertInvalidToken(String rawToken) {
        assertEquals("The password reset token is invalid or expired.", assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(rawToken, NEW_PASSWORD)).getMessage());
    }

    private String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII)));
    }
}
