package com.ghatana.finance.ai;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.migration.BaseAgentAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Tests finance provider wiring for fraud and risk agent factories
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAgentLogicProviderTest {

    @Test
    void exposesFraudAndRiskFactories() {
        FinanceAgentLogicProvider provider = new FinanceAgentLogicProvider(new AlertService((title, message) -> { }));

        assertTrue(provider.getSupportedRefs().contains("finance:fraud-detection"));
        assertTrue(provider.getSupportedRefs().contains("finance:risk-assessment"));
    }

    @Test
    void createsFraudAgentAdapter() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("endpoint", "http://fraud-model.internal/predict"));
        repository.save(model);

        FinanceAgentLogicProvider provider = new FinanceAgentLogicProvider(
            new AlertService((title, message) -> { }),
            repository
        );
        AgentConfig config = AgentConfig.builder()
            .agentId("fraud-agent")
            .type(AgentType.HYBRID)
            .implementationRef("finance:fraud-detection")
            .build();

        TypedAgent<?, ?> agent = provider.createAgent("finance:fraud-detection", config);

        assertTrue(agent instanceof BaseAgentAdapter<?, ?>);
        assertEquals("fraud-detection-agent", agent.descriptor().getAgentId());
    }

    @Test
    void retainsSharedModelRepositoryReference() throws Exception {
        ModelRepository repository = new ModelRepository();
        FinanceAgentLogicProvider provider = new FinanceAgentLogicProvider(
            new AlertService((title, message) -> { }),
            repository
        );

        Field field = FinanceAgentLogicProvider.class.getDeclaredField("modelRepository");
        field.setAccessible(true);

        assertEquals(repository, field.get(provider));
    }

    @Test
    void rejectsUnknownFactoryReference() {
        FinanceAgentLogicProvider provider = new FinanceAgentLogicProvider(new AlertService((title, message) -> { }));
        AgentConfig config = AgentConfig.builder()
            .agentId("unknown-agent")
            .type(AgentType.HYBRID)
            .implementationRef("finance:unknown")
            .build();

        assertThrows(IllegalArgumentException.class, () -> provider.createAgent("finance:unknown", config));
    }
}
