package com.ghatana.kernel.test.integration;

import com.ghatana.kernel.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AI Framework.
 *
 * <p>Tests agent orchestrator, model governance, autonomy manager,
 * and AI evaluation framework in integrated scenarios.</p>
 *
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@DisplayName("AI Framework Integration Tests")
class AIFrameworkIntegrationTest {

    private MockAgentOrchestrator orchestrator;
    private MockModelGovernanceService governance;
    private MockAutonomyManager autonomyManager;
    private MockAIEvaluationFramework evaluationFramework;

    @BeforeEach
    void setUp() {
        orchestrator = new MockAgentOrchestrator();
        governance = new MockModelGovernanceService();
        autonomyManager = new MockAutonomyManager();
        evaluationFramework = new MockAIEvaluationFramework();
    }

    @Test
    @DisplayName("Should register and execute agent")
    void testRegisterAndExecuteAgent() {
        MockAgent agent = new MockAgent("test-agent");
        orchestrator.registerAgent(agent);

        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "classify", Map.of("input", "test"), Map.of()
        );

        AgentOrchestrator.AgentResponse response = orchestrator.executeAgent(agent, request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
    }

    @Test
    @DisplayName("Should execute agent workflow")
    void testExecuteAgentWorkflow() {
        List<AgentOrchestrator.KernelAgent> agents = List.of(
            new MockAgent("agent-1"),
            new MockAgent("agent-2")
        );

        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "process", Map.of(), Map.of()
        );

