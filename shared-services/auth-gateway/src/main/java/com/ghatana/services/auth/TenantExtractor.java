/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.model.User;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Map;

/**
 * Extracts tenant context from HTTP requests.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides multiple extraction strategies:
 * <ul>
 * <li><b>Header-based</b>: X-Tenant-Id header</li>
 * <li><b>JWT-based</b>: Extract from UserPrincipal metadata in JWT token</li>
 * <li><b>Path-based</b>: /tenants/:tenantId/... URL pattern</li>
 * <li><b>Subdomain-based</b>: tenant.example.com → "tenant"</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TenantExtractor extractor = new TenantExtractor();
 * JwtTokenProvider tokenProvider = new JwtTokenProvider(secret, expiryMs);
 *
 * // Extract from header
 * String tenantId = extractor.extractFromHeader(request);
 *
 * // Extract from JWT
 * String tenantId = extractor.extractFromJwt(request, tokenProvider);
 *
 * // Extract from path
 * String tenantId = extractor.extractFromPath(request);
 *
 * // Extract using fallback strategy
 * String tenantId = extractor.extract(request, tokenProvider);
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - stateless extraction logic
 *
 * @see com.ghatana.core.auth.JwtTokenProvider
 * @see com.ghatana.core.auth.UserPrincipal
 * @doc.type class
 * @doc.purpose Tenant context extraction from HTTP requests
 * @doc.layer product
 * @doc.pattern Extractor
 */
public class TenantExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantExtractor.class);

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TENANT_METADATA_KEY = "tenantId";

    /**
     * Extracts tenant ID from X-Tenant-Id header.
     *
     * GIVEN: HTTP request with X-Tenant-Id header WHEN: extractFromHeader() is
     * called THEN: Tenant ID returned if present, null otherwise
     *
     * @param request HTTP request
     * @return tenant ID or null if not present
     */
    public String extractFromHeader(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String tenantId = request.getHeader(HttpHeaders.of(TENANT_HEADER));

        if (tenantId != null && !tenantId.isBlank()) {
            LOGGER.debug("Extracted tenant ID from header: {}", tenantId);
            return tenantId;
        }

        LOGGER.debug("No tenant ID in X-Tenant-Id header");
        return null;
    }

    /**
     * Extracts tenant ID from JWT token in Authorization header.
     *
     * GIVEN: HTTP request with Authorization: Bearer &lt;jwt&gt; header WHEN:
     * extractFromJwt() is called THEN: Tenant ID extracted from JWT
     * UserPrincipal metadata
     *
     * @param request HTTP request
     * @param tokenProvider JWT token provider for validation
     * @return tenant ID or null if not present
     */
    public String extractFromJwt(HttpRequest request, JwtTokenProvider tokenProvider) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(tokenProvider, "tokenProvider must not be null");

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            LOGGER.debug("No Bearer token in Authorization header");
            return null;
        }

        try {
            String token = authHeader.substring(BEARER_PREFIX.length());

            if (!tokenProvider.validateToken(token)) {
                LOGGER.debug("Invalid JWT token");
                return null;
            }

            // Extract tenant from claims
            var claims = tokenProvider.extractClaims(token);
            if (claims.isPresent()) {
                Object tenantId = claims.get().get(TENANT_METADATA_KEY);
                if (tenantId != null && !tenantId.toString().isBlank()) {
                    LOGGER.debug("Extracted tenant ID from JWT: {}", tenantId);
                    return tenantId.toString();
                }
            }

            LOGGER.debug("No tenantId in JWT claims");
            return null;

        } catch (Exception ex) {
            LOGGER.error("Failed to extract tenant ID from JWT", ex);
            return null;
        }
    }

    /**
     * Extracts tenant ID from URL path pattern: /tenants/:tenantId/...
     *
     * GIVEN: HTTP request with path containing tenant ID WHEN:
     * extractFromPath() is called THEN: Tenant ID extracted from path segment
     *
     * @param request HTTP request
     * @return tenant ID or null if not present
     */
    public String extractFromPath(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String path = request.getPath();

        // Pattern: /tenants/:tenantId/...
        if (path.startsWith("/tenants/")) {
            String[] segments = path.split("/");
            if (segments.length >= 3) {
                String tenantId = segments[2];
                LOGGER.debug("Extracted tenant ID from path: {}", tenantId);
                return tenantId;
            }
        }

        LOGGER.debug("No tenant ID in request path");
        return null;
    }

    /**
     * Extracts tenant ID from subdomain: tenant.example.com → "tenant".
     *
     * GIVEN: HTTP request with subdomain WHEN: extractFromSubdomain() is called
     * THEN: Subdomain returned as tenant ID
     *
     * @param request HTTP request
     * @return tenant ID (subdomain) or null if not present
     */
    public String extractFromSubdomain(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String host = request.getHeader(HttpHeaders.HOST);

        if (host == null) {
            LOGGER.debug("No Host header in request");
            return null;
        }

        // Extract subdomain from host (e.g., tenant.example.com → tenant)
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            String subdomain = parts[0];
            LOGGER.debug("Extracted tenant ID from subdomain: {}", subdomain);
            return subdomain;
        }

        LOGGER.debug("No subdomain in Host header");
        return null;
    }

    /**
     * Extracts tenant ID using fallback strategy: header → JWT → path →
     * subdomain.
     *
     * GIVEN: HTTP request WHEN: extract() is called THEN: Tenant ID extracted
     * using first successful strategy
     *
     * @param request HTTP request
     * @param tokenProvider JWT token provider (optional, can be null)
     * @return tenant ID or null if not found
     */
    public String extract(HttpRequest request, JwtTokenProvider tokenProvider) {
        Objects.requireNonNull(request, "request must not be null");

        // Try header first
        String tenantId = extractFromHeader(request);
        if (tenantId != null) {
            return tenantId;
        }

        // Try JWT if provider available
        if (tokenProvider != null) {
            tenantId = extractFromJwt(request, tokenProvider);
            if (tenantId != null) {
                return tenantId;
            }
        }

        // Try path
        tenantId = extractFromPath(request);
        if (tenantId != null) {
            return tenantId;
        }

        // Try subdomain
        tenantId = extractFromSubdomain(request);
        if (tenantId != null) {
            return tenantId;
        }

        LOGGER.warn("Failed to extract tenant ID from request: {}", request.getPath());
        return null;
    }
}
