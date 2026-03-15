/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Currency;
import com.ghatana.appplatform.ledger.domain.Journal;
import com.ghatana.appplatform.ledger.domain.JournalEntry;
import com.ghatana.appplatform.ledger.domain.MonetaryAmount;
import com.ghatana.appplatform.ledger.exception.UnbalancedJournalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MultiCurrencyJournalService} (STORY-K16-011).
 *
 * <p>Covers per-currency balance enforcement and FX conversion journal construction.
 * Tests exercise pure domain logic via {@code buildFxJournals()} and
 * {@code BalanceEnforcer} directly — no async / no LedgerService calls needed.
 *
 * @doc.type class
 * @doc.purpose Unit tests for multi-currency journal posting and FX conversion (K16-011)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiCurrencyJournalService — multi-currency posting and FX conversion")
class MultiCurrencyJournalServiceTest {

    private static final Currency NPR = Currency.NPR;
    private static final Currency USD = Currency.USD;

    private static final UUID NPR_DEBIT_ACCT  = UUID.randomUUID();
    private static final UUID NPR_CREDIT_ACCT = UUID.randomUUID();
    private static final UUID USD_DEBIT_ACCT  = UUID.randomUUID();
    private static final UUID USD_CREDIT_ACCT = UUID.randomUUID();
    private static final UUID BRIDGE_ACCT     = UUID.randomUUID();
    private static final UUID TENANT          = UUID.randomUUID();

    @Mock LedgerService ledgerService;

    private MultiCurrencyJournalService service;

    @BeforeEach
    void setUp() {
        service = new MultiCurrencyJournalService(ledgerService);
    }

    // ── AC1: per-currency balance ──────────────────────────────────────────────

    @Test
    @DisplayName("multiCurrency_eachBalances: NPR entries balance independently of USD entries")
    void multiCurrency_eachBalances() {
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(NPR_DEBIT_ACCT,  MonetaryAmount.of("5000", NPR), "NPR debit"),
                JournalEntry.credit(NPR_CREDIT_ACCT, MonetaryAmount.of("5000", NPR), "NPR credit"),
                JournalEntry.debit(USD_DEBIT_ACCT,  MonetaryAmount.of("100.00", USD), "USD debit"),
                JournalEntry.credit(USD_CREDIT_ACCT, MonetaryAmount.of("100.00", USD), "USD credit")
        );

        // BalanceEnforcer (used inside LedgerService.postJournal) must accept this
        assertThatCode(() -> BalanceEnforcer.enforce("MC-001", entries))
                .doesNotThrowAnyException();
    }

    // ── AC2: mixed-currency rejection ──────────────────────────────────────────

    @Test
    @DisplayName("multiCurrency_mixedRejected: NPR debit with no NPR credit is rejected")
    void multiCurrency_mixedRejected() {
        // NPR has a debit but no credit; USD has a credit but no debit
        List<JournalEntry> entries = List.of(
                JournalEntry.debit(NPR_DEBIT_ACCT,  MonetaryAmount.of("5000", NPR),   "NPR debit"),
                JournalEntry.credit(USD_CREDIT_ACCT, MonetaryAmount.of("37.50", USD), "USD credit")
        );

        assertThatThrownBy(() -> BalanceEnforcer.enforce("MC-MIX", entries))
                .isInstanceOf(UnbalancedJournalException.class);
    }

    // ── AC3: FX conversion produces two journals ───────────────────────────────

    @Test
    @DisplayName("multiCurrency_fxConversion: buildFxJournals returns exactly two balanced journals")
    void multiCurrency_fxConversion() {
        MultiCurrencyJournalService.FxConversionRequest request = fxRequest("133.00");

        List<Journal> journals = service.buildFxJournals(request);

        assertThat(journals).hasSize(2);

        Journal sourceJournal = journals.get(0);
        Journal targetJournal = journals.get(1);

        // Source journal is balanced in NPR
        assertThatCode(() -> BalanceEnforcer.enforce(sourceJournal))
                .as("source journal must be balanced in NPR")
                .doesNotThrowAnyException();

        // Target journal is balanced in USD
        assertThatCode(() -> BalanceEnforcer.enforce(targetJournal))
                .as("target journal must be balanced in USD")
                .doesNotThrowAnyException();

        // Source journal entries are in NPR
        assertThat(sourceJournal.entries())
                .allMatch(e -> "NPR".equals(e.amount().currencyCode()),
                        "all source entries must be in NPR");

        // Target journal entries are in USD
        assertThat(targetJournal.entries())
                .allMatch(e -> "USD".equals(e.amount().currencyCode()),
                        "all target entries must be in USD");
    }

    @Test
    @DisplayName("multiCurrency_fxConversion: journals share the same business reference")
    void multiCurrency_fxConversion_sharedReference() {
        MultiCurrencyJournalService.FxConversionRequest request = fxRequest("133.00");

        List<Journal> journals = service.buildFxJournals(request);

        assertThat(journals.get(0).reference()).isEqualTo("FX-REF-001");
        assertThat(journals.get(1).reference()).isEqualTo("FX-REF-001");
    }

    // ── AC4: exchange rate in metadata ────────────────────────────────────────

    @Test
    @DisplayName("multiCurrency_metadata: exchange rate appears in both journal descriptions")
    void multiCurrency_metadata() {
        MultiCurrencyJournalService.FxConversionRequest request = fxRequest("133.5500");

        List<Journal> journals = service.buildFxJournals(request);

        String srcDescription = journals.get(0).description();
        String tgtDescription = journals.get(1).description();

        assertThat(srcDescription)
                .as("source journal description must contain exchange rate")
                .contains("133.5500");
        assertThat(tgtDescription)
                .as("target journal description must contain exchange rate")
                .contains("133.5500");

        // Currency direction must be mentioned
        assertThat(srcDescription).contains("NPR").contains("USD");
        assertThat(tgtDescription).contains("NPR").contains("USD");
    }

    @Test
    @DisplayName("multiCurrency_metadata: source journal description is FX-SRC, target is FX-TGT")
    void multiCurrency_metadata_journalLabels() {
        List<Journal> journals = service.buildFxJournals(fxRequest("133.00"));

        assertThat(journals.get(0).description()).startsWith("FX-SRC:");
        assertThat(journals.get(1).description()).startsWith("FX-TGT:");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FxConversionRequest rejects non-positive exchange rate")
    void fxRequest_zeroRate_rejected() {
        assertThatThrownBy(() -> fxRequest("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangeRate");
    }

    @Test
    @DisplayName("FxConversionRequest rejects zero source amount")
    void fxRequest_zeroSourceAmount_rejected() {
        assertThatThrownBy(() -> new MultiCurrencyJournalService.FxConversionRequest(
                "REF", NPR_CREDIT_ACCT, USD_DEBIT_ACCT, BRIDGE_ACCT,
                MonetaryAmount.of("0", NPR),    // zero — must be positive
                MonetaryAmount.of("37.59", USD),
                new BigDecimal("133.00"),
                TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceAmount");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MultiCurrencyJournalService.FxConversionRequest fxRequest(String rate) {
        return new MultiCurrencyJournalService.FxConversionRequest(
                "FX-REF-001",
                NPR_CREDIT_ACCT,          // sourceAccountId: NPR account paying out
                USD_DEBIT_ACCT,           // targetAccountId: USD account receiving
                BRIDGE_ACCT,              // bridgeAccountId: clearing/exchange account
                MonetaryAmount.of("5000", NPR),
                MonetaryAmount.of("37.59", USD),
                new BigDecimal(rate),
                TENANT
        );
    }
}
