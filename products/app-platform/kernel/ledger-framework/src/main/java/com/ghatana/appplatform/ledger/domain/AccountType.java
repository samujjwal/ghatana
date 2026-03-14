/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

/**
 * Account classification in the chart of accounts (K16-016).
 *
 * <p>Drives balance calculation direction:
 * <ul>
 *   <li>ASSET / EXPENSE — natural debit balance (debit increases)</li>
 *   <li>LIABILITY / EQUITY / REVENUE — natural credit balance (credit increases)</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Account classification for chart-of-accounts hierarchy
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum AccountType {
    /**
     * Asset account — resources owned. Natural debit balance.
     * Examples: Cash, Securities, Receivables.
     */
    ASSET,

    /**
     * Liability account — obligations owed. Natural credit balance.
     * Examples: Payables, Client deposits.
     */
    LIABILITY,

    /**
     * Equity account — owner's interest. Natural credit balance.
     * Examples: Share capital, Retained earnings.
     */
    EQUITY,

    /**
     * Revenue account — income earned. Natural credit balance.
     * Examples: Brokerage commission, Management fee.
     */
    REVENUE,

    /**
     * Expense account — costs incurred. Natural debit balance.
     * Examples: Transaction fees, Operating expenses.
     */
    EXPENSE;

    /**
     * True when the account's natural balance increases on DEBIT.
     * (ASSET and EXPENSE accounts.)
     */
    public boolean isNaturalDebit() {
        return this == ASSET || this == EXPENSE;
    }

    /**
     * True when the account's natural balance increases on CREDIT.
     * (LIABILITY, EQUITY, and REVENUE accounts.)
     */
    public boolean isNaturalCredit() {
        return !isNaturalDebit();
    }
}
