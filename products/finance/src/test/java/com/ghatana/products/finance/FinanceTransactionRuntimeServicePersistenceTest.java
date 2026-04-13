package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import com.ghatana.finance.service.TransactionResult;
import com.ghatana.platform.core.exception.RateLimitExceededException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies Finance transaction runtime preserves idempotency across managed runtime restarts
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceTransactionRuntimeServicePersistenceTest extends EventloopTestBase {

    private PostgreSQLContainer<?> postgres;

    /**
     * Allow extra time for Testcontainers + JDBC initialisation within the event loop.
     */
    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(60);
    }

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
        runPromise(firstRuntime::start);

        TransactionResult firstResult = firstRuntime.getTransactionService().processTransaction(
            FinanceTransactionRuntimeTestSupport.createTransaction("txn-runtime-persist-1")
        );
        runPromise(firstRuntime::stop);

        FinanceTransactionRuntimeService secondRuntime = new FinanceTransactionRuntimeService(
            config,
            aiRuntime,
            aiRuntime
        );
        runPromise(secondRuntime::start);

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

        runPromise(secondRuntime::stop);
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
        runPromise(firstRuntime::start);

        assertEquals(
            "APPROVED",
            firstRuntime.getTransactionService()
                .processTransaction(FinanceTransactionRuntimeTestSupport.createTransaction("txn-rate-limit-1"))
                .getStatus()
        );
        runPromise(firstRuntime::stop);

        FinanceTransactionRuntimeService secondRuntime = new FinanceTransactionRuntimeService(config, aiRuntime, aiRuntime);
        runPromise(secondRuntime::start);

        RateLimitExceededException exception = org.junit.jupiter.api.Assertions.assertThrows(
            RateLimitExceededException.class,
            () -> secondRuntime.getTransactionService().processTransaction(
                FinanceTransactionRuntimeTestSupport.createTransaction("txn-rate-limit-2")
            )
        );

        assertTrue(exception.getMessage().contains("tenant-1"));
        assertTrue(secondRuntime.isSharedRateLimitingEnabled());

        runPromise(secondRuntime::stop);
    }
}
