/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A single entry line in a multi-asset journal (STORY-K16-012).
 *
 * <p>Extends the double-entry model to support quantity-based assets (securities, fund units)
 * alongside currency-denominated cash entries.
 *
 * <h2>Quantity semantics</h2>
 * <ul>
 *   <li><b>CASH</b>: {@code instrumentId} is null; {@code quantity} equals the monetary amount
 *       value (e.g. 5000.00 NPR). The existing {@link BalanceEnforcer} handles monetary
 *       balance validation via {@link JournalEntry}.</li>
 *   <li><b>SECURITY / UNIT</b>: {@code instrumentId} identifies the instrument
 *       (e.g. {@code "NABIL"}, {@code "NIBL-SAMRIDDHI"}); {@code quantity} is the number
 *       of shares or units (e.g. 100).</li>
 * </ul>
 *
 * <h2>Double-entry balance rule</h2>
 * <p>For SECURITY/UNIT entries, the sum of DEBIT quantities must equal the sum of CREDIT
 * quantities per instrument within a journal. Enforced by
 * {@link com.ghatana.appplatform.ledger.service.AssetAccountService#validateJournal}.
 *
 * @doc.type record
 * @doc.purpose Multi-asset journal entry supporting cash, securities, and fund units
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record AssetEntry(
        UUID entryId,
        UUID accountId,
        Direction direction,
        AssetClass assetClass,
        String instrumentId,    // null for CASH; required for SECURITY and UNIT
        BigDecimal quantity,    // quantity of shares/units; for CASH: same as monetary value
        String description
) {
    public AssetEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("AssetEntry quantity must be positive");
        }
        if (assetClass.isQuantityBased() && (instrumentId == null || instrumentId.isBlank())) {
            throw new IllegalArgumentException(
                    assetClass + " entry requires a non-blank instrumentId");
        }
    }

    /**
     * Creates a SECURITY debit entry (receive shares).
     *
     * @param accountId    securities account
     * @param instrumentId instrument symbol (e.g. "NABIL")
     * @param quantity     number of shares received
     * @param description  entry description
     * @return SECURITY DEBIT entry
     */
    public static AssetEntry securityDebit(UUID accountId, String instrumentId,
                                           BigDecimal quantity, String description) {
        return new AssetEntry(UUID.randomUUID(), accountId, Direction.DEBIT,
                AssetClass.SECURITY, instrumentId, quantity, description);
    }

    /**
     * Creates a SECURITY credit entry (deliver shares).
     *
     * @param accountId    securities account
     * @param instrumentId instrument symbol (e.g. "NABIL")
     * @param quantity     number of shares delivered
     * @param description  entry description
     * @return SECURITY CREDIT entry
     */
    public static AssetEntry securityCredit(UUID accountId, String instrumentId,
                                            BigDecimal quantity, String description) {
        return new AssetEntry(UUID.randomUUID(), accountId, Direction.CREDIT,
                AssetClass.SECURITY, instrumentId, quantity, description);
    }

    /**
     * Creates a UNIT debit entry (receive fund units).
     */
    public static AssetEntry unitDebit(UUID accountId, String instrumentId,
                                       BigDecimal quantity, String description) {
        return new AssetEntry(UUID.randomUUID(), accountId, Direction.DEBIT,
                AssetClass.UNIT, instrumentId, quantity, description);
    }

    /**
     * Creates a UNIT credit entry (redeem fund units).
     */
    public static AssetEntry unitCredit(UUID accountId, String instrumentId,
                                        BigDecimal quantity, String description) {
        return new AssetEntry(UUID.randomUUID(), accountId, Direction.CREDIT,
                AssetClass.UNIT, instrumentId, quantity, description);
    }
}
