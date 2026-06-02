package com.ghatana.platform.security.session;

import io.activej.http.HttpRequest;
import io.activej.promise.Promise;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kernel session context resolver for authenticated identity resolution.
 * <p>
 * This resolver provides a centralized, server-authenticated path for extracting
 * tenant, principal, role, persona, tier, facility, and correlation ID from a
 * verified session. It replaces product-local identity header parsing with a
 * single Kernel-owned authentication boundary.
 * </p>
 *
 * <h2>Security Contract</h2>
 * <ul>
 *   <li>Session must be verified through SessionManager before context extraction</li>
 *   <li>Expired or invalid sessions result in empty Optional</li>
 *   <li>Identity is server-authenticated, not client-authored</li>
 *   <li>Correlation ID is generated server-side if not provided</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * KernelSessionContextResolver resolver = new KernelSessionContextResolver(sessionManager);
 *
 * // Resolve from HTTP request with session cookie
 * Optional<KernelSessionContext> context = resolver.resolve(request);
 *
 * if (context.isPresent()) {
 *     KernelSessionContext ctx = context.get();
 *     String tenantId = ctx.tenantId();
 *     String principalId = ctx.principalId();
 *     String role = ctx.role();
 *     // Use authenticated context
 * } else {
 *     // Return 401 Unauthorized
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Resolves Kernel-authenticated session context from HTTP requests
 * @doc.layer core
 * @doc.pattern Resolver
 */
public final class KernelSessionContextResolver {

    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String SESSION_HEADER_NAME = "X-Session-Token";
    private static final String CORRELATION_HEADER_NAME = "X-Correlation-ID";

    private final SessionManager sessionManager;

    /**
     * Creates a new kernel session context resolver.
     *
     * @param sessionManager the session manager for session verification; must not be null
     */
    public KernelSessionContextResolver(SessionManager sessionManager) {
        this.sessionManager = java.util.Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    /**
     * Resolves the kernel session context from an HTTP request.
     * <p>
     * Extracts session identifier from cookie or header, verifies the session,
     * and returns the authenticated context. Returns empty if session is
     * missing, invalid, or expired.
     * </p>
     *
     * @param request the HTTP request; must not be null
     * @return Promise resolving to Optional containing the session context, or empty if invalid
     */
    public Promise<Optional<KernelSessionContext>> resolve(HttpRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null || sessionId.isBlank()) {
            return Promise.of(Optional.empty());
        }

        return sessionManager.getSession(sessionId)
            .map(sessionOpt -> sessionOpt
                .filter(session -> !session.isExpired())
                .map(this::toContext));
    }

    /**
     * Resolves the kernel session context synchronously from an HTTP request.
     * <p>
     * This is a blocking convenience method for use in non-async contexts.
     * Prefer {@link #resolve(HttpRequest)} in async ActiveJ flows.
     * </p>
     *
     * @param request the HTTP request; must not be null
     * @return Optional containing the session context, or empty if invalid
     */
    public Optional<KernelSessionContext> resolveSync(HttpRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        try {
            Optional<SessionState> sessionOpt = sessionManager.getSession(sessionId)
                .toCompletableFuture()
                .get();
            return sessionOpt
                .filter(session -> !session.isExpired())
                .map(this::toContext);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Creates a new session with the specified identity attributes.
     * <p>
     * This method creates a new session, populates it with the provided
     * identity attributes, and returns the session ID for use in responses.
     * </p>
     *
     * @param tenantId the tenant ID; must not be null
     * @param principalId the principal ID; must not be null
     * @param role the role; must not be null
     * @param persona the persona; must not be null
     * @param tier the tier; must not be null
     * @param facilityId the facility ID; may be null
     * @param maxInactiveIntervalSeconds the session TTL in seconds
     * @return Promise resolving to the new session ID
     */
    public Promise<String> createSession(
        String tenantId,
        String principalId,
        String role,
        String persona,
        String tier,
        String facilityId,
        long maxInactiveIntervalSeconds
    ) {
        return sessionManager.createSession()
            .then(session -> {
                session.setTenantId(tenantId);
                session.setUserId(principalId);
                session.setMaxInactiveInterval(maxInactiveIntervalSeconds);
                session.setAttribute("role", role);
                session.setAttribute("persona", persona);
                session.setAttribute("tier", tier);
                if (facilityId != null) {
                    session.setAttribute("facilityId", facilityId);
                }
                return sessionManager.saveSession(session)
                    .map($ -> session.getId());
            });
    }

    /**
     * Invalidates a session by ID.
     *
     * @param sessionId the session ID to invalidate; must not be null
     * @return Promise resolving to true if session was deleted, false otherwise
     */
    public Promise<Boolean> invalidateSession(String sessionId) {
        return sessionManager.deleteSession(sessionId);
    }

    private String extractSessionId(HttpRequest request) {
        // Try cookie first
        String cookieHeader = request.getHeader(io.activej.http.HttpHeaders.of("Cookie"));
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals(SESSION_COOKIE_NAME)) {
                    return parts[1];
                }
            }
        }

        // Fall back to header
        return request.getHeader(io.activej.http.HttpHeaders.of(SESSION_HEADER_NAME));
    }

    private String extractCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(io.activej.http.HttpHeaders.of(CORRELATION_HEADER_NAME));
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private KernelSessionContext toContext(SessionState session) {
        String role = session.getAttribute("role");
        String persona = session.getAttribute("persona");
        String tier = session.getAttribute("tier");
        String facilityId = session.getAttribute("facilityId");

        return new KernelSessionContext(
            session.getTenantId(),
            session.getUserId(),
            role != null ? role : "patient",
            persona != null ? persona : "patient",
            tier != null ? tier : "core",
            facilityId,
            UUID.randomUUID().toString()
        );
    }

    /**
     * Kernel-authenticated session context record.
     * <p>
     * Contains all identity attributes resolved from a verified session.
     * All values are server-authenticated and cannot be spoofed by clients.
     * </p>
     *
     * @param tenantId the tenant ID
     * @param principalId the principal/user ID
     * @param role the role (e.g., patient, clinician, admin)
     * @param persona the persona (e.g., patient, caregiver, fchv)
     * @param tier the tier (e.g., core, clinical, emergency)
     * @param facilityId the facility ID (may be null)
     * @param correlationId the correlation ID for request tracing
     */
    public record KernelSessionContext(
        String tenantId,
        String principalId,
        String role,
        String persona,
        String tier,
        String facilityId,
        String correlationId
    ) {
        /**
         * Validates that this context contains required non-null fields.
         *
         * @throws IllegalArgumentException if required fields are null or blank
         */
        public KernelSessionContext {
            java.util.Objects.requireNonNull(tenantId, "tenantId must not be null");
            java.util.Objects.requireNonNull(principalId, "principalId must not be null");
            java.util.Objects.requireNonNull(role, "role must not be null");
            java.util.Objects.requireNonNull(persona, "persona must not be null");
            java.util.Objects.requireNonNull(tier, "tier must not be null");
            java.util.Objects.requireNonNull(correlationId, "correlationId must not be null");

            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (principalId.isBlank()) {
                throw new IllegalArgumentException("principalId must not be blank");
            }
            if (role.isBlank()) {
                throw new IllegalArgumentException("role must not be blank");
            }
            if (persona.isBlank()) {
                throw new IllegalArgumentException("persona must not be blank");
            }
            if (tier.isBlank()) {
                throw new IllegalArgumentException("tier must not be blank");
            }
        }

        /**
         * Checks if the role is in the allowed set.
         *
         * @param allowedRoles the allowed roles
         * @return true if role is allowed, false otherwise
         */
        public boolean isRoleAllowed(Set<String> allowedRoles) {
            return allowedRoles.contains(role.toLowerCase());
        }

        /**
         * Checks if the persona is in the allowed set.
         *
         * @param allowedPersonas the allowed personas
         * @return true if persona is allowed, false otherwise
         */
        public boolean isPersonaAllowed(Set<String> allowedPersonas) {
            return allowedPersonas.contains(persona.toLowerCase());
        }

        /**
         * Checks if the tier is in the allowed set.
         *
         * @param allowedTiers the allowed tiers
         * @return true if tier is allowed, false otherwise
         */
        public boolean isTierAllowed(Set<String> allowedTiers) {
            return allowedTiers.contains(tier.toLowerCase());
        }
    }
}
