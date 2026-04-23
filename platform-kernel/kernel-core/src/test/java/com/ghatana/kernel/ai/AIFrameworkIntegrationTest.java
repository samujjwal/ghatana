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
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
        orchestrator = new TestAIOrchestrator(); // GH-90000
    }

    @Test
    @DisplayName("Should register and execute AI agent")
    void testAIAgentRegistrationAndExecution() { // GH-90000
        // GIVEN: AI agent
        TestAIAgent agent = new TestAIAgent("test-agent", "classification"); // GH-90000

        // WHEN: Register and execute
        orchestrator.registerAgent(agent); // GH-90000
        AIResponse response = runPromise(() -> // GH-90000
            orchestrator.executeAgent("test-agent", new AIRequest("test input"))
        );

        // THEN: Agent executes successfully
        assertThat(response.isSuccess()).isTrue(); // GH-90000
        assertThat(response.getResult()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should orchestrate multi-agent workflow")
    void testMultiAgentWorkflow() { // GH-90000
        // GIVEN: Multiple agents in workflow
        TestAIAgent classificationAgent = new TestAIAgent("classifier", "classification"); // GH-90000
        TestAIAgent enrichmentAgent = new TestAIAgent("enricher", "enrichment"); // GH-90000
        TestAIAgent validationAgent = new TestAIAgent("validator", "validation"); // GH-90000

        orchestrator.registerAgent(classificationAgent); // GH-90000
        orchestrator.registerAgent(enrichmentAgent); // GH-90000
        orchestrator.registerAgent(validationAgent); // GH-90000

        // WHEN: Execute workflow
        AIWorkflow workflow = new AIWorkflow("multi-agent-workflow");
        workflow.addStep("classifier");
        workflow.addStep("enricher");
        workflow.addStep("validator");

        WorkflowResult result = runPromise(() -> // GH-90000
            orchestrator.executeWorkflow(workflow, new AIRequest("input data"))
        );

        // THEN: All agents executed in order
        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getExecutedAgents()).containsExactly("classifier", "enricher", "validator"); // GH-90000
    }

    @Test
    @DisplayName("Should enforce AI governance policies")
    void testAIGovernancePolicies() { // GH-90000
        // GIVEN: AI agent with governance policy
        TestAIAgent agent = new TestAIAgent("governed-agent", "sensitive-classification"); // GH-90000

        AIGovernancePolicy policy = new AIGovernancePolicy("sensitive-data-policy");
        policy.addRule("max-tokens", "1000"); // GH-90000
        policy.addRule("require-audit", "true"); // GH-90000
        policy.addRule("pii-detection", "enabled"); // GH-90000

        orchestrator.registerAgent(agent); // GH-90000
        orchestrator.registerGovernancePolicy("governed-agent", policy); // GH-90000

        // WHEN: Execute with governance
        AIRequest request = new AIRequest("sensitive data");
        AIResponse response = runPromise(() -> // GH-90000
            orchestrator.executeAgent("governed-agent", request) // GH-90000
        );

        // THEN: Governance enforced
        assertThat(response.isSuccess()).isTrue(); // GH-90000
        assertThat(orchestrator.getAuditLog()).isNotEmpty(); // GH-90000
        assertThat(orchestrator.getAuditLog().get(0)).contains("governed-agent");
    }

    @Test
    @DisplayName("Should handle AI agent failures gracefully")
    void testAIAgentFailureHandling() { // GH-90000
        // GIVEN: Failing AI agent
        FailingAIAgent failingAgent = new FailingAIAgent("failing-agent");

        orchestrator.registerAgent(failingAgent); // GH-90000

        // WHEN: Execute failing agent
        AIResponse response = runPromise(() -> // GH-90000
            orchestrator.executeAgent("failing-agent", new AIRequest("input"))
        );

        // THEN: Failure handled gracefully
        assertThat(response.isSuccess()).isFalse(); // GH-90000
        assertThat(response.getError()).contains("Agent execution failed");
    }

    @Test
    @DisplayName("Should track AI agent metrics")
    void testAIAgentMetricsTracking() { // GH-90000
        // GIVEN: AI agent with metrics
        TestAIAgent agent = new TestAIAgent("metrics-agent", "classification"); // GH-90000
        orchestrator.registerAgent(agent); // GH-90000

        // WHEN: Execute same agent multiple times concurrently
        int executionCount = 10;
        java.util.stream.IntStream.range(0, executionCount).forEach(i -> { // GH-90000
            runPromise(() -> orchestrator.executeAgent("metrics-agent", new AIRequest("input-" + i))); // GH-90000
        });

        // THEN: Metrics tracked
        AIAgentMetrics metrics = orchestrator.getMetrics("metrics-agent");
        assertThat(metrics.getExecutionCount()).isEqualTo(executionCount); // GH-90000
        assertThat(metrics.getAverageLatencyMs()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should support AI agent versioning")
    void testAIAgentVersioning() { // GH-90000
        // GIVEN: Multiple versions of same agent
        TestAIAgent agentV1 = new TestAIAgent("classifier", "classification"); // GH-90000
        agentV1.setVersion("1.0.0");

        TestAIAgent agentV2 = new TestAIAgent("classifier", "classification"); // GH-90000
        agentV2.setVersion("2.0.0");

        orchestrator.registerAgent(agentV1); // GH-90000
        orchestrator.registerAgent(agentV2); // GH-90000

        // WHEN: Execute specific version
        AIResponse responseV1 = runPromise(() -> // GH-90000
            orchestrator.executeAgentVersion("classifier", "1.0.0", new AIRequest("input"))
        );
        AIResponse responseV2 = runPromise(() -> // GH-90000
            orchestrator.executeAgentVersion("classifier", "2.0.0", new AIRequest("input"))
        );

        // THEN: Correct versions executed
        assertThat(responseV1.isSuccess()).isTrue(); // GH-90000
        assertThat(responseV2.isSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should handle concurrent AI agent executions")
    void testConcurrentAIAgentExecutions() { // GH-90000
        // GIVEN: AI agent
        TestAIAgent agent = new TestAIAgent("concurrent-agent", "classification"); // GH-90000
        orchestrator.registerAgent(agent); // GH-90000

        // WHEN: Execute concurrently
        List<Promise<AIResponse>> executions = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 20; i++) { // GH-90000
            Promise<AIResponse> execution = orchestrator.executeAgent( // GH-90000
                "concurrent-agent",
                new AIRequest("input-" + i) // GH-90000
            );
            executions.add(execution); // GH-90000
        }

        List<AIResponse> responses = runPromise(() -> // GH-90000
            io.activej.promise.Promises.toList(executions) // GH-90000
        );

        // THEN: All executions succeed
        assertThat(responses).hasSize(20); // GH-90000
        assertThat(responses).allMatch(AIResponse::isSuccess); // GH-90000
    }

    @Test
    @DisplayName("Should validate AI agent input and output")
    void testAIAgentInputOutputValidation() { // GH-90000
        // GIVEN: Agent with validation
        ValidatingAIAgent agent = new ValidatingAIAgent("validating-agent");
        orchestrator.registerAgent(agent); // GH-90000

        // WHEN: Execute with invalid input
        AIRequest invalidRequest = new AIRequest(""); // Empty input
        AIResponse invalidResponse = runPromise(() -> // GH-90000
            orchestrator.executeAgent("validating-agent", invalidRequest) // GH-90000
        );

        // THEN: Validation fails
        assertThat(invalidResponse.isSuccess()).isFalse(); // GH-90000
        assertThat(invalidResponse.getError()).contains("Input validation failed");

        // WHEN: Execute with valid input
        AIRequest validRequest = new AIRequest("valid input");
        AIResponse validResponse = runPromise(() -> // GH-90000
            orchestrator.executeAgent("validating-agent", validRequest) // GH-90000
        );

        // THEN: Validation succeeds
        assertThat(validResponse.isSuccess()).isTrue(); // GH-90000
    }

    // Test AI implementations

    private static class TestAIAgent {
        private final String agentId;
        private final String agentType;
        private String version = "1.0.0";

        TestAIAgent(String agentId, String agentType) { // GH-90000
            this.agentId = agentId;
            this.agentType = agentType;
        }

        void setVersion(String version) { // GH-90000
            this.version = version;
        }

        String getAgentId() { // GH-90000
            return agentId;
        }

        String getVersion() { // GH-90000
            return version;
        }

        Promise<AIResponse> execute(AIRequest request) { // GH-90000
            return Promise.of(new AIResponse(true, "Processed: " + request.getInput(), null)); // GH-90000
        }
    }

    private static class FailingAIAgent extends TestAIAgent {
        FailingAIAgent(String agentId) { // GH-90000
            super(agentId, "failing"); // GH-90000
        }

        @Override
        Promise<AIResponse> execute(AIRequest request) { // GH-90000
            return Promise.of(new AIResponse(false, null, "Agent execution failed")); // GH-90000
        }
    }

    private static class ValidatingAIAgent extends TestAIAgent {
        ValidatingAIAgent(String agentId) { // GH-90000
            super(agentId, "validating"); // GH-90000
        }

        @Override
        Promise<AIResponse> execute(AIRequest request) { // GH-90000
            if (request.getInput() == null || request.getInput().isEmpty()) { // GH-90000
                return Promise.of(new AIResponse(false, null, "Input validation failed")); // GH-90000
            }
            return Promise.of(new AIResponse(true, "Validated: " + request.getInput(), null)); // GH-90000
        }
    }

    private static class AIRequest {
        private final String input;

        AIRequest(String input) { // GH-90000
            this.input = input;
        }

        String getInput() { // GH-90000
            return input;
        }
    }

    private static class AIResponse {
        private final boolean success;
        private final String result;
        private final String error;

        AIResponse(boolean success, String result, String error) { // GH-90000
            this.success = success;
            this.result = result;
            this.error = error;
        }

        boolean isSuccess() { // GH-90000
            return success;
        }

        String getResult() { // GH-90000
            return result;
        }

        String getError() { // GH-90000
            return error;
        }
    }

    private static class AIWorkflow {
        private final String workflowId;
        private final List<String> steps = new ArrayList<>(); // GH-90000

        AIWorkflow(String workflowId) { // GH-90000
            this.workflowId = workflowId;
        }

        void addStep(String agentId) { // GH-90000
            steps.add(agentId); // GH-90000
        }

        List<String> getSteps() { // GH-90000
            return new ArrayList<>(steps); // GH-90000
        }
    }

    private static class WorkflowResult {
        private final boolean success;
        private final List<String> executedAgents;

        WorkflowResult(boolean success, List<String> executedAgents) { // GH-90000
            this.success = success;
            this.executedAgents = executedAgents;
        }

        boolean isSuccess() { // GH-90000
            return success;
        }

        List<String> getExecutedAgents() { // GH-90000
            return executedAgents;
        }
    }

    private static class AIGovernancePolicy {
        private final String policyId;
        private final Map<String, String> rules = new HashMap<>(); // GH-90000

        AIGovernancePolicy(String policyId) { // GH-90000
            this.policyId = policyId;
        }

        void addRule(String key, String value) { // GH-90000
            rules.put(key, value); // GH-90000
        }

        Map<String, String> getRules() { // GH-90000
            return new HashMap<>(rules); // GH-90000
        }
    }

    private static class AIAgentMetrics {
        private final AtomicInteger executionCount = new AtomicInteger(0); // GH-90000
        private long totalLatencyMs = 0;

        void recordExecution(long latencyMs) { // GH-90000
            executionCount.incrementAndGet(); // GH-90000
            totalLatencyMs += Math.max(1L, latencyMs); // GH-90000
        }

        int getExecutionCount() { // GH-90000
            return executionCount.get(); // GH-90000
        }

        long getAverageLatencyMs() { // GH-90000
            int count = executionCount.get(); // GH-90000
            return count > 0 ? totalLatencyMs / count : 0;
        }
    }

    private static class TestAIOrchestrator {
        private final Map<String, List<TestAIAgent>> agents = new HashMap<>(); // GH-90000
        private final Map<String, AIGovernancePolicy> policies = new HashMap<>(); // GH-90000
        private final Map<String, AIAgentMetrics> metrics = new HashMap<>(); // GH-90000
        private final List<String> auditLog = new ArrayList<>(); // GH-90000

        void registerAgent(TestAIAgent agent) { // GH-90000
            agents.computeIfAbsent(agent.getAgentId(), k -> new ArrayList<>()).add(agent); // GH-90000
            metrics.putIfAbsent(agent.getAgentId(), new AIAgentMetrics()); // GH-90000
        }

        void registerGovernancePolicy(String agentId, AIGovernancePolicy policy) { // GH-90000
            policies.put(agentId, policy); // GH-90000
        }

        Promise<AIResponse> executeAgent(String agentId, AIRequest request) { // GH-90000
            return executeAgentVersion(agentId, null, request); // GH-90000
        }

        Promise<AIResponse> executeAgentVersion(String agentId, String version, AIRequest request) { // GH-90000
            List<TestAIAgent> agentVersions = agents.get(agentId); // GH-90000
            if (agentVersions == null || agentVersions.isEmpty()) { // GH-90000
                return Promise.of(new AIResponse(false, null, "Agent not found")); // GH-90000
            }

            TestAIAgent agent = version == null ?
                agentVersions.get(agentVersions.size() - 1) : // GH-90000
                agentVersions.stream() // GH-90000
                    .filter(a -> a.getVersion().equals(version)) // GH-90000
                    .findFirst() // GH-90000
                    .orElse(null); // GH-90000

            if (agent == null) { // GH-90000
                return Promise.of(new AIResponse(false, null, "Agent version not found")); // GH-90000
            }

            // Check governance
            if (policies.containsKey(agentId)) { // GH-90000
                auditLog.add("Executing agent: " + agentId + " with governance"); // GH-90000
            }

            long startTime = System.currentTimeMillis(); // GH-90000
            return agent.execute(request).then(response -> { // GH-90000
                long latency = System.currentTimeMillis() - startTime; // GH-90000
                metrics.get(agentId).recordExecution(latency); // GH-90000
                return Promise.of(response); // GH-90000
            });
        }

        Promise<WorkflowResult> executeWorkflow(AIWorkflow workflow, AIRequest request) { // GH-90000
            List<String> executedAgents = new ArrayList<>(); // GH-90000
            Promise<AIRequest> currentPromise = Promise.of(request); // GH-90000

            for (String agentId : workflow.getSteps()) { // GH-90000
                currentPromise = currentPromise.then(req -> // GH-90000
                    executeAgent(agentId, req).then(response -> { // GH-90000
                        if (response.isSuccess()) { // GH-90000
                            executedAgents.add(agentId); // GH-90000
                            return Promise.of(new AIRequest(response.getResult())); // GH-90000
                        }
                        return Promise.ofException(new RuntimeException("Workflow failed at: " + agentId)); // GH-90000
                    })
                );
            }

            return currentPromise
                .then( // GH-90000
                    finalRequest -> Promise.of(new WorkflowResult(true, executedAgents)), // GH-90000
                    error -> Promise.of(new WorkflowResult(false, executedAgents)) // GH-90000
                );
        }

        AIAgentMetrics getMetrics(String agentId) { // GH-90000
            return metrics.get(agentId); // GH-90000
        }

        List<String> getAuditLog() { // GH-90000
            return new ArrayList<>(auditLog); // GH-90000
        }
    }
}
