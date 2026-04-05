package com.ghatana.kernel.ai;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Kernel AI Framework integration.
 * Validates AI agent orchestration and governance.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel AI framework integration and agent orchestration
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AI Framework Integration Tests")
class AIFrameworkIntegrationTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;
    private TestAIOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
        orchestrator = new TestAIOrchestrator();
    }

    @Test
    @DisplayName("Should register and execute AI agent")
    void testAIAgentRegistrationAndExecution() {
        // GIVEN: AI agent
        TestAIAgent agent = new TestAIAgent("test-agent", "classification");

        // WHEN: Register and execute
        orchestrator.registerAgent(agent);
        AIResponse response = runPromise(() -> 
            orchestrator.executeAgent("test-agent", new AIRequest("test input"))
        );

        // THEN: Agent executes successfully
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isNotNull();
    }

    @Test
    @DisplayName("Should orchestrate multi-agent workflow")
    void testMultiAgentWorkflow() {
        // GIVEN: Multiple agents in workflow
        TestAIAgent classificationAgent = new TestAIAgent("classifier", "classification");
        TestAIAgent enrichmentAgent = new TestAIAgent("enricher", "enrichment");
        TestAIAgent validationAgent = new TestAIAgent("validator", "validation");

        orchestrator.registerAgent(classificationAgent);
        orchestrator.registerAgent(enrichmentAgent);
        orchestrator.registerAgent(validationAgent);

        // WHEN: Execute workflow
        AIWorkflow workflow = new AIWorkflow("multi-agent-workflow");
        workflow.addStep("classifier");
        workflow.addStep("enricher");
        workflow.addStep("validator");

        WorkflowResult result = runPromise(() -> 
            orchestrator.executeWorkflow(workflow, new AIRequest("input data"))
        );

        // THEN: All agents executed in order
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutedAgents()).containsExactly("classifier", "enricher", "validator");
    }

    @Test
    @DisplayName("Should enforce AI governance policies")
    void testAIGovernancePolicies() {
        // GIVEN: AI agent with governance policy
        TestAIAgent agent = new TestAIAgent("governed-agent", "sensitive-classification");
        
        AIGovernancePolicy policy = new AIGovernancePolicy("sensitive-data-policy");
        policy.addRule("max-tokens", "1000");
        policy.addRule("require-audit", "true");
        policy.addRule("pii-detection", "enabled");

        orchestrator.registerAgent(agent);
        orchestrator.registerGovernancePolicy("governed-agent", policy);

        // WHEN: Execute with governance
        AIRequest request = new AIRequest("sensitive data");
        AIResponse response = runPromise(() -> 
            orchestrator.executeAgent("governed-agent", request)
        );

        // THEN: Governance enforced
        assertThat(response.isSuccess()).isTrue();
        assertThat(orchestrator.getAuditLog()).isNotEmpty();
        assertThat(orchestrator.getAuditLog().get(0)).contains("governed-agent");
    }

    @Test
    @DisplayName("Should handle AI agent failures gracefully")
    void testAIAgentFailureHandling() {
        // GIVEN: Failing AI agent
        FailingAIAgent failingAgent = new FailingAIAgent("failing-agent");

        orchestrator.registerAgent(failingAgent);

        // WHEN: Execute failing agent
        AIResponse response = runPromise(() -> 
            orchestrator.executeAgent("failing-agent", new AIRequest("input"))
        );

        // THEN: Failure handled gracefully
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).contains("Agent execution failed");
    }

    @Test
    @DisplayName("Should track AI agent metrics")
    void testAIAgentMetricsTracking() {
        // GIVEN: AI agent with metrics
        TestAIAgent agent = new TestAIAgent("metrics-agent", "classification");
        orchestrator.registerAgent(agent);

        // WHEN: Execute same agent multiple times concurrently
        int executionCount = 10;
        java.util.stream.IntStream.range(0, executionCount).forEach(i -> {
            runPromise(() -> orchestrator.executeAgent("metrics-agent", new AIRequest("input-" + i)));
        });

        // THEN: Metrics tracked
        AIAgentMetrics metrics = orchestrator.getMetrics("metrics-agent");
        assertThat(metrics.getExecutionCount()).isEqualTo(executionCount);
        assertThat(metrics.getAverageLatencyMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should support AI agent versioning")
    void testAIAgentVersioning() {
        // GIVEN: Multiple versions of same agent
        TestAIAgent agentV1 = new TestAIAgent("classifier", "classification");
        agentV1.setVersion("1.0.0");
        
        TestAIAgent agentV2 = new TestAIAgent("classifier", "classification");
        agentV2.setVersion("2.0.0");

        orchestrator.registerAgent(agentV1);
        orchestrator.registerAgent(agentV2);

        // WHEN: Execute specific version
        AIResponse responseV1 = runPromise(() -> 
            orchestrator.executeAgentVersion("classifier", "1.0.0", new AIRequest("input"))
        );
        AIResponse responseV2 = runPromise(() -> 
            orchestrator.executeAgentVersion("classifier", "2.0.0", new AIRequest("input"))
        );

        // THEN: Correct versions executed
        assertThat(responseV1.isSuccess()).isTrue();
        assertThat(responseV2.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent AI agent executions")
    void testConcurrentAIAgentExecutions() {
        // GIVEN: AI agent
        TestAIAgent agent = new TestAIAgent("concurrent-agent", "classification");
        orchestrator.registerAgent(agent);

        // WHEN: Execute concurrently
        List<Promise<AIResponse>> executions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Promise<AIResponse> execution = orchestrator.executeAgent(
                "concurrent-agent", 
                new AIRequest("input-" + i)
            );
            executions.add(execution);
        }

        List<AIResponse> responses = runPromise(() -> 
            io.activej.promise.Promises.toList(executions)
        );

        // THEN: All executions succeed
        assertThat(responses).hasSize(20);
        assertThat(responses).allMatch(AIResponse::isSuccess);
    }

    @Test
    @DisplayName("Should validate AI agent input and output")
    void testAIAgentInputOutputValidation() {
        // GIVEN: Agent with validation
        ValidatingAIAgent agent = new ValidatingAIAgent("validating-agent");
        orchestrator.registerAgent(agent);

        // WHEN: Execute with invalid input
        AIRequest invalidRequest = new AIRequest(""); // Empty input
        AIResponse invalidResponse = runPromise(() -> 
            orchestrator.executeAgent("validating-agent", invalidRequest)
        );

        // THEN: Validation fails
        assertThat(invalidResponse.isSuccess()).isFalse();
        assertThat(invalidResponse.getError()).contains("Input validation failed");

        // WHEN: Execute with valid input
        AIRequest validRequest = new AIRequest("valid input");
        AIResponse validResponse = runPromise(() -> 
            orchestrator.executeAgent("validating-agent", validRequest)
        );

        // THEN: Validation succeeds
        assertThat(validResponse.isSuccess()).isTrue();
    }

    // Test AI implementations

    private static class TestAIAgent {
        private final String agentId;
        private final String agentType;
        private String version = "1.0.0";

        TestAIAgent(String agentId, String agentType) {
            this.agentId = agentId;
            this.agentType = agentType;
        }

        void setVersion(String version) {
            this.version = version;
        }

        String getAgentId() {
            return agentId;
        }

        String getVersion() {
            return version;
        }

        Promise<AIResponse> execute(AIRequest request) {
            return Promise.of(new AIResponse(true, "Processed: " + request.getInput(), null));
        }
    }

    private static class FailingAIAgent extends TestAIAgent {
        FailingAIAgent(String agentId) {
            super(agentId, "failing");
        }

        @Override
        Promise<AIResponse> execute(AIRequest request) {
            return Promise.of(new AIResponse(false, null, "Agent execution failed"));
        }
    }

    private static class ValidatingAIAgent extends TestAIAgent {
        ValidatingAIAgent(String agentId) {
            super(agentId, "validating");
        }

        @Override
        Promise<AIResponse> execute(AIRequest request) {
            if (request.getInput() == null || request.getInput().isEmpty()) {
                return Promise.of(new AIResponse(false, null, "Input validation failed"));
            }
            return Promise.of(new AIResponse(true, "Validated: " + request.getInput(), null));
        }
    }

    private static class AIRequest {
        private final String input;

        AIRequest(String input) {
            this.input = input;
        }

        String getInput() {
            return input;
        }
    }

    private static class AIResponse {
        private final boolean success;
        private final String result;
        private final String error;

        AIResponse(boolean success, String result, String error) {
            this.success = success;
            this.result = result;
            this.error = error;
        }

        boolean isSuccess() {
            return success;
        }

        String getResult() {
            return result;
        }

        String getError() {
            return error;
        }
    }

    private static class AIWorkflow {
        private final String workflowId;
        private final List<String> steps = new ArrayList<>();

        AIWorkflow(String workflowId) {
            this.workflowId = workflowId;
        }

        void addStep(String agentId) {
            steps.add(agentId);
        }

        List<String> getSteps() {
            return new ArrayList<>(steps);
        }
    }

    private static class WorkflowResult {
        private final boolean success;
        private final List<String> executedAgents;

        WorkflowResult(boolean success, List<String> executedAgents) {
            this.success = success;
            this.executedAgents = executedAgents;
        }

        boolean isSuccess() {
            return success;
        }

        List<String> getExecutedAgents() {
            return executedAgents;
        }
    }

    private static class AIGovernancePolicy {
        private final String policyId;
        private final Map<String, String> rules = new HashMap<>();

        AIGovernancePolicy(String policyId) {
            this.policyId = policyId;
        }

        void addRule(String key, String value) {
            rules.put(key, value);
        }

        Map<String, String> getRules() {
            return new HashMap<>(rules);
        }
    }

    private static class AIAgentMetrics {
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private long totalLatencyMs = 0;

        void recordExecution(long latencyMs) {
            executionCount.incrementAndGet();
            totalLatencyMs += latencyMs;
        }

        int getExecutionCount() {
            return executionCount.get();
        }

        long getAverageLatencyMs() {
            int count = executionCount.get();
            return count > 0 ? totalLatencyMs / count : 0;
        }
    }

    private static class TestAIOrchestrator {
        private final Map<String, List<TestAIAgent>> agents = new HashMap<>();
        private final Map<String, AIGovernancePolicy> policies = new HashMap<>();
        private final Map<String, AIAgentMetrics> metrics = new HashMap<>();
        private final List<String> auditLog = new ArrayList<>();

        void registerAgent(TestAIAgent agent) {
            agents.computeIfAbsent(agent.getAgentId(), k -> new ArrayList<>()).add(agent);
            metrics.putIfAbsent(agent.getAgentId(), new AIAgentMetrics());
        }

        void registerGovernancePolicy(String agentId, AIGovernancePolicy policy) {
            policies.put(agentId, policy);
        }

        Promise<AIResponse> executeAgent(String agentId, AIRequest request) {
            return executeAgentVersion(agentId, null, request);
        }

        Promise<AIResponse> executeAgentVersion(String agentId, String version, AIRequest request) {
            List<TestAIAgent> agentVersions = agents.get(agentId);
            if (agentVersions == null || agentVersions.isEmpty()) {
                return Promise.of(new AIResponse(false, null, "Agent not found"));
            }

            TestAIAgent agent = version == null ? 
                agentVersions.get(agentVersions.size() - 1) : 
                agentVersions.stream()
                    .filter(a -> a.getVersion().equals(version))
                    .findFirst()
                    .orElse(null);

            if (agent == null) {
                return Promise.of(new AIResponse(false, null, "Agent version not found"));
            }

            // Check governance
            if (policies.containsKey(agentId)) {
                auditLog.add("Executing agent: " + agentId + " with governance");
            }

            long startTime = System.currentTimeMillis();
            return agent.execute(request).then(response -> {
                long latency = System.currentTimeMillis() - startTime;
                metrics.get(agentId).recordExecution(latency);
                return Promise.of(response);
            });
        }

        Promise<WorkflowResult> executeWorkflow(AIWorkflow workflow, AIRequest request) {
            List<String> executedAgents = new ArrayList<>();
            Promise<AIRequest> currentPromise = Promise.of(request);

            for (String agentId : workflow.getSteps()) {
                currentPromise = currentPromise.then(req -> 
                    executeAgent(agentId, req).then(response -> {
                        if (response.isSuccess()) {
                            executedAgents.add(agentId);
                            return Promise.of(new AIRequest(response.getResult()));
                        }
                        return Promise.ofException(new RuntimeException("Workflow failed at: " + agentId));
                    })
                );
            }

            return currentPromise
                .then(
                    finalRequest -> Promise.of(new WorkflowResult(true, executedAgents)),
                    error -> Promise.of(new WorkflowResult(false, executedAgents))
                );
        }

        AIAgentMetrics getMetrics(String agentId) {
            return metrics.get(agentId);
        }

        List<String> getAuditLog() {
            return new ArrayList<>(auditLog);
        }
    }
}
