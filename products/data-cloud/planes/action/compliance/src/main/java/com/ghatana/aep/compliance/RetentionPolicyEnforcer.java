/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import io.activej.promise.Promise;
import java.time.Duration;

/**
 * Enforces retention policies for data assets.
 *
 * <p>Each data asset can have a maximum retention period. After that period expires,
 * access is blocked and deletion should be scheduled. Callers check retention status
 * before accessing any data record.
 *
 * @doc.type interface
 * @doc.purpose Enforce data retention policies and block access to expired data
 * @doc.layer product
 * @doc.pattern Service
 */
public interface RetentionPolicyEnforcer {

    /**
     * Register a retention period for a given data asset.
     *
     * @param tenantId        owning tenant
     * @param dataId          logical data asset identifier
     * @param retentionPeriod how long from now the data asset is allowed to be accessed
     * @return completed promise on success
     */
    Promise<Void> registerRetention(String tenantId, String dataId, Duration retentionPeriod);

    /**
     * Assert that the data asset is within its retention window.
     *
     * @param tenantId owning tenant
     * @param dataId   data asset identifier
     * @return completed promise if within retention; failed with {@link RetentionExpiredException} otherwise
     */
    Promise<Void> checkRetention(String tenantId, String dataId);

    /**
     * Schedule deletion of a data asset (marks it as logically deleted).
     *
     * @param tenantId owning tenant
     * @param dataId   data asset to schedule for deletion
     * @return completed promise on success
     */
    Promise<Void> scheduleDeletion(String tenantId, String dataId);
}
