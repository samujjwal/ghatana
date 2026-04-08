/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and querying {@link MemoryNamespace} instances.
 *
 * <p>This interface is defined in {@code platform/java/agent-core} and is implemented
 * by product-specific persistence modules (e.g., Data Cloud agent-registry).
 * {@link InMemoryMemoryNamespaceRepository} provides an in-memory implementation
 * for contract tests.
 *
 * <p>All methods use {@link Promise} (ActiveJ) for non-blocking async execution.
 * Implementations must not block the ActiveJ event loop.
 *
 * @doc.type interface
 * @doc.purpose SPI for persisting and querying memory namespaces
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface MemoryNamespaceRepository {

    /**
     * Persists or updates a {@link MemoryNamespace}.
     *
     * @param namespace the namespace to save (upsert by {@code namespaceId})
     * @return the saved namespace
     */
    Promise<MemoryNamespace> save(MemoryNamespace namespace);

    /**
     * Finds a namespace by its unique identifier.
     *
     * @param namespaceId the namespace ID
     * @return an {@code Optional} containing the namespace if found, or empty
     */
    Promise<Optional<MemoryNamespace>> findById(String namespaceId);

    /**
     * Returns all namespaces registered for a given agent and tenant.
     *
     * @param agentId  the agent ID
     * @param tenantId the tenant scope
     * @return list of namespaces (may be empty)
     */
    Promise<List<MemoryNamespace>> findByAgent(String agentId, String tenantId);

    /**
     * Returns all promotion-enabled namespaces for a given agent and tenant.
     *
     * @param agentId  the agent ID
     * @param tenantId the tenant scope
     * @return list of promotion-enabled namespaces (may be empty)
     */
    Promise<List<MemoryNamespace>> findPromotionEnabledByAgent(String agentId, String tenantId);

    /**
     * Finds a namespace by agent, scope, and tenant. Useful when looking up the
     * canonical procedural namespace for a given agent.
     *
     * @param agentId  the agent ID
     * @param scope    the memory scope
     * @param tenantId the tenant scope
     * @return an {@code Optional} containing the namespace if found, or empty
     */
    Promise<Optional<MemoryNamespace>> findByAgentAndScope(String agentId, MemoryScope scope, String tenantId);

    /**
     * Deletes a namespace and all associated policy metadata.
     *
     * @param namespaceId the namespace ID
     * @param tenantId    the tenant scope
     * @return {@code true} if a namespace was deleted, {@code false} if not found
     */
    Promise<Boolean> delete(String namespaceId, String tenantId);
}
