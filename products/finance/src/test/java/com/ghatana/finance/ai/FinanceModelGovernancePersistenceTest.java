package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.ModelGovernanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @doc.type class
 * @doc.purpose Verifies finance governance persists registered model metadata through the repository layer
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class FinanceModelGovernancePersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-governance-persistence-test");
    }

    @AfterEach
    void tearDown() {
        FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistsRegisteredModelMetadataAcrossGovernanceInstances() {
        FinanceModelGovernanceImpl first = new FinanceModelGovernanceImpl(
            new ModelApprovalRepository(dataSource),
            new ModelPerformanceRepository(dataSource),
            new ModelRepository(dataSource),
            new AlertService((title, message) -> { })
        );

        first.registerModel(new ModelGovernanceService.ModelRegistration(
            "fraud-detection-v3",
            "Persistent Fraud Detector",
            "3.0.0",
            "classification",
            Map.of("accuracy", 0.97, "region", "global")
        ));

        FinanceModelGovernanceImpl second = new FinanceModelGovernanceImpl(
            new ModelApprovalRepository(dataSource),
            new ModelPerformanceRepository(dataSource),
            new ModelRepository(dataSource),
            new AlertService((title, message) -> { })
        );

        ModelGovernanceService.ModelMetadata metadata = second.getModelMetadata("fraud-detection-v3");

        assertNotNull(metadata);
        assertEquals("Persistent Fraud Detector", metadata.getName());
        assertEquals("global", metadata.getAttributes().get("region"));
    }
}
