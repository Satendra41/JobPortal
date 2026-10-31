package com.jobportal.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "pending_registrations", indexes = @Index(name = "idx_pending_registration_email", columnList = "email"))
public class PendingRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    private String passwordHash;
    private String otpHash;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant lastSentAt;
    private Instant resendWindowStartedAt;
    private int attempts;
    private int resendCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public Instant getResendWindowStartedAt() { return resendWindowStartedAt; }
    public void setResendWindowStartedAt(Instant resendWindowStartedAt) { this.resendWindowStartedAt = resendWindowStartedAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getResendCount() { return resendCount; }
    public void setResendCount(int resendCount) { this.resendCount = resendCount; }
}
