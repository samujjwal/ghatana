package com.ghatana.phr.security;

import com.ghatana.phr.kernel.policy.PhrFieldClassification;
import com.ghatana.phr.kernel.policy.PhrLogRedactor;

/**
 * PHI-safe logging utility that redacts protected health information from log messages.
 *
 * <p>This utility prevents PHI from appearing in logs by delegating to the canonical
 * PhrFieldClassification and PhrLogRedactor. It should be used for all logging in PHR-related code paths.</p>
 *
 * <p>Redaction rules are defined in PhrFieldClassification with sensitivity levels:
 * <ul>
 *   <li>PUBLIC: Safe to log without redaction</li>
 *   <li>IDENTIFIABLE: Redacted in logs as [REDACTED-ID]</li>
 *   <li>SENSITIVE_PHI: Redacted in logs as [REDACTED-PHI]</li>
 *   <li>RESTRICTED: Redacted in logs as [REDACTED-RESTRICTED]</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PHI-safe logging utility that redacts protected health information
 * @doc.layer product
 * @doc.pattern Utility, Adapter
 * @since 1.0.0
 */
public final class PhrSafeLogger {

    private PhrSafeLogger() {
        // Utility class - prevent instantiation
    }

    /**
     * Redacts PHI from a log message using canonical field classification.
     *
     * <p>This method delegates to PhrLogRedactor for pattern-based redaction
     * and PhrFieldClassification for field-level classification.</p>
     *
     * @param message the original message that may contain PHI
     * @return the message with PHI redacted
     */
    public static String redactPhi(String message) {
        return PhrLogRedactor.redactMessage(message);
    }

    /**
     * Redacts a specific field value based on canonical field classification.
     *
     * @param fieldName the field name to classify
     * @param value the field value
     * @return the redacted value, or the original if not sensitive
     */
    public static String redactField(String fieldName, String value) {
        return PhrLogRedactor.redactField(fieldName, value);
    }

    /**
     * Safely logs a message with PHI redaction.
     *
     * <p>This method should be used instead of direct logging when the message
     * may contain PHI. It redacts the message before returning it for logging.</p>
     *
     * @param message the message to log (may contain PHI)
     * @return the redacted message safe for logging
     */
    public static String safeLog(String message) {
        return redactPhi(message);
    }

    /**
     * Safely logs a message with parameters, redacting any PHI in the parameters.
     *
     * @param format the message format string
     * @param args the arguments to format into the message
     * @return the redacted message safe for logging
     */
    public static String safeLog(String format, Object... args) {
        if (args == null || args.length == 0) {
            return redactPhi(format);
        }

        // Redact each argument
        Object[] redactedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                redactedArgs[i] = redactPhi(args[i].toString());
            } else {
                redactedArgs[i] = null;
            }
        }

        try {
            return String.format(format, redactedArgs);
        } catch (Exception e) {
            // If formatting fails, return redacted format string
            return redactPhi(format);
        }
    }

    /**
     * Checks if a field should be redacted in logs based on canonical classification.
     *
     * @param fieldName the field name to check
     * @return true if the field should be redacted in logs
     */
    public static boolean shouldRedactInLogs(String fieldName) {
        return PhrFieldClassification.shouldRedactInLogs(fieldName);
    }

    /**
     * Checks if a string likely contains PHI.
     *
     * <p>This is a conservative check that returns true if any PHI patterns
     * are detected. Use this to determine if a message should be redacted before logging.</p>
     *
     * @param message the message to check
     * @return true if the message likely contains PHI
     */
    public static boolean containsPhi(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        // Check if redaction would change the message
        String redacted = redactPhi(message);
        return !redacted.equals(message);
    }
}
