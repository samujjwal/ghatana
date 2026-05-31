package com.ghatana.datacloud.security;

import com.ghatana.datacloud.security.RouteSensitivityMatrix.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Backend policy enforcer for route-level security validation.
 *
 * <p><b>Purpose</b><br>
 * Enforces security policies per route based on the route sensitivity matrix,
 * including authentication, authorization, tenant isolation, rate limiting,
 * audit logging, and data classification requirements.
 *
 * @doc.type class
 * @doc.purpose Backend policy enforcement per route
 * @doc.layer product
 * @doc.pattern Security Enforcer
 */
public final class RoutePolicyEnforcer {

    private static final Logger log = LoggerFactory.getLogger(RoutePolicyEnforcer.class);

    private final RouteSensitivityMatrix sensitivityMatrix;

    public RoutePolicyEnforcer(RouteSensitivityMatrix sensitivityMatrix) {
        this.sensitivityMatrix = Objects.requireNonNull(sensitivityMatrix, "sensitivityMatrix cannot be null");
    }

    /**
     * Policy enforcement result.
     */
    public record EnforcementResult(
            boolean allowed,
            String reason,
            String violationType,
            Map<String, Object> context
    ) {
        public EnforcementResult {
            if (context == null) context = Map.of();
        }

        public static EnforcementResult allow() {
            return new EnforcementResult(true, "Request allowed", null, Map.of());
        }

        public static EnforcementResult denied(String reason, String violationType) {
            return new EnforcementResult(false, reason, violationType, Map.of());
        }

        public static EnforcementResult denied(String reason, String violationType, Map<String, Object> context) {
            return new EnforcementResult(false, reason, violationType, context);
        }
    }

    /**
     * Security context for policy evaluation.
     */
    public record SecurityContext(
            String tenantId,
            String userId,
            Set<String> roles,
            Set<String> permissions,
            String ipAddress,
            String userAgent,
            Map<String, Object> additionalContext
    ) {
        public SecurityContext {
            if (roles == null) roles = Set.of();
            if (permissions == null) permissions = Set.of();
            if (additionalContext == null) additionalContext = Map.of();
        }
    }

    /**
     * Enforces policy for a given route and security context.
     */
    public EnforcementResult enforcePolicy(String route, String method, SecurityContext context) {
        Optional<RouteSensitivity> sensitivityOpt = sensitivityMatrix.getSensitivity(route, method);

        if (sensitivityOpt.isEmpty()) {
            log.warn("[policy-enforcer] No sensitivity defined for route: {} {}", method, route);
            // Default to MEDIUM sensitivity for undefined routes
            return enforceDefaultPolicy(route, method, context);
        }

        RouteSensitivity sensitivity = sensitivityOpt.get();
        return enforceSensitivity(sensitivity, context);
    }

    private EnforcementResult enforceSensitivity(RouteSensitivity sensitivity, SecurityContext context) {
        // Check authentication requirement
        EnforcementResult authResult = checkAuthentication(sensitivity, context);
        if (!authResult.allowed()) {
            return authResult;
        }

        // Check authorization requirement
        EnforcementResult authzResult = checkAuthorization(sensitivity, context);
        if (!authzResult.allowed()) {
            return authzResult;
        }

        // Check tenant isolation
        if (sensitivity.requiresTenantIsolation()) {
            EnforcementResult tenantResult = checkTenantIsolation(sensitivity, context);
            if (!tenantResult.allowed()) {
                return tenantResult;
            }
        }

        // Check data classification
        if (sensitivity.requiresDataClassification()) {
            EnforcementResult classificationResult = checkDataClassification(sensitivity, context);
            if (!classificationResult.allowed()) {
                return classificationResult;
            }
        }

        return EnforcementResult.allow();
    }

