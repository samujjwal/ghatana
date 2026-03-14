/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Vector clock implementation for causal ordering across distributed services (K17-004).
 *
 * <p>A version vector (Mattern 1989) assigns per-node monotonically increasing counters to
 * allow the comparison of event causality across a distributed system:
 *
 * <ul>
 *   <li>{@code A.BEFORE(B)} — A causally precedes B</li>
 *   <li>{@code A.AFTER(B)}  — B causally precedes A</li>
 *   <li>{@code A.CONCURRENT(B)} — A and B are concurrent (neither precedes the other)</li>
 * </ul>
 *
 * <p>Stored as a JSON object {@code {nodeId: counter}} suitable for JSONB columns.
 *
 * <p>This class is immutable — every mutating operation returns a new instance.
 *
 * @doc.type class
 * @doc.purpose Immutable version vector (vector clock) for causal ordering (K17-004)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class VersionVector {

    private final Map<String, Long> counters;

    private VersionVector(Map<String, Long> counters) {
        this.counters = Collections.unmodifiableMap(new HashMap<>(counters));
    }

    /** Creates an empty version vector (all counters implicitly zero). */
    public static VersionVector empty() {
        return new VersionVector(Map.of());
    }

    /** Creates a version vector from a pre-existing counter map (defensive copy). */
    public static VersionVector of(Map<String, Long> counters) {
        Objects.requireNonNull(counters, "counters");
        return new VersionVector(counters);
    }

    // ─── Mutators (return new instances) ─────────────────────────────────────

    /**
     * Returns a new vector with the counter for {@code nodeId} incremented by 1.
     *
     * @param nodeId logical node/service identifier
     * @return new version vector with updated counter
     */
    public VersionVector increment(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        Map<String, Long> next = new HashMap<>(counters);
        next.merge(nodeId, 1L, Long::sum);
        return new VersionVector(next);
    }

    /**
     * Returns the point-wise maximum of {@code this} and {@code other}.
     *
     * <p>This is the standard vector-clock merge used when receiving a remote event.
     *
     * @param other remote version vector
     * @return merged vector
     */
    public VersionVector merge(VersionVector other) {
        Objects.requireNonNull(other, "other");
        Map<String, Long> merged = new HashMap<>(counters);
        other.counters.forEach((node, cnt) ->
            merged.merge(node, cnt, Math::max));
        return new VersionVector(merged);
    }

    // ─── Comparison ───────────────────────────────────────────────────────────

    /**
     * Compares causal ordering of {@code this} with {@code other}.
     *
     * @param other vector to compare against
     * @return {@link Ordering#BEFORE} if {@code this} causally precedes {@code other},
     *         {@link Ordering#AFTER} if {@code other} causally precedes {@code this},
     *         {@link Ordering#CONCURRENT} if neither precedes the other
     */
    public Ordering compare(VersionVector other) {
        Objects.requireNonNull(other, "other");
        boolean thisLessOrEq = leq(this, other);
        boolean otherLessOrEq = leq(other, this);

        if (thisLessOrEq && !otherLessOrEq) return Ordering.BEFORE;
        if (otherLessOrEq && !thisLessOrEq) return Ordering.AFTER;
        if (thisLessOrEq)                   return Ordering.BEFORE;   // equal — treat as BEFORE
        return Ordering.CONCURRENT;
    }

    /** True if {@code a} is component-wise ≤ {@code b}. */
    private static boolean leq(VersionVector a, VersionVector b) {
        // Every node in A must have a counter ≤ the corresponding counter in B
        for (Map.Entry<String, Long> entry : a.counters.entrySet()) {
            long bVal = b.counters.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() > bVal) return false;
        }
        return true;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Returns the current counter for the given node (0 if absent). */
    public long get(String nodeId) {
        return counters.getOrDefault(Objects.requireNonNull(nodeId), 0L);
    }

    /** Returns an unmodifiable view of the underlying counter map (for serialisation). */
    public Map<String, Long> asMap() {
        return counters;
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof VersionVector other && counters.equals(other.counters);
    }

    @Override
    public int hashCode() {
        return counters.hashCode();
    }

    @Override
    public String toString() {
        return "VersionVector" + counters;
    }

    // ─── Ordering enum ────────────────────────────────────────────────────────

    /**
     * Causal ordering result between two version vectors.
     *
     * @doc.type enum
     * @doc.purpose Causal ordering outcome (K17-004)
     * @doc.layer core
     * @doc.pattern ValueObject
     */
    public enum Ordering {
        /** This vector causally precedes the other. */
        BEFORE,
        /** The other vector causally precedes this one. */
        AFTER,
        /** Neither vector precedes the other — concurrent events. */
        CONCURRENT
    }
}
