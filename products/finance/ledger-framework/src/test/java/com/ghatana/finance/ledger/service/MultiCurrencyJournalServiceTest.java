/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.*;
import com.ghatana.finance.ledger.service.MultiCurrencyJournalService.FxConversionRequest;
import com.ghatana.finance.ledger.service.MultiCurrencyJournalService.FxConversionResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MultiCurrencyJournalService} covering:
 * <ul>
 *   <li>Multi-currency journal posting delegation</li>
 *   <li>FX journal construction (source + target legs)</li>
 *   <li>FX conversion posting with saga compensation</li>
 *   <li>FxConversionRequest validation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for multi-currency journal posting and FX conversion
 * @doc.layer finance
 * @doc.pattern Test
 */
@DisplayName("MultiCurrencyJournalService")
class MultiCurrencyJournalServiceTest extends EventloopTestBase {

    private StubLedgerService ledgerService;
    private MultiCurrencyJournalService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SOURCE_ACCOUNT = UUID.randomUUID();
    private static final UUID TARGET_ACCOUNT = UUID.randomUUID();
    private static final UUID BRIDGE_ACCOUNT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ledgerService = new StubLedgerService();
        service = new MultiCurrencyJournalService(ledgerService);
    }

    // ==================== postMultiCurrencyJournal ====================

    @Nested
    @DisplayName("postMultiCurrencyJournal")
    class PostMultiCurrencyJournal {

        @Test
        @DisplayName("delegates to LedgerService.postJournal")
        void delegatesToLedgerService() {
            Journal journal = Journal.of("REF-001", "Test journal", TENANT, List.of(
                    JournalEntry.debit(SOURCE_ACCOUNT, MonetaryAmount.of("100.00", Currency.NPR), "debit"),
                    JournalEntry.credit(TARGET_ACCOUNT, MonetaryAmount.of("100.00", Currency.NPR), "credit")
            ));

            Journal posted = runPromise(() -> service.postMultiCurrencyJournal(journal));

            assertNotNull(posted);
            assertEquals(1, ledgerService.postedJournals.size());
            assertEquals(journal, ledgerService.postedJournals.get(0));
        }

        @Test
        @DisplayName("rejects null journal")
        void rejectsNullJournal() {
            assertThrows(NullPointerException.class,
                    () -> runPromise(() -> service.postMultiCurrencyJournal(null)));
        }

        @Test
        @DisplayName("propagates LedgerService failure")
        void propagatesFailure() {
            ledgerService.shouldFail = true;
            Journal journal = Journal.of("REF-002", "Fail journal", TENANT, List.of(
                    JournalEntry.debit(SOURCE_ACCOUNT, MonetaryAmount.of("50.00", Currency.NPR), "d"),
                    JournalEntry.credit(TARGET_ACCOUNT, MonetaryAmount.of("50.00", Currency.NPR), "c")
            ));

            assertThrows(RuntimeException.class,
                    () -> runPromise(() -> service.postMultiCurrencyJournal(journal)));
            clearFatalError();
        }
    }

    // ==================== buildFxJournals ====================

    @Nested
    @DisplayName("buildFxJournals")
    class BuildFxJournals {

        @Test
        @DisplayName("produces exactly two journals (source and target)")
        void producesTwoJournals() {
            FxConversionRequest request = validFxRequest();
            List<Journal> journals = service.buildFxJournals(request);
            assertEquals(2, journals.size());
        }

        @Test
        @DisplayName("source journal debits bridge and credits source account")
        void sourceJournalStructure() {
            FxConversionRequest request = validFxRequest();
            Journal source = service.buildFxJournals(request).get(0);

            assertEquals(2, source.entryCount());
            List<JournalEntry> debits = source.entriesOf(Direction.DEBIT);
            List<JournalEntry> credits = source.entriesOf(Direction.CREDIT);
            assertEquals(1, debits.size());
            assertEquals(1, credits.size());
            assertEquals(BRIDGE_ACCOUNT, debits.get(0).accountId());
            assertEquals(SOURCE_ACCOUNT, credits.get(0).accountId());
        }

        @Test
        @DisplayName("target journal debits target and credits bridge account")
        void targetJournalStructure() {
            FxConversionRequest request = validFxRequest();
            Journal target = service.buildFxJournals(request).get(1);

            assertEquals(2, target.entryCount());
            List<JournalEntry> debits = target.entriesOf(Direction.DEBIT);
            List<JournalEntry> credits = target.entriesOf(Direction.CREDIT);
            assertEquals(1, debits.size());
            assertEquals(1, credits.size());
            assertEquals(TARGET_ACCOUNT, debits.get(0).accountId());
            assertEquals(BRIDGE_ACCOUNT, credits.get(0).accountId());
        }

        @Test
        @DisplayName("journal descriptions contain exchange rate")
        void descriptionsContainRate() {
            FxConversionRequest request = validFxRequest();
            List<Journal> journals = service.buildFxJournals(request);

            assertTrue(journals.get(0).description().contains("rate="));
            assertTrue(journals.get(1).description().contains("rate="));
            assertTrue(journals.get(0).description().contains("NPR"));
            assertTrue(journals.get(1).description().contains("USD"));
        }

        @Test
        @DisplayName("journals carry the business reference and tenant from request")
        void journalsCarryReferenceAndTenant() {
            FxConversionRequest request = validFxRequest();
            List<Journal> journals = service.buildFxJournals(request);

            for (Journal j : journals) {
                assertEquals("FX-TRADE-001", j.reference());
                assertEquals(TENANT, j.tenantId());
            }
        }

        @Test
        @DisplayName("rejects null request")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> service.buildFxJournals(null));
        }
    }

    // ==================== FxConversionRequest validation ====================

    @Nested
    @DisplayName("FxConversionRequest validation")
    class RequestValidation {

        @Test
        @DisplayName("rejects null reference")
        void rejectsNullReference() {
            assertThrows(NullPointerException.class, () -> new FxConversionRequest(
                    null, SOURCE_ACCOUNT, TARGET_ACCOUNT, BRIDGE_ACCOUNT,
                    MonetaryAmount.of("100.00", Currency.NPR),
                    MonetaryAmount.of("1.20", Currency.USD),
                    new BigDecimal("83.33"), TENANT));
        }

        @Test
        @DisplayName("rejects null sourceAccountId")
        void rejectsNullSourceAccount() {
            assertThrows(NullPointerException.class, () -> new FxConversionRequest(
                    "REF", null, TARGET_ACCOUNT, BRIDGE_ACCOUNT,
                    MonetaryAmount.of("100.00", Currency.NPR),
                    MonetaryAmount.of("1.20", Currency.USD),
                    new BigDecimal("83.33"), TENANT));
        }

        @Test
        @DisplayName("rejects zero exchange rate")
        void rejectsZeroRate() {
            assertThrows(IllegalArgumentException.class, () -> new FxConversionRequest(
                    "REF", SOURCE_ACCOUNT, TARGET_ACCOUNT, BRIDGE_ACCOUNT,
                    MonetaryAmount.of("100.00", Currency.NPR),
                    MonetaryAmount.of("1.20", Currency.USD),
                    BigDecimal.ZERO, TENANT));
        }

        @Test
        @DisplayName("rejects negative exchange rate")
        void rejectsNegativeRate() {
            assertThrows(IllegalArgumentException.class, () -> new FxConversionRequest(
                    "REF", SOURCE_ACCOUNT, TARGET_ACCOUNT, BRIDGE_ACCOUNT,
                    MonetaryAmount.of("100.00", Currency.NPR),
                    MonetaryAmount.of("1.20", Currency.USD),
                    new BigDecimal("-1"), TENANT));
        }
    }

    // ==================== postFxConversion ====================

    @Nested
    @DisplayName("postFxConversion")
    class PostFxConversion {

        @Test
        @DisplayName("posts both source and target journals successfully")
        void happyPath() {
            FxConversionRequest request = validFxRequest();
            FxConversionResult result = runPromise(() -> service.postFxConversion(request));

            assertNotNull(result);
            assertNotNull(result.sourceJournal());
            assertNotNull(result.targetJournal());
            assertEquals(new BigDecimal("83.33"), result.exchangeRate());
            assertEquals(2, ledgerService.postedJournals.size());
        }

        @Test
        @DisplayName("posts source journal first then target journal")
        void postsInOrder() {
            FxConversionRequest request = validFxRequest();
            runPromise(() -> service.postFxConversion(request));

            assertEquals(2, ledgerService.postedJournals.size());
            assertTrue(ledgerService.postedJournals.get(0).description().contains("FX-SRC"));
            assertTrue(ledgerService.postedJournals.get(1).description().contains("FX-TGT"));
        }

        @Test
        @DisplayName("compensates source journal when target posting fails")
        void sagaCompensation() {
            ledgerService.failOnSecondPost = true;
            FxConversionRequest request = validFxRequest();

            assertThrows(RuntimeException.class,
                    () -> runPromise(() -> service.postFxConversion(request)));
            clearFatalError();

            // Source was posted, then compensation reversal was attempted
            // postedJournals: [sourceJournal, reversingJournal]
            assertTrue(ledgerService.postedJournals.size() >= 2);
            Journal reversal = ledgerService.postedJournals.get(ledgerService.postedJournals.size() - 1);
            assertTrue(reversal.description().contains("FX-COMPENSATION"));
        }

        @Test
        @DisplayName("rejects null request")
        void rejectsNull() {
            assertThrows(NullPointerException.class,
                    () -> runPromise(() -> service.postFxConversion(null)));
        }
    }

    // ==================== Journal.reverse ====================

    @Nested
    @DisplayName("Journal.reverse")
    class JournalReverse {

        @Test
        @DisplayName("creates journal with all directions flipped")
        void flipsDirections() {
            Journal original = Journal.of("REF", "Original", TENANT, List.of(
                    JournalEntry.debit(SOURCE_ACCOUNT, MonetaryAmount.of("100.00", Currency.NPR), "d"),
                    JournalEntry.credit(TARGET_ACCOUNT, MonetaryAmount.of("100.00", Currency.NPR), "c")
            ));

            Journal reversed = original.reverse("reversal reason");

            assertEquals(2, reversed.entryCount());
            List<JournalEntry> credits = reversed.entriesOf(Direction.CREDIT);
            List<JournalEntry> debits = reversed.entriesOf(Direction.DEBIT);
            assertEquals(1, credits.size());
            assertEquals(1, debits.size());
            // Original debit → reversed credit
            assertEquals(SOURCE_ACCOUNT, credits.get(0).accountId());
            // Original credit → reversed debit
            assertEquals(TARGET_ACCOUNT, debits.get(0).accountId());
        }

        @Test
        @DisplayName("carries reversal description")
        void carriesDescription() {
            Journal original = Journal.of("REF", "Original", TENANT, List.of(
                    JournalEntry.debit(SOURCE_ACCOUNT, MonetaryAmount.of("50.00", Currency.NPR), "d"),
                    JournalEntry.credit(TARGET_ACCOUNT, MonetaryAmount.of("50.00", Currency.NPR), "c")
            ));

            Journal reversed = original.reverse("compensation for failure");
            assertEquals("compensation for failure", reversed.description());
        }
    }

    // ==================== Helpers ====================

    private FxConversionRequest validFxRequest() {
        return new FxConversionRequest(
                "FX-TRADE-001",
                SOURCE_ACCOUNT,
                TARGET_ACCOUNT,
                BRIDGE_ACCOUNT,
                MonetaryAmount.of("10000.00", Currency.NPR),
                MonetaryAmount.of("120.00", Currency.USD),
                new BigDecimal("83.33"),
                TENANT
        );
    }

    /**
     * Stub LedgerService that records posted journals and optionally fails.
     */
    private static class StubLedgerService implements LedgerService {

        final List<Journal> postedJournals = new ArrayList<>();
        boolean shouldFail = false;
        boolean failOnSecondPost = false;
        private int postCount = 0;

        @Override
        public Promise<Journal> postJournal(Journal journal) {
            postCount++;
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("Simulated ledger failure"));
            }
            if (failOnSecondPost && postCount == 2) {
                return Promise.ofException(new RuntimeException("Target leg failure"));
            }
            postedJournals.add(journal);
            return Promise.of(journal);
        }

        @Override
        public Promise<Optional<Journal>> getJournal(UUID journalId) {
            return Promise.of(postedJournals.stream()
                    .filter(j -> j.journalId().equals(journalId))
                    .findFirst());
        }

        @Override
        public Promise<List<Journal>> getJournalsByReference(String reference, UUID tenantId) {
            return Promise.of(postedJournals.stream()
                    .filter(j -> j.reference().equals(reference))
                    .toList());
        }
    }
}
