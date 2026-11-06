package com.jobportal.services;

public interface PasswordResetEmailSender {
    void sendPasswordResetLink(String email, String resetUrl);
}
