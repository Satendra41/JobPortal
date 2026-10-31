package com.jobportal.services;

import java.io.IOException;
import java.util.Set;

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
    private static final Set<String> SAFE_OAUTH_ERROR_CODES = Set.of(
            "invalid_request", "unauthorized_client", "access_denied", "unsupported_response_type",
            "invalid_scope", "server_error", "temporarily_unavailable", "invalid_client", "invalid_grant",
            "redirect_uri_mismatch",
            "invalid_token_response", "invalid_user_info_response", "invalid_id_token",
            "authorization_request_not_found", "github_verified_primary_email_missing");

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        OAuthFailureReason reason = reasonFor(exception);
        LOGGER.warn("Social sign-in failure provider={} stage=provider_callback reason={} errorCategory={} exceptionClass={}",
                providerFromCallback(request), reason, safeErrorCategory(exception), exception.getClass().getSimpleName());
        response.sendRedirect(request.getContextPath() + "/login?error=social-login");
    }

    private String providerFromCallback(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return "unknown";
        }
        if (requestUri.endsWith("/login/oauth2/code/google")) {
            return "google";
        }
        if (requestUri.endsWith("/login/oauth2/code/github")) {
            return "github";
        }
        return "unknown";
    }

    private String safeErrorCategory(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();
            if (errorCode != null && SAFE_OAUTH_ERROR_CODES.contains(errorCode)) {
                return errorCode;
            }
        }
        return "unavailable";
    }

    private OAuthFailureReason reasonFor(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException
                && "github_verified_primary_email_missing".equals(oauthException.getError().getErrorCode())) {
            return OAuthFailureReason.GITHUB_VERIFIED_PRIMARY_EMAIL_MISSING;
        }
        return OAuthFailureReason.PROVIDER_OAUTH_FAILURE;
    }
}
