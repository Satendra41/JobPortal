package com.jobportal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
public class AdminTestAccountInitializer implements ApplicationRunner {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private final String testEmail;
    private final String testPassword;

    private final UserRepository userRepository;

    public AdminTestAccountInitializer(UserRepository userRepository,
            @Value("${app.demo-accounts.admin-email:}") String testEmail,
            @Value("${app.demo-accounts.admin-password:}") String testPassword) {
        this.userRepository = userRepository;
        this.testEmail = testEmail;
        this.testPassword = testPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (testEmail.isBlank() || testPassword.isBlank() || !userRepository.findAllByEmail(testEmail).isEmpty()) {
            return;
        }

        User admin = new User();
        admin.setName("JobPortal Admin");
        admin.setEmail(testEmail);
        admin.setPassword(PASSWORD_ENCODER.encode(testPassword));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }
}
