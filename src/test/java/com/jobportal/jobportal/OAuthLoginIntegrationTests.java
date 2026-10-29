package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.OAuthAccountProvisioningException;
import com.jobportal.services.OAuthAccountService;
import com.jobportal.services.UserService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OAuthLoginIntegrationTests {

    private static final String GOOGLE_CLIENT_ID = UUID.randomUUID().toString();
    private static final String GOOGLE_CLIENT_SECRET = UUID.randomUUID().toString();
    private static final String GITHUB_CLIENT_ID = UUID.randomUUID().toString();
    private static final String GITHUB_CLIENT_SECRET = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuthAccountRepository oauthAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountService oauthAccountService;

    @Autowired
    private UserService userService;

    @DynamicPropertySource
    static void oauthClientCredentials(DynamicPropertyRegistry registry) {
        registry.add("GOOGLE_CLIENT_ID", () -> GOOGLE_CLIENT_ID);
        registry.add("GOOGLE_CLIENT_SECRET", () -> GOOGLE_CLIENT_SECRET);
        registry.add("GITHUB_CLIENT_ID", () -> GITHUB_CLIENT_ID);
        registry.add("GITHUB_CLIENT_SECRET", () -> GITHUB_CLIENT_SECRET);
    }

    @Test
    void loginAndRegistrationPagesShowProviderLinksAndKeepEmailForms() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("/oauth2/authorization/google"));
                    assertTrue(html.contains("/oauth2/authorization/github"));
                    assertTrue(html.contains("OR CONTINUE WITH EMAIL"));
                    assertTrue(html.contains("name=\"_csrf\""));
                });

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("/oauth2/authorization/google"));
                    assertTrue(html.contains("/oauth2/authorization/github"));
                    assertTrue(html.contains("action=\"/register\""));
                });
    }

    @Test
    void providerEntryPointsRedirectToTheExpectedOAuthAuthorizationServers() throws Exception {
        assertNotNull(clientRegistrationRepository.findByRegistrationId("google"));
        assertNotNull(clientRegistrationRepository.findByRegistrationId("github"));

        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl()
                        .startsWith("https://accounts.google.com/")));

        mockMvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl()
                        .startsWith("https://github.com/login/oauth/authorize")));
    }

    @Test
    void oauthAccountUsesStableProviderIdentityAndNeverOverwritesLocalRoleOrEmail() {
        User created = oauthAccountService.authenticateOrCreate(
                "google", "google-subject-1", "First.Email@example.com", true, "OAuth User");
        assertEquals(UserRole.JOB_SEEKER, created.getRole());
        assertEquals("first.email@example.com", created.getEmail());
        assertNull(created.getPassword());
        assertEquals(1, oauthAccountRepository.count());

        created.setRole(UserRole.HR);
        userRepository.saveAndFlush(created);
        User returning = oauthAccountService.authenticateOrCreate(
                "google", "google-subject-1", "changed@example.com", true, "Changed Name");

        assertEquals(created.getId(), returning.getId());
        assertEquals(UserRole.HR, returning.getRole());
        assertEquals("first.email@example.com", returning.getEmail());
        assertEquals("OAuth User", returning.getName());
        assertEquals(1, oauthAccountRepository.count());
    }

    @Test
    void emailCollisionRequiresExistingAccountAuthenticationInsteadOfAutomaticLinking() {
        User localUser = new User();
        localUser.setName("Local HR");
        localUser.setEmail("already-registered@example.com");
        localUser.setPassword("existing-password-hash");
        localUser.setRole(UserRole.HR);
        localUser = userRepository.saveAndFlush(localUser);

        User existing = localUser;
        assertThrows(OAuthAccountProvisioningException.class, () -> oauthAccountService.authenticateOrCreate(
                "github", "github-user-22", "ALREADY-REGISTERED@example.com", true, "Provider Name"));

        User unchanged = userRepository.findById(existing.getId()).orElseThrow();
        assertEquals(UserRole.HR, unchanged.getRole());
        assertEquals("existing-password-hash", unchanged.getPassword());
        assertEquals(0, oauthAccountRepository.count());
    }

    @Test
    void missingOrUnverifiedEmailAndDisabledAccountsCannotAuthenticate() {
        assertThrows(OAuthAccountProvisioningException.class, () -> oauthAccountService.authenticateOrCreate(
                "github", "github-no-email", null, false, "No Email"));
        assertTrue(userRepository.findAllByEmailIgnoreCase("verified@example.com").isEmpty());

        User created = oauthAccountService.authenticateOrCreate(
                "github", "github-disabled", "verified@example.com", true, "Disabled Later");
        created.setEnabled(false);
        userRepository.saveAndFlush(created);

        assertThrows(OAuthAccountProvisioningException.class, () -> oauthAccountService.authenticateOrCreate(
                "github", "github-disabled", "verified@example.com", true, "Disabled Later"));
        assertEquals(1, oauthAccountRepository.count());
    }

    @Test
    void deletingAnAccountAlsoRemovesItsOAuthIdentity() {
        User user = oauthAccountService.authenticateOrCreate(
                "google", "google-delete-user", "delete@example.com", true, "Delete Me");

        assertTrue(userService.deleteUser(user.getId()));
        assertTrue(oauthAccountRepository.findByProviderAndProviderUserId("google", "google-delete-user").isEmpty());
    }
}
