package com.ghatana.platform.billing;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests saga coordination and compensation behavior for billing workflows
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultBillingTransactionCoordinator")
class DefaultBillingTransactionCoordinatorTest {

    @Test
    @DisplayName("succeeds when all postings succeed")
    void succeedsWhenAllPostingsSucceed() {
        StubLedgerPostingService ledger = new StubLedgerPostingService(Set.of());
        DefaultBillingTransactionCoordinator coordinator = new DefaultBillingTransactionCoordinator(ledger);

        BillingTransactionCoordinator.CoordinationResult result = coordinator.coordinate(
            "wf-1",
            List.of(tx("tx-1", "100.00"), tx("tx-2", "200.00"))
        ).toCompletableFuture().join();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.postedTransactionIds()).containsExactly("tx-1", "tx-2");
        assertThat(result.compensatedTransactionIds()).isEmpty();
    }

    @Test
    @DisplayName("compensates prior postings when a later posting fails")
    void compensatesOnFailure() {
        StubLedgerPostingService ledger = new StubLedgerPostingService(Set.of("tx-2"));
        DefaultBillingTransactionCoordinator coordinator = new DefaultBillingTransactionCoordinator(ledger);

        BillingTransactionCoordinator.CoordinationResult result = coordinator.coordinate(
            "wf-2",
            List.of(tx("tx-1", "100.00"), tx("tx-2", "200.00"), tx("tx-3", "300.00"))
        ).toCompletableFuture().join();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.postedTransactionIds()).containsExactly("tx-1");
        assertThat(result.compensatedTransactionIds()).containsExactly("tx-1");
        assertThat(result.failureReason()).contains("simulated failure");
    }

    @Test
    @DisplayName("short-circuits when coordinator circuit is open")
    void shortCircuitsWhenCircuitOpen() {
        StubLedgerPostingService ledger = new StubLedgerPostingService(Set.of("tx-1"));
        CircuitBreaker breaker = CircuitBreaker.builder("billing-coordinator-test")
            .failureThreshold(1)
            .successThreshold(1)
            .resetTimeout(Duration.ofMinutes(5))
            .build();

        DefaultBillingTransactionCoordinator coordinator = new DefaultBillingTransactionCoordinator(
            ledger,
            breaker,
            Bulkhead.of("billing-coordinator-test", 2)
        );

        BillingTransactionCoordinator.CoordinationResult first = coordinator.coordinate(
            "wf-open-1",
            List.of(tx("tx-1", "100.00"))
        ).toCompletableFuture().join();
        assertThat(first.succeeded()).isFalse();
        assertThat(ledger.postAttempts).isEqualTo(1);

        BillingTransactionCoordinator.CoordinationResult second = coordinator.coordinate(
            "wf-open-2",
            List.of(tx("tx-2", "100.00"))
        ).toCompletableFuture().join();
        assertThat(second.succeeded()).isFalse();
        assertThat(second.failureReason()).contains("Circuit breaker is OPEN");
        assertThat(ledger.postAttempts).isEqualTo(1);
    }

    private static BillingTransaction tx(String id, String amount) {
        return BillingTransaction.builder()
            .transactionId(id)
            .sourceProductId("phr")
            .debitAccount("PHR:AR:patient")
            .creditAccount("PHR:REVENUE:provider")
            .amount(new BigDecimal(amount))
            .currency("NPR")
            .type(BillingTransaction.TransactionType.CHARGE)
            .description("test")
            .externalReferenceId("enc-1")
            .tenantId("tenant-1")
            .occurredAt(Instant.now())
            .build();
    }

    private static final class StubLedgerPostingService implements LedgerPostingService {
        private final Set<String> failOnPostTxIds;
        private final List<String> reversed = new ArrayList<>();
        private int postAttempts;

        private StubLedgerPostingService(Set<String> failOnPostTxIds) {
            this.failOnPostTxIds = new HashSet<>(failOnPostTxIds);
        }

        @Override
        public Promise<String> postTransaction(BillingTransaction transaction) {
            postAttempts++;
            if (failOnPostTxIds.contains(transaction.getTransactionId())) {
                return Promise.ofException(new IllegalStateException("simulated failure for " + transaction.getTransactionId()));
            }
            return Promise.of("entry-" + transaction.getTransactionId());
        }

        @Override
        public Promise<String> reverseTransaction(String originalTransactionId, String reversalReason) {
            reversed.add(originalTransactionId);
            return Promise.of("reversal-" + originalTransactionId);
        }

        @Override
        public Promise<PostingStatus> getPostingStatus(String transactionId) {
            if (reversed.contains(transactionId)) {
                return Promise.of(PostingStatus.REVERSED);
            }
            return Promise.of(failOnPostTxIds.contains(transactionId) ? PostingStatus.FAILED : PostingStatus.POSTED);
        }
    }
}
