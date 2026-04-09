package com.ghatana.products.finance;

import com.ghatana.finance.service.TransactionInputSanitizationUtils;

/**
 * @doc.type class
 * @doc.purpose Validates identifiers and required payloads at the Finance product API boundary
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class FinanceApiBoundaryValidationUtils {

    private FinanceApiBoundaryValidationUtils() {
    }

    public static String requirePrincipalId(String value, String fieldName) {
        return TransactionInputSanitizationUtils.requireSafeIdentifier(value, fieldName);
    }

    public static String requireResourceId(String value, String fieldName) {
        return TransactionInputSanitizationUtils.requireSafeIdentifier(value, fieldName);
    }

    public static <T> T requirePayload(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
