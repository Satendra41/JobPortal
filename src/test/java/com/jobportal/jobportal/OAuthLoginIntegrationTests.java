package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.services.OAuthAccountProvisioningException;
import com.jobportal.services.OAuthAccountService;
import com.jobportal.services.UserService;
import com.jobportal.services.OAuthLoginSuccessHandler;
import com.jobportal.services.OAuthLoginFailureHandler;
import com.jobportal.services.OAuthFailureReason;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Autowired
    private OAuthLoginSuccessHandler oauthLoginSuccessHandler;

    @Autowired
    private OAuthLoginFailureHandler oauthLoginFailureHandler;

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
                    assertTrue(html.contains("data-password-toggle"));
                });

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("/oauth2/authorization/google"));
                    assertTrue(html.contains("/oauth2/authorization/github"));
                    assertTrue(html.contains("action=\"/register\""));
                    assertTrue(html.contains("data-password-toggle"));
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
    void googleSuccessHandlerCreatesVerifiedAccountAndPreservesSessionLoginFlow() throws Exception {
        String email = "google-handler-" + UUID.randomUUID() + "@example.com";
        var principal = new DefaultOAuth2User(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
                java.util.Map.of("sub", "google-subject-" + UUID.randomUUID(),
                        "email", email, "email_verified", true, "name", "Google User"),
                "sub");
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        var request = new MockHttpServletRequest();
        request.getSession(true);
        var response = new MockHttpServletResponse();

        oauthLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

        User sessionUser = (User) request.getSession(false).getAttribute("loggedInUser");
        assertEquals(email, sessionUser.getEmail());
        assertEquals(UserRole.JOB_SEEKER, sessionUser.getRole());
        assertTrue(sessionUser.isEmailVerified());
        assertNull(sessionUser.getPassword());
        assertEquals("/dashboard", response.getRedirectedUrl());
        assertEquals(1, oauthAccountRepository.count());
    }

    @Test
    void emailCollisionRequiresExistingAccountAuthenticationInsteadOfAutomaticLinking() throws Exception {
        User localUser = new User();
        localUser.setName("Local HR");
        localUser.setEmail("already-registered@example.com");
        localUser.setPassword("existing-password-hash");
        localUser.setRole(UserRole.HR);
        localUser = userRepository.saveAndFlush(localUser);

        User existing = localUser;
        String subject = "google-collision-" + UUID.randomUUID();
        ListAppender<ILoggingEvent> logs = attachLogs(OAuthLoginSuccessHandler.class);
        var request = new MockHttpServletRequest();
        request.getSession(true);
        var response = new MockHttpServletResponse();
        oauthLoginSuccessHandler.onAuthenticationSuccess(request, response,
                googleAuthentication(subject, Map.of("sub", subject, "email", "ALREADY-REGISTERED@example.com",
                        "email_verified", true, "name", "Provider Name")));

        User unchanged = userRepository.findById(existing.getId()).orElseThrow();
        assertEquals(UserRole.HR, unchanged.getRole());
        assertEquals("existing-password-hash", unchanged.getPassword());
        assertEquals(0, oauthAccountRepository.count());
        assertEquals("/login?error=social-login", response.getRedirectedUrl());
        assertContainsReason(logs, OAuthFailureReason.EXISTING_EMAIL_ACCOUNT_COLLISION);
        assertNoSensitiveData(logs, "already-registered@example.com", "existing-password-hash");
        detachLogs(OAuthLoginSuccessHandler.class, logs);
    }

    @Test
    void missingAndUnverifiedGoogleClaimsAreRejectedWithSafeReasonCode() throws Exception {
        ListAppender<ILoggingEvent> logs = attachLogs(OAuthLoginSuccessHandler.class);
        String missingSubject = "google-missing-" + UUID.randomUUID();
        String unverifiedSubject = "google-unverified-" + UUID.randomUUID();
        var missingResponse = invokeGoogle(missingSubject,
                Map.of("sub", missingSubject, "email_verified", true, "name", "Sensitive Name"));
        var unverifiedResponse = invokeGoogle(unverifiedSubject,
                Map.of("sub", unverifiedSubject, "email", "sensitive-address@example.com",
                        "email_verified", false, "name", "Sensitive Name"));

        assertEquals("/login?error=social-login", missingResponse.getRedirectedUrl());
        assertEquals("/login?error=social-login", unverifiedResponse.getRedirectedUrl());
        assertTrue(userRepository.findAllByEmailIgnoreCase("sensitive-address@example.com").isEmpty());
        assertEquals(2, logs.list.size());
        logs.list.forEach(event -> assertTrue(event.getFormattedMessage()
                .contains(OAuthFailureReason.GOOGLE_EMAIL_MISSING_OR_UNVERIFIED.name())));
        assertNoSensitiveData(logs, "sensitive-address@example.com", "Sensitive Name", "token-secret");
        detachLogs(OAuthLoginSuccessHandler.class, logs);
    }

    @Test
    void providerAndGithubEmailFailuresUseSafeCodesAndKeepGenericBrowserMessage() throws Exception {
        ListAppender<ILoggingEvent> logs = attachLogs(OAuthLoginFailureHandler.class);
        var request = new MockHttpServletRequest();
        var githubResponse = new MockHttpServletResponse();
        oauthLoginFailureHandler.onAuthenticationFailure(request, githubResponse,
                new OAuth2AuthenticationException(new OAuth2Error("github_verified_primary_email_missing"),
                        "private@example.com token-secret"));
        var providerResponse = new MockHttpServletResponse();
        oauthLoginFailureHandler.onAuthenticationFailure(request, providerResponse,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"),
                        "private@example.com authorization-code-secret"));

        assertEquals("/login?error=social-login", githubResponse.getRedirectedUrl());
        assertEquals("/login?error=social-login", providerResponse.getRedirectedUrl());
        assertTrue(logs.list.get(0).getFormattedMessage()
                .contains(OAuthFailureReason.GITHUB_VERIFIED_PRIMARY_EMAIL_MISSING.name()));
        assertTrue(logs.list.get(1).getFormattedMessage()
                .contains(OAuthFailureReason.PROVIDER_OAUTH_FAILURE.name()));
        assertNoSensitiveData(logs, "private@example.com", "token-secret", "authorization-code-secret");
        detachLogs(OAuthLoginFailureHandler.class, logs);

        mockMvc.perform(get("/login").param("error", "social-login"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertTrue(html.contains("Social sign-in could not be completed"));
                    assertFalse(html.contains("GITHUB_VERIFIED_PRIMARY_EMAIL_MISSING"));
                    assertFalse(html.contains("EXISTING_EMAIL_ACCOUNT_COLLISION"));
                });
    }

    @Test
    void unexpectedProvisioningFailureLogsOnlyReasonAndExceptionClass() throws Exception {
        String email = "private-provisioning-address@example.com";
        String subject = "google-provisioning-failure-" + UUID.randomUUID();
        OAuthAccountService failingService = mock(OAuthAccountService.class);
        when(failingService.authenticateOrCreate("google", subject, email, true, "Private Name"))
                .thenThrow(new IllegalStateException("token-secret password-secret " + email));
        OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(failingService);
        ListAppender<ILoggingEvent> logs = attachLogs(OAuthLoginSuccessHandler.class);
        var request = new MockHttpServletRequest();
        request.getSession(true);
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                googleAuthentication(subject, Map.of("sub", subject, "email", email,
                        "email_verified", true, "name", "Private Name")));

        assertEquals("/login?error=social-login", response.getRedirectedUrl());
        assertTrue(logs.list.get(0).getFormattedMessage()
                .contains(OAuthFailureReason.OTHER_ACCOUNT_PROVISIONING_FAILURE.name()));
        assertTrue(logs.list.get(0).getFormattedMessage().contains("exceptionClass=IllegalStateException"));
        assertNoSensitiveData(logs, email, "Private Name", "token-secret", "password-secret");
        detachLogs(OAuthLoginSuccessHandler.class, logs);
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

        OAuthAccountProvisioningException disabledException = assertThrows(
                OAuthAccountProvisioningException.class,
                () -> oauthAccountService.authenticateOrCreate(
                        "github", "github-disabled", "verified@example.com", true, "Disabled Later"));
        assertEquals(OAuthFailureReason.LINKED_ACCOUNT_DISABLED, disabledException.getReason());
        assertEquals(1, oauthAccountRepository.count());
    }

    @Test
    void deletingAnAccountAlsoRemovesItsOAuthIdentity() {
        User user = oauthAccountService.authenticateOrCreate(
                "google", "google-delete-user", "delete@example.com", true, "Delete Me");

        assertTrue(userService.deleteUser(user.getId()));
        assertTrue(oauthAccountRepository.findByProviderAndProviderUserId("google", "google-delete-user").isEmpty());
    }

    private MockHttpServletResponse invokeGoogle(String subject, Map<String, Object> attributes) throws Exception {
        var request = new MockHttpServletRequest();
        request.getSession(true);
        var response = new MockHttpServletResponse();
        oauthLoginSuccessHandler.onAuthenticationSuccess(request, response, googleAuthentication(subject, attributes));
        return response;
    }

    private OAuth2AuthenticationToken googleAuthentication(String subject, Map<String, Object> attributes) {
        var principal = new DefaultOAuth2User(
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private ListAppender<ILoggingEvent> attachLogs(Class<?> source) {
        Logger logger = (Logger) LoggerFactory.getLogger(source);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogs(Class<?> source, ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(source)).detachAppender(appender);
        appender.stop();
    }

    private void assertContainsReason(ListAppender<ILoggingEvent> logs, OAuthFailureReason reason) {
        assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(reason.name())));
    }

    private void assertNoSensitiveData(ListAppender<ILoggingEvent> logs, String... sensitiveValues) {
        String diagnostic = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        for (String value : sensitiveValues) {
            assertFalse(diagnostic.contains(value));
        }
    }
}
