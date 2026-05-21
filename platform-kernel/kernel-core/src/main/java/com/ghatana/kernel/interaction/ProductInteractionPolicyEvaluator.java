package com.ghatana.kernel.interaction;

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

    static ProductInteractionPolicyEvaluator defaultEvaluator() {
        return request -> {
            Object purpose = request.policyContext().get("purpose");
            if (!(purpose instanceof String value) || value.isBlank()) {
                return ProductInteractionPolicyDecision.denied("product_interaction.purpose_required");
            }
            return ProductInteractionPolicyDecision.allow();
        };
    }

    static ProductInteractionPolicyEvaluator allowAll() {
        return request -> ProductInteractionPolicyDecision.allow();
    }
}
