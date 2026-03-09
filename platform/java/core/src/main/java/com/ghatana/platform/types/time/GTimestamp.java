/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.time;

import java.time.Instant;

/**
 * Timestamp wrapper for platform events.
 *
 * @doc.type record
 * @doc.purpose Platform-standard timestamp with nanosecond precision
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record GTimestamp(Instant value) {
    
    public GTimestamp {
        if (value == null) {
            throw new IllegalArgumentException("Timestamp value cannot be null");
        }
    }
    
    public static GTimestamp now() {
        return new GTimestamp(Instant.now());
    }
    
    public static GTimestamp of(Instant instant) {
        return new GTimestamp(instant);
    }
    
    public static GTimestamp ofEpochMilli(long epochMilli) {
        return new GTimestamp(Instant.ofEpochMilli(epochMilli));
    }
    
    public Instant toInstant() {
        return value;
    }
}
