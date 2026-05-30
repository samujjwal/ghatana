package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Route policy enforcer for backend security enforcement.
 *
 * <p><b>Purpose</b><br>
 * Enforces backend policy per route before handler logic execution.
 * Ensures UI gating is convenience only; backend is authoritative.
 *
 * <p><b>Enforcement Flow</b><br>
 * 1. Check route sensitivity from RouteSensitivityMatrix
 * 2. Validate authentication for non-PUBLIC routes
 * 3. Validate tenant isolation for routes requiring it
 * 4. Validate required permissions
 * 5. Validate policy compliance for routes requiring it
 * 6. Validate approval for routes requiring it
 * 7. Audit all enforcement actions
 *
 * @doc.type class
 * @doc.purpose Backend policy enforcement before handler execution
 * @doc.layer product
 * @doc.pattern Security Middleware
 */
public class RoutePolicyEnforcer {

    private static final Logger log = LoggerFactory.getLogger(RoutePolicyEnforcer.class);

    private final TenantValidator tenantValidator;
    private final PermissionValidator permissionValidator;
    private final PolicyValidator policyValidator;
    private final ApprovalValidator approvalValidator;
    private final AuditLogger auditLogger;

    public RoutePolicyEnforcer(
            TenantValidator tenantValidator,
            PermissionValidator permissionValidator,
            PolicyValidator policyValidator,
            ApprovalValidator approvalValidator,
            AuditLogger auditLogger) {
        this.tenantValidator = Objects.requireNonNull(tenantValidator, "tenantValidator cannot be null");
        this.permissionValidator = Objects.requireNonNull(permissionValidator, "permissionValidator cannot be null");
        this.policyValidator = Objects.requireNonNull(policyValidator, "policyValidator cannot be null");
        this.approvalValidator = Objects.requireNonNull(approvalValidator, "approvalValidator cannot be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger cannot be null");
    }

    /**
     * Enforces policy for a given request before handler execution.
     */
    public Promise<PolicyEnforcementResult> enforcePolicy(HttpRequest request, String path, String method) {
        String tenantId = extractTenantId(request);
        String userId = extractUserId(request);
        String correlationId = extractCorrelationId(request);

        log.info("[policy-enforcer] Enforcing policy for path: {}, method: {}, tenant: {}, user: {}",
                path, method, tenantId, userId);

        // Get route configuration
        RouteSensitivityMatrix.RouteConfig routeConfig = RouteSensitivityMatrix
                .getRouteConfig(path, method)
                .orElseThrow(() -> new SecurityException("Route configuration not found"));

        // 1. Check authentication for non-PUBLIC routes
        if (routeConfig.getSensitivity() != RouteSensitivityMatrix.SensitivityLevel.PUBLIC) {
            if (!isAuthenticated(request)) {
                log.warn("[policy-enforcer] Unauthenticated access attempt to: {}", path);
                auditLogger.logSecurityEvent(tenantId, userId, correlationId, "UNAUTHENTICATED_ACCESS",
                        "Unauthenticated access attempt to " + path);
                return Promise.of(PolicyEnforcementResult.unauthorized("Authentication required"));
            }
        }

        // 2. Validate tenant isolation
        if (routeConfig.requiresTenantIsolation()) {
            if (tenantId == null || tenantId.isEmpty()) {
                log.warn("[policy-enforcer] Missing tenant ID for route requiring isolation: {}", path);
                auditLogger.logSecurityEvent(tenantId, userId, correlationId, "MISSING_TENANT_ID",
                        "Missing tenant ID for route requiring isolation: " + path);
                return Promise.of(PolicyEnforcementResult.forbidden("Tenant ID required"));
            }

            if (!tenantValidator.validateTenantAccess(tenantId, userId)) {
                log.warn("[policy-enforcer] Tenant mismatch for user: {}, tenant: {}", userId, tenantId);
                auditLogger.logSecurityEvent(tenantId, userId, correlationId, "TENANT_MISMATCH",
                        "Tenant mismatch for user: " + userId + ", tenant: " + tenantId);
                return Promise.of(PolicyEnforcementResult.forbidden("Tenant access denied"));
            }
        }

        // 3. Validate required permissions
        if (!routeConfig.getRequiredPermissions().isEmpty()) {
            Promise<Boolean> permissionCheck = permissionValidator.hasPermissions(userId, tenantId,
                    routeConfig.getRequiredPermissions());

            return permissionCheck.then(hasPermissions -> {
                if (!hasPermissions) {
                    log.warn("[policy-enforcer] Insufficient permissions for user: {}, required: {}",
                            userId, routeConfig.getRequiredPermissions());
                    auditLogger.logSecurityEvent(tenantId, userId, correlationId, "INSUFFICIENT_PERMISSIONS",
                            "Insufficient permissions: " + routeConfig.getRequiredPermissions());
                    return Promise.of(PolicyEnforcementResult.forbidden("Insufficient permissions"));
                }

                // Continue with policy check
                return checkPolicyAndApproval(request, routeConfig, tenantId, userId, correlationId, path);
            });
        }

        // Continue with policy check
        return checkPolicyAndApproval(request, routeConfig, tenantId, userId, correlationId, path);
    }

