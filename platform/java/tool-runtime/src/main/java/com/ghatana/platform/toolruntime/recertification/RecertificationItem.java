/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of a single item within a {@link RecertificationCampaign}.
 *
 * <p>An item represents one unit of access, policy, or tool registration that a
 * certifier must either {@link ItemDecision#CERTIFIED certify} or {@link ItemDecision#REVOKED revoke}.
 *
 * @param itemId       unique identifier within the campaign
 * @param campaignId   the owning campaign
 * @param itemType     category label (e.g. "agent-permission", "tool-registration", "policy")
 * @param resourceId   identifier of the governed resource
 * @param resourceName human-readable name or description of the resource
 * @param metadata     additional context (e.g. grantScope, lastUsed, riskIndicators)
 * @param decision     current certifier decision
 * @param certifierId  reviewer who acted; {@code null} if still pending
 * @param decisionNotes optional notes from the certifier
 * @param reviewedAt   when the certifier acted; {@code null} if still pending
 *
 * @doc.type record
 * @doc.purpose Immutable value object for a recertification campaign item
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RecertificationItem(
        String itemId,
        String campaignId,
        String itemType,
        String resourceId,
        String resourceName,
        Map<String, Object> metadata,
        ItemDecision decision,
        String certifierId,
        String decisionNotes,
        Instant reviewedAt) {
}
