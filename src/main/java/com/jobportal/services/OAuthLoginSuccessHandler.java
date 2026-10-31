package com.jobportal.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    private final OAuthAccountService oauthAccountService;

    public OAuthLoginSuccessHandler(OAuthAccountService oauthAccountService) {
        this.oauthAccountService = oauthAccountService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            LOGGER.warn("Social sign-in failure provider=unknown stage=success_handler reason={} exceptionClass={}",
                    OAuthFailureReason.PROVIDER_OAUTH_FAILURE, authentication.getClass().getSimpleName());
            response.sendRedirect(request.getContextPath() + "/login?error=social-login");
            return;
        }

        OAuth2User principal = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String providerUserId = principal instanceof OidcUser oidcUser
                ? oidcUser.getSubject()
                : principal.getName();
        String email = principal.getAttribute("email");
        boolean emailVerified = Boolean.TRUE.equals(principal.getAttribute("email_verified"));
        String name = principal.getAttribute("name");

        User user;
        try {
            user = oauthAccountService.authenticateOrCreate(provider, providerUserId, email, emailVerified, name);
        } catch (OAuthAccountProvisioningException ex) {
            logRejection(provider, ex.getReason(), ex);
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            response.sendRedirect(request.getContextPath() + "/login?error=social-login");
            return;
        } catch (RuntimeException ex) {
            logRejection(provider, OAuthFailureReason.OTHER_ACCOUNT_PROVISIONING_FAILURE, ex);
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            response.sendRedirect(request.getContextPath() + "/login?error=social-login");
            return;
        }

        request.changeSessionId();
        user.setPassword(null);
        request.getSession(true).setAttribute("loggedInUser", user);
        response.sendRedirect(request.getContextPath() + dashboardFor(user));
    }

    private void logRejection(String provider, OAuthFailureReason reason, RuntimeException exception) {
        LOGGER.warn("Social sign-in failure provider={} stage=account_provisioning reason={} exceptionClass={} databaseCategory={} sqlState={}",
                safeProvider(provider), reason, exception.getClass().getSimpleName(),
                databaseCategory(exception), safeSqlState(exception));
    }

    private String safeProvider(String provider) {
        return "google".equals(provider) || "github".equals(provider) ? provider : "unknown";
    }

    private String databaseCategory(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return "duplicate_key";
            }
            if (cause instanceof DataIntegrityViolationException) {
                return "integrity_constraint";
            }
            if (cause instanceof DataAccessResourceFailureException) {
                return "database_unavailable";
            }
            if (cause instanceof QueryTimeoutException) {
                return "query_timeout";
            }
            if (cause instanceof DataAccessException || cause instanceof SQLException) {
                return "database_error";
            }
        }
        return "not_database";
    }

    private String safeSqlState(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.matches("(?i)[a-z0-9]{5}")) {
                    return sqlState.toUpperCase(Locale.ROOT);
                }
            }
        }
        return "none";
    }

    private String dashboardFor(User user) {
        if (user.getRole() == UserRole.HR) {
            return "/hr/dashboard";
        }
        if (user.getRole() == UserRole.ADMIN) {
            return "/admin/dashboard";
        }
        return "/dashboard";
    }
}
