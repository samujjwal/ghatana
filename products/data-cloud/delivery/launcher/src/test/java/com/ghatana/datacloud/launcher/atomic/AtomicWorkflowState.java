/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.atomic;

/**
 * State of an atomic workflow.
 *
 * @doc.type class
 * @doc.purpose State of an atomic workflow
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AtomicWorkflowState {

    private final String workflowId;
    private final Status status;
    private final Instant updatedAt;

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }

    public AtomicWorkflowState(String workflowId, Status status) {
        this.workflowId = workflowId;
        this.status = status;
        this.updatedAt = java.time.Instant.now();
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
