package com.jobportal.jobportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.jobportal.config.OAuthClientRegistrationRepository;

class OAuthClientRegistrationRepositoryTests {

    @Test
    void missingCredentialsLeaveSocialLoginUnavailable() {
        OAuthClientRegistrationRepository registrations =
                new OAuthClientRegistrationRepository(new MockEnvironment());

        assertFalse(registrations.isConfigured("google"));
        assertFalse(registrations.isConfigured("github"));
        assertNull(registrations.findByRegistrationId("google"));
    }

    @Test
    void incompleteProviderCredentialsDoNotPreventOtherProviderConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GOOGLE_CLIENT_ID", UUID.randomUUID().toString())
                .withProperty("GITHUB_CLIENT_ID", UUID.randomUUID().toString())
                .withProperty("GITHUB_CLIENT_SECRET", UUID.randomUUID().toString());
        OAuthClientRegistrationRepository registrations =
                new OAuthClientRegistrationRepository(environment);

        assertFalse(registrations.isConfigured("google"));
        assertTrue(registrations.isConfigured("github"));
        assertEquals("https://api.github.com/user",
                registrations.findByRegistrationId("github").getProviderDetails().getUserInfoEndpoint().getUri());
        assertEquals(Set.of("user:email"), registrations.findByRegistrationId("github").getScopes());
    }

    @Test
    void completeGoogleCredentialsUseStandardCallbackAndMinimumScopes() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GOOGLE_CLIENT_ID", UUID.randomUUID().toString())
                .withProperty("GOOGLE_CLIENT_SECRET", UUID.randomUUID().toString());
        OAuthClientRegistrationRepository registrations =
                new OAuthClientRegistrationRepository(environment);

        var google = registrations.findByRegistrationId("google");
        assertTrue(registrations.isConfigured("google"));
        assertEquals("{baseUrl}/login/oauth2/code/{registrationId}", google.getRedirectUri());
        assertEquals(Set.of("openid", "profile", "email"), google.getScopes());
    }
}
