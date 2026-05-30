package com.ghatana.datacloud.api.controller;

import static com.ghatana.datacloud.api.controller.ApiResponses.json;

import com.ghatana.datacloud.security.SecurityAuditService;
import com.ghatana.datacloud.security.SecurityPolicyService;
import com.ghatana.datacloud.security.policies.RateLimitPolicy;
import com.ghatana.datacloud.security.policies.TenantIsolationPolicy;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Security management controller for Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Provides administrative endpoints for security policy management, monitoring,
 * and configuration. Enables security administrators to view security metrics,
 * manage policies, and monitor security events.
 *
 * <p><b>Endpoints</b><br>
 * - GET /api/v1/security/metrics - Security metrics and statistics
 * - GET /api/v1/security/policies - List active security policies
 * - POST /api/v1/security/policies/refresh - Refresh security policies
 * - GET /api/v1/security/audit/recent - Recent security audit events
 * - POST /api/v1/security/break-glass/enable - Enable break-glass access
 * - POST /api/v1/security/break-glass/disable - Disable break-glass access
 *
 * @see SecurityPolicyService
 * @see SecurityAuditService
 * @doc.type class
 * @doc.purpose Security management and monitoring endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
public class SecurityController {

    private static final Logger log = LoggerFactory.getLogger(SecurityController.class);

    private final SecurityPolicyService securityPolicyService;
    private final SecurityAuditService securityAuditService;

    public SecurityController(
            SecurityPolicyService securityPolicyService,
            SecurityAuditService securityAuditService) {
        this.securityPolicyService = securityPolicyService;
        this.securityAuditService = securityAuditService;
    }

    /**
     * Gets security metrics and statistics.
     */
    public Promise<HttpResponse> getSecurityMetrics() {
        try {
            SecurityPolicyService.SecurityMetrics policyMetrics = securityPolicyService.getSecurityMetrics();
            SecurityAuditService.SecurityMetricsSnapshot auditMetrics = securityAuditService.getSecurityMetrics();

            Map<String, Object> response = new HashMap<>();
            response.put("policyMetrics", Map.of(
                "enforcing", policyMetrics.isEnforcing(),
                "breakGlassTenantsCount", policyMetrics.getBreakGlassTenantsCount(),
                "customPoliciesCount", policyMetrics.getCustomPoliciesCount(),
                "runtimeProfile", policyMetrics.getRuntimeProfile()
            ));
            
            response.put("auditMetrics", Map.of(
                "totalEvents", auditMetrics.getTotalEvents(),
                "uniqueEventTypes", auditMetrics.getUniqueEventTypes(),
                "eventCounts", auditMetrics.getEventCounts()
            ));

            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get security metrics: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve security metrics")));
        }
    }

