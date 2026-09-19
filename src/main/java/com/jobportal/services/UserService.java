package com.jobportal.services;

import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public @NonNull User registerUser(@NonNull User user) {
        return userRepository.save(user);
    }

    public @NonNull User save(@NonNull User user) {
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
        user.setRole(UserRole.JOB_SEEKER);
        userRepository.save(user);
        return "success";
    }

    public boolean isEmailTaken(@NonNull String email) {
        return !userRepository.findAllByEmail(email).isEmpty();
    }

    public String loginUser(String email, String password) {
        List<User> existingUsers = userRepository.findAllByEmail(email);

        if (existingUsers.isEmpty()) {
            return "User Not Found";
        }

        for (User existingUser : existingUsers) {
            if (existingUser.getPassword().equals(password)) {
                return "Login Successful";
            }
        }

        return "Wrong Password";
    }

    public User authenticateUser(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        return userRepository.findAllByEmail(email).stream()
            .filter(user -> password.equals(user.getPassword()))
            .findFirst()
            .orElse(null);
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
        user.setPassword(updatedUser.getPassword());

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
}
