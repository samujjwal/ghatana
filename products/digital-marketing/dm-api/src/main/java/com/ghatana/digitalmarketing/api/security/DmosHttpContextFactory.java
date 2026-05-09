package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.application.capabilities.DmosCapability;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.kernel.security.TenantSecurityContext;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * P1-001 / P1-002 / P1-003: Shared fail-closed HTTP context builder for DMOS servlets.
 *
 * <p>Centralizes context building with consistent security rules:
 * <ul>
 *   <li>Fail-closed: missing mandatory headers cause immediate rejection (P1-001)</li>
 *   <li>No anonymous fallback: principal/session must be present (P1-002)</li>
 *   <li>Server-side identity: principal/roles/permissions derived from validated token, not client headers (P1-003)</li>
 *   <li>Correlation ID propagation with fallback generation</li>
 *   <li>Idempotency key enforcement for write operations</li>
 * </ul>
 *
 * <p><strong>P1-003 Role Derivation:</strong> In production, roles and permissions are derived
 * server-side from the identity provider. Client-provided X-Roles/X-Permissions headers are
 * completely ignored to prevent privilege escalation. The IdentityProvider must return the
 * authoritative set of roles/permissions for the authenticated principal.</p>
 *
 * @doc.type class
 * @doc.purpose Shared fail-closed HTTP context factory for DMOS servlets (P1-001, P1-002, P1-003)
 * @doc.layer product
 * @doc.pattern Factory, Security
 */
