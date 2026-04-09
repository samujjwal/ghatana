package com.ghatana.finance.service;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies JDBC-backed idempotency persistence for Finance transaction processing
 * @doc.layer product
 * @doc.pattern Test
 */
class JdbcTransactionProcessingIdempotencyStoreTest {

    @Test
    void storesResultsAcrossStoreInstances() {
        PostgreSQLContainer<?> postgres = FinanceAiPersistenceTestSupport.startPostgres();
        DataSource dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-txn-idem-test");
        try {
            JdbcTransactionProcessingIdempotencyStore writer = new JdbcTransactionProcessingIdempotencyStore(
                dataSource,
                Duration.ofHours(24),
                Clock.systemUTC()
            );
            TransactionResult result = TransactionResult.approved(java.util.Map.of("fraud_score", 0.1d));

            writer.putIfAbsent("txn-1", "fp-1", result);

            JdbcTransactionProcessingIdempotencyStore reader = new JdbcTransactionProcessingIdempotencyStore(
                dataSource,
                Duration.ofHours(24),
                Clock.systemUTC()
            );
            TransactionResult persisted = reader.get("txn-1", "fp-1");

            assertNotNull(persisted);
            assertEquals("APPROVED", persisted.getStatus());
            assertEquals("Transaction approved", persisted.getMessage());
            assertEquals(0.1d, persisted.getMetadata().get("fraud_score"));
        } finally {
            FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
            postgres.stop();
        }
    }

    @Test
    void expiresPersistedEntriesAfterTtl() {
        PostgreSQLContainer<?> postgres = FinanceAiPersistenceTestSupport.startPostgres();
        DataSource dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-txn-idem-expiry");
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));
        try {
            JdbcTransactionProcessingIdempotencyStore store = new JdbcTransactionProcessingIdempotencyStore(
                dataSource,
                Duration.ofMinutes(5),
                clock
            );

            store.putIfAbsent("txn-2", "fp-2", TransactionResult.approved());
            clock.advance(Duration.ofMinutes(6));

            assertNull(store.get("txn-2", "fp-2"));
        } finally {
            FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
            postgres.stop();
        }
    }

    @Test
    void rejectsConflictingReplayForPersistedEntries() {
        PostgreSQLContainer<?> postgres = FinanceAiPersistenceTestSupport.startPostgres();
        DataSource dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-txn-idem-conflict");
        try {
            JdbcTransactionProcessingIdempotencyStore store = new JdbcTransactionProcessingIdempotencyStore(
                dataSource,
                Duration.ofHours(24),
                Clock.systemUTC()
            );

            store.putIfAbsent("txn-3", "fp-3", TransactionResult.approved());

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.get("txn-3", "other-fingerprint")
            );

            assertEquals("Transaction 'txn-3' was already processed with different content", exception.getMessage());
        } finally {
            FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
            postgres.stop();
        }
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
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
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
