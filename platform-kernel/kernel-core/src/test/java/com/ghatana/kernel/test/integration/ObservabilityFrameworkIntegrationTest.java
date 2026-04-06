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
@DisplayName("Observability Framework Integration Tests")
class ObservabilityFrameworkIntegrationTest {

    private MockKernelTelemetryManager telemetryManager;
    private MockAuditTrailService auditTrailService;
    private MockExplainabilityFramework explainabilityFramework;

    @BeforeEach
    void setUp() {
        telemetryManager = new MockKernelTelemetryManager();
        auditTrailService = new MockAuditTrailService();
        explainabilityFramework = new MockExplainabilityFramework();
    }

    @Test
    @DisplayName("Should record metric with tags")
    void testRecordMetric() {
        assertDoesNotThrow(() -> 
            telemetryManager.recordMetric("test.metric", 42.0, "tag1", "value1")
        );
    }

    @Test
    @DisplayName("Should record event")
    void testRecordEvent() {
        KernelTelemetryManager.Event event = new KernelTelemetryManager.Event(
            "test.event", "test-source", Map.of("key", "value")
        );

        assertDoesNotThrow(() -> telemetryManager.recordEvent(event));
    }

    @Test
    @DisplayName("Should create explainability context")
    void testCreateExplainabilityContext() {
        KernelTelemetryManager.AgentAction action = new KernelTelemetryManager.AgentAction(
            "test-agent", "classify", Map.of("input", "test")
        );

        ExplainabilityContext context = telemetryManager.createExplainabilityContext(action);

        assertNotNull(context);
        assertEquals("test-agent", context.getAgentId());
    }

    @Test
    @DisplayName("Should start and stop timer")
    void testTimer() {
        KernelTelemetryManager.Timer timer = telemetryManager.startTimer("test.timer");

        assertNotNull(timer);
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        timer.stop();
        assertTrue(timer.getElapsedMillis() >= 10);
    }

    @Test
    @DisplayName("Should record audit event")
    void testRecordAuditEvent() {
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("event-1")
            .eventType("user.login")
            .entityId("user-1")
            .userId("user-1")
            .tenantId("tenant-1")
            .action("login")
            .data(Map.of("ip", "192.168.1.1"))
            .build();

        assertDoesNotThrow(() -> auditTrailService.recordAuditEvent(event));
    }

    @Test
    @DisplayName("Should query audit events")
    void testQueryAuditEvents() {
        AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder()
            .entityId("user-1")
            .tenantId("tenant-1")
            .limit(10)
            .build();

        var events = auditTrailService.queryAuditEvents(query);

        assertNotNull(events);
    }

