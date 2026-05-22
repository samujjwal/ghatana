/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.atomic;

import java.time.Instant;

/**
 * Result of atomic workflow execution.
 *
 * @doc.type class
 * @doc.purpose Result of atomic workflow execution
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AtomicWorkflowResult {

    private final String workflowId;
    private final boolean success;
    private final boolean replayed;
    private final int retryCount;
    private final Instant completedAt;

    private AtomicWorkflowResult(Builder builder) {
        this.workflowId = builder.workflowId;
        this.success = builder.success;
        this.replayed = builder.replayed;
        this.retryCount = builder.retryCount;
        this.completedAt = builder.completedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AtomicWorkflowResult success(String workflowId) {
        return builder()
            .workflowId(workflowId)
            .success(true)
            .completedAt(Instant.now())
            .build();
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public AtomicWorkflowResult withReplayed(boolean replayed) {
        return new Builder()
            .workflowId(this.workflowId)
            .success(this.success)
            .replayed(replayed)
            .retryCount(this.retryCount)
            .completedAt(this.completedAt)
            .build();
    }

    public static class Builder {
        private String workflowId;
        private boolean success;
        private boolean replayed;
        private int retryCount;
        private Instant completedAt;

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder replayed(boolean replayed) {
            this.replayed = replayed;
            return this;
        }

        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public AtomicWorkflowResult build() {
            return new AtomicWorkflowResult(this);
        }
    }
}
