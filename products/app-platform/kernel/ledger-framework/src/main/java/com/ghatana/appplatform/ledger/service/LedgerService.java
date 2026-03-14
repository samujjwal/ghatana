/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.port.LedgerStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ledger application service — the primary entry point for posting journals (K16-002).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Balance enforcement: delegate to {@link BalanceEnforcer} (fail fast before any DB call)</li>
 *   <li>Delegation: call {@link LedgerStore#postJournal(Journal)} for persistence</li>
 *   <li>Query delegation: expose balance and journal queries</li>
 * </ol>
 *
 * <p>This service intentionally does NOT emit events directly. The adapter
 * (PostgresLedgerStore) writes to the outbox table atomically; the relay
 * service (K17) publishes from there.
 *
 * @doc.type class
 * @doc.purpose Application service for double-entry journal posting and queries
 * @doc.layer product
 * @doc.pattern Service
 */
public final class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerStore store;

    public LedgerService(LedgerStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Posts a balanced journal to the ledger.
     *
     * <p>Validates balance constraint before any persistence call.
     * Fails fast with {@code UnbalancedJournalException} if unbalanced.
     *
     * @param journal the journal to post (must have balanced entries)
     * @return promise resolving to the persisted journal
     * @throws com.ghatana.appplatform.ledger.exception.UnbalancedJournalException if unbalanced
     */
    public Promise<Journal> postJournal(Journal journal) {
        Objects.requireNonNull(journal, "journal");
        // Balance enforcement: synchronous, throws before any async work
        BalanceEnforcer.enforce(journal);

        log.info("[ledger] posting journal: ref={} entries={} tenant={}",
                journal.reference(), journal.entryCount(), journal.tenantId());

        return store.postJournal(journal)
                .map(posted -> {
                    log.info("[ledger] journal posted: id={} ref={}",
                            posted.journalId(), posted.reference());
                    return posted;
                });
    }

    /**
     * Retrieves a journal by its ID.
     *
     * @param journalId journal identifier
     * @return promise resolving to the journal, empty if not found
     */
    public Promise<Optional<Journal>> getJournal(UUID journalId) {
        return store.getJournal(journalId);
    }

    /**
     * Returns all journals for a business reference within a tenant.
     *
     * @param reference business reference (order_id, trade_id, etc.)
     * @param tenantId  tenant scoping
     * @return journals ordered by posted_at_utc
     */
    public Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId) {
        return store.getJournalsByReference(reference, tenantId);
    }

    /**
     * Returns the current account balance.
     *
     * @param accountId account identifier
     * @param currency  ISO 4217 currency code
     * @return current balance (materialized from all posted entries)
     */
    public Promise<MonetaryAmount> getAccountBalance(UUID accountId, String currency) {
        return store.getAccountBalance(accountId, currency);
    }
}
