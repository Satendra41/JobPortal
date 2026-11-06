package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.jobportal.model.PasswordResetOutboxJob;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.PasswordResetOutboxJobRepository;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.PasswordResetEmailSender;
import com.jobportal.services.PasswordResetOutboxService;
import com.jobportal.services.PasswordResetOutboxWorker;
import com.jobportal.services.PasswordResetRequestService;
import com.jobportal.services.PasswordResetService;

@SpringBootTest(properties = {
        "app.password-reset.base-url=https://jagaq.example.test",
        "app.password-reset.rate-limit-hash-key=outbox-test-only-key-at-least-32-bytes-long",
        "app.password-reset.rate-limit.account-max=10",
        "app.password-reset.rate-limit.client-max=10"
})
class PasswordResetOutboxIntegrationTests {
    @MockitoSpyBean private PasswordResetOutboxJobRepository outboxRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetRequestService requestService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetOutboxWorker worker;
    @Autowired private PasswordResetOutboxService outboxService;
    @MockitoBean private PasswordResetEmailSender emailSender;
    private User user;

    @BeforeEach
    void setup() {
        reset(emailSender);
        outboxRepository.deleteAll();
        user = new User();
        user.setName("Outbox Test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("$2a$10$not-a-real-password-hash-for-test-fixture-only");
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.saveAndFlush(user);
    }

    @AfterEach
    void cleanup() {
        outboxRepository.deleteAll();
        tokenRepository.deleteAllByUserId(user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    void workerCreatesTokenAndEncryptedEmailJobThenSendsCanonicalLink() {
        requestService.requestReset(user.getEmail(), "198.51.100.20");
        PasswordResetOutboxJob intake = outboxRepository.findAll().get(0);
        assertEquals(PasswordResetOutboxJob.Type.RESET_REQUEST, intake.getJobType());
        assertFalse(new String(intake.getEncryptedPayload(), StandardCharsets.UTF_8).contains(user.getEmail()));

        worker.processAvailable();

        assertEquals(1, tokenRepository.count());
        PasswordResetOutboxJob email = outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL)
                .findFirst().orElseThrow();
        assertEquals(PasswordResetOutboxJob.Status.SENT, email.getStatus());
        assertNotNull(email.getSentAt());
        verify(emailSender).sendPasswordResetLink(eq(user.getEmail()),
                org.mockito.ArgumentMatchers.matches("https://jagaq\\.example\\.test/reset-password\\?token=.{43}"));
    }

    @Test
    void failedEmailDeliveryIsRetriedAndExpiredLeaseCanBeRecovered() {
        queueEmailWithoutSending();
        PasswordResetOutboxJob email = outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL)
                .findFirst().orElseThrow();
        // Simulate a process that died after claiming; the next worker may reclaim the expired lease.
        email.setStatus(PasswordResetOutboxJob.Status.PROCESSING);
        email.setLockedUntil(java.time.Instant.now().minusSeconds(1));
        email.setNextAttemptAt(null);
        outboxRepository.saveAndFlush(email);
        var recovered = outboxService.claimNext();
        assertTrue(recovered.isPresent());
        assertEquals(email.getId(), recovered.get().id());
    }

    @Test
    void failedDeliveryStoresOnlyBoundedErrorAndSchedulesRetry() {
        queueEmailWithoutSending();
        PasswordResetOutboxJob email = outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL)
                .findFirst().orElseThrow();
        email.setNextAttemptAt(java.time.Instant.now().minusSeconds(1));
        outboxRepository.saveAndFlush(email);
        reset(emailSender);
        doThrow(new IllegalStateException("sensitive provider response")).when(emailSender)
                .sendPasswordResetLink(anyString(), anyString());

        worker.processAvailable();

        PasswordResetOutboxJob retried = outboxRepository.findById(email.getId()).orElseThrow();
        assertEquals(PasswordResetOutboxJob.Status.PENDING, retried.getStatus());
        assertEquals("DELIVERY_FAILED", retried.getLastErrorCode());
        assertTrue(retried.getNextAttemptAt().isAfter(java.time.Instant.now()));
        assertFalse(retried.toString().contains("sensitive provider response"));

        // Once backoff elapses, a still-valid token remains eligible for delivery.
        retried.setNextAttemptAt(java.time.Instant.now().minusSeconds(1));
        outboxRepository.saveAndFlush(retried);
        reset(emailSender);
        worker.processAvailable();
        assertEquals(PasswordResetOutboxJob.Status.SENT,
                outboxRepository.findById(email.getId()).orElseThrow().getStatus());
        verify(emailSender).sendPasswordResetLink(eq(user.getEmail()), anyString());
    }

