/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Net asset position for one account — one asset type / instrument combination (K16-012).
 *
 * <p>Computed by {@link com.ghatana.appplatform.ledger.service.AssetAccountService#computeBalances}
 * by summing all DEBIT entries and subtracting all CREDIT entries for a given
 * (accountId, assetClass, instrumentId) key, consistent with the ASSET account natural balance.
 *
 * <h2>Interpreting quantity</h2>
 * <ul>
 *   <li>CASH: quantity is the monetary amount in the account's currency (e.g. 5000.00 NPR).</li>
 *   <li>SECURITY: quantity is the number of shares (e.g. 100 NABIL shares).</li>
 *   <li>UNIT: quantity is the number of fund units (e.g. 200.500 NIBL-SAMRIDDHI units).</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Net asset position per account per (assetClass, instrumentId) tuple
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record AssetBalance(
        UUID accountId,
        AssetClass assetClass,
        String instrumentId,    // null for CASH
        BigDecimal quantity
) {
    public AssetBalance {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(quantity, "quantity");
    }

    /** Creates a zero balance for the given account, asset class and instrument. */
    public static AssetBalance zero(UUID accountId, AssetClass assetClass, String instrumentId) {
        return new AssetBalance(accountId, assetClass, instrumentId, BigDecimal.ZERO);
    }

    /** Adds a quantity to this balance (returns a new record). */
    public AssetBalance add(BigDecimal delta) {
        return new AssetBalance(accountId, assetClass, instrumentId, quantity.add(delta));
    }

    /** Subtracts a quantity from this balance (returns a new record). */
    public AssetBalance subtract(BigDecimal delta) {
        return new AssetBalance(accountId, assetClass, instrumentId, quantity.subtract(delta));
    }
}
