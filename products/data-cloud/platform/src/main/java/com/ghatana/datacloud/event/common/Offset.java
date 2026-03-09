package com.ghatana.datacloud.event.common;

import java.util.Objects;

/**
 * Immutable offset value within a partition.
 *
 * <p><b>Purpose</b><br>
 * Represents the position of an event within a partition. Offsets are:
 * - Monotonically increasing within a partition
 * - NOT globally unique (partition-scoped)
 * - Zero-based (first event has offset 0)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Offset offset = Offset.of(42L);
 * Offset next = offset.next();
 * boolean isFirst = offset.isFirst();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable offset value within partition
 * @doc.layer common
 * @doc.pattern Value Object
 */
public record Offset(long value) implements Comparable<Offset> {

    /**
     * First offset in any partition.
     */
    public static final Offset FIRST = new Offset(0L);

    /**
     * Latest offset marker (for tailing).
     */
    public static final Offset LATEST = new Offset(-1L);

    /**
     * Earliest offset marker (for reading from beginning).
     */
    public static final Offset EARLIEST = new Offset(-2L);

    public Offset {
        if (value < -2) {
            throw new IllegalArgumentException("Offset value must be >= -2, got: " + value);
        }
    }

    /**
     * Create offset from value.
     *
     * @param value offset value
     * @return Offset instance
     */
    public static Offset of(long value) {
        if (value == 0L) return FIRST;
        if (value == -1L) return LATEST;
        if (value == -2L) return EARLIEST;
        return new Offset(value);
    }

    /**
     * Get next offset (increment by 1).
     *
     * @return next offset
     */
    public Offset next() {
        if (this == LATEST || this == EARLIEST) {
            throw new IllegalStateException("Cannot increment special offset: " + this);
        }
        return new Offset(value + 1);
    }

    /**
     * Check if this is the first offset.
     *
     * @return true if offset is 0
     */
    public boolean isFirst() {
        return value == 0L;
    }

    /**
     * Check if this is a special marker offset.
     *
     * @return true if LATEST or EARLIEST
     */
    public boolean isSpecial() {
        return value < 0;
    }

    /**
     * Check if this offset is before another.
     *
     * @param other offset to compare
     * @return true if this &lt; other
     */
    public boolean isBefore(Offset other) {
        Objects.requireNonNull(other, "other offset required");
        if (this.isSpecial() || other.isSpecial()) {
            throw new IllegalStateException("Cannot compare special offsets");
        }
        return this.value < other.value;
    }

    /**
     * Check if this offset is after another.
     *
     * @param other offset to compare
     * @return true if this &gt; other
     */
    public boolean isAfter(Offset other) {
        Objects.requireNonNull(other, "other offset required");
        if (this.isSpecial() || other.isSpecial()) {
            throw new IllegalStateException("Cannot compare special offsets");
        }
        return this.value > other.value;
    }

    @Override
    public int compareTo(Offset other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        if (this == LATEST) return "Offset[LATEST]";
        if (this == EARLIEST) return "Offset[EARLIEST]";
        return "Offset[" + value + "]";
    }
}
