package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Evaluates interaction policy before a broker dispatches work.
 *
 * @doc.type interface
 * @doc.purpose Policy guard for plugin interactions
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface PluginInteractionPolicyEvaluator {
    @NotNull
    PluginInteractionPolicyDecision evaluate(@NotNull PluginInteractionEnvelope<?> envelope,
            @NotNull PluginInteractionPolicy policy);

    static PluginInteractionPolicyEvaluator defaultEvaluator() {
        return (envelope, policy) -> {
            if (!policy.allowedCallerPluginIds().isEmpty()
                    && !policy.allowedCallerPluginIds().contains(envelope.callerPluginId())) {
                return PluginInteractionPolicyDecision.denied("plugin.policy_denied",
                        "Caller plugin is not allowed by this interaction contract");
            }
            if (policy.requiresTenant() && (envelope.tenantId() == null || envelope.tenantId().isBlank())) {
                return PluginInteractionPolicyDecision.denied("plugin.policy_denied",
                        "Tenant scope is required by this interaction contract");
            }
            if (policy.requiresWorkspace() && (envelope.workspaceId() == null || envelope.workspaceId().isBlank())) {
                return PluginInteractionPolicyDecision.denied("plugin.policy_denied",
                        "Workspace scope is required by this interaction contract");
            }
            if (!policy.allowedLifecyclePhases().isEmpty()
                    && (envelope.lifecyclePhase() == null
                    || envelope.lifecyclePhase().isBlank()
                    || !policy.allowedLifecyclePhases().contains(envelope.lifecyclePhase()))) {
                return PluginInteractionPolicyDecision.denied("plugin.lifecycle_phase_denied",
                        "Lifecycle phase is not allowed by this interaction contract");
            }
            return PluginInteractionPolicyDecision.allow();
        };
    }
}
