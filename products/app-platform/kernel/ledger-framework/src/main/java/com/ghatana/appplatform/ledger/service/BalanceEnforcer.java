/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Direction;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.exception.UnbalancedJournalException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure domain service that enforces double-entry balance rules (K16-002).
 *
 * <p>A journal is balanced when the sum of DEBIT amounts equals the sum of CREDIT
 * amounts for every currency present in the entry list. Multi-currency journals
 * are validated per-currency independently.
 *
 * <p>This class has no I/O dependencies and is synchronous — suitable for
 * in-process validation before any DB call.
 *
 * @doc.type class
 * @doc.purpose Domain balance enforcement for double-entry journal validation
 * @doc.layer core
 * @doc.pattern Service
 */
public final class BalanceEnforcer {

    private BalanceEnforcer() {}

    /**
     * Validates that the journal's entries are balanced per currency.
     *
     * @param journal the journal to validate
     * @throws UnbalancedJournalException if any currency is unbalanced
     */
    public static void enforce(Journal journal) {
        enforce(journal.reference(), journal.entries());
    }

    /**
     * Validates a raw entry list without requiring a fully-constructed journal.
     *
     * @param reference business reference (for error message)
     * @param entries   list of journal entries to balance-check
     * @throws UnbalancedJournalException if unbalanced
     */
    public static void enforce(String reference, List<JournalEntry> entries) {
        Map<String, MonetaryAmount> debitsByCurrency = new HashMap<>();
        Map<String, MonetaryAmount> creditsByCurrency = new HashMap<>();

        for (JournalEntry entry : entries) {
            String code = entry.amount().currencyCode();
            Currency currency = entry.amount().getCurrency();

            if (entry.direction() == Direction.DEBIT) {
                debitsByCurrency.merge(code,
                        entry.amount(),
                        MonetaryAmount::add);
            } else {
                creditsByCurrency.merge(code,
                        entry.amount(),
                        MonetaryAmount::add);
            }
        }

        // Every currency with at least one debit must have equal credits and vice versa
        boolean balanced = debitsByCurrency.entrySet().stream()
                .allMatch(e -> {
                    MonetaryAmount credits = creditsByCurrency.get(e.getKey());
                    return credits != null && e.getValue().equals(credits);
                }) && creditsByCurrency.keySet().stream()
                .allMatch(debitsByCurrency::containsKey);

        if (!balanced) {
            throw new UnbalancedJournalException(reference, debitsByCurrency, creditsByCurrency);
        }
    }
}
