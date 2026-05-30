package com.ghatana.phr.kernel.policy;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * PHI/PII log redaction utility for PHR Nepal.
 *
 * Redacts sensitive fields from log messages based on field classification.
 * Used by logging appenders and audit export handlers to ensure PHI
 * is not exposed in logs or non-admin audit exports.
 *
 * @doc.type class
 * @doc.purpose PHI/PII redaction for logs and audit exports
 * @doc.layer product
 * @doc.pattern Utility, Security
 */
public final class PhrLogRedactor {

    private PhrLogRedactor() {}

    // Patterns for detecting PHI in free-form text
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("\\b\\d{12,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\+?\\d{10,15}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-?\\d{2}-?\\d{4}\\b");

    /**
     * Redacts a single field value based on its field name.
     *
     * @param fieldName the field name to classify
     * @param value the field value
     * @return the redacted value, or the original if not sensitive
     */
    public static String redactField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return PhrFieldClassification.redactValue(fieldName, value);
    }

    /**
     * Redacts all sensitive fields in a map of data.
     *
     * @param data the data map to redact
     * @return a new map with sensitive fields redacted
     */
    public static Map<String, Object> redactMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        Map<String, Object> redacted = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String stringValue) {
                redacted.put(key, redactField(key, stringValue));
            } else if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringKeyedMap = (Map<String, Object>) nestedMap;
                redacted.put(key, redactMap(stringKeyedMap));
            } else {
                redacted.put(key, value);
            }
        }
        return redacted;
    }

    /**
     * Redacts PHI from a free-form log message.
     *
     * This is a best-effort scan for common PHI patterns.
     * Structured logging with field-level redaction is preferred.
     *
     * @param message the log message
     * @return the message with PHI redacted
     */
    public static String redactMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String redacted = message;
        redacted = NATIONAL_ID_PATTERN.matcher(redacted).replaceAll("[REDACTED-ID]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED-PHONE]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED-EMAIL]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED-SSN]");

        return redacted;
    }

    /**
     * Redacts a message for audit export (less aggressive than logs).
     *
     * Audit exports may show identifiable information but not restricted PHI.
     *
     * @param message the message
     * @return the message with restricted PHI redacted
     */
    public static String redactForAuditExport(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String redacted = message;
        // Only redact highly sensitive fields in audit exports
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED-SSN]");

        return redacted;
    }

    /**
     * Redacts a message for mobile cache (most aggressive).
     *
     * Mobile cache should not contain any PHI or PII.
     *
     * @param message the message
     * @return the message with all PHI/PII redacted
     */
    public static String redactForMobileCache(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String redacted = message;
        redacted = NATIONAL_ID_PATTERN.matcher(redacted).replaceAll("[REDACTED-ID]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED-PHONE]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED-EMAIL]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED-SSN]");

        // Also redact common PHI terms
        redacted = redacted.replaceAll("(?i)\\bdiagnosis\\b", "[REDACTED-PHI]");
        redacted = redacted.replaceAll("(?i)\\bmedication\\b", "[REDACTED-PHI]");
        redacted = redacted.replaceAll("(?i)\\blab result\\b", "[REDACTED-PHI]");

        return redacted;
    }

    /**
     * Redacts a message for push notifications.
     *
     * Push notifications should not contain PHI.
     *
     * @param message the message
     * @return the message with PHI redacted
     */
    public static String redactForNotification(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String redacted = message;
        redacted = NATIONAL_ID_PATTERN.matcher(redacted).replaceAll("[REDACTED-ID]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED-PHONE]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED-EMAIL]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED-SSN]");

        // Redact clinical terms
        redacted = redacted.replaceAll("(?i)\\bdiagnosis\\b", "[REDACTED]");
        redacted = redacted.replaceAll("(?i)\\bmedication\\b", "[REDACTED]");
        redacted = redacted.replaceAll("(?i)\\bcondition\\b", "[REDACTED]");

        return redacted;
    }
}
