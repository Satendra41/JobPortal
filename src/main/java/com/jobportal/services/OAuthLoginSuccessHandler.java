package com.jobportal.services;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

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

        try {
            User user = oauthAccountService.authenticateOrCreate(
                    provider, providerUserId, email, emailVerified, name);
            request.changeSessionId();
            user.setPassword(null);
            request.getSession(true).setAttribute("loggedInUser", user);
            response.sendRedirect(request.getContextPath() + dashboardFor(user));
        } catch (OAuthAccountProvisioningException ex) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            }
            response.sendRedirect(request.getContextPath() + "/login?error=social-login");
        }
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