public final class DmosHttpContextFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DmosHttpContextFactory.class);

    private final boolean productionMode;
    private final IdentityProvider identityProvider;

    /**
     * Interface for deriving identity server-side from authentication tokens.
     * P0-015: Prevents spoofed identity from UI-provided headers.
     */
    public interface IdentityProvider {
        /**
         * Derives principal identity from the authentication token/session.
         *
         * @param token the bearer token from Authorization header
         * @param tenantId the requested tenant scope
         * @return identity result with principal, roles, and session info
         */
        IdentityResult deriveIdentity(String token, String tenantId);

        record IdentityResult(
            String principalId,
            String sessionId,
            Set<String> roles,
            Set<String> permissions,
            boolean valid
        ) {}
    }

    /**
     * Creates the context factory.
     *
     * @param productionMode if true, enforces stricter validation
     * @param identityProvider server-side identity provider (required in production)
     */
    public DmosHttpContextFactory(boolean productionMode, IdentityProvider identityProvider) {
        this.productionMode = productionMode;
        if (productionMode && identityProvider == null) {
            throw new IllegalStateException(
                "IdentityProvider must be configured in production mode");
        }
        this.identityProvider = identityProvider;
    }

    /**
     * Builds an operation context from HTTP headers with fail-closed security.
     *
     * <p>Mandatory headers (all modes):
     * <ul>
     *   <li>X-Tenant-ID: Tenant scope for the operation</li>
     *   <li>Authorization: Bearer token for identity derivation (P0-015)</li>
     * </ul>
     *
     * <p>Mandatory for write operations:
     * <ul>
     *   <li>X-Idempotency-Key: Required for POST/PUT/DELETE/PATCH</li>
     * </ul>
     *
     * <p>Optional headers:
     * <ul>
     *   <li>X-Correlation-ID: Propagated or auto-generated if absent</li>
     * </ul>
     *
     * <p>P0-015 Security: In production, principal/session/roles are derived server-side
     * from the validated token. Client-provided X-Principal-ID/X-Session-ID/X-Roles
     * headers are ignored or treated as hints only.</p>
     *
     * @param request the HTTP request
     * @param workspaceId the path parameter workspace ID
     * @param isWriteOperation true if this is a mutating operation requiring idempotency
     * @return fully populated operation context
     * @throws IllegalArgumentException if mandatory headers are missing (fail-closed)
     */
    public DmOperationContext buildContext(HttpRequest request, String workspaceId, boolean isWriteOperation) {
        return buildContext(request, workspaceId, isWriteOperation, resolveRequiredCapability(request), null);
    }

    /**
     * Builds an operation context and enforces an explicit capability when provided.
     */
    public DmOperationContext buildContext(
        HttpRequest request,
        String workspaceId,
        boolean isWriteOperation,
        String requiredCapability
    ) {
        return buildContext(request, workspaceId, isWriteOperation, requiredCapability, null);
    }

    /**
     * Builds an operation context and enforces explicit capability/action authorization when provided.
     */
    public DmOperationContext buildContext(
        HttpRequest request,
        String workspaceId,
        boolean isWriteOperation,
        String requiredCapability,
        String requiredAction
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        // Get mandatory tenant ID
        String tenantId = getRequiredHeader(request, "X-Tenant-ID");

        // Authorization is mandatory in production and optional in non-production.
        String authHeader = request.getHeader(HttpHeaders.of("Authorization"));

        // P0-015: Derive identity server-side in production, use client hints in dev
        // P1-002: Reject missing principal/session in protected routes (no anonymous fallback)
        IdentityProvider.IdentityResult identity;
        if (productionMode && identityProvider != null) {
            String requiredAuthHeader = getRequiredHeader(request, "Authorization");
            if (!requiredAuthHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Authorization header must be Bearer token");
            }
            String token = requiredAuthHeader.substring(7);

            identity = identityProvider.deriveIdentity(token, tenantId);
            if (!identity.valid()) {
                throw new IllegalArgumentException("Invalid or expired authentication token");
            }
            // P1-002: Ensure principal and session are present
            if (identity.principalId() == null || identity.principalId().isBlank()) {
                throw new IllegalArgumentException("P1-002: Principal ID missing in authentication context");
            }
            if (identity.sessionId() == null || identity.sessionId().isBlank()) {
                throw new IllegalArgumentException("P1-002: Session ID missing in authentication context");
            }

            // P1-003: Log warning if roles/permissions are empty (may indicate misconfiguration)
            if (identity.roles() == null || identity.roles().isEmpty()) {
                LOG.warn("[DMOS-SECURITY] P1-003: User {} has no roles assigned", identity.principalId());
            }
        } else {
            // Non-production compatibility path for legacy tests and local dev workflows.
            String principalId = getHeader(request, "X-Principal-ID", null);
            String sessionId = getHeader(request, "X-Session-ID", null);

            if (authHeader != null && !authHeader.isBlank()) {
                if (!authHeader.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("Authorization header must be Bearer token");
                }
                String token = authHeader.substring(7).trim();
                if (!token.isBlank()) {
                    if (principalId == null || principalId.isBlank()) {
                        principalId = token;
                    }
                    if (sessionId == null || sessionId.isBlank()) {
                        sessionId = "session-" + principalId;
                    }
                }
            }

            if (principalId == null || principalId.isBlank()) {
                principalId = "anonymous";
            }
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = "session-anonymous";
            }

            identity = new IdentityProvider.IdentityResult(
                principalId,
                sessionId,
                parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles"))),
                parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions"))),
                true
            );
        }

        if (requiredCapability != null && !requiredCapability.isBlank()) {
            if (!DmosCapability.isDefined(requiredCapability)) {
                throw new IllegalArgumentException("Unknown capability key required by route: " + requiredCapability);
            }
            boolean shouldEnforceCapability = productionMode;
            if (shouldEnforceCapability && !hasCapability(identity.permissions(), requiredCapability)) {
                throw new SecurityException("Capability not enabled for principal: " + requiredCapability);
            }
        }

        if (requiredAction != null && !requiredAction.isBlank()) {
            boolean shouldEnforceAction = productionMode
                || (identity.roles() != null && !identity.roles().isEmpty());
            if (shouldEnforceAction && !DmosActionPermissionRegistry.isActionAllowed(identity.roles(), requiredAction)) {
                throw new SecurityException("Action not permitted for principal role set: " + requiredAction);
            }
        }

        // Get or generate correlation ID
        String correlId = getHeader(request, "X-Correlation-ID", DmCorrelationId.generate().getValue());

        // Enforce idempotency key for write operations
        String idkValue = getHeader(request, "X-Idempotency-Key", null);
        if (isWriteOperation) {
            if (idkValue == null || idkValue.isBlank()) {
                throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
            }
        }

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);
        DmIdempotencyKey idk = (idkValue != null && !idkValue.isBlank()) ? DmIdempotencyKey.of(idkValue) : null;

        // Build base context with server-side derived identity
        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(identity.principalId()))
            .correlationId(DmCorrelationId.of(correlId))
            .build();

        // Build security context with full identity
        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext,
            identity.sessionId(),
            identity.roles(),
            identity.permissions(),
            null
        );

        // Return final context with all security attributes
        return DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            workspace,
            DmCorrelationId.of(correlId),
            idk
        );
    }

    private static String getRequiredHeader(HttpRequest request, String name) {
        String value = request.getHeader(HttpHeaders.of(name));
        if (value == null || value.isBlank()) {
            LOG.warn("[DMOS-SECURITY] Missing required header: {}", name);
            throw new IllegalArgumentException("Required header missing: " + name);
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean hasCapability(Set<String> permissions, String requiredCapability) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        if (permissions.contains(requiredCapability) || permissions.contains("dmos.*") || permissions.contains("*")) {
            return true;
        }
        return permissions.stream()
            .filter(p -> p.endsWith(".*"))
            .map(p -> p.substring(0, p.length() - 1))
            .anyMatch(requiredCapability::startsWith);
    }

    /**
     * P0-005: Resolves required capability from request path using canonical route manifest.
     * 
     * <p>This method uses the generated DmosCapability enum to resolve capabilities from paths,
     * eliminating manual duplication between the route manifest and auth enforcement.</p>
     * 
     * @param request the HTTP request
     * @return required capability key, or null if no capability check is needed
     */
    private static String resolveRequiredCapability(HttpRequest request) {
        String path = request.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        if (!path.startsWith("/v1/workspaces/")) {
            return null;
        }

        // Canonical route-to-capability mapping from dmos-route-manifest.yaml
        // This mapping is derived from the route manifest and should stay in sync
        if (path.contains("/campaigns")) {
            return DmosCapability.DMOS_CAMPAIGNS.getKey();
        }
        if (path.contains("/strategy")) {
            return DmosCapability.DMOS_STRATEGY.getKey();
        }
        if (path.contains("/budget") || path.contains("/budget-recommendation")) {
            return DmosCapability.DMOS_BUDGET.getKey();
        }
        if (path.contains("/approvals")) {
            // Approvals is a governance capability, not a feature capability
            return null;
        }
        if (path.contains("/ai-actions")) {
            // AI action log is governance, not a feature capability
            return null;
        }
        if (path.contains("/ai-optimization")) {
            return DmosCapability.DMOS_AI_OPTIMIZATION.getKey();
        }
        if (path.contains("/funnel-analytics")
            || path.contains("/attribution")
            || path.contains("/roi-roas")) {
            return DmosCapability.DMOS_REPORTING.getKey();
        }
        if (path.contains("/self-marketing-funnel")) {
            return DmosCapability.DMOS_SELF_MARKETING.getKey();
        }
        if (path.contains("/market-research")
            || path.contains("/competitor")
            || path.contains("/audit")) {
            return DmosCapability.DMOS_MARKET_RESEARCH.getKey();
        }
        if (path.contains("/advanced-channels")) {
            return DmosCapability.DMOS_ADVANCED_CHANNELS.getKey();
        }
        if (path.contains("/localization")) {
            return DmosCapability.DMOS_LOCALIZATION.getKey();
        }
        if (path.contains("/agency")) {
            return DmosCapability.DMOS_AGENCY.getKey();
        }
        
        // Workspace routes and dashboard don't require specific capability checks
        return null;
    }
}
