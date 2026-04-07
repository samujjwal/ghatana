package com.ghatana.products.finance;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.ModelGovernanceService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies in-memory finance AI runtime lifecycle and service delegation
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAiRuntimeServiceTest {

    @Test
    void startsInMemoryRuntimeAndRegistersFraudAgent() {
        FinanceAiRuntimeService runtimeService = new FinanceAiRuntimeService(FinanceAiRuntimeConfig.disabled());

        runtimeService.start();

        assertTrue(runtimeService.isHealthy());
        assertNotNull(runtimeService.getAgent("finance.fraud-detection"));

        runtimeService.registerModel(new ModelGovernanceService.ModelRegistration(
            "fraud-detection-v9",
            "Finance Fraud Model",
            "9.0.0",
            "classification",
            Map.of("jurisdiction", "US")
        ));

        assertEquals("fraud-detection-v9", runtimeService.getModelMetadata("fraud-detection-v9").getModelId());
    }

    @Test
    void rejectsUsageBeforeRuntimeStart() {
        FinanceAiRuntimeService runtimeService = new FinanceAiRuntimeService(FinanceAiRuntimeConfig.disabled());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            runtimeService.getAvailableAgents()
        );

        assertTrue(exception.getMessage().contains("not started"));
    }

    @Test
    void delegatesAutonomyChecksAfterStart() {
        FinanceAiRuntimeService runtimeService = new FinanceAiRuntimeService(FinanceAiRuntimeConfig.disabled());
        runtimeService.start();

        AgentOrchestrator.KernelAgent agent = runtimeService.getAgent("finance.fraud-detection");
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1",
            "detect_fraud",
            Map.of(),
            Map.of("amount", 150_000.0)
        );

        assertTrue(runtimeService.requiresHumanReview(request, agent));
    }
}