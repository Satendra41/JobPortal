package com.jobportal.services;

import java.io.Serial;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jobportal.model.User;
import com.jobportal.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class EmailChangeVerificationService {

    private static final String SESSION_KEY = EmailChangeVerificationService.class.getName() + ".pending";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RegistrationEmailSender emailSender;
    private final Clock clock;
    private final int expirationMinutes;
    private final int maxAttempts;

    @Autowired
    public EmailChangeVerificationService(UserRepository userRepository, RegistrationEmailSender emailSender,
            @Value("${app.registration.otp.expiration-minutes:5}") int expirationMinutes,
            @Value("${app.registration.otp.max-attempts:5}") int maxAttempts) {
        this(userRepository, emailSender, Clock.systemUTC(), expirationMinutes, maxAttempts);
    }

    EmailChangeVerificationService(UserRepository userRepository, RegistrationEmailSender emailSender, Clock clock,
            int expirationMinutes, int maxAttempts) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.clock = clock;
        this.expirationMinutes = Math.max(1, expirationMinutes);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public void startEmailChange(Long userId, String requestedEmail, HttpSession session) {
        String email = normalizeEmail(requestedEmail);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
        if (currentUser.getEmail() != null && currentUser.getEmail().trim().equalsIgnoreCase(email)) {
            session.removeAttribute(SESSION_KEY);
            return;
        }
        if (isUsedByAnotherUser(email, userId)) {
            throw new IllegalArgumentException("That email address is already associated with an account.");
        }

        String otp = newOtp();
        emailSender.sendOtp(email, otp, expirationMinutes);
        session.setAttribute(SESSION_KEY, new PendingEmailChange(userId, email, ENCODER.encode(otp),
                clock.instant().plus(Duration.ofMinutes(expirationMinutes)), 0));
    }

    @Transactional
    public User verifyEmailChange(Long userId, String otp, HttpSession session) {
        Object stored = session.getAttribute(SESSION_KEY);
        if (!(stored instanceof PendingEmailChange pending) || !pending.userId().equals(userId)) {
            throw invalidCode();
        }

        Instant now = clock.instant();
        if (!pending.expiresAt().isAfter(now)) {
            session.removeAttribute(SESSION_KEY);
            throw new IllegalArgumentException("The email verification code has expired. Request a new code.");
        }
        if (pending.attempts() >= maxAttempts) {
            session.removeAttribute(SESSION_KEY);
            throw new IllegalArgumentException("Too many attempts. Request a new verification code.");
        }
        if (!StringUtils.hasText(otp) || !ENCODER.matches(otp.trim(), pending.otpHash())) {
            session.setAttribute(SESSION_KEY, pending.withAttempts(pending.attempts() + 1));
            throw invalidCode();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
        if (isUsedByAnotherUser(pending.email(), userId)) {
            session.removeAttribute(SESSION_KEY);
            throw new IllegalArgumentException("That email address is already associated with an account.");
        }

        user.setEmail(pending.email());
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        session.removeAttribute(SESSION_KEY);
        return saved;
    }

    private boolean isUsedByAnotherUser(String email, Long userId) {
        var matches = userRepository.findAllByEmailIgnoreCase(email);
        return matches.stream().anyMatch(match -> !userId.equals(match.getId()));
    }

    private IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("The email verification code is invalid or expired.");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String newOtp() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private record PendingEmailChange(Long userId, String email, String otpHash, Instant expiresAt, int attempts)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private PendingEmailChange withAttempts(int updatedAttempts) {
            return new PendingEmailChange(userId, email, otpHash, expiresAt, updatedAttempts);
        }
    }
}
