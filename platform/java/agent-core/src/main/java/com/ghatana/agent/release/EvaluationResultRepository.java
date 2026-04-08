/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and querying {@link EvaluationResult} instances.
 *
 * <p>This interface is defined in {@code platform/java/agent-core} and implemented
 * by product-specific persistence modules (e.g., Data Cloud agent-registry).
 * {@link InMemoryEvaluationResultRepository} provides an in-memory implementation
 * for contract tests and unit test scenarios.
 *
 * <p>All methods use {@link Promise} (ActiveJ) for non-blocking async execution.
 * Implementations must not block the ActiveJ event loop.
 *
 * @doc.type interface
 * @doc.purpose SPI for persisting and querying agent evaluation results
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface EvaluationResultRepository {

    /**
     * Persists a new evaluation result.
     *
     * @param result the result to save
     * @return the saved result
     */
    Promise<EvaluationResult> save(EvaluationResult result);

    /**
     * Finds an evaluation result by its unique ID.
     *
     * @param evaluationId the evaluation ID
     * @return an {@code Optional} containing the result if found, or empty
     */
    Promise<Optional<EvaluationResult>> findById(String evaluationId);

    /**
     * Returns all evaluation results for a given agent release and tenant.
     *
     * @param agentReleaseId the release ID
     * @param tenantId       the tenant scope
     * @return list of results (may be empty)
     */
    Promise<List<EvaluationResult>> findByRelease(String agentReleaseId, String tenantId);

    /**
     * Returns only the passing evaluation results for a given agent release and tenant.
     *
     * @param agentReleaseId the release ID
     * @param tenantId       the tenant scope
     * @return list of passing results (may be empty)
     */
    Promise<List<EvaluationResult>> findPassingByRelease(String agentReleaseId, String tenantId);

    /**
     * Counts the number of evaluations that passed for a given agent release and tenant.
     *
     * @param agentReleaseId the release ID
     * @param tenantId       the tenant scope
     * @return count of passing evaluations
     */
    Promise<Long> countPassing(String agentReleaseId, String tenantId);

    /**
     * Deletes all evaluation results for a given agent release and tenant.
     * Used during rollback or release cleanup.
     *
     * @param agentReleaseId the release ID
     * @param tenantId       the tenant scope
     * @return count of deleted records
     */
    Promise<Long> deleteByRelease(String agentReleaseId, String tenantId);
}
