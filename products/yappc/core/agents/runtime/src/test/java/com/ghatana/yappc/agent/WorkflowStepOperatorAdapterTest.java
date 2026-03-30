package com.ghatana.yappc.agent;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorConfig;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorState;
import com.ghatana.platform.workflow.operator.OperatorType;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WorkflowStepOperatorAdapter} — the adapter that exposes a YAPPC
 * {@link WorkflowStep} as a platform {@link com.ghatana.platform.workflow.operator.UnifiedOperator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for WorkflowStepOperatorAdapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("WorkflowStepOperatorAdapter Tests")
class WorkflowStepOperatorAdapterTest extends EventloopTestBase {

    private static final String STEP_NAME = "architecture.intake";
    private static final StepContract CONTRACT = new StepContract(
            STEP_NAME,
            "#/definitions/ArchitectureInput",
            "#/definitions/ArchitectureOutput",
            List.of("DATA_CLOUD", "EVENT_LOG"),
            Map.of("description", "Architecture intake step", "version", "1.0.0"));

    /** A simple WorkflowStep that echoes the input as output. */
    private WorkflowStep<String, String> echoStep;
    /** A WorkflowStep that always fails validation. */
    private WorkflowStep<String, String> invalidatingStep;
    /** A WorkflowStep whose execute always returns a failed StepResult. */
    private WorkflowStep<String, String> failingStep;

    private WorkflowStepOperatorAdapter<String, String> adapter;

    @BeforeEach
    void setUp() {
        echoStep = new WorkflowStep<>() {
            @Override
            public String stepName() { return STEP_NAME; }

            @Override
            public StepContract contract() { return CONTRACT; }

            @Override
            public ValidationResult validateInput(String input) {
                return ValidationResult.success();
            }

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) {
                return Promise.of(StepResult.success(
                        "echo:" + input,
                        Map.of("tenant", context.tenantId()),
                        Instant.now(),
                        Instant.now()));
            }
        };

        invalidatingStep = new WorkflowStep<>() {
            @Override
            public String stepName() { return STEP_NAME; }

            @Override
            public StepContract contract() { return CONTRACT; }

            @Override
            public ValidationResult validateInput(String input) {
                return ValidationResult.fail("input is too short", "schema mismatch");
            }

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) {
                return Promise.of(StepResult.success("never-called", Map.of(), Instant.now(), Instant.now()));
            }
        };

        failingStep = new WorkflowStep<>() {
            @Override
            public String stepName() { return STEP_NAME; }

            @Override
            public StepContract contract() { return CONTRACT; }

            @Override
            public ValidationResult validateInput(String input) { return ValidationResult.success(); }

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) {
                return Promise.of(StepResult.failed(
                        List.of("downstream service unavailable"),
                        Map.of(),
                        Instant.now(),
                        Instant.now()));
            }
        };

        adapter = new WorkflowStepOperatorAdapter<>(echoStep);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Identity and metadata")
    class Identity {

        @Test
        @DisplayName("getId returns operatorId derived from step name")
        void getIdReflectsStepName() {
            assertThat(adapter.getId().toString())
                    .contains(STEP_NAME)
                    .startsWith("yappc:workflow:");
        }

        @Test
        @DisplayName("getName returns the step name")
        void getNameReturnsStepName() {
            assertThat(adapter.getName()).isEqualTo(STEP_NAME);
        }

        @Test
        @DisplayName("getType returns STREAM")
        void getTypeIsStream() {
            assertThat(adapter.getType()).isEqualTo(OperatorType.STREAM);
        }

        @Test
        @DisplayName("getVersion returns 1.0.0")
        void getVersion() {
            assertThat(adapter.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("getDescription references step name and contract schemas")
        void getDescriptionContainsContractSchemas() {
            String desc = adapter.getDescription();
            assertThat(desc)
                    .contains(STEP_NAME)
                    .contains(CONTRACT.inputSchemaRef())
                    .contains(CONTRACT.outputSchemaRef());
        }

        @Test
        @DisplayName("getCapabilities returns contract capabilities")
        void getCapabilitiesFromContract() {
            assertThat(adapter.getCapabilities())
                    .containsExactlyInAnyOrderElementsOf(CONTRACT.requiredCapabilities());
        }

        @Test
        @DisplayName("getCapabilities uses step-name prefix when contract has no capabilities")
        void getCapabilitiesDefaultsToStepNamePrefix() {
            StepContract bare = new StepContract(
                    STEP_NAME, "#/in", "#/out", null, Map.of());
            WorkflowStep<String, String> stepWithNoCaps = new WorkflowStep<>() {
                @Override public String stepName() { return STEP_NAME; }
                @Override public StepContract contract() { return bare; }
                @Override public ValidationResult validateInput(String input) { return ValidationResult.success(); }
                @Override public Promise<StepResult<String>> execute(String input, StepContext ctx) {
                    return Promise.of(StepResult.success("x", Map.of(), Instant.now(), Instant.now()));
                }
            };
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(stepWithNoCaps);

            assertThat(a.getCapabilities())
                    .hasSize(1)
                    .first().asString().startsWith("yappc.workflow.");
        }

        @Test
        @DisplayName("getWorkflowStep returns the wrapped step")
        void getWorkflowStepReturnsWrappedStep() {
            assertThat(adapter.getWorkflowStep()).isSameAs(echoStep);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle state transitions")
    class Lifecycle {

        @Test
        @DisplayName("initial state is CREATED, not healthy")
        void initialStateIsCreated() {
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep);
            assertThat(fresh.getState()).isEqualTo(OperatorState.CREATED);
            assertThat(fresh.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("after initialize → state INITIALIZED, still not healthy")
        void afterInitializeStateIsInitialized() {
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep);
            runPromise(() -> fresh.initialize(OperatorConfig.empty()));
            assertThat(fresh.getState()).isEqualTo(OperatorState.INITIALIZED);
            assertThat(fresh.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("after start → state RUNNING, healthy")
        void afterStartStateIsRunningAndHealthy() {
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep);
            runPromise(() -> fresh.initialize(OperatorConfig.empty()).then(() -> fresh.start()));
            assertThat(fresh.getState()).isEqualTo(OperatorState.RUNNING);
            assertThat(fresh.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("after stop → state STOPPED, not healthy")
        void afterStopStateIsStoppedAndNotHealthy() {
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep);
            runPromise(() -> fresh.initialize(OperatorConfig.empty())
                    .then(() -> fresh.start())
                    .then(() -> fresh.stop()));
            assertThat(fresh.getState()).isEqualTo(OperatorState.STOPPED);
            assertThat(fresh.isHealthy()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("process() — happy path")
    class ProcessHappyPath {

        @Test
        @DisplayName("valid event with required payload → OperatorResult success")
        void validEventProducesSuccessResult() {
            Event event = buildEvent("hello", "tenant-1");

            OperatorResult result = runPromise(() -> adapter.process(event));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("process extracts tenantId from event payload")
        void processPropagatesTenantId() {
            // We verify through a step that captures its context
            String[] capturedTenant = {null};
            WorkflowStep<String, String> capturingStep = new WorkflowStep<>() {
                @Override public String stepName() { return STEP_NAME; }
                @Override public StepContract contract() { return CONTRACT; }
                @Override public ValidationResult validateInput(String input) { return ValidationResult.success(); }
                @Override public Promise<StepResult<String>> execute(String input, StepContext ctx) {
                    capturedTenant[0] = ctx.tenantId();
                    return Promise.of(StepResult.success("ok", Map.of(), Instant.now(), Instant.now()));
                }
            };
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(capturingStep);
            runPromise(() -> a.process(buildEvent("any", "my-tenant")));

            assertThat(capturedTenant[0]).isEqualTo("my-tenant");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process — validation failure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("process() — validation failure")
    class ProcessValidationFailure {

        @Test
        @DisplayName("invalid input returns failed OperatorResult without throwing")
        void invalidInputProducesFailedResult() {
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(invalidatingStep);
            Event event = buildEvent("x", "tenant-1");

            OperatorResult result = runPromise(() -> a.process(event));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Validation failed");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process — step execution failure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("process() — step execution failure")
    class ProcessExecutionFailure {

        @Test
        @DisplayName("step that returns failed StepResult → OperatorResult failed")
        void failingStepProducesFailedOperatorResult() {
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(failingStep);
            Event event = buildEvent("input-data", "tenant-1");

            OperatorResult result = runPromise(() -> a.process(event));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("downstream service unavailable");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process — missing tenantId
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("process() — missing required payload fields")
    class ProcessMissingPayload {

        @Test
        @DisplayName("event without tenantId payload → IllegalArgumentException")
        void missingTenantIdThrowsIllegalArgument() {
            Event event = GEvent.builder()
                    .type("step.execute")
                    .addPayload("input", "some-input")
                    .addPayload("runId", "run-001")
                    .addPayload("phase", "architecture")
                    .build();

            assertThatThrownBy(() -> runPromise(() -> adapter.process(event)))
                    .hasMessageContaining("tenantId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toEvent / getMetrics / getInternalState / getMetadata
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Introspection methods")
    class Introspection {

        @Test
        @DisplayName("toEvent produces operator.registered event with correct payload")
        void toEventHasCorrectType() {
            Event event = adapter.toEvent();
            assertThat(event.getPayload("source")).isEqualTo("yappc-workflow-step");
            assertThat(event.getPayload("stepName")).isEqualTo(STEP_NAME);
            assertThat(event.getPayload("type")).isEqualTo("STREAM");
        }

        @Test
        @DisplayName("getMetrics includes operator.type and step name")
        void getMetricsContainsExpectedKeys() {
            Map<String, Object> metrics = adapter.getMetrics();
            assertThat(metrics)
                    .containsKey("operator.type")
                    .containsKey("operator.step")
                    .containsKey("operator.state");
            assertThat(metrics.get("operator.step")).isEqualTo(STEP_NAME);
        }

        @Test
        @DisplayName("getInternalState contains stepName and state")
        void getInternalStateContainsStepNameAndState() {
            Map<String, Object> state = adapter.getInternalState();
            assertThat(state).containsKey("stepName").containsKey("state");
        }

        @Test
        @DisplayName("getMetadata includes product=yappc and adaptedFrom=WorkflowStep")
        void getMetadataContainsProductLabels() {
            Map<String, String> meta = adapter.getMetadata();
            assertThat(meta)
                    .containsEntry("product", "yappc")
                    .containsEntry("adaptedFrom", "WorkflowStep");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null step argument → NullPointerException")
    void nullStepThrowsNPE() {
        assertThatThrownBy(() -> new WorkflowStepOperatorAdapter<>(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Event buildEvent(String input, String tenantId) {
        return GEvent.builder()
                .type("step.execute")
                .addPayload("input", input)
                .addPayload("tenantId", tenantId)
                .addPayload("runId", "run-001")
                .addPayload("phase", "architecture")
                .addPayload("configSnapshotId", "cfg-snap-1")
                .build();
    }
}
