/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.BalanceSnapshot;
import com.ghatana.appplatform.ledger.port.BalanceSnapshotStore;
import com.ghatana.appplatform.ledger.port.LedgerStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and retrieves point-in-time balance snapshots (K16-008).
 *
 * <p>Snapshots provide an efficient base for historical balance queries: instead
 * of summing all journal entries from the beginning of time, callers can start
 * from the most recent snapshot and add only the delta entries.
 *
 * @doc.type class
 * @doc.purpose Service for creating and querying account balance snapshots (K16-008)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class BalanceSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BalanceSnapshotService.class);

    private final LedgerStore ledgerStore;
    private final BalanceSnapshotStore snapshotStore;

    public BalanceSnapshotService(LedgerStore ledgerStore, BalanceSnapshotStore snapshotStore) {
        this.ledgerStore   = Objects.requireNonNull(ledgerStore, "ledgerStore");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    // ─── Snapshot creation ─────────────────────────────────────────────────────

    /**
     * Takes a snapshot of the current live balance for an account and persists it.
     *
     * @param accountId    account identifier
     * @param currencyCode ISO 4217 currency code
     * @param tenantId     tenant scope
     * @return the persisted snapshot
     */
    public Promise<BalanceSnapshot> takeSnapshot(UUID accountId, String currencyCode, UUID tenantId) {
        return ledgerStore.getAccountBalance(accountId, currencyCode)
            .then(balance -> {
                BalanceSnapshot snapshot = BalanceSnapshot.of(accountId, balance, tenantId);
                log.info("Taking balance snapshot: account={} currency={} balance={}",
                         accountId, currencyCode, balance.getAmount());
                return snapshotStore.save(snapshot).map(v -> snapshot);
            });
    }

    // ─── Snapshot queries ──────────────────────────────────────────────────────

    /**
     * Returns the most recent snapshot for an account.
     *
     * @param accountId    account identifier
     * @param currencyCode ISO 4217 currency code
     * @param tenantId     tenant scope
     */
    public Promise<Optional<BalanceSnapshot>> findLatestSnapshot(UUID accountId, String currencyCode,
                                                                  UUID tenantId) {
        return snapshotStore.findLatest(accountId, currencyCode, tenantId);
    }

    /**
     * Returns the snapshot history for an account (most recent first).
     *
     * @param accountId    account identifier
     * @param currencyCode ISO 4217 currency code
     * @param tenantId     tenant scope
     * @param limit        max results
     */
    public Promise<List<BalanceSnapshot>> findSnapshotHistory(UUID accountId, String currencyCode,
                                                               UUID tenantId, int limit) {
        return snapshotStore.findAll(accountId, currencyCode, tenantId, limit);
    }
}
