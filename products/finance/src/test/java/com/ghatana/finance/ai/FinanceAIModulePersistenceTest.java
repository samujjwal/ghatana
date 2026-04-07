package com.ghatana.finance.ai;

import com.ghatana.agent.spi.AgentLogicProvider;
import com.ghatana.kernel.ai.ModelGovernanceService;
import io.activej.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies persistence-aware finance AI module wiring
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
class FinanceAIModulePersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        postgres = FinanceAiPersistenceTestSupport.startPostgres();
        dataSource = FinanceAiPersistenceTestSupport.createDataSource(postgres, "finance-ai-module-persistence-test");
    }

    @AfterEach
    void tearDown() {
        FinanceAiPersistenceTestSupport.closeDataSource(dataSource);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void wiresPersistentRepositoriesAndGovernanceIntoInjector() {
        Injector injector = Injector.of(FinanceAIModule.create(dataSource));

        ModelRepository modelRepository = injector.getInstance(ModelRepository.class);
        ModelGovernanceService governance = injector.getInstance(ModelGovernanceService.class);
        AgentLogicProvider provider = injector.getInstance(AgentLogicProvider.class);

        governance.registerModel(new ModelGovernanceService.ModelRegistration(
            "fraud-detection-v4",
            "Module Wired Detector",
            "4.0.0",
            "classification",
            Map.of("endpoint", "http://fraud-model.internal/predict")
        ));

        ModelRecord persisted = modelRepository.findByModelId("fraud-detection-v4");

        assertNotNull(persisted);
        assertEquals("Module Wired Detector", persisted.getName());
        assertTrue(provider.getSupportedRefs().contains("finance:fraud-detection"));
    }
}