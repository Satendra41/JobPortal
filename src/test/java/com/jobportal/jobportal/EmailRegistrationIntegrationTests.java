package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.PendingRegistration;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.PendingRegistrationRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.RegistrationEmailSender;
import com.jobportal.services.RegistrationVerificationService;
import com.jobportal.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=",
        "GITHUB_CLIENT_SECRET="
})
class EmailRegistrationIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private RegistrationVerificationService registrationService;
    @Autowired private PendingRegistrationRepository pendingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
    @MockitoBean private RegistrationEmailSender emailSender;

    private String email;

    @BeforeEach
    void setUp() {
        email = "otp-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void signupStartsPendingRegistrationAndOnlyCreatesAccountAfterVerification() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("Sign Up")));

        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "OTP Test User")
                        .param("email", email)
                        .param("password", "strong-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl()
                        .startsWith("/verify-email?email=" + email.substring(0, email.indexOf('@')) + "%40")));

        assertTrue(userRepository.findAllByEmailIgnoreCase(email).isEmpty());
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(email).orElseThrow();
        assertTrue(new BCryptPasswordEncoder().matches("strong-test-password", pending.getPasswordHash()));
        assertFalse(pending.getPasswordHash().equals("strong-test-password"));
        assertTrue(pending.getOtpHash().matches("\\$2[aby]\\$.*"));
        String code = lastSentCode();

        User verified = registrationService.verify(email, code);
        assertTrue(verified.isEmailVerified());
        assertEquals(UserRole.JOB_SEEKER, verified.getRole());
        assertTrue(userRepository.findAllByEmailIgnoreCase(email).get(0).isEmailVerified());
        assertTrue(pendingRepository.findFirstByEmailIgnoreCase(email).isEmpty());
        assertNull(userService.authenticateUser(email, "wrong-password"));
        assertNotNull(userService.authenticateUser(email, "strong-test-password"));
        assertThrows(IllegalArgumentException.class, () -> registrationService.verify(email, code));
    }

    @Test
    void wrongOtpConsumesAttemptsAndAttemptLimitIsEnforced() {
        begin();
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> registrationService.verify(email, "000000"));
        }
        assertEquals(5, pendingRepository.findFirstByEmailIgnoreCase(email).orElseThrow().getAttempts());
        assertThrows(IllegalArgumentException.class, () -> registrationService.verify(email, "123456"));
        assertTrue(userRepository.findAllByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void expiredOtpIsRejectedAndPendingRegistrationCanBeRetried() {
        begin();
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(email).orElseThrow();
        pending.setExpiresAt(Instant.now().minusSeconds(1));
        pendingRepository.saveAndFlush(pending);

        assertThrows(IllegalArgumentException.class, () -> registrationService.verify(email, lastSentCode()));
        assertTrue(userRepository.findAllByEmailIgnoreCase(email).isEmpty());
        assertTrue(pendingRepository.findFirstByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void resendIsCooldownLimitedAndOldCodeCannotBeReused() {
        begin();
        String oldCode = lastSentCode();
        assertThrows(IllegalArgumentException.class, () -> registrationService.resendOtp(email));

        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(email).orElseThrow();
        pending.setLastSentAt(Instant.now().minusSeconds(61));
        pendingRepository.saveAndFlush(pending);
        registrationService.resendOtp(email);
        String newCode = lastSentCode();
        assertNotNull(newCode);
        assertThrows(IllegalArgumentException.class, () -> registrationService.verify(email, oldCode));
        assertNotNull(registrationService.verify(email, newCode));
    }

    @Test
    void resendHourlyLimitIsEnforced() {
        begin();
        PendingRegistration pending = pendingRepository.findFirstByEmailIgnoreCase(email).orElseThrow();
        pending.setLastSentAt(Instant.now().minusSeconds(61));
        pending.setResendCount(5);
        pending.setResendWindowStartedAt(Instant.now().minusSeconds(30));
        pendingRepository.saveAndFlush(pending);
        assertThrows(IllegalArgumentException.class, () -> registrationService.resendOtp(email));
    }

    @Test
    void unverifiedAccountsCannotSignInAndExistingVerifiedAccountsCan() {
        begin();
        assertNull(userService.authenticateUser(email, "strong-test-password"));

        User existing = new User();
        existing.setName("Existing verified user");
        existing.setEmail("existing-" + email);
        existing.setPassword(new BCryptPasswordEncoder().encode("existing-password"));
        existing.setRole(UserRole.JOB_SEEKER);
        existing.setEnabled(true);
        existing.setEmailVerified(true);
        userRepository.saveAndFlush(existing);
        assertNotNull(userService.authenticateUser(existing.getEmail(), "existing-password"));
    }

    private void begin() {
        registrationService.startRegistration("OTP Test User", email, "strong-test-password");
    }

    private String lastSentCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(sentCount())).sendOtp(eq(email), code.capture(), eq(5));
        List<String> allCodes = code.getAllValues();
        return allCodes.get(allCodes.size() - 1);
    }

    private int sentCount() {
        return org.mockito.Mockito.mockingDetails(emailSender).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("sendOtp")).toList().size();
    }
}
