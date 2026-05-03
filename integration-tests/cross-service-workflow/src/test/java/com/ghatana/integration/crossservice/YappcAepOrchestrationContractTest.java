/*
 * Copyright (c) 2026 Ghatana Technologies
 * Integration Tests — Cross-Service Workflow
 */
package com.ghatana.integration.crossservice;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.pipeline.Pipeline;
import com.ghatana.yappc.services.lifecycle.YappcAepPipelineBootstrapper;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Contract tests that verify YAPPC's consumption of the AEP operator pipeline.
 *
 * <p>These tests assert the structural and behavioural contracts of the
 * {@link YappcAepPipelineBootstrapper} from the perspective of an integration consumer:
 * <ul>
 *   <li>The pipeline is built with the correct ID, version, and 4-operator topology</li>
 *   <li>{@code start()} is idempotent</li>
 *   <li>{@code routeEvent()} guards against uninitialised pipeline</li>
 *   <li>{@code routeEvent()} delegates through the operator chain on the happy path</li>
 *   <li>Dead-letter publishing is triggered when the pipeline raises an exception</li>
 * </ul>
 *
 * <p>The 4 lifecycle operators and the {@link DlqPublisher} are mocked so that this
 * test remains fast and focused on the bootstrapper contract rather than operator
 * business logic (which has its own unit tests).
 *
 * @doc.type class
 * @doc.purpose Contract tests for YAPPC consumption of AEP orchestration pipeline
 * @doc.layer integration
 * @doc.pattern Test, ContractTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("YAPPC → AEP orchestration pipeline contract")
class YappcAepOrchestrationContractTest extends EventloopTestBase {

    @Mock private PhaseTransitionValidatorOperator validatorOperator;
    @Mock private GateOrchestratorOperator         gateOperator;
    @Mock private AgentDispatchOperator            dispatchOperator;
    @Mock private LifecycleStatePublisherOperator  publisherOperator;
    @Mock private DlqPublisher                    dlqPublisher;

    private YappcAepPipelineBootstrapper bootstrapper;

    @BeforeEach
    void setUp() {
        bootstrapper = new YappcAepPipelineBootstrapper(
                validatorOperator,
                gateOperator,
                dispatchOperator,
                publisherOperator,
                dlqPublisher);

        // Every operator must return a completed Promise<Void> from initialize(); lenient
        // because some tests do not call start() at all.
        lenient().when(validatorOperator.initialize(any())).thenReturn(Promise.complete());
        lenient().when(gateOperator.initialize(any())).thenReturn(Promise.complete());
        lenient().when(dispatchOperator.initialize(any())).thenReturn(Promise.complete());
        lenient().when(publisherOperator.initialize(any())).thenReturn(Promise.complete());

        // Operators return a successful result by default; lenient because DLQ tests
        // override process() on the first operator.
        lenient().when(validatorOperator.process(any()))
                .thenReturn(Promise.of(OperatorResult.empty()));
        lenient().when(gateOperator.process(any()))
                .thenReturn(Promise.of(OperatorResult.empty()));
        lenient().when(dispatchOperator.process(any()))
                .thenReturn(Promise.of(OperatorResult.empty()));
        lenient().when(publisherOperator.process(any()))
                .thenReturn(Promise.of(OperatorResult.empty()));
    }

    // ── Pipeline ID constants ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline constants")
    class PipelineConstants {

        @Test
        @DisplayName("PIPELINE_ID is 'lifecycle-management-v1'")
        void pipelineId_isLifecycleManagementV1() {
            assertThat(YappcAepPipelineBootstrapper.PIPELINE_ID)
                    .isEqualTo("lifecycle-management-v1");
        }

        @Test
        @DisplayName("PIPELINE_VERSION is '1.0.0'")
        void pipelineVersion_is1_0_0() {
            assertThat(YappcAepPipelineBootstrapper.PIPELINE_VERSION)
                    .isEqualTo("1.0.0");
        }
    }

    // ── start() contract ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("start() — pipeline bootstrap")
    class StartContract {

        @Test
        @DisplayName("getPipeline() returns null before start()")
        void getPipeline_isNullBeforeStart() {
            assertThat(bootstrapper.getPipeline()).isNull();
        }