    /**
     * Checks policy compliance and approval requirements.
     */
    private Promise<PolicyEnforcementResult> checkPolicyAndApproval(
            HttpRequest request,
            RouteSensitivityMatrix.RouteConfig routeConfig,
            String tenantId,
            String userId,
            String correlationId,
            String path) {

        // 4. Validate policy compliance
        if (routeConfig.requiresPolicyCheck()) {
            return policyValidator.validatePolicyCompliance(tenantId, userId, path, routeConfig.getSensitivity())
                    .then(isCompliant -> {
                        if (!isCompliant) {
                            log.warn("[policy-enforcer] Policy compliance check failed for: {}", path);
                            auditLogger.logSecurityEvent(tenantId, userId, correlationId, "POLICY_VIOLATION",
                                    "Policy compliance check failed for: " + path);
                            return Promise.of(PolicyEnforcementResult.forbidden("Policy violation"));
                        }

                        // Continue with approval check
                        return checkApproval(request, routeConfig, tenantId, userId, correlationId, path);
                    });
        }

        // Continue with approval check
        return checkApproval(request, routeConfig, tenantId, userId, correlationId, path);
    }

    /**
     * Checks approval requirements.
     */
    private Promise<PolicyEnforcementResult> checkApproval(
            HttpRequest request,
            RouteSensitivityMatrix.RouteConfig routeConfig,
            String tenantId,
            String userId,
            String correlationId,
            String path) {

        // 5. Validate approval for routes requiring it
        if (routeConfig.requiresApproval()) {
            return approvalValidator.hasRequiredApproval(tenantId, userId, path)
                    .then(hasApproval -> {
                        if (!hasApproval) {
                            log.warn("[policy-enforcer] Missing required approval for: {}", path);
                            auditLogger.logSecurityEvent(tenantId, userId, correlationId, "MISSING_APPROVAL",
                                    "Missing required approval for: " + path);
                            return Promise.of(PolicyEnforcementResult.forbidden("Approval required"));
                        }

                        // Audit successful enforcement
                        auditPolicyEnforcement(tenantId, userId, correlationId, path, routeConfig);
                        return Promise.of(PolicyEnforcementResult.approved());
                    });
        }

        // Audit successful enforcement
        auditPolicyEnforcement(tenantId, userId, correlationId, path, routeConfig);
        return Promise.of(PolicyEnforcementResult.approved());
    }

    /**
     * Audits successful policy enforcement.
     */
    private void auditPolicyEnforcement(String tenantId, String userId, String correlationId,
                                        String path, RouteSensitivityMatrix.RouteConfig routeConfig) {
        auditLogger.logSecurityEvent(tenantId, userId, correlationId, "POLICY_ENFORCEMENT_SUCCESS",
                "Policy enforcement successful for: " + path + ", sensitivity: " + routeConfig.getSensitivity());
    }

    /**
     * Extracts tenant ID from request.
     */
    private String extractTenantId(HttpRequest request) {
        return request.getHeader("X-Tenant-ID");
    }

    /**
     * Extracts user ID from request.
     */
    private String extractUserId(HttpRequest request) {
        return request.getHeader("X-User-ID");
    }

    /**
     * Extracts correlation ID from request.
     */
    private String extractCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Checks if request is authenticated.
     */
    private boolean isAuthenticated(HttpRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && !authHeader.isEmpty();
    }

    /**
     * Policy enforcement result.
     */
    public static class PolicyEnforcementResult {
        private final boolean approved;
        private final String reason;
        private final int statusCode;

        private PolicyEnforcementResult(boolean approved, String reason, int statusCode) {
            this.approved = approved;
            this.reason = reason;
            this.statusCode = statusCode;
        }

        public static PolicyEnforcementResult approved() {
            return new PolicyEnforcementResult(true, null, 200);
        }

        public static PolicyEnforcementResult unauthorized(String reason) {
            return new PolicyEnforcementResult(false, reason, 401);
        }

        public static PolicyEnforcementResult forbidden(String reason) {
            return new PolicyEnforcementResult(false, reason, 403);
        }

        public boolean isApproved() { return approved; }
        public String getReason() { return reason; }
        public int getStatusCode() { return statusCode; }
    }

    /**
     * Tenant validator interface.
     */
    public interface TenantValidator {
        boolean validateTenantAccess(String tenantId, String userId);
    }

    /**
     * Permission validator interface.
     */
    public interface PermissionValidator {
        Promise<Boolean> hasPermissions(String userId, String tenantId, Set<String> requiredPermissions);
    }

    /**
     * Policy validator interface.
     */
    public interface PolicyValidator {
        Promise<Boolean> validatePolicyCompliance(String tenantId, String userId, String path,
                                                   RouteSensitivityMatrix.SensitivityLevel sensitivity);
    }

    /**
     * Approval validator interface.
     */
    public interface ApprovalValidator {
        Promise<Boolean> hasRequiredApproval(String tenantId, String userId, String path);
    }

    /**
     * Audit logger interface.
     */
    public interface AuditLogger {
        void logSecurityEvent(String tenantId, String userId, String correlationId, String eventType, String details);
    }
}
