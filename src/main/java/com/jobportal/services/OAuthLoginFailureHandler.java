package com.jobportal.services;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthLoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        OAuthFailureReason reason = reasonFor(exception);
        LOGGER.warn("OAuth sign-in failed reason={} exceptionClass={}",
                reason, exception.getClass().getSimpleName());
        response.sendRedirect(request.getContextPath() + "/login?error=social-login");
    }

    private OAuthFailureReason reasonFor(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException
                && "github_verified_primary_email_missing".equals(oauthException.getError().getErrorCode())) {
            return OAuthFailureReason.GITHUB_VERIFIED_PRIMARY_EMAIL_MISSING;
        }
        return OAuthFailureReason.PROVIDER_OAUTH_FAILURE;
    }
}
