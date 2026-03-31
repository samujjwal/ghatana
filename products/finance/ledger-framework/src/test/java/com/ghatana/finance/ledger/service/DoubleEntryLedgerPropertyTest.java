/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.Currency;
import com.ghatana.finance.ledger.domain.Journal;
import com.ghatana.finance.ledger.domain.JournalEntry;
import com.ghatana.finance.ledger.domain.MonetaryAmount;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for the double-entry ledger invariant.
 *
 * <p>These tests verify the fundamental accounting invariant that governs every
 * journal posted to the ledger:</p>
 *
 * <blockquote>
 *   For every posted journal, the sum of DEBIT amounts exactly equals
 *   the sum of CREDIT amounts, per currency.
 * </blockquote>
 *
 * <p>Test strategy: instead of a single-example test, each parameterized test
 * runs across a stream of randomly-generated inputs that exercise the invariant
 * at scale (100 balanced cases, 100 unbalanced cases, multi-currency combinations).
 * This mirrors property-based testing semantics without adding an external library.
 *
 * @doc.type class
 * @doc.purpose Property-based tests for double-entry ledger balance invariant
 * @doc.layer finance
 * @doc.pattern Test
 */
@DisplayName("Double-Entry Ledger – Property-Based Tests")
class DoubleEntryLedgerPropertyTest extends EventloopTestBase {

    private InMemoryLedgerService ledger;

    // Seed fixed for reproducibility while still covering a wide input space
    private static final long SEED = 0xDEADBEEFCAFEL;
    private static final int TRIAL_COUNT = 100;

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_C = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_D = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    // Currencies used across tests
    private static final Currency[] CURRENCIES = {Currency.NPR, Currency.USD, Currency.JPY};

    @BeforeEach
    void setUp() {
        ledger = new InMemoryLedgerService();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 1: Any single-currency balanced journal is always accepted
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates 100 random balanced single-currency journals.
     * Each case: one debit + one credit for the same random amount in a random currency.
     */
    static Stream<Journal> balancedSingleCurrencyJournals() {
        Random rng = new Random(SEED);
        return Stream.generate(() -> {
            Currency currency = CURRENCIES[rng.nextInt(CURRENCIES.length)];
            String amount = randomPositiveAmount(rng, currency);
            return Journal.of(
                    "PROP-BAL-" + rng.nextInt(999_999),
                    "Property balanced single-currency",
                    TENANT,
                    List.of(
                            JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of(amount, currency), "debit"),
                            JournalEntry.credit(ACCOUNT_B, MonetaryAmount.of(amount, currency), "credit")
                    )
            );
        }).limit(TRIAL_COUNT);
    }

