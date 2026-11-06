package com.jobportal.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "password_reset_email_outbox", indexes = @Index(
        name = "ix_password_reset_email_outbox_claim",
        columnList = "status, next_attempt_at, locked_until, created_at"),
        uniqueConstraints = @UniqueConstraint(name = "ux_password_reset_email_outbox_token", columnNames = "token_id"))
public class PasswordResetOutboxJob {
    public enum Type { RESET_REQUEST, RESET_EMAIL }
    public enum Status { PENDING, PROCESSING, SENT, COMPLETED, FAILED }

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 24)
    private Type jobType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "encrypted_payload", columnDefinition = "BLOB")
    private byte[] encryptedPayload;

    @Column(name = "nonce", columnDefinition = "BINARY(12)")
    private byte[] nonce;

    @Column(name = "encryption_key_id", nullable = false, length = 64)
    private String encryptionKeyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Type getJobType() { return jobType; }
    public void setJobType(Type jobType) { this.jobType = jobType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
    public byte[] getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(byte[] encryptedPayload) { this.encryptedPayload = encryptedPayload; }
    public byte[] getNonce() { return nonce; }
    public void setNonce(byte[] nonce) { this.nonce = nonce; }
    public String getEncryptionKeyId() { return encryptionKeyId; }
    public void setEncryptionKeyId(String encryptionKeyId) { this.encryptionKeyId = encryptionKeyId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    @Override
    public String toString() {
        return "PasswordResetOutboxJob[id=" + id + ", jobType=" + jobType + ", status=" + status
                + ", encryptedPayload=<redacted>]";
    }
}
