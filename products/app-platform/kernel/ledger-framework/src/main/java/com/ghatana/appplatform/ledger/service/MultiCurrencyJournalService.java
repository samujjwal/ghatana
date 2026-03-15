/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for multi-currency journal posting (STORY-K16-011).
 *
 * <p>Multi-currency journals are validated per-currency independently: each currency's
 * total debits must equal its total credits within the same journal. Cross-currency
 * imbalance (e.g. NPR debit without matching NPR credit) is rejected by
 * {@link BalanceEnforcer} before any persistence call.
 *
 * <p>FX conversion is modelled as two separate single-currency journals:
 * <ol>
 *   <li>Source journal: balanced in the source currency (e.g. NPR)</li>
 *   <li>Target journal: balanced in the target currency (e.g. USD)</li>
 * </ol>
 * Both journals carry the exchange rate in their description for audit and reconciliation.
 *
 * @doc.type class
 * @doc.purpose Multi-currency journal posting with FX conversion support
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MultiCurrencyJournalService {

    private final LedgerService ledgerService;

    /**
     * @param ledgerService the underlying journal posting service
     */
    public MultiCurrencyJournalService(LedgerService ledgerService) {
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService");
    }

    // ── Multi-currency posting ─────────────────────────────────────────────────

    /**
     * Posts a multi-currency balanced journal.
     *
     * <p>Each currency present in the journal's entries must independently balance:
     * the sum of DEBIT amounts for a given currency must equal the sum of CREDIT amounts
     * for that same currency. Mixing currencies across debit/credit sides is rejected.
     *
     * @param journal the journal to post; entries may span multiple currencies
     * @return promise resolving to the persisted journal
     * @throws com.ghatana.appplatform.ledger.exception.UnbalancedJournalException if any
     *         currency is not balanced
     */
    public Promise<Journal> postMultiCurrencyJournal(Journal journal) {
        Objects.requireNonNull(journal, "journal");
        return ledgerService.postJournal(journal);
    }

    // ── FX conversion ─────────────────────────────────────────────────────────

    /**
     * Constructs two balanced journals for a cross-currency FX conversion without
     * posting them. Use this method to inspect and validate the generated journals
     * before committing.
     *
     * <p>The returned list always has exactly two elements:
     * <ol>
     *   <li>index 0 — source journal (balanced in {@code request.sourceAmount()} currency)</li>
     *   <li>index 1 — target journal (balanced in {@code request.targetAmount()} currency)</li>
     * </ol>
     *
     * @param request FX conversion parameters
     * @return list of [sourceJournal, targetJournal], each fully balanced
     */
    public List<Journal> buildFxJournals(FxConversionRequest request) {
        Objects.requireNonNull(request, "request");

        String srcCode = request.sourceAmount().currencyCode();
        String tgtCode = request.targetAmount().currencyCode();
        String rateStr = request.exchangeRate().toPlainString();
        String rateLabel = String.format("rate=%s %s/%s", rateStr, srcCode, tgtCode);

        // Source journal: balanced in source currency
        // DEBIT bridge account ← source amount (exchange clearing owes source currency)
        // CREDIT source account   (source account pays out source currency)
        Journal sourceJournal = Journal.of(
                request.reference(),
                "FX-SRC: " + srcCode + "→" + tgtCode + " " + rateLabel,
                request.tenantId(),
                List.of(
                        JournalEntry.debit(
                                request.bridgeAccountId(),
                                request.sourceAmount(),
                                "FX source debit: " + rateLabel),
                        JournalEntry.credit(
                                request.sourceAccountId(),
                                request.sourceAmount(),
                                "FX source credit: " + rateLabel)
                )
        );

        // Target journal: balanced in target currency
        // DEBIT target account    (target account receives target currency)
        // CREDIT bridge account  ← target amount (exchange clearing delivers target currency)
        Journal targetJournal = Journal.of(
                request.reference(),
                "FX-TGT: " + srcCode + "→" + tgtCode + " " + rateLabel,
                request.tenantId(),
                List.of(
                        JournalEntry.debit(
                                request.targetAccountId(),
                                request.targetAmount(),
                                "FX target debit: " + rateLabel),
                        JournalEntry.credit(
                                request.bridgeAccountId(),
                                request.targetAmount(),
                                "FX target credit: " + rateLabel)
                )
        );

        return List.of(sourceJournal, targetJournal);
    }

    /**
     * Posts a cross-currency FX conversion as two separate balanced journals.
     *
     * <p>The source journal is persisted first; on success the target journal is
     * persisted. Both journals carry the exchange rate in their description for
     * audit and reconciliation.
     *
     * @param request FX conversion parameters
     * @return promise resolving to the conversion result containing both posted journals
     */
    public Promise<FxConversionResult> postFxConversion(FxConversionRequest request) {
        Objects.requireNonNull(request, "request");
        List<Journal> journals = buildFxJournals(request);
        Journal sourceJournal = journals.get(0);
        Journal targetJournal = journals.get(1);

        return ledgerService.postJournal(sourceJournal)
                .then(postedSource -> ledgerService.postJournal(targetJournal)
                        .map(postedTarget -> new FxConversionResult(
                                postedSource, postedTarget, request.exchangeRate())));
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    /**
     * Parameters for a cross-currency FX conversion.
     *
     * @param reference       business reference (e.g. trade_id)
     * @param sourceAccountId account debited in the source currency
     * @param targetAccountId account credited in the target currency
     * @param bridgeAccountId currency exchange clearing/bridge account
     * @param sourceAmount    amount in the source currency (must be positive)
     * @param targetAmount    amount in the target currency (must be positive)
     * @param exchangeRate    exchange rate used (sourceAmount / targetAmount)
     * @param tenantId        owning tenant
     */
    public record FxConversionRequest(
            String reference,
            UUID sourceAccountId,
            UUID targetAccountId,
            UUID bridgeAccountId,
            MonetaryAmount sourceAmount,
            MonetaryAmount targetAmount,
            BigDecimal exchangeRate,
            UUID tenantId
    ) {
        public FxConversionRequest {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId");
            Objects.requireNonNull(targetAccountId, "targetAccountId");
            Objects.requireNonNull(bridgeAccountId, "bridgeAccountId");
            Objects.requireNonNull(sourceAmount, "sourceAmount");
            Objects.requireNonNull(targetAmount, "targetAmount");
            Objects.requireNonNull(exchangeRate, "exchangeRate");
            if (!sourceAmount.isPositive()) throw new IllegalArgumentException("sourceAmount must be positive");
            if (!targetAmount.isPositive()) throw new IllegalArgumentException("targetAmount must be positive");
            if (exchangeRate.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("exchangeRate must be positive");
        }
    }

    /**
     * Result of a posted FX conversion containing both journals and the applied rate.
     *
     * @param sourceJournal posted journal in the source currency
     * @param targetJournal posted journal in the target currency
     * @param exchangeRate  exchange rate applied
     */
    public record FxConversionResult(
            Journal sourceJournal,
            Journal targetJournal,
            BigDecimal exchangeRate
    ) {}
}
