/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import com.ghatana.orchestrator.queue.ExecutionQueueJob;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * JPA entity for durable execution queue jobs.
 *
 * <p>Uses PostgreSQL SKIP LOCKED for distributed job claiming with visibility timeouts.
 *
 * @doc.type class
 * @doc.purpose Persistent entity for durable execution queue
 * @doc.layer core
 * @doc.pattern Entity
 */
@Entity
@Table(name = "execution_queue", indexes = {
    @Index(name = "idx_execution_queue_tenant_status", columnList = "tenant_id, status"),
    @Index(name = "idx_execution_queue_status_enqueued", columnList = "status, enqueued_at"),
    @Index(name = "idx_execution_queue_lease_expires", columnList = "lease_expires_at"),
    @Index(name = "idx_execution_queue_idempotency", columnList = "tenant_id, idempotency_key", unique = true),
    @Index(name = "idx_execution_queue_pipeline", columnList = "tenant_id, pipeline_id")
})
public class ExecutionQueueJobEntity {

    @Id
    @Column(name = "job_id", length = 100)
    private String jobId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "pipeline_id", nullable = false, length = 100)
    private String pipelineId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_data", columnDefinition = "jsonb")
    private Map<String, Object> triggerData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionQueueJob.JobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "enqueued_at", nullable = false)
    private Instant enqueuedAt;

    @Column(name = "leased_at")
    private Instant leasedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "leased_by", length = 100)
    private String leasedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Default constructor for JPA
    public ExecutionQueueJobEntity() {
    }

    // Constructor for creating new jobs
    public ExecutionQueueJobEntity(String jobId, String tenantId, String pipelineId,
                                   String idempotencyKey, Map<String, Object> triggerData,
                                   int maxAttempts) {
        this.jobId = jobId;
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.idempotencyKey = idempotencyKey;
        this.triggerData = triggerData;
        this.maxAttempts = maxAttempts;
        this.status = ExecutionQueueJob.JobStatus.PENDING;
        this.attemptCount = 0;
        this.enqueuedAt = Instant.now();
    }

    /**
     * Convert to domain object.
     */
    public ExecutionQueueJob toDomainObject() {
        return new ExecutionQueueJob(
            jobId, tenantId, pipelineId, idempotencyKey, triggerData,
            status, attemptCount, maxAttempts, enqueuedAt, leasedAt,
            leaseExpiresAt, leasedBy, completedAt, errorMessage
        );
    }

    /**
     * Acquire a lease on this job.
     */
    public void acquireLease(String workerId, Instant expiresAt) {
        this.status = ExecutionQueueJob.JobStatus.IN_PROGRESS;
        this.leasedAt = Instant.now();
        this.leaseExpiresAt = expiresAt;
        this.leasedBy = workerId;
        this.attemptCount++;
    }

    /**
     * Release the lease.
     */
    public void releaseLease() {
        this.status = ExecutionQueueJob.JobStatus.PENDING;
        this.leasedAt = null;
        this.leaseExpiresAt = null;
        this.leasedBy = null;
    }

    /**
     * Mark as completed.
     */
    public void complete(Map<String, Object> result) {
        this.status = ExecutionQueueJob.JobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.result = result;
        this.leasedAt = null;
        this.leaseExpiresAt = null;
    }

    /**
     * Mark as failed.
     */
    public void fail(String errorMessage, Instant nextRetryAt) {
        this.errorMessage = errorMessage;
        this.leasedAt = null;
        this.leaseExpiresAt = null;
        this.leasedBy = null;

        if (attemptCount < maxAttempts && nextRetryAt != null) {
            // Will retry
            this.status = ExecutionQueueJob.JobStatus.PENDING;
            this.nextRetryAt = nextRetryAt;
        } else {
            // No more retries
            this.status = ExecutionQueueJob.JobStatus.FAILED;
            this.completedAt = Instant.now();
        }
    }

    /**
     * Extend the lease.
     */
    public void extendLease(Instant newExpiresAt) {
        this.leaseExpiresAt = newExpiresAt;
    }

    /**
     * Check if lease is expired.
     */
    public boolean isLeaseExpired() {
        return leaseExpiresAt != null && Instant.now().isAfter(leaseExpiresAt);
    }

    // Getters and Setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Map<String, Object> getTriggerData() { return triggerData; }
    public void setTriggerData(Map<String, Object> triggerData) { this.triggerData = triggerData; }

    public ExecutionQueueJob.JobStatus getStatus() { return status; }
    public void setStatus(ExecutionQueueJob.JobStatus status) { this.status = status; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getEnqueuedAt() { return enqueuedAt; }
    public void setEnqueuedAt(Instant enqueuedAt) { this.enqueuedAt = enqueuedAt; }

    public Instant getLeasedAt() { return leasedAt; }
    public void setLeasedAt(Instant leasedAt) { this.leasedAt = leasedAt; }

    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }

    public String getLeasedBy() { return leasedBy; }
    public void setLeasedBy(String leasedBy) { this.leasedBy = leasedBy; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

