package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.infra.transparency.InMemoryAiActionLogRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GovernedAgentWorkflowService (DMOS-P1-019).
 *
 * @doc.type test
 * @doc.purpose Verify governed agent workflow with logging and approval routing
 * @doc.layer application
 */
@DisplayName("GovernedAgentWorkflowService")
class GovernedAgentWorkflowServiceTest {

    private AiActionLogRepository aiActionLogRepository;
    private InMemoryAgentPort agentPort;
    private GovernedAgentWorkflowService service;

    @BeforeEach
    void setUp() {
        aiActionLogRepository = new InMemoryAiActionLogRepository();
        agentPort = new InMemoryAgentPort();
        service = new GovernedAgentWorkflowService(agentPort, aiActionLogRepository, new AiPolicyCheckServiceImpl());
    }

    @Test
    @DisplayName("executeGovernedWorkflow logs AI action and returns result")
    void executeGovernedWorkflow_logsAiActionAndReturnsResult() {
        agentPort.setResponse(new DmAgentOrchestrationPort.AgentResponse(
            "Generated strategy output",
            "gpt-4",
            0.95,
            "kernel://evidence/123",
            Duration.ofMillis(500),
            true,
            null
        ));

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            operationContext(),
            DmAgentOrchestrationPort.AgentType.AD_COPY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of()
        ).getResult();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Generated strategy output");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.approvalRequirement()).isEqualTo(AiPolicyCheckService.ApprovalRequirement.AUTO_APPROVE);
        assertThat(result.logEntryId()).isNotNull();
    }

    @Test
    @DisplayName("executeGovernedWorkflow requires approval for low confidence")
    void executeGovernedWorkflow_requiresApprovalForLowConfidence() {
        agentPort.setResponse(new DmAgentOrchestrationPort.AgentResponse(
            "Generated strategy output",
            "gpt-4",
            0.65, // Low confidence
            "kernel://evidence/123",
            Duration.ofMillis(500),
            true,
            null
        ));

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            operationContext(),
            DmAgentOrchestrationPort.AgentType.AD_COPY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of()
        ).getResult();

        assertThat(result.approvalRequirement()).isNotEqualTo(AiPolicyCheckService.ApprovalRequirement.AUTO_APPROVE);
    }

    @Test
    @DisplayName("executeGovernedWorkflow does not require approval for failed response")
    void executeGovernedWorkflow_doesNotRequireApprovalForFailedResponse() {
        agentPort.setResponse(new DmAgentOrchestrationPort.AgentResponse(
            "",
            "gpt-4",
            0.0,
            null,
            Duration.ZERO,
            false,
            "Agent invocation failed"
        ));

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            operationContext(),
            DmAgentOrchestrationPort.AgentType.AD_COPY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of()
        ).getResult();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Agent invocation failed");
    }

    private DmOperationContext operationContext() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .actor(ActorRef.user("user-789"))
            .correlationId(DmCorrelationId.of("corr-789"))
            .build();
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class InMemoryAgentPort implements DmAgentOrchestrationPort {
        private DmAgentOrchestrationPort.AgentResponse response;

        void setResponse(DmAgentOrchestrationPort.AgentResponse response) {
            this.response = response;
        }

        @Override
        public Promise<AgentResponse> invokeAgent(
                AgentType agentType,
                String prompt,
                String model,
                Map<String, Object> parameters,
                Duration timeout) {
            return Promise.of(response);
        }

        @Override
        public Promise<Boolean> isAvailable() {
            return Promise.of(true);
        }

        @Override
        public Promise<AgentHealthStatus> getHealthStatus() {
            return Promise.of(AgentHealthStatus.HEALTHY);
        }
    }
}
