package com.ghatana.platform.security.validation;

import com.ghatana.core.util.PiiRedactor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Kernel-owned redaction helpers for safe logs and audit metadata.
 *
 * @doc.type class
 * @doc.purpose Shared redaction primitives for strings and structured metadata
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class SafeLogRedactor {

    private static final String MASK = "****";
    private static final Set<String> SENSITIVE_KEY_TOKENS = Set.of(
        "authorization",
        "password",
        "passwd",
        "secret",
        "token",
        "api_key",
        "apikey",
        "access_token",
        "refresh_token",
        "ssn",
        "pan",
        "card",
        "credit_card"
    );

    private SafeLogRedactor() {
    }

    public static String redactText(String value) {
        return PiiRedactor.redact(value);
    }

    public static Map<String, Object> redactMetadata(Map<String, ?> metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        LinkedHashMap<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metadata keys must not be blank");
            }
            redacted.put(key, redactValue(key, entry.getValue()));
        }
        return Collections.unmodifiableMap(redacted);
    }

    public static boolean containsSensitiveText(String value) {
        return PiiRedactor.containsPii(value);
    }

    private static Object redactValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return MASK;
        }
        if (value instanceof String stringValue) {
            return redactText(stringValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            LinkedHashMap<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nestedEntry : mapValue.entrySet()) {
                String nestedKey = String.valueOf(nestedEntry.getKey());
                nested.put(nestedKey, redactValue(nestedKey, nestedEntry.getValue()));
            }
            return Collections.unmodifiableMap(nested);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        for (String token : SENSITIVE_KEY_TOKENS) {
            if (normalized.equals(token) || normalized.endsWith("_" + token) || normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
