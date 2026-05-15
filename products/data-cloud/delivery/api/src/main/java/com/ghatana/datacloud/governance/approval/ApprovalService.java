/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.approval;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * No-op implementation of ApprovalService for production use.
 *
 * <p>This implementation performs no governance checks and always grants access.
 * A full implementation would validate tenant access rights and enforce approval workflows.
 *
 * @doc.type class
 * @doc.purpose No-op approval service for production
 * @doc.layer data-cloud
 * @doc.pattern Null Object
 */
public final class ApprovalService {

    private static final ApprovalService INSTANCE = new ApprovalService();

    private ApprovalService() {
        // Private constructor for singleton
    }

    /**
     * Returns the singleton instance.
     *
     * @return the approval service instance
     */
    @NotNull
    public static ApprovalService getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if a tenant has access to a given resource.
     *
     * <p>This no-op implementation always returns true.
     *
     * @param tenantId the tenant ID
     * @param resource the resource to check access for
     * @return promise of true (no-op implementation)
     */
    @NotNull
    public Promise<Boolean> checkAccess(@NotNull String tenantId, @NotNull String resource) {
        return Promise.of(Boolean.TRUE);
    }

    /**
     * Requests approval for a resource.
     *
     * <p>This no-op implementation always grants approval immediately.
     *
     * @param tenantId the tenant ID
     * @param resource the resource to approve
     * @return promise of true (no-op implementation)
     */
    @NotNull
    public Promise<Boolean> requestApproval(@NotNull String tenantId, @NotNull String resource) {
        return Promise.of(Boolean.TRUE);
    }
}
