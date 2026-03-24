package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Filter for querying task execution history.
 *
 * @param userId    Filter by user ID
 * @param projectId Filter by project ID
 * @param taskId    Filter by task ID
 * @param status    Filter by status
 * @param startTime Filter by start time (after)
 * @param endTime   Filter by end time (before)
 * @param limit     Maximum number of results
 * @doc.type record
 * @doc.purpose Task history query filter
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskHistoryFilter(
        @Nullable String userId,
        @Nullable String projectId,
        @Nullable String taskId,
        @Nullable TaskExecutionStatus status,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        int limit
) {
    public TaskHistoryFilter {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String projectId;
        private String taskId;
        private TaskExecutionStatus status;
        private Instant startTime;
        private Instant endTime;
        private int limit = 100;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder status(TaskExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public TaskHistoryFilter build() {
            return new TaskHistoryFilter(userId, projectId, taskId, status, startTime, endTime, limit);
        }
    }
}
