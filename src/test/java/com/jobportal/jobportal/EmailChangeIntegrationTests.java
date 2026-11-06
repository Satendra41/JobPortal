package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.RegistrationEmailSender;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=",
        "GITHUB_CLIENT_SECRET="
})
class EmailChangeIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private com.jobportal.services.UserService userService;
    @MockitoBean private RegistrationEmailSender emailSender;

    @Test
    void emailChangesOnlyAfterVerificationAndCanBeUsedForLogin() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = createUser("current-" + suffix + "@example.test", "verified-password");
        assertTrue(userRepository.findById(user.getId()).orElseThrow().getPassword() != null,
                "the test fixture must have an existing password");
        String requestedEmail = "new-" + suffix + "@example.test";
        var session = sessionFor(user);

        mockMvc.perform(put("/users/{id}", user.getId()).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\",\"email\":\"%s\"}".formatted(requestedEmail)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("verification code")));

        assertEquals(user.getEmail(), userRepository.findById(user.getId()).orElseThrow().getEmail());
        assertTrue(userRepository.findById(user.getId()).orElseThrow().getPassword() != null,
                "the existing password must survive the initial update request");
        String otp = lastSentCode(requestedEmail);
        mockMvc.perform(put("/users/{id}/verify-email", user.getId()).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"%s\"}".formatted(otp)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(requestedEmail)));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(requestedEmail, updated.getEmail());
        assertTrue(updated.isEmailVerified());
        assertTrue(updated.getPassword() != null, "the existing password must be preserved");
        assertEquals(user.getId(), userRepository.findAllByEmailIgnoreCase(requestedEmail).get(0).getId());
        assertTrue(userService.authenticateUser(requestedEmail, "verified-password") != null,
                "the account must remain sign-in capable after verified email change");
    }

    @Test
    void duplicateEmailAndAnotherUsersAccountUpdatesAreRejected() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User first = createUser("first-" + suffix + "@example.test", "password-one");
        User second = createUser("second-" + suffix + "@example.test", "password-two");

        mockMvc.perform(put("/users/{id}", first.getId()).with(csrf()).session(sessionFor(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"email\":\"%s\"}".formatted(second.getEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already associated")));
        assertEquals(first.getEmail(), userRepository.findById(first.getId()).orElseThrow().getEmail());

        mockMvc.perform(put("/users/{id}", second.getId()).with(csrf()).session(sessionFor(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed by another user\",\"email\":\"attacker@example.test\"}"))
                .andExpect(status().isForbidden());
        assertEquals(second.getEmail(), userRepository.findById(second.getId()).orElseThrow().getEmail());
    }

    @Test
    void incorrectVerificationCodeLeavesCurrentEmailInPlace() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = createUser("unchanged-" + suffix + "@example.test", "verified-password");
        String originalEmail = user.getEmail();
        var session = sessionFor(user);
        String requestedEmail = "unverified-" + suffix + "@example.test";

        mockMvc.perform(put("/users/{id}", user.getId()).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"%s\"}".formatted(requestedEmail)))
                .andExpect(status().isAccepted());
        mockMvc.perform(put("/users/{id}/verify-email", user.getId()).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"incorrect-code\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(originalEmail, userRepository.findById(user.getId()).orElseThrow().getEmail());
    }

    private User createUser(String email, String password) {
        User user = new User();
        user.setName("Email Update User");
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(UserRole.JOB_SEEKER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private org.springframework.mock.web.MockHttpSession sessionFor(User user) {
        var session = new org.springframework.mock.web.MockHttpSession();
        User sessionUser = new User();
        sessionUser.setId(user.getId());
        sessionUser.setName(user.getName());
        sessionUser.setEmail(user.getEmail());
        sessionUser.setRole(user.getRole());
        session.setAttribute("loggedInUser", sessionUser);
        return session;
    }

    private String lastSentCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(1)).sendOtp(eq(email), code.capture(), eq(5));
        List<String> captured = code.getAllValues();
        return captured.get(captured.size() - 1);
    }
}