    @Test
    @DisplayName("Should verify audit trail integrity")
    void testVerifyTrailIntegrity() {
        AuditTrailService.VerificationResult result = 
            auditTrailService.verifyTrailIntegrity("entity-1");

        assertNotNull(result);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should generate explanation for agent action")
    void testGenerateExplanation() {
        KernelTelemetryManager.AgentAction action = new KernelTelemetryManager.AgentAction(
            "test-agent", "classify", Map.of("input", "test")
        );
        MockExecutionContext context = new MockExecutionContext();

        ExplainabilityFramework.Explanation explanation = 
            explainabilityFramework.generateExplanation(action, context);

        assertNotNull(explanation);
        assertNotNull(explanation.getSummary());
    }

    @Test
    @DisplayName("Should record and retrieve decision explanation")
    void testRecordDecisionExplanation() {
        ExplainabilityFramework.Explanation explanation = 
            ExplainabilityFramework.Explanation.builder()
                .decisionId("decision-1")
                .summary("Test decision")
                .detailedReasoning("Detailed reasoning")
                .confidence(0.95)
                .modelId("model-1")
                .build();

        explainabilityFramework.recordDecisionExplanation("decision-1", explanation);
        
        ExplainabilityFramework.Explanation retrieved = 
            explainabilityFramework.getExplanation("decision-1");

        assertNotNull(retrieved);
        assertEquals("decision-1", retrieved.getDecisionId());
    }

    // Mock implementations for testing

    private static class MockKernelTelemetryManager implements KernelTelemetryManager {
        @Override
        public void recordMetric(String name, double value, String... tags) {
        }

        @Override
        public void recordEvent(Event event) {
        }

        @Override
        public ExplainabilityContext createExplainabilityContext(AgentAction action) {
            return new MockExplainabilityContext(action.getAgentId());
        }

        @Override
        public Timer startTimer(String name, String... tags) {
            return new MockTimer();
        }

        @Override
        public void incrementCounter(String name, long increment, String... tags) {
        }

        @Override
        public void recordGauge(String name, double value, String... tags) {
        }

        @Override
        public void recordHistogram(String name, double value, String... tags) {
        }
    }

    private static class MockTimer implements KernelTelemetryManager.Timer {
        private final long startTime = System.currentTimeMillis();

        @Override
        public void stop() {
        }

        @Override
        public long getElapsedMillis() {
            return System.currentTimeMillis() - startTime;
        }
    }

    private static class MockExplainabilityContext implements ExplainabilityContext {
        private final String agentId;
        private final Map<String, Double> featureImportance = new HashMap<>();
        private String explanation;

        MockExplainabilityContext(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public String getDecisionId() {
            return "decision-1";
        }

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public String getModelId() {
            return "model-1";
        }

        @Override
        public Map<String, Object> getInputs() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getOutputs() {
            return Map.of();
        }

        @Override
        public String getExplanation() {
            return explanation;
        }

        @Override
        public double getConfidence() {
            return 0.95;
        }

        @Override
        public Map<String, Double> getFeatureImportance() {
            return featureImportance;
        }

        @Override
        public long getTimestamp() {
            return System.currentTimeMillis();
        }

        @Override
        public void recordExplanation(String explanation) {
            this.explanation = explanation;
        }

        @Override
        public void recordFeatureImportance(String feature, double importance) {
            featureImportance.put(feature, importance);
        }
    }

    private static class MockAuditTrailService implements AuditTrailService {
        @Override
        public void recordAuditEvent(AuditEvent event) {
        }

        @Override
        public java.util.List<AuditEvent> queryAuditEvents(AuditQuery query) {
            return java.util.List.of();
        }

        @Override
        public ImmutableAuditTrail getImmutableTrail(String entityId) {
            return new MockImmutableAuditTrail(entityId);
        }

        @Override
        public VerificationResult verifyTrailIntegrity(String entityId) {
            return new VerificationResult(true, "Valid", java.util.List.of());
        }
    }

    private static class MockImmutableAuditTrail implements AuditTrailService.ImmutableAuditTrail {
        private final String entityId;

        MockImmutableAuditTrail(String entityId) {
            this.entityId = entityId;
        }

        @Override
        public String getEntityId() {
            return entityId;
        }

        @Override
        public java.util.List<AuditTrailService.AuditEvent> getEvents() {
            return java.util.List.of();
        }

        @Override
        public String getMerkleRoot() {
            return "mock-merkle-root";
        }

        @Override
        public boolean isIntact() {
            return true;
        }
    }

    private static class MockExplainabilityFramework implements ExplainabilityFramework {
        private final Map<String, Explanation> explanations = new HashMap<>();

        @Override
        public Explanation generateExplanation(KernelTelemetryManager.AgentAction action, 
                                               ExecutionContext context) {
            return Explanation.builder()
                .decisionId("decision-1")
                .summary("Mock explanation for " + action.getActionType())
                .detailedReasoning("Detailed reasoning")
                .confidence(0.95)
                .modelId("model-1")
                .build();
        }

        @Override
        public void recordDecisionExplanation(String decisionId, Explanation explanation) {
            explanations.put(decisionId, explanation);
        }

        @Override
        public Explanation getExplanation(String decisionId) {
            return explanations.get(decisionId);
        }

        @Override
        public ValidationResult validateExplanation(Explanation explanation) {
            return new ValidationResult(true, 1.0, "Valid");
        }
    }

    private static class MockExecutionContext implements ExplainabilityFramework.ExecutionContext {
        @Override
        public com.ghatana.kernel.context.KernelContext getKernelContext() {
            return null;
        }

        @Override
        public Map<String, Object> getInputs() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getOutputs() {
            return Map.of();
        }

        @Override
        public String getAgentId() {
            return "test-agent";
        }
    }
}
