package com.jobportal.services;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!prod")
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public SmtpPasswordResetEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    @Override
    public void sendPasswordResetLink(String email, String resetUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!StringUtils.hasText(from) || mailSender == null) throw new IllegalStateException("Email delivery failed.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your JAGAQ password");
        message.setText("A password reset was requested for your JAGAQ account. Use this link to choose a new password:\n\n"
                + resetUrl + "\n\nIf you did not request this, ignore this email.");
        try {
            mailSender.send(message);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Email delivery failed.");
        }
    }
}
