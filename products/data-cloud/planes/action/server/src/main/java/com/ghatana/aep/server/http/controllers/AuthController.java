/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.security.AepAuthFilter;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for authentication and session management endpoints.
 *
 * <p>F-008: Provides `/api/v1/auth/platform-session` to bootstrap
 * short-lived platform session tokens when the UI loads, gating on
 * SSO configuration and feature flags.
 *
 * @doc.type class
 * @doc.purpose Authentication and session token lifecycle
 * @doc.layer product
 * @doc.pattern Controller
 */
public class AuthController implements AepController {

    private final SessionTokenManager sessionTokenManager;

    /**
     * Creates a new AuthController with the given session token manager.
     *
     * @param sessionTokenManager manages session token issuance and validation
     */
    public AuthController(SessionTokenManager sessionTokenManager) {
        this.sessionTokenManager = sessionTokenManager;
    }

    @Override
    public String getBasePath() {
        return "/api/v1/auth";
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, String path) throws Exception {
        if (request.getMethod() != HttpMethod.GET) {
            return Promise.of(HttpHelper.errorResponse(405, "Method not allowed"));
        }

        if ("platform-session".equals(path)) {
            return handlePlatformSession(request);
        }

        if ("roles".equals(path)) {
            return handleGetRoles(request);
        }

        return Promise.of(HttpHelper.errorResponse(404, "Not found"));
    }

    /**
     * Handles GET /api/v1/auth/platform-session.
     *
     * <p>F-008: Issues a short-lived platform session token bound to the current tenant.
     * This endpoint is gated behind the {@code AEP_SSO_CONFIGURED} feature flag to ensure
     * platform-session tokens are only issued when SSO is actively configured.
     *
     * <p>The token is scoped to the tenant extracted from the request JWT. If no tenant is
     * resolved or SSO is not configured, returns 403 (Forbidden).
     *
     * <p>Response structure:
     * <pre>
     * {
     *   "session": "<token>",
     *   "expiresInSeconds": 3600,
     *   "issuedAt": "2026-04-27T..."
     * }
     * </pre>
     *
     * @param request the HTTP request (authentication context attached by filter)
     * @return JSON response with session token and expiration, or 503 on SSO not configured
     */
    private Promise<HttpResponse> handlePlatformSession(HttpRequest request) {
        // F-008: Gate on SSO configuration environment variable
        String ssoConfigured = System.getenv().getOrDefault("AEP_SSO_CONFIGURED", "false");
        if (!"true".equalsIgnoreCase(ssoConfigured)) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Platform session bootstrap unavailable; SSO not configured"));
        }

        // Authentication filter ensures context; extract tenant from verified JWT
        String tenantId = HttpHelper.resolveTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(403, "Tenant ID required for platform session"));
        }

        // Issue a new session token scoped to this tenant
        String sessionToken = sessionTokenManager.issueSessionToken(tenantId);
        long expiresInSeconds = sessionTokenManager.getSessionDurationSeconds();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session", sessionToken);
        response.put("expiresInSeconds", expiresInSeconds);
        response.put("issuedAt", Instant.now().toString());

        return Promise.of(HttpHelper.jsonResponse(response));
    }

    /**
     * Handles GET /api/v1/auth/roles.
     *
     * <p>F-032: Returns the roles assigned to the current authenticated user.
     * Roles are extracted from the JWT claims and are used by the UI to gate
     * sensitive actions such as kill-switch activation, publish operations, and
     * configuration changes.
     *
     * <p>Response structure:
     * <pre>
     * {
     *   "roles": ["OPERATOR", "ADMIN"],
     *   "retrievedAt": "2026-04-27T..."
     * }
     * </pre>
     *
     * @param request the HTTP request (must be authenticated)
     * @return JSON response with user's roles
     */
    private Promise<HttpResponse> handleGetRoles(HttpRequest request) {
        // F-032: Extract roles from the verified JWT payload attached by AepAuthFilter.
        // Falls back to an empty list when auth is disabled (dev mode only).
        AepAuthFilter.JwtPayload jwtPayload =
            (AepAuthFilter.JwtPayload) request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT);

        List<String> roles = (jwtPayload != null && jwtPayload.roles() != null)
            ? jwtPayload.roles()
            : List.of();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roles", roles);
        response.put("sub", jwtPayload != null ? jwtPayload.sub() : null);
        response.put("retrievedAt", Instant.now().toString());

        return Promise.of(HttpHelper.jsonResponse(response));
    }

    /**
     * Manages session token issuance and validation.
     *
     * <p>Uses an in-memory store with TTL-based expiry. In production,
     * this should delegate to a durable session store (Redis or DB).
     */
    public interface SessionTokenManager {
        /**
         * Issues a new session token for the given tenant.
         * The token is valid for {@link #getSessionDurationSeconds()} seconds.
         *
         * @param tenantId the tenant ID to scope this session to
         * @return a new session token (opaque string)
         */
        String issueSessionToken(String tenantId);

        /**
         * Returns the lifetime of issued session tokens in seconds.
         */
        long getSessionDurationSeconds();

        /**
         * Validates that the given token belongs to the given tenant.
         * Returns true if token is valid and has not expired.
         *
         * @param token the token to validate
         * @param tenantId the expected tenant
         * @return true if token is valid for this tenant
         */
        boolean validateSessionToken(String token, String tenantId);
    }

    /**
     * Default in-memory session token manager.
     *
     * <p>Uses a bounded concurrent map with manual TTL cleanup.
     * Production deployments should use {@code RedisSessionTokenManager}
     * or a database-backed implementation.
     */
    public static final class InMemorySessionTokenManager implements SessionTokenManager {
        private static final long DURATION_SECONDS = 3600L;  // 1 hour
        private final Map<String, SessionEntry> sessions = new java.util.concurrent.ConcurrentHashMap<>();

        private static final class SessionEntry {
            final String tenantId;
            final long expiresAtMs;

            SessionEntry(String tenantId, long expiresAtMs) {
                this.tenantId = tenantId;
                this.expiresAtMs = expiresAtMs;
            }

            boolean isExpired() {
                return System.currentTimeMillis() > expiresAtMs;
            }
        }

        @Override
        public String issueSessionToken(String tenantId) {
            String token = UUID.randomUUID().toString();
            long expiresAtMs = System.currentTimeMillis() + DURATION_SECONDS * 1000L;
            sessions.put(token, new SessionEntry(tenantId, expiresAtMs));

            // Opportunistic cleanup of expired entries
            if (sessions.size() > 10_000) {
                sessions.entrySet().removeIf(e -> e.getValue().isExpired());
            }

            return token;
        }

        @Override
        public long getSessionDurationSeconds() {
            return DURATION_SECONDS;
        }

        @Override
        public boolean validateSessionToken(String token, String tenantId) {
            SessionEntry entry = sessions.get(token);
            if (entry == null) return false;
            if (entry.isExpired()) {
                sessions.remove(token);
                return false;
            }
            return tenantId.equals(entry.tenantId);
        }
    }
}

