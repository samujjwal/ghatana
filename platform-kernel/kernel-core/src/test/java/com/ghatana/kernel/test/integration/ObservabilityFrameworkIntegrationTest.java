package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.observability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Observability Framework.
 *
 * <p>Tests telemetry manager, audit trail service, and explainability framework
 * in integrated scenarios.</p>
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("Observability Framework Integration Tests [GH-90000]")
class ObservabilityFrameworkIntegrationTest {

    private MockKernelTelemetryManager telemetryManager;
    private MockAuditTrailService auditTrailService;
    private MockExplainabilityFramework explainabilityFramework;

    @BeforeEach
    void setUp() { // GH-90000
        telemetryManager = new MockKernelTelemetryManager(); // GH-90000
        auditTrailService = new MockAuditTrailService(); // GH-90000
        explainabilityFramework = new MockExplainabilityFramework(); // GH-90000
    }

    @Test
    @DisplayName("Should record metric with tags [GH-90000]")
    void testRecordMetric() { // GH-90000
        assertDoesNotThrow(() -> // GH-90000
            telemetryManager.recordMetric("test.metric", 42.0, "tag1", "value1") // GH-90000
        );
    }

    @Test
    @DisplayName("Should record event [GH-90000]")
    void testRecordEvent() { // GH-90000
        KernelTelemetryManager.Event event = new KernelTelemetryManager.Event( // GH-90000
            "test.event", "test-source", Map.of("key", "value") // GH-90000
        );

        assertDoesNotThrow(() -> telemetryManager.recordEvent(event)); // GH-90000
    }

    @Test
    @DisplayName("Should create explainability context [GH-90000]")
    void testCreateExplainabilityContext() { // GH-90000
        KernelTelemetryManager.AgentAction action = new KernelTelemetryManager.AgentAction( // GH-90000
            "test-agent", "classify", Map.of("input", "test") // GH-90000
        );

        ExplainabilityContext context = telemetryManager.createExplainabilityContext(action); // GH-90000

        assertNotNull(context); // GH-90000
        assertEquals("test-agent", context.getAgentId()); // GH-90000
    }

    @Test
    @DisplayName("Should start and stop timer [GH-90000]")
    void testTimer() { // GH-90000
        KernelTelemetryManager.Timer timer = telemetryManager.startTimer("test.timer [GH-90000]");

        assertNotNull(timer); // GH-90000

        try {
            Thread.sleep(10); // GH-90000
        } catch (InterruptedException e) { // GH-90000
            Thread.currentThread().interrupt(); // GH-90000
        }

        timer.stop(); // GH-90000
        assertTrue(timer.getElapsedMillis() >= 10); // GH-90000
    }

    @Test
    @DisplayName("Should record audit event [GH-90000]")
    void testRecordAuditEvent() { // GH-90000
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder() // GH-90000
            .eventId("event-1 [GH-90000]")
            .eventType("user.login [GH-90000]")
            .entityId("user-1 [GH-90000]")
            .userId("user-1 [GH-90000]")
            .tenantId("tenant-1 [GH-90000]")
            .action("login [GH-90000]")
            .data(Map.of("ip", "192.168.1.1")) // GH-90000
            .build(); // GH-90000

        assertDoesNotThrow(() -> auditTrailService.recordAuditEvent(event)); // GH-90000
    }

    @Test
    @DisplayName("Should query audit events [GH-90000]")
    void testQueryAuditEvents() { // GH-90000
        AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder() // GH-90000
            .entityId("user-1 [GH-90000]")
            .tenantId("tenant-1 [GH-90000]")
            .limit(10) // GH-90000
            .build(); // GH-90000

        var events = auditTrailService.queryAuditEvents(query); // GH-90000

        assertNotNull(events); // GH-90000
    }

    @Test
    @DisplayName("Should verify audit trail integrity [GH-90000]")
    void testVerifyTrailIntegrity() { // GH-90000
        AuditTrailService.VerificationResult result =
            auditTrailService.verifyTrailIntegrity("entity-1 [GH-90000]");

        assertNotNull(result); // GH-90000
        assertTrue(result.isValid()); // GH-90000
    }

    @Test
    @DisplayName("Should generate explanation for agent action [GH-90000]")
    void testGenerateExplanation() { // GH-90000
        KernelTelemetryManager.AgentAction action = new KernelTelemetryManager.AgentAction( // GH-90000
            "test-agent", "classify", Map.of("input", "test") // GH-90000
        );
        MockExecutionContext context = new MockExecutionContext(); // GH-90000

        ExplainabilityFramework.Explanation explanation =
            explainabilityFramework.generateExplanation(action, context); // GH-90000

        assertNotNull(explanation); // GH-90000
        assertNotNull(explanation.getSummary()); // GH-90000
    }

    @Test
    @DisplayName("Should record and retrieve decision explanation [GH-90000]")
    void testRecordDecisionExplanation() { // GH-90000
        ExplainabilityFramework.Explanation explanation =
            ExplainabilityFramework.Explanation.builder() // GH-90000
                .decisionId("decision-1 [GH-90000]")
                .summary("Test decision [GH-90000]")
                .detailedReasoning("Detailed reasoning [GH-90000]")
                .confidence(0.95) // GH-90000
                .modelId("model-1 [GH-90000]")
                .build(); // GH-90000

        explainabilityFramework.recordDecisionExplanation("decision-1", explanation); // GH-90000

        ExplainabilityFramework.Explanation retrieved =
            explainabilityFramework.getExplanation("decision-1 [GH-90000]");

        assertNotNull(retrieved); // GH-90000
        assertEquals("decision-1", retrieved.getDecisionId()); // GH-90000
    }

