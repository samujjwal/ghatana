package com.ghatana.yappc.domain.workflow;

import com.ghatana.yappc.domain.agent.AgentRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for AiWorkflowService.
 *
 * @doc.type test
 * @doc.purpose Test workflow service operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiWorkflowService Tests")
class AiWorkflowServiceTest extends EventloopTestBase {

    private InMemoryAiWorkflowRepository workflowRepository;
    private InMemoryAiPlanRepository planRepository;
    private AgentRegistry agentRegistry;
    private AiWorkflowService service;

    private static final String TENANT_ID = "test-tenant";

    @BeforeEach
    void setUp() { 
        workflowRepository = new InMemoryAiWorkflowRepository(); 
        planRepository = new InMemoryAiPlanRepository(); 
        agentRegistry = mock(AgentRegistry.class); 
        service = new AiWorkflowService(workflowRepository, planRepository, agentRegistry); 
    }

    @Nested
    @DisplayName("Workflow CRUD Operations")
    class WorkflowCrudTests {

        @Test
        @DisplayName("should create workflow with DRAFT status")
        void shouldCreateWorkflowWithDraftStatus() { 
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Test Workflow",
                    "A test workflow description",
                    AiWorkflowInstance.WorkflowType.APP_CREATION,
                    "test-user"
                );

            // WHEN
            AiWorkflowInstance result = runPromise(() -> service.createWorkflow(request)); 

            // THEN
            assertThat(result).isNotNull(); 
            assertThat(result.id()).isNotEmpty(); 
            assertThat(result.name()).isEqualTo("Test Workflow");
            assertThat(result.description()).isEqualTo("A test workflow description");
            assertThat(result.type()).isEqualTo(AiWorkflowInstance.WorkflowType.APP_CREATION); 
            assertThat(result.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.DRAFT); 
            assertThat(result.tenantId()).isEqualTo(TENANT_ID); 
        }

