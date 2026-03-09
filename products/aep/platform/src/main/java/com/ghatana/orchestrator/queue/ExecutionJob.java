/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue;

/**
 * Represents a job in the execution queue.
 * 
 * <p>Encapsulates all information needed to execute a pipeline job including
 * tenant context, trigger data, and idempotency tracking.</p>
 * 
 * @doc.type class
 * @doc.purpose Immutable value object representing a queued pipeline execution job
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class ExecutionJob {
    private final String tenantId;
    private final String pipelineId;
    private final Object triggerData;
    private final String idempotencyKey;
    private final String jobId; // Unique ID for the job itself (e.g. UUID)
    private final String instanceId; // Pipeline instance ID (if applicable)

    public ExecutionJob(String tenantId, String pipelineId, Object triggerData, String idempotencyKey, String jobId, String instanceId) {
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.triggerData = triggerData;
        this.idempotencyKey = idempotencyKey;
        this.jobId = jobId;
        this.instanceId = instanceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public Object getTriggerData() {
        return triggerData;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getJobId() {
        return jobId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}