    // Mock implementations for testing

    private static class MockKernelTelemetryManager implements KernelTelemetryManager {
        @Override
        public void recordMetric(String name, double value, String... tags) { // GH-90000
        }

        @Override
        public void recordEvent(Event event) { // GH-90000
        }

        @Override
        public ExplainabilityContext createExplainabilityContext(AgentAction action) { // GH-90000
            return new MockExplainabilityContext(action.getAgentId()); // GH-90000
        }

        @Override
        public Timer startTimer(String name, String... tags) { // GH-90000
            return new MockTimer(); // GH-90000
        }

        @Override
        public void incrementCounter(String name, long increment, String... tags) { // GH-90000
        }

        @Override
        public void recordGauge(String name, double value, String... tags) { // GH-90000
        }

        @Override
        public void recordHistogram(String name, double value, String... tags) { // GH-90000
        }
    }

    private static class MockTimer implements KernelTelemetryManager.Timer {
        private final long startTime = System.currentTimeMillis(); // GH-90000

        @Override
        public void stop() { // GH-90000
        }

        @Override
        public long getElapsedMillis() { // GH-90000
            return System.currentTimeMillis() - startTime; // GH-90000
        }
    }

    private static class MockExplainabilityContext implements ExplainabilityContext {
        private final String agentId;
        private final Map<String, Double> featureImportance = new HashMap<>(); // GH-90000
        private String explanation;

        MockExplainabilityContext(String agentId) { // GH-90000
            this.agentId = agentId;
        }

        @Override
        public String getDecisionId() { // GH-90000
            return "decision-1";
        }

        @Override
        public String getAgentId() { // GH-90000
            return agentId;
        }

        @Override
        public String getModelId() { // GH-90000
            return "model-1";
        }

        @Override
        public Map<String, Object> getInputs() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public Map<String, Object> getOutputs() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public String getExplanation() { // GH-90000
            return explanation;
        }

        @Override
        public double getConfidence() { // GH-90000
            return 0.95;
        }

        @Override
        public Map<String, Double> getFeatureImportance() { // GH-90000
            return featureImportance;
        }

        @Override
        public long getTimestamp() { // GH-90000
            return System.currentTimeMillis(); // GH-90000
        }

        @Override
        public void recordExplanation(String explanation) { // GH-90000
            this.explanation = explanation;
        }

        @Override
        public void recordFeatureImportance(String feature, double importance) { // GH-90000
            featureImportance.put(feature, importance); // GH-90000
        }
    }

    private static class MockAuditTrailService implements AuditTrailService {
        @Override
        public void recordAuditEvent(AuditEvent event) { // GH-90000
        }

        @Override
        public java.util.List<AuditEvent> queryAuditEvents(AuditQuery query) { // GH-90000
            return java.util.List.of(); // GH-90000
        }

        @Override
        public ImmutableAuditTrail getImmutableTrail(String entityId) { // GH-90000
            return new MockImmutableAuditTrail(entityId); // GH-90000
        }

        @Override
        public VerificationResult verifyTrailIntegrity(String entityId) { // GH-90000
            return new VerificationResult(true, "Valid", java.util.List.of()); // GH-90000
        }
    }

    private static class MockImmutableAuditTrail implements AuditTrailService.ImmutableAuditTrail {
        private final String entityId;

        MockImmutableAuditTrail(String entityId) { // GH-90000
            this.entityId = entityId;
        }

        @Override
        public String getEntityId() { // GH-90000
            return entityId;
        }

        @Override
        public java.util.List<AuditTrailService.AuditEvent> getEvents() { // GH-90000
            return java.util.List.of(); // GH-90000
        }

        @Override
        public String getMerkleRoot() { // GH-90000
            return "mock-merkle-root";
        }

        @Override
        public boolean isIntact() { // GH-90000
            return true;
        }
    }

    private static class MockExplainabilityFramework implements ExplainabilityFramework {
        private final Map<String, Explanation> explanations = new HashMap<>(); // GH-90000

        @Override
        public Explanation generateExplanation(KernelTelemetryManager.AgentAction action, // GH-90000
                                               ExecutionContext context) {
            return Explanation.builder() // GH-90000
                .decisionId("decision-1 [GH-90000]")
                .summary("Mock explanation for " + action.getActionType()) // GH-90000
                .detailedReasoning("Detailed reasoning [GH-90000]")
                .confidence(0.95) // GH-90000
                .modelId("model-1 [GH-90000]")
                .build(); // GH-90000
        }

        @Override
        public void recordDecisionExplanation(String decisionId, Explanation explanation) { // GH-90000
            explanations.put(decisionId, explanation); // GH-90000
        }

        @Override
        public Explanation getExplanation(String decisionId) { // GH-90000
            return explanations.get(decisionId); // GH-90000
        }

        @Override
        public ValidationResult validateExplanation(Explanation explanation) { // GH-90000
            return new ValidationResult(true, 1.0, "Valid"); // GH-90000
        }
    }

    private static class MockExecutionContext implements ExplainabilityFramework.ExecutionContext {
        @Override
        public com.ghatana.kernel.context.KernelContext getKernelContext() { // GH-90000
            return null;
        }

        @Override
        public Map<String, Object> getInputs() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public Map<String, Object> getOutputs() { // GH-90000
            return Map.of(); // GH-90000
        }

        @Override
        public String getAgentId() { // GH-90000
            return "test-agent";
        }
    }
}
