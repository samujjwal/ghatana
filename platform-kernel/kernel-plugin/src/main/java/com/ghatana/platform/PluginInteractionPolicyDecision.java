package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

/**
 * Result of plugin interaction policy evaluation.
 *
 * @doc.type record
 * @doc.purpose Allow/deny outcome for plugin broker policy checks
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record PluginInteractionPolicyDecision(
        boolean allowed,
        @NotNull String reasonCode,
        @NotNull String message
) {
    public static PluginInteractionPolicyDecision allow() {
        return new PluginInteractionPolicyDecision(true, "plugin.policy_allowed", "Allowed");
    }

    public static PluginInteractionPolicyDecision denied(@NotNull String reasonCode, @NotNull String message) {
        return new PluginInteractionPolicyDecision(false, reasonCode, message);
    }
}
