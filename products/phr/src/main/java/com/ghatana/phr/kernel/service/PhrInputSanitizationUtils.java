package com.ghatana.phr.kernel.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Shared boundary sanitization and validation helpers for PHR service inputs
 * @doc.layer product
 * @doc.pattern Utils
 */
public final class PhrInputSanitizationUtils {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_:@./#-]{0,127}$");
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_./-]{0,127}$");
    private static final Pattern SAFE_CONTENT_TYPE = Pattern.compile(
        "^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final int MAX_STRUCTURE_DEPTH = 5;

    private PhrInputSanitizationUtils() {}

    public static String requireSafeIdentifier(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
        return normalized;
    }

    public static String requireSafeCode(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName);
        if (!SAFE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
        return normalized;
    }

    public static String requireAllowedValue(String value, String fieldName, Set<String> allowedValues) {
        String normalized = requireNonBlank(value, fieldName);
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(fieldName + " must be one of " + allowedValues);
        }
        return normalized;
    }

    public static String sanitizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeText(value, fieldName, true, maxLength);
        return escapeHtml(normalized);
    }

    public static String sanitizeOptionalText(String value, String fieldName, int maxLength) {
        String normalized = normalizeText(value, fieldName, false, maxLength);
        return normalized == null ? null : escapeHtml(normalized);
    }

    public static String requireHttpsUrl(String value, String fieldName) {
        String normalized = sanitizeOptionalText(value, fieldName, 2048);
        if (normalized == null) {
            return null;
        }
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " must start with https://");
        }
        return normalized;
    }

    public static String requireContentType(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName);
        if (!SAFE_CONTENT_TYPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a valid content type");
        }
        return normalized;
    }

    public static byte[] requireBinaryContent(byte[] content, String fieldName, int maxBytes) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (content.length > maxBytes) {
            throw new IllegalArgumentException(fieldName + " exceeds max size of " + maxBytes + " bytes");
        }
        return content;
    }

    public static Map<String, Object> sanitizeStructuredData(Map<String, Object> data, String fieldName) {
        Objects.requireNonNull(data, fieldName + " must not be null");
        return sanitizeMap(data, fieldName, 0);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> input, String fieldName, int depth) {
        if (depth > MAX_STRUCTURE_DEPTH) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum nesting depth");
        }

        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = requireSafeCode(String.valueOf(entry.getKey()), fieldName + ".key");
            sanitized.put(key, sanitizeValue(entry.getValue(), fieldName + "." + key, depth + 1));
        }
        return Map.copyOf(sanitized);
    }

    private static Object sanitizeValue(Object value, String fieldName, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return sanitizeOptionalText(stringValue, fieldName, 4000);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return sanitizeMap(mapValue, fieldName, depth);
        }
        if (value instanceof Iterable<?> iterableValue) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterableValue) {
                sanitized.add(sanitizeValue(item, fieldName + "[]", depth + 1));
            }
            return List.copyOf(sanitized);
        }
        if (value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof Instant
                || value instanceof UUID
                || value instanceof BigDecimal) {
            return value;
        }
        return sanitizeOptionalText(String.valueOf(value), fieldName, 4000);
    }

    private static String requireNonBlank(String value, String fieldName) {
        String normalized = normalizeText(value, fieldName, true, 256);
        return normalized;
    }

    private static String normalizeText(String value, String fieldName, boolean required, int maxLength) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return null;
        }

        String normalized = CONTROL_CHARS.matcher(value.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("").trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (!required && normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length of " + maxLength);
        }
        return normalized;
    }

    private static String escapeHtml(String input) {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}