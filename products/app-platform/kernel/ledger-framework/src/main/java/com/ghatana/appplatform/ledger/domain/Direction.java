/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

/**
 * Double-entry accounting direction for journal entries.
 *
 * <p>Standard accounting rules:
 * <ul>
 *   <li>ASSET / EXPENSE accounts: DEBIT increases, CREDIT decreases</li>
 *   <li>LIABILITY / EQUITY / REVENUE accounts: CREDIT increases, DEBIT decreases</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Debit/Credit direction for double-entry accounting entries
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum Direction {
    /**
     * Debit — increases ASSET and EXPENSE accounts; decreases LIABILITY, EQUITY, REVENUE.
     */
    DEBIT,

    /**
     * Credit — increases LIABILITY, EQUITY, REVENUE accounts; decreases ASSET and EXPENSE.
     */
    CREDIT;

    /**
     * Returns the opposite direction.
     */
    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
