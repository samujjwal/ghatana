package com.ghatana.finance.kernel.service;

import com.ghatana.platform.billing.BillingTransaction;
import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Validates circuit-breaker/bulkhead resilience for BillingLedgerAdapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BillingLedgerAdapter Resilience")
class BillingLedgerAdapterResilienceTest {

    @Test
    @DisplayName("opens circuit after threshold and short-circuits further posts")
    void opensCircuitAfterThreshold() {
        LedgerManagementService ledgerService = mock(LedgerManagementService.class);
        when(ledgerService.postEntry(any()))
            .thenReturn(Promise.ofException(new RuntimeException("ledger unavailable")));

        CircuitBreaker breaker = CircuitBreaker.builder("ledger-test")
            .failureThreshold(2)
            .successThreshold(1)
            .resetTimeout(Duration.ofMinutes(5))
            .build();

        BillingLedgerAdapter adapter = new BillingLedgerAdapter(
            ledgerService,
            breaker,
            Bulkhead.of("ledger-test", 4)
        );

        assertThrows(CompletionException.class,
            () -> adapter.postTransaction(tx("tx-fail-1")).toCompletableFuture().join());
        assertThrows(CompletionException.class,
            () -> adapter.postTransaction(tx("tx-fail-2")).toCompletableFuture().join());

        CompletionException openError = assertThrows(CompletionException.class,
            () -> adapter.postTransaction(tx("tx-fail-3")).toCompletableFuture().join());

        verify(ledgerService, times(2)).postEntry(any());
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        Throwable cause = openError;
        boolean foundCircuitOpen = false;
        while (cause != null) {
            if (cause instanceof CircuitBreaker.CircuitBreakerOpenException) {
                foundCircuitOpen = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(foundCircuitOpen, "Expected CircuitBreakerOpenException in cause chain");
    }

    @Test
    @DisplayName("transitions from open to closed after reset timeout and successful probe")
    void transitionsOpenToClosedOnSuccessfulProbe() throws InterruptedException {
        LedgerManagementService ledgerService = mock(LedgerManagementService.class);
        AtomicInteger attempts = new AtomicInteger(0);

        when(ledgerService.postEntry(any())).thenAnswer(invocation -> {
            int n = attempts.incrementAndGet();
            if (n == 1) {
                return Promise.ofException(new RuntimeException("transient failure"));
            }
            return Promise.of(new LedgerManagementService.LedgerEntry(
                "entry-" + n,
                "d",
                "c",
                new BigDecimal("100.00"),
                "NPR",
                "ok",
                "CHARGE",
                Instant.now(),
                "POSTED"
            ));
        });

        CircuitBreaker breaker = CircuitBreaker.builder("ledger-recovery")
            .failureThreshold(1)
            .successThreshold(1)
            .resetTimeout(Duration.ofMillis(5))
            .build();

        BillingLedgerAdapter adapter = new BillingLedgerAdapter(
            ledgerService,
            breaker,
            Bulkhead.of("ledger-recovery", 4)
        );

        assertThrows(CompletionException.class,
            () -> adapter.postTransaction(tx("tx-open-1")).toCompletableFuture().join());
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        Thread.sleep(15L);

        String entryId = adapter.postTransaction(tx("tx-open-2")).toCompletableFuture().join();
        assertEquals("entry-2", entryId);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    private static BillingTransaction tx(String id) {
        return BillingTransaction.builder()
            .transactionId(id)
            .sourceProductId("phr")
            .debitAccount("PHR:AR:p1")
            .creditAccount("PHR:REVENUE:prov1")
            .amount(new BigDecimal("100.00"))
            .currency("NPR")
            .type(BillingTransaction.TransactionType.CHARGE)
            .description("test tx")
            .externalReferenceId("enc-1")
            .tenantId("tenant-1")
            .occurredAt(Instant.now())
            .build();
    }
}
