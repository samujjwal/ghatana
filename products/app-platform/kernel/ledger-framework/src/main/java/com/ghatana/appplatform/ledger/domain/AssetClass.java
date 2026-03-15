/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

/**
 * Asset class classification for multi-asset ledger accounts (STORY-K16-012).
 *
 * <p>Extends the ledger to track three distinct asset dimensions alongside
 * the existing monetary/currency dimension:
 * <ul>
 *   <li>{@link #CASH} — currency-denominated monetary amounts (existing ledger type).</li>
 *   <li>{@link #SECURITY} — exchange-listed securities (equities, bonds) tracked by
 *       instrument ID and share quantity.</li>
 *   <li>{@link #UNIT} — fund units (mutual funds, ETFs) tracked by instrument ID
 *       and unit quantity.</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Asset dimension classification for multi-asset account support
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum AssetClass {
    /**
     * Currency-denominated cash holding. Balance expressed as monetary amount in a
     * specific {@link Currency}.
     */
    CASH,

    /**
     * Exchange-listed security (equity or debt instrument). Balance expressed as
     * quantity of shares/units identified by an instrument ID (e.g. {@code "NABIL"}).
     */
    SECURITY,

    /**
     * Mutual fund or ETF unit holding. Balance expressed as quantity of units
     * identified by a fund instrument ID (e.g. {@code "NIBL-SAMRIDDHI"}).
     */
    UNIT;

    /**
     * True if this asset class uses quantity-based tracking (vs monetary amount).
     * SECURITY and UNIT entries are validated by quantity balance, not monetary balance.
     */
    public boolean isQuantityBased() {
        return this == SECURITY || this == UNIT;
    }
}
