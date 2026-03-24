package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Task execution context containing metadata and user information.
 *
 * @param userId        The executing user ID
 * @param projectId     The project ID (if applicable)
 * @param tenantId      The tenant ID
 * @param traceId       Distributed tracing ID
 * @param metadata      Additional context metadata
 * @doc.type record
 * @doc.purpose Task execution context
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskExecutionContext(
        @NotNull String userId,
        @Nullable String projectId,
        @NotNull String tenantId,
        @NotNull String traceId,
        @NotNull Map<String, Object> metadata
) {
    public TaskExecutionContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be null or blank");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Trace ID cannot be null or blank");
        }
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a builder for TaskExecutionContext.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String projectId;
        private String tenantId;
        private String traceId = java.util.UUID.randomUUID().toString();
        private Map<String, Object> metadata = Map.of();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = Map.copyOf(metadata);
            return this;
        }

        public TaskExecutionContext build() {
            return new TaskExecutionContext(userId, projectId, tenantId, traceId, metadata);
        }
    }
}
