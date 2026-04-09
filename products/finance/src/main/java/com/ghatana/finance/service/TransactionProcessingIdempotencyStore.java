package com.ghatana.finance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose Stores expiring idempotent transaction results and rejects conflicting replays
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class TransactionProcessingIdempotencyStore implements TransactionIdempotencyStore {

    private final ConcurrentHashMap<String, CachedTransactionResult> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public TransactionProcessingIdempotencyStore(Duration ttl, Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public TransactionResult get(String transactionId, String fingerprint) {
        CachedTransactionResult entry = entries.get(transactionId);
        if (entry == null) {
            return null;
        }

        Instant now = Instant.now(clock);
        if (entry.isExpired(now)) {
            entries.remove(transactionId, entry);
            return null;
        }
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new IllegalStateException(
                "Transaction '" + transactionId + "' was already processed with different content"
            );
        }
        return entry.result();
    }

    @Override
    public TransactionResult putIfAbsent(String transactionId, String fingerprint, TransactionResult result) {
        CachedTransactionResult candidate = new CachedTransactionResult(
            fingerprint,
            result,
            Instant.now(clock).plus(ttl)
        );

        while (true) {
            CachedTransactionResult existing = entries.putIfAbsent(transactionId, candidate);
            if (existing == null) {
                return result;
            }

            Instant now = Instant.now(clock);
            if (existing.isExpired(now)) {
                if (entries.remove(transactionId, existing)) {
                    continue;
                }
            }

            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalStateException(
                    "Transaction '" + transactionId + "' was already processed with different content"
                );
            }
            return existing.result();
        }
    }

    private record CachedTransactionResult(String fingerprint, TransactionResult result, Instant expiresAt) {
        private boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
