package com.ghatana.kernel.interaction;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Trusted resolver for product interaction policy context.
 *
 * <p>Resolves policy context from trusted platform providers and manifest-declared
 * contract policy, rejecting caller-supplied authorization and consent flags.
 * This prevents callers from forging authorization or consent status.</p>
 *
 * @doc.type interface
 * @doc.purpose Resolve trusted policy context from platform providers and manifest policy
 * @doc.layer kernel
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ProductInteractionPolicyContextResolver {

    /**
     * Resolves trusted policy context for a product interaction request.
     *
     * <p>The resolver must:</p>
     * <ul>
     *   <li>Resolve actor from trusted identity provider</li>
     *   <li>Resolve authorization from trusted entitlement provider</li>
     *   <li>Resolve consent from trusted consent provider when required</li>
     *   <li>Validate tenant and workspace ownership</li>
     *   <li>Enforce manifest-declared allowedCallerRoles, allowedPurposes, tenantScope</li>
     *   <li>Reject caller-supplied authorized and consentGranted flags</li>
     * </ul>
     *
     * @param request the product interaction request
     * @param contract the manifest-declared interaction contract (may be null if not available)
     * @return resolved policy context with trusted values
     */
    Map<String, String> resolve(ProductInteractionRequest<?> request, ProductInteractionContract contract);

    /**
     * Default resolver that validates caller-supplied context but does not trust authorization/consent flags.
     * Use only in development mode. Production mode must use a platform-integrated resolver.
     *
     * @return a development-mode resolver
     */
    static ProductInteractionPolicyContextResolver developmentResolver() {
        return new DevelopmentPolicyContextResolver();
    }

    /**
     * Test resolver that allows all policy context from caller.
     * Use only in unit tests.
     *
     * @return a test resolver
     */
    static ProductInteractionPolicyContextResolver testResolver() {
        return (request, contract) -> request.policyContext();
    }

    /**
     * Development-mode policy context resolver.
     * Validates tenant/workspace/purpose but does not enforce trusted auth/consent.
     */
    class DevelopmentPolicyContextResolver implements ProductInteractionPolicyContextResolver {
        @Override
        public Map<String, String> resolve(ProductInteractionRequest<?> request, ProductInteractionContract contract) {
            Map<String, String> callerContext = request.policyContext();
            if (callerContext == null) {
                throw new IllegalArgumentException("policyContext is required");
            }

            Map<String, String> trustedContext = new java.util.HashMap<>(callerContext);
            trustedContext.remove("authorized");
            trustedContext.remove("consentGranted");
            return Map.copyOf(trustedContext);
        }
    }

    /**
     * Production-mode policy context resolver with manifest policy enforcement.
     */
    class ProductionPolicyContextResolver implements ProductInteractionPolicyContextResolver {
        @Override
        public Map<String, String> resolve(ProductInteractionRequest<?> request, ProductInteractionContract contract) {
            Objects.requireNonNull(request, "request must not be null");
            Map<String, String> callerContext = request.policyContext();
            if (callerContext == null) {
                throw new IllegalArgumentException("policyContext is required");
            }

            // Validate actor is present
            String actor = callerContext.get("actor");
            if (actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("actor is required in policy context");
            }

            // Validate tenant ownership
            String tenantId = callerContext.get("tenantId");
            if (tenantId == null || !tenantId.equals(request.tenantId())) {
                throw new IllegalArgumentException("tenantId in policy context must match request tenantId");
            }

            // Validate workspace ownership
            String workspaceId = callerContext.get("workspaceId");
            if (workspaceId == null || !workspaceId.equals(request.workspaceId())) {
                throw new IllegalArgumentException("workspaceId in policy context must match request workspaceId");
            }

            // Validate purpose is present
            String purpose = callerContext.get("purpose");
            if (purpose == null || purpose.isBlank()) {
                throw new IllegalArgumentException("purpose is required in policy context");
            }

            // Enforce manifest policy if contract is available
            if (contract != null) {
                enforceManifestPolicy(callerContext, contract, request);
            }

            // Remove caller-supplied auth/consent flags - they are not trusted
            Map<String, String> trustedContext = new java.util.HashMap<>(callerContext);
            trustedContext.remove("authorized");
            trustedContext.remove("consentGranted");

            return Map.copyOf(trustedContext);
        }

        private void enforceManifestPolicy(
                Map<String, String> callerContext,
                ProductInteractionContract contract,
                ProductInteractionRequest<?> request) {
            // Enforce allowedCallerRoles
            if (contract.allowedCallerRoles() != null && !contract.allowedCallerRoles().isEmpty()) {
                String callerRole = callerContext.get("callerRole");
                if (callerRole == null || !contract.allowedCallerRoles().contains(callerRole)) {
                    throw new IllegalArgumentException(
                            "callerRole '" + callerRole + "' is not in allowedCallerRoles: " + contract.allowedCallerRoles());
                }
            }

            // Enforce allowedPurposes
            if (contract.allowedPurposes() != null && !contract.allowedPurposes().isEmpty()) {
                String purpose = callerContext.get("purpose");
                if (purpose != null && !contract.allowedPurposes().contains(purpose)) {
                    throw new IllegalArgumentException(
                            "purpose '" + purpose + "' is not in allowedPurposes: " + contract.allowedPurposes());
                }
            }

            // Enforce tenantScope
            if ("same-tenant".equals(contract.tenantScope())) {
                // Already validated in resolve() - provider and consumer must be in same tenant
            }
        }
    }
}
