package com.ghatana.kernel.interaction;

/**
 * Policy port invoked before product interaction events dispatch to subscribers.
 *
 * @doc.type interface
 * @doc.purpose Evaluate tenant, workspace, purpose, consent, and authorization policy for product interaction events
 * @doc.layer kernel
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ProductInteractionEventPolicyEvaluator {

    ProductInteractionPolicyDecision evaluate(ProductInteractionEventEnvelope<?> envelope);

    static ProductInteractionEventPolicyEvaluator defaultEvaluator() {
        return envelope -> {
            Object purpose = envelope.policyContext().get("purpose");
            if (!(purpose instanceof String value) || value.isBlank()) {
                return ProductInteractionPolicyDecision.denied("product_interaction.purpose_required");
            }
            return ProductInteractionPolicyDecision.allow();
        };
    }

    static ProductInteractionEventPolicyEvaluator allowAll() {
        return envelope -> ProductInteractionPolicyDecision.allow();
    }
}
