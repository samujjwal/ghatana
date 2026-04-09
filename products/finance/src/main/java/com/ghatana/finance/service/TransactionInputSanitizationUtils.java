package com.ghatana.finance.service;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Validates and sanitizes transaction inputs at the Finance service boundary
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class TransactionInputSanitizationUtils {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9_./-]{1,64}$");

    private TransactionInputSanitizationUtils() {
    }

    public static String requireSafeIdentifier(String value, String fieldName) {
        String sanitized = requireTrimmed(value, fieldName);
        if (!SAFE_IDENTIFIER.matcher(sanitized).matches()) {
            throw new IllegalArgumentException(fieldName + " must contain only safe identifier characters");
        }
        return sanitized;
    }

    public static String requireSafeCode(String value, String fieldName) {
        String sanitized = requireTrimmed(value, fieldName);
        if (!SAFE_CODE.matcher(sanitized).matches()) {
            throw new IllegalArgumentException(fieldName + " must contain only safe code characters");
        }
        return sanitized;
    }

    public static String requireAllowedValue(String value, String fieldName, Set<String> allowedValues) {
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        String sanitized = requireTrimmed(value, fieldName);
        if (!allowedValues.contains(sanitized)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowedValues);
        }
        return sanitized;
    }

    public static double requirePositiveAmount(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive finite number");
        }
        return value;
    }

    public static double requireNonNegativeMetric(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative finite number");
        }
        return value;
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String sanitized = value.trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return sanitized;
    }
}
