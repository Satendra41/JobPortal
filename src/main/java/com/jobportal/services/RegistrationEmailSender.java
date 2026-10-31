package com.jobportal.services;

public interface RegistrationEmailSender {
    void sendOtp(String email, String otp, int expirationMinutes);
}
