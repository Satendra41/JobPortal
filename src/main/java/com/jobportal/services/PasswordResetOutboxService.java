package com.jobportal.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.jobportal.model.PasswordResetOutboxJob;
import com.jobportal.model.User;
import com.jobportal.repository.PasswordResetOutboxJobRepository;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;

@Service
public class PasswordResetOutboxService {
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);
    private static final int CLAIM_BATCH_SIZE = 16;

    private final PasswordResetOutboxJobRepository outboxRepository;
    private final PasswordResetOutboxCipher cipher;
    private final PasswordResetRateLimiter rateLimiter;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetService passwordResetService;
    private final CanonicalResetUrlBuilder resetUrlBuilder;
    private final Clock clock;

    @Autowired
    public PasswordResetOutboxService(PasswordResetOutboxJobRepository outboxRepository,
            PasswordResetOutboxCipher cipher, PasswordResetRateLimiter rateLimiter,
            UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            PasswordResetService passwordResetService,
            CanonicalResetUrlBuilder resetUrlBuilder) {
        this(outboxRepository, cipher, rateLimiter, userRepository, tokenRepository, passwordResetService,
                resetUrlBuilder, Clock.systemUTC());
    }

    PasswordResetOutboxService(PasswordResetOutboxJobRepository outboxRepository,
            PasswordResetOutboxCipher cipher, PasswordResetRateLimiter rateLimiter,
            UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            PasswordResetService passwordResetService,
            CanonicalResetUrlBuilder resetUrlBuilder, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordResetService = passwordResetService;
        this.resetUrlBuilder = resetUrlBuilder;
        this.clock = clock;
    }

    /** Persist every syntactically valid request before the endpoint returns its generic response. */
    @Transactional
    public void enqueueRequest(String normalizedEmail) {
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        PasswordResetOutboxJob job = new PasswordResetOutboxJob();
        job.setId(id);
        job.setJobType(PasswordResetOutboxJob.Type.RESET_REQUEST);
        job.setStatus(PasswordResetOutboxJob.Status.PENDING);
        job.setAttemptCount(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setNextAttemptAt(now);
        PasswordResetOutboxCipher.EncryptedPayload encrypted = cipher.encrypt(id, job.getJobType(), null, null,
                new PasswordResetOutboxCipher.Payload(normalizedEmail, null));
        applyEncrypted(job, encrypted);
        outboxRepository.saveAndFlush(job);
    }

    /** Claim uses a compare-and-set UPDATE, so multiple app instances cannot claim the same job. */
    @Transactional
    public Optional<ClaimedJob> claimNext() {
        Instant now = clock.instant();
        List<String> candidates = outboxRepository.findCandidateIds(now, CLAIM_BATCH_SIZE);
        for (String id : candidates) {
            if (outboxRepository.claim(id, now, now.plus(CLAIM_LEASE)) != 1) continue;
            PasswordResetOutboxJob job = outboxRepository.findById(id).orElse(null);
            if (job == null) continue;
            if (job.getAttemptCount() > MAX_ATTEMPTS) {
                finish(job, PasswordResetOutboxJob.Status.FAILED, "RETRY_LIMIT_REACHED", false);
                continue;
            }
            return Optional.of(ClaimedJob.from(job));
        }
        return Optional.empty();
    }

    /** Token creation and encrypted email-job insertion share this transaction. */
    @Transactional
    public void processResetRequest(ClaimedJob claim) {
        PasswordResetOutboxJob requestJob = activeClaim(claim, PasswordResetOutboxJob.Type.RESET_REQUEST);
        PasswordResetOutboxCipher.Payload request = cipher.decrypt(requestJob);
        User user = userRepository.findAllByEmailIgnoreCase(request.email()).stream().findFirst().orElse(null);
        if (user == null || !rateLimiter.allowAccountRequest(request.email())) {
            finish(requestJob, PasswordResetOutboxJob.Status.COMPLETED, null, false);
            return;
        }

        Optional<PasswordResetService.IssuedResetToken> issued = passwordResetService.issueResetToken(user.getId());
        if (issued.isEmpty()) {
            finish(requestJob, PasswordResetOutboxJob.Status.COMPLETED, null, false);
            return;
        }

        PasswordResetService.IssuedResetToken token = issued.get();
        String emailJobId = UUID.randomUUID().toString();
        PasswordResetOutboxJob emailJob = new PasswordResetOutboxJob();
        emailJob.setId(emailJobId);
        emailJob.setJobType(PasswordResetOutboxJob.Type.RESET_EMAIL);
        emailJob.setUserId(user.getId());
        emailJob.setTokenId(token.tokenId());
        emailJob.setStatus(PasswordResetOutboxJob.Status.PENDING);
        emailJob.setAttemptCount(0);
        emailJob.setCreatedAt(clock.instant());
        emailJob.setUpdatedAt(emailJob.getCreatedAt());
        emailJob.setNextAttemptAt(emailJob.getCreatedAt());
        String resetUrl = resetUrlBuilder.build(token.rawToken());
        PasswordResetOutboxCipher.EncryptedPayload encrypted = cipher.encrypt(emailJobId, emailJob.getJobType(),
                user.getId(), token.tokenId(), new PasswordResetOutboxCipher.Payload(user.getEmail(), resetUrl));
        applyEncrypted(emailJob, encrypted);
        outboxRepository.saveAndFlush(emailJob);
        finish(requestJob, PasswordResetOutboxJob.Status.COMPLETED, null, false);
    }

    @Transactional(readOnly = true)
    public PasswordResetOutboxCipher.Payload decryptEmail(ClaimedJob claim) {
        PasswordResetOutboxJob job = activeClaim(claim, PasswordResetOutboxJob.Type.RESET_EMAIL);
        return cipher.decrypt(job);
    }

    @Transactional
    public void markSent(ClaimedJob claim) {
        outboxRepository.findById(claim.id()).ifPresent(job -> {
            if (job.getStatus() == PasswordResetOutboxJob.Status.PROCESSING) {
                finish(job, PasswordResetOutboxJob.Status.SENT, null, true);
            }
        });
    }

    /**
     * Validates under the same user-then-token locks used by issuance and redemption,
     * holding them through delivery so replacement cannot race the send operation.
     */
    @Transactional
    public void deliverResetEmail(ClaimedJob claim, PasswordResetEmailSender sender) {
        PasswordResetOutboxJob job = activeClaim(claim, PasswordResetOutboxJob.Type.RESET_EMAIL);
        if (job.getUserId() == null || job.getTokenId() == null) {
            markObsolete(job);
            return;
        }

        User user = userRepository.findByIdForUpdate(job.getUserId()).orElse(null);
        var token = tokenRepository.findByIdForUpdate(job.getTokenId()).orElse(null);
        Instant now = clock.instant();
        if (user == null || token == null || !passwordResetService.isEligibleForLocalPasswordReset(user)
                || token.getUser() == null || !job.getUserId().equals(token.getUser().getId())
                || token.isConsumed() || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)) {
            markObsolete(job);
            return;
        }

        PasswordResetOutboxCipher.Payload payload = cipher.decrypt(job);
        if (payload.email() == null || user.getEmail() == null
                || !payload.email().equalsIgnoreCase(user.getEmail())) {
            markObsolete(job);
            return;
        }

        sender.sendPasswordResetLink(payload.email(), payload.resetUrl());
        finish(job, PasswordResetOutboxJob.Status.SENT, null, true);
    }

    private void markObsolete(PasswordResetOutboxJob job) {
        finish(job, PasswordResetOutboxJob.Status.FAILED, "RESET_TOKEN_OBSOLETE", false);
    }

    @Transactional
    public void retryOrFail(ClaimedJob claim, String errorCode) {
        outboxRepository.findById(claim.id()).ifPresent(job -> {
            if (job.getStatus() != PasswordResetOutboxJob.Status.PROCESSING) return;
            if (job.getAttemptCount() >= MAX_ATTEMPTS) {
                finish(job, PasswordResetOutboxJob.Status.FAILED, errorCode, false);
                return;
            }
            long delaySeconds = Math.min(3600L, 30L << Math.min(10, Math.max(0, job.getAttemptCount() - 1)));
            job.setStatus(PasswordResetOutboxJob.Status.PENDING);
            job.setLockedUntil(null);
            job.setNextAttemptAt(clock.instant().plusSeconds(delaySeconds));
            job.setUpdatedAt(clock.instant());
            job.setLastErrorCode(errorCode);
            outboxRepository.save(job);
        });
    }

    private PasswordResetOutboxJob activeClaim(ClaimedJob claim, PasswordResetOutboxJob.Type expectedType) {
        PasswordResetOutboxJob job = outboxRepository.findById(claim.id())
                .orElseThrow(() -> new IllegalStateException("Outbox job no longer exists."));
        if (job.getStatus() != PasswordResetOutboxJob.Status.PROCESSING || job.getJobType() != expectedType) {
            throw new IllegalStateException("Outbox job is not claimed.");
        }
        return job;
    }

    private void applyEncrypted(PasswordResetOutboxJob job, PasswordResetOutboxCipher.EncryptedPayload encrypted) {
        job.setEncryptionKeyId(encrypted.keyId());
        job.setNonce(encrypted.nonce());
        job.setEncryptedPayload(encrypted.ciphertext());
    }

    private void finish(PasswordResetOutboxJob job, PasswordResetOutboxJob.Status status,
            String errorCode, boolean sent) {
        job.setStatus(status);
        job.setEncryptedPayload(null);
        job.setNonce(null);
        job.setLockedUntil(null);
        job.setNextAttemptAt(null);
        job.setUpdatedAt(clock.instant());
        job.setLastErrorCode(errorCode);
        if (sent) job.setSentAt(clock.instant());
        outboxRepository.saveAndFlush(job);
    }

    public record ClaimedJob(String id, PasswordResetOutboxJob.Type type, Long userId, Long tokenId,
            int attemptCount, String encryptionKeyId, byte[] nonce, byte[] encryptedPayload) {
        static ClaimedJob from(PasswordResetOutboxJob job) {
            return new ClaimedJob(job.getId(), job.getJobType(), job.getUserId(), job.getTokenId(),
                    job.getAttemptCount(), job.getEncryptionKeyId(), job.getNonce().clone(),
                    job.getEncryptedPayload().clone());
        }
        @Override public String toString() { return "ClaimedJob[id=" + id + ", type=" + type + ", payload=<redacted>]"; }
    }
}
