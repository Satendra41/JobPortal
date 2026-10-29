package com.jobportal.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> EMAIL_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient;

    public GitHubOAuth2UserService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User githubUser = delegate.loadUser(userRequest);
        String verifiedEmail = loadVerifiedPrimaryEmail(userRequest);
        Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
        attributes.put("email", verifiedEmail);
        attributes.put("email_verified", true);
        return new DefaultOAuth2User(
                githubUser.getAuthorities(),
                attributes,
                "id");
    }

    private String loadVerifiedPrimaryEmail(OAuth2UserRequest userRequest) {
        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userRequest.getAccessToken().getTokenValue())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .body(EMAIL_RESPONSE_TYPE);

        if (emails != null) {
            return emails.stream()
                    .filter(email -> Boolean.TRUE.equals(email.get("primary")))
                    .filter(email -> Boolean.TRUE.equals(email.get("verified")))
                    .map(email -> email.get("email"))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .findFirst()
                    .orElseThrow(this::missingVerifiedEmail);
        }
        throw missingVerifiedEmail();
    }

    private OAuth2AuthenticationException missingVerifiedEmail() {
        return new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info"),
                "A verified primary email address is required for GitHub sign-in.");
    }
}
