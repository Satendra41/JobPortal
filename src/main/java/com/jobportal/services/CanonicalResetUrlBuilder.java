package com.jobportal.services;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class CanonicalResetUrlBuilder {
    private final String baseUrl;

    @Autowired
    public CanonicalResetUrlBuilder(@Value("${app.password-reset.base-url:http://localhost:8080}") String baseUrl,
            Environment environment) {
        this(baseUrl, environment.acceptsProfiles(Profiles.of("prod")));
    }

    CanonicalResetUrlBuilder(String baseUrl, boolean production) {
        this.baseUrl = validateBaseUrl(baseUrl, production).toString().replaceAll("/$", "");
    }

    public String build(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("Invalid reset token.");
        URI base = URI.create(baseUrl);
        String path = base.getRawPath() == null ? "" : base.getRawPath();
        String link = base.getScheme() + "://" + base.getRawAuthority() + path
                + "/reset-password?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return link;
    }

    static URI validateBaseUrl(String value, boolean production) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)
                || value.chars().anyMatch(Character::isISOControl) || value.contains("\\")) {
            throw new IllegalArgumentException("Password reset base URL is invalid.");
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getRawAuthority() == null
                    || path != null && !path.isEmpty()
                            && !path.matches("^/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+/?$")
                    || (production ? !"https".equalsIgnoreCase(scheme)
                            : !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)))) {
                throw new IllegalArgumentException("Password reset base URL is invalid.");
            }
            if (production && (!host.matches("(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")
                    || host.matches("^\\d+(?:\\.\\d+){3}$") || uri.getPort() > 0 && uri.getPort() != 443)) {
                throw new IllegalArgumentException("Password reset base URL is invalid.");
            }
            return uri.normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Password reset base URL is invalid.");
        }
    }
}
