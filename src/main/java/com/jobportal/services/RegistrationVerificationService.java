package com.jobportal.services;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jobportal.model.PendingRegistration;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.PendingRegistrationRepository;
import com.jobportal.repository.UserRepository;

@Service
public class RegistrationVerificationService {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final PendingRegistrationRepository pendingRepository;
    private final UserRepository userRepository;
    private final RegistrationEmailSender emailSender;
    private final Clock clock;
    private final int expirationMinutes;
    private final int maxAttempts;
    private final int resendCooldownSeconds;
    private final int maxResendsPerHour;

    @org.springframework.beans.factory.annotation.Autowired
    public RegistrationVerificationService(PendingRegistrationRepository pendingRepository,
            UserRepository userRepository, RegistrationEmailSender emailSender,
            @Value("${app.registration.otp.expiration-minutes:5}") int expirationMinutes,
            @Value("${app.registration.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.registration.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${app.registration.otp.max-resends-per-hour:5}") int maxResendsPerHour) {
        this(pendingRepository, userRepository, emailSender, Clock.systemUTC(), expirationMinutes, maxAttempts,
                resendCooldownSeconds, maxResendsPerHour);
    }

    RegistrationVerificationService(PendingRegistrationRepository pendingRepository, UserRepository userRepository,
            RegistrationEmailSender emailSender, Clock clock, int expirationMinutes, int maxAttempts,
            int resendCooldownSeconds, int maxResendsPerHour) {
        this.pendingRepository = pendingRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.clock = clock;
        this.expirationMinutes = Math.max(1, expirationMinutes);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.resendCooldownSeconds = Math.max(1, resendCooldownSeconds);
        this.maxResendsPerHour = Math.max(0, maxResendsPerHour);
    }

    @Transactional
    public void startRegistration(String name, String email, String password) {
        if (!StringUtils.hasText(name) || name.length() > 160 || !StringUtils.hasText(email)
                || email.trim().length() > 254 || !EMAIL.matcher(email.trim()).matches()
                || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("Enter a valid name, email, and password.");
        }
        PasswordPolicy.validate(password);
        String normalizedEmail = normalizeEmail(email);
        if (!userRepository.findAllByEmailIgnoreCase(normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("Unable to start sign up for this email. Check your details or sign in.");
        }

        Instant now = clock.instant();
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (pending == null) {
            pending = new PendingRegistration();
            pending.setEmail(normalizedEmail);
            pending.setCreatedAt(now);
            pending.setResendWindowStartedAt(now);
            pending.setResendCount(0);
        } else {
            enforceResendLimit(pending, now);
            pending.setResendCount(pending.getResendCount() + 1);
            if (pending.getResendWindowStartedAt() == null
                    || !pending.getResendWindowStartedAt().plus(Duration.ofHours(1)).isAfter(now)) {
                pending.setResendWindowStartedAt(now);
                pending.setResendCount(1);
            }
        }

        String otp = newOtp();
        emailSender.sendOtp(normalizedEmail, otp, expirationMinutes);
        pending.setName(name.trim());
        pending.setPasswordHash(ENCODER.encode(password));
        pending.setOtpHash(ENCODER.encode(otp));
        pending.setExpiresAt(now.plus(Duration.ofMinutes(expirationMinutes)));
        pending.setLastSentAt(now);
        pending.setAttempts(0);
        pendingRepository.save(pending);
    }

    @Transactional
    public void resendOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (pending == null) {
            return;
        }
        Instant now = clock.instant();
        enforceResendLimit(pending, now);
        String otp = newOtp();
        emailSender.sendOtp(normalizedEmail, otp, expirationMinutes);
        if (pending.getResendWindowStartedAt() == null
                || !pending.getResendWindowStartedAt().plus(Duration.ofHours(1)).isAfter(now)) {
            pending.setResendWindowStartedAt(now);
            pending.setResendCount(0);
        }
        pending.setResendCount(pending.getResendCount() + 1);
        pending.setOtpHash(ENCODER.encode(otp));
        pending.setExpiresAt(now.plus(Duration.ofMinutes(expirationMinutes)));
        pending.setLastSentAt(now);
        pending.setAttempts(0);
        pendingRepository.save(pending);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public User verify(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("The verification code is invalid or expired."));
        Instant now = clock.instant();
        if (pending.getExpiresAt() == null || !pending.getExpiresAt().isAfter(now)) {
            pendingRepository.delete(pending);
            throw new IllegalArgumentException("The verification code is invalid or expired. Request a new code.");
        }
        if (pending.getAttempts() >= maxAttempts) {
            throw new IllegalArgumentException("Too many attempts. Request a new verification code.");
        }
        if (!StringUtils.hasText(otp) || !ENCODER.matches(otp.trim(), pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            pendingRepository.save(pending);
            throw new IllegalArgumentException("The verification code is invalid or expired.");
        }
        if (!userRepository.findAllByEmailIgnoreCase(normalizedEmail).isEmpty()) {
            pendingRepository.delete(pending);
            throw new IllegalArgumentException("Unable to complete sign up. Please sign in or contact support.");
        }

        User user = new User();
        user.setName(pending.getName());
        user.setEmail(normalizedEmail);
        user.setPassword(pending.getPasswordHash());
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);
        pendingRepository.delete(pending);
        return saved;
    }

    private void enforceResendLimit(PendingRegistration pending, Instant now) {
        if (pending.getLastSentAt() != null
                && pending.getLastSentAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new IllegalArgumentException("Please wait before requesting another verification code.");
        }
        boolean windowActive = pending.getResendWindowStartedAt() != null
                && pending.getResendWindowStartedAt().plus(Duration.ofHours(1)).isAfter(now);
        if (windowActive && pending.getResendCount() >= maxResendsPerHour) {
            throw new IllegalArgumentException("Too many code requests. Try again later.");
        }
    }

    private String newOtp() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
