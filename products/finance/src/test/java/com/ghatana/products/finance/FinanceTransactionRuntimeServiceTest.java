package com.ghatana.products.finance;

import com.ghatana.finance.service.TransactionResult;
import java.time.Clock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance transaction runtime lifecycle and in-memory transaction processing wiring
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceTransactionRuntimeServiceTest {

    @Test
    void startsInMemoryTransactionRuntime() {
        FinanceTransactionRuntimeTestSupport.StubAiRuntime aiRuntime = new FinanceTransactionRuntimeTestSupport.StubAiRuntime();
        FinanceTransactionRuntimeService runtimeService = new FinanceTransactionRuntimeService(
            FinanceTransactionRuntimeConfig.disabled(java.time.Duration.ofHours(24)),
            aiRuntime,
            aiRuntime,
            Clock.systemUTC()
        );

        runtimeService.start();

        assertTrue(runtimeService.isHealthy());
        assertFalse(runtimeService.isSharedRateLimitingEnabled());
        TransactionResult result = runtimeService.getTransactionService().processTransaction(
            FinanceTransactionRuntimeTestSupport.createTransaction("txn-runtime-1")
        );
        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertEquals(1, aiRuntime.executions());

        runtimeService.stop();
    }

    @Test
    void rejectsTransactionAccessBeforeStart() {
        FinanceTransactionRuntimeTestSupport.StubAiRuntime aiRuntime = new FinanceTransactionRuntimeTestSupport.StubAiRuntime();
        FinanceTransactionRuntimeService runtimeService = new FinanceTransactionRuntimeService(
            FinanceTransactionRuntimeConfig.disabled(java.time.Duration.ofHours(24)),
            aiRuntime,
            aiRuntime,
            Clock.systemUTC()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, runtimeService::getTransactionService);

        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void disablesSharedRateLimitingWhenPersistenceIsUnavailable() {
        FinanceTransactionRuntimeConfig config = FinanceTransactionRuntimeConfig.fromContext(
            new com.ghatana.finance.ai.FinanceAiPersistenceTestSupport.RuntimeConfigKernelContext(java.util.Map.of(
                "finance.transaction.rate-limit.shared.enabled", true,
                "finance.transaction.idempotency.ttl-hours", 24L,
                "finance.transaction.rate-limit.max-requests-per-minute", 1,
                "finance.transaction.rate-limit.window-seconds", 60L
            ))
        );

        assertFalse(config.isPersistenceEnabled());
        assertFalse(config.isSharedRateLimitEnabled());
    }
}
