package com.jobportal.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jobportal.model.OAuthAccount;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.UserRepository;

@Service
public class OAuthAccountService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "github");

    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;

    public OAuthAccountService(OAuthAccountRepository oauthAccountRepository, UserRepository userRepository) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public @NonNull User authenticateOrCreate(
            String provider,
            String providerUserId,
            String email,
            boolean emailVerified,
            String name) {
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalizedProvider)
                || !StringUtils.hasText(providerUserId)
                || !StringUtils.hasText(normalizedEmail)
                || !emailVerified) {
            throw new OAuthAccountProvisioningException("A verified email is required for social sign-in.");
        }

        var existingAccount = oauthAccountRepository
                .findByProviderAndProviderUserId(normalizedProvider, providerUserId);
        if (existingAccount.isPresent()) {
            User existingUser = existingAccount.get().getUser();
            if (!existingUser.isEnabled()) {
                throw new OAuthAccountProvisioningException("This account is unavailable.");
            }
            return existingUser;
        }

        List<User> matchingUsers = userRepository.findAllByEmailIgnoreCase(normalizedEmail);
        if (!matchingUsers.isEmpty()) {
            throw new OAuthAccountProvisioningException(
                    "This email is already associated with an account. Sign in using its existing method.");
        }

        User user = new User();
        user.setName(StringUtils.hasText(name) ? name.trim() : normalizedEmail);
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.JOB_SEEKER);
        user.setEnabled(true);
        User savedUser = userRepository.saveAndFlush(user);
        oauthAccountRepository.save(new OAuthAccount(normalizedProvider, providerUserId, savedUser));
        return savedUser;
    }
}
