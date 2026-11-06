package com.jobportal.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobportal.model.PasswordResetOutboxJob;

@Component
public class PasswordResetOutboxWorker {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetOutboxWorker.class);
    private final PasswordResetOutboxService outboxService;
    private final PasswordResetEmailSender emailSender;
    private final boolean enabled;

    public PasswordResetOutboxWorker(PasswordResetOutboxService outboxService,
            PasswordResetEmailSender emailSender,
            @Value("${app.password-reset.outbox.worker-enabled:true}") boolean enabled) {
        this.outboxService = outboxService;
        this.emailSender = emailSender;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.password-reset.outbox.poll-interval-ms:5000}")
    public void poll() {
        if (enabled) processAvailable();
    }

    /** Public for deterministic H2 integration tests; scheduling is disabled in test properties. */
    public void processAvailable() {
        for (int processed = 0; processed < 16; processed++) {
            var next = outboxService.claimNext();
            if (next.isEmpty()) return;
            PasswordResetOutboxService.ClaimedJob claim = next.get();
            try {
                if (claim.type() == PasswordResetOutboxJob.Type.RESET_REQUEST) {
                    outboxService.processResetRequest(claim);
                } else {
                    outboxService.deliverResetEmail(claim, emailSender);
                }
            } catch (RuntimeException ex) {
                outboxService.retryOrFail(claim, claim.type() == PasswordResetOutboxJob.Type.RESET_EMAIL
                        ? "DELIVERY_FAILED" : "REQUEST_PROCESSING_FAILED");
                logger.warn("Password reset outbox attempt failed; retry policy applied. exceptionClass={}",
                        ex.getClass().getSimpleName());
            }
        }
    }
}
