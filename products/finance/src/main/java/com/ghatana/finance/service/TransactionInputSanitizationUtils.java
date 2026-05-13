package com.ghatana.finance.service;

import com.ghatana.platform.security.validation.SafeInputValidator;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Validates and sanitizes transaction inputs at the Finance service boundary
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class TransactionInputSanitizationUtils {

    private TransactionInputSanitizationUtils() {
    }

    public static String requireSafeIdentifier(String value, String fieldName) {
        return SafeInputValidator.requireSafeIdentifier(value, fieldName);
    }

    public static String requireSafeCode(String value, String fieldName) {
        return SafeInputValidator.requireSafeCode(value, fieldName);
    }

    public static String requireAllowedValue(String value, String fieldName, Set<String> allowedValues) {
        Objects.requireNonNull(allowedValues, "allowedValues must not be null");
        return SafeInputValidator.requireAllowedValue(value, fieldName, allowedValues);
    }

    public static double requirePositiveAmount(double value, String fieldName) {
        return SafeInputValidator.requirePositiveFinite(value, fieldName);
    }

    public static double requireNonNegativeMetric(double value, String fieldName) {
        return SafeInputValidator.requireNonNegativeFinite(value, fieldName);
    }
}
