/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * Retention policy for data governance.
 *
 * <p>Defines how long data should be retained and when it should be deleted.
 *
 * @doc.type record
 * @doc.purpose Defines retention requirements for data
 * @doc.layer product
 * @doc.pattern Policy
 */
public record RetentionPolicy(
    String policyId,
    Duration retentionPeriod,
    boolean applyToAuditLogs,
    boolean applyToEntities,
    boolean applyToEvents,
    RetentionAction retentionAction) {

    public enum RetentionAction {
        DELETE,
        ARCHIVE,
        ANONYMIZE,
        REDACT
    }

    public RetentionPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (retentionPeriod == null) {
            throw new IllegalArgumentException("retentionPeriod must not be null");
        }
        if (retentionPeriod.isNegative() || retentionPeriod.isZero()) {
            throw new IllegalArgumentException("retentionPeriod must be positive");
        }
    }

    public boolean shouldRetain(Instant creationTime) {
        Instant expiryTime = creationTime.plus(retentionPeriod);
        return Instant.now().isBefore(expiryTime);
    }

    public boolean appliesTo(String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "auditlog" -> applyToAuditLogs;
            case "entity" -> applyToEntities;
            case "event" -> applyToEvents;
            default -> false;
        };
    }
}
