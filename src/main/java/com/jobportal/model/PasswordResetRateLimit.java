package com.jobportal.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "password_reset_rate_limits", uniqueConstraints = @UniqueConstraint(
        name = "ux_password_reset_rate_limits_scope_hash", columnNames = { "limit_scope", "identifier_hash" }))
public class PasswordResetRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "limit_scope", nullable = false, length = 16)
    private String limitScope;

    @Column(name = "identifier_hash", nullable = false, length = 64)
    private String identifierHash;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    public Long getId() { return id; }
    public String getLimitScope() { return limitScope; }
    public String getIdentifierHash() { return identifierHash; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public int getRequestCount() { return requestCount; }
}
