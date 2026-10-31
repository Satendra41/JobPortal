package com.jobportal.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OAuthClientRegistrationRepository implements ClientRegistrationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthClientRegistrationRepository.class);

    private final Map<String, ClientRegistration> registrations;

    public OAuthClientRegistrationRepository(Environment environment) {
        Map<String, ClientRegistration> configuredRegistrations = new LinkedHashMap<>();
        addGoogleRegistration(environment, configuredRegistrations);
        addGitHubRegistration(environment, configuredRegistrations);
        this.registrations = Map.copyOf(configuredRegistrations);
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    public boolean isConfigured(String registrationId) {
        return registrations.containsKey(registrationId);
    }

    private void addGoogleRegistration(Environment environment, Map<String, ClientRegistration> configured) {
        String clientId = environment.getProperty("GOOGLE_CLIENT_ID", "");
        String clientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET", "");
        if (hasCredentials(clientId, clientSecret)) {
            configured.put("google", CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .scope("openid", "profile", "email")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .build());
        } else if (hasPartialCredentials(clientId, clientSecret)) {
            LOGGER.warn("Google OAuth is disabled because both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required.");
        }
    }

    private void addGitHubRegistration(Environment environment, Map<String, ClientRegistration> configured) {
        String clientId = environment.getProperty("GITHUB_CLIENT_ID", "");
        String clientSecret = environment.getProperty("GITHUB_CLIENT_SECRET", "");
        if (hasCredentials(clientId, clientSecret)) {
            configured.put("github", CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .scope("user:email")
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .userInfoUri("https://api.github.com/user")
                    .userNameAttributeName("id")
                    .build());
        } else if (hasPartialCredentials(clientId, clientSecret)) {
            LOGGER.warn("GitHub OAuth is disabled because both GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET are required.");
        }
    }

    private boolean hasCredentials(String clientId, String clientSecret) {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    private boolean hasPartialCredentials(String clientId, String clientSecret) {
        return StringUtils.hasText(clientId) || StringUtils.hasText(clientSecret);
    }
}
