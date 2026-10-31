package com.jobportal.services;

/** Safe diagnostic categories for social sign-in failures. */
public enum OAuthFailureReason {
    PROVIDER_OAUTH_FAILURE,
    GOOGLE_EMAIL_MISSING_OR_UNVERIFIED,
    GITHUB_VERIFIED_PRIMARY_EMAIL_MISSING,
    EXISTING_EMAIL_ACCOUNT_COLLISION,
    LINKED_ACCOUNT_DISABLED,
    OTHER_ACCOUNT_PROVISIONING_FAILURE
}
