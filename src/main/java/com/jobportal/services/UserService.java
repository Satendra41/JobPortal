package com.jobportal.services;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.CompanyProfileRepository;
import com.jobportal.repository.JobAlertRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.OAuthAccountRepository;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.repository.SavedJobRepository;
import com.jobportal.repository.UserRepository;

@Service
public class UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final Pattern BCRYPT_HASH_PATTERN = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final ApplicationRepository applicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final ProfileRepository profileRepository;
    private final JobAlertRepository jobAlertRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final JobRepository jobRepository;

    public UserService(UserRepository userRepository, OAuthAccountRepository oauthAccountRepository,
            ApplicationRepository applicationRepository, SavedJobRepository savedJobRepository,
            ProfileRepository profileRepository, JobAlertRepository jobAlertRepository,
            CompanyProfileRepository companyProfileRepository, JobRepository jobRepository) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.applicationRepository = applicationRepository;
        this.savedJobRepository = savedJobRepository;
        this.profileRepository = profileRepository;
        this.jobAlertRepository = jobAlertRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.jobRepository = jobRepository;
    }

    public @NonNull User save(@NonNull User user) {
        User stored = user.getId() == null ? null : userRepository.findById(user.getId()).orElse(null);
        if (stored != null) {
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(stored.getPassword());
            } else if (!isPasswordHash(user.getPassword())) {
                user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
            }
        } else if (user.getPassword() != null) {
            PasswordPolicy.validate(user.getPassword());
            user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        }
        return userRepository.save(user);
    }

    public boolean isEmailTaken(@NonNull String email) {
        return !userRepository.findAllByEmailIgnoreCase(email.trim()).isEmpty();
    }

    public String loginUser(String email, String password) {
        if (authenticateUser(email, password) != null) {
            return "Login Successful";
        }
        List<User> existingUsers = userRepository.findAllByEmail(email);

        if (existingUsers.isEmpty()) {
            return "User Not Found";
        }

        return "Wrong Password";
    }

    public User authenticateUser(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        for (User user : userRepository.findAllByEmail(email)) {
            if (!user.isEnabled() || !user.isEmailVerified()) continue;
            String storedPassword = user.getPassword();
            if (storedPassword == null) continue;
            boolean matched = isPasswordHash(storedPassword)
                    ? PASSWORD_ENCODER.matches(password, storedPassword)
                    : password.equals(storedPassword);
            if (matched) {
                if (!isPasswordHash(storedPassword)) {
                    user.setPassword(PASSWORD_ENCODER.encode(password));
                    userRepository.save(user);
                }
                return user;
            }
        }
        return null;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(@NonNull Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        return userOptional.orElse(null);
    }

    public User getEnabledUserForSession(@NonNull Long id) {
        User user = getUserById(id);
        if (user == null || !user.isEnabled()) {
            return null;
        }
        User sessionUser = new User();
        sessionUser.setId(user.getId());
        sessionUser.setName(user.getName());
        sessionUser.setEmail(user.getEmail());
        sessionUser.setRole(user.getRole());
        sessionUser.setEnabled(user.isEnabled());
        sessionUser.setEmailVerified(user.isEmailVerified());
        sessionUser.setCredentialVersion(user.getCredentialVersion());
        return sessionUser;
    }

    public User updateUser(@NonNull Long id, @NonNull User updatedUser) {
        String requestedPassword = updatedUser.getPassword();
        boolean passwordChangeRequested = requestedPassword != null && !requestedPassword.isBlank();
        if (passwordChangeRequested) {
            if (isPasswordHash(requestedPassword)) {
                throw new IllegalArgumentException("Password must be provided as plain text.");
            }
            PasswordPolicy.validate(requestedPassword);
        }

        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();
        user.setName(updatedUser.getName());
        if (passwordChangeRequested) {
            user.setPassword(PASSWORD_ENCODER.encode(requestedPassword));
            user.incrementCredentialVersion();
        }

        return userRepository.save(user);
    }

    public boolean updateRoleForAdmin(@NonNull Long id, @NonNull UserRole role) {
        if (role != UserRole.JOB_SEEKER && role != UserRole.HR) {
            return false;
        }

        User user = getUserById(id);
        if (user == null || user.getRole() == UserRole.ADMIN) {
            return false;
        }

        user.setRole(role);
        userRepository.save(user);
        return true;
    }

    public boolean updateEnabledForAdmin(@NonNull Long id, boolean enabled) {
        User user = getUserById(id);
        if (user == null || user.getRole() == UserRole.ADMIN) {
            return false;
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public boolean deleteUser(@NonNull Long id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        if (hasDependentRecords(id)) {
            return false;
        }
        oauthAccountRepository.deleteAllByUser_Id(id);
        userRepository.deleteById(id);
        return true;
    }

    public boolean hasDependentRecords(@NonNull Long id) {
        return applicationRepository.existsByUserId(id)
                || savedJobRepository.countByUserId(id) > 0
                || profileRepository.existsByUserId(id)
                || jobAlertRepository.countByUserId(id) > 0
                || companyProfileRepository.existsByUserId(id)
                || jobRepository.existsByOwnerId(id);
    }

    private boolean isPasswordHash(String password) {
        return BCRYPT_HASH_PATTERN.matcher(password).matches();
    }
}
