package com.ghatana.datacloud.security;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API-layer route security metadata registry.
 *
 * @doc.type registry
 * @doc.purpose Provide explicit route security metadata with deterministic fallback classification
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class RouteSecurityRegistry {
    private static final Map<String, RouteSecurityMetadata> ROUTES = new ConcurrentHashMap<>();

    private RouteSecurityRegistry() {
    }

    public static void register(RouteSecurityMetadata metadata) {
        ROUTES.put(key(metadata.method(), metadata.path()), metadata);
    }

    public static Optional<RouteSecurityMetadata> lookupWithFallback(String method, String path) {
        RouteSecurityMetadata explicit = ROUTES.get(key(method, path));
        if (explicit != null) {
            return Optional.of(explicit);
        }

        EndpointSensitivity sensitivity = EndpointSensitivity.classify(method, path);
        RouteSecurityMetadata.AccessLevel accessLevel = sensitivity == EndpointSensitivity.CRITICAL
                ? RouteSecurityMetadata.AccessLevel.ADMIN
                : sensitivity == EndpointSensitivity.SENSITIVE
                    ? RouteSecurityMetadata.AccessLevel.OPERATOR
                    : RouteSecurityMetadata.AccessLevel.VIEWER;
        return Optional.of(new RouteSecurityMetadata(method == null ? "" : method, path == null ? "" : path, sensitivity, accessLevel));
    }

    private static String key(String method, String path) {
        return (method == null ? "" : method.toUpperCase(java.util.Locale.ROOT)) + " " + (path == null ? "" : path);
    }
}
