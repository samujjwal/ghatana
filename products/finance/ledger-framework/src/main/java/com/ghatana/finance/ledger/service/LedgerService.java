/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.Journal;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for double-entry journal posting and balance queries.
 *
 * <p>Decouples the application services (e.g. {@link MultiCurrencyJournalService})
 * from the concrete persistence implementation. The finance ledger infrastructure
 * module wires the actual implementation at the application-composition boundary.
 *
 * @doc.type interface
 * @doc.purpose Port interface for ledger journal posting
 * @doc.layer finance
 * @doc.pattern Port
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public interface LedgerService {

    /**
     * Posts a balanced journal to the ledger.
     *
     * <p>Validates balance constraint before any persistence call.
     * Fails fast with {@link UnbalancedJournalException} if entries are unbalanced.
     *
     * @param journal the journal to post (must have balanced entries)
     * @return promise resolving to the persisted journal
     * @throws UnbalancedJournalException if debit does not equal credit per currency
     */
    Promise<Journal> postJournal(Journal journal);

    /**
     * Retrieves a journal by its ID.
     *
     * @param journalId journal identifier
     * @return promise resolving to the journal, empty if not found
     */
    Promise<Optional<Journal>> getJournal(UUID journalId);

    /**
     * Returns all journals for a business reference within a tenant.
     *
     * @param reference business reference (order_id, trade_id, etc.)
     * @param tenantId  tenant scoping
     * @return journals ordered by posted_at_utc
     */
    Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId);
}
