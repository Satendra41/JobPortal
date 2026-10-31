package com.jobportal.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.ObjectProvider;

@Component
public class SmtpRegistrationEmailSender implements RegistrationEmailSender {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public SmtpRegistrationEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    @Override
    public void sendOtp(String email, String otp, int expirationMinutes) {
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("Email sender is not configured.");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email sender is not configured.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your JAGAQ sign-up verification code");
        message.setText("Your verification code is " + otp + ". It expires in " + expirationMinutes
                + " minutes. If you did not request this, ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new IllegalStateException("Email delivery failed.", ex);
        }
    }
}
