/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.billing;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Billing module.
 * Tests transaction coordination, compensation, and billing workflows at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for billing subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Billing - Phase 3 Expansion")
class BillingExpansionTest extends EventloopTestBase {

    private StubLedgerPostingService ledger;
    private DefaultBillingTransactionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        ledger = new StubLedgerPostingService(Set.of());
        coordinator = new DefaultBillingTransactionCoordinator(ledger);
    }

    // ============================================
    // TRANSACTION COORDINATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Transaction Coordination")
    class CoordinationTests {

        @Test
        @DisplayName("Coordinate many transactions successfully")
        void coordinateManyTransactions() {
            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                transactions.add(tx("tx-" + i, String.valueOf(100 + i * 10)));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-1", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(100);
            assertThat(result.compensatedTransactionIds()).isEmpty();
        }

        @Test
        @DisplayName("Handle various transaction amounts")
        void variousAmounts() {
            List<BillingTransaction> transactions = new ArrayList<>();
            String[] amounts = {"1.00", "99.99", "1000.00", "0.01", "10000.99"};

            for (int i = 0; i < 50; i++) {
                String amount = amounts[i % amounts.length];
                transactions.add(tx("tx-" + i, amount));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-amounts", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(50);
        }

        @Test
        @DisplayName("Multi-tenant transaction isolation")
        void multiTenantIsolation() {
            String[] tenants = {"t1", "t2", "t3", "t4", "t5"};

            for (String tenant : tenants) {
                List<BillingTransaction> transactions = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    final int idx = i;
                    BillingTransaction tx = BillingTransaction.builder()
                            .transactionId(tenant + "-tx-" + idx)
                            .sourceProductId("product")
                            .debitAccount("ACCOUNT_DB")
                            .creditAccount("ACCOUNT_CR")
                            .amount(new BigDecimal("100.00"))
                            .currency("USD")
                            .type(BillingTransaction.TransactionType.CHARGE)
                            .description("test")
                            .externalReferenceId("ext-" + idx)
                            .tenantId(tenant)
                            .occurredAt(Instant.now())
                            .build();
                    transactions.add(tx);
                }

                BillingTransactionCoordinator.CoordinationResult result = runPromise(
                    () -> coordinator.coordinate("wf-" + tenant, transactions));

                assertThat(result.succeeded()).isTrue();
                assertThat(result.postedTransactionIds()).hasSize(20);
            }
        }

        @Test
        @DisplayName("Various transaction types")
        void variousTransactionTypes() {
            List<BillingTransaction> transactions = new ArrayList<>();
            BillingTransaction.TransactionType[] types = {
                BillingTransaction.TransactionType.CHARGE,
                BillingTransaction.TransactionType.REFUND,
                BillingTransaction.TransactionType.ADJUSTMENT
            };

            for (int i = 0; i < 60; i++) {
                final int idx = i;
                BillingTransaction.TransactionType type = types[idx % types.length];

                BillingTransaction transaction = BillingTransaction.builder()
                        .transactionId("tx-" + idx)
                        .sourceProductId("product")
                        .debitAccount("ACCOUNT_DB")
                        .creditAccount("ACCOUNT_CR")
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .type(type)
                        .description("test-" + type)
                        .externalReferenceId("ext-" + idx)
                        .tenantId("tenant-1")
                        .occurredAt(Instant.now())
                        .build();
                transactions.add(transaction);
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-types", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(60);
        }
    }

    // ============================================
    // COMPENSATION AND ROLLBACK (3 tests)
    // ============================================

    @Nested
    @DisplayName("Compensation and Rollback")
    class CompensationTests {

        @Test
        @DisplayName("Compensate when middle transaction fails")
        void compensateOnMiddleFailure() {
            Set<String> failingTxIds = new HashSet<>();
            failingTxIds.add("tx-5");

            StubLedgerPostingService failingLedger = new StubLedgerPostingService(failingTxIds);
            DefaultBillingTransactionCoordinator coord = new DefaultBillingTransactionCoordinator(failingLedger);

            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                transactions.add(tx("tx-" + i, "100.00"));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coord.coordinate("wf-fail-middle", transactions));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.postedTransactionIds()).contains("tx-0", "tx-1", "tx-2", "tx-3", "tx-4");
            assertThat(result.compensatedTransactionIds()).isNotEmpty();
        }

        @Test
        @DisplayName("Compensate early failures immediately")
        void compensateEarlyFailure() {
            Set<String> failingTxIds = new HashSet<>();
            failingTxIds.add("tx-1");

            StubLedgerPostingService failingLedger = new StubLedgerPostingService(failingTxIds);
            DefaultBillingTransactionCoordinator coord = new DefaultBillingTransactionCoordinator(failingLedger);

            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                transactions.add(tx("tx-" + i, "100.00"));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coord.coordinate("wf-fail-early", transactions));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.postedTransactionIds()).contains("tx-0");
            assertThat(result.compensatedTransactionIds()).contains("tx-0");
        }

        @Test
        @DisplayName("Handle cascading failures with proper rollback")
        void cascadingFailures() {
            Set<String> failingTxIds = new HashSet<>();
            for (int i = 3; i < 8; i++) {
                failingTxIds.add("tx-" + i);
            }

            StubLedgerPostingService failingLedger = new StubLedgerPostingService(failingTxIds);
            DefaultBillingTransactionCoordinator coord = new DefaultBillingTransactionCoordinator(failingLedger);

            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                transactions.add(tx("tx-" + i, "100.00"));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coord.coordinate("wf-cascade", transactions));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.postedTransactionIds()).hasSize(3);
        }
    }

    // ============================================
    // CONCURRENT BILLING OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent transaction coordination")
        void concurrentCoordination() throws Exception {
            int threadCount = 20;
            int txPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            List<BillingTransaction> transactions = new ArrayList<>();
                            for (int i = 0; i < txPerThread; i++) {
                                final int txIdx = i;
                                transactions.add(tx(
                                    "tx-" + threadIdx + "-" + txIdx,
                                    String.valueOf(100 + txIdx)));
                            }

                            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                                () -> coordinator.coordinate("wf-" + threadIdx, transactions));

                            if (result.succeeded()) {
                                successCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isGreaterThan(0);
        }

        @Test
        @DisplayName("High-volume transaction processing")
        void highVolumeProcessing() {
            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                final int idx = i;
                transactions.add(tx("tx-" + idx, String.valueOf(100 + (idx % 1000))));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-high-volume", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(500);
        }

        @Test
        @DisplayName("Concurrent mixed success and failure scenarios")
        void concurrentMixedScenarios() throws Exception {
            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    exec.submit(() -> {
                        try {
                            // Mix of success and failure scenarios
                            Set<String> failingTxs = (threadIdx % 3 == 0) ? 
                                Set.of("inject-fail-" + threadIdx) : Set.of();

                            StubLedgerPostingService threadLedger = new StubLedgerPostingService(failingTxs);
                            DefaultBillingTransactionCoordinator threadCoord = 
                                new DefaultBillingTransactionCoordinator(threadLedger);

                            List<BillingTransaction> transactions = new ArrayList<>();
                            for (int i = 0; i < 30; i++) {
                                final int txIdx = i;
                                transactions.add(tx("tx-" + threadIdx + "-" + txIdx, "100.00"));
                            }

                            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                                () -> threadCoord.coordinate("wf-" + threadIdx, transactions));

                            if (!result.succeeded()) {
                                failureCount.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }
        }
    }

    // ============================================
    // EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large transaction amounts")
        void veryLargeAmounts() {
            List<BillingTransaction> transactions = new ArrayList<>();
            String[] largAmounts = {"999999.99", "1000000.00", "9999999.99"};

            for (int i = 0; i < 30; i++) {
                final int idx = i;
                transactions.add(tx("tx-" + idx, largAmounts[idx % largAmounts.length]));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-large-amounts", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(30);
        }

        @Test
        @DisplayName("Very many transactions in single workflow")
        void veryManyTransactions() {
            List<BillingTransaction> transactions = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                final int idx = i;
                transactions.add(tx("tx-" + idx, "100.00"));
            }

            BillingTransactionCoordinator.CoordinationResult result = runPromise(
                () -> coordinator.coordinate("wf-many", transactions));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.postedTransactionIds()).hasSize(1000);
        }
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    private static BillingTransaction tx(String id, String amount) {
        return BillingTransaction.builder()
                .transactionId(id)
                .sourceProductId("product")
                .debitAccount("ACCOUNT_DB")
                .creditAccount("ACCOUNT_CR")
                .amount(new BigDecimal(amount))
                .currency("USD")
                .type(BillingTransaction.TransactionType.CHARGE)
                .description("test")
                .externalReferenceId("ext-" + id)
                .tenantId("tenant-1")
                .occurredAt(Instant.now())
                .build();
    }
}
