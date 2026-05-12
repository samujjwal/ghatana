/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Common request context for all platform client calls.
 * Standardizes the context passed to platform services.
 *
 * @doc.type record
 * @doc.purpose Common request context for platform client calls
 * @doc.layer product
 * @doc.pattern DTO
 */
public record PlatformRequestContext(
    String tenantId,
    String workspaceId,
    String projectId,
    String actorId,
    @Nullable String phase,
    @Nullable String operation,
    @Nullable String dataClassification,
    Instant requestedAt,
    String correlationId,
    @Nullable String artifactId,
    @Nullable String canvasNodeId,
    @Nullable String generationRunId
) {
    public PlatformRequestContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
    }

    /**
     * Creates a new builder for PlatformRequestContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PlatformRequestContext.
     */
    public static class Builder {
        private String tenantId;
        private String workspaceId;
        private String projectId;
        private String actorId;
        private String phase;
        private String operation;
        private String dataClassification;
        private Instant requestedAt = Instant.now();
        private String correlationId = UUID.randomUUID().toString();
        private String artifactId;
        private String canvasNodeId;
        private String generationRunId;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder dataClassification(String dataClassification) {
            this.dataClassification = dataClassification;
            return this;
        }

        public Builder requestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder canvasNodeId(String canvasNodeId) {
            this.canvasNodeId = canvasNodeId;
            return this;
        }

        public Builder generationRunId(String generationRunId) {
            this.generationRunId = generationRunId;
            return this;
        }

        public PlatformRequestContext build() {
            return new PlatformRequestContext(
                tenantId,
                workspaceId,
                projectId,
                actorId,
                phase,
                operation,
                dataClassification,
                requestedAt,
                correlationId,
                artifactId,
                canvasNodeId,
                generationRunId
            );
        }
    }
}
