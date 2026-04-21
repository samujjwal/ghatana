package com.ghatana.core.util;

import java.util.regex.Pattern;

/**
 * Utility class for redacting Personally Identifiable Information (PII) from strings.
 *
 * <p><b>Purpose</b><br>
 * Provides methods to redact sensitive information such as email addresses,
 * phone numbers, credit card numbers, SSNs, and other PII from log messages,
 * debug output, and other text that might be displayed or logged.</p>
 *
 * <p><b>Supported PII Types:</b></p>
 * <ul>
 *   <li>Email addresses</li>
 *   <li>Phone numbers (various formats)</li>
 *   <li>Credit card numbers</li>
 *   <li>Social Security Numbers (SSN)</li>
 *   <li>IP addresses</li>
 *   <li>API keys and tokens</li>
 *   <li>Passwords in key=value pairs</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Redact email
 * String safe = PiiRedactor.redact("Contact user@example.com for support");
 * // Result: "Contact ***@***.*** for support"
 *
 * // Redact credit card
 * String safe = PiiRedactor.redact("Card: 4111-1111-1111-1111");
 * // Result: "Card: ****-****-****-1111"
 *
 * // Redact password in query string
 * String safe = PiiRedactor.redact("https://api.com?user=john&password=secret");
 * // Result: "https://api.com?user=john&password=****"
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe (all methods are static and stateless).</p>
 *
 * @doc.type class
 * @doc.purpose PII redaction utilities for security and privacy
 * @doc.layer core
 * @doc.pattern Utility
 */
public final class PiiRedactor {

    // Email pattern: local-part@domain.tld
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    // Phone pattern: various formats
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"
    );

    // Credit card pattern: 13-19 digits, may have spaces or dashes
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    // SSN pattern: ###-##-#### or #########
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}[-.]?\\d{2}[-.]?\\d{4}\\b"
    );

    // IP address pattern: IPv4
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    // API key/token pattern: common patterns like "key=...", "token=...", "Bearer ..."
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "(?i)(key|token|api[_-]?key|secret|password|passwd|pwd)[\\s]*[=:][\\s]*([^\\s&]+)"
    );

    // UUID pattern
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );

    private static final String REDACTION_MASK = "****";
    private static final String EMAIL_REDACTION = "***@***.***";

    private PiiRedactor() {
        // Utility class - prevent instantiation
    }

    /**
     * Redacts all detected PII from the input string.
     *
     * @param input The input string that may contain PII
     * @return The input string with PII redacted, or null if input is null
     */
    public static String redact(String input) {
        if (input == null) {
            return null;
        }

        String result = input;
        result = redactEmails(result);
        result = redactPhones(result);
        result = redactCreditCards(result);
        result = redactSSNs(result);
        result = redactIPs(result);
        result = redactApiKeys(result);
        result = redactUUIDs(result);

        return result;
    }

    /**
     * Redacts email addresses from the input string.
     *
     * @param input The input string
     * @return The input string with emails redacted
     */
    public static String redactEmails(String input) {
        if (input == null) {
            return null;
        }
        return EMAIL_PATTERN.matcher(input).replaceAll(EMAIL_REDACTION);
    }

    /**
     * Redacts phone numbers from the input string.
     *
     * @param input The input string
     * @return The input string with phone numbers redacted
     */
    public static String redactPhones(String input) {
        if (input == null) {
            return null;
        }
        return PHONE_PATTERN.matcher(input).replaceAll(REDACTION_MASK);
    }

    /**
     * Redacts credit card numbers from the input string, preserving last 4 digits.
     *
     * @param input The input string
     * @return The input string with credit cards redacted
     */
    public static String redactCreditCards(String input) {
        if (input == null) {
            return null;
        }
        return CREDIT_CARD_PATTERN.matcher(input).replaceAll(match -> {
            String card = match.group().replaceAll("[^0-9]", "");
            if (card.length() >= 4) {
                return REDACTION_MASK + "-" + card.substring(card.length() - 4);
            }
            return REDACTION_MASK;
        });
    }

    /**
     * Redacts Social Security Numbers from the input string.
     *
     * @param input The input string
     * @return The input string with SSNs redacted
     */
    public static String redactSSNs(String input) {
        if (input == null) {
            return null;
        }
        return SSN_PATTERN.matcher(input).replaceAll(REDACTION_MASK);
    }

    /**
     * Redacts IP addresses from the input string.
     *
     * @param input The input string
     * @return The input string with IPs redacted
     */
    public static String redactIPs(String input) {
        if (input == null) {
            return null;
        }
        return IPV4_PATTERN.matcher(input).replaceAll("***.***.***.***");
    }

    /**
     * Redacts API keys, tokens, and passwords from the input string.
     *
     * @param input The input string
     * @return The input string with API keys redacted
     */
    public static String redactApiKeys(String input) {
        if (input == null) {
            return null;
        }
        return API_KEY_PATTERN.matcher(input).replaceAll("$1=" + REDACTION_MASK);
    }

    /**
     * Redacts UUIDs from the input string.
     *
     * @param input The input string
     * @return The input string with UUIDs redacted
     */
    public static String redactUUIDs(String input) {
        if (input == null) {
            return null;
        }
        return UUID_PATTERN.matcher(input).replaceAll(REDACTION_MASK);
    }

    /**
     * Checks if a string contains potential PII.
     *
     * @param input The input string to check
     * @return true if PII is detected, false otherwise
     */
    public static boolean containsPii(String input) {
        if (input == null) {
            return false;
        }

        return EMAIL_PATTERN.matcher(input).find()
            || PHONE_PATTERN.matcher(input).find()
            || CREDIT_CARD_PATTERN.matcher(input).find()
            || SSN_PATTERN.matcher(input).find()
            || IPV4_PATTERN.matcher(input).find()
            || API_KEY_PATTERN.matcher(input).find()
            || UUID_PATTERN.matcher(input).find();
    }
}
