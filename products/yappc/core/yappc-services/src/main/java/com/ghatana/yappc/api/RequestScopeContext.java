/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical scope context for API requests.
 *
 * <p>This DTO defines the standard scope fields that should be passed to API endpoints.
 * The actual transport mechanism (path/query/header/body) depends on the endpoint and is
 * handled by the route authorization filter.
 *
 * <p><b>Scope Transport Conventions:</b>
 * <ul>
 *   <li><b>Path params:</b> When scope is part of the route (e.g., /api/v1/workspaces/{workspaceId})</li>
 *   <li><b>Query params:</b> For read routes where scope is optional or for filtering</li>
 *   <li><b>Headers:</b> Only for cross-cutting scope that applies to the entire request</li>
 *   <li><b>Body:</b> Only for controller-level validation after authorization</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical scope context for API requests
 * @doc.layer api
 * @doc.pattern DTO
 */
public final class RequestScopeContext {

    private final String tenantId;
    private final String workspaceId;
    private final String projectId;
    private final String artifactId;
    private final String actorId;
    private final String generationRunId;
    private final String canvasNodeId;

    private RequestScopeContext(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
        this.workspaceId = builder.workspaceId;
        this.projectId = builder.projectId;
        this.artifactId = builder.artifactId;
        this.actorId = builder.actorId;
        this.generationRunId = builder.generationRunId;
        this.canvasNodeId = builder.canvasNodeId;
    }

    /**
     * Creates a new builder for RequestScopeContext.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a RequestScopeContext with only the required tenantId.
     *
     * @param tenantId the tenant identifier (required)
     * @return a new RequestScopeContext instance
     */
    public static RequestScopeContext of(@NotNull String tenantId) {
        return builder().tenantId(tenantId).build();
    }

    /**
     * Gets the tenant identifier (multi-tenant isolation).
     *
     * @return the tenant identifier, never null
     */
    @NotNull
    public String tenantId() {
        return tenantId;
    }

    /**
     * Gets the workspace identifier (workspace-level isolation).
     *
     * @return the workspace identifier, or empty if not set
     */
    @NotNull
    public Optional<String> workspaceId() {
        return Optional.ofNullable(workspaceId);
    }

    /**
     * Gets the project identifier (project-level isolation).
     *
     * @return the project identifier, or empty if not set
     */
    @NotNull
    public Optional<String> projectId() {
        return Optional.ofNullable(projectId);
    }

    /**
     * Gets the artifact identifier (for artifact-specific operations).
     *
     * @return the artifact identifier, or empty if not set
     */
    @NotNull
    public Optional<String> artifactId() {
        return Optional.ofNullable(artifactId);
    }

    /**
     * Gets the actor identifier (who is performing the operation).
     *
     * @return the actor identifier, or empty if not set
     */
    @NotNull
    public Optional<String> actorId() {
        return Optional.ofNullable(actorId);
    }

    /**
     * Gets the generation run identifier (for generation-specific operations).
     *
     * @return the generation run identifier, or empty if not set
     */
    @NotNull
    public Optional<String> generationRunId() {
        return Optional.ofNullable(generationRunId);
    }

    /**
     * Gets the canvas node identifier (for canvas-specific operations).
     *
     * @return the canvas node identifier, or empty if not set
     */
    @NotNull
    public Optional<String> canvasNodeId() {
        return Optional.ofNullable(canvasNodeId);
    }

