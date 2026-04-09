package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies retry and workflow behavior in FinanceAgentOrchestratorImpl
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceAgentOrchestratorImplTest {

    @Test
    void retriesExceptionUntilSuccess() {
        FinanceAgentOrchestratorImpl orchestrator = new FinanceAgentOrchestratorImpl();
        AtomicInteger attempts = new AtomicInteger();

        AgentOrchestrator.KernelAgent agent = new TestAgent("agent-1", request -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary failure");
            }
            return successResponse(request.getRequestId(), "ok");
        });

        AgentOrchestrator.AgentResponse response = orchestrator.executeAgent(
            agent,
            new AgentOrchestrator.AgentRequest("req-1", "detect", Map.of(), Map.of())
        );

        assertEquals(3, attempts.get());
        assertTrue(response.isSuccess());
        assertEquals("ok", response.getResult());
    }

    @Test
    void retriesUnsuccessfulResponsesThenThrows() {
        FinanceAgentOrchestratorImpl orchestrator = new FinanceAgentOrchestratorImpl();
        AtomicInteger attempts = new AtomicInteger();

        AgentOrchestrator.KernelAgent agent = new TestAgent("agent-2", request -> {
            attempts.incrementAndGet();
            return AgentOrchestrator.AgentResponse.builder()
                .requestId(request.getRequestId())
                .success(false)
                .error("model unavailable")
                .build();
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> orchestrator.executeAgent(
            agent,
            new AgentOrchestrator.AgentRequest("req-2", "detect", Map.of(), Map.of())
        ));

        assertEquals(3, attempts.get());
        assertTrue(exception.getMessage().contains("model unavailable"));
    }

    @Test
    void executesWorkflowUsingRetryingExecutionPath() {
        FinanceAgentOrchestratorImpl orchestrator = new FinanceAgentOrchestratorImpl();
        AtomicInteger attempts = new AtomicInteger();

        AgentOrchestrator.KernelAgent flakyAgent = new TestAgent("agent-3", request -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first failure");
            }
            return successResponse(request.getRequestId(), "recovered");
        });

        AgentOrchestrator.WorkflowResult result = orchestrator.executeAgentWorkflow(
            List.of(flakyAgent),
            new AgentOrchestrator.AgentRequest("req-3", "detect", Map.of(), Map.of())
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.getResponses().size());
        assertEquals("recovered", result.getResponses().get(0).getResult());
    }

    @Test
    void executesAsyncUsingRetryPath() {
        FinanceAgentOrchestratorImpl orchestrator = new FinanceAgentOrchestratorImpl();
        AtomicInteger attempts = new AtomicInteger();

        AgentOrchestrator.KernelAgent agent = new TestAgent("agent-4", request -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("retry me");
            }
            return successResponse(request.getRequestId(), "async-ok");
        });

        AgentOrchestrator.AgentResponse response = orchestrator.executeAgentAsync(
            agent,
            new AgentOrchestrator.AgentRequest("req-4", "detect", Map.of(), Map.of())
        ).getResult();

        assertNotNull(response);
        assertEquals(2, attempts.get());
        assertEquals("async-ok", response.getResult());
    }

    private static AgentOrchestrator.AgentResponse successResponse(String requestId, Object result) {
        return AgentOrchestrator.AgentResponse.builder()
            .requestId(requestId)
            .success(true)
            .result(result)
            .metadata(Map.of())
            .build();
    }

    private record TestAgent(String agentId,
                             java.util.function.Function<AgentOrchestrator.AgentRequest, AgentOrchestrator.AgentResponse> executor)
        implements AgentOrchestrator.KernelAgent {

        @Override
        public String getAgentId() {
            return agentId;
        }

        @Override
        public String getName() {
            return agentId;
        }

        @Override
        public String getDescription() {
            return agentId;
        }

        @Override
        public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
            return executor.apply(request);
        }

        @Override
        public AgentOrchestrator.AgentCapabilities getCapabilities() {
            return new AgentOrchestrator.AgentCapabilities() {
                @Override
                public List<String> getSupportedOperations() {
                    return List.of("detect");
                }

                @Override
                public Map<String, Object> getMetadata() {
                    return Map.of();
                }

                @Override
                public boolean supportsOperation(String operation) {
                    return "detect".equals(operation);
                }
            };
        }
    }
}
