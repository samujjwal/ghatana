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
 * Unique identifier for an event type.
 *
 * @doc.type record
 * @doc.purpose Typed identifier for event type schemas
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record EventTypeId(String value) implements Identifier {

    public EventTypeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Event Type ID cannot be null or blank");
        }
    }

    public static EventTypeId of(String value) {
        return new EventTypeId(value);
    }

    public static EventTypeId random() {
        return new EventTypeId(UUID.randomUUID().toString());
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
