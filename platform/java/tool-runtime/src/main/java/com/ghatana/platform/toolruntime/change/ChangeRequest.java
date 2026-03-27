/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of a change request at a point in time.
 *
 * <p>A {@code ChangeRequest} describes a proposed mutation to tenant configuration,
 * tooling, policy, or permissions. It carries a computed {@link #riskScore()} (0–100)
 * that drives automated routing: high-risk changes require human review, low-risk ones
 * may be auto-approved.
 *
 * @param changeId       unique identifier for this change
 * @param tenantId       the owning tenant
 * @param requestingAgent the agent or service that submitted the change
 * @param changeType     classification of the change
 * @param description    human-readable summary of what is changing
 * @param metadata       additional context key-value pairs (e.g. before/after values)
 * @param status         current lifecycle state
 * @param riskScore      computed risk score in the range [0, 100]
 * @param reviewerId     reviewer who approved/rejected; {@code null} if not yet reviewed
 * @param reviewNotes    optional reviewer comment; {@code null} if not yet reviewed
 * @param submittedAt    when the change was submitted
 * @param reviewedAt     when the reviewer acted; {@code null} if not yet reviewed
 *
 * @doc.type record
 * @doc.purpose Immutable value object representing a change request
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ChangeRequest(
        String changeId,
        String tenantId,
        String requestingAgent,
        ChangeType changeType,
        String description,
        Map<String, Object> metadata,
        ChangeStatus status,
        int riskScore,
        String reviewerId,
        String reviewNotes,
        Instant submittedAt,
        Instant reviewedAt) {
}
