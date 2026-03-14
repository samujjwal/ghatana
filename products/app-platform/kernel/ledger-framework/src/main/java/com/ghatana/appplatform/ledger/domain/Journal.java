/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A double-entry accounting journal grouping balanced {@link JournalEntry} records (K16-001).
 *
 * <p>A journal is immutable once persisted. The {@link #entries()} list must contain
 * at least two entries, and the sum of DEBIT amounts must equal the sum of CREDIT
 * amounts per currency within this journal (enforced by {@code LedgerService}).
 *
 * @doc.type record
 * @doc.purpose Double-entry journal grouping balanced accounting entries
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record Journal(
        UUID journalId,
        String reference,       // business reference (order_id, trade_id, settlement_id)
        String description,
        String fiscalYear,      // BS fiscal year label e.g., '2081/82' (from K-15)
        String postedAtBs,      // dual-calendar BS date string e.g., '2081-04-15'
        Instant postedAtUtc,
        UUID tenantId,
        UUID createdBy,
        List<JournalEntry> entries
) {
    public Journal {
        Objects.requireNonNull(journalId, "journalId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(postedAtUtc, "postedAtUtc");
        Objects.requireNonNull(entries, "entries");
        if (entries.size() < 2)
            throw new IllegalArgumentException("A journal must have at least 2 entries");
        entries = List.copyOf(entries); // immutable
    }

    /**
     * Creates a journal for posting. The journal ID is auto-generated.
     *
     * @param reference   business reference for traceability
     * @param description human-readable description
     * @param tenantId    owning tenant (null for platform-level)
     * @param entries     list of debit/credit entries (must balance before posting)
     * @return unsaved journal
     */
    public static Journal of(String reference, String description, UUID tenantId,
                             List<JournalEntry> entries) {
        return new Journal(
                UUID.randomUUID(), reference, description,
                null, null, Instant.now(),
                tenantId, null, entries);
    }

    /**
     * Reconstructs a persisted journal loaded from storage.
     *
     * <p>This factory enforces the same invariants as the compact constructor —
     * the entries list must have at least 2 entries. A journal that was persisted
     * successfully always satisfies this, so header-only queries should include
     * the entries JOIN rather than passing an empty list here.
     *
     * @param journalId   persisted journal ID
     * @param reference   business reference
     * @param description human-readable description
     * @param fiscalYear  Bikram Sambat fiscal year label (e.g. "2081/82")
     * @param postedAtBs  dual-calendar BS date string
     * @param postedAtUtc UTC posting timestamp
     * @param tenantId    owning tenant
     * @param createdBy   creator identifier
     * @param entries     all persisted entries for this journal (must be >= 2)
     * @return reconstructed journal
     */
    public static Journal reconstruct(UUID journalId, String reference, String description,
                                      String fiscalYear, String postedAtBs, Instant postedAtUtc,
                                      UUID tenantId, UUID createdBy,
                                      List<JournalEntry> entries) {
        return new Journal(journalId, reference, description, fiscalYear,
                postedAtBs, postedAtUtc, tenantId, createdBy, entries);
    }

    /**
     * Returns all entries matching the given direction.
     */
    public List<JournalEntry> entriesOf(Direction direction) {
        return entries.stream()
                .filter(e -> e.direction() == direction)
                .toList();
    }

    /**
     * Total number of entries in this journal.
     */
    public int entryCount() {
        return entries.size();
    }
}
