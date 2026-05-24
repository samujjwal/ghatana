package com.ghatana.aep.model;

import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Represents an interval event's start and end event-time bounds
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EventInterval(Instant start, Instant end) {

    public EventInterval {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }
}
