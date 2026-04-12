package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.platform.core.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance transaction runtime preserves idempotency across managed runtime restarts
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceTransactionRuntimeServicePersistenceTest {

    private PostgreSQLContainer<?> postgres;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
    }

    @AfterEach
    void tearDown() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static void startSync(FinanceTransactionRuntimeService service) {
        try {
            service.start().toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start FinanceTransactionRuntimeService", e);
        }
    }

    @Test
    void preservesIdempotentReplayAcrossRuntimeRestarts() {
        FinanceTransactionRuntimeConfig config = FinanceAiPersistenceTestSupport.createTransactionRuntimeConfig(
            postgres,
            "finance-txn-runtime"
        );
        FinanceTransactionRuntimeTestSupport.StubAiRuntime aiRuntime = new FinanceTransactionRuntimeTestSupport.StubAiRuntime();
        FinanceTransactionRuntimeService firstRuntime = new FinanceTransactionRuntimeService(
            config,
            aiRuntime,
            aiRuntime
        );
        startSync(firstRuntime);

        TransactionResult firstResult = firstRuntime.getTransactionService().processTransaction(
            FinanceTransactionRuntimeTestSupport.createTransaction("txn-runtime-persist-1")
        );
        firstRuntime.stop();

        FinanceTransactionRuntimeService secondRuntime = new FinanceTransactionRuntimeService(
            config,
            aiRuntime,
            aiRuntime
        );
        startSync(secondRuntime);

        TransactionResult replayResult = secondRuntime.getTransactionService().processTransaction(
            FinanceTransactionRuntimeTestSupport.createTransaction("txn-runtime-persist-1")
        );

        assertNotNull(firstResult);
        assertNotNull(replayResult);
        assertEquals(firstResult.getStatus(), replayResult.getStatus());
        assertEquals(firstResult.getMessage(), replayResult.getMessage());
        assertEquals(firstResult.getMetadata(), replayResult.getMetadata());
        assertEquals(1, aiRuntime.executions());
        assertTrue(secondRuntime.isPersistenceEnabled());

        secondRuntime.stop();
    }

    @Test
    void preservesSharedRateLimitAcrossRuntimeRestarts() {
        FinanceTransactionRuntimeConfig config = FinanceAiPersistenceTestSupport.createTransactionRuntimeConfig(
            postgres,
            "finance-txn-rate-limit-runtime",
            24L,
            1,
            true
        );
        FinanceTransactionRuntimeTestSupport.StubAiRuntime aiRuntime = new FinanceTransactionRuntimeTestSupport.StubAiRuntime();
        FinanceTransactionRuntimeService firstRuntime = new FinanceTransactionRuntimeService(config, aiRuntime, aiRuntime);
        startSync(firstRuntime);

        assertEquals(
            "APPROVED",
            firstRuntime.getTransactionService()
                .processTransaction(FinanceTransactionRuntimeTestSupport.createTransaction("txn-rate-limit-1"))
                .getStatus()
        );
        firstRuntime.stop();

        FinanceTransactionRuntimeService secondRuntime = new FinanceTransactionRuntimeService(config, aiRuntime, aiRuntime);
        startSync(secondRuntime);

        RateLimitExceededException exception = org.junit.jupiter.api.Assertions.assertThrows(
            RateLimitExceededException.class,
            () -> secondRuntime.getTransactionService().processTransaction(
                FinanceTransactionRuntimeTestSupport.createTransaction("txn-rate-limit-2")
            )
        );

        assertTrue(exception.getMessage().contains("tenant-1"));
        assertTrue(secondRuntime.isSharedRateLimitingEnabled());

        secondRuntime.stop();
    }
}
