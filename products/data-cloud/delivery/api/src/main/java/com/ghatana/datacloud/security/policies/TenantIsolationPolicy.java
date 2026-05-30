package com.ghatana.datacloud.security.policies;

import com.ghatana.datacloud.security.SecurityPolicy;
import com.ghatana.datacloud.security.SecurityPolicyService;
import com.ghatana.datacloud.security.SecurityPolicyService.SecurityEvaluationResult;
import com.ghatana.platform.governance.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tenant isolation security policy for Data Cloud endpoints.
 *
 * <p><b>Purpose</b><br>
 * Enforces strict tenant isolation by validating that requests can only access
 * data and resources belonging to their own tenant. Prevents cross-tenant data
 * leakage and ensures multi-tenant security boundaries.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TenantIsolationPolicy policy = new TenantIsolationPolicy(Set.of("/api/v1/admin/"));
 * 
 * SecurityPolicyService service = SecurityPolicyService.builder()
 *     .addCustomPolicy("tenant-isolation", policy)
 *     .build();
 * }</pre>
 *
 * @see SecurityPolicy
 * @doc.type class
 * @doc.purpose Tenant isolation security policy implementation
 * @doc.layer product
 * @doc.pattern Multi-tenancy
 */
public class TenantIsolationPolicy implements SecurityPolicy {

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationPolicy.class);

    private final Set<String> bypassPaths;
    private final Pattern tenantPathPattern;
    private final boolean strictMode;

    /**
     * Creates a tenant isolation policy with default settings.
     */
    public TenantIsolationPolicy() {
        this(Set.of(), true);
    }

    /**
     * Creates a tenant isolation policy with bypass paths.
     *
     * @param bypassPaths paths that bypass tenant isolation checks
     * @param strictMode whether to enforce strict tenant validation
     */
    public TenantIsolationPolicy(Set<String> bypassPaths, boolean strictMode) {
        this.bypassPaths = Set.copyOf(bypassPaths);
        this.strictMode = strictMode;
        this.tenantPathPattern = Pattern.compile("/api/v1/([^/]+)/");
    }

    @Override
    public SecurityPolicyService.SecurityEvaluationResult evaluate(
            Principal principal,
            String method,
            String path,
            Map<String, String> headers) {

        // Skip tenant isolation for bypass paths
        if (shouldBypassTenantIsolation(path)) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(true)
                .reason("Path bypasses tenant isolation")
                .build();
        }

        // Require authenticated principal for tenant-isolated endpoints
        if (principal == null) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(false)
                .reason("Authentication required for tenant-isolated endpoint")
                .errorCode("TENANT_ISOLATION_AUTH_REQUIRED")
                .build();
        }

        String tenantId = principal.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(false)
                .reason("Principal missing tenant ID")
                .errorCode("MISSING_TENANT_ID")
                .build();
        }

        // Validate tenant format
        if (!isValidTenantId(tenantId)) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(false)
                .reason("Invalid tenant ID format: " + tenantId)
                .errorCode("INVALID_TENANT_ID")
                .build();
        }

        // Check for cross-tenant access attempts in path
        SecurityEvaluationResult pathResult = validateTenantPath(path, tenantId);
        if (!pathResult.isAllowed()) {
            return pathResult;
        }

        // Validate headers for tenant consistency
        SecurityEvaluationResult headerResult = validateTenantHeaders(headers, tenantId);
        if (!headerResult.isAllowed()) {
            return headerResult;
        }

        // Check for admin cross-tenant access (if allowed)
        if (isCrossTenantRequest(path, tenantId)) {
            SecurityEvaluationResult adminResult = validateCrossTenantAccess(principal, path);
            if (!adminResult.isAllowed()) {
                return adminResult;
            }
        }

        return SecurityPolicyService.SecurityEvaluationResult.builder()
            .allowed(true)
            .reason("Tenant isolation validated")
            .metadata(Map.of("tenantId", tenantId))
            .build();
    }

    @Override
    public boolean appliesTo(String method, String path) {
        // Apply to all API endpoints except public ones
        return path.startsWith("/api/v1/") && 
               !shouldBypassTenantIsolation(path);
    }

    @Override
    public int getPriority() {
        return 200; // Very high priority - evaluate early
    }

    /**
     * Checks if the path should bypass tenant isolation.
     */
    private boolean shouldBypassTenantIsolation(String path) {
        return bypassPaths.stream().anyMatch(path::startsWith) ||
               path.startsWith("/health") ||
               path.startsWith("/metrics") ||
               path.startsWith("/ready") ||
               path.startsWith("/live") ||
               path.startsWith("/api/v1/auth/") ||
               path.startsWith("/api/v1/public/");
    }

    /**
     * Validates tenant ID format.
     */
    private boolean isValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return false;
        }

        // Basic validation: alphanumeric with hyphens and underscores
        return tenantId.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Validates that the path doesn't contain cross-tenant references.
     */
    private SecurityEvaluationResult validateTenantPath(String path, String tenantId) {
        // Check for tenant ID in path
        java.util.regex.Matcher matcher = tenantPathPattern.matcher(path);
        if (matcher.find()) {
            String pathTenant = matcher.group(1);
            
            // If path contains a tenant ID, it must match the principal's tenant
            if (!pathTenant.equals(tenantId)) {
                log.warn("Cross-tenant path access attempted: {} vs {}", pathTenant, tenantId);
                
                return SecurityPolicyService.SecurityEvaluationResult.builder()
                    .allowed(false)
                    .reason("Path tenant ID does not match principal tenant: " + pathTenant + " vs " + tenantId)
                    .errorCode("CROSS_TENANT_PATH_ACCESS")
                    .build();
            }
        }

        // Additional validation for resource IDs that might encode tenant information
        if (path.contains("tenant=") || path.contains("tenantId=")) {
            // Extract tenant from query parameters and validate
            String[] pathParts = path.split("[?&]");
            for (String part : pathParts) {
                if (part.startsWith("tenant=") || part.startsWith("tenantId=")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        String queryTenant = keyValue[1];
                        if (!queryTenant.equals(tenantId)) {
                            return SecurityPolicyService.SecurityEvaluationResult.builder()
                                .allowed(false)
                                .reason("Query parameter tenant does not match principal tenant: " + queryTenant + " vs " + tenantId)
                                .errorCode("CROSS_TENANT_QUERY_ACCESS")
                                .build();
                        }
                    }
                }
            }
        }

        return SecurityPolicyService.SecurityEvaluationResult.builder()
            .allowed(true)
            .build();
    }

    /**
     * Validates headers for tenant consistency.
     */
    private SecurityEvaluationResult validateTenantHeaders(Map<String, String> headers, String tenantId) {
        // Check X-Tenant-ID header if present
        String headerTenant = headers.get("X-Tenant-ID");
        if (headerTenant != null && !headerTenant.trim().isEmpty()) {
            if (!headerTenant.equals(tenantId)) {
                return SecurityPolicyService.SecurityEvaluationResult.builder()
                    .allowed(false)
                    .reason("Header tenant ID does not match principal tenant: " + headerTenant + " vs " + tenantId)
                    .errorCode("CROSS_TENANT_HEADER_ACCESS")
                    .build();
            }
        }

        return SecurityPolicyService.SecurityEvaluationResult.builder()
            .allowed(true)
            .build();
    }

    /**
     * Checks if this is a cross-tenant request (admin operations).
     */
    private boolean isCrossTenantRequest(String path, String tenantId) {
        return path.startsWith("/api/v1/admin/") || 
               path.startsWith("/api/v1/tenants/") ||
               path.contains("/cross-tenant/");
    }

    /**
     * Validates cross-tenant access for admin users.
     */
    private SecurityEvaluationResult validateCrossTenantAccess(Principal principal, String path) {
        Set<String> roles = Set.copyOf(principal.getRoles());
        if (roles == null || !roles.contains("ADMIN")) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(false)
                .reason("Cross-tenant access requires admin role")
                .errorCode("INSUFFICIENT_PRIVILEGES_CROSS_TENANT")
                .build();
        }

        // Additional validation for specific admin operations
        if (path.startsWith("/api/v1/tenants/") && !path.contains(principal.getTenantId())) {
            // Only allow tenant admins to manage their own tenants unless system admin
            if (!roles.contains("SYSTEM_ADMIN")) {
                return SecurityPolicyService.SecurityEvaluationResult.builder()
                    .allowed(false)
                    .reason("System admin role required for cross-tenant management")
                    .errorCode("INSUFFICIENT_PRIVILEGES_SYSTEM_ADMIN")
                    .build();
            }
        }

        return SecurityPolicyService.SecurityEvaluationResult.builder()
            .allowed(true)
            .reason("Cross-tenant access granted to admin")
            .build();
    }

    /**
     * Gets tenant isolation statistics.
     */
    public TenantIsolationStats getStats() {
        return new TenantIsolationStats(
            bypassPaths.size(),
            strictMode,
            tenantPathPattern.pattern()
        );
    }

    /**
     * Tenant isolation statistics.
     */
    public static class TenantIsolationStats {
        private final int bypassPathsCount;
        private final boolean strictMode;
        private final String pathPattern;

        public TenantIsolationStats(int bypassPathsCount, boolean strictMode, String pathPattern) {
            this.bypassPathsCount = bypassPathsCount;
            this.strictMode = strictMode;
            this.pathPattern = pathPattern;
        }

        public int getBypassPathsCount() {
            return bypassPathsCount;
        }

        public boolean isStrictMode() {
            return strictMode;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        @Override
        public String toString() {
            return String.format("TenantIsolationStats{bypassPaths=%d, strictMode=%s, pattern=%s}",
                    bypassPathsCount, strictMode, pathPattern);
        }
    }
}
