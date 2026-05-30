/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and querying {@link AgentRelease} instances.
 *
 * <p>This interface is defined in {@code platform/java/agent-core} and implemented
 * by product-specific persistence modules (e.g., the Data Cloud agent-registry).
 * {@link InMemoryAgentReleaseRepository} provides a fast in-memory implementation
 * used in contract tests.
 *
 * <p>All methods use {@link Promise} (ActiveJ) for non-blocking async execution.
 * Implementations must not block the ActiveJ event loop.
 *
 * @doc.type interface
 * @doc.purpose SPI for persisting and querying agent releases
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface AgentReleaseRepository {

    /**
     * Persists or updates an {@link AgentRelease}.
     *
     * @param release the release to save (insert or upsert by {@code agentReleaseId})
     * @return the saved release
     */
    Promise<AgentRelease> save(AgentRelease release);

    /**
     * Finds a release by its unique identifier.
     *
     * @param agentReleaseId the release ID
     * @return an {@code Optional} containing the release if found, or empty
     */
    Promise<Optional<AgentRelease>> findById(String agentReleaseId);

    /**
     * Returns all releases for a given agent ID, across all states and tenants.
     *
     * @param agentId the agent ID
     * @return list of releases (may be empty)
     */
    Promise<List<AgentRelease>> findByAgentId(String agentId);

    /**
     * Returns the currently {@code ACTIVE} release for a given agent and tenant.
     *
     * @param agentId  the agent ID
     * @param tenantId the tenant scoping the lookup
     * @return an {@code Optional} containing the active release if one exists
     */
    Promise<Optional<AgentRelease>> findActiveRelease(String agentId, String tenantId);

    /**
     * Transitions a release to a new {@link AgentReleaseState}.
     *
     * <p>Implementations must validate that the transition is permitted by
     * {@link AgentReleaseState#canTransitionTo(AgentReleaseState)}.
     *
     * @param agentReleaseId the release to transition
     * @param targetState    the desired next state
     * @param principalId    the identity of the actor performing the transition
     * @return the updated release
     * @throws IllegalStateException if the transition is not permitted or the release does not exist
     */
    Promise<AgentRelease> transition(String agentReleaseId, AgentReleaseState targetState, String principalId);

    /**
     * Returns all releases in a given {@link AgentReleaseState}.
     *
     * @param state the state to query
     * @return list of releases in that state
     */
    Promise<List<AgentRelease>> findByState(AgentReleaseState state);

    /**
     * Returns the governing release for an agent and tenant.
     *
     * <p>The governing release is the most recently updated release that is in a live
     * or emergency state — specifically {@code ACTIVE}, {@code CANARY}, {@code SHADOW},
     * or {@code BLOCKED}. It excludes {@code RETIRED}, {@code DEPRECATED}, {@code DRAFT},
     * and {@code VALIDATED} releases.
     *
     * <p>Dispatch callers should prefer this method over {@link #findActiveRelease} when
     * they need to enforce block-state guards:
     * <ul>
     *   <li>If the returned release is {@code BLOCKED} → deny dispatch.</li>
     *   <li>If the returned release is {@code ACTIVE}/{@code CANARY}/{@code SHADOW} → dispatch.</li>
     *   <li>If {@code Optional.empty()} → no governing release; apply fallback policy.</li>
     * </ul>
     *
     * @param agentId  the agent ID
     * @param tenantId the tenant scoping the lookup
     * @return an {@code Optional} containing the governing release if one exists
     */
    Promise<Optional<AgentRelease>> findGoverningRelease(String agentId, String tenantId);

    /**
     * Finds an optional tenant-scoped runtime configuration overlay for a release.
     *
     * <p>Repository implementations that do not persist instance overlays may rely
     * on this fail-open default so dispatch can continue enforcing release-state
     * policy without a kill-switch overlay.
     *
     * @param agentReleaseId the release ID
     * @param tenantId the tenant scoping the lookup
     * @return an optional runtime configuration overlay
     */
    default Promise<Optional<AgentInstanceConfig>> findInstanceConfig(String agentReleaseId, String tenantId) {
        return Promise.of(Optional.empty());
    }
}
