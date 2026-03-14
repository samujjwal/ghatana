/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Direction;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.port.LedgerStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates a contra-entry journal that reverses an existing posted journal (K16-004).
 *
 * <p>The reversal journal:
 * <ul>
 *   <li>Swaps each DEBIT entry to CREDIT and vice-versa (same amount, same account).</li>
 *   <li>Uses reference format {@code "reversal:{originalJournalId}"}.</li>
 *   <li>Embeds the original journal ID and reason in the description.</li>
 *   <li>Inherits tenant scope from the original journal.</li>
 * </ul>
 *
 * <p>Only the posting side is duplicated — {@code entryHash} and {@code sequenceNum}
 * are left null and assigned by the {@link LedgerStore} adapter during {@code postJournal}.
 *
 * @doc.type class
 * @doc.purpose Contra-entry reversal of a posted journal (K16-004)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class JournalReversalService {

    private static final Logger log = LoggerFactory.getLogger(JournalReversalService.class);

    private static final String REVERSAL_REF_PREFIX = "reversal:";

    private final LedgerStore store;

    public JournalReversalService(LedgerStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Reverses an existing journal by creating a contra-entry journal with all
     * DEBIT↔CREDIT directions flipped.
     *
     * @param originalJournalId journal to reverse
     * @param createdBy         principal initiating the reversal
     * @param reason            mandatory human-readable reason (e.g., "data entry error")
     * @param tenantId          tenant scope (must match original journal)
     * @return the newly posted reversal journal
     * @throws IllegalArgumentException if the original journal is not found or already a reversal
     */
    public Promise<Journal> reverse(UUID originalJournalId, UUID createdBy,
                                    String reason, UUID tenantId) {
        Objects.requireNonNull(originalJournalId, "originalJournalId");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) throw new IllegalArgumentException("Reversal reason must not be blank");

        return store.getJournal(originalJournalId)
            .then(opt -> {
                Journal original = opt.orElseThrow(() ->
                    new IllegalArgumentException("Journal not found: " + originalJournalId));

                if (original.reference().startsWith(REVERSAL_REF_PREFIX)) {
                    throw new IllegalArgumentException(
                        "Cannot reverse a reversal journal: " + originalJournalId);
                }

                Journal reversal = buildReversalJournal(original, createdBy, reason);
                log.info("Reversing journal={} reason='{}' by={}", originalJournalId, reason, createdBy);
                return store.postJournal(reversal);
            });
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private Journal buildReversalJournal(Journal original, UUID createdBy, String reason) {
        List<JournalEntry> contraEntries = original.entries().stream()
            .map(e -> JournalEntry.of(
                e.accountId(),
                flip(e.direction()),
                e.amount(),
                "Reversal of entry " + e.entryId() + ": " + reason
            ))
            .toList();

        String ref  = REVERSAL_REF_PREFIX + original.journalId();
        String desc = "Reversal of '" + original.description()
                    + "' [journalId=" + original.journalId() + "]. Reason: " + reason;

        return Journal.reconstruct(
            UUID.randomUUID(),
            ref,
            desc,
            original.fiscalYear(),
            original.postedAtBs(),
            java.time.Instant.now(),
            original.tenantId(),
            createdBy,
            contraEntries
        );
    }

    private static Direction flip(Direction d) {
        return d == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT;
    }
}
