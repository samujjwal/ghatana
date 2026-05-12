/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.capability;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Resolver for tenant-level capabilities.
 *
 * <p>Evaluates capabilities based on tenant subscription tier (FREE, PRO, ENTERPRISE)
 * and tenant-level feature flags.
 *
 * @doc.type interface
 * @doc.purpose Resolves tenant-level capabilities based on subscription tier
 * @doc.layer service
 * @doc.pattern Resolver
 */
public interface TenantCapabilityResolver {

    /**
     * Resolves tenant-level capabilities.
     *
     * @param tenantId the tenant identifier
     * @return a promise containing the capability model
     */
    Promise<CapabilityEvaluationService.CapabilityModel> resolve(@NotNull String tenantId);

    /**
     * Checks if the tenant has read access.
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has read access
     */
    boolean canRead(@NotNull String tenantId);

    /**
     * Checks if the tenant has create access.
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has create access
     */
    boolean canCreate(@NotNull String tenantId);

    /**
     * Checks if the tenant has update access.
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has update access
     */
    boolean canUpdate(@NotNull String tenantId);

    /**
     * Checks if the tenant has delete access.
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has delete access
     */
    boolean canDelete(@NotNull String tenantId);

    /**
     * Checks if the tenant has approve access (ENTERPRISE tier).
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has approve access
     */
    boolean canApprove(@NotNull String tenantId);

    /**
     * Checks if the tenant has reject access (ENTERPRISE tier).
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has reject access
     */
    boolean canReject(@NotNull String tenantId);

    /**
     * Checks if the tenant has rollback access (ENTERPRISE tier).
     *
     * @param tenantId the tenant identifier
     * @return true if the tenant has rollback access
     */
    boolean canRollback(@NotNull String tenantId);
}
