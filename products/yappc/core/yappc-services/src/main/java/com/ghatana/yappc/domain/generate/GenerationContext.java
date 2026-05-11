/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.domain.generate;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Canonical Generation Context for provenance and authorization.
 *
 * <p>This is the canonical contract for generation context that must be
 * explicitly provided for all generation operations. No default values
 * are allowed - all fields must be provided explicitly.
 *
 * <p>This ensures that every generation operation has complete provenance
 * and authorization context, preventing silent fallback to default
 * project/workspace IDs.
 *
 * @doc.type record
 * @doc.purpose Canonical generation context with explicit provenance
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record GenerationContext(
    @NotNull String tenantId,
    @NotNull String workspaceId,
    @NotNull String projectId,
    @NotNull String actorId,
    @NotNull String phase,
    @NotNull List<String> sourceArtifactIds,
    @NotNull List<String> canvasNodeIds,
    @NotNull String intentId,
    @NotNull String shapeId,
    @NotNull String correlationId
) {
    public GenerationContext {
        if (isBlank(tenantId)) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (isBlank(workspaceId)) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (isBlank(projectId)) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        if (isBlank(actorId)) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        if (isBlank(phase)) {
            throw new IllegalArgumentException("phase must not be blank");
        }
        if (sourceArtifactIds == null) {
            throw new IllegalArgumentException("sourceArtifactIds must not be null");
        }
        if (canvasNodeIds == null) {
            throw new IllegalArgumentException("canvasNodeIds must not be null");
        }
        if (isBlank(intentId)) {
            throw new IllegalArgumentException("intentId must not be blank");
        }
        if (isBlank(shapeId)) {
            throw new IllegalArgumentException("shapeId must not be blank");
        }
        if (isBlank(correlationId)) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }

        sourceArtifactIds = List.copyOf(sourceArtifactIds);
        canvasNodeIds = List.copyOf(canvasNodeIds);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String workspaceId;
        private String projectId;
        private String actorId;
        private String phase;
        private List<String> sourceArtifactIds = List.of();
        private List<String> canvasNodeIds = List.of();
        private String intentId;
        private String shapeId;
        private String correlationId;

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

        public Builder sourceArtifactIds(List<String> sourceArtifactIds) {
            this.sourceArtifactIds = sourceArtifactIds;
            return this;
        }

        public Builder canvasNodeIds(List<String> canvasNodeIds) {
            this.canvasNodeIds = canvasNodeIds;
            return this;
        }

        public Builder intentId(String intentId) {
            this.intentId = intentId;
            return this;
        }

        public Builder shapeId(String shapeId) {
            this.shapeId = shapeId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public GenerationContext build() {
            return new GenerationContext(
                tenantId,
                workspaceId,
                projectId,
                actorId,
                phase,
                sourceArtifactIds,
                canvasNodeIds,
                intentId,
                shapeId,
                correlationId
            );
        }
    }
}