    private EnforcementResult checkAuthentication(RouteSensitivity sensitivity, SecurityContext context) {
        switch (sensitivity.authRequirement()) {
            case NONE:
                return EnforcementResult.allow();

            case OPTIONAL:
                // Authentication optional, no enforcement
                return EnforcementResult.allow();

            case REQUIRED:
                if (context.userId() == null || context.userId().isBlank()) {
                    log.warn("[policy-enforcer] Authentication required but missing for route: {}", sensitivity.route());
                    return EnforcementResult.denied(
                            "Authentication required",
                            "AUTHENTICATION_REQUIRED",
                            Map.of("route", sensitivity.route(), "method", sensitivity.method())
                    );
                }
                return EnforcementResult.allow();

            case STRICT:
                if (context.userId() == null || context.userId().isBlank()) {
                    log.warn("[policy-enforcer] Strict authentication required but missing for route: {}", sensitivity.route());
                    return EnforcementResult.denied(
                            "Strict authentication with MFA required",
                            "STRICT_AUTHENTICATION_REQUIRED",
                            Map.of("route", sensitivity.route(), "method", sensitivity.method())
                    );
                }
                // In strict mode, also check for MFA claim in context
                boolean hasMfa = Boolean.TRUE.equals(context.additionalContext().get("mfaVerified"));
                if (!hasMfa) {
                    return EnforcementResult.denied(
                            "MFA verification required",
                            "MFA_REQUIRED",
                            Map.of("route", sensitivity.route(), "method", sensitivity.method())
                    );
                }
                return EnforcementResult.allow();

            default:
                return EnforcementResult.allow();
        }
    }

    private EnforcementResult checkAuthorization(RouteSensitivity sensitivity, SecurityContext context) {
        switch (sensitivity.authzRequirement()) {
            case NONE:
                return EnforcementResult.allow();

            case BASIC:
                // Basic role check - ensure user has at least one role
                if (context.roles().isEmpty()) {
                    log.warn("[policy-enforcer] Basic authorization failed - no roles for route: {}", sensitivity.route());
                    return EnforcementResult.denied(
                            "At least one role required",
                            "ROLE_REQUIRED",
                            Map.of("route", sensitivity.route(), "method", sensitivity.method())
                    );
                }
                return EnforcementResult.allow();

            case RBAC:
                // Role-based access control - check required roles
                if (!sensitivity.requiredRoles().isEmpty()) {
                    boolean hasRequiredRole = context.roles().stream()
                            .anyMatch(sensitivity.requiredRoles()::contains);

                    if (!hasRequiredRole) {
                        log.warn("[policy-enforcer] RBAC failed - missing required roles for route: {}", sensitivity.route());
                        return EnforcementResult.denied(
                                "Required role not found",
                                "ROLE_NOT_FOUND",
                                Map.of(
                                        "route", sensitivity.route(),
                                        "method", sensitivity.method(),
                                        "requiredRoles", sensitivity.requiredRoles(),
                                        "userRoles", context.roles()
                                )
                        );
                    }
                }
                return EnforcementResult.allow();

            case DATA_ACCESS:
                // Data access validation - check permissions
                if (!sensitivity.requiredPermissions().isEmpty()) {
                    boolean hasRequiredPermission = context.permissions().stream()
                            .anyMatch(sensitivity.requiredPermissions()::contains);

                    if (!hasRequiredPermission) {
                        log.warn("[policy-enforcer] Data access authorization failed - missing permissions for route: {}", sensitivity.route());
                        return EnforcementResult.denied(
                                "Required permission not found",
                                "PERMISSION_NOT_FOUND",
                                Map.of(
                                        "route", sensitivity.route(),
                                        "method", sensitivity.method(),
                                        "requiredPermissions", sensitivity.requiredPermissions(),
                                        "userPermissions", context.permissions()
                                )
                        );
                    }
                }
                return EnforcementResult.allow();

            case POLICY:
                // Policy-based authorization - check both roles and permissions
                if (!sensitivity.requiredRoles().isEmpty()) {
                    boolean hasRequiredRole = context.roles().stream()
                            .anyMatch(sensitivity.requiredRoles()::contains);

                    if (!hasRequiredRole) {
                        log.warn("[policy-enforcer] Policy authorization failed - missing required roles for route: {}", sensitivity.route());
                        return EnforcementResult.denied(
                                "Required role not found for policy access",
                                "POLICY_ROLE_NOT_FOUND",
                                Map.of(
                                        "route", sensitivity.route(),
                                        "method", sensitivity.method(),
                                        "requiredRoles", sensitivity.requiredRoles(),
                                        "userRoles", context.roles()
                                )
                        );
                    }
                }

                if (!sensitivity.requiredPermissions().isEmpty()) {
                    boolean hasRequiredPermission = context.permissions().stream()
                            .anyMatch(sensitivity.requiredPermissions()::contains);

                    if (!hasRequiredPermission) {
                        log.warn("[policy-enforcer] Policy authorization failed - missing permissions for route: {}", sensitivity.route());
                        return EnforcementResult.denied(
                                "Required permission not found for policy access",
                                "POLICY_PERMISSION_NOT_FOUND",
                                Map.of(
                                        "route", sensitivity.route(),
                                        "method", sensitivity.method(),
                                        "requiredPermissions", sensitivity.requiredPermissions(),
                                        "userPermissions", context.permissions()
                                )
                        );
                    }
                }
                return EnforcementResult.allow();

            default:
                return EnforcementResult.allow();
        }
    }

