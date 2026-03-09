/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.types.time;

import java.time.Instant;
import java.util.Objects;

/**
 * Time interval representing a range from start to end.
 *
 * @doc.type record
 * @doc.purpose Bounded time interval with start and end timestamps
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record GTimeInterval(GTimestamp start, GTimestamp end) {
    
    public GTimeInterval {
        Objects.requireNonNull(start, "Start time cannot be null");
        Objects.requireNonNull(end, "End time cannot be null");
        if (start.toInstant().isAfter(end.toInstant())) {
            throw new IllegalArgumentException("Start time cannot be after end time");
        }
    }
    
    public static GTimeInterval between(GTimestamp start, GTimestamp end) {
        return new GTimeInterval(start, end);
    }
    
    public static GTimeInterval between(Instant start, Instant end) {
        return new GTimeInterval(GTimestamp.of(start), GTimestamp.of(end));
    }

    public boolean isBefore(Instant instant) {
        return end.toInstant().isBefore(instant);
    }

    public boolean isAfter(Instant instant) {
        return start.toInstant().isAfter(instant);
    }
    
    public boolean contains(Instant instant) {
        return !isBefore(instant) && !isAfter(instant);
    }
}
