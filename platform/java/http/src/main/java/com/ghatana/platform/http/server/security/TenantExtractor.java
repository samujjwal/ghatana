/*
 * Copyright (c) 2025-2026 Ghatana Technologies
 * Platform HTTP Module
 */
package com.ghatana.platform.http.server.security;

import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared tenant-id extractor for HTTP and gRPC requests.
 *
 * <p><b>Purpose</b><br>
 * Provides a single, consistent extraction point for tenant identifiers.
 * All products MUST use this class rather than implementing inline
 * {@code extractTenantId()} methods.
 *
 * <p><b>Header Standard</b><br>
 * The canonical header name is {@value #TENANT_HEADER_NAME}
 * ({@code X-Tenant-Id}).  For gRPC metadata the key name is
 * {@value #GRPC_TENANT_KEY} ({@code x-tenant-id}).
 *
 * <p><b>Usage – HTTP</b><br>
 * <pre>{@code
 * // Optional extraction
 * Optional<String> tenant = TenantExtractor.fromHttp(request);
 *
 * // With default
 * String tenant = TenantExtractor.fromHttpOrDefault(request, "default");
 *
 * // Require or fail (returns 400)
 * String tenant = TenantExtractor.fromHttpOrThrow(request);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * This class is a stateless utility; all methods are thread-safe.
 *
 * @doc.type class
 * @doc.purpose Shared tenant-id extraction
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class TenantExtractor {

    /** Canonical HTTP header name for tenant id. */
    public static final String TENANT_HEADER_NAME = "X-Tenant-Id";

    /** Pre-built ActiveJ header constant for efficient header access. */
    public static final HttpHeader TENANT_HEADER =
            HttpHeaders.of(TENANT_HEADER_NAME);

    /** gRPC metadata key name (lower-case per gRPC convention). */
    public static final String GRPC_TENANT_KEY = "x-tenant-id";

    private TenantExtractor() {
        // Utility class
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HTTP
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Extracts tenant id from HTTP request header.
     *
     * @param request the ActiveJ HTTP request
     * @return optional tenant id; empty when header is absent or blank
     */
    public static Optional<String> fromHttp(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String value = request.getHeader(TENANT_HEADER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.strip());
    }

    /**
     * Extracts tenant id from HTTP request, returning a default when absent.
     *
     * @param request      the ActiveJ HTTP request
     * @param defaultValue fallback tenant id
     * @return tenant id from header, or {@code defaultValue}
     */
    public static String fromHttpOrDefault(HttpRequest request, String defaultValue) {
        return fromHttp(request).orElse(defaultValue);
    }

    /**
     * Extracts tenant id from HTTP request, throwing if absent.
     *
     * @param request the ActiveJ HTTP request
     * @return tenant id
     * @throws MissingTenantException if the header is absent or blank
     */
    public static String fromHttpOrThrow(HttpRequest request) {
        return fromHttp(request)
                .orElseThrow(() -> new MissingTenantException(
                        "Missing required header: " + TENANT_HEADER_NAME));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Exception
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Thrown when a required tenant id header is missing from an HTTP request.
     */
    public static final class MissingTenantException extends RuntimeException {
        public MissingTenantException(String message) {
            super(message);
        }
    }
}
