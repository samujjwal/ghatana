/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A single entry line within a {@link Journal} (K16-001).
 *
 * <p>Each journal must have at least two entries, and the sum of DEBIT amounts
 * must equal the sum of CREDIT amounts per currency (enforced by {@code LedgerService}).
 *
 * @doc.type record
 * @doc.purpose Single debit or credit line within a double-entry journal
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record JournalEntry(
        UUID entryId,
        UUID journalId,    // set when persisted; null for pre-persist records
        UUID accountId,
        Direction direction,
        MonetaryAmount amount,
        String description,
        String entryHash,  // SHA-256 hash chain link per account (K16-006); null before persist
        Long sequenceNum   // per-account monotonic sequence (K16-006); null before persist
) {
    public JournalEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) throw new IllegalArgumentException("Entry amount must be positive");
    }

    /**
     * Creates a new entry for a journal before it is persisted (no entryHash / sequenceNum yet).
     */
    public static JournalEntry of(UUID accountId, Direction direction, MonetaryAmount amount,
                                  String description) {
        return new JournalEntry(UUID.randomUUID(), null, accountId, direction, amount,
                description, null, null);
    }

    /**
     * Creates a debit entry for the given account and amount.
     */
    public static JournalEntry debit(UUID accountId, MonetaryAmount amount, String description) {
        return of(accountId, Direction.DEBIT, amount, description);
    }

    /**
     * Creates a credit entry for the given account and amount.
     */
    public static JournalEntry credit(UUID accountId, MonetaryAmount amount, String description) {
        return of(accountId, Direction.CREDIT, amount, description);
    }
}
