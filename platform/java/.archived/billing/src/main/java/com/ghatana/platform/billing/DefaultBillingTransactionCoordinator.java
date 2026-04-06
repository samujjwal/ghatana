/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.billing;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default saga-style billing coordinator.
 *
 * <p>Posts each transaction in order using {@link LedgerPostingService}. On the
 * first failure, reverses previously posted transactions in reverse order and
 * returns a failed coordination result.
 *
 * @doc.type class
 * @doc.purpose Default implementation for cross-domain billing saga coordination
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultBillingTransactionCoordinator implements BillingTransactionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DefaultBillingTransactionCoordinator.class);
    private static final int DEFAULT_COORDINATOR_BULKHEAD_LIMIT = 16;

    private final LedgerPostingService ledgerPostingService;
    private final CircuitBreaker coordinatorCircuitBreaker;
    private final Bulkhead coordinatorBulkhead;

    public DefaultBillingTransactionCoordinator(LedgerPostingService ledgerPostingService) {
        this(
            ledgerPostingService,
            CircuitBreakerProfiles.strict("billing-transaction-coordinator"),
            Bulkhead.of("billing-transaction-coordinator", DEFAULT_COORDINATOR_BULKHEAD_LIMIT)
        );
    }

    public DefaultBillingTransactionCoordinator(LedgerPostingService ledgerPostingService,
                                                CircuitBreaker coordinatorCircuitBreaker,
                                                Bulkhead coordinatorBulkhead) {
        this.ledgerPostingService = Objects.requireNonNull(ledgerPostingService,
            "ledgerPostingService must not be null");
        this.coordinatorCircuitBreaker = Objects.requireNonNull(coordinatorCircuitBreaker,
            "coordinatorCircuitBreaker must not be null");
        this.coordinatorBulkhead = Objects.requireNonNull(coordinatorBulkhead,
            "coordinatorBulkhead must not be null");
    }

    @Override
    public Promise<CoordinationResult> coordinate(String workflowId, List<BillingTransaction> transactions) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(transactions, "transactions must not be null");

        if (transactions.isEmpty()) {
            return Promise.of(new CoordinationResult(
                workflowId,
                true,
                List.of(),
                List.of(),
                null
            ));
        }

        return Promise.ofBlocking(Runnable::run, () -> coordinateSync(workflowId, transactions));
    }

    private CoordinationResult coordinateSync(String workflowId, List<BillingTransaction> transactions) {
        List<String> posted = new ArrayList<>();
        List<String> compensated = new ArrayList<>();

        for (BillingTransaction tx : transactions) {
            try {
                executeWithResilience(() -> ledgerPostingService.postTransaction(tx).toCompletableFuture().join());
                posted.add(tx.getTransactionId());
            } catch (Exception postError) {
                compensatePosted(posted, compensated);
                String reason = rootMessage(postError);
                log.warn("Billing workflow '{}' failed after {} posts: {}",
                    workflowId, posted.size(), reason);
                return new CoordinationResult(
                    workflowId,
                    false,
                    List.copyOf(posted),
                    List.copyOf(compensated),
                    reason
                );
            }
        }

        return new CoordinationResult(
            workflowId,
            true,
            List.copyOf(posted),
            List.of(),
            null
        );
    }

    private void compensatePosted(List<String> posted, List<String> compensated) {
        List<String> reverseOrder = new ArrayList<>(posted);
        Collections.reverse(reverseOrder);

        for (String txId : reverseOrder) {
            try {
                executeWithResilience(() -> ledgerPostingService.reverseTransaction(txId,
                    "workflow compensation").toCompletableFuture().join());
                compensated.add(txId);
            } catch (Exception compensationError) {
                log.error("Compensation failed for transactionId='{}'", txId, compensationError);
            }
        }
    }

    private <T> T executeWithResilience(java.util.concurrent.Callable<T> operation) {
        try {
            return coordinatorBulkhead.tryExecuteBlocking(() -> coordinatorCircuitBreaker.executeSync(operation));
        } catch (Exception exception) {
            throw new IllegalStateException("Coordinator resilient execution failed", exception);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cur = error;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
