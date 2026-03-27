/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of a recertification campaign.
 *
 * @param campaignId    unique identifier
 * @param tenantId      owning tenant
 * @param campaignName  human-readable name
 * @param scope         what this campaign covers
 * @param status        current lifecycle state
 * @param totalItems    total number of items enrolled in the campaign
 * @param certifiedCount items that have been certified
 * @param revokedCount  items that have been revoked
 * @param createdAt     when the campaign was created
 * @param completedAt   when the campaign completed; {@code null} if still in progress
 *
 * @doc.type record
 * @doc.purpose Immutable value object for a recertification campaign
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RecertificationCampaign(
        String campaignId,
        String tenantId,
        String campaignName,
        RecertificationScope scope,
        CampaignStatus status,
        int totalItems,
        int certifiedCount,
        int revokedCount,
        Instant createdAt,
        Instant completedAt) {

    /** Returns the number of items still awaiting review. */
    public int pendingCount() {
        return totalItems - certifiedCount - revokedCount;
    }

    /** Returns {@code true} if all items have been reviewed. */
    public boolean isFullyReviewed() {
        return pendingCount() == 0;
    }
}