    /**
     * Validates that required scope fields are present.
     *
     * @param requiredFields the field names that must be present
     * @throws IllegalStateException if any required field is missing
     */
    public void validateRequired(String... requiredFields) {
        for (String field : requiredFields) {
            switch (field) {
                case "tenantId":
                    if (tenantId == null || tenantId.isEmpty()) {
                        throw new IllegalStateException("tenantId is required");
                    }
                    break;
                case "workspaceId":
                    if (workspaceId == null || workspaceId.isEmpty()) {
                        throw new IllegalStateException("workspaceId is required");
                    }
                    break;
                case "projectId":
                    if (projectId == null || projectId.isEmpty()) {
                        throw new IllegalStateException("projectId is required");
                    }
                    break;
                case "artifactId":
                    if (artifactId == null || artifactId.isEmpty()) {
                        throw new IllegalStateException("artifactId is required");
                    }
                    break;
                case "actorId":
                    if (actorId == null || actorId.isEmpty()) {
                        throw new IllegalStateException("actorId is required");
                    }
                    break;
                case "generationRunId":
                    if (generationRunId == null || generationRunId.isEmpty()) {
                        throw new IllegalStateException("generationRunId is required");
                    }
                    break;
                case "canvasNodeId":
                    if (canvasNodeId == null || canvasNodeId.isEmpty()) {
                        throw new IllegalStateException("canvasNodeId is required");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown required field: " + field);
            }
        }
    }

    /**
     * Checks if this scope context has a specific field set.
     *
     * @param field the field name to check
     * @return true if the field is set and non-empty, false otherwise
     */
    public boolean has(String field) {
        switch (field) {
            case "tenantId":
                return tenantId != null && !tenantId.isEmpty();
            case "workspaceId":
                return workspaceId != null && !workspaceId.isEmpty();
            case "projectId":
                return projectId != null && !projectId.isEmpty();
            case "artifactId":
                return artifactId != null && !artifactId.isEmpty();
            case "actorId":
                return actorId != null && !actorId.isEmpty();
            case "generationRunId":
                return generationRunId != null && !generationRunId.isEmpty();
            case "canvasNodeId":
                return canvasNodeId != null && !canvasNodeId.isEmpty();
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestScopeContext that = (RequestScopeContext) o;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(workspaceId, that.workspaceId)
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(actorId, that.actorId)
                && Objects.equals(generationRunId, that.generationRunId)
                && Objects.equals(canvasNodeId, that.canvasNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, workspaceId, projectId, artifactId, actorId, generationRunId, canvasNodeId);
    }

    @Override
    public String toString() {
        return "RequestScopeContext{"
                + "tenantId='" + tenantId + '\''
                + ", workspaceId='" + workspaceId + '\''
                + ", projectId='" + projectId + '\''
                + ", artifactId='" + artifactId + '\''
                + ", actorId='" + actorId + '\''
                + ", generationRunId='" + generationRunId + '\''
                + ", canvasNodeId='" + canvasNodeId + '\''
                + '}';
    }

    /**
     * Builder for RequestScopeContext.
     */
    public static final class Builder {
        private String tenantId;
        private String workspaceId;
        private String projectId;
        private String artifactId;
        private String actorId;
        private String generationRunId;
        private String canvasNodeId;

        private Builder() {}

        /**
         * Sets the tenant identifier (required).
         *
         * @param tenantId the tenant identifier
         * @return this builder
         */
        public Builder tenantId(@NotNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the workspace identifier.
         *
         * @param workspaceId the workspace identifier
         * @return this builder
         */
        public Builder workspaceId(@Nullable String workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        /**
         * Sets the project identifier.
         *
         * @param projectId the project identifier
         * @return this builder
         */
        public Builder projectId(@Nullable String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the artifact identifier.
         *
         * @param artifactId the artifact identifier
         * @return this builder
         */
        public Builder artifactId(@Nullable String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        /**
         * Sets the actor identifier.
         *
         * @param actorId the actor identifier
         * @return this builder
         */
        public Builder actorId(@Nullable String actorId) {
            this.actorId = actorId;
            return this;
        }

        /**
         * Sets the generation run identifier.
         *
         * @param generationRunId the generation run identifier
         * @return this builder
         */
        public Builder generationRunId(@Nullable String generationRunId) {
            this.generationRunId = generationRunId;
            return this;
        }

        /**
         * Sets the canvas node identifier.
         *
         * @param canvasNodeId the canvas node identifier
         * @return this builder
         */
        public Builder canvasNodeId(@Nullable String canvasNodeId) {
            this.canvasNodeId = canvasNodeId;
            return this;
        }

        /**
         * Builds the RequestScopeContext.
         *
         * @return a new RequestScopeContext instance
         * @throws IllegalStateException if tenantId is not set
         */
        public RequestScopeContext build() {
            return new RequestScopeContext(this);
        }
    }
}
