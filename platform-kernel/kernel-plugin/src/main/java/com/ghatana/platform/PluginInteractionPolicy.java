package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Policy attached to a plugin interaction contract.
 *
 * @doc.type record
 * @doc.purpose Policy metadata for brokered plugin interactions
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record PluginInteractionPolicy(
        boolean requiresTenant,
        boolean requiresWorkspace,
        @NotNull Set<String> allowedCallerPluginIds,
        @NotNull Set<String> allowedLifecyclePhases
) {
    public static PluginInteractionPolicy allowAll() {
        return new PluginInteractionPolicy(false, false, Set.of(), Set.of());
    }
}
