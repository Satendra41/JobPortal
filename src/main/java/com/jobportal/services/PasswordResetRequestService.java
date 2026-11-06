package com.jobportal.services;

import java.text.Normalizer;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetRequestService.class);
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordResetOutboxService outboxService;

    public PasswordResetRequestService(PasswordResetRateLimiter rateLimiter,
            PasswordResetOutboxService outboxService) {
        this.rateLimiter = rateLimiter;
        this.outboxService = outboxService;
    }

    /** Always completes without exposing account or delivery state to the caller. */
    public void requestReset(String email, String clientAddress) {
        try {
            String normalizedEmail = email == null ? ""
                    : Normalizer.normalize(email.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
            if (!rateLimiter.allowClientRequest(clientAddress) || !isValidEmail(normalizedEmail)) return;
            outboxService.enqueueRequest(normalizedEmail);
        } catch (RuntimeException ex) {
            // Fixed message only; includes bounded-executor rejection, never request data.
            logger.warn("Password reset request was not queued.");
        }
    }

    private boolean isValidEmail(String email) {
        return email.length() <= 254 && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }
}
