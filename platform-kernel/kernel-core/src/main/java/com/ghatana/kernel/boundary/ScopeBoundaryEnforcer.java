package com.ghatana.kernel.boundary;

import com.ghatana.kernel.context.KernelTenantContext;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Objects;

/**
 * Scope-aware boundary enforcer using policy-driven access control.
 *
 * <p>Canonical replacement for {@link ProductBoundaryEnforcer}. Enforces cross-scope
 * access control through a three-layer security model that operates on {@link ScopeDescriptor}
 * and {@link ClassificationDescriptor} rather than hardcoded product id allowlists:</p>
 * <ol>
 *   <li><b>Scope Boundary</b>: {@link BoundaryPolicyResolver} evaluates scope, resource, action,
 *       and classification to produce a {@link BoundaryPolicyResolver.BoundaryDecision}</li>
 *   <li><b>Tenant Permission</b>: Verifies the tenant context grants the required permission</li>
 *   <li><b>Consent &amp; Audit</b>: Enforces consent requirements flagged by the policy decision</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Scope-aware boundary enforcement replacing product-id allowlists
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class ScopeBoundaryEnforcer {

    private final BoundaryPolicyResolver policyResolver;

    /**
     * Creates a scope-aware boundary enforcer.
     *
     * @param policyResolver resolves boundary access policy from scope and classification
     */
    public ScopeBoundaryEnforcer(BoundaryPolicyResolver policyResolver) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver cannot be null");
    }

    /**
     * Checks if access is allowed across all security layers.
     *
     * @param source         the scope requesting access
     * @param target         the scope being accessed
     * @param resource       the resource being accessed
     * @param action         the action being performed (read, write, execute)
     * @param classification classification metadata for the access context
     * @param tenantContext  the tenant context for permission checking
     * @return true if access is allowed
     */
    public boolean canAccess(ScopeDescriptor source, ScopeDescriptor target,
                             String resource, String action,
                             ClassificationDescriptor classification,
                             KernelTenantContext tenantContext) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(tenantContext, "tenantContext cannot be null");

        // Layer 1: Policy-driven boundary check
        BoundaryPolicyResolver.BoundaryDecision decision =
                policyResolver.resolve(source, target, resource, action, classification);

        if (!decision.allowed()) {
            return false;
        }

        // Layer 2: Tenant permission check
        String requiredPermission = action + ":" + resource;
        if (!tenantContext.hasPermission(requiredPermission)) {
            return false;
        }

        // Layer 3: Consent check (if required by policy)
        if (decision.requiresConsent()) {
            String consentFeature = "cross-scope.consent." + target.getScopeType().name().toLowerCase();
            if (!tenantContext.isFeatureEnabled(consentFeature)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates access and throws exception if denied.
     *
     * @param source         the scope requesting access
     * @param target         the scope being accessed
     * @param resource       the resource being accessed
     * @param action         the action being performed
     * @param classification classification metadata
     * @param tenantContext  the tenant context
     * @throws ScopeBoundaryException if access is denied
     */
    public void validateAccess(ScopeDescriptor source, ScopeDescriptor target,
                               String resource, String action,
                               ClassificationDescriptor classification,
                               KernelTenantContext tenantContext) {
        if (!canAccess(source, target, resource, action, classification, tenantContext)) {
            throw new ScopeBoundaryException(
                    String.format("Access denied: %s cannot %s %s in %s",
                            source, action, resource, target));
        }
    }

    /**
     * Exception thrown when scope boundary access is denied.
     */
    public static class ScopeBoundaryException extends RuntimeException {
        public ScopeBoundaryException(String message) {
            super(message);
        }
    }
}