    private EnforcementResult checkTenantIsolation(RouteSensitivity sensitivity, SecurityContext context) {
        if (context.tenantId() == null || context.tenantId().isBlank()) {
            log.warn("[policy-enforcer] Tenant isolation failed - missing tenant ID for route: {}", sensitivity.route());
            return EnforcementResult.denied(
                    "Tenant ID required",
                    "TENANT_ID_REQUIRED",
                    Map.of("route", sensitivity.route(), "method", sensitivity.method())
            );
        }
        return EnforcementResult.allow();
    }

    private EnforcementResult checkDataClassification(RouteSensitivity sensitivity, SecurityContext context) {
        // Check if user has access to the required data classification
        String requiredClassification = sensitivity.dataClassification();
        if (requiredClassification != null) {
            String userClearance = (String) context.additionalContext().getOrDefault("dataClearance", "PUBLIC");

            if (!hasClearanceForClassification(userClearance, requiredClassification)) {
                log.warn("[policy-enforcer] Data classification check failed - insufficient clearance for route: {}", sensitivity.route());
                return EnforcementResult.denied(
                        "Insufficient data clearance",
                        "INSUFFICIENT_CLEARANCE",
                        Map.of(
                                "route", sensitivity.route(),
                                "method", sensitivity.method(),
                                "requiredClassification", requiredClassification,
                                "userClearance", userClearance
                        )
                );
            }
        }
        return EnforcementResult.allow();
    }

    private boolean hasClearanceForClassification(String userClearance, String requiredClassification) {
        // Classification hierarchy: PUBLIC < INTERNAL < CONFIDENTIAL < RESTRICTED
        Map<String, Integer> classificationLevels = Map.of(
                "PUBLIC", 0,
                "INTERNAL", 1,
                "CONFIDENTIAL", 2,
                "RESTRICTED", 3
        );

        int userLevel = classificationLevels.getOrDefault(userClearance, 0);
        int requiredLevel = classificationLevels.getOrDefault(requiredClassification, 0);

        return userLevel >= requiredLevel;
    }

    private EnforcementResult enforceDefaultPolicy(String route, String method, SecurityContext context) {
        // Default policy: require authentication for non-public routes
        if (route.startsWith("/health") || route.startsWith("/readiness")) {
            return EnforcementResult.allow();
        }

        if (context.userId() == null || context.userId().isBlank()) {
            return EnforcementResult.denied(
                    "Authentication required for undefined route",
                    "DEFAULT_AUTHENTICATION_REQUIRED",
                    Map.of("route", route, "method", method)
            );
        }

        return EnforcementResult.allow();
    }

    /**
     * Checks if rate limiting should be applied for a route.
     */
    public boolean requiresRateLimiting(String route, String method) {
        return sensitivityMatrix.getSensitivity(route, method)
                .map(RouteSensitivity::requiresRateLimiting)
                .orElse(false);
    }

    /**
     * Checks if audit logging should be applied for a route.
     */
    public boolean requiresAuditLogging(String route, String method) {
        return sensitivityMatrix.getSensitivity(route, method)
                .map(RouteSensitivity::requiresAuditLogging)
                .orElse(true); // Default to audit logging
    }

    /**
     * Gets the sensitivity level for a route.
     */
    public SensitivityLevel getSensitivityLevel(String route, String method) {
        return sensitivityMatrix.getSensitivity(route, method)
                .map(RouteSensitivity::sensitivity)
                .orElse(SensitivityLevel.MEDIUM);
    }
}