        @Test
        @DisplayName("should retrieve workflow by ID")
        void shouldRetrieveWorkflowById() { 
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Get Test Workflow",
                    "Description",
                    AiWorkflowInstance.WorkflowType.BUG_FIX,
                    null
                );
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(request)); 

            // WHEN
            Optional<AiWorkflowInstance> result = runPromise(() -> 
                service.getWorkflow(created.id(), TENANT_ID) 
            );

            // THEN
            assertThat(result).isPresent(); 
            assertThat(result.get().id()).isEqualTo(created.id()); 
            assertThat(result.get().name()).isEqualTo("Get Test Workflow");
        }

        @Test
        @DisplayName("should return empty for non-existent workflow")
        void shouldReturnEmptyForNonExistentWorkflow() { 
            // WHEN
            Optional<AiWorkflowInstance> result = runPromise(() -> 
                service.getWorkflow("non-existent-id", TENANT_ID) 
            );

            // THEN
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("should list workflows by tenant")
        void shouldListWorkflowsByTenant() { 
            // GIVEN
            for (int i = 0; i < 5; i++) { 
                runPromise(() -> service.createWorkflow( 
                    new AiWorkflowService.CreateWorkflowRequest( 
                        TENANT_ID,
                        "Workflow " + System.currentTimeMillis(), 
                        "Description",
                        AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                        null
                    )
                ));
            }

            // WHEN
            List<AiWorkflowInstance> result = runPromise(() -> 
                service.listWorkflows(TENANT_ID, null, 10, 0) 
            );

            // THEN
            assertThat(result).hasSize(5); 
        }

        @Test
        @DisplayName("should filter workflows by status")
        void shouldFilterWorkflowsByStatus() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Draft Workflow",
                    "Description",
                    AiWorkflowInstance.WorkflowType.TESTING,
                    null
                )
            ));

            // Start one workflow to change its status
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); 

            // WHEN - filter by IN_PROGRESS
            List<AiWorkflowInstance> inProgress = runPromise(() -> 
                service.listWorkflows(TENANT_ID, AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, 10, 0) 
            );

            // THEN
            assertThat(inProgress).hasSize(1); 
            assertThat(inProgress.get(0).status()) 
                .isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); 
        }

        @Test
        @DisplayName("should delete workflow")
        void shouldDeleteWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "To Delete",
                    "Description",
                    AiWorkflowInstance.WorkflowType.CUSTOM,
                    null
                )
            ));

            // WHEN
            Boolean deleted = runPromise(() -> 
                service.deleteWorkflow(created.id(), TENANT_ID) 
            );

            // THEN
            assertThat(deleted).isTrue(); 
            assertThat(runPromise(() -> service.getWorkflow(created.id(), TENANT_ID))).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Workflow State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("should start draft workflow")
        void shouldStartDraftWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Start Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.DEPLOYMENT,
                    null
                )
            ));

            // WHEN
            AiWorkflowInstance started = runPromise(() -> 
                service.startWorkflow(created.id(), TENANT_ID) 
            );

            // THEN
            assertThat(started.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); 
        }

        @Test
        @DisplayName("should pause running workflow")
        void shouldPauseRunningWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Pause Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.REFACTORING,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); 

            // WHEN
            AiWorkflowInstance paused = runPromise(() -> 
                service.pauseWorkflow(created.id(), TENANT_ID) 
            );

            // THEN
            assertThat(paused.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.PAUSED); 
        }

        @Test
        @DisplayName("should resume paused workflow")
        void shouldResumePausedWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Resume Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.TESTING,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); 
            runPromise(() -> service.pauseWorkflow(created.id(), TENANT_ID)); 

            // WHEN
            AiWorkflowInstance resumed = runPromise(() -> 
                service.resumeWorkflow(created.id(), TENANT_ID) 
            );

            // THEN
            assertThat(resumed.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); 
        }

        @Test
        @DisplayName("should cancel workflow")
        void shouldCancelWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Cancel Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.BUG_FIX,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); 

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() -> 
                service.cancelWorkflow(created.id(), TENANT_ID, null, null) 
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED); 
            assertThat(cancelled.isTerminal()).isTrue(); 
        }

        @Test
        @DisplayName("should throw when starting already running workflow")
        void shouldThrowWhenStartingRunningWorkflow() { 
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID,
                    "Invalid Start Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.APP_CREATION,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); 

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)) 
            ).isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class); 
        }

        @Test
        @DisplayName("should throw for non-existent workflow")
        void shouldThrowForNonExistentWorkflow() { 
            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> service.startWorkflow("non-existent", TENANT_ID)) 
            ).isInstanceOf(AiWorkflowService.WorkflowNotFoundException.class); 
        }
    }

    @Nested
    @DisplayName("Workflow Progress")
    class WorkflowProgressTests {

        @Test
        @DisplayName("should calculate progress correctly")
        void shouldCalculateProgressCorrectly() { 
            // GIVEN
            AiWorkflowInstance workflow = new AiWorkflowInstance( 
                "test-id",
                TENANT_ID,
                "Progress Test",
                "Description",
                AiWorkflowInstance.WorkflowType.APP_CREATION,
                AiWorkflowInstance.WorkflowStatus.IN_PROGRESS,
                "step-3",
                2,
                5,
                java.util.Map.of(), 
                java.util.Map.of(), 
                null,
                null,
                java.time.Instant.now(), 
                java.time.Instant.now(), 
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // WHEN
            double progress = workflow.getProgress(); 

            // THEN
            assertThat(progress).isEqualTo(40.0); // 2/5 * 100 
        }

        @Test
        @DisplayName("should return zero progress for empty workflow")
        void shouldReturnZeroProgressForEmptyWorkflow() { 
            // GIVEN
            AiWorkflowInstance workflow = AiWorkflowInstance.create( 
                "test-id",
                TENANT_ID,
                "Empty Progress Test",
                "Description",
                AiWorkflowInstance.WorkflowType.TESTING
            );

            // WHEN
            double progress = workflow.getProgress(); 

            // THEN
            assertThat(progress).isEqualTo(0.0); 
        }
    }

    @Nested
    @DisplayName("Repository Status Counts")
    class RepositoryStatusCountsTests {

        @Test
        @DisplayName("should count workflows by status")
        void shouldCountWorkflowsByStatus() { 
            // GIVEN - create workflows in various states
            AiWorkflowInstance w1 = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID, "Draft 1", "Desc",
                    AiWorkflowInstance.WorkflowType.APP_CREATION, null
                )
            ));

            AiWorkflowInstance w2 = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID, "Started", "Desc",
                    AiWorkflowInstance.WorkflowType.BUG_FIX, null
                )
            ));
            runPromise(() -> service.startWorkflow(w2.id(), TENANT_ID)); 

            AiWorkflowInstance w3 = runPromise(() -> service.createWorkflow( 
                new AiWorkflowService.CreateWorkflowRequest( 
                    TENANT_ID, "Paused", "Desc",
                    AiWorkflowInstance.WorkflowType.TESTING, null
                )
            ));
            runPromise(() -> service.startWorkflow(w3.id(), TENANT_ID)); 
            runPromise(() -> service.pauseWorkflow(w3.id(), TENANT_ID)); 

            // WHEN
            AiWorkflowRepository.WorkflowStatusCounts counts = runPromise(() -> 
                workflowRepository.countByStatus(TENANT_ID) 
            );

            // THEN
            assertThat(counts.draft()).isEqualTo(1); 
            assertThat(counts.inProgress()).isEqualTo(1); 
            assertThat(counts.paused()).isEqualTo(1); 
            assertThat(counts.total()).isEqualTo(3); 
            assertThat(counts.active()).isEqualTo(1); // only in-progress 
        }
    }

    @Nested
    @DisplayName("Durable Workflow Cancellation")
    class WorkflowCancellationTests {

        @Test
        @DisplayName("should cancel IN_PROGRESS workflow with cooperative method")
        void shouldCancelInProgressWorkflowCooperatively() {
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "In-Progress Cancel", "Desc",
                    AiWorkflowInstance.WorkflowType.APP_CREATION, "user-1"
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID));

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() ->
                service.cancelWorkflow(created.id(), TENANT_ID, "user-1", "no longer needed")
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED);
            assertThat(cancelled.isTerminal()).isTrue();
            assertThat(cancelled.cancelRequestedAt()).isNotNull();
            assertThat(cancelled.cancelCompletedAt()).isNotNull();
            assertThat(cancelled.cancelRequestedBy()).isEqualTo("user-1");
            assertThat(cancelled.cancelReason()).isEqualTo("no longer needed");
            assertThat(cancelled.cancelMethod()).isEqualTo("cooperative");
        }

        @Test
        @DisplayName("should cancel PAUSED workflow with immediate method")
        void shouldCancelPausedWorkflowImmediately() {
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Paused Cancel", "Desc",
                    AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT, "user-2"
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID));
            runPromise(() -> service.pauseWorkflow(created.id(), TENANT_ID));

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() ->
                service.cancelWorkflow(created.id(), TENANT_ID, "user-2", "paused too long")
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED);
            assertThat(cancelled.cancelMethod()).isEqualTo("immediate");
            assertThat(cancelled.cancelRequestedBy()).isEqualTo("user-2");
            assertThat(cancelled.cancelReason()).isEqualTo("paused too long");
        }

        @Test
        @DisplayName("should cancel DRAFT workflow with immediate method")
        void shouldCancelDraftWorkflowImmediately() {
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Draft Cancel", "Desc",
                    AiWorkflowInstance.WorkflowType.REFACTORING, null
                )
            ));

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() ->
                service.cancelWorkflow(created.id(), TENANT_ID, null, "abandoned")
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED);
            assertThat(cancelled.cancelMethod()).isEqualTo("immediate");
            assertThat(cancelled.cancelRequestedBy()).isNull();
            assertThat(cancelled.cancelReason()).isEqualTo("abandoned");
        }

        @Test
        @DisplayName("should record cancelRequestedAt before cancelCompletedAt")
        void shouldRecordCancellationTimestampsInOrder() {
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Timestamp Cancel", "Desc",
                    AiWorkflowInstance.WorkflowType.TESTING, null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID));

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() ->
                service.cancelWorkflow(created.id(), TENANT_ID, "auditor", null)
            );

            // THEN — cancelRequestedAt must not be after cancelCompletedAt
            assertThat(cancelled.cancelRequestedAt()).isNotNull();
            assertThat(cancelled.cancelCompletedAt()).isNotNull();
            assertThat(cancelled.cancelRequestedAt())
                .isBeforeOrEqualTo(cancelled.cancelCompletedAt());
        }

        @Test
        @DisplayName("should throw WorkflowNotFoundException for unknown workflow")
        void shouldThrowForUnknownWorkflow() {
            // WHEN/THEN
            assertThatThrownBy(() ->
                runPromise(() -> service.cancelWorkflow("no-such-id", TENANT_ID, null, null))
            ).isInstanceOf(AiWorkflowService.WorkflowNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InvalidWorkflowStateException when cancelling a CANCELLED workflow")
        void shouldThrowWhenCancellingTerminalWorkflow() {
            // GIVEN — cancel once to reach terminal state
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Double Cancel", "Desc",
                    AiWorkflowInstance.WorkflowType.BUG_FIX, null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID));
            runPromise(() -> service.cancelWorkflow(created.id(), TENANT_ID, null, "first cancel"));

            // WHEN/THEN — second cancel must reject
            assertThatThrownBy(() ->
                runPromise(() -> service.cancelWorkflow(created.id(), TENANT_ID, null, "second cancel"))
            ).isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class);
        }

        @Test
        @DisplayName("should use hard_kill method when hard-kill timeout already elapsed")
        void shouldUseHardKillMethodWhenTimeoutElapsed() {
            // GIVEN — use a registry with an effectively zero hard-kill timeout
            WorkflowCancellationRegistry instantRegistry =
                new WorkflowCancellationRegistry(Duration.ofNanos(1));
            AiWorkflowService serviceWithFastTimeout =
                new AiWorkflowService(workflowRepository, planRepository, agentRegistry, instantRegistry);

            AiWorkflowInstance created = runPromise(() -> serviceWithFastTimeout.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Hard Kill Workflow", "Desc",
                    AiWorkflowInstance.WorkflowType.DEPLOYMENT, null
                )
            ));
            runPromise(() -> serviceWithFastTimeout.startWorkflow(created.id(), TENANT_ID));

            // Manually signal to simulate a cancel that was sent but agent hasn't exited yet
            instantRegistry.signal(created.id(), TENANT_ID);

            // Wait for the 1-nanosecond timeout to elapse
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // WHEN — cancel arrives after timeout is already exceeded
            AiWorkflowInstance cancelled = runPromise(() ->
                serviceWithFastTimeout.cancelWorkflow(created.id(), TENANT_ID, "admin", "force kill")
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED);
            assertThat(cancelled.cancelMethod()).isEqualTo("hard_kill");
        }

        @Test
        @DisplayName("should release cancellation signal after successful cancel")
        void shouldReleaseCancellationSignalAfterCancel() {
            // GIVEN
            WorkflowCancellationRegistry registry = new WorkflowCancellationRegistry();
            AiWorkflowService trackedService =
                new AiWorkflowService(workflowRepository, planRepository, agentRegistry, registry);

            AiWorkflowInstance created = runPromise(() -> trackedService.createWorkflow(
                new AiWorkflowService.CreateWorkflowRequest(
                    TENANT_ID, "Signal Release", "Desc",
                    AiWorkflowInstance.WorkflowType.CUSTOM, null
                )
            ));
            runPromise(() -> trackedService.startWorkflow(created.id(), TENANT_ID));

            // WHEN
            runPromise(() -> trackedService.cancelWorkflow(created.id(), TENANT_ID, null, null));

            // THEN — signal must be released so registry does not leak
            assertThat(registry.activeSignalCount()).isZero();
            assertThat(registry.isCancellationRequested(created.id(), TENANT_ID)).isFalse();
        }
    }
}
