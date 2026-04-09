package com.ghatana.finance.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies expiring idempotency semantics for Finance transaction processing
 * @doc.layer product
 * @doc.pattern Test
 */
class TransactionProcessingIdempotencyStoreTest {

    @Test
    void returnsStoredResultForMatchingFingerprint() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        TransactionProcessingIdempotencyStore store = new TransactionProcessingIdempotencyStore(
            Duration.ofHours(24),
            clock
        );
        TransactionResult result = TransactionResult.approved();

        store.putIfAbsent("txn-1", "fp-1", result);

        assertSame(result, store.get("txn-1", "fp-1"));
    }

    @Test
    void expiresEntriesAfterTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        TransactionProcessingIdempotencyStore store = new TransactionProcessingIdempotencyStore(
            Duration.ofMinutes(5),
            clock
        );

        store.putIfAbsent("txn-1", "fp-1", TransactionResult.approved());
        clock.advance(Duration.ofMinutes(6));

        assertNull(store.get("txn-1", "fp-1"));
    }

    @Test
    void rejectsConflictingReplay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        TransactionProcessingIdempotencyStore store = new TransactionProcessingIdempotencyStore(
            Duration.ofHours(24),
            clock
        );

        store.putIfAbsent("txn-1", "fp-1", TransactionResult.approved());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> store.get("txn-1", "fp-2")
        );

        assertEquals("Transaction 'txn-1' was already processed with different content", exception.getMessage());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
