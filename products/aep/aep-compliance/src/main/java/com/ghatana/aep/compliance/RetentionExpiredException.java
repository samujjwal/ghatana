/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

/**
 * Thrown when a data asset is accessed after its retention period has expired.
 *
 * @doc.type class
 * @doc.purpose Signal that a data asset's retention window has elapsed
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class RetentionExpiredException extends RuntimeException {

    private final String tenantId;
    private final String dataId;

    /**
     * @param tenantId owning tenant
     * @param dataId   the expired data asset
     */
    public RetentionExpiredException(String tenantId, String dataId) {
        super("Data asset '%s' in tenant '%s' has exceeded its retention period."
            .formatted(dataId, tenantId));
        this.tenantId = tenantId;
        this.dataId = dataId;
    }

    /** @return the tenant owning the expired data */
    public String tenantId() { return tenantId; }

    /** @return the data asset identifier */
    public String dataId() { return dataId; }
}
