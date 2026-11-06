package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jobportal.model.PasswordResetOutboxJob;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.PasswordResetOutboxJobRepository;
import com.jobportal.repository.PasswordResetTokenRepository;
import com.jobportal.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetRequestIntegrationTests {
    private static final String GENERIC_MESSAGE =
            "{\"message\":\"If an account can receive a password reset link, one will be sent.\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordResetOutboxJobRepository outboxRepository;
    private User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setName("Reset Request Test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPassword("$2a$10$not-a-real-password-hash-for-test-fixture-only");
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.saveAndFlush(user);
    }

    @AfterEach
    void removeFixtures() {
        outboxRepository.deleteAll();
        tokenRepository.deleteAllByUserId(user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    void knownAndUnknownEmailsReceiveSameGenericResponseAndAreDurablyQueuedEncrypted() throws Exception {
        String unknownEmail = UUID.randomUUID() + "@example.test";
        String knownResponse = postRequest(user.getEmail());
        PasswordResetOutboxJob stored = outboxRepository.findAll().stream()
                .filter(job -> job.getJobType() == PasswordResetOutboxJob.Type.RESET_REQUEST)
                .findFirst().orElseThrow();
        assertEquals(PasswordResetOutboxJob.Status.PENDING, stored.getStatus());
        assertTrue(stored.getEncryptedPayload().length > 0);
        assertFalse(new String(stored.getEncryptedPayload(), StandardCharsets.UTF_8).contains(user.getEmail()));
        assertFalse(stored.toString().contains(user.getEmail()));

        String unknownResponse = postRequest(unknownEmail);
        assertEquals(GENERIC_MESSAGE, knownResponse);
        assertEquals(knownResponse, unknownResponse);
        assertFalse(unknownResponse.contains(unknownEmail));
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void requestPayloadStringRepresentationIsRedacted() {
        com.jobportal.controller.UserController.PasswordResetRequestPayload payload =
                new com.jobportal.controller.UserController.PasswordResetRequestPayload(user.getEmail());
        assertFalse(payload.toString().contains(user.getEmail()));
        assertTrue(payload.toString().contains("redacted"));
    }

    private String postRequest(String email) throws Exception {
        return mockMvc.perform(post("/users/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(GENERIC_MESSAGE))
                .andReturn().getResponse().getContentAsString();
    }
}
