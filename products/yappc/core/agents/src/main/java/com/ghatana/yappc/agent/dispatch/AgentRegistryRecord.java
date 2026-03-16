/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.dispatch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a resolved agent entry from the YAPPC
 * agent registry, suitable for routing and dispatch decisions.
 *
 * <p>This record acts as the common currency between the JDBC-backed
 * {@link AgentRegistryLookup} and the in-memory {@link CatalogAgentDispatcher}.
 * It carries enough metadata for the dispatcher to make routing decisions
 * without holding a live agent instance.
 *
 * @doc.type record
 * @doc.purpose Immutable agent registry record for dispatcher cache and routing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentRegistryRecord(

        /** Unique agent identifier, stable across restarts. */
        @NotNull String agentId,

        /** Human-readable name for logging and UI. */
        @NotNull String name,

        /** Agent type discriminator (e.g. {@code LLM}, {@code PIPELINE}, {@code RULE}). */
        @NotNull String agentType,

        /** Capabilities advertised by this agent (e.g. {@code "requirements"}, {@code "testing"}). */
        @NotNull List<String> capabilities,

        /** The tenant this record belongs to. */
        @NotNull String tenantId,

        /** Semantic version of the agent definition. */
        @NotNull String version,

        /** Opaque configuration blob surfaced from the backing store. */
        @Nullable Map<String, Object> config,

        /** Wall-clock timestamp when the record was last confirmed alive. */
        @Nullable Instant lastHeartbeat

) {

    /**
     * Compact canonical constructor — validates required fields and defensively
     * copies mutable collections.
     */
    public AgentRegistryRecord {
        Objects.requireNonNull(agentId,    "agentId must not be null");
        Objects.requireNonNull(name,       "name must not be null");
        Objects.requireNonNull(agentType,  "agentType must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(version,    "version must not be null");

        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        config       = config       == null ? Map.of()  : Map.copyOf(config);
    }

    /**
     * Returns {@code true} when this record advertises the given capability.
     *
     * @param capability the capability to test (case-sensitive)
     * @return {@code true} if the capability is present
     */
    public boolean hasCapability(@NotNull String capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    /**
     * Convenience factory for simple records without config or heartbeat
     * (e.g. for tests or catalog pre-seeding).
     *
     * @param agentId      unique agent ID
     * @param name         display name
     * @param agentType    type discriminator
     * @param capabilities advertised capability list
     * @param tenantId     owning tenant
     * @param version      semantic version
     * @return a new {@code AgentRegistryRecord}
     */
    public static AgentRegistryRecord of(
            @NotNull String agentId,
            @NotNull String name,
            @NotNull String agentType,
            @NotNull List<String> capabilities,
            @NotNull String tenantId,
            @NotNull String version) {
        return new AgentRegistryRecord(agentId, name, agentType, capabilities, tenantId, version, null, null);
    }
}
