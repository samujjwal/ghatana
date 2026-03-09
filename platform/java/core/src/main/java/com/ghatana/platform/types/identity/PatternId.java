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
 * Unique identifier for an event pattern.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for detected patterns
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PatternId(String value) implements Identifier {

    public PatternId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pattern ID cannot be null or blank");
        }
    }

    public static PatternId of(String value) {
        return new PatternId(value);
    }

    public static PatternId random() {
        return new PatternId(UUID.randomUUID().toString());
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
