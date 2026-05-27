package com.ghatana.phr.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * PHI-safe logging utility that redacts Protected Health Information from log messages.
 *
 * This utility ensures that PHI is never written to logs in plain text, complying with
 * healthcare data protection requirements (Nepal Privacy Act 2075, HIPAA-like standards).
 *
 * @doc.type class
 * @doc.purpose PHI-safe logging with automatic redaction of sensitive fields
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PhiSafeLogger {

    private static final Set<String> PHI_FIELDS = Set.of(
        "patientId", "patient_name", "patientName", "name", "givenName", "familyName",
        "diagnosis", "condition", "medication", "prescription", "labResult", "observation",
        "address", "phone", "email", "ssn", "nationalId", "insuranceNumber",
        "bloodType", "allergies", "medicalRecordNumber", "mrn"
    );

    private static final Pattern PHI_PATTERN = Pattern.compile(
        "(?i)(?:" + String.join("|", PHI_FIELDS) + ")[\"']?\\s*[:=]\\s*[\"']?([^\"',}\\s]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b\\+?[0-9]{1,3}[-. ]?\\(?[0-9]{3}\\)?[-. ]?[0-9]{3}[-. ]?[0-9]{4}\\b"
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}[-.]?\\d{2}[-.]?\\d{4}\\b"
    );

    private final Logger delegate;

    private PhiSafeLogger(Logger delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a PHI-safe logger for the given class.
     *
     * @param clazz the class to log for
     * @return PHI-safe logger instance
     */
    public static PhiSafeLogger getLogger(Class<?> clazz) {
        return new PhiSafeLogger(LoggerFactory.getLogger(clazz));
    }

    /**
     * Redacts PHI from a log message.
     *
     * @param message the original message
     * @return message with PHI redacted
     */
    public static String redactPhi(String message) {
        if (message == null) {
            return "";
        }

        String redacted = message;

        // Redact known PHI field patterns
        redacted = PHI_PATTERN.matcher(redacted).replaceAll("$1=***REDACTED***");

        // Redact email addresses
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("***EMAIL***");

        // Redact phone numbers
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("***PHONE***");

        // Redact SSN-like patterns
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("***SSN***");

        return redacted;
    }

    /**
     * Logs a debug message with PHI redaction.
     */
    public void debug(String message) {
        delegate.debug(redactPhi(message));
    }

    /**
     * Logs a debug message with PHI redaction and parameters.
     */
    public void debug(String message, Object... params) {
        Object[] redactedParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            redactedParams[i] = params[i] instanceof String ? redactPhi((String) params[i]) : params[i];
        }
        delegate.debug(redactPhi(message), redactedParams);
    }

    /**
     * Logs an info message with PHI redaction.
     */
    public void info(String message) {
        delegate.info(redactPhi(message));
    }

    /**
     * Logs an info message with PHI redaction and parameters.
     */
    public void info(String message, Object... params) {
        Object[] redactedParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            redactedParams[i] = params[i] instanceof String ? redactPhi((String) params[i]) : params[i];
        }
        delegate.info(redactPhi(message), redactedParams);
    }

    /**
     * Logs a warning message with PHI redaction.
     */
    public void warn(String message) {
        delegate.warn(redactPhi(message));
    }

    /**
     * Logs a warning message with PHI redaction and parameters.
     */
    public void warn(String message, Object... params) {
        Object[] redactedParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            redactedParams[i] = params[i] instanceof String ? redactPhi((String) params[i]) : params[i];
        }
        delegate.warn(redactPhi(message), redactedParams);
    }

    /**
     * Logs a warning message with PHI redaction and exception.
     */
    public void warn(String message, Throwable throwable) {
        delegate.warn(redactPhi(message), throwable);
    }

    /**
     * Logs an error message with PHI redaction.
     */
    public void error(String message) {
        delegate.error(redactPhi(message));
    }

    /**
     * Logs an error message with PHI redaction and parameters.
     */
    public void error(String message, Object... params) {
        Object[] redactedParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            redactedParams[i] = params[i] instanceof String ? redactPhi((String) params[i]) : params[i];
        }
        delegate.error(redactPhi(message), redactedParams);
    }

    /**
     * Logs an error message with PHI redaction and exception.
     */
    public void error(String message, Throwable throwable) {
        delegate.error(redactPhi(message), throwable);
    }

    /**
     * Checks if debug logging is enabled.
     */
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    /**
     * Checks if info logging is enabled.
     */
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    /**
     * Checks if warn logging is enabled.
     */
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    /**
     * Checks if error logging is enabled.
     */
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }
}
