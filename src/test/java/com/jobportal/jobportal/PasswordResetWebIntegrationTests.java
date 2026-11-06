package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.PasswordResetService;
import com.jobportal.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetWebIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordResetService resetService;
    @Autowired private UserService userService;
    private User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setName("Reset Web Test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("old-password-123");
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userService.save(user);
    }

    @AfterEach
    void cleanup() {
        tokenRepository.deleteAllByUserId(user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    void forgotPasswordPageRendersWithoutAccountDisclosure() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("forgot-password"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("If it can receive a reset link")));
    }

    @Test
    void resetGetValidatesWithoutConsumingAndSetsNoStoreHeaders() throws Exception {
        String rawToken = resetService.issueResetToken(user.getId()).orElseThrow().rawToken();

        mockMvc.perform(get("/reset-password").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\"")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));

        assertTrue(resetService.isTokenRedeemable(rawToken));
    }

    @Test
    void invalidTokenRendersSafeUnavailableState() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", "malformed"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("invalidLink", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("invalid, expired, or has already been used")));
    }

    @Test
    void resetPostRequiresCsrf() throws Exception {
        String rawToken = resetService.issueResetToken(user.getId()).orElseThrow().rawToken();
        mockMvc.perform(post("/reset-password").param("token", rawToken)
                        .param("password", "new-password-123").param("confirmPassword", "new-password-123"))
                .andExpect(status().isForbidden());
        assertTrue(resetService.isTokenRedeemable(rawToken));
    }

    @Test
    void mismatchAndPolicyFailureLeaveTokenUsable() throws Exception {
        String rawToken = resetService.issueResetToken(user.getId()).orElseThrow().rawToken();
        mockMvc.perform(post("/reset-password").with(csrf()).param("token", rawToken)
                        .param("password", "new-password-123").param("confirmPassword", "different-password"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "The passwords do not match."));
        assertTrue(resetService.isTokenRedeemable(rawToken));

        mockMvc.perform(post("/reset-password").with(csrf()).param("token", rawToken)
                        .param("password", "short").param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
        assertTrue(resetService.isTokenRedeemable(rawToken));
        assertNotNull(userService.authenticateUser(user.getEmail(), "old-password-123"));
    }

    @Test
    void successfulResetChangesPasswordConsumesTokenAndInvalidatesCurrentSession() throws Exception {
        String rawToken = resetService.issueResetToken(user.getId()).orElseThrow().rawToken();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", user);
        int oldVersion = user.getCredentialVersion();

        mockMvc.perform(post("/reset-password").with(csrf()).session(session)
                        .param("token", rawToken).param("password", "brand-new-password-456")
                        .param("confirmPassword", "brand-new-password-456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/login?passwordReset=success"));

        assertFalse(resetService.isTokenRedeemable(rawToken));
        assertNotNull(userService.authenticateUser(user.getEmail(), "brand-new-password-456"));
        assertTrue(userService.authenticateUser(user.getEmail(), "old-password-123") == null);
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(oldVersion + 1, reloaded.getCredentialVersion());
        assertThrowsInvalidSession(session);
    }

    @Test
    void sessionsFromOtherBrowsersAreRejectedOnTheirNextRequestAfterReset() throws Exception {
        String rawToken = resetService.issueResetToken(user.getId()).orElseThrow().rawToken();
        MockHttpSession olderSession = new MockHttpSession();
        olderSession.setAttribute("loggedInUser", user);

        mockMvc.perform(post("/reset-password").with(csrf()).param("token", rawToken)
                        .param("password", "another-new-password-789")
                        .param("confirmPassword", "another-new-password-789"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/dashboard").session(olderSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
        assertThrowsInvalidSession(olderSession);
    }

    private void assertThrowsInvalidSession(MockHttpSession session) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> session.getAttribute("loggedInUser"));
    }
}
