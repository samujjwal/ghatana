/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolver for workspace-level capabilities.
 *
 * <p>Evaluates capabilities based on workspace membership and role (owner, admin, member, viewer).
 *
 * @doc.type interface
 * @doc.purpose Resolves workspace-level capabilities based on membership and role
 * @doc.layer service
 * @doc.pattern Resolver
 */
public interface WorkspaceCapabilityResolver {

    /**
     * Resolves workspace-level capabilities for an actor.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param actorId the actor identifier
     * @return a promise containing the capability model
     */
    Promise<CapabilityEvaluationService.CapabilityModel> resolve(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String actorId
    );

    /**
     * Checks if the actor has read access to the workspace.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param actorId the actor identifier
     * @return true if the actor has read access
     */
    boolean canRead(@NotNull String tenantId, @NotNull String workspaceId, @NotNull String actorId);

    /**
     * Checks if the actor has update access to the workspace.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param actorId the actor identifier
     * @return true if the actor has update access
     */
    boolean canUpdate(@NotNull String tenantId, @NotNull String workspaceId, @NotNull String actorId);
}
