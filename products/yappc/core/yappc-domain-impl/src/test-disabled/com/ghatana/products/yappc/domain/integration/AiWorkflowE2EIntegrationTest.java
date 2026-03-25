package com.ghatana.products.yappc.domain.integration;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.workflow.*;
import io.activej.promise.Promise;
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
    void setUp() {
        workflowRepository = new InMemoryAiWorkflowRepository();
        planRepository = new InMemoryAiPlanRepository();
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        agentRegistry = new AgentRegistry(metricsCollector);
        workflowService = new AiWorkflowService(workflowRepository, planRepository, agentRegistry);
    }

    @Nested
    @DisplayName("Complete Workflow Lifecycle")
    class CompleteWorkflowLifecycleTests {

        @Test
        @DisplayName("should create workflow in DRAFT state")
        void shouldCreateWorkflowInDraftState() {
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                    new AiWorkflowService.CreateWorkflowRequest(
                            TENANT_ID,
                            "Feature: Login Form",
                            "Create a login form with email and password fields",
                            AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                            "test-user"
                    );

            // WHEN
            AiWorkflowInstance workflow = runPromise(
                    () -> workflowService.createWorkflow(request)
            );

            // THEN
            assertThat(workflow).isNotNull();
            assertThat(workflow.id()).isNotBlank();
            assertThat(workflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.DRAFT);
            assertThat(workflow.tenantId()).isEqualTo(TENANT_ID);
            assertThat(workflow.name()).isEqualTo("Feature: Login Form");
            assertThat(workflow.type()).isEqualTo(AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT);
        }

        @Test
        @DisplayName("should generate plan for workflow")
        void shouldGeneratePlanForWorkflow() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Generate plan test");

            // WHEN
            AiPlan plan = runPromise(
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Build a REST API")
            );

            // THEN
            assertThat(plan).isNotNull();
            assertThat(plan.workflowId()).isEqualTo(workflow.id());
            assertThat(plan.steps()).isNotEmpty();
            assertThat(plan.objective()).isEqualTo("Build a REST API");
        }

        @Test
        @DisplayName("should approve plan and start workflow")
        void shouldApprovePlanAndStartWorkflow() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Approve and start test");
            AiPlan plan = runPromise(
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Test objective")
            );

            // WHEN - Approve plan
            AiPlan approvedPlan = runPromise(
                    () -> workflowService.approvePlan(plan.id(), TENANT_ID)
            );

            assertThat(approvedPlan.status()).isEqualTo(AiPlan.PlanStatus.APPROVED);

            // AND - Start workflow
            AiWorkflowInstance startedWorkflow = runPromise(
                    () -> workflowService.startWorkflow(workflow.id(), TENANT_ID)
            );

            // THEN
            assertThat(startedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("should handle workflow pause and resume")
        void shouldHandleWorkflowPauseAndResume() {
            // GIVEN - Start workflow
            AiWorkflowInstance workflow = createAndStartWorkflow("Pause resume test");

            // WHEN - Pause
            AiWorkflowInstance pausedWorkflow = runPromise(
                    () -> workflowService.pauseWorkflow(workflow.id(), TENANT_ID)
            );

            assertThat(pausedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.PAUSED);

            // AND - Resume
            AiWorkflowInstance resumedWorkflow = runPromise(
                    () -> workflowService.resumeWorkflow(workflow.id(), TENANT_ID)
            );

            // THEN
            assertThat(resumedWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("should handle workflow cancellation")
        void shouldHandleWorkflowCancellation() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Cancel test");

            // WHEN
            AiWorkflowInstance cancelledWorkflow = runPromise(
                    () -> workflowService.cancelWorkflow(workflow.id(), TENANT_ID)
            );

            // THEN
            assertThat(cancelledWorkflow.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Plan Modification")
    class PlanModificationTests {

        @Test
        @DisplayName("should modify plan steps before approval")
        void shouldModifyPlanStepsBeforeApproval() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Modify plan test");
            AiPlan plan = runPromise(
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Refactor service")
            );

            // WHEN - Modify steps
            List<AiPlan.PlanStep> modifiedSteps = List.of(
                    new AiPlan.PlanStep(
                            "step-1",
                            "Analyze dependencies",
                            "Review all service dependencies",
                            AiPlan.PlanStep.StepType.CONTEXT_GATHERING,
                            0,
                            List.of(),
                            "Identify constraints",
                            null, null, true, null
                    ),
                    new AiPlan.PlanStep(
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

            AiPlan modifiedPlan = runPromise(
                    () -> workflowService.modifyPlanSteps(plan.id(), TENANT_ID, modifiedSteps)
            );

            // THEN
            assertThat(modifiedPlan.status()).isEqualTo(AiPlan.PlanStatus.MODIFIED);
        }

        @Test
        @DisplayName("should reject plan")
        void shouldRejectPlan() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Reject plan test");
            AiPlan plan = runPromise(
                    () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Generate docs")
            );

            // WHEN
            AiPlan rejectedPlan = runPromise(
                    () -> workflowService.rejectPlan(plan.id(), TENANT_ID, "Plan too complex")
            );

            // THEN
            assertThat(rejectedPlan.status()).isEqualTo(AiPlan.PlanStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("Workflow Queries")
    class WorkflowQueryTests {

        @Test
        @DisplayName("should list workflows by tenant")
        void shouldListWorkflowsByTenant() {
            // GIVEN - Create multiple workflows
            createTestWorkflow("Workflow A");
            createTestWorkflow("Workflow B");
            createTestWorkflow("Workflow C");

            // WHEN
            List<AiWorkflowInstance> workflows = runPromise(
                    () -> workflowService.listWorkflows(TENANT_ID, null, 10, 0)
            );

            // THEN
            assertThat(workflows).hasSize(3);
        }

        @Test
        @DisplayName("should get workflow by ID")
        void shouldGetWorkflowById() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Get by ID test");

            // WHEN
            Optional<AiWorkflowInstance> found = runPromise(
                    () -> workflowService.getWorkflow(workflow.id(), TENANT_ID)
            );

            // THEN
            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(workflow.id());
            assertThat(found.get().name()).isEqualTo("Get by ID test");
        }

        @Test
        @DisplayName("should delete workflow")
        void shouldDeleteWorkflow() {
            // GIVEN
            AiWorkflowInstance workflow = createTestWorkflow("Delete test");

            // WHEN
            boolean deleted = runPromise(
                    () -> workflowService.deleteWorkflow(workflow.id(), TENANT_ID)
            );

            // THEN
            assertThat(deleted).isTrue();

            Optional<AiWorkflowInstance> found = runPromise(
                    () -> workflowService.getWorkflow(workflow.id(), TENANT_ID)
            );
            assertThat(found).isEmpty();
        }
    }

    // ==================== HELPER METHODS ====================

    private AiWorkflowInstance createTestWorkflow(String name) {
        AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest(
                        TENANT_ID,
                        name,
                        "Test workflow: " + name,
                        AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                        "test-user"
                );
        return runPromise(() -> workflowService.createWorkflow(request));
    }

    private AiWorkflowInstance createAndStartWorkflow(String name) {
        AiWorkflowInstance workflow = createTestWorkflow(name);
        AiPlan plan = runPromise(
                () -> workflowService.generatePlan(workflow.id(), TENANT_ID, "Test objective for " + name)
        );
        runPromise(() -> workflowService.approvePlan(plan.id(), TENANT_ID));
        return runPromise(() -> workflowService.startWorkflow(workflow.id(), TENANT_ID));
    }
}
