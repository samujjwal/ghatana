/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.runtime;

import com.ghatana.appplatform.plugin.domain.PluginCapability;
import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginTier;
import com.ghatana.appplatform.plugin.domain.PluginTierViolationException;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that a plugin's execution stays within the boundaries defined by its declared tier
 * (STORY-K04-006).
 *
 * <p>Every API call made by a plugin at runtime passes through this enforcer before execution.
 * It cross-checks:
 * <ol>
 *   <li>That the attempted capability is allowed for the plugin's tier</li>
 *   <li>That the plugin declared the capability in its manifest</li>
 * </ol>
 *
 * <p>Tier capability matrix:
 * <ul>
 *   <li>T1 — no capabilities allowed (data-only)</li>
 *   <li>T2 — READ_CONFIG, READ_CALENDAR, QUERY_REF_DATA, EMIT_LOGS</li>
 *   <li>T3 — all capabilities (including EXECUTE_NETWORK, WRITE_DATA); must declare each</li>
 * </ul>
 *
 * @doc.type  class
 * @doc.purpose Runtime gatekeeper enforcing plugin tier capability boundaries (K04-006)
 * @doc.layer kernel
 * @doc.pattern Guard
 */
public final class PluginTierEnforcer {

    // Capabilities available to T2 plugins
    private static final Set<String> T2_ALLOWED = Set.of(
            PluginCapability.READ_CONFIG,
            PluginCapability.READ_CALENDAR,
            PluginCapability.QUERY_REF_DATA,
            PluginCapability.EMIT_LOGS
    );

    // T3 plugins may use any capability, but must declare high-risk ones explicitly.
    // (No exclusion list — T3 is fully trusted but must declare capabilities)

    /**
     * Asserts that the plugin may use the given capability.
     *
     * @param manifest   the plugin manifest
     * @param capability the capability being attempted
     * @throws PluginTierViolationException if the capability is not allowed for the plugin's tier
     *         or was not declared in the manifest
     */
    public void enforce(PluginManifest manifest, String capability) {
        Objects.requireNonNull(manifest,   "manifest");
        Objects.requireNonNull(capability, "capability");

        PluginTier tier = manifest.tier();

        switch (tier) {
            case T1 -> throw new PluginTierViolationException(
                    "T1 plugins may not invoke any runtime capabilities. " +
                    "Plugin=" + manifest.name() + " attempted: " + capability);

            case T2 -> {
                if (!T2_ALLOWED.contains(capability)) {
                    throw new PluginTierViolationException(
                            "T2 plugin attempted disallowed capability: " + capability +
                            ". Plugin=" + manifest.name() +
                            ". Allowed for T2: " + T2_ALLOWED);
                }
                ensureDeclared(manifest, capability);
            }

            case T3 -> ensureDeclared(manifest, capability);
        }
    }

    /**
     * Convenience overload accepting a {@link PluginCapability} record.
     */
    public void enforce(PluginManifest manifest, PluginCapability capability) {
        enforce(manifest, capability.name());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void ensureDeclared(PluginManifest manifest, String capability) {
        Set<String> declared = manifest.capabilities().stream()
                .map(PluginCapability::name)
                .collect(Collectors.toSet());

        if (!declared.contains(capability)) {
            throw new PluginTierViolationException(
                    "Plugin attempted un-declared capability: " + capability +
                    ". Plugin=" + manifest.name() + " declared: " + declared);
        }
    }
}
