package com.ghatana.datacloud.security;

import java.util.Objects;

/**
 * Security metadata for a Data Cloud API route.
 *
 * @doc.type record
 * @doc.purpose Capture route sensitivity and required access level for API security evaluation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record RouteSecurityMetadata(
        String method,
        String path,
        EndpointSensitivity sensitivity,
        AccessLevel requiredAccess
) {
    public RouteSecurityMetadata {
        method = Objects.requireNonNull(method, "method").toUpperCase(java.util.Locale.ROOT);
        path = Objects.requireNonNull(path, "path");
        sensitivity = Objects.requireNonNull(sensitivity, "sensitivity");
        requiredAccess = Objects.requireNonNull(requiredAccess, "requiredAccess");
    }

    public enum AccessLevel {
        VIEWER,
        OPERATOR,
        AUDITOR,
        ADMIN
    }
}