    @ParameterizedTest(name = "balanced single-currency trial {index}")
    @MethodSource("balancedSingleCurrencyJournals")
    @DisplayName("Prop-1: every balanced single-currency journal is accepted")
    void balancedSingleCurrencyJournalIsAccepted(Journal journal) {
        Journal posted = runPromise(() -> ledger.postJournal(journal));

        assertNotNull(posted, "postJournal must return the persisted journal");
        assertEquals(journal.journalId(), posted.journalId());
        assertDebitsCreditBalance(posted);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 2: Any unbalanced journal is always rejected
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates 100 deliberately unbalanced journals.
     * Each case: debit and credit differ by 1 cent (or the smallest currency unit).
     */
    static Stream<Journal> unbalancedJournals() {
        Random rng = new Random(SEED + 1);
        return Stream.generate(() -> {
            Currency currency = CURRENCIES[rng.nextInt(CURRENCIES.length)];
            String debitAmt = randomPositiveAmount(rng, currency);
            // Add 1 unit to the credit to make it unbalanced
            BigDecimal creditAmt = new BigDecimal(debitAmt)
                    .add(BigDecimal.ONE.scaleByPowerOfTen(-currency.decimalPlaces()))
                    .setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY);
            return Journal.of(
                    "PROP-UNBAL-" + rng.nextInt(999_999),
                    "Property unbalanced",
                    TENANT,
                    List.of(
                            JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of(debitAmt, currency), "debit"),
                            JournalEntry.credit(ACCOUNT_B, MonetaryAmount.of(creditAmt.toPlainString(), currency), "credit")
                    )
            );
        }).limit(TRIAL_COUNT);
    }

    @ParameterizedTest(name = "unbalanced trial {index}")
    @MethodSource("unbalancedJournals")
    @DisplayName("Prop-2: every unbalanced journal is rejected with UnbalancedJournalException")
    void unbalancedJournalIsRejected(Journal journal) {
        assertThrows(UnbalancedJournalException.class,
                () -> runPromise(() -> ledger.postJournal(journal)),
                "posting an unbalanced journal must throw UnbalancedJournalException");
        clearFatalError();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 3: Split-leg balanced journals (many debits, many credits)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates 50 journals with multiple random debit/credit legs that balance.
     * Each case: N random debit splits + one matching total credit (or vice versa).
     */
    static Stream<Journal> multiLegBalancedJournals() {
        Random rng = new Random(SEED + 2);
        return Stream.generate(() -> {
            Currency currency = CURRENCIES[rng.nextInt(CURRENCIES.length)];
            int legCount = 2 + rng.nextInt(4); // 2..5 debit legs
            List<JournalEntry> entries = new ArrayList<>();

            BigDecimal totalDebit = BigDecimal.ZERO;
            for (int i = 0; i < legCount; i++) {
                String amt = randomPositiveAmount(rng, currency);
                entries.add(JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of(amt, currency), "debit-" + i));
                totalDebit = totalDebit.add(new BigDecimal(amt));
            }
            // Single credit leg matching the sum
            String creditAmt = totalDebit.setScale(currency.decimalPlaces(), RoundingMode.HALF_UP).toPlainString();
            entries.add(JournalEntry.credit(ACCOUNT_B, MonetaryAmount.of(creditAmt, currency), "credit-total"));

            return Journal.of("PROP-SPLIT-" + rng.nextInt(999_999), "Multi-leg balanced", TENANT, entries);
        }).limit(50);
    }

    @ParameterizedTest(name = "multi-leg balanced trial {index}")
    @MethodSource("multiLegBalancedJournals")
    @DisplayName("Prop-3: multi-leg balanced journal is always accepted")
    void multiLegBalancedJournalIsAccepted(Journal journal) {
        Journal posted = runPromise(() -> ledger.postJournal(journal));

        assertNotNull(posted);
        assertDebitsCreditBalance(posted);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 4: Multi-currency balanced journals (two independent legs)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates 50 multi-currency journals — two currencies, each independently balanced.
     */
    static Stream<Journal> multiCurrencyBalancedJournals() {
        Random rng = new Random(SEED + 3);
        return Stream.generate(() -> {
            Currency curr1 = Currency.NPR;
            Currency curr2 = Currency.USD;
            String amt1 = randomPositiveAmount(rng, curr1);
            String amt2 = randomPositiveAmount(rng, curr2);
            return Journal.of(
                    "PROP-MULTI-" + rng.nextInt(999_999),
                    "Multi-currency balanced",
                    TENANT,
                    List.of(
                            JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of(amt1, curr1), "NPR debit"),
                            JournalEntry.credit(ACCOUNT_B, MonetaryAmount.of(amt1, curr1), "NPR credit"),
                            JournalEntry.debit(ACCOUNT_C, MonetaryAmount.of(amt2, curr2), "USD debit"),
                            JournalEntry.credit(ACCOUNT_D, MonetaryAmount.of(amt2, curr2), "USD credit")
                    )
            );
        }).limit(50);
    }

    @ParameterizedTest(name = "multi-currency balanced trial {index}")
    @MethodSource("multiCurrencyBalancedJournals")
    @DisplayName("Prop-4: multi-currency journal with each currency balanced is accepted")
    void multiCurrencyBalancedJournalIsAccepted(Journal journal) {
        Journal posted = runPromise(() -> ledger.postJournal(journal));

        assertNotNull(posted);
        assertDebitsCreditBalance(posted);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 5: Multi-currency where one currency is unbalanced is rejected
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates journals where NPR is balanced but USD has a 1-cent mismatch.
     */
    static Stream<Journal> multiCurrencyPartiallyUnbalancedJournals() {
        Random rng = new Random(SEED + 4);
        return Stream.generate(() -> {
            String nprAmt = randomPositiveAmount(rng, Currency.NPR);
            String usdDebit = randomPositiveAmount(rng, Currency.USD);
            // USD credit differs by 1 cent
            BigDecimal usdCredit = new BigDecimal(usdDebit).add(new BigDecimal("0.01"));

            return Journal.of(
                    "PROP-PARTIAL-" + rng.nextInt(999_999),
                    "Multi-currency partially unbalanced",
                    TENANT,
                    List.of(
                            JournalEntry.debit(ACCOUNT_A, MonetaryAmount.of(nprAmt, Currency.NPR), "NPR debit"),
                            JournalEntry.credit(ACCOUNT_B, MonetaryAmount.of(nprAmt, Currency.NPR), "NPR credit"),
                            JournalEntry.debit(ACCOUNT_C, MonetaryAmount.of(usdDebit, Currency.USD), "USD debit"),
                            JournalEntry.credit(ACCOUNT_D, MonetaryAmount.of(usdCredit.toPlainString(), Currency.USD), "USD credit off-by-one")
                    )
            );
        }).limit(50);
    }

    @ParameterizedTest(name = "partial unbalanced trial {index}")
    @MethodSource("multiCurrencyPartiallyUnbalancedJournals")
    @DisplayName("Prop-5: multi-currency journal where one currency is off is rejected")
    void multiCurrencyPartiallyUnbalancedIsRejected(Journal journal) {
        assertThrows(UnbalancedJournalException.class,
                () -> runPromise(() -> ledger.postJournal(journal)),
                "A partially unbalanced multi-currency journal must be rejected");
        clearFatalError();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property 6: GetJournal round-trip — posted journal is retrievable by ID
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates 50 balanced journals to verify post → get round-trip.
     */
    static Stream<Journal> balancedJournalsForRoundTrip() {
        return balancedSingleCurrencyJournals().limit(50);
    }

    @ParameterizedTest(name = "round-trip trial {index}")
    @MethodSource("balancedJournalsForRoundTrip")
    @DisplayName("Prop-6: posted journal is retrievable by ID with identical entries")
    void postedJournalIsRetrievableById(Journal journal) {
        runPromise(() -> ledger.postJournal(journal));

        Journal retrieved = runPromise(() -> ledger.getJournal(journal.journalId()))
                .orElseThrow(() -> new AssertionError("Expected journal not found: " + journal.journalId()));

        assertEquals(journal.journalId(), retrieved.journalId());
        assertEquals(journal.reference(), retrieved.reference());
        assertEquals(journal.entries().size(), retrieved.entries().size());
        assertDebitsCreditBalance(retrieved);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a random positive amount string appropriate for the given currency's precision.
     * Range: 0.01 .. 99_999.99 (for 2-decimal currencies) or equivalent.
     */
    private static String randomPositiveAmount(Random rng, Currency currency) {
        // Pick a random integer number of base units (1..9_999_999)
        int baseUnits = 1 + rng.nextInt(9_999_999);
        BigDecimal raw = BigDecimal.valueOf(baseUnits, currency.decimalPlaces());
        return raw.setScale(currency.decimalPlaces(), RoundingMode.UNNECESSARY).toPlainString();
    }

    /**
     * Asserts that sum(DEBIT) == sum(CREDIT) per currency for a given journal.
     */
    private static void assertDebitsCreditBalance(Journal journal) {
        java.util.Map<String, BigDecimal> net = new java.util.LinkedHashMap<>();
        for (JournalEntry entry : journal.entries()) {
            String code = entry.amount().currencyCode();
            BigDecimal value = entry.amount().getAmount();
            if (entry.direction() == com.ghatana.finance.ledger.domain.Direction.DEBIT) {
                net.merge(code, value, BigDecimal::add);
            } else {
                net.merge(code, value.negate(), BigDecimal::add);
            }
        }
        for (java.util.Map.Entry<String, BigDecimal> e : net.entrySet()) {
            assertEquals(0, e.getValue().compareTo(BigDecimal.ZERO),
                    "Ledger invariant violated: currency " + e.getKey() + " net=" + e.getValue());
        }
    }
}
