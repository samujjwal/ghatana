/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release.rollout;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and querying {@link AgentRolloutRecord} instances.
 *
 * <p>Implementations must not block the ActiveJ event loop.
 * {@link InMemoryAgentRolloutRepository} provides a reference in-memory implementation
 * for use in contract tests.
 *
 * @doc.type interface
 * @doc.purpose SPI for persisting and querying agent rollout records
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface AgentRolloutRepository {

    /**
     * Persists a new rollout record or updates an existing one (upsert by {@code rolloutId}).
     *
     * @param record the rollout record to save
     * @return the saved record
     */
    Promise<AgentRolloutRecord> save(AgentRolloutRecord record);

    /**
     * Finds a rollout record by its unique identifier.
     *
     * @param rolloutId the rollout ID
     * @return an {@code Optional} containing the record if found, or empty
     */
    Promise<Optional<AgentRolloutRecord>> findById(String rolloutId);

    /**
     * Returns all rollout records for a given agent release ID.
     *
     * @param agentReleaseId the release ID
     * @return list of rollout records (may be empty)
     */
    Promise<List<AgentRolloutRecord>> findByReleaseId(String agentReleaseId);

    /**
     * Returns all rollout records for a given tenant and environment.
     *
     * @param tenantId          the tenant ID
     * @param targetEnvironment the target environment (e.g., {@code "production"})
     * @return list of rollout records in that environment for the tenant
     */
    Promise<List<AgentRolloutRecord>> findByTenantAndEnvironment(String tenantId, String targetEnvironment);

    /**
     * Approves a pending rollout request.
     *
     * <p>Implementations must transition the record from {@code PENDING} to {@code APPROVED}
     * and record the {@code approvedBy} identity.
     *
     * @param rolloutId  the rollout to approve
     * @param approvedBy the identity of the approving principal
     * @return the updated rollout record in {@code APPROVED} state
     * @throws IllegalStateException if the rollout is not in {@code PENDING} state or does not exist
     */
    Promise<AgentRolloutRecord> approve(String rolloutId, String approvedBy);

    /**
     * Rejects a pending rollout request with a mandatory reason.
     *
     * <p>Implementations must transition the record from {@code PENDING} to {@code REJECTED}
     * and record the {@code rejectedBy} identity and reason.
     *
     * @param rolloutId  the rollout to reject
     * @param rejectedBy the identity of the rejecting principal
     * @param reason     human-readable explanation for the rejection
     * @return the updated rollout record in {@code REJECTED} state
     * @throws IllegalStateException if the rollout is not in {@code PENDING} state or does not exist
     */
    Promise<AgentRolloutRecord> reject(String rolloutId, String rejectedBy, String reason);

    /**
     * Reverts an approved rollout back to a {@code ROLLED_BACK} state.
     *
     * <p>Only records in the {@code APPROVED} state may be rolled back.
     *
     * @param rolloutId    the rollout to roll back
     * @param rolledBackBy the identity of the operator performing the rollback
     * @return the updated rollout record in {@code ROLLED_BACK} state
     * @throws IllegalStateException if the rollout is not in {@code APPROVED} state or does not exist
     */
    Promise<AgentRolloutRecord> rollback(String rolloutId, String rolledBackBy);
}
