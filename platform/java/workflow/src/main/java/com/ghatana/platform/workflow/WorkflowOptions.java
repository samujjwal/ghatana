/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Common execution options applicable to both ephemeral and durable workflows.
 *
 * <p>Provides factory methods for common configurations:
 * <pre>{@code
 * WorkflowOptions opts = WorkflowOptions.ephemeral();
 * WorkflowOptions durable = WorkflowOptions.durable()
 *     .withTimeout(Duration.ofHours(1))
 *     .withMaxRetries(5);
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Workflow execution options (timeout, retries, saga policy)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record WorkflowOptions(
        @NotNull WorkflowKind kind,
        @Nullable Duration timeout,
        int maxRetries,
        @NotNull SagaPolicy sagaPolicy
) {

    /**
     * Canonical constructor with validation.
     */
    public WorkflowOptions {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sagaPolicy, "sagaPolicy");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got: " + maxRetries);
        }
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
        }
    }

    /**
     * Default options for ephemeral (in-memory) workflows.
     *
     * @return options with EPHEMERAL kind, 5-minute timeout, 0 retries, no saga
     */
    public static WorkflowOptions ephemeral() {
        return new WorkflowOptions(WorkflowKind.EPHEMERAL, Duration.ofMinutes(5), 0, SagaPolicy.NONE);
    }

    /**
     * Default options for durable (persisted) workflows.
     *
     * @return options with DURABLE kind, no timeout, 3 retries, backward compensation
     */
    public static WorkflowOptions durable() {
        return new WorkflowOptions(WorkflowKind.DURABLE, null, 3, SagaPolicy.BACKWARD_COMPENSATION);
    }

    /**
     * Returns a copy with the specified timeout.
     */
    public WorkflowOptions withTimeout(@Nullable Duration timeout) {
        return new WorkflowOptions(kind, timeout, maxRetries, sagaPolicy);
    }

    /**
     * Returns a copy with the specified max retries.
     */
    public WorkflowOptions withMaxRetries(int maxRetries) {
        return new WorkflowOptions(kind, timeout, maxRetries, sagaPolicy);
    }

    /**
     * Returns a copy with the specified saga policy.
     */
    public WorkflowOptions withSagaPolicy(@NotNull SagaPolicy sagaPolicy) {
        return new WorkflowOptions(kind, timeout, maxRetries, sagaPolicy);
    }
}
