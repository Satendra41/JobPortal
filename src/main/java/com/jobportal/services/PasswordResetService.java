package com.jobportal.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.PasswordResetToken;
import com.jobportal.model.User;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;

@Service
public class PasswordResetService {

    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final String INVALID_TOKEN_MESSAGE = "The password reset token is invalid or expired.";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository) {
        this(userRepository, tokenRepository, Clock.systemUTC());
    }

    PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    /**
     * Creates one outstanding reset token for an eligible user. The raw token is
     * returned only to the caller responsible for delivering it to that user.
     */
    @Transactional
    public Optional<IssuedResetToken> issueResetToken(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (!isEligibleForLocalPasswordReset(user)) {
            return Optional.empty();
        }

        // Retain invalidated rows so queued email jobs can be terminalized instead of
        // being silently cascade-deleted by the V3 outbox foreign key.
        tokenRepository.consumeOutstandingByUserId(userId);

        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant createdAt = clock.instant();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenDigest(digest(rawToken));
        token.setCreatedAt(createdAt);
        token.setExpiresAt(createdAt.plus(TOKEN_LIFETIME));
        token.setConsumed(false);
        token.setFailedAttempts(0);
        tokenRepository.saveAndFlush(token);

        return Optional.of(new IssuedResetToken(rawToken, token.getExpiresAt(), token.getId()));
    }

    /** Read-only validation for rendering the reset form; POST redemption revalidates under locks. */
    @Transactional(readOnly = true)
    public boolean isTokenRedeemable(String rawToken) {
        final String tokenDigest;
        try {
            tokenDigest = digestIfWellFormed(rawToken);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        PasswordResetToken token = tokenRepository.findByTokenDigest(tokenDigest).orElse(null);
        if (token == null || token.isConsumed() || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(clock.instant())) {
            return false;
        }
        return isEligibleForLocalPasswordReset(token.getUser());
    }

    /**
     * Redeems a reset token once. The user row is locked before the token row so
     * issuance and redemption use the same lock order and serialize per account.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        String tokenDigest = digestIfWellFormed(rawToken);

        Long userId = tokenRepository.findUserIdByTokenDigest(tokenDigest)
                .orElseThrow(this::invalidToken);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidToken);
        PasswordResetToken token = tokenRepository.findByTokenDigestForUpdate(tokenDigest)
                .orElseThrow(this::invalidToken);

        if (!userId.equals(token.getUser().getId()) || token.isConsumed()
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(clock.instant())
                || !isEligibleForLocalPasswordReset(user)) {
            throw invalidToken();
        }

        user.setPassword(PASSWORD_ENCODER.encode(newPassword));
        user.incrementCredentialVersion();
        token.setConsumed(true);

        userRepository.saveAndFlush(user);
        tokenRepository.saveAndFlush(token);
    }

    boolean isEligibleForLocalPasswordReset(User user) {
        return user != null && user.isEnabled() && user.hasExplicitlyVerifiedEmail()
                && user.getPassword() != null && !user.getPassword().isBlank();
    }

    private String digestIfWellFormed(String rawToken) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            throw invalidToken();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
            if (decoded.length != TOKEN_BYTES
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(rawToken)) {
                throw invalidToken();
            }
        } catch (IllegalArgumentException ex) {
            throw invalidToken();
        }
        return digest(rawToken);
    }

    private String digest(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException(INVALID_TOKEN_MESSAGE);
    }

    public record IssuedResetToken(String rawToken, Instant expiresAt, Long tokenId) {
        @Override
        public String toString() {
            return "IssuedResetToken[rawToken=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }
}
