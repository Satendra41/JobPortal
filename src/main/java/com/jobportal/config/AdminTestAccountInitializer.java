package com.jobportal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

@Component
public class AdminTestAccountInitializer implements ApplicationRunner {

    private static final String TEST_EMAIL = "admin@jobportal.com";
    private static final String TEST_PASSWORD = "admin123456";

    private final UserRepository userRepository;

    public AdminTestAccountInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.findAllByEmail(TEST_EMAIL).isEmpty()) {
            return;
        }

        User admin = new User();
        admin.setName("JobPortal Admin");
        admin.setEmail(TEST_EMAIL);
        admin.setPassword(TEST_PASSWORD);
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }
}