package com.jobportal.services;

import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

@Service
public class UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public @NonNull User registerUser(@NonNull User user) {
        if (user.getName() == null || user.getName().isBlank()
                || user.getEmail() == null || user.getEmail().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Name, email, and password are required.");
        }
        if (isEmailTaken(user.getEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        user.setId(null);
        user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        user.setRole(UserRole.JOB_SEEKER);
        return userRepository.save(user);
    }

    public @NonNull User save(@NonNull User user) {
        if (user.getId() != null) {
            User stored = userRepository.findById(user.getId()).orElse(null);
            if (stored != null) {
                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    user.setPassword(stored.getPassword());
                } else if (!isPasswordHash(user.getPassword())) {
                    user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
                }
            }
        }
        return userRepository.save(user);
    }

    public String registerNewUser(@NonNull User user) {
        if (user.getName() == null || user.getName().isBlank()) {
            return "Name is required";
        }
        final String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "Email is required";
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return "Password is required";
        }
        if (isEmailTaken(email)) {
            return "Email is already registered";
        }
        user.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        user.setRole(UserRole.JOB_SEEKER);
        userRepository.save(user);
        return "success";
    }

    public boolean isEmailTaken(@NonNull String email) {
        return !userRepository.findAllByEmail(email).isEmpty();
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

    public User updateUser(@NonNull Long id, @NonNull User updatedUser) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();
        user.setName(updatedUser.getName());
        user.setEmail(updatedUser.getEmail());
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
            user.setPassword(isPasswordHash(updatedUser.getPassword()) ? updatedUser.getPassword()
                    : PASSWORD_ENCODER.encode(updatedUser.getPassword()));
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

    public boolean deleteUser(@NonNull Long id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }

    private boolean isPasswordHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
