package com.ghatana.virtualorg.framework.integration;

import com.ghatana.core.operator.catalog.InMemoryOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.workflow.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WorkflowPipelineAdapter.
 *
 * <p>Tests validate:
 * - Workflow to pipeline conversion
 * - Trigger event mapping
 * - Workflow step to operator mapping
 * - Error handling configuration
 * - Pipeline metadata enrichment
 *
 * @doc.type test
 * @doc.purpose Validate workflow-to-pipeline conversion
 * @doc.layer product
 */
@DisplayName("WorkflowPipelineAdapter Tests")
class WorkflowPipelineAdapterTest extends EventloopTestBase {

    private OperatorCatalog operatorCatalog;
    private WorkflowPipelineAdapter adapter;

    private static final String TENANT_ID = "test-tenant";

    @BeforeEach
    void setUp() {
        operatorCatalog = new InMemoryOperatorCatalog();
        adapter = new WorkflowPipelineAdapter(operatorCatalog);
    }

    /**
     * Test basic workflow to pipeline conversion.
     *
     * GIVEN: simple workflow with 2 steps
     * WHEN: converting to pipeline
     * THEN: pipeline created with correct structure
     */
    @Test
    @DisplayName("Should convert workflow to pipeline")
    void shouldConvertWorkflowToPipeline() {
        // GIVEN: Simple workflow
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            .id("sprint-planning")
            .name("Sprint Planning")
            .version("1.0.0")
            .description("Plan sprint with backlog and capacity")
            .triggerEvent("sprint.started")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "load-backlog",
                "Load backlog items",
                "ProductManager",
                60
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "assign-tasks",
                "Assign tasks to team",
                "CTO",
                120
            ))
            .build();

        // WHEN: converting to pipeline
        Pipeline pipeline = runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));

        // THEN: pipeline created successfully
        assertThat(pipeline).as("Pipeline should be created").isNotNull();
        assertThat(pipeline.getId()).as("Pipeline ID should be set").isNotNull();

        PipelineId expectedId = PipelineId.of(
            TENANT_ID,
            "virtualorg.workflow",
            "sprint-planning",
            "1.0.0"
        );

        assertThat(pipeline.getId())
            .as("Pipeline ID should match workflow")
            .isEqualTo(expectedId.value());

        assertThat(pipeline.getName())
            .as("Pipeline name should match workflow")
            .isEqualTo("Sprint Planning");

        assertThat(pipeline.getDescription())
            .as("Pipeline description should reference workflow")
            .contains("Auto-generated from workflow: Sprint Planning");
    }

    /**
     * Test workflow with multiple steps.
     *
     * GIVEN: workflow with 5 sequential steps
     * WHEN: converting to pipeline
     * THEN: pipeline has 6 stages (1 filter + 5 map)
     */
    @Test
    @DisplayName("Should convert multi-step workflow")
    void shouldConvertMultiStepWorkflow() {
        // GIVEN: Complex workflow
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            .id("code-review")
            .name("Code Review Process")
            .version("1.0.0")
            .triggerEvent("pull_request.created")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "assign-reviewer",
                "Assign code reviewer",
                "ArchitectLead",
                30
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "review-code",
                "Review code changes",
                "SeniorEngineer",
                120
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "run-tests",
                "Run automated tests",
                "DevOpsEngineer",
                300
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "merge-pr",
                "Merge pull request",
                "ArchitectLead",
                60
            ))
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "deploy",
                "Deploy to staging",
                "DevOpsLead",
                180
            ))
            .build();

        // WHEN: converting to pipeline
        Pipeline pipeline = runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));

        // THEN: pipeline has correct number of stages
        assertThat(pipeline).as("Pipeline should be created").isNotNull();

        // Filter stage + 5 map stages + 1 DLQ = 7 total
        assertThat(pipeline.getStages())
            .as("Pipeline should have 7 stages (1 filter + 5 map + 1 DLQ)")
            .hasSize(7);
    }

    /**
     * Test workflow name sanitization.
     *
     * GIVEN: workflow with special characters in name
     * WHEN: converting to pipeline
     * THEN: pipeline ID sanitized correctly
     */
    @Test
    @DisplayName("Should sanitize workflow name for pipeline ID")
    void shouldSanitizeWorkflowName() {
        // GIVEN: Workflow with special characters
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            .id("test-workflow")
            .name("Sprint Planning & Review Process!")
            .version("1.0.0")
            .triggerEvent("event.test")
            .addStep(WorkflowDefinition.WorkflowStep.of(
                "step1",
                "Test step",
                "CEO",
                60
            ))
            .build();

        // WHEN: converting to pipeline
        Pipeline pipeline = runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));

        // THEN: pipeline ID contains sanitized name, display name stays raw
        assertThat(pipeline.getId())
            .as("Pipeline ID should contain sanitized workflow name")
            .contains("sprint-planning--review-process");

        assertThat(pipeline.getName())
            .as("Pipeline name should preserve original workflow name")
            .isEqualTo("Sprint Planning & Review Process!");
    }

    /**
     * Test null workflow rejection.
     *
     * GIVEN: null workflow
     * WHEN: converting to pipeline
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject null workflow")
    void shouldRejectNullWorkflow() {
        assertThatThrownBy(() -> adapter.toPipeline(null, TENANT_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("WorkflowDefinition required");
    }

    /**
     * Test null tenantId rejection.
     *
     * GIVEN: valid workflow but null tenantId
     * WHEN: converting to pipeline
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject null tenantId")
    void shouldRejectNullTenantId() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .triggerEvent("test.event")
            .build();

        assertThatThrownBy(() -> adapter.toPipeline(workflow, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId required");
    }

    /**
     * Test empty workflow (no steps).
     *
     * GIVEN: workflow with no steps
     * WHEN: converting to pipeline
     * THEN: pipeline created with only filter stage
     */
    @Test
    @DisplayName("Should handle workflow with no steps")
    void shouldHandleEmptyWorkflow() {
        // GIVEN: Workflow with no steps
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            .id("empty-workflow")
            .name("Empty Workflow")
            .version("1.0.0")
            .triggerEvent("test.event")
            .build();

        // WHEN: converting to pipeline
        Pipeline pipeline = runPromise(() -> adapter.toPipeline(workflow, TENANT_ID));

        // THEN: pipeline created with filter + DLQ stages
        assertThat(pipeline).as("Pipeline should be created").isNotNull();
        assertThat(pipeline.getStages())
            .as("Pipeline should have filter + DLQ stages")
            .hasSize(2);
    }
}

