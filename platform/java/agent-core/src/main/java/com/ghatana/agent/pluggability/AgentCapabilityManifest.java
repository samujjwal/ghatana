/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.pluggability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable capability manifest declared by an agent package.
 *
 * <p>The manifest describes the agent's interaction modes, supervision role,
 * handoff capability, declared tool contracts, and shared context keys it may
 * read or write. The {@link AgentCapabilityManifestValidator} enforces all
 * cross-field constraints.
 *
 * @param agentId            unique agent identifier
 * @param agentVersion       SemVer-compatible version string
 * @param tenantId           tenant scope for this manifest
 * @param interactionModes   supported interaction modes (at least one required)
 * @param supervisionRole    supervision role; may be null for standalone agents
 * @param handoffCapability  handoff participation level
 * @param declaredToolIds    tool IDs this agent may invoke (immutable)
 * @param sharedContextKeys  context keys this agent may read or write (immutable)
 * @param metadata           additional key/value metadata (immutable)
 *
 * @doc.type class
 * @doc.purpose Immutable agent capability manifest for pluggability and inter-agent protocol
 * @doc.layer platform
 * @doc.pattern Record
 */
public record AgentCapabilityManifest(
        @NotNull String agentId,
        @NotNull String agentVersion,
        @NotNull String tenantId,
        @NotNull List<InteractionMode> interactionModes,
        @Nullable SupervisionRole supervisionRole,
        @NotNull HandoffCapability handoffCapability,
        @NotNull List<String> declaredToolIds,
        @NotNull List<String> sharedContextKeys,
        @NotNull Map<String, String> metadata
) {
    /**
     * Compact constructor — validates required fields and makes collections immutable.
     */
    public AgentCapabilityManifest {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (agentVersion == null || agentVersion.isBlank()) {
            throw new IllegalArgumentException("agentVersion must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(interactionModes, "interactionModes");
        if (interactionModes.isEmpty()) {
            throw new IllegalArgumentException("interactionModes must not be empty");
        }
        Objects.requireNonNull(handoffCapability, "handoffCapability");
        Objects.requireNonNull(declaredToolIds, "declaredToolIds");
        Objects.requireNonNull(sharedContextKeys, "sharedContextKeys");
        Objects.requireNonNull(metadata, "metadata");
        interactionModes = List.copyOf(interactionModes);
        declaredToolIds = List.copyOf(declaredToolIds);
        sharedContextKeys = List.copyOf(sharedContextKeys);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Returns {@code true} if the manifest declares the given interaction mode.
     *
     * @param mode the mode to check
     */
    public boolean supports(@NotNull InteractionMode mode) {
        return interactionModes.contains(Objects.requireNonNull(mode, "mode"));
    }

    /**
     * Returns {@code true} if this agent can receive handoffs.
     */
    public boolean canReceiveHandoff() {
        return handoffCapability == HandoffCapability.RECEIVER_ONLY
                || handoffCapability == HandoffCapability.BIDIRECTIONAL;
    }

    /**
     * Returns {@code true} if this agent can initiate handoffs.
     */
    public boolean canInitiateHandoff() {
        return handoffCapability == HandoffCapability.INITIATOR_ONLY
                || handoffCapability == HandoffCapability.BIDIRECTIONAL;
    }

    /**
     * Convenience factory for a standalone autonomous agent with no tool declarations.
     *
     * @param agentId      agent identifier
     * @param agentVersion version string
     * @param tenantId     tenant scope
     */
    public static AgentCapabilityManifest standalone(
            String agentId, String agentVersion, String tenantId) {
        return new AgentCapabilityManifest(agentId, agentVersion, tenantId,
                List.of(InteractionMode.AUTONOMOUS), SupervisionRole.STANDALONE,
                HandoffCapability.NONE, List.of(), List.of(), Map.of());
    }
}
