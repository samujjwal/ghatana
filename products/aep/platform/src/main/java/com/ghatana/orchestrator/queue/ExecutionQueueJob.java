/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a job in the execution queue.
 *
 * @doc.type record
 * @doc.purpose Immutable representation of a queued pipeline execution job
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record ExecutionQueueJob(
    /** Unique job identifier */
    String jobId,

    /** Tenant identifier for multi-tenancy */
    String tenantId,

    /** Pipeline to execute */
    String pipelineId,

    /** Unique key to prevent duplicate executions */
    String idempotencyKey,

    /** Trigger data that initiated the execution */
    Map<String, Object> triggerData,

    /** Current job status */
    JobStatus status,

    /** Number of execution attempts */
    int attemptCount,

    /** Maximum retry attempts */
    int maxAttempts,

    /** When the job was enqueued */
    Instant enqueuedAt,

    /** When the lease started (for in-progress jobs) */
    Instant leasedAt,

    /** When the lease expires (for visibility timeout) */
    Instant leaseExpiresAt,

    /** Worker that holds the lease */
    String leasedBy,

    /** When the job was completed */
    Instant completedAt,

    /** Error message if failed */
    String errorMessage
) {

    /**
     * Job status enumeration.
     */
    public enum JobStatus {
        /** Job is waiting to be picked up */
        PENDING,

        /** Job is being processed (leased by a worker) */
        IN_PROGRESS,

        /** Job completed successfully */
        COMPLETED,

        /** Job failed after all retry attempts */
        FAILED,

        /** Job was explicitly cancelled */
        CANCELLED,

        /** Job timed out (lease expired without completion) */
        TIMED_OUT
    }

    /**
     * Check if this job can be retried.
     */
    public boolean canRetry() {
        return attemptCount < maxAttempts && status != JobStatus.CANCELLED;
    }

    /**
     * Check if the lease has expired.
     */
    public boolean isLeaseExpired() {
        return leaseExpiresAt != null && Instant.now().isAfter(leaseExpiresAt);
    }
}

