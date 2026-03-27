/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import java.time.Instant;
import java.util.List;

/**
 * Summary report produced at the end of a {@link RecertificationCampaign}.
 *
 * @param campaignId         the campaign this report covers
 * @param tenantId           owning tenant
 * @param campaignName       human-readable campaign name
 * @param scope              campaign scope
 * @param totalItems         total items reviewed
 * @param certifiedCount     items that were certified
 * @param revokedCount       items that were revoked
 * @param pendingCount       items still pending (should be 0 for completed campaigns)
 * @param certificationRate  fraction of items certified, in [0.0, 1.0]
 * @param revokedItems       list of items that were revoked (for audit trail)
 * @param generatedAt        when this report was generated
 *
 * @doc.type record
 * @doc.purpose Audit report summarising a recertification campaign outcome
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RecertificationReport(
        String campaignId,
        String tenantId,
        String campaignName,
        RecertificationScope scope,
        int totalItems,
        int certifiedCount,
        int revokedCount,
        int pendingCount,
        double certificationRate,
        List<RecertificationItem> revokedItems,
        Instant generatedAt) {
}
