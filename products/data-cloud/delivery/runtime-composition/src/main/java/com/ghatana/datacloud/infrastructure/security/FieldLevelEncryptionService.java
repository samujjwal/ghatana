package com.ghatana.datacloud.infrastructure.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Field-level encryption and redaction helper for Data-Cloud entity payloads.
 *
 * @doc.type class
 * @doc.purpose Encrypts or redacts sensitive payload fields before storage or audit export
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public final class FieldLevelEncryptionService {

    public static final String ENCRYPTED_PREFIX = "enc:v1:";
    public static final String REDACTED_VALUE = "[REDACTED]";

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecureRandom secureRandom;

    public FieldLevelEncryptionService() {
        this(new SecureRandom());
    }

    FieldLevelEncryptionService(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public Map<String, Object> encryptSensitiveFields(
            Map<String, Object> payload,
            Set<String> sensitiveFields,
            byte[] key) {
        Map<String, Object> copy = new HashMap<>(payload != null ? payload : Map.of());
        for (String field : Set.copyOf(sensitiveFields != null ? sensitiveFields : Set.of())) {
            Object value = copy.get(field);
            if (value != null && !isEncrypted(value)) {
                copy.put(field, encrypt(String.valueOf(value), key));
            }
        }
        return Map.copyOf(copy);
    }

    public Map<String, Object> decryptSensitiveFields(
            Map<String, Object> payload,
            Set<String> sensitiveFields,
            byte[] key) {
        Map<String, Object> copy = new HashMap<>(payload != null ? payload : Map.of());
        for (String field : Set.copyOf(sensitiveFields != null ? sensitiveFields : Set.of())) {
            Object value = copy.get(field);
            if (isEncrypted(value)) {
                copy.put(field, decrypt(String.valueOf(value), key));
            }
        }
        return Map.copyOf(copy);
    }

    public Map<String, Object> redactSensitiveFields(Map<String, Object> payload, Set<String> sensitiveFields) {
        Map<String, Object> copy = new HashMap<>(payload != null ? payload : Map.of());
        for (String field : Set.copyOf(sensitiveFields != null ? sensitiveFields : Set.of())) {
            if (copy.containsKey(field)) {
                copy.put(field, REDACTED_VALUE);
            }
        }
        return Map.copyOf(copy);
    }

    private String encrypt(String plaintext, byte[] key) {
        SecretKeySpec secretKey = secretKey(key);
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt sensitive field", e);
        }
    }

    private String decrypt(String ciphertext, byte[] key) {
        SecretKeySpec secretKey = secretKey(key);
        try {
            byte[] envelope = Base64.getDecoder().decode(ciphertext.substring(ENCRYPTED_PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(envelope, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(envelope, IV_BYTES, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt sensitive field", e);
        }
    }

    private static SecretKeySpec secretKey(byte[] key) {
        if (key == null || !(key.length == 16 || key.length == 24 || key.length == 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        return new SecretKeySpec(key, "AES");
    }

    private static boolean isEncrypted(Object value) {
        return value instanceof String text && text.startsWith(ENCRYPTED_PREFIX);
    }
}
