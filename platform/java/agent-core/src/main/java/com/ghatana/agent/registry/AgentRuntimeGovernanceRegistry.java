/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for agent runtime governance state.
 *
 * <p>Provides a single source of truth for:
 * <ul>
 *   <li><b>Active releases</b>: Current governing release for each agent</li>
 *   <li><b>Rollback state</b>: Previous versions available for rollback</li>
 *   <li><b>Fallback configuration</b>: Fallback agent mappings</li>
 *   <li><b>Governance decisions</b>: Historical decision records</li>
 * </ul>
 *
 * <p>This registry serves as the central coordination point for agent runtime governance,
 * ensuring that all governance decisions are traceable and reversible.
 *
 * @doc.type interface
 * @doc.purpose Central registry for agent runtime governance state
 * @doc.layer agent-runtime
 * @doc.pattern Registry
 */
public interface AgentRuntimeGovernanceRegistry {

    /**
     * Records a governance decision for an agent dispatch.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @param decision the governance decision (allowed/denied/requires-approval)
     * @param reason the reason for the decision
     * @param metadata additional decision metadata
     * @return promise completing when the decision is recorded
     */
    @NotNull
    Promise<Void> recordDecision(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull GovernanceDecision decision,
            @NotNull String reason,
            @NotNull Map<String, String> metadata);

    /**
     * Records a rollback operation for an agent.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @param fromVersion the version being rolled back from
     * @param toVersion the version being rolled back to
     * @param reason the reason for the rollback
     * @return promise completing when the rollback is recorded
     */
    @NotNull
    Promise<Void> recordRollback(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String fromVersion,
            @NotNull String toVersion,
            @NotNull String reason);

    /**
     * Retrieves the previous version for rollback.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @return promise of optional previous version
     */
    @NotNull
    Promise<Optional<String>> getPreviousVersion(@NotNull String agentId, @NotNull String tenantId);

    /**
     * Records a fallback operation when an agent dispatch fails.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @param fallbackAgentId the fallback agent identifier
     * @param reason the reason for the fallback
     * @return promise completing when the fallback is recorded
     */
    @NotNull
    Promise<Void> recordFallback(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String fallbackAgentId,
            @NotNull String reason);

    /**
     * Retrieves the configured fallback agent for a given agent.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @return promise of optional fallback agent ID
     */
    @NotNull
    Promise<Optional<String>> getFallbackAgent(@NotNull String agentId, @NotNull String tenantId);

    /**
     * Queries governance decisions for an agent within a time window.
     *
     * @param agentId the agent identifier
     * @param tenantId the tenant identifier
     * @param from inclusive start time (null for unbounded)
     * @param to exclusive end time (null for unbounded)
     * @param limit maximum number of decisions to return
     * @return promise of governance decision records
     */
    @NotNull
    Promise<java.util.List<GovernanceDecisionRecord>> queryDecisions(
            @NotNull String agentId,
            @NotNull String tenantId,
            @Nullable Instant from,
            @Nullable Instant to,
            int limit);

    /**
     * Governance decision enumeration.
     */
    enum GovernanceDecision {
        ALLOWED,
        DENIED,
        REQUIRES_APPROVAL,
        REQUIRES_VERIFICATION,
        BLOCKED
    }

    /**
     * Governance decision record.
     */
    record GovernanceDecisionRecord(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull GovernanceDecision decision,
            @NotNull String reason,
            @NotNull Map<String, String> metadata,
            @NotNull Instant timestamp
    ) {}
}
