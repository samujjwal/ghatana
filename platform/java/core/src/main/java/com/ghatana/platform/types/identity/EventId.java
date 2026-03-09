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
 * Unique identifier for an event instance.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for event instances
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record EventId(String value) implements Identifier {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Event ID cannot be null or blank");
        }
    }

    public static EventId of(String value) {
        return new EventId(value);
    }

    public static EventId random() {
        return new EventId(UUID.randomUUID().toString());
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
