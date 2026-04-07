package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAiPersistenceTestSupport;
import com.ghatana.kernel.ai.ModelGovernanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies finance AI runtime persistence across managed runtime restarts
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAiRuntimeServicePersistenceTest {

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

    @Test
    void persistsGovernanceMetadataAcrossRuntimeRestarts() {
        FinanceAiRuntimeConfig config = FinanceAiPersistenceTestSupport.createRuntimeConfig(postgres, "finance-ai-runtime-persistence");

        FinanceAiRuntimeService firstRuntime = new FinanceAiRuntimeService(config);
        firstRuntime.start();
        firstRuntime.registerModel(new ModelGovernanceService.ModelRegistration(
            "fraud-detection-v10",
            "Finance Fraud Model",
            "10.0.0",
            "classification",
            Map.of("jurisdiction", "EU", "owner", "finance-risk")
        ));
        firstRuntime.stop();

        FinanceAiRuntimeService secondRuntime = new FinanceAiRuntimeService(config);
        secondRuntime.start();

        ModelGovernanceService.ModelMetadata metadata = secondRuntime.getModelMetadata("fraud-detection-v10");

        assertNotNull(metadata);
        assertEquals("10.0.0", metadata.getVersion());
        assertEquals("EU", metadata.getAttributes().get("jurisdiction"));
        assertNotNull(secondRuntime.getAgent("finance.fraud-detection"));
        assertTrue(secondRuntime.isPersistenceEnabled());

        secondRuntime.stop();
    }
}