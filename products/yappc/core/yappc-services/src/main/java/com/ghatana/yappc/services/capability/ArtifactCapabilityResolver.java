/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolver for artifact-level capabilities.
 *
 * <p>Evaluates capabilities based on artifact ownership and project permissions.
 *
 * @doc.type interface
 * @doc.purpose Resolves artifact-level capabilities based on ownership and project permissions
 * @doc.layer service
 * @doc.pattern Resolver
 */
public interface ArtifactCapabilityResolver {

    /**
     * Resolves artifact-level capabilities for an actor.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param artifactId the artifact identifier
     * @param actorId the actor identifier
     * @return a promise containing the capability model
     */
    Promise<CapabilityEvaluationService.CapabilityModel> resolve(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String actorId
    );

    /**
     * Checks if the actor has read access to the artifact.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param artifactId the artifact identifier
     * @param actorId the actor identifier
     * @return true if the actor has read access
     */
    boolean canRead(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String artifactId, @NotNull String actorId);

    /**
     * Checks if the actor has update access to the artifact.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param artifactId the artifact identifier
     * @param actorId the actor identifier
     * @return true if the actor has update access
     */
    boolean canUpdate(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String artifactId, @NotNull String actorId);

    /**
     * Checks if the actor has delete access to the artifact.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param artifactId the artifact identifier
     * @param actorId the actor identifier
     * @return true if the actor has delete access
     */
    boolean canDelete(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String artifactId, @NotNull String actorId);
}
