/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Repository for durable lifecycle execution persistence.
 *
 * <p>Provides transactional, idempotent persistence of lifecycle execution results
 * with proper project/workspace/actor/correlation ID traceability. Replaces the
 * fire-and-forget HTTP persistence pattern with durable storage.
 *
 * @doc.type interface
 * @doc.purpose Durable repository for lifecycle execution persistence with full traceability
 * @doc.layer api
 * @doc.pattern Repository
 */
public interface LifecycleExecutionRepository {

    /**
     * Persists a lifecycle execution result with full traceability.
     *
     * <p>The execution is persisted under the correct tenant/workspace/project/actor
     * context with correlation ID for request tracing. Idempotency is ensured via
     * the executionId field.
     *
     * @param execution the lifecycle execution record to persist
     * @return Promise that completes when persistence is successful or fails with error
     */
    Promise<Void> persist(@NotNull LifecycleExecution execution);

    /**
     * Retrieves a lifecycle execution by its ID.
     *
     * @param executionId the unique execution ID
     * @return Promise containing the execution record, or null if not found
     */
    Promise<LifecycleExecution> findById(@NotNull String executionId);

    /**
     * Retrieves lifecycle executions for a specific project.
     *
     * @param tenantId the tenant ID
     * @param projectId the project ID
     * @param limit maximum number of results to return
     * @return Promise containing list of execution records
     */
    Promise<java.util.List<LifecycleExecution>> findByProject(
        @NotNull String tenantId,
        @NotNull String projectId,
        int limit
    );

    /**
     * Lifecycle execution record with full traceability.
     */
    record LifecycleExecution(
        String executionId,
        String tenantId,
        String workspaceId,
        String projectId,
        String actorId,
        String correlationId,
        String idempotencyKey,
        Instant startedAt,
        Instant completedAt,
        long totalDurationMs,
        java.util.List<String> executedPhases,
        Map<String, Long> phaseDurationsMs,
        String status,
        Map<String, Object> intentResult,
        Map<String, Object> shapeResult,
        Map<String, Object> validationResult,
        Map<String, Object> generationResult,
        Map<String, Object> runResult,
        Map<String, Object> observationResult,
        Map<String, Object> learningResult,
        Map<String, Object> evolutionResult,
        Map<String, String> metadata
    ) {
        public LifecycleExecution {
            if (executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("executionId is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId is required");
            }
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId is required");
            }
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalArgumentException("correlationId is required");
            }
        }
    }
}
