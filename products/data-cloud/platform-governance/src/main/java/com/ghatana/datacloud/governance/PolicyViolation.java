/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Policy violation event captured for governance analysis.
 *
 * @doc.type class
 * @doc.purpose Represents policy violation telemetry used for recommendations and audits
 * @doc.layer product
 * @doc.pattern Record
 */
public record PolicyViolation(
        String id,
        String policyId,
        String tenantId,
        String userId,
        String action,
        String resourceId,
        String violationType,
        Instant timestamp,
        Map<String, Object> metadata) {

    public PolicyViolation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(policyId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(violationType);
        Objects.requireNonNull(timestamp);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}