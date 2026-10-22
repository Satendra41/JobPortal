package com.jobportal.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import com.jobportal.dto.UserResponse;
import com.jobportal.model.User;
import com.jobportal.model.UserRole;
import com.jobportal.services.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse registerUser(@RequestBody @NonNull User user) {
        return UserResponse.from(userService.registerUser(user));
    }

    @PostMapping("/login")
    public String loginUser(@RequestBody @NonNull User user, HttpServletRequest request) {
        User authenticatedUser = userService.authenticateUser(user.getEmail(), user.getPassword());
        if (authenticatedUser == null) return "Invalid email or password";
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        authenticatedUser.setPassword(null);
        session.setAttribute("loggedInUser", authenticatedUser);
        return "Login Successful";
    }

    @GetMapping("/login")
    public String loginHelp() {
        return "Use POST /users/login with JSON body: {\"email\":\"...\",\"password\":\"...\"}";
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();
        List<UserResponse> users = userService.getAllUsers().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable @NonNull Long id, HttpSession session) {
        User current = getCurrentUser(session);
        if (current == null || (!isAdmin(session) && !id.equals(current.getId()))) {
            return ResponseEntity.status(403).build();
        }
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable @NonNull Long id, @RequestBody @NonNull User user,
            HttpSession session) {
        User current = getCurrentUser(session);
        if (current == null || (!isAdmin(session) && !id.equals(current.getId()))) {
            return ResponseEntity.status(403).build();
        }
        User updatedUser = userService.updateUser(id, user);
        if (updatedUser == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable @NonNull Long id, HttpSession session) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();
        boolean deleted = userService.deleteUser(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok("User Deleted Successfully");
    }

    private User getCurrentUser(HttpSession session) {
        Object user = session.getAttribute("loggedInUser");
        return user instanceof User current ? current : null;
    }

    private boolean isAdmin(HttpSession session) {
        User user = getCurrentUser(session);
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if ("id".equals(ex.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user id. Use numeric id like /users/1"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request parameter"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidUserInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
