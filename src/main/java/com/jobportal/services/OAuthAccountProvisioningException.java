package com.jobportal.services;

public class OAuthAccountProvisioningException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OAuthFailureReason reason;

    public OAuthAccountProvisioningException(OAuthFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OAuthFailureReason getReason() {
        return reason;
    }
}
