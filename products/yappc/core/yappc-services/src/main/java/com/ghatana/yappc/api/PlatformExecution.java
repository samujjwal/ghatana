/**
 * Platform Execution DTOs
 * 
 * Canonical schema for Data Cloud+AEP platform execution.
 * Defines the structure for platform execution requests and responses.
 * 
 * @doc.type class
 * @doc.purpose Platform execution schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical platform execution schema.
 */
public final class PlatformExecution {

    private final String executionId;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final ExecutionRequest request;
    private final ExecutionResponse response;
    private final ExecutionMetadata metadata;
    private final ExecutionStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PlatformExecution(
            @NotNull String executionId,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull ExecutionRequest request,
            ExecutionResponse response,
            @NotNull ExecutionMetadata metadata,
            @NotNull ExecutionStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt
    ) {
        this.executionId = executionId;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.request = request;
        this.response = response;
        this.metadata = metadata;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String executionId() {
        return executionId;
    }

    public String projectId() {
        return projectId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String tenantId() {
        return tenantId;
    }

    public ExecutionRequest request() {
        return request;
    }

    public ExecutionResponse response() {
        return response;
    }

    public ExecutionMetadata metadata() {
        return metadata;
    }

    public ExecutionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Execution request.
     */
    public record ExecutionRequest(
            String executionType,
            String modelId,
            String modelVersion,
            Map<String, Object> parameters,
            Map<String, String> headers,
            String correlationId
    ) {}

    /**
     * Execution response.
     */
    public record ExecutionResponse(
            String responseId,
            String executionType,
            Object result,
            ExecutionMetrics metrics,
            List<String> warnings,
            String error
    ) {}

    /**
     * Execution metrics.
     */
    public record ExecutionMetrics(
            long durationMs,
            long tokensUsed,
            double cost,
            Map<String, String> additionalMetrics
    ) {}

    /**
     * Execution metadata.
     */
    public record ExecutionMetadata(
            String traceId,
            String sessionId,
            String userId,
            String platformType,
            Set<String> tags,
            Map<String, String> customMetadata
    ) {}

    /**
     * Execution status.
     */
    public record ExecutionStatus(
            ExecutionState state,
            String message,
            int progress,
            Instant completedAt
    ) {
        public enum ExecutionState {
            PENDING,
            RUNNING,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }
}
