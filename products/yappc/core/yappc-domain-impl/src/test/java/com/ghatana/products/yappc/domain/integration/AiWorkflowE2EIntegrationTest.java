package com.ghatana.products.yappc.domain.integration;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for AI Workflow E2E flows.
 * Tests the complete workflow from creation to completion.
 *
 * @doc.type test
 * @doc.purpose Integration test for AI workflow
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("AI Workflow E2E Integration Tests")
class AiWorkflowE2EIntegrationTest extends EventloopTestBase {

    private AiWorkflowRepository workflowRepository;
    private AiPlanRepository planRepository;
    private AgentRegistry agentRegistry;
    private AiWorkflowService workflowService;

    private static final String TENANT_ID = "test-tenant";

    @BeforeEach
    void setUp() { // GH-90000
        workflowRepository = new InMemoryAiWorkflowRepository(); // GH-90000
        planRepository = new InMemoryAiPlanRepository(); // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        agentRegistry = new AgentRegistry(metricsCollector); // GH-90000
        workflowService = new AiWorkflowService(workflowRepository, planRepository, agentRegistry); // GH-90000
    }

    @Nested
    @DisplayName("Complete Workflow Lifecycle")
    class CompleteWorkflowLifecycleTests {

        @Test
        @DisplayName("should create workflow in DRAFT state")
        void shouldCreateWorkflowInDraftState() { // GH-90000
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                    new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                            TENANT_ID,
                            "Feature: Login Form",
                            "Create a login form with email and password fields",
                            AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                            "test-user"
                    );

            // WHEN
            AiWorkflowInstance workflow = runPromise( // GH-90000
                    () -> workflowService.createWorkflow(request) // GH-90000
            );

            // THEN
            assertThat(workflow).isNotNull(); // GH-90000
            assertThat(workflow.id()).isNotBlank(); // GH-90000
            assertThat(workflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.DRAFT); // GH-90000
            assertThat(workflow.tenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(workflow.name()).isEqualTo("Feature: Login Form");
            assertThat(workflow.type()).isEqualTo(AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT); // GH-90000
        }

        @Test
        @DisplayName("should generate plan for workflow")
        void shouldGeneratePlanForWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Generate plan test");

            // WHEN
            AiPlan plan = runPromise( // GH-90000
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Build a REST API") // GH-90000
            );

            // THEN
            assertThat(plan).isNotNull(); // GH-90000
            assertThat(plan.workflowId()).isEqualTo(workflow.id()); // GH-90000
            assertThat(plan.steps()).isNotEmpty(); // GH-90000
            assertThat(plan.objective()).isEqualTo("Build a REST API");
        }

        @Test
        @DisplayName("should approve plan and start workflow")
        void shouldApprovePlanAndStartWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Approve and start test");
            AiPlan plan = runPromise( // GH-90000
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Test objective") // GH-90000
            );

            // WHEN - Approve plan
            AiPlan approvedPlan = runPromise( // GH-90000
                    () -> workflowService.approvePlan(plan.id(), TENANT_ID) // GH-90000
            );

            assertThat(approvedPlan.status()).isEqualTo(AiPlan.PlanStatus.APPROVED); // GH-90000

            // AND - Start workflow
            AiWorkflowInstance startedWorkflow = runPromise( // GH-90000
                    () -> workflowService.startWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(startedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); // GH-90000
        }

