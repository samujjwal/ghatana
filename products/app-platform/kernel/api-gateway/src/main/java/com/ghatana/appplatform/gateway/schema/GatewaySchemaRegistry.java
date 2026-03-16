/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Per-service schema cache for request validation at the API gateway (STORY-K11-013).
 *
 * <p>Schemas are keyed by route key: {@code "{HTTP_METHOD}:{path_pattern}"}
 * (e.g. {@code "POST:/api/v1/trades"}). Each schema entry wraps a validation function
 * that is called by {@link RequestSchemaValidator} for every matching request.
 *
 * <p>Schema entry lifecycle:
 * <ol>
 *   <li>{@link #register(String, SchemaEntry)} — called at service boot or config reload</li>
 *   <li>{@link #findSchema(String)} — called per request by the validator filter</li>
 *   <li>{@link #deregister(String)} — called when a service is gracefully removed</li>
 * </ol>
 *
 * <p>Schema loading from OpenAPI spec files is handled by the service bootstrap module
 * (outside this class). This registry only manages the validated-schema entries.
 *
 * @doc.type  class
 * @doc.purpose In-memory route → schema mapping for gateway request validation (K11-013)
 * @doc.layer kernel
 * @doc.pattern Registry
 */
public final class GatewaySchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewaySchemaRegistry.class);

    private final Map<String, SchemaEntry> schemasByRouteKey = new ConcurrentHashMap<>();

    /**
     * Registers a schema entry for the given route key.
     *
     * @param routeKey  key in the form {@code "METHOD:/path"} (e.g. {@code "POST:/api/v1/trades"})
     * @param entry     schema entry containing the validation logic
     */
    public void register(String routeKey, SchemaEntry entry) {
        Objects.requireNonNull(routeKey, "routeKey");
        Objects.requireNonNull(entry,    "entry");
        schemasByRouteKey.put(routeKey, entry);
        log.info("Schema registered for route: {}", routeKey);
    }

    /**
     * Resolves the schema entry for a request's route key, or {@code null} when none
     * is registered for that route.
     *
     * @param routeKey the request's route key
     * @return matching {@link SchemaEntry} or {@code null}
     */
    public SchemaEntry findSchema(String routeKey) {
        // Exact match first
        SchemaEntry exact = schemasByRouteKey.get(routeKey);
        if (exact != null) return exact;

        // Fallback: find a registered prefix that matches the path portion
        String[] parts = routeKey.split(":", 2);
        if (parts.length < 2) return null;
        String method = parts[0];
        String path   = parts[1];

        for (Map.Entry<String, SchemaEntry> e : schemasByRouteKey.entrySet()) {
            String[] keyParts = e.getKey().split(":", 2);
            if (keyParts.length == 2 && keyParts[0].equals(method)) {
                String pattern = keyParts[1];
                // Simple wildcard: if registered pattern ends with /** match prefix
                if (pattern.endsWith("/**") && path.startsWith(pattern.substring(0, pattern.length() - 3))) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Removes a schema registration.
     *
     * @param routeKey the route to deregister
     */
    public void deregister(String routeKey) {
        schemasByRouteKey.remove(routeKey);
        log.info("Schema deregistered for route: {}", routeKey);
    }

    /** Returns the number of registered schema entries. */
    public int size() {
        return schemasByRouteKey.size();
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * An immutable schema entry that holds the service name, schema name, version,
     * and a validation function constructed from the parsed OpenAPI spec.
     */
    public record SchemaEntry(
            String serviceName,
            String schemaName,
            String schemaVersion,
            /** Validates a raw JSON request body string; returns a {@link RequestSchemaValidator.ValidationResult}. */
            Function<String, RequestSchemaValidator.ValidationResult> validator
    ) {
        /**
         * Validates the given JSON body against this entry's schema.
         *
         * @param jsonBody raw request body
         * @return validation result
         */
        public RequestSchemaValidator.ValidationResult validate(String jsonBody) {
            try {
                return validator.apply(jsonBody);
            } catch (Exception e) {
                return RequestSchemaValidator.ValidationResult.fail("Validator threw exception: " + e.getMessage());
            }
        }
    }
}
