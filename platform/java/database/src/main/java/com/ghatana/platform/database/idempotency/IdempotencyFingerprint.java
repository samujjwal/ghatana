package com.ghatana.platform.database.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Creates stable request fingerprints for Kernel idempotent mutation contracts
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class IdempotencyFingerprint {

    private static final char FIELD_SEPARATOR = '\u001F';
    private static final char KEY_VALUE_SEPARATOR = '\u001E';

    private IdempotencyFingerprint() {
    }

    public static String sha256(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        return sha256(canonicalPayload(fields));
    }

    public static String sha256(String canonicalPayload) {
        if (canonicalPayload == null || canonicalPayload.isBlank()) {
            throw new IllegalArgumentException("canonicalPayload must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available for idempotency fingerprinting", exception);
        }
    }

    public static String canonicalPayload(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        StringJoiner joiner = new StringJoiner(Character.toString(FIELD_SEPARATOR));
        fields.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> joiner.add(canonicalField(entry.getKey(), entry.getValue())));
        return joiner.toString();
    }

    private static String canonicalField(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("fingerprint field keys must not be blank");
        }
        return key + KEY_VALUE_SEPARATOR + canonicalValue(value);
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .map(entry -> Map.entry(String.valueOf(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> canonicalField(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(Character.toString(FIELD_SEPARATOR), "{", "}"));
        }
        return String.valueOf(value);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