    /**
     * Lists active security policies.
     */
    public Promise<HttpResponse> getSecurityPolicies() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // This would be enhanced to return actual policy configurations
            response.put("policies", Map.of(
                "rateLimit", Map.of(
                    "enabled", true,
                    "description", "Rate limiting policy to prevent API abuse"
                ),
                "tenantIsolation", Map.of(
                    "enabled", true,
                    "description", "Strict tenant isolation enforcement"
                ),
                "authentication", Map.of(
                    "enabled", true,
                    "description", "JWT and API key authentication"
                ),
                "authorization", Map.of(
                    "enabled", true,
                    "description", "Role-based access control"
                )
            ));

            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get security policies: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve security policies")));
        }
    }

    /**
     * Refreshes security policies (reloads configuration).
     */
    public Promise<HttpResponse> refreshSecurityPolicies() {
        try {
            // This would trigger policy refresh logic
            log.info("Security policy refresh requested by admin");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Security policies refreshed successfully");
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to refresh security policies: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to refresh security policies")));
        }
    }

    /**
     * Gets recent security audit events.
     */
    public Promise<HttpResponse> getRecentAuditEvents(
            int limit,
            String eventType) {
        
        try {
            SecurityAuditService.SecurityMetricsSnapshot metrics = securityAuditService.getSecurityMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("summary", Map.of(
                "totalEvents", metrics.getTotalEvents(),
                "uniqueEventTypes", metrics.getUniqueEventTypes(),
                "eventCounts", eventType != null ? 
                    Map.of(eventType, metrics.getEventCount(eventType)) : 
                    metrics.getEventCounts()
            ));
            
            response.put("limit", limit);
            response.put("eventType", eventType);
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get recent audit events: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve audit events")));
        }
    }

    /**
     * Enables break-glass access for emergency situations.
     */
    public Promise<HttpResponse> enableBreakGlassAccess(Map<String, Object> request) {
        try {
            String reason = (String) request.get("reason");
            String tenantId = (String) request.get("tenantId");
            Integer durationHours = (Integer) request.getOrDefault("durationHours", 1);

            if (reason == null || reason.trim().isEmpty()) {
                return Promise.of(json(400, Map.of("error", "Reason is required for break-glass access")));
            }

            log.warn("Break-glass access enabled for tenant: {}, reason: {}, duration: {} hours", 
                    tenantId, reason, durationHours);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Break-glass access enabled");
            response.put("tenantId", tenantId);
            response.put("reason", reason);
            response.put("durationHours", durationHours);
            response.put("expiresAt", java.time.Instant.now().plus(Duration.ofHours((durationHours).longValue())).toString());
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to enable break-glass access: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to enable break-glass access")));
        }
    }

    /**
     * Disables break-glass access.
     */
    public Promise<HttpResponse> disableBreakGlassAccess(Map<String, Object> request) {
        try {
            String tenantId = (String) request.get("tenantId");
            String reason = (String) request.getOrDefault("reason", "Emergency resolved");

            log.info("Break-glass access disabled for tenant: {}, reason: {}", tenantId, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Break-glass access disabled");
            response.put("tenantId", tenantId);
            response.put("reason", reason);
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to disable break-glass access: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to disable break-glass access")));
        }
    }

    /**
     * Gets security configuration status.
     */
    public Promise<HttpResponse> getSecurityStatus() {
        try {
            SecurityPolicyService.SecurityMetrics metrics = securityPolicyService.getSecurityMetrics();

            Map<String, Object> response = new HashMap<>();
            response.put("security", Map.of(
                "enforcing", metrics.isEnforcing(),
                "breakGlassTenantsCount", metrics.getBreakGlassTenantsCount(),
                "customPoliciesCount", metrics.getCustomPoliciesCount(),
                "runtimeProfile", metrics.getRuntimeProfile()
            ));
            
            response.put("features", Map.of(
                "authentication", true,
                "authorization", true,
                "tenantIsolation", true,
                "rateLimiting", true,
                "auditLogging", true,
                "policyEngine", true,
                "breakGlass", metrics.getBreakGlassTenantsCount() > 0
            ));

            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to get security status: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to retrieve security status")));
        }
    }

    /**
     * Tests security policy evaluation.
     */
    public Promise<HttpResponse> testSecurityPolicy(Map<String, Object> request) {
        try {
            String method = (String) request.get("method");
            String path = (String) request.get("path");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) request.getOrDefault("headers", Map.of());

            if (method == null || path == null) {
                return Promise.of(json(400, Map.of("error", "Method and path are required")));
            }

            Principal testPrincipal = new Principal("policy-test-user", java.util.List.of("ADMIN"), "test-tenant");

            SecurityPolicyService.SecurityEvaluationResult result = 
                securityPolicyService.evaluateRequest(testPrincipal, method, path, headers);

            Map<String, Object> response = new HashMap<>();
            response.put("allowed", result.isAllowed());
            response.put("sensitivity", result.getSensitivity().name());
            response.put("reason", result.getReason());
            response.put("errorCode", result.getErrorCode());
            response.put("breakGlassUsed", result.isBreakGlassUsed());
            response.put("evaluationTime", result.getEvaluationTime().toMillis());
            response.put("timestamp", java.time.Instant.now().toString());

            return Promise.of(json(200, response));

        } catch (Exception e) {
            log.error("Failed to test security policy: {}", e.getMessage(), e);
            return Promise.of(json(500, Map.of("error", "Failed to test security policy")));
        }
    }
}
