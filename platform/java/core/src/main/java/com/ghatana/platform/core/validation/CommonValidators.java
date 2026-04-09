package com.ghatana.platform.core.validation;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Common validation predicates for use with ValidationFramework.
 *
 * <p>Provides pre-built validators for common validation scenarios including
 * email, phone, URL, UUID, and other standard formats.</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * ValidationResult result = ValidationFramework.validate()
 *     .field("email", user.getEmail())
 *         .matches(CommonValidators.EMAIL, "Invalid email format")
 *     .field("phone", user.getPhone())
 *         .matches(CommonValidators.PHONE_US, "Invalid US phone number")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Common validation predicates for standard formats
 * @doc.layer core
 * @doc.pattern Utility
 *
 * @since 1.0.0
 */
public final class CommonValidators {

    // Email validation pattern (RFC 5322 simplified)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // US phone number pattern (various formats)
    private static final Pattern PHONE_US_PATTERN = Pattern.compile(
            "^(\\+?1)?[-.\\s]?\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})$"
    );

    // UUID pattern
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    // URL pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$"
    );

    // IP address pattern (IPv4)
    private static final Pattern IP_V4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // Alphanumeric pattern
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    // Alphabetic pattern
    private static final Pattern ALPHABETIC_PATTERN = Pattern.compile("^[a-zA-Z]+$");

    // Numeric pattern
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]+$");

    private CommonValidators() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Email validator (RFC 5322 simplified).
     */
    @NotNull
    public static final Predicate<String> EMAIL = str ->
            str != null && EMAIL_PATTERN.matcher(str).matches();

    /**
     * US phone number validator.
     */
    @NotNull
    public static final Predicate<String> PHONE_US = str ->
            str != null && PHONE_US_PATTERN.matcher(str).matches();

    /**
     * UUID validator.
     */
    @NotNull
    public static final Predicate<String> UUID = str ->
            str != null && UUID_PATTERN.matcher(str).matches();

    /**
     * URL validator (HTTP/HTTPS/FTP).
     */
    @NotNull
    public static final Predicate<String> URL = str ->
            str != null && URL_PATTERN.matcher(str).matches();

    /**
     * IPv4 address validator.
     */
    @NotNull
    public static final Predicate<String> IP_V4 = str ->
            str != null && IP_V4_PATTERN.matcher(str).matches();

    /**
     * Alphanumeric validator (letters and numbers only).
     */
    @NotNull
    public static final Predicate<String> ALPHANUMERIC = str ->
            str != null && ALPHANUMERIC_PATTERN.matcher(str).matches();

    /**
     * Alphabetic validator (letters only).
     */
    @NotNull
    public static final Predicate<String> ALPHABETIC = str ->
            str != null && ALPHABETIC_PATTERN.matcher(str).matches();

    /**
     * Numeric validator (numbers only).
     */
    @NotNull
    public static final Predicate<String> NUMERIC = str ->
            str != null && NUMERIC_PATTERN.matcher(str).matches();

    /**
     * Not empty validator.
     */
    @NotNull
    public static final Predicate<String> NOT_EMPTY = str ->
            str != null && !str.isEmpty();

    /**
     * Not blank validator (not null, not empty, not whitespace).
     */
    @NotNull
    public static final Predicate<String> NOT_BLANK = str ->
            str != null && !str.trim().isEmpty();

    /**
     * Positive number validator.
     */
    @NotNull
    public static final Predicate<Number> POSITIVE = num ->
            num != null && num.doubleValue() > 0;

    /**
     * Non-negative number validator.
     */
    @NotNull
    public static final Predicate<Number> NON_NEGATIVE = num ->
            num != null && num.doubleValue() >= 0;

    /**
     * Create a length range validator.
     *
     * @param min minimum length (inclusive)
     * @param max maximum length (inclusive)
     * @return length range validator
     */
    @NotNull
    public static Predicate<String> lengthBetween(int min, int max) {
        return str -> str != null && str.length() >= min && str.length() <= max;
    }

    /**
     * Create a numeric range validator.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     * @return numeric range validator
     */
    @NotNull
    public static Predicate<Number> between(double min, double max) {
        return num -> num != null && num.doubleValue() >= min && num.doubleValue() <= max;
    }

    /**
     * Create a custom regex validator.
     *
     * @param pattern regex pattern
     * @return regex validator
     */
    @NotNull
    public static Predicate<String> regex(@NotNull String pattern) {
        Pattern compiled = Pattern.compile(pattern);
        return str -> str != null && compiled.matcher(str).matches();
    }

    /**
     * Create a custom regex validator with flags.
     *
     * @param pattern regex pattern
     * @param flags regex flags
     * @return regex validator
     */
    @NotNull
    public static Predicate<String> regex(@NotNull String pattern, int flags) {
        Pattern compiled = Pattern.compile(pattern, flags);
        return str -> str != null && compiled.matcher(str).matches();
    }
}
