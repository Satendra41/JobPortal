package com.jobportal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "oauth_accounts", uniqueConstraints = @UniqueConstraint(
        name = "uk_oauth_account_provider_user",
        columnNames = { "provider", "provider_user_id" }))
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected OAuthAccount() {
    }

    public OAuthAccount(String provider, String providerUserId, User user) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
