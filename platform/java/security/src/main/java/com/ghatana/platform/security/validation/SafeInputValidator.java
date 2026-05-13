package com.ghatana.platform.security.validation;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kernel-owned validation helpers for common boundary inputs.
 *
 * @doc.type class
 * @doc.purpose Shared safe identifier, safe code, allowed-value, and numeric validation primitives
 * @doc.layer platform
 * @doc.pattern Utils
 */
public final class SafeInputValidator {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:@/#-]{0,127}$");
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_./-]{0,127}$");

    private SafeInputValidator() {
    }

    public static String requireSafeIdentifier(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName, 128);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must contain only safe identifier characters");
        }
        return normalized;
    }

    public static String requireSafeCode(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName, 128);
        if (!SAFE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must contain only safe code characters");
        }
        return normalized;
    }

    public static String requireAllowedValue(String value, String fieldName, Set<String> allowedValues) {
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        String normalized = requireNonBlank(value, fieldName, 256);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowedValues);
        }
        return normalized;
    }

    public static String requireNonBlank(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length of " + maxLength);
        }
        return normalized;
    }

    public static double requirePositiveFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive finite number");
        }
        return value;
    }

    public static double requireNonNegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite number");
        }
        return value;
    }
}
