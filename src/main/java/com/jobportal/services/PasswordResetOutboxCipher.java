package com.jobportal.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.model.PasswordResetOutboxJob;

/** Encrypts reset email payloads before they are written to the outbox table. */
@Component
public class PasswordResetOutboxCipher {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetOutboxCipher.class);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ObjectMapper objectMapper;
    private final Map<String, byte[]> keys;
    private final String activeKeyId;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetOutboxCipher(ObjectMapper objectMapper,
            @Value("${app.password-reset.outbox.encryption-keys:}") String configuredKeys,
            @Value("${app.password-reset.outbox.active-key-id:}") String activeKeyId,
            Environment environment) {
        this.objectMapper = objectMapper;
        this.keys = parseKeys(configuredKeys);
        this.activeKeyId = activeKeyId == null ? "" : activeKeyId.trim();
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            if (keys.isEmpty()) {
                throw new IllegalStateException("Missing or invalid PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS: "
                        + "configure one or more keyId=Base64(32-byte-key) entries.");
            }
            if (!keys.containsKey(this.activeKeyId)) {
                throw new IllegalStateException("PASSWORD_RESET_OUTBOX_ACTIVE_KEY_ID must match a key ID in "
                        + "PASSWORD_RESET_OUTBOX_ENCRYPTION_KEYS.");
            }
            logger.info("Password reset outbox encryption configuration is valid ({} key(s); values hidden).",
                    keys.size());
        }
    }

    public EncryptedPayload encrypt(String jobId, PasswordResetOutboxJob.Type type,
            Long userId, Long tokenId, Payload payload) {
        byte[] key = keys.get(activeKeyId);
        if (key == null) throw new IllegalStateException("Password reset outbox encryption is not configured.");
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(payload);
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(jobId, type, userId, tokenId));
            return new EncryptedPayload(activeKeyId, nonce, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            throw new IllegalStateException("Password reset outbox encryption failed.");
        }
    }

    public Payload decrypt(PasswordResetOutboxJob job) {
        byte[] key = keys.get(job.getEncryptionKeyId());
        if (key == null || job.getNonce() == null || job.getEncryptedPayload() == null) {
            throw new IllegalStateException("Password reset outbox payload is unavailable.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, job.getNonce()));
            cipher.updateAAD(aad(job.getId(), job.getJobType(), job.getUserId(), job.getTokenId()));
            return objectMapper.readValue(cipher.doFinal(job.getEncryptedPayload()), Payload.class);
        } catch (GeneralSecurityException | java.io.IOException ex) {
            throw new IllegalStateException("Password reset outbox payload could not be decrypted.");
        }
    }

    private Map<String, byte[]> parseKeys(String configured) {
        Map<String, byte[]> parsed = new HashMap<>();
        if (configured == null || configured.isBlank()) return Map.of();
        try {
            for (String entry : configured.split(",")) {
                int separator = entry.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException();
                String keyId = entry.substring(0, separator).trim();
                String encoded = entry.substring(separator + 1).trim();
                if (!KEY_ID.matcher(keyId).matches() || parsed.containsKey(keyId)) throw new IllegalArgumentException();
                byte[] key = Base64.getDecoder().decode(encoded);
                if (key.length != 32) throw new IllegalArgumentException();
                parsed.put(keyId, key);
            }
            return Map.copyOf(parsed);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Password reset outbox encryption configuration is invalid.");
        }
    }

    private byte[] aad(String id, PasswordResetOutboxJob.Type type, Long userId, Long tokenId) {
        String value = id + "|" + type.name() + "|" + (userId == null ? "-" : userId)
                + "|" + (tokenId == null ? "-" : tokenId);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public record Payload(String email, String resetUrl) {
        @Override public String toString() { return "PasswordResetOutboxCipher.Payload[redacted]"; }
    }

    public record EncryptedPayload(String keyId, byte[] nonce, byte[] ciphertext) {
        @Override public String toString() { return "EncryptedPayload[keyId=" + keyId + ", data=<redacted>]"; }
    }
}
