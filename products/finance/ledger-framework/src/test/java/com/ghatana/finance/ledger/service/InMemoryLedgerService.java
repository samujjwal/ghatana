/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.Direction;
import com.ghatana.finance.ledger.domain.Journal;
import com.ghatana.finance.ledger.domain.JournalEntry;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link LedgerService} for unit tests.
 *
 * <p>Enforces the double-entry balance invariant: for every currency present in the
 * journal's entries, the sum of DEBIT amounts must equal the sum of CREDIT amounts.
 * Any violation causes an {@link UnbalancedJournalException} before any storage occurs.
 *
 * @doc.type class
 * @doc.purpose Test-double LedgerService enforcing real balance invariant
 * @doc.layer finance
 * @doc.pattern Test Double
 */
class InMemoryLedgerService implements LedgerService {

    private final Map<UUID, Journal> store = new ConcurrentHashMap<>();

    @Override
    public Promise<Journal> postJournal(Journal journal) {
        Objects.requireNonNull(journal, "journal");
        enforceBalance(journal);
        store.put(journal.journalId(), journal);
        return Promise.of(journal);
    }

    @Override
    public Promise<Optional<Journal>> getJournal(UUID journalId) {
        return Promise.of(Optional.ofNullable(store.get(journalId)));
    }

    @Override
    public Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId) {
        List<Journal> result = store.values().stream()
                .filter(j -> reference.equals(j.reference()))
                .toList();
        return Promise.of(result);
    }

    /**
     * Returns the number of journals currently stored (for assertions).
     */
    int size() {
        return store.size();
    }

    // ── Balance enforcement ────────────────────────────────────────────────────

    /**
     * Validates that total DEBIT == total CREDIT for every currency in the journal.
     *
     * @throws UnbalancedJournalException if any currency is not balanced
     */
    private static void enforceBalance(Journal journal) {
        Map<String, BigDecimal> netByCurrency = new LinkedHashMap<>();

        for (JournalEntry entry : journal.entries()) {
            String code = entry.amount().currencyCode();
                BigDecimal value = entry.amount().getAmount();
            if (entry.direction() == Direction.DEBIT) {
                netByCurrency.merge(code, value, BigDecimal::add);
            } else {
                netByCurrency.merge(code, value.negate(), BigDecimal::add);
            }
        }

        for (Map.Entry<String, BigDecimal> e : netByCurrency.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) != 0) {
                throw new UnbalancedJournalException(
                        "Journal " + journal.journalId()
                                + " is unbalanced for currency " + e.getKey()
                                + ": net=" + e.getValue());
            }
        }
    }
}
