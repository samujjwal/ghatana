/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolver for project-level capabilities.
 *
 * <p>Evaluates capabilities based on project role (owner, admin, editor, viewer)
 * and project-specific permissions.
 *
 * @doc.type interface
 * @doc.purpose Resolves project-level capabilities based on role and permissions
 * @doc.layer service
 * @doc.pattern Resolver
 */
public interface ProjectCapabilityResolver {

    /**
     * Resolves project-level capabilities for an actor.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier (may be null for tenant-level projects)
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return a promise containing the capability model
     */
    Promise<CapabilityEvaluationService.CapabilityModel> resolve(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @NotNull String projectId,
            @NotNull String actorId
    );

    /**
     * Checks if the actor has read access to the project.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has read access
     */
    boolean canRead(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);

    /**
     * Checks if the actor has update access to the project.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has update access
     */
    boolean canUpdate(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);

    /**
     * Checks if the actor has delete access to the project.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has delete access
     */
    boolean canDelete(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);

    /**
     * Checks if the actor has approve access to the project (owner/admin only).
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has approve access
     */
    boolean canApprove(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);

    /**
     * Checks if the actor has reject access to the project (owner/admin only).
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has reject access
     */
    boolean canReject(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);

    /**
     * Checks if the actor has rollback access to the project (owner/admin only).
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param actorId the actor identifier
     * @return true if the actor has rollback access
     */
    boolean canRollback(@NotNull String tenantId, @Nullable String workspaceId, @NotNull String projectId, @NotNull String actorId);
}
