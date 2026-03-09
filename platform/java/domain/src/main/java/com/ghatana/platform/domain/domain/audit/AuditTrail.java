/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.domain.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An immutable, ordered collection of {@link AuditEntry} instances that represents
 * a complete audit trail for compliance, debugging, and forensics.
 *
 * <p>Supports functional-style operations: filtering, appending (via copy),
 * and querying. Thread-safe through immutability.
 *
 * @see AuditEntry
 * @see AuditEvent
 *
 * @doc.type class
 * @doc.purpose Immutable ordered collection of audit entries forming an audit trail
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public final class AuditTrail {

    private final List<AuditEntry> entries;

    /**
     * Creates an audit trail from the given list of entries.
     *
     * @param entries the audit entries (must not be null; may be empty)
     * @throws NullPointerException if entries is null
     */
    public AuditTrail(List<AuditEntry> entries) {
        Objects.requireNonNull(entries, "Entries list cannot be null");
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns {@code true} if this audit trail contains no entries.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the number of entries in this audit trail.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns an unmodifiable view of the audit entries.
     */
    public List<AuditEntry> getEntries() {
        return entries;
    }

    /**
     * Returns the most recent (last) entry, or empty if the trail is empty.
     */
    public Optional<AuditEntry> getLatestEntry() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    /**
     * Returns a new {@code AuditTrail} with the given entry appended.
     * The original trail is not modified.
     *
     * @param entry the entry to append (must not be null)
     * @throws NullPointerException if entry is null
     */
    public AuditTrail withEntry(AuditEntry entry) {
        Objects.requireNonNull(entry, "Audit entry cannot be null");
        var newEntries = new ArrayList<>(entries);
        newEntries.add(entry);
        return new AuditTrail(newEntries);
    }

    /**
     * Returns a new {@code AuditTrail} containing only entries that match
     * the given predicate.
     *
     * @param predicate the filter predicate
     */
    public AuditTrail filter(Predicate<AuditEntry> predicate) {
        return new AuditTrail(entries.stream().filter(predicate).toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditTrail that)) return false;
        return entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "AuditTrail{entries=" + entries.size() + "}";
    }
}
