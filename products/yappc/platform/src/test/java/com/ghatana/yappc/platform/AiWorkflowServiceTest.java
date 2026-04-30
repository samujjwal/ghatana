package com.ghatana.yappc.platform;

import com.ghatana.yappc.domain.agent.AgentRegistry;
import com.ghatana.yappc.domain.workflow.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    void setUp() { // GH-90000
        workflowRepository = new InMemoryAiWorkflowRepository(); // GH-90000
        planRepository = new InMemoryAiPlanRepository(); // GH-90000
        agentRegistry = mock(AgentRegistry.class); // GH-90000
        service = new AiWorkflowService(workflowRepository, planRepository, agentRegistry); // GH-90000
    }

    @Nested
    @DisplayName("Workflow CRUD Operations")
    class WorkflowCrudTests {

        @Test
        @DisplayName("should create workflow with DRAFT status")
        void shouldCreateWorkflowWithDraftStatus() { // GH-90000
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Test Workflow",
                    "A test workflow description",
                    AiWorkflowInstance.WorkflowType.APP_CREATION,
                    "test-user"
                );

            // WHEN
            AiWorkflowInstance result = runPromise(() -> service.createWorkflow(request)); // GH-90000

            // THEN
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.id()).isNotEmpty(); // GH-90000
            assertThat(result.name()).isEqualTo("Test Workflow");
            assertThat(result.description()).isEqualTo("A test workflow description");
            assertThat(result.type()).isEqualTo(AiWorkflowInstance.WorkflowType.APP_CREATION); // GH-90000
            assertThat(result.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.DRAFT); // GH-90000
            assertThat(result.tenantId()).isEqualTo(TENANT_ID); // GH-90000
        }

        @Test
        @DisplayName("should retrieve workflow by ID")
        void shouldRetrieveWorkflowById() { // GH-90000
            // GIVEN
            AiWorkflowService.CreateWorkflowRequest request =
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Get Test Workflow",
                    "Description",
                    AiWorkflowInstance.WorkflowType.BUG_FIX,
                    null
                );
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow(request)); // GH-90000

            // WHEN
            Optional<AiWorkflowInstance> result = runPromise(() -> // GH-90000
                service.getWorkflow(created.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(created.id()); // GH-90000
            assertThat(result.get().name()).isEqualTo("Get Test Workflow");
        }

        @Test
        @DisplayName("should return empty for non-existent workflow")
        void shouldReturnEmptyForNonExistentWorkflow() { // GH-90000
            // WHEN
            Optional<AiWorkflowInstance> result = runPromise(() -> // GH-90000
                service.getWorkflow("non-existent-id", TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should list workflows by tenant")
        void shouldListWorkflowsByTenant() { // GH-90000
            // GIVEN
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> service.createWorkflow( // GH-90000
                    new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                        TENANT_ID,
                        "Workflow " + System.currentTimeMillis(), // GH-90000
                        "Description",
                        AiWorkflowInstance.WorkflowType.FEATURE_DEVELOPMENT,
                        null
                    )
                ));
            }

            // WHEN
            List<AiWorkflowInstance> result = runPromise(() -> // GH-90000
                service.listWorkflows(TENANT_ID, null, 10, 0) // GH-90000
            );

            // THEN
            assertThat(result).hasSize(5); // GH-90000
        }

        @Test
        @DisplayName("should filter workflows by status")
        void shouldFilterWorkflowsByStatus() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Draft Workflow",
                    "Description",
                    AiWorkflowInstance.WorkflowType.TESTING,
                    null
                )
            ));

            // Start one workflow to change its status
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); // GH-90000

            // WHEN - filter by IN_PROGRESS
            List<AiWorkflowInstance> inProgress = runPromise(() -> // GH-90000
                service.listWorkflows(TENANT_ID, AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, 10, 0) // GH-90000
            );

            // THEN
            assertThat(inProgress).hasSize(1); // GH-90000
            assertThat(inProgress.get(0).status()) // GH-90000
                .isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); // GH-90000
        }

        @Test
        @DisplayName("should delete workflow")
        void shouldDeleteWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "To Delete",
                    "Description",
                    AiWorkflowInstance.WorkflowType.CUSTOM,
                    null
                )
            ));

            // WHEN
            Boolean deleted = runPromise(() -> // GH-90000
                service.deleteWorkflow(created.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(deleted).isTrue(); // GH-90000
            assertThat(runPromise(() -> service.getWorkflow(created.id(), TENANT_ID))).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Workflow State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("should start draft workflow")
        void shouldStartDraftWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Start Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.DEPLOYMENT,
                    null
                )
            ));

            // WHEN
            AiWorkflowInstance started = runPromise(() -> // GH-90000
                service.startWorkflow(created.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(started.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); // GH-90000
        }

        @Test
        @DisplayName("should pause running workflow")
        void shouldPauseRunningWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Pause Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.REFACTORING,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); // GH-90000

            // WHEN
            AiWorkflowInstance paused = runPromise(() -> // GH-90000
                service.pauseWorkflow(created.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(paused.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.PAUSED); // GH-90000
        }

        @Test
        @DisplayName("should resume paused workflow")
        void shouldResumePausedWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Resume Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.TESTING,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); // GH-90000
            runPromise(() -> service.pauseWorkflow(created.id(), TENANT_ID)); // GH-90000

            // WHEN
            AiWorkflowInstance resumed = runPromise(() -> // GH-90000
                service.resumeWorkflow(created.id(), TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(resumed.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.IN_PROGRESS); // GH-90000
        }

        @Test
        @DisplayName("should cancel workflow")
        void shouldCancelWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Cancel Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.BUG_FIX,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); // GH-90000

            // WHEN
            AiWorkflowInstance cancelled = runPromise(() -> // GH-90000
                service.cancelWorkflow(created.id(), TENANT_ID, "test-user", "test reason") // GH-90000
            );

            // THEN
            assertThat(cancelled.status()).isEqualTo(AiWorkflowInstance.WorkflowStatus.CANCELLED); // GH-90000
            assertThat(cancelled.isTerminal()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should throw when starting already running workflow")
        void shouldThrowWhenStartingRunningWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance created = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID,
                    "Invalid Start Test",
                    "Description",
                    AiWorkflowInstance.WorkflowType.APP_CREATION,
                    null
                )
            ));
            runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.startWorkflow(created.id(), TENANT_ID)) // GH-90000
            ).isInstanceOf(AiWorkflowService.InvalidWorkflowStateException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw for non-existent workflow")
        void shouldThrowForNonExistentWorkflow() { // GH-90000
            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.startWorkflow("non-existent", TENANT_ID)) // GH-90000
            ).isInstanceOf(AiWorkflowService.WorkflowNotFoundException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("Workflow Progress")
    class WorkflowProgressTests {

        @Test
        @DisplayName("should calculate progress correctly")
        void shouldCalculateProgressCorrectly() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = new AiWorkflowInstance( // GH-90000
                "test-id",
                TENANT_ID,
                "Progress Test",
                "Description",
                AiWorkflowInstance.WorkflowType.APP_CREATION,
                AiWorkflowInstance.WorkflowStatus.IN_PROGRESS,
                "step-3",
                2,
                5,
                java.util.Map.of(), // GH-90000
                java.util.Map.of(), // GH-90000
                null,
                null,
                java.time.Instant.now(), // GH-90000
                java.time.Instant.now(), // GH-90000
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            // WHEN
            double progress = workflow.getProgress(); // GH-90000

            // THEN
            assertThat(progress).isEqualTo(40.0); // 2/5 * 100 // GH-90000
        }

        @Test
        @DisplayName("should return zero progress for empty workflow")
        void shouldReturnZeroProgressForEmptyWorkflow() { // GH-90000
            // GIVEN
            AiWorkflowInstance workflow = AiWorkflowInstance.create( // GH-90000
                "test-id",
                TENANT_ID,
                "Empty Progress Test",
                "Description",
                AiWorkflowInstance.WorkflowType.TESTING
            );

            // WHEN
            double progress = workflow.getProgress(); // GH-90000

            // THEN
            assertThat(progress).isEqualTo(0.0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Repository Status Counts")
    class RepositoryStatusCountsTests {

        @Test
        @DisplayName("should count workflows by status")
        void shouldCountWorkflowsByStatus() { // GH-90000
            // GIVEN - create workflows in various states
            AiWorkflowInstance w1 = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID, "Draft 1", "Desc",
                    AiWorkflowInstance.WorkflowType.APP_CREATION, null
                )
            ));

            AiWorkflowInstance w2 = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID, "Started", "Desc",
                    AiWorkflowInstance.WorkflowType.BUG_FIX, null
                )
            ));
            runPromise(() -> service.startWorkflow(w2.id(), TENANT_ID)); // GH-90000

            AiWorkflowInstance w3 = runPromise(() -> service.createWorkflow( // GH-90000
                new AiWorkflowService.CreateWorkflowRequest( // GH-90000
                    TENANT_ID, "Paused", "Desc",
                    AiWorkflowInstance.WorkflowType.TESTING, null
                )
            ));
            runPromise(() -> service.startWorkflow(w3.id(), TENANT_ID)); // GH-90000
            runPromise(() -> service.pauseWorkflow(w3.id(), TENANT_ID)); // GH-90000

            // WHEN
            AiWorkflowRepository.WorkflowStatusCounts counts = runPromise(() -> // GH-90000
                workflowRepository.countByStatus(TENANT_ID) // GH-90000
            );

            // THEN
            assertThat(counts.draft()).isEqualTo(1); // GH-90000
            assertThat(counts.inProgress()).isEqualTo(1); // GH-90000
            assertThat(counts.paused()).isEqualTo(1); // GH-90000
            assertThat(counts.total()).isEqualTo(3); // GH-90000
            assertThat(counts.active()).isEqualTo(1); // only in-progress // GH-90000
        }
    }
}