        @Test
        @DisplayName("should handle workflow pause and resume")
        void shouldHandleWorkflowPauseAndResume() { // GH-90000
            // GIVEN - Start workflow
            AiWorkflowInstance workflow = createAndStartWorkflow("Pause resume test");

            // WHEN - Pause
            AiWorkflowInstance pausedWorkflow = runPromise( // GH-90000
                    () -> workflowService.pauseWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            assertThat(pausedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.PAUSED); // GH-90000

            // AND - Resume
            AiWorkflowInstance resumedWorkflow = runPromise( // GH-90000
                    () -> workflowService.resumeWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(resumedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); // GH-90000
        }

        @Test
        @DisplayName("should handle workflow cancellation")
        void shouldHandleWorkflowCancellation() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Cancel test");

            // WHEN
            AiWorkflowInstance cancelledWorkflow = runPromise( // GH-90000
                    () -> workflowService.cancelWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(cancelledWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED); // GH-90000
        }
    }

    @Nested
    @DisplayName("Plan Modification")
    class PlanModificationTests {

        @Test
        @DisplayName("should modify plan steps before approval")
        void shouldModifyPlanStepsBeforeApproval() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Modify plan test");
            AiPlan plan = runPromise( // GH-90000
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Refactor service") // GH-90000
            );

            // WHEN - Modify steps
            List<AiPlan.PlanStep> modifiedSteps = List.of( // GH-90000
                    new AiPlan.PlanStep( // GH-90000
                            "step-1",
                            "Analyze dependencies",
                            "Review all service dependencies",
                            AiPlan.PlanStep.StepType.CONTEXT_GATHERING,
                            0,
                            List.of(), // GH-90000
                            "Identify constraints",
                            null, null, true, null
                    ),
                    new AiPlan.PlanStep( // GH-90000
                            "step-2",
                            "Apply refactoring",
                            "Execute refactoring patterns",
                            AiPlan.PlanStep.StepType.CODE_GENERATION,
                            1,
                            List.of("step-1"),
                            "Implement changes",
                            null, null, false, null
                    )
            );

            AiPlan modifiedPlan = runPromise( // GH-90000
                    () -> workflowService.modifyPlanSteps(plan.id(), TENANT_ID, modifiedSteps) // GH-90000
            );

            // THEN
            assertThat(modifiedPlan.status()).isEqualTo(AiPlan.PlanStatus.MODIFIED); // GH-90000
        }

        @Test
        @DisplayName("should reject plan")
        void shouldRejectPlan() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Reject plan test");
            AiPlan plan = runPromise( // GH-90000
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Generate docs") // GH-90000
            );

            // WHEN
            AiPlan rejectedPlan = runPromise( // GH-90000
                    () -> workflowService.rejectPlan(plan.id(), TENANT_ID, "Plan too complex") // GH-90000
            );

            // THEN
            assertThat(rejectedPlan.status()).isEqualTo(AiPlan.PlanStatus.REJECTED); // GH-90000
        }
    }

    @Nested
    @DisplayName("Workflow Queries")
    class WorkflowQueryTests {

        @Test
        @DisplayName("should list workflows by tenant")
        void shouldListWorkflowsByTenant() { // GH-90000
            // GIVEN - Create multiple workflows
            createTestWorkflow("Workflow A");
            createTestWorkflow("Workflow B");
            createTestWorkflow("Workflow C");

            // WHEN
            List<AiWorkflowInstance> workflows = runPromise( // GH-90000
                    () -> workflowService.listWorkflows(TENANT_ID, null, 10, 0) // GH-90000
            );

            // THEN
            assertThat(workflows).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("should get workflow by ID")
        void shouldGetWorkflowById() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Get by ID test");

            // WHEN
            Optional<AiWorkflowInstance> found = runPromise( // GH-90000
                    () -> workflowService.getWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().id()).isEqualTo(workflow.id()); // GH-90000
            assertThat(found.get().name()).isEqualTo("Get by ID test");
        }

        @Test
        @DisplayName("should delete workflow")
        void shouldDeleteWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Delete test");

            // WHEN
            boolean deleted = runPromise( // GH-90000
                    () -> workflowService.deleteWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(deleted).isTrue(); // GH-90000

            Optional<AiWorkflowInstance> found = runPromise( // GH-90000
                    () -> workflowService.getWorkflow(workflow.id(), TENANT_ID) // GH-90000
            );
            assertThat(found).isEmpty(); // GH-90000
        }
    }

    // ==================== HELPER METHODS ====================

    private AiWorkflowInstance createTestWorkflow(String name) { // GH-90000
        AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                        TENANT_ID,
                        name,
                        "Test workflow: " + name,
                        AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                        "test-user"
                );
        return runPromise(() -> workflowService.createWorkflow(request)); // GH-90000
    }

    private AiWorkflowInstance createAndStartWorkflow(String name) { // GH-90000
        AiWorkflowInstance workflow = createTestWorkflow(name); // GH-90000
        AiPlan plan = runPromise( // GH-90000
                () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Test objective for " + name) // GH-90000
        );
        runPromise(() -> workflowService.approvePlan(plan.id(), TENANT_ID)); // GH-90000
        return runPromise(() -> workflowService.startWorkflow(workflow.id(), TENANT_ID)); // GH-90000
    }
}
