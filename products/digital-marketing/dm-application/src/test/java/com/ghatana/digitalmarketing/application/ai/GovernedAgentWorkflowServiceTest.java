package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for GovernedAgentWorkflowService (DMOS-P1-019).
 *
 * @doc.type test
 * @doc.purpose Verify governed agent workflow with logging and approval routing
 * @doc.layer application
 */
@DisplayName("GovernedAgentWorkflowService")
class GovernedAgentWorkflowServiceTest {

    @Test
    @DisplayName("executeGovernedWorkflow logs AI action and returns result")
    void executeGovernedWorkflow_logsAiActionAndReturnsResult() {
        DmAgentOrchestrationPort agentPort = mock(DmAgentOrchestrationPort.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        when(agentPort.invokeAgent(any(), any(), any(), any(), any(Duration.class)))
            .thenReturn(Promise.of(new DmAgentOrchestrationPort.AgentResponse(
                "Generated strategy output",
                "gpt-4",
                0.95,
                "kernel://evidence/123",
                Duration.ofMillis(500),
                true,
                null
            )));

        when(aiActionLogRepository.save(any(AiActionLogEntry.class)))
            .thenReturn(Promise.of(mock(AiActionLogEntry.class)));

        GovernedAgentWorkflowService service = new GovernedAgentWorkflowService(agentPort, aiActionLogRepository);

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("Generated strategy output");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.approvalRequired()).isFalse(); // High confidence, no approval required
        assertThat(result.logEntryId()).isNotNull();
    }

    @Test
    @DisplayName("executeGovernedWorkflow requires approval for low confidence")
    void executeGovernedWorkflow_requiresApprovalForLowConfidence() {
        DmAgentOrchestrationPort agentPort = mock(DmAgentOrchestrationPort.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        when(agentPort.invokeAgent(any(), any(), any(), any(), any(Duration.class)))
            .thenReturn(Promise.of(new DmAgentOrchestrationPort.AgentResponse(
                "Generated strategy output",
                "gpt-4",
                0.65, // Low confidence
                "kernel://evidence/123",
                Duration.ofMillis(500),
                true,
                null
            )));

        when(aiActionLogRepository.save(any(AiActionLogEntry.class)))
            .thenReturn(Promise.of(mock(AiActionLogEntry.class)));

        GovernedAgentWorkflowService service = new GovernedAgentWorkflowService(agentPort, aiActionLogRepository);

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.approvalRequired()).isTrue(); // Low confidence, approval required
    }

    @Test
    @DisplayName("executeGovernedWorkflow does not require approval for failed response")
    void executeGovernedWorkflow_doesNotRequireApprovalForFailedResponse() {
        DmAgentOrchestrationPort agentPort = mock(DmAgentOrchestrationPort.class);
        AiActionLogRepository aiActionLogRepository = mock(AiActionLogRepository.class);

        when(agentPort.invokeAgent(any(), any(), any(), any(), any(Duration.class)))
            .thenReturn(Promise.of(new DmAgentOrchestrationPort.AgentResponse(
                "",
                "gpt-4",
                0.0,
                null,
                Duration.ZERO,
                false,
                "Agent invocation failed"
            )));

        when(aiActionLogRepository.save(any(AiActionLogEntry.class)))
            .thenReturn(Promise.of(mock(AiActionLogEntry.class)));

        GovernedAgentWorkflowService service = new GovernedAgentWorkflowService(agentPort, aiActionLogRepository);

        GovernedAgentWorkflowService.GovernedWorkflowResult result = service.executeGovernedWorkflow(
            DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
            "Generate a strategy",
            "gpt-4",
            Map.of(),
            new DmTenantId("tenant-123"),
            new DmWorkspaceId("workspace-456"),
            "user-789"
        ).getResult();

        assertThat(result.success()).isFalse();
        assertThat(result.approvalRequired()).isFalse(); // Failed response, no approval needed
        assertThat(result.errorMessage()).isEqualTo("Agent invocation failed");
    }
}
