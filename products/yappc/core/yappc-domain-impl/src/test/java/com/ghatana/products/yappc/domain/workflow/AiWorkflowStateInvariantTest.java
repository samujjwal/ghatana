package com.ghatana.products.yappc.domain.workflow;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AiWorkflowService State Invariant Tests")
class AiWorkflowStateInvariantTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-invariant";

    private InMemoryAiWorkflowRepository workflowRepository;
    private AiWorkflowService service;

    @BeforeEach
    void setUp() {
        workflowRepository = new InMemoryAiWorkflowRepository();
        service = new AiWorkflowService(
                workflowRepository,
                new InMemoryAiPlanRepository(),
                mock(AgentRegistry.class));
    }

    @Test
    @DisplayName("YD-1: pause is rejected when workflow is not IN_PROGRESS")
    void pause_requiresInProgressState() {
        AiWorkflowInstance workflow = createDraftWorkflow("pause-invalid");

        assertThatThrownBy(() -> runPromise(() -> service.pauseWorkflow(workflow.id(), TENANT_ID)))
                .isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class)
                .hasMessageContaining("Cannot pause workflow not in progress");
    }

    @Test
    @DisplayName("YD-1: resume is rejected when workflow is not PAUSED")
    void resume_requiresPausedState() {
        AiWorkflowInstance workflow = createDraftWorkflow("resume-invalid");
        runPromise(() -> service.startWorkflow(workflow.id(), TENANT_ID));

        assertThatThrownBy(() -> runPromise(() -> service.resumeWorkflow(workflow.id(), TENANT_ID)))
                .isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class)
                .hasMessageContaining("Cannot resume workflow not paused");
    }

    @Test
    @DisplayName("YD-1: start is rejected for terminal workflow")
    void start_rejectsTerminalWorkflow() {
        AiWorkflowInstance workflow = createDraftWorkflow("terminal-start");
        runPromise(() -> workflowRepository.updateStatus(
                workflow.id(),
                TENANT_ID,
                AiWorkflowInstance.WorkflowStatus.COMPLETED));

        assertThatThrownBy(() -> runPromise(() -> service.startWorkflow(workflow.id(), TENANT_ID)))
                .isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class)
                .hasMessageContaining("Cannot start workflow in current state");
    }

    private AiWorkflowInstance createDraftWorkflow(String name) {
        return runPromise(() -> service.createWorkflow(new AiWorkflowService.CreateWorkflowRequest(
                TENANT_ID,
                name,
                "invariant test",
                AiWorkflowInstance.WorkflowType.TESTING,
                "tester")));
    }
}

