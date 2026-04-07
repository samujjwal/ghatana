package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance transaction runtime config resolution for durable idempotency wiring
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceTransactionRuntimeConfigTest {

    @Test
    void defaultsToDisabledWhenNoRuntimeDatabaseExists() {
        FinanceTransactionRuntimeConfig config = FinanceTransactionRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of())
        );

        assertFalse(config.isPersistenceEnabled());
        assertEquals(24L, config.getIdempotencyTtl().toHours());
        assertFalse(config.isSharedRateLimitEnabled());
        assertEquals(120, config.getMaxRequestsPerMinute());
        assertTrue(config.getDataSourceConfig().isEmpty());
    }

    @Test
    void enablesPersistenceFromDedicatedTransactionConfig() {
        FinanceTransactionRuntimeConfig config = FinanceTransactionRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                "finance.transaction.idempotency.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance_txn",
                "finance.transaction.idempotency.database.username", "ghatana",
                "finance.transaction.idempotency.database.password", "secret",
                "finance.transaction.idempotency.ttl-hours", 48L,
                "finance.transaction.rate-limit.max-requests-per-minute", 90,
                "finance.transaction.rate-limit.window-seconds", 120L
            ))
        );

        assertTrue(config.isPersistenceEnabled());
        assertTrue(config.isSharedRateLimitEnabled());
        assertEquals("jdbc:postgresql://localhost:5432/finance_txn", config.getDataSourceConfig().orElseThrow().jdbcUrl());
        assertEquals(48L, config.getIdempotencyTtl().toHours());
        assertEquals(90, config.getMaxRequestsPerMinute());
        assertEquals(120L, config.getRateLimitWindow().toSeconds());
    }

    @Test
    void fallsBackToAiDatabaseConfigWhenTransactionDatabaseAbsent() {
        FinanceTransactionRuntimeConfig config = FinanceTransactionRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                "finance.ai.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance_ai",
                "finance.ai.database.username", "ghatana",
                "finance.ai.database.password", "secret",
                "finance.ai.database.pool-name", "finance-ai-pool"
            ))
        );

        assertTrue(config.isPersistenceEnabled());
        assertTrue(config.isSharedRateLimitEnabled());
        assertEquals("jdbc:postgresql://localhost:5432/finance_ai", config.getDataSourceConfig().orElseThrow().jdbcUrl());
        assertEquals("finance-ai-pool-transactions", config.getDataSourceConfig().orElseThrow().poolName());
    }

    @Test
    void allowsSharedRateLimitToBeDisabledExplicitly() {
        FinanceTransactionRuntimeConfig config = FinanceTransactionRuntimeConfig.fromContext(
            new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                "finance.transaction.idempotency.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance_txn",
                "finance.transaction.idempotency.database.username", "ghatana",
                "finance.transaction.idempotency.database.password", "secret",
                "finance.transaction.rate-limit.shared.enabled", false
            ))
        );

        assertFalse(config.isSharedRateLimitEnabled());
    }

    @Test
    void rejectsEnabledPersistenceWithoutCredentials() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            FinanceTransactionRuntimeConfig.fromContext(
                new FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(Map.of(
                    "finance.transaction.idempotency.persistence.enabled", true,
                    "finance.transaction.idempotency.database.jdbc-url", "jdbc:postgresql://localhost:5432/finance_txn"
                ))
            )
        );

        assertTrue(exception.getMessage().contains("username"));
    }
}