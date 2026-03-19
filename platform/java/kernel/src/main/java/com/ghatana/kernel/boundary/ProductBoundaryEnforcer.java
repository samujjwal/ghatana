package com.ghatana.kernel.boundary;

import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.registry.KernelRegistry;

import java.util.Objects;
import java.util.Set;

/**
 * Enforces product-to-product access control: product boundary → tenant permission → compliance.
 *
 * <p>Validates cross-product access requests through a three-layer security model:
 * <ol>
 *   <li><b>Product Boundary</b>: Check if source product can access target product resources</li>
 *   <li><b>Tenant Permission</b>: Verify tenant has required permissions for the action</li>
 *   <li><b>Compliance Rules</b>: Ensure access complies with regulatory requirements</li>
 * </ol></p>
 *
 * @doc.type class
 * @doc.purpose Product boundary enforcement — prevents unauthorized cross-product access
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class ProductBoundaryEnforcer {

    private final KernelRegistry registry;
    private final BoundaryPolicy boundaryPolicy;

    /**
     * Creates a new boundary enforcer.
     *
     * @param registry the kernel registry for capability discovery
     */
    public ProductBoundaryEnforcer(KernelRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.boundaryPolicy = new BoundaryPolicy();
    }

    /**
     * Checks if access is allowed across all three security layers.
     *
     * @param sourceProductId the source product requesting access
     * @param targetProductId the target product being accessed
     * @param resource the resource being accessed
     * @param action the action being performed (read, write, execute)
     * @param context the tenant context for permission checking
     * @return true if access is allowed
     */
    public boolean canAccess(String sourceProductId, String targetProductId,
                            String resource, String action, KernelTenantContext context) {
        Objects.requireNonNull(sourceProductId, "sourceProductId cannot be null");
        Objects.requireNonNull(targetProductId, "targetProductId cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        // Layer 1: Check product boundaries
        if (!isProductBoundaryAllowed(sourceProductId, targetProductId, resource, action)) {
            return false;
        }

        // Layer 2: Check tenant permissions
        if (!hasTenantPermission(context, resource, action)) {
            return false;
        }

        // Layer 3: Check compliance rules
        if (!isComplianceAllowed(sourceProductId, targetProductId, resource, action, context)) {
            return false;
        }

        return true;
    }

    /**
     * Validates access and throws exception if denied.
     *
     * @param sourceProductId the source product requesting access
     * @param targetProductId the target product being accessed
     * @param resource the resource being accessed
     * @param action the action being performed
     * @param context the tenant context
     * @throws ProductBoundaryException if access is denied
     */
    public void validateAccess(String sourceProductId, String targetProductId,
                               String resource, String action, KernelTenantContext context) {
        if (!canAccess(sourceProductId, targetProductId, resource, action, context)) {
            throw new ProductBoundaryException(
                String.format("Access denied: %s cannot %s %s in %s",
                    sourceProductId, action, resource, targetProductId));
        }
    }

    // ==================== Layer 1: Product Boundary ====================

    private boolean isProductBoundaryAllowed(String sourceProductId, String targetProductId,
                                            String resource, String action) {
        // Check if source is allowed to access target
        Set<String> allowedTargets = boundaryPolicy.getAllowedTargets(sourceProductId);
        if (!allowedTargets.contains(targetProductId) && !allowedTargets.contains("*")) {
            return false;
        }

        // Check resource-specific rules
        return boundaryPolicy.isResourceAllowed(sourceProductId, targetProductId, resource, action);
    }

    // ==================== Layer 2: Tenant Permission ====================

    private boolean hasTenantPermission(KernelTenantContext context, String resource, String action) {
        // Check if tenant has the required permission
        String requiredPermission = action + ":" + resource;
        return context.hasPermission(requiredPermission);
    }

    // ==================== Layer 3: Compliance ====================

    private boolean isComplianceAllowed(String sourceProductId, String targetProductId,
                                       String resource, String action, KernelTenantContext context) {
        // Healthcare data protection
        if (targetProductId.equals("phr")) {
            // PHR data requires explicit consent
            if (!context.isFeatureEnabled("phr.cross-product.access")) {
                return false;
            }
        }

        // Financial data protection
        if (targetProductId.equals("finance")) {
            // Financial data requires audit trail
            if (!context.isFeatureEnabled("finance.audit.enabled")) {
                return false;
            }
        }

        // Cross-border data transfer restrictions
        String tenantRegion = context.getConfig("tenant.region", String.class);
        if (tenantRegion != null && isRestrictedRegion(tenantRegion, targetProductId)) {
            return false;
        }

        return true;
    }

    private boolean isRestrictedRegion(String region, String targetProductId) {
        // Check GDPR, data residency, and other regional restrictions
        Set<String> restrictedProducts = Set.of("phr", "finance");
        return restrictedProducts.contains(targetProductId) &&
               Set.of("CN", "RU").contains(region); // Example restricted regions
    }

    // ==================== Inner Types ====================

    /**
     * Boundary policy configuration.
     */
    private static class BoundaryPolicy {
        // Define which products can access which other products
        private final java.util.Map<String, Set<String>> allowedTargets = java.util.Map.of(
            "phr", Set.of("finance"),           // PHR can access Finance (for billing)
            "finance", Set.of("phr"),           // Finance can access PHR (for healthcare payments)
            "flashit", Set.of("*"),             // FlashIt can access all (context platform)
            "aura", Set.of("phr", "finance")    // Aura can access PHR and Finance (recommendations)
        );

        // Define resource-specific rules
        private final java.util.Map<String, Set<String>> resourceActions = java.util.Map.of(
            "patient.records", Set.of("read"),              // Read-only
            "trade.records", Set.of("read", "write"),       // Read-write
            "billing", Set.of("read", "write", "execute")  // Full access
        );

        Set<String> getAllowedTargets(String sourceProductId) {
            return allowedTargets.getOrDefault(sourceProductId, Set.of());
        }

        boolean isResourceAllowed(String sourceProductId, String targetProductId,
                                 String resource, String action) {
            // Check if action is allowed on resource
            Set<String> allowedActions = resourceActions.getOrDefault(resource, Set.of("read"));
            return allowedActions.contains(action);
        }
    }

    /**
     * Exception thrown when boundary access is denied.
     */
    public static class ProductBoundaryException extends RuntimeException {
        public ProductBoundaryException(String message) {
            super(message);
        }
    }
}
