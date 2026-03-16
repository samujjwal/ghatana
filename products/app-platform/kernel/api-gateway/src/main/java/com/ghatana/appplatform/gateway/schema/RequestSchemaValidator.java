/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.schema;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Validates request bodies against registered OpenAPI schemas before forwarding to downstream
 * services (STORY-K11-012).
 *
 * <p>Schema validation at the gateway catches malformed payloads early — before they reach
 * domain services — reducing wasted processing and preventing injection via schema drift.
 *
 * <p>The schema registry is backed by {@link GatewaySchemaRegistry}. If no schema is
 * registered for a given route, the request is forwarded without validation (fail-open for
 * unregistered routes; fail-closed can be enabled per-route).
 *
 * @doc.type  class
 * @doc.purpose OpenAPI request body validation at the API gateway ingress (K11-012)
 * @doc.layer kernel
 * @doc.pattern Filter
 */
public final class RequestSchemaValidator implements com.ghatana.platform.http.server.filter.FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestSchemaValidator.class);

    private final GatewaySchemaRegistry schemaRegistry;

    public RequestSchemaValidator(GatewaySchemaRegistry schemaRegistry) {
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry, "schemaRegistry");
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String path   = request.getPath();
        String method = request.getMethod().name();
        String routeKey = method + ":" + path;

        GatewaySchemaRegistry.SchemaEntry schema = schemaRegistry.findSchema(routeKey);

        if (schema == null) {
            // No schema registered for this route — pass through
            return next.serve(request);
        }

        String contentType = request.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.contains("application/json")) {
            log.debug("Non-JSON content type on validated route: {} {}", method, path);
            return Promise.of(HttpResponse.ofCode(415)); // Unsupported Media Type
        }

        String body;
        try {
            body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read request body for schema validation: {}", e.getMessage());
            return Promise.of(HttpResponse.ofCode(400));
        }

        ValidationResult validationResult = schema.validate(body);
        if (!validationResult.valid()) {
            log.warn("Schema validation failed: route={} errors={}", routeKey, validationResult.errors());
            return Promise.of(HttpResponse.ofCode(400)
                    .withBody(("Schema validation failed: " + validationResult.errors())
                            .getBytes(StandardCharsets.UTF_8)));
        }

        return next.serve(request);
    }

    /** Result of validating a request body against a registered schema. */
    public record ValidationResult(boolean valid, String errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        public static ValidationResult fail(String errors) {
            return new ValidationResult(false, errors);
        }
    }
}
