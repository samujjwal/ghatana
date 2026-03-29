/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.agent.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AI agent execution workflows.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentWorkflowIntegrationTest extends EventloopTestBase {

    private static AgentOrchestrator orchestrator;
    private static AgentRegistry registry;

    @BeforeAll
    static void setUpAll() {
        registry = new MockAgentRegistry();
        orchestrator = new MockAgentOrchestrator(registry);
    }

    @Test
    @Order(1)
    @DisplayName("Integration: Should execute single agent successfully")
    void testSingleAgentExecution() throws Exception {
        AgentRequest request = AgentRequest.builder()
                .agentId("code-generator")
                .phase("IMPLEMENTATION")
                .input(Map.of("language", "java", "feature", "user-service"))
                .build();

        Promise<AgentResponse> promise = orchestrator.executeAgent(request);
        AgentResponse response = runPromise(() -> promise);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.output()).containsKey("generatedCode");
    }

    @Test
    @Order(2)
    @DisplayName("Integration: Should execute agent workflow with multiple phases")
    void testMultiPhaseWorkflow() throws Exception {
        WorkflowRequest workflow = WorkflowRequest.builder()
                .workflowId("create-microservice")
                .phases(java.util.List.of("PLANNING", "DESIGN", "IMPLEMENTATION"))
                .context(Map.of("serviceName", "payment-service"))
                .build();

        Promise<WorkflowResponse> promise = orchestrator.executeWorkflow(workflow);
        WorkflowResponse response = runPromise(() -> promise);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.completedPhases()).hasSize(3);
        assertThat(response.completedPhases()).containsExactly("PLANNING", "DESIGN", "IMPLEMENTATION");
    }

    @Test
    @Order(3)
    @DisplayName("Integration: Should handle agent failure gracefully")
    void testAgentFailureHandling() throws Exception {
        AgentRequest request = AgentRequest.builder()
                .agentId("failing-agent")
                .phase("TESTING")
                .input(Map.of("shouldFail", "true"))
                .build();

        Promise<AgentResponse> promise = orchestrator.executeAgent(request);
        AgentResponse response = runPromise(() -> promise);

        assertThat(response).isNotNull();
        assertThat(response.success()).isFalse();
        assertThat(response.error()).isNotNull();
        assertThat(response.error()).contains("Agent execution failed");
    }

    @Test
    @Order(4)
    @DisplayName("Integration: Should chain agent outputs as inputs")
    void testAgentChaining() throws Exception {
        // First agent: analyze requirements
        AgentRequest analyzeRequest = AgentRequest.builder()
                .agentId("requirement-analyzer")
                .phase("PLANNING")
                .input(Map.of("requirements", "Build a REST API"))
                .build();

        AgentResponse analyzeResponse = runPromise(() -> orchestrator.executeAgent(analyzeRequest));
        assertThat(analyzeResponse.success()).isTrue();

        // Second agent: use first agent's output
        AgentRequest designRequest = AgentRequest.builder()
                .agentId("api-designer")
                .phase("DESIGN")
                .input(analyzeResponse.output())
                .build();

        AgentResponse designResponse = runPromise(() -> orchestrator.executeAgent(designRequest));
        assertThat(designResponse.success()).isTrue();
        assertThat(designResponse.output()).containsKey("apiSpec");
    }

    @Test
    @Order(5)
    @DisplayName("Integration: Should execute parallel agents")
    void testParallelAgentExecution() throws Exception {
        AgentRequest[] requests = {
            AgentRequest.builder()
                .agentId("frontend-generator")
                .phase("IMPLEMENTATION")
                .input(Map.of("framework", "react"))
                .build(),
            AgentRequest.builder()
                .agentId("backend-generator")
                .phase("IMPLEMENTATION")
                .input(Map.of("framework", "spring"))
                .build(),
            AgentRequest.builder()
                .agentId("database-generator")
                .phase("IMPLEMENTATION")
                .input(Map.of("type", "postgresql"))
                .build()
        };

        Promise<AgentResponse[]> promise = orchestrator.executeParallel(requests);
        AgentResponse[] responses = runPromise(() -> promise);

        assertThat(responses).hasSize(3);
        for (AgentResponse response : responses) {
            assertThat(response.success()).isTrue();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Integration: Should track agent execution metrics")
    void testAgentMetrics() throws Exception {
        AgentRequest request = AgentRequest.builder()
                .agentId("code-generator")
                .phase("IMPLEMENTATION")
                .input(Map.of("language", "typescript"))
                .build();

        AgentResponse response = runPromise(() -> orchestrator.executeAgent(request));

        assertThat(response.metrics()).isNotNull();
        assertThat(response.metrics().executionTimeMs()).isGreaterThan(0);
        assertThat(response.metrics().tokensUsed()).isGreaterThan(0);
    }

    // Mock implementations

    interface AgentOrchestrator {
        Promise<AgentResponse> executeAgent(AgentRequest request);
        Promise<WorkflowResponse> executeWorkflow(WorkflowRequest workflow);
        Promise<AgentResponse[]> executeParallel(AgentRequest[] requests);
    }

    interface AgentRegistry {
        Agent getAgent(String agentId);
    }

    interface Agent {
        Promise<Map<String, Object>> execute(Map<String, Object> input);
    }

    record AgentRequest(String agentId, String phase, Map<String, Object> input) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String agentId, phase;
            private Map<String, Object> input;
            Builder agentId(String v) { agentId = v; return this; }
            Builder phase(String v) { phase = v; return this; }
            Builder input(Map<String, Object> v) { input = v; return this; }
            AgentRequest build() { return new AgentRequest(agentId, phase, input); }
        }
    }

    record AgentResponse(boolean success, Map<String, Object> output, String error, AgentMetrics metrics) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private boolean success;
            private Map<String, Object> output;
            private String error;
            private AgentMetrics metrics;
            Builder success(boolean v) { success = v; return this; }
            Builder output(Map<String, Object> v) { output = v; return this; }
            Builder error(String v) { error = v; return this; }
            Builder metrics(AgentMetrics v) { metrics = v; return this; }
            AgentResponse build() { return new AgentResponse(success, output, error, metrics); }
        }
    }

    record AgentMetrics(long executionTimeMs, int tokensUsed) {}

    record WorkflowRequest(String workflowId, java.util.List<String> phases, Map<String, Object> context) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String workflowId;
            private java.util.List<String> phases;
            private Map<String, Object> context;
            Builder workflowId(String v) { workflowId = v; return this; }
            Builder phases(java.util.List<String> v) { phases = v; return this; }
            Builder context(Map<String, Object> v) { context = v; return this; }
            WorkflowRequest build() { return new WorkflowRequest(workflowId, phases, context); }
        }
    }

    record WorkflowResponse(boolean success, java.util.List<String> completedPhases, Map<String, Object> result) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private boolean success;
            private java.util.List<String> completedPhases;
            private Map<String, Object> result;
            Builder success(boolean v) { success = v; return this; }
            Builder completedPhases(java.util.List<String> v) { completedPhases = v; return this; }
            Builder result(Map<String, Object> v) { result = v; return this; }
            WorkflowResponse build() { return new WorkflowResponse(success, completedPhases, result); }
        }
    }

    static class MockAgentRegistry implements AgentRegistry {
        private final Map<String, Agent> agents = new ConcurrentHashMap<>();

        MockAgentRegistry() {
            agents.put("code-generator", new CodeGeneratorAgent());
            agents.put("requirement-analyzer", new RequirementAnalyzerAgent());
            agents.put("api-designer", new ApiDesignerAgent());
            agents.put("frontend-generator", new FrontendGeneratorAgent());
            agents.put("backend-generator", new BackendGeneratorAgent());
            agents.put("database-generator", new DatabaseGeneratorAgent());
            agents.put("failing-agent", new FailingAgent());
        }

        @Override
        public Agent getAgent(String agentId) {
            return agents.get(agentId);
        }
    }

    static class MockAgentOrchestrator implements AgentOrchestrator {
        private final AgentRegistry registry;

        MockAgentOrchestrator(AgentRegistry registry) {
            this.registry = registry;
        }

        @Override
        public Promise<AgentResponse> executeAgent(AgentRequest request) {
            long startTime = System.currentTimeMillis();
            Agent agent = registry.getAgent(request.agentId());

            if (agent == null) {
                return Promise.of(AgentResponse.builder()
                    .success(false)
                    .error("Agent not found: " + request.agentId())
                    .metrics(new AgentMetrics(0, 0))
                    .build());
            }

            return agent.execute(request.input())
                .map(output -> {
                    long executionTime = Math.max(1L, System.currentTimeMillis() - startTime);
                    return AgentResponse.builder()
                        .success(true)
                        .output(output)
                        .metrics(new AgentMetrics(executionTime, 1500))
                        .build();
                })
                .then(Promise::of, e -> Promise.of(AgentResponse.builder()
                    .success(false)
                    .error("Agent execution failed: " + e.getMessage())
                    .metrics(new AgentMetrics(0, 0))
                    .build()));
        }

        @Override
        public Promise<WorkflowResponse> executeWorkflow(WorkflowRequest workflow) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                java.util.List<String> completed = new java.util.ArrayList<>();
                Map<String, Object> context = new java.util.HashMap<>(workflow.context());

                for (String phase : workflow.phases()) {
                    completed.add(phase);
                    context.put(phase.toLowerCase() + "Complete", true);
                }

                return WorkflowResponse.builder()
                    .success(true)
                    .completedPhases(completed)
                    .result(context)
                    .build();
            });
        }

        @Override
        public Promise<AgentResponse[]> executeParallel(AgentRequest[] requests) {
            java.util.List<Promise<AgentResponse>> promises = new java.util.ArrayList<>();
            for (AgentRequest request : requests) {
                promises.add(executeAgent(request));
            }
            return Promises.toList(promises)
                .map(list -> list.toArray(new AgentResponse[0]));
        }
    }

    static class CodeGeneratorAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("generatedCode", "public class UserService { }"));
        }
    }

    static class RequirementAnalyzerAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("analysis", "REST API requirements analyzed", "endpoints", 5));
        }
    }

    static class ApiDesignerAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("apiSpec", "OpenAPI 3.0 specification"));
        }
    }

    static class FrontendGeneratorAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("frontend", "React components generated"));
        }
    }

    static class BackendGeneratorAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("backend", "Spring Boot service generated"));
        }
    }

    static class DatabaseGeneratorAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.of(Map.of("database", "PostgreSQL schema generated"));
        }
    }

    static class FailingAgent implements Agent {
        @Override
        public Promise<Map<String, Object>> execute(Map<String, Object> input) {
            return Promise.ofException(new RuntimeException("Simulated failure"));
        }
    }
}
