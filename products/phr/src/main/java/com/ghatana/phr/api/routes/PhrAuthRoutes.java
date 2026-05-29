package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
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
 * success the route returns a lightweight session envelope that the frontend
 * stores in {@code sessionStorage} for subsequent API calls.
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
    private static final long SESSION_TTL_HOURS = 1L;

    private final Eventloop eventloop;
    private final KernelSecurityManager securityManager;
    private final UserRepository userRepository;
    private final AuditTrailService auditTrailService;

    /**
     * Creates auth routes.
     *
     * @param eventloop        the ActiveJ event loop; must not be null
     * @param securityManager  the kernel security manager for credential validation; must not be null
     * @param userRepository   the user repository for session population; must not be null
     * @param auditTrailService the audit trail service for recording auth events; must not be null
     */
    public PhrAuthRoutes(Eventloop eventloop, KernelSecurityManager securityManager, UserRepository userRepository, AuditTrailService auditTrailService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.securityManager = Objects.requireNonNull(securityManager, "securityManager must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.auditTrailService = Objects.requireNonNull(auditTrailService, "auditTrailService must not be null");
    }

    /**
     * Returns the routing servlet for auth endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/login", this::handleLogin)
            .with(HttpMethod.POST, "/sign-in", this::handleLogin) // Alias for /login for IA compatibility
            .with(HttpMethod.POST, "/logout", this::handleLogout)
            .with(HttpMethod.GET, "/me", this::handleMe)
            .build();
    }

    private Promise<HttpResponse> handleLogin(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String nationalId;
                String password;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    nationalId = requireTextField(node, "nationalId");
                    password = requireTextField(node, "password");
                } catch (IllegalArgumentException ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_LOGIN_REQUEST", ex.getMessage());
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_LOGIN_REQUEST", "Request body must be valid JSON");
                }

                KernelSecurityManager.Credentials credentials =
                    new KernelSecurityManager.Credentials(nationalId, password, null);

                KernelSecurityManager.ValidationResult result;
                try {
                    result = securityManager.validateCredentials(credentials);
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(500, "AUTH_INTERNAL_ERROR", "Authentication service unavailable");
                }

                if (!result.isValid()) {
                    AuditTrailService.AuditTrailEvent failedEvent = AuditTrailService.AuditTrailEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("AUTH_LOGIN_FAILED")
                        .entityId(nationalId)
                        .userId(nationalId)
                        .tenantId("default-tenant")
                        .action("LOGIN_ATTEMPT")
                        .data(Map.of("reason", "INVALID_CREDENTIALS"))
                        .timestamp(Instant.now().toEpochMilli())
                        .build();
                    auditTrailService.recordAuditEvent(failedEvent);
                    return PhrRouteSupport.errorResponse(401, "INVALID_CREDENTIALS", "Invalid national ID or password");
                }

                Optional<PHRUser> userOpt = userRepository.findByUsername(nationalId);
                if (userOpt.isEmpty()) {
                    // Should not happen because validateCredentials already found the user,
                    // but fail securely rather than returning an inconsistent session.
                    return PhrRouteSupport.errorResponse(401, "INVALID_CREDENTIALS", "Invalid national ID or password");
                }

                PHRUser user = userOpt.get();
                String role = resolveRole(user.getRoles());
                String name = user.getUsername() != null ? user.getUsername() : nationalId;
                String tenantId = user.getTenantId() != null ? user.getTenantId() : "default-tenant";
                String expiresAt = Instant.now().plus(SESSION_TTL_HOURS, ChronoUnit.HOURS).toString();

                Map<String, Object> session = new LinkedHashMap<>();
                session.put("principalId", user.getUserId());
                session.put("tenantId", tenantId);
                session.put("role", role);
                session.put("name", name);
                session.put("expiresAt", expiresAt);

                AuditTrailService.AuditTrailEvent successEvent = AuditTrailService.AuditTrailEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("AUTH_LOGIN_SUCCESS")
                    .entityId(user.getUserId())
                    .userId(user.getUserId())
                    .tenantId(tenantId)
                    .action("LOGIN")
                    .data(Map.of("role", role, "sessionExpiresAt", expiresAt))
                    .timestamp(Instant.now().toEpochMilli())
                    .build();
                auditTrailService.recordAuditEvent(successEvent);

                return PhrRouteSupport.jsonResponse(200, session);
            });
    }

    private Promise<HttpResponse> handleLogout(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_SESSION_CONTEXT", ex.getMessage());
        }

        if (userRepository.findByUserId(context.principalId()).isEmpty()) {
            return PhrRouteSupport.errorResponse(401, "INVALID_SESSION", "Session principal not found");
        }

        AuditTrailService.AuditTrailEvent logoutEvent = AuditTrailService.AuditTrailEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("AUTH_LOGOUT")
            .entityId(context.principalId())
            .userId(context.principalId())
            .tenantId(context.tenantId())
            .action("LOGOUT")
            .data(Map.of("role", context.role()))
            .timestamp(Instant.now().toEpochMilli())
            .build();
        auditTrailService.recordAuditEvent(logoutEvent);

        org.slf4j.LoggerFactory.getLogger(PhrAuthRoutes.class)
            .info("PHR session logout acknowledged");
        return Promise.of(HttpResponse.ofCode(204).build());
    }
    private Promise<HttpResponse> handleMe(HttpRequest request) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(401, "INVALID_SESSION_CONTEXT", ex.getMessage());
        }

        // Fetch user to validate session and return current actor info
        Optional<PHRUser> userOpt = userRepository.findByUserId(context.principalId());
        if (userOpt.isEmpty()) {
            return PhrRouteSupport.errorResponse(401, "INVALID_SESSION", "Session principal not found");
        }

        PHRUser user = userOpt.get();
        String name = user.getUsername() != null ? user.getUsername() : context.principalId();
        String resolvedRole = resolveRole(user.getRoles());

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("principalId", user.getUserId());
        actor.put("tenantId", context.tenantId());
        actor.put("role", resolvedRole);
        actor.put("name", name);
        actor.put("permissions", getPermissionsForRole(resolvedRole));

        return PhrRouteSupport.jsonResponse(200, actor);
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
     * Falls back to {@code "patient"} if none match.
     */
    private String resolveRole(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "patient";
        }
        // Prefer most-privileged valid role
        for (String preferred : new String[]{"admin", "clinician", "fchv", "caregiver", "patient"}) {
            for (String role : roles) {
                if (preferred.equalsIgnoreCase(role)) {
                    return preferred;
                }
            }
        }
        return "patient";
    }

    private String requireTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText("").isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return field.asText().strip();
    }
}
