/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.AssetBalance;
import com.ghatana.appplatform.ledger.domain.AssetClass;
import com.ghatana.appplatform.ledger.domain.AssetEntry;
import com.ghatana.appplatform.ledger.domain.Direction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for multi-asset account posting and balance queries (STORY-K16-012).
 *
 * <p>Extends the ledger model to support quantity-based asset classes:
 * {@link AssetClass#SECURITY} (exchange-listed equities and bonds) and
 * {@link AssetClass#UNIT} (mutual fund / ETF units), alongside the existing
 * {@link AssetClass#CASH} monetary entries.
 *
 * <h2>Validation</h2>
 * <p>For quantity-based assets (SECURITY, UNIT), each journal must balance per instrument:
 * the sum of DEBIT quantities must equal the sum of CREDIT quantities for every
 * (assetClass, instrumentId) pair in the journal. CASH entries are validated separately
 * by the existing {@link BalanceEnforcer} on the monetary side.
 *
 * <h2>Balance computation</h2>
 * <p>Net quantity for an account is: DEBIT total − CREDIT total per
 * (accountId, assetClass, instrumentId). Consistent with natural ASSET account direction
 * (debits increase the balance, credits decrease it).
 *
 * <h2>Mixed journals</h2>
 * <p>A single asset journal may contain both CASH legs (for the monetary movement) and
 * SECURITY/UNIT legs (for the instrument quantity movement), enabling trade-settlement
 * double-entry across asset dimensions.
 *
 * @doc.type class
 * @doc.purpose Multi-asset account ledger posting and balance computation service (K16-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AssetAccountService {

    /**
     * Validates that quantity-based legs of the given journal balance per instrument.
     *
     * <p>For each (assetClass, instrumentId) pair among the SECURITY and UNIT entries:
     * {@code sum(DEBIT quantities) == sum(CREDIT quantities)}.
     *
     * <p>CASH entries are excluded — monetary balance is enforced by {@link BalanceEnforcer}.
     *
     * @param entries journal entries to validate; not null
     * @throws IllegalArgumentException if any quantity-based instrument is unbalanced
     */
    public void validateJournal(List<AssetEntry> entries) {
        Objects.requireNonNull(entries, "entries");

        // Group quantity-based entries by (assetClass, instrumentId)
        Map<String, BigDecimal> debitTotals  = new LinkedHashMap<>();
        Map<String, BigDecimal> creditTotals = new LinkedHashMap<>();

        for (AssetEntry entry : entries) {
            if (!entry.assetClass().isQuantityBased()) continue;

            String key = entry.assetClass() + ":" + entry.instrumentId();
            if (entry.direction() == Direction.DEBIT) {
                debitTotals.merge(key, entry.quantity(), BigDecimal::add);
            } else {
                creditTotals.merge(key, entry.quantity(), BigDecimal::add);
            }
        }

        // Every key that appears on either side must balance
        for (String key : debitTotals.keySet()) {
            BigDecimal debitTotal  = debitTotals.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal creditTotal = creditTotals.getOrDefault(key, BigDecimal.ZERO);
            if (debitTotal.compareTo(creditTotal) != 0) {
                throw new IllegalArgumentException(
                        "Asset journal imbalanced for instrument key [" + key +
                        "]: debits=" + debitTotal + " credits=" + creditTotal);
            }
        }
        for (String key : creditTotals.keySet()) {
            if (!debitTotals.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Asset journal imbalanced for instrument key [" + key +
                        "]: no debit entries found");
            }
        }
    }

    /**
     * Computes net asset balances for a specific account from a list of asset entries.
     *
     * <p>Net quantity per (assetClass, instrumentId) = sum(DEBIT quantities) − sum(CREDIT quantities).
     * Only entries matching {@code accountId} are considered.
     *
     * @param accountId target account
     * @param entries   all asset entries to compute from (may include other accounts)
     * @return list of net balances per (assetClass, instrumentId); empty if no entries
     */
    public List<AssetBalance> computeBalances(UUID accountId, List<AssetEntry> entries) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(entries, "entries");

        // Key: assetClass + ":" + instrumentId (null becomes "null" for CASH)
        Map<String, AssetBalance> balanceMap = new LinkedHashMap<>();

        for (AssetEntry entry : entries) {
            if (!accountId.equals(entry.accountId())) continue;

            String key = entry.assetClass() + ":" + entry.instrumentId();
            balanceMap.putIfAbsent(key,
                AssetBalance.zero(accountId, entry.assetClass(), entry.instrumentId()));

            if (entry.direction() == Direction.DEBIT) {
                balanceMap.computeIfPresent(key, (k, b) -> b.add(entry.quantity()));
            } else {
                balanceMap.computeIfPresent(key, (k, b) -> b.subtract(entry.quantity()));
            }
        }

        return new ArrayList<>(balanceMap.values());
    }

    /**
     * Validates and "posts" an asset journal by returning the resulting balances
     * per account from the provided entries.
     *
     * <p>Validates quantity balance (see {@link #validateJournal}) before computing.
     * In production this method would additionally delegate to a persistent store;
     * the balance computation is always deterministic from the entry set.
     *
     * @param entries complete set of asset journal entries for one journal
     * @return per-account balances derived from the journal entries
     * @throws IllegalArgumentException if any quantity-based instrument is unbalanced
     */
    public Map<UUID, List<AssetBalance>> postAssetJournal(List<AssetEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        validateJournal(entries);

        // Group by accountId and compute per-account balances
        Map<UUID, List<AssetEntry>> byAccount = entries.stream()
                .collect(Collectors.groupingBy(AssetEntry::accountId));

        Map<UUID, List<AssetBalance>> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<AssetEntry>> acctEntries : byAccount.entrySet()) {
            result.put(acctEntries.getKey(),
                    computeBalances(acctEntries.getKey(), acctEntries.getValue()));
        }
        return result;
    }
}
