/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolver for policy-based capabilities.
 *
 * <p>Evaluates capabilities based on policy decisions from the governance engine.
 * Policies can deny access based on data classification, compliance requirements, or business rules.
 *
 * @doc.type interface
 * @doc.purpose Resolves policy-based capabilities from governance engine
 * @doc.layer service
 * @doc.pattern Resolver
 */
public interface PolicyCapabilityResolver {

    /**
     * Resolves policy-based capabilities for an operation.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier (may be null)
     * @param projectId the project identifier (may be null)
     * @param operation the operation being performed (may be null)
     * @param phase the lifecycle phase (may be null)
     * @return a promise containing the capability model
     */
    Promise<CapabilityEvaluationService.CapabilityModel> resolve(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @Nullable String projectId,
            @Nullable String operation,
            @Nullable String phase
    );

    /**
     * Checks if the policy allows the operation.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param operation the operation being performed
     * @param phase the lifecycle phase
     * @return true if the policy allows the operation
     */
    boolean allows(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @Nullable String projectId,
            @Nullable String operation,
            @Nullable String phase
    );

    /**
     * Checks if the policy denies the operation.
     *
     * @param tenantId the tenant identifier
     * @param workspaceId the workspace identifier
     * @param projectId the project identifier
     * @param operation the operation being performed
     * @param phase the lifecycle phase
     * @return true if the policy denies the operation
     */
    boolean denies(
            @NotNull String tenantId,
            @Nullable String workspaceId,
            @Nullable String projectId,
            @Nullable String operation,
            @Nullable String phase
    );
}
