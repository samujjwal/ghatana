package com.ghatana.digitalmarketing.contracts;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed wrapper for a DMOS correlation identifier.
 *
 * <p>Every DMOS operation that crosses a service or module boundary must carry a
 * {@code DmCorrelationId}. This enables distributed tracing, audit correlation,
 * and replay debugging. The correlation ID is propagated from inbound API requests
 * through commands, events, workflows, and bridge adapter calls.</p>
 *
 * <p>Use {@link #generate()} to create a new ID at the system entry point.
 * Propagate the same ID through all downstream calls.</p>
 *
 * @doc.type class
 * @doc.purpose Typed correlation identifier for distributed tracing across DMOS
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmCorrelationId {

    private final String value;

    private DmCorrelationId(String value) {
        this.value = value;
    }

    /**
     * Creates a new random correlation ID using a UUID.
     */
    public static DmCorrelationId generate() {
        return new DmCorrelationId(UUID.randomUUID().toString());
    }

    /**
     * Creates a correlation ID from an existing string (e.g. from an inbound HTTP header).
     *
     * @param value existing correlation ID string; must not be blank
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public static DmCorrelationId of(String value) {
        Objects.requireNonNull(value, "correlationId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        return new DmCorrelationId(value);
    }

    /** Returns the raw string value. Never {@code null} or blank. */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return value.equals(((DmCorrelationId) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "DmCorrelationId{" + value + '}';
    }
}
