/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.port;

import com.ghatana.appplatform.ledger.domain.BalanceSnapshot;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for {@link BalanceSnapshot} persistence and retrieval (K16-008).
 *
 * @doc.type interface
 * @doc.purpose Port for balance snapshot read/write operations
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface BalanceSnapshotStore {

    /** Persists a new snapshot (append-only). */
    Promise<Void> save(BalanceSnapshot snapshot);

    /**
     * Retrieves the most recent snapshot for an account and currency.
     *
     * @param accountId    account identifier
     * @param currencyCode ISO 4217 code
     * @param tenantId     tenant scope
     * @return most recent snapshot, or empty if no snapshots exist
     */
    Promise<Optional<BalanceSnapshot>> findLatest(UUID accountId, String currencyCode, UUID tenantId);

    /**
     * Returns all snapshots for an account, ordered by {@code snapshot_at} DESC.
     *
     * @param accountId    account identifier
     * @param currencyCode ISO 4217 code
     * @param tenantId     tenant scope
     * @param limit        max rows to return
     */
    Promise<List<BalanceSnapshot>> findAll(UUID accountId, String currencyCode,
                                           UUID tenantId, int limit);
}
