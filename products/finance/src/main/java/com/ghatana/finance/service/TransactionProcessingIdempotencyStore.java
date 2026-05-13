package com.ghatana.finance.service;

import com.ghatana.platform.database.idempotency.IdempotencyConflictException;
import com.ghatana.platform.database.idempotency.InMemoryIdempotencyStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Adapts Finance transaction idempotency to the Kernel replay store
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class TransactionProcessingIdempotencyStore implements TransactionIdempotencyStore {

    private static final String OPERATION = "finance:transaction.process";

    private final InMemoryIdempotencyStore<TransactionResult> store;

    public TransactionProcessingIdempotencyStore(Duration ttl, Clock clock) {
        this.store = new InMemoryIdempotencyStore<>(
            Objects.requireNonNull(ttl, "ttl must not be null"),
            Objects.requireNonNull(clock, "clock must not be null")
        );
    }

    @Override
    public TransactionResult get(String transactionId, String fingerprint) {
        try {
            return store.findReplay(OPERATION, transactionId, fingerprint)
                .result()
                .orElse(null);
        } catch (IdempotencyConflictException exception) {
            throw toFinanceConflict(exception, transactionId);
        }
    }

    @Override
    public TransactionResult putIfAbsent(String transactionId, String fingerprint, TransactionResult result) {
        try {
            return store.putIfAbsent(OPERATION, transactionId, fingerprint, result);
        } catch (IdempotencyConflictException exception) {
            throw toFinanceConflict(exception, transactionId);
        }
    }

    static IllegalStateException toFinanceConflict(IdempotencyConflictException exception, String transactionId) {
        if (exception == null) {
            return new IllegalStateException("Transaction '" + transactionId
                + "' was already processed with different content");
        }
        return new IllegalStateException(
            "Transaction '" + transactionId + "' was already processed with different content",
            exception
        );
    }
}
