/*
 * Copyright (c) 2025 Ghatana
 */
package com.ghatana.services.auth;

import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

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
 * @doc.layer platform
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
     * Also supports development URLs like tenant.localhost.
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
            // Fall back to URL host for programmatically built requests
            String hostAndPort = request.getHostAndPort();
            if (hostAndPort != null && !hostAndPort.isBlank()) {
                host = hostAndPort;
            }
        }

        if (host == null) {
            LOGGER.debug("No Host header or URL host in request");
            return null;
        }

        // Remove port if present
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) {
            host = host.substring(0, colonIdx);
        }

        // Extract subdomain from host (e.g., tenant.example.com → tenant).
        // Also support development hosts like tenant.localhost (2 parts).
        String[] parts = host.split("\\.");
        if (parts.length >= 3
                || (parts.length == 2 && "localhost".equals(parts[parts.length - 1]))) {
            String subdomain = parts[0];
            LOGGER.debug("Extracted tenant ID from subdomain: {}", subdomain);
            return subdomain;
        }

        LOGGER.debug("No subdomain in Host header");
        return null;
    }

    /**
     * Extracts tenant ID using fallback strategy: header → path → subdomain.
     * Returns a Promise wrapping an Optional with the first resolved tenant ID.
     *
     * @param request HTTP request
     * @return promise of optional tenant ID; never null
     */
    public Promise<Optional<String>> extractTenant(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // Header strategy (highest priority)
        String tenantId = extractFromHeader(request);
        if (tenantId != null && isValidTenantId(tenantId)) {
            return Promise.of(Optional.of(sanitizeTenantId(tenantId)));
        }

        // Path strategy
        tenantId = extractFromPath(request);
        if (tenantId != null && isValidTenantId(tenantId)) {
            return Promise.of(Optional.of(sanitizeTenantId(tenantId)));
        }

        // Subdomain strategy
        tenantId = extractFromSubdomain(request);
        if (tenantId != null && isValidTenantId(tenantId)) {
            return Promise.of(Optional.of(sanitizeTenantId(tenantId)));
        }

        return Promise.of(Optional.empty());
    }

    /**
     * Validates that the given tenant ID conforms to the allowed format:
     * non-null, non-blank, only alphanumeric characters, hyphens, and underscores.
     *
     * @param tenantId the tenant ID string to validate
     * @return {@code true} if valid, {@code false} otherwise
     */
    public static boolean isValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return tenantId.matches("[A-Za-z0-9_-]+");
    }

    /**
     * Sanitizes a tenant ID by trimming whitespace and converting to lowercase.
     *
     * @param tenantId the raw tenant ID
     * @return sanitized tenant ID
     */
    public static String sanitizeTenantId(String tenantId) {
        return tenantId.trim().toLowerCase();
    }

    /**
     * Extracts tenant ID using fallback strategy: header → JWT → path →
     * subdomain.
     *
     * <p><b>DEPRECATED</b> - Use {@link #extractForUserPath(HttpRequest, JwtTokenProvider)}
     * for user-facing paths. This method allows unsigned path-based extraction
     * which is insecure for user-facing endpoints. Path-based extraction should
     * only be used for internal system paths where tenant context is optional.</p>
     *
     * GIVEN: HTTP request WHEN: extract() is called THEN: Tenant ID extracted
     * using first successful strategy
     *
     * @param request HTTP request
     * @param tokenProvider JWT token provider (optional, can be null)
     * @return tenant ID or null if not found
     * @deprecated Use {@link #extractForUserPath(HttpRequest, JwtTokenProvider)} for user-facing paths
     */
    @Deprecated
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

        // Try path (insecure for user-facing paths)
        tenantId = extractFromPath(request);
        if (tenantId != null) {
            return tenantId;
        }

        // Try subdomain (insecure for user-facing paths)
        tenantId = extractFromSubdomain(request);
        if (tenantId != null) {
            return tenantId;
        }

        LOGGER.warn("Failed to extract tenant ID from request: {}", request.getPath());
        return null;
    }

    /**
     * Extracts tenant ID for user-facing paths using only signed sources.
     *
     * <p><b>Security Requirement</b><br>
     * For user-facing paths, tenant ID MUST come from signed sources only:
     * <ul>
     * <li>JWT token claims (signed by auth provider)</li>
     * <li>X-Tenant-Id header (signed/trusted by gateway)</li>
     * </ul>
     * Path-based and subdomain-based extraction are NOT allowed for user-facing
     * paths as they are unsigned and can be spoofed.</p>
     *
     * <p><b>Usage</b><br>
     * Use this method for all user-facing API endpoints. Use {@link #extract(HttpRequest, JwtTokenProvider)}
     * only for internal system paths where tenant context is optional.</p>
     *
     * GIVEN: HTTP request WHEN: extractForUserPath() is called THEN: Tenant ID
     * extracted only from signed sources (JWT or header)
     *
     * @param request HTTP request
     * @param tokenProvider JWT token provider (optional, can be null)
     * @return tenant ID from signed sources, or null if not found
     * @throws SecurityException if path-based or subdomain-based tenant is detected
     */
    public String extractForUserPath(HttpRequest request, JwtTokenProvider tokenProvider) {
        Objects.requireNonNull(request, "request must not be null");

        // Try header first (signed/trusted by gateway)
        String tenantId = extractFromHeader(request);
        if (tenantId != null) {
            return tenantId;
        }

        // Try JWT if provider available (signed by auth provider)
        if (tokenProvider != null) {
            tenantId = extractFromJwt(request, tokenProvider);
            if (tenantId != null) {
                return tenantId;
            }
        }

        // Reject path-based tenant extraction for user-facing paths
        String pathTenant = extractFromPath(request);
        if (pathTenant != null) {
            LOGGER.error("SECURITY: Path-based tenant extraction rejected for user-facing path: {}", request.getPath());
            throw new SecurityException("Path-based tenant extraction not allowed for user-facing paths. Use signed JWT or X-Tenant-Id header.");
        }

        // Reject subdomain-based tenant extraction for user-facing paths
        String subdomainTenant = extractFromSubdomain(request);
        if (subdomainTenant != null) {
            LOGGER.error("SECURITY: Subdomain-based tenant extraction rejected for user-facing path: {}", request.getPath());
            throw new SecurityException("Subdomain-based tenant extraction not allowed for user-facing paths. Use signed JWT or X-Tenant-Id header.");
        }

        LOGGER.warn("Failed to extract tenant ID from signed sources for user-facing path: {}", request.getPath());
        return null;
    }
}
