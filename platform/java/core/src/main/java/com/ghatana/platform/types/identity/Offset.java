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
 * Unique identifier for an offset in a stream.
 *
 * @doc.type record
 * @doc.purpose Typed offset for event stream position tracking
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record Offset(String value) implements Identifier {

    public Offset {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Offset cannot be null or blank");
        }
    }

    public static Offset of(String value) {
        return new Offset(value);
    }

    public static Offset of(long value) {
        return new Offset(String.valueOf(value));
    }

    public static Offset zero() {
        return new Offset("0");
    }

    public static Offset random() {
        return new Offset(UUID.randomUUID().toString());
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
