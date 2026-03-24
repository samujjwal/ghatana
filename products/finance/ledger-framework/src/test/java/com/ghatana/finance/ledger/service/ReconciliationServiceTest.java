/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.Currency;
import com.ghatana.finance.ledger.service.ReconciliationService.ReconciliationItem;
import com.ghatana.finance.ledger.service.ReconciliationService.ReconciliationRequest;
import com.ghatana.finance.ledger.service.ReconciliationService.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReconciliationService} covering:
 * <ul>
 *   <li>Exact match (zero difference)</li>
 *   <li>Tolerance-based match</li>
 *   <li>Break detection</li>
 *   <li>Zero-tolerance overload</li>
 *   <li>Negative tolerance rejection</li>
 *   <li>Empty batch</li>
 *   <li>Ordering preservation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for ledger reconciliation engine tolerance-based matching (K16-013)
 * @doc.layer finance
 * @doc.pattern Test
 */
@DisplayName("ReconciliationService")
class ReconciliationServiceTest {

    private ReconciliationService service;

    private static final UUID ACCOUNT_A = UUID.randomUUID();
    private static final UUID ACCOUNT_B = UUID.randomUUID();
    private static final UUID ACCOUNT_C = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReconciliationService();
    }

    // ── Exact match ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exact match")
    class ExactMatch {

        @Test
        @DisplayName("Should return MATCHED when balances are identical")
        void shouldMatchWhenBalancesAreIdentical() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.NPR,
                    new BigDecimal("1000.00"), new BigDecimal("1000.00"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), BigDecimal.ZERO);

            assertEquals(1, items.size());
            ReconciliationItem item = items.get(0);
            assertEquals(ReconciliationStatus.MATCHED, item.status());
            assertTrue(item.difference().compareTo(BigDecimal.ZERO) == 0,
                    "difference should be zero");
            assertFalse(item.toleranceUsed());
            assertFalse(item.isBreak());
        }

        @Test
        @DisplayName("Should return MATCHED for zero balances")
        void shouldMatchForZeroBalances() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.USD,
                    BigDecimal.ZERO, BigDecimal.ZERO);

            List<ReconciliationItem> items = service.reconcile(List.of(req));

            assertEquals(ReconciliationStatus.MATCHED, items.get(0).status());
            assertFalse(items.get(0).toleranceUsed());
        }
    }

    // ── Tolerance match ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tolerance-based match")
    class ToleranceMatch {

        @Test
        @DisplayName("Should return MATCHED with toleranceUsed when within tolerance")
        void shouldMatchWithToleranceWhenWithinLimit() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.NPR,
                    new BigDecimal("1000.00"), new BigDecimal("999.99"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), new BigDecimal("0.05"));

            ReconciliationItem item = items.get(0);
            assertEquals(ReconciliationStatus.MATCHED, item.status());
            assertEquals(new BigDecimal("0.01"), item.difference());
            assertTrue(item.toleranceUsed());
            assertFalse(item.isBreak());
        }

        @Test
        @DisplayName("Should return MATCHED when difference exactly equals tolerance")
        void shouldMatchWhenDifferenceExactlyEqualsTolerance() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.USD,
                    new BigDecimal("500.00"), new BigDecimal("499.50"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), new BigDecimal("0.50"));

            assertEquals(ReconciliationStatus.MATCHED, items.get(0).status());
            assertTrue(items.get(0).toleranceUsed());
        }

        @Test
        @DisplayName("Should return BREAK when difference exceeds tolerance")
        void shouldBreakWhenDifferenceExceedsTolerance() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.USD,
                    new BigDecimal("1000.00"), new BigDecimal("990.00"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), new BigDecimal("5.00"));

            ReconciliationItem item = items.get(0);
            assertEquals(ReconciliationStatus.BREAK, item.status());
            assertEquals(new BigDecimal("10.00"), item.difference());
            assertFalse(item.toleranceUsed());
            assertTrue(item.isBreak());
        }
    }

    // ── Break detection ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Break detection")
    class BreakDetection {

        @Test
        @DisplayName("Should return BREAK for non-zero difference with zero tolerance")
        void shouldBreakForNonZeroDifferenceWithZeroTolerance() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.NPR,
                    new BigDecimal("1000.00"), new BigDecimal("999.99"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), BigDecimal.ZERO);

            assertEquals(ReconciliationStatus.BREAK, items.get(0).status());
            assertTrue(items.get(0).isBreak());
        }

        @Test
        @DisplayName("Should compute absolute difference regardless of sign")
        void shouldComputeAbsoluteDifference() {
            // Ledger < source
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.USD,
                    new BigDecimal("900.00"), new BigDecimal("1000.00"));

            List<ReconciliationItem> items = service.reconcile(List.of(req), BigDecimal.ZERO);

            assertEquals(new BigDecimal("100.00"), items.get(0).difference());
            assertEquals(ReconciliationStatus.BREAK, items.get(0).status());
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Should reject negative tolerance")
        void shouldRejectNegativeTolerance() {
            ReconciliationRequest req = ReconciliationRequest.of(
                    ACCOUNT_A, Currency.NPR,
                    BigDecimal.TEN, BigDecimal.TEN);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.reconcile(List.of(req), new BigDecimal("-0.01")));
            assertTrue(ex.getMessage().contains("tolerance must be >= 0"));
        }

        @Test
        @DisplayName("Should reject null requests list")
        void shouldRejectNullRequests() {
            assertThrows(NullPointerException.class,
                    () -> service.reconcile(null, BigDecimal.ZERO));
        }

        @Test
        @DisplayName("Should reject null tolerance")
        void shouldRejectNullTolerance() {
            assertThrows(NullPointerException.class,
                    () -> service.reconcile(List.of(), null));
        }

        @Test
        @DisplayName("ReconciliationRequest should reject null mandatory fields")
        void requestShouldRejectNullMandatoryFields() {
            assertThrows(NullPointerException.class, () ->
                    new ReconciliationRequest(null, ACCOUNT_A, Currency.NPR,
                            BigDecimal.TEN, BigDecimal.TEN, null));
            assertThrows(NullPointerException.class, () ->
                    new ReconciliationRequest(UUID.randomUUID(), null, Currency.NPR,
                            BigDecimal.TEN, BigDecimal.TEN, null));
            assertThrows(NullPointerException.class, () ->
                    new ReconciliationRequest(UUID.randomUUID(), ACCOUNT_A, null,
                            BigDecimal.TEN, BigDecimal.TEN, null));
        }
    }

    // ── Batch behavior ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Batch behavior")
    class BatchBehavior {

        @Test
        @DisplayName("Should reconcile empty batch without error")
        void shouldReconcileEmptyBatch() {
            List<ReconciliationItem> items = service.reconcile(List.of(), BigDecimal.ZERO);

            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("Should preserve input ordering in results")
        void shouldPreserveInputOrdering() {
            ReconciliationRequest reqA = ReconciliationRequest.of(ACCOUNT_A, Currency.NPR,
                    new BigDecimal("100"), new BigDecimal("100"));
            ReconciliationRequest reqB = ReconciliationRequest.of(ACCOUNT_B, Currency.USD,
                    new BigDecimal("200"), new BigDecimal("300"));
            ReconciliationRequest reqC = ReconciliationRequest.of(ACCOUNT_C, Currency.NPR,
                    new BigDecimal("50"), new BigDecimal("50"));

            List<ReconciliationItem> items = service.reconcile(
                    List.of(reqA, reqB, reqC), BigDecimal.ZERO);

            assertEquals(3, items.size());
            assertEquals(reqA, items.get(0).request());
            assertEquals(reqB, items.get(1).request());
            assertEquals(reqC, items.get(2).request());
        }

        @Test
        @DisplayName("Should handle mixed outcomes in single batch")
        void shouldHandleMixedOutcomesInSingleBatch() {
            ReconciliationRequest exact = ReconciliationRequest.of(ACCOUNT_A, Currency.NPR,
                    new BigDecimal("1000"), new BigDecimal("1000"));
            ReconciliationRequest tolMatch = ReconciliationRequest.of(ACCOUNT_B, Currency.NPR,
                    new BigDecimal("1000"), new BigDecimal("999.99"));
            ReconciliationRequest brk = ReconciliationRequest.of(ACCOUNT_C, Currency.NPR,
                    new BigDecimal("1000"), new BigDecimal("950"));

            List<ReconciliationItem> items = service.reconcile(
                    List.of(exact, tolMatch, brk), new BigDecimal("0.05"));

            assertEquals(ReconciliationStatus.MATCHED, items.get(0).status());
            assertFalse(items.get(0).toleranceUsed());

            assertEquals(ReconciliationStatus.MATCHED, items.get(1).status());
            assertTrue(items.get(1).toleranceUsed());

            assertEquals(ReconciliationStatus.BREAK, items.get(2).status());
        }
    }

    // ── Zero-tolerance overload ────────────────────────────────────────────────

    @Nested
    @DisplayName("Zero-tolerance overload")
    class ZeroToleranceOverload {

        @Test
        @DisplayName("Overloaded reconcile (no tolerance) should use zero tolerance")
        void overloadedReconcileShouldUseZeroTolerance() {
            ReconciliationRequest exact = ReconciliationRequest.of(ACCOUNT_A, Currency.USD,
                    new BigDecimal("100"), new BigDecimal("100"));
            ReconciliationRequest diff = ReconciliationRequest.of(ACCOUNT_B, Currency.USD,
                    new BigDecimal("100"), new BigDecimal("99.99"));

            List<ReconciliationItem> items = service.reconcile(List.of(exact, diff));

            assertEquals(ReconciliationStatus.MATCHED, items.get(0).status());
            assertEquals(ReconciliationStatus.BREAK, items.get(1).status());
        }
    }
}
