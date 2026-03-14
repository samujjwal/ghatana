/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.exception;

import com.ghatana.appplatform.ledger.domain.MonetaryAmount;

import java.util.Map;

/**
 * Thrown when a journal's entries do not balance (debit sum ≠ credit sum per currency).
 *
 * <p>Carries per-currency imbalance details for diagnostic reporting.
 *
 * @doc.type class
 * @doc.purpose Domain exception for unbalanced double-entry journal rejection
 * @doc.layer core
 * @doc.pattern Service
 */
public class UnbalancedJournalException extends RuntimeException {

    private final Map<String, MonetaryAmount> debitTotals;
    private final Map<String, MonetaryAmount> creditTotals;

    public UnbalancedJournalException(String reference,
                                      Map<String, MonetaryAmount> debitTotals,
                                      Map<String, MonetaryAmount> creditTotals) {
        super("Journal '" + reference + "' is unbalanced. Debits: " + debitTotals
              + " Credits: " + creditTotals);
        this.debitTotals = Map.copyOf(debitTotals);
        this.creditTotals = Map.copyOf(creditTotals);
    }

    public Map<String, MonetaryAmount> getDebitTotals() { return debitTotals; }
    public Map<String, MonetaryAmount> getCreditTotals() { return creditTotals; }
}
