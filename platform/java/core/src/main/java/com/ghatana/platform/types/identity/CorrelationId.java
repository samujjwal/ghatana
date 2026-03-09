/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.identity;

import java.util.UUID;

/**
 * Unique identifier for correlation tracking.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for correlating distributed operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record CorrelationId(String value) implements Identifier {

    public CorrelationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or blank");
        }
    }

    public static CorrelationId of(String value) {
        return new CorrelationId(value);
    }

    public static CorrelationId random() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    @Override
    public String raw() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
