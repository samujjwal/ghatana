/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents
 */
package com.ghatana.yappc.agent.dispatch;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Port interface for agent metadata lookup from a persistent backing store.
 *
 * <p>Implementations are typically JDBC-backed (e.g. backed by the
 * {@code yappc_agent_registry} table) and are called by
 * {@link RegistryReadThroughDispatcher} on cache misses.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Returns {@code Optional.empty()} when no matching active agent is found.</li>
 *   <li>Never returns {@code null} — wrap database exceptions in a failed Promise.</li>
 *   <li>MUST NOT block the ActiveJ Eventloop; use {@code Promise.ofBlocking(executor, ...)}
 *       around any blocking JDBC calls.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Port for persistent agent registry lookup (JDBC or remote)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface AgentRegistryLookup {

    /**
     * Resolves an agent entry from the persistent store.
     *
     * @param tenantId the tenant scope for the lookup (never {@code null} or blank)
     * @param agentId  the unique agent identifier (never {@code null} or blank)
     * @return a Promise of an Optional containing the matching {@link AgentRegistryRecord},
     *         or {@code Optional.empty()} when not found
     */
    @NotNull
    Promise<Optional<AgentRegistryRecord>> findById(
            @NotNull String tenantId,
            @NotNull String agentId);
}
