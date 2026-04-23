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
    private static final StepContract CONTRACT = new StepContract( // GH-90000
            STEP_NAME,
            "#/definitions/ArchitectureInput",
            "#/definitions/ArchitectureOutput",
            List.of("DATA_CLOUD", "EVENT_LOG"), // GH-90000
            Map.of("description", "Architecture intake step", "version", "1.0.0")); // GH-90000

    /** A simple WorkflowStep that echoes the input as output. */
    private WorkflowStep<String, String> echoStep;
    /** A WorkflowStep that always fails validation. */
    private WorkflowStep<String, String> invalidatingStep;
    /** A WorkflowStep whose execute always returns a failed StepResult. */
    private WorkflowStep<String, String> failingStep;

    private WorkflowStepOperatorAdapter<String, String> adapter;

    @BeforeEach
    void setUp() { // GH-90000
        echoStep = new WorkflowStep<>() { // GH-90000
            @Override
            public String stepName() { return STEP_NAME; } // GH-90000

            @Override
            public StepContract contract() { return CONTRACT; } // GH-90000

            @Override
            public ValidationResult validateInput(String input) { // GH-90000
                return ValidationResult.success(); // GH-90000
            }

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) { // GH-90000
                return Promise.of(StepResult.success( // GH-90000
                        "echo:" + input,
                        Map.of("tenant", context.tenantId()), // GH-90000
                        Instant.now(), // GH-90000
                        Instant.now())); // GH-90000
            }
        };

        invalidatingStep = new WorkflowStep<>() { // GH-90000
            @Override
            public String stepName() { return STEP_NAME; } // GH-90000

            @Override
            public StepContract contract() { return CONTRACT; } // GH-90000

            @Override
            public ValidationResult validateInput(String input) { // GH-90000
                return ValidationResult.fail("input is too short", "schema mismatch"); // GH-90000
            }

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) { // GH-90000
                return Promise.of(StepResult.success("never-called", Map.of(), Instant.now(), Instant.now())); // GH-90000
            }
        };

        failingStep = new WorkflowStep<>() { // GH-90000
            @Override
            public String stepName() { return STEP_NAME; } // GH-90000

            @Override
            public StepContract contract() { return CONTRACT; } // GH-90000

            @Override
            public ValidationResult validateInput(String input) { return ValidationResult.success(); } // GH-90000

            @Override
            public Promise<StepResult<String>> execute(String input, StepContext context) { // GH-90000
                return Promise.of(StepResult.failed( // GH-90000
                        List.of("downstream service unavailable"),
                        Map.of(), // GH-90000
                        Instant.now(), // GH-90000
                        Instant.now())); // GH-90000
            }
        };

        adapter = new WorkflowStepOperatorAdapter<>(echoStep); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Identity and metadata")
    class Identity {

        @Test
        @DisplayName("getId returns operatorId derived from step name")
        void getIdReflectsStepName() { // GH-90000
            assertThat(adapter.getId().toString()) // GH-90000
                    .contains(STEP_NAME) // GH-90000
                    .startsWith("yappc:workflow:");
        }

        @Test
        @DisplayName("getName returns the step name")
        void getNameReturnsStepName() { // GH-90000
            assertThat(adapter.getName()).isEqualTo(STEP_NAME); // GH-90000
        }

        @Test
        @DisplayName("getType returns STREAM")
        void getTypeIsStream() { // GH-90000
            assertThat(adapter.getType()).isEqualTo(OperatorType.STREAM); // GH-90000
        }

        @Test
        @DisplayName("getVersion returns 1.0.0")
        void getVersion() { // GH-90000
            assertThat(adapter.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("getDescription references step name and contract schemas")
        void getDescriptionContainsContractSchemas() { // GH-90000
            String desc = adapter.getDescription(); // GH-90000
            assertThat(desc) // GH-90000
                    .contains(STEP_NAME) // GH-90000
                    .contains(CONTRACT.inputSchemaRef()) // GH-90000
                    .contains(CONTRACT.outputSchemaRef()); // GH-90000
        }

        @Test
        @DisplayName("getCapabilities returns contract capabilities")
        void getCapabilitiesFromContract() { // GH-90000
            assertThat(adapter.getCapabilities()) // GH-90000
                    .containsExactlyInAnyOrderElementsOf(CONTRACT.requiredCapabilities()); // GH-90000
        }

        @Test
        @DisplayName("getCapabilities uses step-name prefix when contract has no capabilities")
        void getCapabilitiesDefaultsToStepNamePrefix() { // GH-90000
            StepContract bare = new StepContract( // GH-90000
                    STEP_NAME, "#/in", "#/out", null, Map.of()); // GH-90000
            WorkflowStep<String, String> stepWithNoCaps = new WorkflowStep<>() { // GH-90000
                @Override public String stepName() { return STEP_NAME; } // GH-90000
                @Override public StepContract contract() { return bare; } // GH-90000
                @Override public ValidationResult validateInput(String input) { return ValidationResult.success(); } // GH-90000
                @Override public Promise<StepResult<String>> execute(String input, StepContext ctx) { // GH-90000
                    return Promise.of(StepResult.success("x", Map.of(), Instant.now(), Instant.now())); // GH-90000
                }
            };
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(stepWithNoCaps); // GH-90000

            assertThat(a.getCapabilities()) // GH-90000
                    .hasSize(1) // GH-90000
                    .first().asString().startsWith("yappc.workflow.");
        }

        @Test
        @DisplayName("getWorkflowStep returns the wrapped step")
        void getWorkflowStepReturnsWrappedStep() { // GH-90000
            assertThat(adapter.getWorkflowStep()).isSameAs(echoStep); // GH-90000
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
        void initialStateIsCreated() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep); // GH-90000
            assertThat(fresh.getState()).isEqualTo(OperatorState.CREATED); // GH-90000
            assertThat(fresh.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("after initialize → state INITIALIZED, still not healthy")
        void afterInitializeStateIsInitialized() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep); // GH-90000
            runPromise(() -> fresh.initialize(OperatorConfig.empty())); // GH-90000
            assertThat(fresh.getState()).isEqualTo(OperatorState.INITIALIZED); // GH-90000
            assertThat(fresh.isHealthy()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("after start → state RUNNING, healthy")
        void afterStartStateIsRunningAndHealthy() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep); // GH-90000
            runPromise(() -> fresh.initialize(OperatorConfig.empty()).then(() -> fresh.start())); // GH-90000
            assertThat(fresh.getState()).isEqualTo(OperatorState.RUNNING); // GH-90000
            assertThat(fresh.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("after stop → state STOPPED, not healthy")
        void afterStopStateIsStoppedAndNotHealthy() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> fresh = new WorkflowStepOperatorAdapter<>(echoStep); // GH-90000
            runPromise(() -> fresh.initialize(OperatorConfig.empty()) // GH-90000
                    .then(() -> fresh.start()) // GH-90000
                    .then(() -> fresh.stop())); // GH-90000
            assertThat(fresh.getState()).isEqualTo(OperatorState.STOPPED); // GH-90000
            assertThat(fresh.isHealthy()).isFalse(); // GH-90000
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
        void validEventProducesSuccessResult() { // GH-90000
            Event event = buildEvent("hello", "tenant-1"); // GH-90000

            OperatorResult result = runPromise(() -> adapter.process(event)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("process extracts tenantId from event payload")
        void processPropagatesTenantId() { // GH-90000
            // We verify through a step that captures its context
            String[] capturedTenant = {null};
            WorkflowStep<String, String> capturingStep = new WorkflowStep<>() { // GH-90000
                @Override public String stepName() { return STEP_NAME; } // GH-90000
                @Override public StepContract contract() { return CONTRACT; } // GH-90000
                @Override public ValidationResult validateInput(String input) { return ValidationResult.success(); } // GH-90000
                @Override public Promise<StepResult<String>> execute(String input, StepContext ctx) { // GH-90000
                    capturedTenant[0] = ctx.tenantId(); // GH-90000
                    return Promise.of(StepResult.success("ok", Map.of(), Instant.now(), Instant.now())); // GH-90000
                }
            };
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(capturingStep); // GH-90000
            runPromise(() -> a.process(buildEvent("any", "my-tenant"))); // GH-90000

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
        void invalidInputProducesFailedResult() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(invalidatingStep); // GH-90000
            Event event = buildEvent("x", "tenant-1"); // GH-90000

            OperatorResult result = runPromise(() -> a.process(event)); // GH-90000

            assertThat(result.isSuccess()).isFalse(); // GH-90000
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
        void failingStepProducesFailedOperatorResult() { // GH-90000
            WorkflowStepOperatorAdapter<String, String> a = new WorkflowStepOperatorAdapter<>(failingStep); // GH-90000
            Event event = buildEvent("input-data", "tenant-1"); // GH-90000

            OperatorResult result = runPromise(() -> a.process(event)); // GH-90000

            assertThat(result.isSuccess()).isFalse(); // GH-90000
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
        void missingTenantIdThrowsIllegalArgument() { // GH-90000
            Event event = GEvent.builder() // GH-90000
                    .type("step.execute")
                    .addPayload("input", "some-input") // GH-90000
                    .addPayload("runId", "run-001") // GH-90000
                    .addPayload("phase", "architecture") // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> adapter.process(event))) // GH-90000
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
        void toEventHasCorrectType() { // GH-90000
            Event event = adapter.toEvent(); // GH-90000
            assertThat(event.getPayload("source")).isEqualTo("yappc-workflow-step");
            assertThat(event.getPayload("stepName")).isEqualTo(STEP_NAME);
            assertThat(event.getPayload("type")).isEqualTo("STREAM");
        }

        @Test
        @DisplayName("getMetrics includes operator.type and step name")
        void getMetricsContainsExpectedKeys() { // GH-90000
            Map<String, Object> metrics = adapter.getMetrics(); // GH-90000
            assertThat(metrics) // GH-90000
                    .containsKey("operator.type")
                    .containsKey("operator.step")
                    .containsKey("operator.state");
            assertThat(metrics.get("operator.step")).isEqualTo(STEP_NAME);
        }

        @Test
        @DisplayName("getInternalState contains stepName and state")
        void getInternalStateContainsStepNameAndState() { // GH-90000
            Map<String, Object> state = adapter.getInternalState(); // GH-90000
            assertThat(state).containsKey("stepName").containsKey("state");
        }

        @Test
        @DisplayName("getMetadata includes product=yappc and adaptedFrom=WorkflowStep")
        void getMetadataContainsProductLabels() { // GH-90000
            Map<String, String> meta = adapter.getMetadata(); // GH-90000
            assertThat(meta) // GH-90000
                    .containsEntry("product", "yappc") // GH-90000
                    .containsEntry("adaptedFrom", "WorkflowStep"); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null step argument → NullPointerException")
    void nullStepThrowsNPE() { // GH-90000
        assertThatThrownBy(() -> new WorkflowStepOperatorAdapter<>(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Event buildEvent(String input, String tenantId) { // GH-90000
        return GEvent.builder() // GH-90000
                .type("step.execute")
                .addPayload("input", input) // GH-90000
                .addPayload("tenantId", tenantId) // GH-90000
                .addPayload("runId", "run-001") // GH-90000
                .addPayload("phase", "architecture") // GH-90000
                .addPayload("configSnapshotId", "cfg-snap-1") // GH-90000
                .build(); // GH-90000
    }
}