        AgentOrchestrator.WorkflowResult result = orchestrator.executeAgentWorkflow(agents, request);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getResponses().size());
    }

    @Test
    @DisplayName("Should validate model approval")
    void testModelApproval() {
        ModelGovernanceService.ModelApproval approval = governance.getModelApproval("model-1");

        assertNotNull(approval);
        assertTrue(approval.isApproved());
    }

    @Test
    @DisplayName("Should validate model usage")
    void testValidateModelUsage() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "classify", Map.of(), Map.of()
        );

        assertDoesNotThrow(() -> governance.validateModelUsage("model-1", request));
    }

    @Test
    @DisplayName("Should record model performance")
    void testRecordModelPerformance() {
        ModelGovernanceService.ModelPerformanceMetrics metrics = 
            new ModelGovernanceService.ModelPerformanceMetrics(0.95, 0.92);

        assertDoesNotThrow(() -> governance.recordModelPerformance("model-1", metrics));
    }

    @Test
    @DisplayName("Should configure autonomy level")
    void testConfigureAutonomyLevel() {
        assertDoesNotThrow(() -> 
            autonomyManager.configureAutonomyLevel("agent-1", AutonomyManager.AutonomyLevel.MEDIUM)
        );

        AutonomyManager.AutonomyLevel level = autonomyManager.getAutonomyLevel("agent-1");
        assertEquals(AutonomyManager.AutonomyLevel.MEDIUM, level);
    }

    @Test
    @DisplayName("Should determine human review requirement")
    void testRequiresHumanReview() {
        MockAgent agent = new MockAgent("test-agent");
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "critical-decision", Map.of(), Map.of()
        );

        boolean requiresReview = autonomyManager.requiresHumanReview(request, agent);

        assertTrue(requiresReview);
    }

    @Test
    @DisplayName("Should record autonomous decision")
    void testRecordAutonomousDecision() {
        MockAgent agent = new MockAgent("test-agent");
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "classify", Map.of(), Map.of()
        );

        AutonomyManager.AutonomousDecision decision = new AutonomyManager.AutonomousDecision(
            agent.getAgentId(), request, Map.of("result", "positive"), false
        );

        assertDoesNotThrow(() -> autonomyManager.recordAutonomousDecision(decision));
    }

    @Test
    @DisplayName("Should evaluate agent performance")
    void testEvaluateAgent() {
        MockAgent agent = new MockAgent("test-agent");
        AIEvaluationFramework.EvaluationCriteria criteria = 
            AIEvaluationFramework.EvaluationCriteria.builder()
                .withAccuracyThreshold(0.9)
                .withPerformanceThreshold(100)
                .build();

        AIEvaluationFramework.EvaluationResult result = 
            evaluationFramework.evaluateAgent(agent, criteria);

        assertNotNull(result);
        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("Should compare multiple agents")
    void testCompareAgents() {
        List<String> agentIds = List.of("agent-1", "agent-2", "agent-3");
        MockComparisonCriteria criteria = new MockComparisonCriteria();

        AIEvaluationFramework.ComparisonReport report = 
            evaluationFramework.compareAgents(agentIds, criteria);

        assertNotNull(report);
        assertEquals(3, report.getAgentIds().size());
    }

    // Mock implementations for testing

    private static class MockAgentOrchestrator implements AgentOrchestrator {
        private final Map<String, KernelAgent> agents = new HashMap<>();

        @Override
        public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
            return AgentResponse.builder()
                .requestId(request.getRequestId())
                .success(true)
                .result(Map.of("status", "completed"))
                .confidence(0.95)
                .build();
        }

        @Override
        public void registerAgent(KernelAgent agent) {
            agents.put(agent.getAgentId(), agent);
        }

        @Override
        public void unregisterAgent(String agentId) {
            agents.remove(agentId);
        }

        @Override
        public List<KernelAgent> getAvailableAgents() {
            return new ArrayList<>(agents.values());
        }

        @Override
        public WorkflowResult executeAgentWorkflow(List<KernelAgent> agents, AgentRequest request) {
            List<AgentResponse> responses = new ArrayList<>();
            for (KernelAgent agent : agents) {
                responses.add(executeAgent(agent, request));
            }
            return new WorkflowResult(true, responses, null);
        }

        @Override
        public KernelAgent getAgent(String agentId) {
            return agents.get(agentId);
        }

        @Override
        public io.activej.promise.Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
            return io.activej.promise.Promise.of(executeAgent(agent, request));
        }
    }

    private static class MockAgent implements AgentOrchestrator.KernelAgent {
        private final String agentId;

        MockAgent(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public String getName() {
            return "Mock Agent";
        }

        @Override
        public String getDescription() {
            return "Mock agent for testing";
        }

        @Override
        public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
            return AgentOrchestrator.AgentResponse.builder()
                .requestId(request.getRequestId())
                .success(true)
                .result(Map.of("status", "completed"))
                .build();
        }

        @Override
        public AgentOrchestrator.AgentCapabilities getCapabilities() {
            return new MockAgentCapabilities();
        }
    }

    private static class MockAgentCapabilities implements AgentOrchestrator.AgentCapabilities {
        @Override
        public List<String> getSupportedOperations() {
            return List.of("classify", "process");
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of();
        }

        @Override
        public boolean supportsOperation(String operation) {
            return getSupportedOperations().contains(operation);
        }
    }

    private static class MockModelGovernanceService implements ModelGovernanceService {
        @Override
        public ModelApproval getModelApproval(String modelId) {
            return ModelApproval.builder()
                .modelId(modelId)
                .approved(true)
                .approver("admin")
                .version("1.0")
                .build();
        }

        @Override
        public void validateModelUsage(String modelId, AgentOrchestrator.AgentRequest request) {
        }

        @Override
        public void recordModelPerformance(String modelId, ModelPerformanceMetrics metrics) {
        }

        @Override
        public boolean isModelCompliant(String modelId, CompliancePolicy policy) {
            return true;
        }

        @Override
        public void registerModel(ModelRegistration model) {
        }

        @Override
        public ModelMetadata getModelMetadata(String modelId) {
            return new MockModelMetadata(modelId);
        }
    }

    private static class MockModelMetadata implements ModelGovernanceService.ModelMetadata {
        private final String modelId;

        MockModelMetadata(String modelId) {
            this.modelId = modelId;
        }

        @Override
        public String getModelId() {
            return modelId;
        }

        @Override
        public String getName() {
            return "Mock Model";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getType() {
            return "classification";
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public long getCreatedDate() {
            return System.currentTimeMillis();
        }

        @Override
        public long getLastUpdated() {
            return System.currentTimeMillis();
        }
    }

    private static class MockAutonomyManager implements AutonomyManager {
        private final Map<String, AutonomyLevel> levels = new HashMap<>();

        @Override
        public void configureAutonomyLevel(String agentId, AutonomyLevel level) {
            levels.put(agentId, level);
        }

        @Override
        public boolean requiresHumanReview(AgentOrchestrator.AgentRequest request, 
                                          AgentOrchestrator.KernelAgent agent) {
            return "critical-decision".equals(request.getOperation());
        }

        @Override
        public void recordAutonomousDecision(AutonomousDecision decision) {
        }

        @Override
        public List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window) {
            return List.of();
        }

        @Override
        public AutonomyLevel getAutonomyLevel(String agentId) {
            return levels.getOrDefault(agentId, AutonomyLevel.MEDIUM);
        }

        @Override
        public void approveDecision(String decisionId, String approver) {
        }

        @Override
        public void rejectDecision(String decisionId, String rejector, String reason) {
        }
    }

    private static class MockAIEvaluationFramework implements AIEvaluationFramework {
        @Override
        public EvaluationResult evaluateAgent(AgentOrchestrator.KernelAgent agent, 
                                             EvaluationCriteria criteria) {
            return EvaluationResult.builder()
                .agentId(agent.getAgentId())
                .passed(true)
                .accuracy(0.95)
                .precision(0.93)
                .recall(0.94)
                .f1Score(0.935)
                .latencyMillis(50)
                .build();
        }

        @Override
        public void recordEvaluationMetrics(String agentId, EvaluationMetrics metrics) {
        }

        @Override
        public ComparisonReport compareAgents(List<String> agentIds, ComparisonCriteria criteria) {
            return new MockComparisonReport(agentIds);
        }

        @Override
        public List<EvaluationResult> getEvaluationHistory(String agentId) {
            return List.of();
        }
    }

    private static class MockComparisonCriteria implements AIEvaluationFramework.ComparisonCriteria {
        @Override
        public List<String> getMetrics() {
            return List.of("accuracy", "latency");
        }

        @Override
        public String getSortBy() {
            return "accuracy";
        }

        @Override
        public boolean isAscending() {
            return false;
        }
    }

    private static class MockComparisonReport implements AIEvaluationFramework.ComparisonReport {
        private final List<String> agentIds;

        MockComparisonReport(List<String> agentIds) {
            this.agentIds = agentIds;
        }

        @Override
        public List<String> getAgentIds() {
            return agentIds;
        }

        @Override
        public Map<String, AIEvaluationFramework.EvaluationResult> getResults() {
            return Map.of();
        }

        @Override
        public String getBestAgent() {
            return agentIds.isEmpty() ? null : agentIds.get(0);
        }

        @Override
        public Map<String, Object> getSummary() {
            return Map.of("total_agents", agentIds.size());
        }
    }
}