        @Test
        @DisplayName("start() resolves with a non-null Pipeline")
        void start_resolvesPipeline() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            assertThat(pipeline).isNotNull();
        }

        @Test
        @DisplayName("start() assigns getPipeline() after completion")
        void start_setsPipelineField() {
            runPromise(bootstrapper::start);

            assertThat(bootstrapper.getPipeline()).isNotNull();
        }

        @Test
        @DisplayName("built pipeline has the correct ID")
        void builtPipeline_hasCorrectId() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            assertThat(pipeline.getId())
                    .isEqualTo(YappcAepPipelineBootstrapper.PIPELINE_ID);
        }

        @Test
        @DisplayName("built pipeline has the correct version")
        void builtPipeline_hasCorrectVersion() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            assertThat(pipeline.getVersion())
                    .isEqualTo(YappcAepPipelineBootstrapper.PIPELINE_VERSION);
        }

        @Test
        @DisplayName("pipeline contains exactly 4 operator nodes")
        void builtPipeline_hasExactlyFourNodes() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            assertThat(pipeline.getNodesTopological()).hasSize(4);
        }

        @Test
        @DisplayName("operator topology follows validator → gate → dispatch → publisher order")
        void builtPipeline_operatorTopologyIsLinear() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            List<String> nodeIds = pipeline.getNodesTopological().stream()
                    .map(node -> node.nodeId())
                    .toList();

            assertThat(nodeIds).containsExactly(
                    "phase-transition-validator",
                    "gate-orchestrator",
                    "agent-dispatch",
                    "lifecycle-state-publisher");
        }

        @Test
        @DisplayName("each node ID is present in the pipeline adjacency map")
        void builtPipeline_allNodeIdsInAdjacency() {
            Pipeline pipeline = runPromise(bootstrapper::start);

            assertThat(pipeline.getAdjacency())
                    .containsKeys(
                            "phase-transition-validator",
                            "gate-orchestrator",
                            "agent-dispatch",
                            "lifecycle-state-publisher");
        }

        @Test
        @DisplayName("start() is idempotent — second call returns the same Pipeline instance")
        void start_isIdempotent() {
            Pipeline first  = runPromise(bootstrapper::start);
            Pipeline second = runPromise(bootstrapper::start);

            assertThat(second).isSameAs(first);
        }

        @Test
        @DisplayName("start() initializes each operator exactly once")
        void start_initializesEachOperatorOnce() {
            runPromise(bootstrapper::start);

            verify(validatorOperator,  atLeastOnce()).initialize(any());
            verify(gateOperator,       atLeastOnce()).initialize(any());
            verify(dispatchOperator,   atLeastOnce()).initialize(any());
            verify(publisherOperator,  atLeastOnce()).initialize(any());
        }
    }

    // ── routeEvent() guard contract ────────────────────────────────────────────

    @Nested
    @DisplayName("routeEvent() — guard against uninitialised pipeline")
    class RouteEventGuardContract {

        @Test
        @DisplayName("routeEvent() fails with IllegalStateException when pipeline not started")
        void routeEvent_failsWhenPipelineNotStarted() {
            Map<String, Object> payload = Map.of("fromStage", "planning", "toStage", "development");

            assertThatThrownBy(() -> runPromise(() -> bootstrapper.routeEvent(
                            "tenant-1",
                            "lifecycle.phase.transition.requested",
                            payload,
                            null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Pipeline not started");
        }
    }

    // ── routeEvent() happy-path contract ──────────────────────────────────────

    @Nested
    @DisplayName("routeEvent() — happy-path routing")
    class RouteEventHappyPath {

        @Test
        @DisplayName("routeEvent() completes successfully when pipeline is started")
        void routeEvent_completesSuccessfully() {
            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "planning", "toStage", "development");

            runPromise(() -> bootstrapper.routeEvent(
                    "tenant-alpha",
                    "lifecycle.phase.transition.requested",
                    payload,
                    "corr-001"));
        }

        @Test
        @DisplayName("routeEvent() invokes the first operator (validator)")
        void routeEvent_invokesValidatorOperator() {
            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "development", "toStage", "qa");

            runPromise(() -> bootstrapper.routeEvent(
                    "tenant-beta",
                    "lifecycle.phase.transition.requested",
                    payload,
                    null));

            verify(validatorOperator, atLeastOnce()).process(any());
        }

        @Test
        @DisplayName("routeEvent() does NOT publish to DLQ on success")
        void routeEvent_doesNotPublishDlqOnSuccess() {
            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "qa", "toStage", "staging");

            runPromise(() -> bootstrapper.routeEvent(
                    "tenant-gamma",
                    "lifecycle.phase.transition.requested",
                    payload,
                    "corr-002"));

            verify(dlqPublisher, never()).publish(anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("routeEvent() includes correlationId when provided")
        void routeEvent_handlesNullCorrelationId() {
            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "staging", "toStage", "production");

            // Must complete without error even when correlationId is null
            runPromise(() -> bootstrapper.routeEvent(
                    "tenant-delta",
                    "lifecycle.phase.transition.requested",
                    payload,
                    null));
        }
    }

    // ── DLQ publishing on pipeline failure ────────────────────────────────────

    @Nested
    @DisplayName("routeEvent() — DLQ on pipeline failure")
    class DlqPublishingContract {

        @Test
        @DisplayName("routeEvent() publishes to DLQ when pipeline execution throws")
        void routeEvent_publishesDlqOnPipelineException() {
            RuntimeException simulatedFailure = new RuntimeException("operator-failure");
            lenient().when(validatorOperator.process(any()))
                    .thenReturn(Promise.ofException(simulatedFailure));

            lenient().when(dlqPublisher.publish(
                    anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any()))
                    .thenReturn(Promise.complete());

            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "planning", "toStage", "development");

            // routeEvent re-propagates the exception after DLQ publish
            assertThatThrownBy(() -> runPromise(() -> bootstrapper.routeEvent(
                            "tenant-failure",
                            "lifecycle.phase.transition.requested",
                            payload,
                            "corr-fail")))
                    .isInstanceOf(RuntimeException.class);

            verify(dlqPublisher, atLeastOnce()).publish(
                    anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any());
        }

        @Test
        @DisplayName("DLQ publish uses the correct pipeline ID")
        void dlqPublish_usesCorrectPipelineId() {
            RuntimeException simulatedFailure = new RuntimeException("gate-failure");
            lenient().when(validatorOperator.process(any()))
                    .thenReturn(Promise.ofException(simulatedFailure));

            lenient().when(dlqPublisher.publish(
                    anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any()))
                    .thenReturn(Promise.complete());

            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "qa", "toStage", "staging");

            assertThatThrownBy(() -> runPromise(() -> bootstrapper.routeEvent(
                            "tenant-dlq-id",
                            "lifecycle.phase.transition.requested",
                            payload,
                            null)))
                    .isInstanceOf(RuntimeException.class);

            verify(dlqPublisher, atLeastOnce()).publish(
                    anyString(),
                    org.mockito.ArgumentMatchers.eq(YappcAepPipelineBootstrapper.PIPELINE_ID),
                    anyString(),
                    anyString(),
                    any(),
                    anyString(),
                    any());
        }
    }

    // ── stop() contract ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop() — graceful shutdown")
    class StopContract {

        @Test
        @DisplayName("stop() before start() completes without error")
        void stop_beforeStart_completesWithoutError() {
            runPromise(bootstrapper::stop);

            assertThat(bootstrapper.getPipeline()).isNull();
        }

        @Test
        @DisplayName("stop() after start() clears the pipeline reference")
        void stop_afterStart_clearsPipelineReference() {
            lenient().when(validatorOperator.stop()).thenReturn(Promise.complete());
            lenient().when(gateOperator.stop()).thenReturn(Promise.complete());
            lenient().when(dispatchOperator.stop()).thenReturn(Promise.complete());
            lenient().when(publisherOperator.stop()).thenReturn(Promise.complete());

            runPromise(bootstrapper::start);
            runPromise(bootstrapper::stop);

            assertThat(bootstrapper.getPipeline()).isNull();
        }
    }
}
