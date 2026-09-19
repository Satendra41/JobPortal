package com.jobportal.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

@Component
public class HrTestAccountInitializer implements ApplicationRunner {

    private static final String TEST_EMAIL = "hr@jobportal.com";
    private static final String TEST_PASSWORD = "hr123456";

    private final UserRepository userRepository;

    public HrTestAccountInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.findAllByEmail(TEST_EMAIL).isEmpty()) {
            return;
        }

        User hrUser = new User();
        hrUser.setName("JobPortal HR");
        hrUser.setEmail(TEST_EMAIL);
        hrUser.setPassword(TEST_PASSWORD);
        hrUser.setRole(UserRole.HR);
        userRepository.save(hrUser);
    }
}
