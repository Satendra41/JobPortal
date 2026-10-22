package com.jobportal.dto;

import com.jobportal.model.User;
import com.jobportal.model.UserRole;

public record UserResponse(Long id, String name, String email, UserRole role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