    @Test
    void expiredTokenEmailIsTerminalizedAndNeverRetried() {
        PasswordResetOutboxJob email = queueEmailWithoutSending();
        var token = tokenRepository.findById(email.getTokenId()).orElseThrow();
        token.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        tokenRepository.saveAndFlush(token);

        worker.processAvailable();

        assertObsoleteAndNotSent(email.getId());
        worker.processAvailable();
        assertObsoleteAndNotSent(email.getId());
        verifyNoInteractions(emailSender);
    }

    @Test
    void consumedTokenEmailIsTerminalizedAndNeverRetried() {
        PasswordResetOutboxJob email = queueEmailWithoutSending();
        var token = tokenRepository.findById(email.getTokenId()).orElseThrow();
        token.setConsumed(true);
        tokenRepository.saveAndFlush(token);

        worker.processAvailable();

        assertObsoleteAndNotSent(email.getId());
        verifyNoInteractions(emailSender);
    }

    @Test
    void replacedTokenEmailIsTerminalizedAndNeverRetried() {
        PasswordResetOutboxJob email = queueEmailWithoutSending();
        // Issuing a newer token invalidates the queued token without cascading away its job.
        passwordResetService.issueResetToken(user.getId()).orElseThrow();

        worker.processAvailable();

        assertObsoleteAndNotSent(email.getId());
        verifyNoInteractions(emailSender);
    }

    @Test
    void disabledAccountEmailIsTerminalizedAndNeverRetried() {
        PasswordResetOutboxJob email = queueEmailWithoutSending();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        worker.processAvailable();

        assertObsoleteAndNotSent(email.getId());
        verifyNoInteractions(emailSender);
    }

    @Test
    void tokenAndEmailJobCreationRollBackTogetherWhenEmailJobCannotBeStored() {
        requestService.requestReset(user.getEmail(), "198.51.100.24");
        var intake = outboxService.claimNext().orElseThrow();
        org.mockito.Mockito.doAnswer(invocation -> {
            PasswordResetOutboxJob job = invocation.getArgument(0);
            if (job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL) {
                throw new IllegalStateException("simulated isolated test persistence failure");
            }
            return invocation.callRealMethod();
        }).when(outboxRepository).saveAndFlush(any(PasswordResetOutboxJob.class));

        assertThrows(IllegalStateException.class, () -> outboxService.processResetRequest(intake));

        assertEquals(0, tokenRepository.count());
        assertEquals(0, outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL).count());
        assertEquals(PasswordResetOutboxJob.Status.PROCESSING,
                outboxRepository.findById(intake.id()).orElseThrow().getStatus());
    }

    private PasswordResetOutboxJob queueEmailWithoutSending() {
        requestService.requestReset(user.getEmail(), "198.51.100.23");
        var intake = outboxService.claimNext().orElseThrow();
        outboxService.processResetRequest(intake);
        return outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_EMAIL)
                .findFirst().orElseThrow();
    }

    private void assertObsoleteAndNotSent(String jobId) {
        PasswordResetOutboxJob obsolete = outboxRepository.findById(jobId).orElseThrow();
        assertEquals(PasswordResetOutboxJob.Status.FAILED, obsolete.getStatus());
        assertEquals("RESET_TOKEN_OBSOLETE", obsolete.getLastErrorCode());
        assertEquals(1, obsolete.getAttemptCount(), "The obsolete job must not be claimed again for retry.");
        assertEquals(null, obsolete.getEncryptedPayload());
        assertEquals(null, obsolete.getNonce());
        assertEquals(null, obsolete.getNextAttemptAt());
        assertEquals(null, obsolete.getLockedUntil());
    }
}
