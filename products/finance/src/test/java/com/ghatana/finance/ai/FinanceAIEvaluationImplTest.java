package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AIEvaluationFramework;
import com.ghatana.kernel.ai.AgentOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Regression tests for FinanceAIEvaluationImpl interface compatibility and behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FinanceAIEvaluationImpl")
class FinanceAIEvaluationImplTest {

    @Test
    @DisplayName("evaluateAgent stores history entry")
    void evaluateAgentStoresHistory() {
        FinanceAIEvaluationImpl service = new FinanceAIEvaluationImpl();
        AgentOrchestrator.KernelAgent agent = new StubAgent("agent-A", 0.92, 0.88, 0.87);
        AIEvaluationFramework.EvaluationCriteria criteria = AIEvaluationFramework.EvaluationCriteria.builder()
            .withAccuracyThreshold(0.85)
            .withPerformanceThreshold(2000)
            .build();

        AIEvaluationFramework.EvaluationResult result = service.evaluateAgent(agent, criteria);

        assertTrue(result.isPassed());
        assertEquals("agent-A", result.getAgentId());
        assertFalse(service.getEvaluationHistory("agent-A").isEmpty());
    }

    @Test
    @DisplayName("recordEvaluationMetrics appends snapshot")
    void recordEvaluationMetricsAppendsSnapshot() {
        FinanceAIEvaluationImpl service = new FinanceAIEvaluationImpl();
        service.recordEvaluationMetrics("agent-M", new AIEvaluationFramework.EvaluationMetrics() {
            @Override
            public double getAccuracy() { return 0.9; }

            @Override
            public double getPrecision() { return 0.88; }

            @Override
            public double getRecall() { return 0.86; }

            @Override
            public long getLatency() { return 120L; }

            @Override
            public Map<String, Double> getCustomMetrics() { return Map.of("drift", 0.02); }
        });

        List<AIEvaluationFramework.EvaluationResult> history = service.getEvaluationHistory("agent-M");
        assertEquals(1, history.size());
        assertEquals(0.9, history.getFirst().getAccuracy(), 1e-6);
    }

    @Test
    @DisplayName("compareAgents returns best agent")
    void compareAgentsReturnsBest() {
        FinanceAIEvaluationImpl service = new FinanceAIEvaluationImpl();
        service.evaluateAgent(new StubAgent("agent-1", 0.80, 0.80, 0.80), defaultCriteria());
        service.evaluateAgent(new StubAgent("agent-2", 0.95, 0.91, 0.90), defaultCriteria());

        AIEvaluationFramework.ComparisonReport report = service.compareAgents(
            List.of("agent-1", "agent-2"),
            new AIEvaluationFramework.ComparisonCriteria() {
                @Override
                public List<String> getMetrics() { return List.of("accuracy"); }

                @Override
                public String getSortBy() { return "accuracy"; }

                @Override
                public boolean isAscending() { return false; }
            }
        );

        assertNotNull(report);
        assertEquals("agent-2", report.getBestAgent());
        assertEquals(2, report.getResults().size());
    }

    private static AIEvaluationFramework.EvaluationCriteria defaultCriteria() {
        return AIEvaluationFramework.EvaluationCriteria.builder()
            .withAccuracyThreshold(0.80)
            .withPerformanceThreshold(2000)
            .build();
    }

    private record StubAgent(String id, double accuracy, double precision, double recall)
        implements AgentOrchestrator.KernelAgent {

        @Override
        public String getAgentId() {
            return id;
        }

        @Override
        public String getName() {
            return "stub-" + id;
        }

        @Override
        public String getDescription() {
            return "stub";
        }

        @Override
        public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
            return AgentOrchestrator.AgentResponse.builder()
                .requestId(request.getRequestId())
                .success(true)
                .result(Map.of("ok", true))
                .confidence(accuracy)
                .metadata(Map.of(
                    "accuracy", accuracy,
                    "precision", precision,
                    "recall", recall
                ))
                .build();
        }

        @Override
        public AgentOrchestrator.AgentCapabilities getCapabilities() {
            return new AgentOrchestrator.AgentCapabilities() {
                @Override
                public List<String> getSupportedOperations() {
                    return List.of("evaluate");
                }

                @Override
                public Map<String, Object> getMetadata() {
                    return Map.of();
                }

                @Override
                public boolean supportsOperation(String operation) {
                    return "evaluate".equals(operation);
                }
            };
        }
    }
}
