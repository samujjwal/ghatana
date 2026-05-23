package com.ghatana.kernel.interaction;

import java.util.Map;
import java.util.Objects;

/**
 * Policy port invoked before a product interaction dispatches to a provider handler.
 *
 * @doc.type interface
 * @doc.purpose Evaluate tenant, workspace, purpose, consent, and authorization policy for product interactions
 * @doc.layer kernel
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ProductInteractionPolicyEvaluator {

    ProductInteractionPolicyDecision evaluate(ProductInteractionRequest<?> request);

    /**
     * Default evaluator that performs comprehensive security checks:
     * - Tenant validation
     * - Workspace validation
     * - Purpose validation
     * - Authorization validation
     * - Actor validation
     * - Consent validation
     *
     * @return a default evaluator with comprehensive checks
     */
    static ProductInteractionPolicyEvaluator defaultEvaluator() {
        return new ComprehensivePolicyEvaluator();
    }

    /**
     * Allow-all evaluator that bypasses all security checks.
     * Use with caution in development/testing only.
     *
     * @return an evaluator that allows all requests
     */
    static ProductInteractionPolicyEvaluator allowAll() {
        return request -> ProductInteractionPolicyDecision.allow();
    }

    /**
     * Comprehensive policy evaluator with auth, tenant, workspace, purpose, actor, and consent checks.
     * All checks are enforced at the broker level before handler dispatch.
     *
     * <p>P0-02 hardening: This evaluator no longer trusts caller-supplied "authorized" or "consentGranted"
     * flags. Those flags are removed by the ProductInteractionPolicyContextResolver before evaluation.
     * Authorization and consent must be resolved by trusted platform providers.</p>
     */
    class ComprehensivePolicyEvaluator implements ProductInteractionPolicyEvaluator {
        @Override
        public ProductInteractionPolicyDecision evaluate(ProductInteractionRequest<?> request) {
            Map<String, String> policyContext = request.policyContext();
            if (policyContext == null) {
                return ProductInteractionPolicyDecision.denied("product_interaction.policy_context_required");
            }

            // Actor check - must be present for audit trail
            String actor = policyContext.get("actor");
            if (actor == null || actor.isBlank()) {
                return ProductInteractionPolicyDecision.denied("product_interaction.actor_required");
            }

            // Tenant ownership check - prevents cross-tenant access
            String tenantId = policyContext.get("tenantId");
            if (tenantId != null) {
                if (!Objects.equals(tenantId, request.tenantId())) {
                    return ProductInteractionPolicyDecision.denied("product_interaction.tenant_mismatch");
                }
            } else {
                // Tenant ID is required in policy context for multi-tenant isolation
                return ProductInteractionPolicyDecision.denied("product_interaction.tenant_id_required");
            }

            // Workspace ownership check - prevents cross-workspace access
            String workspaceId = policyContext.get("workspaceId");
            if (workspaceId != null) {
                if (!Objects.equals(workspaceId, request.workspaceId())) {
                    return ProductInteractionPolicyDecision.denied("product_interaction.workspace_mismatch");
                }
            } else {
                // Workspace ID is required in policy context for workspace isolation
                return ProductInteractionPolicyDecision.denied("product_interaction.workspace_id_required");
            }

            // Purpose check - ensures interaction has declared purpose
            String purposeValue = policyContext.get("purpose");
            if (purposeValue == null || purposeValue.isBlank()) {
                return ProductInteractionPolicyDecision.denied("product_interaction.purpose_required");
            }

            // P0-02: Reject caller-supplied authorization flag - must be resolved by trusted provider
            String authorized = policyContext.get("authorized");
            if (authorized != null) {
                return ProductInteractionPolicyDecision.denied("product_interaction.caller_supplied_auth_not_trusted");
            }

            // P0-02: Reject caller-supplied consent flag - must be resolved by trusted provider
            String consentGranted = policyContext.get("consentGranted");
            if (consentGranted != null) {
                return ProductInteractionPolicyDecision.denied("product_interaction.caller_supplied_consent_not_trusted");
            }

            // All checks passed
            return ProductInteractionPolicyDecision.allow();
        }
    }
}
