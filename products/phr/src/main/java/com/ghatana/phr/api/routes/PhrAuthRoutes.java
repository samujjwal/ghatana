package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import com.ghatana.platform.security.session.KernelSessionContextResolver;
import com.ghatana.platform.security.session.SessionManager;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authentication API routes for the PHR product.
 *
 * <p>Provides session bootstrap via national-ID and password credentials. On
 * success the route returns a Kernel-authenticated session ID that the frontend
 * stores as an opaque session reference. Identity is resolved server-side
 * through the Kernel session context resolver, not client-authored headers.
 *
 * <p>Credential validation is delegated to {@link KernelSecurityManager} which
 * enforces lockout, account-active checks, and password hashing.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for PHR credential authentication and session logout
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrAuthRoutes {

    /**
     * Session lifetime: 1 hour. Must align with the frontend
     * {@code PhrSessionContext} expiry guard.
     */
    private static final long SESSION_TTL_SECONDS = 3600L;

    private final Eventloop eventloop;
    private final KernelSecurityManager securityManager;
    private final UserRepository userRepository;
    private final AuditTrailService auditTrailService;
    private final KernelSessionContextResolver sessionContextResolver;
    private final SessionManager sessionManager;

    /**
     * Creates auth routes.
     *
     * @param eventloop        the ActiveJ event loop; must not be null
     * @param securityManager  the kernel security manager for credential validation; must not be null
     * @param userRepository   the user repository for session population; must not be null
     * @param auditTrailService the audit trail service for recording auth events; must not be null
     * @param sessionContextResolver the kernel session context resolver; must not be null
     * @param sessionManager the session manager for session creation/invalidation; must not be null
     */
    public PhrAuthRoutes(
        Eventloop eventloop,
        KernelSecurityManager securityManager,
        UserRepository userRepository,
        AuditTrailService auditTrailService,
        KernelSessionContextResolver sessionContextResolver,
        SessionManager sessionManager
    ) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.securityManager = Objects.requireNonNull(securityManager, "securityManager must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.auditTrailService = Objects.requireNonNull(auditTrailService, "auditTrailService must not be null");
        this.sessionContextResolver = Objects.requireNonNull(sessionContextResolver, "sessionContextResolver must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    /**
     * Returns the routing servlet for auth endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/login", this::handleLogin)
            .with(HttpMethod.POST, "/logout", this::handleLogout)
            .with(HttpMethod.GET, "/me", this::handleMe)
            .build();
    }

    private Promise<HttpResponse> handleLogin(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return request.loadBody()
            .then(body -> {
                String nationalId;
                String password;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    nationalId = requireTextField(node, "nationalId");
                    password = requireTextField(node, "password");
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_LOGIN_REQUEST", ex.getMessage(), correlationId);
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_LOGIN_REQUEST", "Request body must be valid JSON", correlationId);
                }

                KernelSecurityManager.Credentials credentials =
                    new KernelSecurityManager.Credentials(nationalId, password, null);

                KernelSecurityManager.ValidationResult result;
                try {
                    result = securityManager.validateCredentials(credentials);
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(500, "AUTH_INTERNAL_ERROR", "Authentication service unavailable", correlationId);
                }

                if (!result.isValid()) {
                    AuditTrailService.AuditTrailEvent failedEvent = AuditTrailService.AuditTrailEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("AUTH_LOGIN_FAILED")
                        .entityId(nationalId)
                        .userId(nationalId)
                        .tenantId("default-tenant")
                        .action("LOGIN_ATTEMPT")
                        .data(Map.of("reason", "INVALID_CREDENTIALS", "correlationId", correlationId))
                        .timestamp(Instant.now().toEpochMilli())
                        .build();
                    auditTrailService.recordAuditEvent(failedEvent);
                    return PhrRouteSupport.errorResponse(401, "INVALID_CREDENTIALS", "Invalid national ID or password", correlationId);
                }

                Optional<PHRUser> userOpt = userRepository.findByUsername(nationalId);
                if (userOpt.isEmpty()) {
                    // Should not happen because validateCredentials already found the user,
                    // but fail securely rather than returning an inconsistent session.
                    return PhrRouteSupport.errorResponse(401, "INVALID_CREDENTIALS", "Invalid national ID or password", correlationId);
                }

                PHRUser user = userOpt.get();
                String role = resolveRole(user.getRoles());
                String persona = resolvePersona(user.getRoles());
                String tier = resolveTier(user.getRoles());
                String tenantId = user.getTenantId() != null ? user.getTenantId() : "default-tenant";
                String facilityId = user.getFacilityId();

                // Create Kernel-authenticated session
                return sessionContextResolver.createSession(
                    tenantId,
                    user.getUserId(),
                    role,
                    persona,
                    tier,
                    facilityId,
                    SESSION_TTL_SECONDS
                ).then(sessionId -> {
                    String expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS).toString();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("sessionId", sessionId);
                    response.put("expiresAt", expiresAt);

                    AuditTrailService.AuditTrailEvent successEvent = AuditTrailService.AuditTrailEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("AUTH_LOGIN_SUCCESS")
                        .entityId(user.getUserId())
                        .userId(user.getUserId())
                        .tenantId(tenantId)
                        .action("LOGIN")
                        .data(Map.of("role", role, "sessionId", sessionId, "sessionExpiresAt", expiresAt, "correlationId", correlationId))
                        .timestamp(Instant.now().toEpochMilli())
                        .build();
                    auditTrailService.recordAuditEvent(successEvent);

                    return PhrRouteSupport.jsonResponse(200, response, correlationId);
                });
            });
    }

    private Promise<HttpResponse> handleLogout(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        
        return sessionContextResolver.resolve(request)
            .then(contextOpt -> {
                if (contextOpt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(401, "INVALID_SESSION", "No valid session found", correlationId);
                }
                
                KernelSessionContextResolver.KernelSessionContext context = contextOpt.get();
                
                return sessionContextResolver.invalidateSession(
                    request.getHeader(HttpHeaders.of("Cookie")) != null 
                        ? extractSessionIdFromCookie(request.getHeader(HttpHeaders.of("Cookie")))
                        : request.getHeader(HttpHeaders.of("X-Session-Token"))
                ).then(deleted -> {
                    AuditTrailService.AuditTrailEvent logoutEvent = AuditTrailService.AuditTrailEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("AUTH_LOGOUT")
                        .entityId(context.principalId())
                        .userId(context.principalId())
                        .tenantId(context.tenantId())
                        .action("LOGOUT")
                        .data(Map.of("role", context.role(), "correlationId", correlationId))
                        .timestamp(Instant.now().toEpochMilli())
                        .build();
                    auditTrailService.recordAuditEvent(logoutEvent);

                    org.slf4j.LoggerFactory.getLogger(PhrAuthRoutes.class)
                        .info("PHR session logout acknowledged for principal: {}", context.principalId());
                    return Promise.of(HttpResponse.ofCode(204)
                        .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                        .build());
                });
            });
    }

    private Promise<HttpResponse> handleMe(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        
        return sessionContextResolver.resolve(request)
            .then(contextOpt -> {
                if (contextOpt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(401, "INVALID_SESSION", "No valid session found", correlationId);
                }
                
                KernelSessionContextResolver.KernelSessionContext context = contextOpt.get();
                
                // Fetch user to validate session and return current actor info
                Optional<PHRUser> userOpt = userRepository.findByUserId(context.principalId());
                if (userOpt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(401, "INVALID_SESSION", "Session principal not found", correlationId);
                }

                PHRUser user = userOpt.get();
                String name = user.getUsername() != null ? user.getUsername() : context.principalId();

                Map<String, Object> actor = new LinkedHashMap<>();
                actor.put("principalId", user.getUserId());
                actor.put("tenantId", context.tenantId());
                actor.put("role", context.role());
                actor.put("persona", context.persona());
                actor.put("tier", context.tier());
                actor.put("facilityId", context.facilityId());
                actor.put("name", name);
                actor.put("permissions", getPermissionsForRole(context.role()));

                return PhrRouteSupport.jsonResponse(200, actor, correlationId);
            });
    }

    /**
     * Returns permission list for a given role.
     */
    private java.util.List<String> getPermissionsForRole(String role) {
        return switch (role) {
            case "admin" -> java.util.List.of(
                "view-release-readiness", "view-audit-trail", "view-provider-dashboard",
                "view-patient-list", "break-glass-review"
            );
            case "clinician" -> java.util.List.of(
                "view-provider-dashboard", "view-patient-list", "review-lab-results",
                "review-medications", "view-observations", "break-glass-review"
            );
            case "caregiver" -> java.util.List.of(
                "view-dependents", "review-lab-results", "review-medications",
                "view-observations", "view-fchv-dashboard"
            );
            case "fchv" -> java.util.List.of(
                "view-fchv-dashboard", "capture-community-vitals", "register-community-patient"
            );
            case "patient" -> java.util.List.of(
                "view-patient-summary", "view-records", "manage-consent",
                "schedule-visit", "manage-profile-settings", "view-notifications",
                "upload-document", "review-ocr"
            );
            default -> java.util.List.of();
        };
    }

    /**
     * Picks the first role from the user's role set that is a valid PHR role.
     * KERNEL-04: No silent fallback - fails closed if no valid role is found.
     */
    private String resolveRole(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("User has no roles assigned. Cannot determine role without silent fallback.");
        }
        // Prefer most-privileged valid role
        for (String preferred : new String[]{"admin", "clinician", "fchv", "caregiver", "patient"}) {
            for (String role : roles) {
                if (preferred.equalsIgnoreCase(role)) {
                    return preferred;
                }
            }
        }
        throw new IllegalStateException("User has no valid PHR role. Found roles: " + roles + ". Cannot determine role without silent fallback.");
    }

    /**
     * Resolves the persona from the user's role set.
     * KERNEL-04: No silent fallback - fails closed if no valid persona is found.
     */
    private String resolvePersona(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("User has no roles assigned. Cannot determine persona without silent fallback.");
        }
        for (String preferred : new String[]{"admin", "clinician", "fchv", "caregiver", "patient"}) {
            for (String role : roles) {
                if (preferred.equalsIgnoreCase(role)) {
                    return preferred;
                }
            }
        }
        throw new IllegalStateException("User has no valid PHR persona. Found roles: " + roles + ". Cannot determine persona without silent fallback.");
    }

    /**
     * Resolves the tier from the user's role set.
     * KERNEL-04: No silent fallback - fails closed if no valid tier is found.
     */
    private String resolveTier(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException("User has no roles assigned. Cannot determine tier without silent fallback.");
        }
        // Admin and clinicians get clinical tier
        for (String role : roles) {
            if (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("clinician")) {
                return "clinical";
            }
        }
        // All other valid roles get core tier
        for (String role : roles) {
            if (role.equalsIgnoreCase("fchv") || role.equalsIgnoreCase("caregiver") || role.equalsIgnoreCase("patient")) {
                return "core";
            }
        }
        throw new IllegalStateException("User has no valid PHR tier. Found roles: " + roles + ". Cannot determine tier without silent fallback.");
    }

    /**
     * Extracts session ID from Cookie header.
     */
    private String extractSessionIdFromCookie(String cookieHeader) {
        if (cookieHeader == null) return null;
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals("SESSION")) {
                return parts[1];
            }
        }
        return null;
    }

    private String requireTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText("").isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return field.asText().strip();
    }
}
