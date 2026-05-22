package com.ghatana.kernel.plugin;

/**
 * Policy evaluator for plugin interactions.
 *
 * <p>This interface defines the contract for evaluating whether a plugin interaction
 * is allowed based on policy rules. It mirrors the product interaction policy evaluation
 * but for plugin-specific policies.</p>
 *
 * @doc.type interface
 * @doc.purpose Policy evaluation contract for plugin interactions
 * @doc.layer kernel
 * @doc.pattern Policy
 */
@FunctionalInterface
public interface PluginInteractionPolicyEvaluator {

    /**
     * Evaluates whether a plugin interaction is allowed.
     *
     * @param envelope the event envelope
     * @return the policy decision
     */
    PluginInteractionEventBroker.PluginInteractionPolicyDecision evaluate(
        PluginInteractionEventEnvelope<?> envelope);
}
