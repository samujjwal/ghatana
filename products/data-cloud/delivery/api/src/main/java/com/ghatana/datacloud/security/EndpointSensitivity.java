package com.ghatana.datacloud.security;

import java.util.Locale;

/**
 * API-layer endpoint sensitivity classification used by security policy evaluation.
 *
 * @doc.type enum
 * @doc.purpose Classify Data Cloud API endpoints for authentication, audit, and policy checks
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum EndpointSensitivity {
    PUBLIC,
    INTERNAL,
    SENSITIVE,
    CRITICAL;

    public static EndpointSensitivity classify(String method, String path) {
        String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);

        if (normalizedPath.endsWith("/health") || normalizedPath.contains("/health/")) {
            return PUBLIC;
        }

        if ("DELETE".equals(normalizedMethod)
                || normalizedPath.contains("/admin")
                || normalizedPath.contains("/policy")
                || normalizedPath.contains("/break-glass")) {
            return CRITICAL;
        }

        if ("POST".equals(normalizedMethod)
                || "PUT".equals(normalizedMethod)
                || "PATCH".equals(normalizedMethod)
                || normalizedPath.contains("/security")
                || normalizedPath.contains("/audit")) {
            return SENSITIVE;
        }

        return INTERNAL;
    }
}
