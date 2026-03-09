package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Comprehensive string manipulation utilities for common transformations and validations.
 * 
 * Provides production-grade string operations including null-safe checks, case conversions
 * (camelCase, snake_case, kebab-case, PascalCase), joining/truncation, and generation.
 * 
 * All methods are static and stateless. Thread-safe for concurrent use.
 *
 * @doc.type class
 * @doc.purpose Comprehensive string manipulation utilities for common transformations and validations
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class StringUtils {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
    private static final String HYPHEN = "-";
    private static final String DOUBLE_HYPHEN = HYPHEN + HYPHEN;

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Check if a string is null, empty, or contains only whitespace.
     */
    public static boolean isBlank(@Nullable String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Check if a string is not null, not empty, and contains non-whitespace characters.
     */
    public static boolean isNotBlank(@Nullable String str) {
        return !isBlank(str);
    }

    /**
     * Return the first non-blank string from the given strings.
     */
    @Nullable
    public static String firstNonBlank(@Nullable String... strings) {
        if (strings == null) {
            return null;
        }
        for (String str : strings) {
            if (isNotBlank(str)) {
                return str;
            }
        }
        return null;
    }

    /**
     * Return the first non-null string from the given strings.
     */
    @Nullable
    public static String firstNonNull(@Nullable String... strings) {
        if (strings == null) {
            return null;
        }
        for (String str : strings) {
            if (str != null) {
                return str;
            }
        }
        return null;
    }

    /**
     * Return the string if not blank, otherwise return the default value.
     */
    @NotNull
    public static String defaultIfBlank(@Nullable String str, @NotNull String defaultValue) {
        return isNotBlank(str) ? str : defaultValue;
    }

    /**
     * Return the string if not blank, otherwise compute and return the default value.
     */
    @NotNull
    public static String defaultIfBlank(@Nullable String str, @NotNull Supplier<String> supplier) {
        return isNotBlank(str) ? str : supplier.get();
    }

    /**
     * Join a collection of strings with a delimiter, skipping null elements.
     */
    @NotNull
    public static String join(@Nullable Collection<String> collection, @NotNull String delimiter) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String str : collection) {
            if (str != null) {
                if (!first) {
                    sb.append(delimiter);
                }
                sb.append(str);
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Convert a string to snake_case.
     * Example: "userId" → "user_id", "HTTPSConnection" → "https_connection"
     */
    @Nullable
    public static String toSnakeCase(@Nullable String str) {
        if (isBlank(str)) {
            return str;
        }
        str = str.replace('-', '_');
        str = str.replaceAll(" ", "_");
        return CAMEL_CASE_PATTERN
            .matcher(str)
            .replaceAll("_")
            .toLowerCase();
    }

    /**
     * Convert a string to kebab-case.
     * Example: "userId" → "user-id", "HTTPSConnection" → "https-connection"
     */
    @Nullable
    public static String toKebabCase(@Nullable String str) {
        if (isBlank(str)) {
            return str;
        }
        str = str.replace(" ", HYPHEN);
        str = str.replace("_", DOUBLE_HYPHEN);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(HYPHEN).append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Convert a string to camelCase.
     * Example: "user_id" → "userId", "user-profile" → "userProfile"
     */
    @Nullable
    public static String toCamelCase(@Nullable String str) {
        if (isBlank(str)) {
            return str;
        }
        String[] parts = str.split("(?<=[ _-])|(?=[ _-])|(?<=[a-z])(?=[A-Z])");
        StringBuilder result = new StringBuilder();
        boolean firstWord = true;
        for (String part : parts) {
            if (part.equals(" ") || part.equals("-") || part.equals("_")) {
                continue;
            }
            if (firstWord) {
                result.append(part.toLowerCase());
                firstWord = false;
            } else {
                result.append(capitalizeWord(part));
            }
        }
        return result.toString();
    }

    /**
     * Convert a string to PascalCase.
     * Example: "user_id" → "UserId", "user-profile" → "UserProfile"
     */
    @Nullable
    public static String toPascalCase(@Nullable String str) {
        if (isBlank(str)) {
            return str;
        }
        String[] parts = str.split("(?<=[ _-])|(?=[ _-])|(?<=[a-z])(?=[A-Z])");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.equals(" ") || part.equals("-") || part.equals("_")) {
                continue;
            }
            result.append(capitalizeWord(part));
        }
        return result.toString();
    }

    /**
     * Generate a random alphanumeric string of the given length.
     * Note: Not cryptographically secure. Use SecureRandom for security-sensitive operations.
     */
    @NotNull
    public static String randomAlphanumeric(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Generate a random UUID string.
     */
    @NotNull
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Truncate a string to the given maximum length, appending "..." if truncated.
     */
    @Nullable
    public static String truncate(@Nullable String str, int maxLength) {
        if (str == null || maxLength < 0) {
            return str;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Repeat a string the given number of times.
     */
    @NotNull
    public static String repeat(@Nullable String str, int times) {
        if (str == null || times <= 0) {
            return "";
        }
        return str.repeat(times);
    }

    /**
     * Check if a string contains any of the given substrings.
     */
    public static boolean containsAny(@Nullable String str, @Nullable String... substrings) {
        if (str == null || substrings == null || str.isEmpty()) {
            return false;
        }
        for (String substring : substrings) {
            if (substring != null && !substring.isEmpty() && str.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a string equals any of the given values.
     */
    public static boolean equalsAny(@Nullable String str, @Nullable String... values) {
        if (values == null) {
            return str == null;
        }
        if (str == null) {
            for (String value : values) {
                if (value == null) {
                    return true;
                }
            }
            return false;
        }
        for (String value : values) {
            if (str.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String capitalizeWord(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) +
               (word.length() > 1 ? word.substring(1).toLowerCase() : "");
    }
}
