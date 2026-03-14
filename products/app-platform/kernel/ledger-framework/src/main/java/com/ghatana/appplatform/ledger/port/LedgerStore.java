/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.port;

import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for append-only ledger journal operations (K16-001, K16-002).
 *
 * <p>Implementations MUST enforce append-only semantics —
 * no UPDATE or DELETE on persisted records.
 *
 * @doc.type interface
 * @doc.purpose Append-only storage port for double-entry ledger journals
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface LedgerStore {

    /**
     * Persists a balanced journal and its entries atomically.
     *
     * <p>The implementation assigns entry hash values and sequence numbers
     * (K16-006 hash chain) before persisting.
     *
     * @param journal balanced journal to post (entries must be balanced)
     * @return the persisted journal with DB-generated metadata
     */
    Promise<Journal> postJournal(Journal journal);

    /**
     * Retrieves a journal by its ID.
     *
     * @param journalId journal identifier
     * @return Optional containing the journal with all entries
     */
    Promise<Optional<Journal>> getJournal(UUID journalId);

    /**
     * Retrieves all journals for a given business reference.
     *
     * @param reference business reference (e.g., order_id, trade_id)
     * @param tenantId  tenant scoping
     * @return journals ordered by posted_at_utc
     */
    Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId);

    /**
     * Retrieves the current materialized balance for an account.
     *
     * @param accountId account identifier
     * @param currency  ISO 4217 currency code
     * @return current balance (always reflects all posted entries)
     */
    Promise<MonetaryAmount> getAccountBalance(UUID accountId, String currency);

    /**
     * Returns the last N journals for a tenant, ordered by posted_at_utc DESC.
     *
     * @param tenantId tenant scoping
     * @param limit    maximum number of journals to return
     * @return recent journals, most recent first
     */
    Promise<List<Journal>> getRecentJournals(UUID tenantId, int limit);

    /**
     * Returns the balance for an account as-of a point in time (K16-007).
     *
     * <p>Only journals posted at or before {@code asOf} are included in the calculation.
     * This is useful for historical balance reporting and audit snapshots.
     *
     * @param accountId account identifier
     * @param currency  ISO 4217 currency code
     * @param asOf      inclusive cut-off timestamp
     * @return balance as of the given timestamp (may be zero if no entries exist)
     */
    Promise<MonetaryAmount> getAccountBalanceAsOf(UUID accountId, String currency, Instant asOf);
}
