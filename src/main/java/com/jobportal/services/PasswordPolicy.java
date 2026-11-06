package com.jobportal.services;

import java.nio.charset.StandardCharsets;

/** Shared password length rules for passwords that are encoded with BCrypt. */
public final class PasswordPolicy {

    public static final int MIN_CHARACTERS = 8;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
