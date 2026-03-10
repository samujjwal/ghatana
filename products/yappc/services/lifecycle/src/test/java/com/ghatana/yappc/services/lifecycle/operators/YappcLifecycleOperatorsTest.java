/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.yappc.services.lifecycle.AepEventBridge;
import com.ghatana.yappc.services.lifecycle.ApprovalRequest;
import com.ghatana.yappc.services.lifecycle.GateEvaluator;
import com.ghatana.yappc.services.lifecycle.HumanApprovalService;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.StageSpec;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.TransitionSpec;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the YAPPC lifecycle pipeline operators.
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}
 * to execute promises on the managed ActiveJ Eventloop without blocking.
 *
 * @doc.type class
 * @doc.purpose Tests for PhaseTransitionValidatorOperator, GateOrchestratorOperator,
 *              AgentDispatchOperator, and LifecycleStatePublisherOperator
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("YAPPC Lifecycle Operators")
class YappcLifecycleOperatorsTest extends EventloopTestBase {

    // ─── shared fixtures ─────────────────────────────────────────────────────

    private static Event buildTransitionRequestedEvent(
            String projectId, String fromPhase, String toPhase, String tenantId) {
        return GEvent.builder()
            .typeTenantVersion(tenantId,
                    PhaseTransitionValidatorOperator.EVENT_TRANSITION_REQUESTED, "v1")
            .addPayload("projectId",   projectId)
            .addPayload("fromPhase",   fromPhase)
            .addPayload("toPhase",     toPhase)
            .addPayload("tenantId",    tenantId)
            .addPayload("requestedBy", "test-user")
            .build();
    }

    private static Event buildValidatedEvent(
            String projectId, String fromPhase, String toPhase, String tenantId, boolean gateOpen) {
        return GEvent.builder()
            .typeTenantVersion(tenantId,
                    PhaseTransitionValidatorOperator.EVENT_TRANSITION_VALIDATED, "v1")
            .addPayload("projectId",    projectId)
            .addPayload("fromPhase",    fromPhase)
            .addPayload("toPhase",      toPhase)
            .addPayload("tenantId",     tenantId)
            .addPayload("requestedBy",  "test-user")
            .addPayload("gateOpen",     String.valueOf(gateOpen))
            .addPayload("unmetCriteria", List.of())
            .build();
    }

    private static Event buildGatePassedEvent(
            String projectId, String fromPhase, String toPhase, String tenantId) {
        return GEvent.builder()
            .typeTenantVersion(tenantId, GateOrchestratorOperator.EVENT_GATE_PASSED, "v1")
            .addPayload("projectId",   projectId)
            .addPayload("fromPhase",   fromPhase)
            .addPayload("toPhase",     toPhase)
            .addPayload("tenantId",    tenantId)
            .addPayload("requestedBy", "test-user")
            .build();
    }

    // =========================================================================
    // PhaseTransitionValidatorOperator tests
    // =========================================================================

    @Nested
    @DisplayName("PhaseTransitionValidatorOperator")
    class ValidatorOperatorTests {

        private PhaseTransitionValidatorOperator operator;

        @Mock private TransitionConfigLoader transitionConfig;
        @Mock private StageConfigLoader stageConfig;
        @Mock private GateEvaluator gateEvaluator;

        @BeforeEach
        void setUp() {
            operator = new PhaseTransitionValidatorOperator(transitionConfig, stageConfig, gateEvaluator);
        }

        @Test
        @DisplayName("should emit validated event when transition is allowed and gate is open")
        void shouldEmitValidatedEventForAllowedTransition() {
            // GIVEN
            TransitionSpec spec = new TransitionSpec();
            StageSpec targetStage = new StageSpec();
            GateEvaluator.GateResult gateResult =
                    new GateEvaluator.GateResult(true, 0, 0, List.of());

            when(transitionConfig.findTransition("intent", "context"))
                    .thenReturn(Optional.of(spec));
            when(stageConfig.findById("context"))
                    .thenReturn(Optional.of(targetStage));
            when(gateEvaluator.evaluateEntry(any(), any()))
                    .thenReturn(gateResult);

            Event requestedEvent = buildTransitionRequestedEvent(
                    "proj-1", "intent", "context", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event outputEvent = result.getOutputEvents().get(0);
            assertThat(outputEvent.getType())
                    .isEqualTo(PhaseTransitionValidatorOperator.EVENT_TRANSITION_VALIDATED);
            assertThat(outputEvent.getPayload("toPhase")).isEqualTo("context");
            assertThat(outputEvent.getPayload("gateOpen")).isEqualTo(true);
        }

        @Test
        @DisplayName("should fail when no transition rule matches")
        void shouldFailForUnknownTransition() {
            // GIVEN
            when(transitionConfig.findTransition("intent", "ship"))
                    .thenReturn(Optional.empty());

            Event requestedEvent = buildTransitionRequestedEvent(
                    "proj-2", "intent", "ship", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("INVALID_TRANSITION");
        }

        @Test
        @DisplayName("should fail when target stage is unknown")
        void shouldFailForUnknownTargetStage() {
            // GIVEN
            TransitionSpec spec = new TransitionSpec();
            when(transitionConfig.findTransition("intent", "unknown-stage"))
                    .thenReturn(Optional.of(spec));
            when(stageConfig.findById("unknown-stage"))
                    .thenReturn(Optional.empty());

            Event requestedEvent = buildTransitionRequestedEvent(
                    "proj-3", "intent", "unknown-stage", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("UNKNOWN_TARGET_STAGE");
        }
    }

    // =========================================================================
    // GateOrchestratorOperator tests
    // =========================================================================

    @Nested
    @DisplayName("GateOrchestratorOperator")
    class GateOrchestratorTests {

        private GateOrchestratorOperator operator;

        @Mock private PolicyEngine policyEngine;
        @Mock private HumanApprovalService humanApprovalService;

        @BeforeEach
        void setUp() {
            operator = new GateOrchestratorOperator(policyEngine, humanApprovalService);
        }

        @Test
        @DisplayName("should emit gate.passed when policy passes and gate is open")
        void shouldEmitGatePassedWhenPolicyPassesAndGateOpen() {
            // GIVEN
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.of(true));

            Event validatedEvent = buildValidatedEvent(
                    "proj-1", "intent", "context", "tenant-1", true);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            assertThat(result.getOutputEvents().get(0).getType())
                    .isEqualTo(GateOrchestratorOperator.EVENT_GATE_PASSED);
        }

        @Test
        @DisplayName("should fail result when policy denies the transition")
        void shouldFailWhenPolicyDenies() {
            // GIVEN
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.of(false));

            Event validatedEvent = buildValidatedEvent(
                    "proj-2", "intent", "context", "tenant-1", true);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("POLICY_DENIED");
        }

        @Test
        @DisplayName("should request human approval when gate has unmet criteria")
        void shouldRequestHumanApprovalForUnmetCriteria() {
            // GIVEN
            when(policyEngine.evaluate(anyString(), any()))
                    .thenReturn(Promise.of(true));

            ApprovalRequest fakeRequest = new ApprovalRequest(
                    "req-1", "proj-3", "agent", ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                    null, ApprovalRequest.ApprovalStatus.PENDING, "tenant-1",
                    null, null, null, null
            );
            when(humanApprovalService.requestApproval(
                    anyString(), anyString(), anyString(),
                    any(ApprovalRequest.ApprovalType.class), any()))
                .thenReturn(Promise.of(fakeRequest));

            Event validatedEvent = buildValidatedEvent(
                    "proj-3", "intent", "context", "tenant-1", false);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            assertThat(result.getOutputEvents().get(0).getType())
                    .isEqualTo(GateOrchestratorOperator.EVENT_APPROVAL_REQUESTED);
            assertThat(result.getOutputEvents().get(0).getPayload("approvalId"))
                    .isEqualTo("req-1");
        }
    }

    // =========================================================================
    // AgentDispatchOperator tests
    // =========================================================================

    @Nested
    @DisplayName("AgentDispatchOperator")
    class AgentDispatchTests {

        private AgentDispatchOperator operator;
        @Mock private StageConfigLoader stageConfig;

        @BeforeEach
        void setUp() {
            operator = new AgentDispatchOperator(stageConfig);
        }

        @Test
        @DisplayName("should emit one dispatch event per agent assignment")
        void shouldEmitDispatchEventsForEachAgent() {
            // GIVEN
            StageSpec stage = new StageSpec();
            // Inject agent assignments via reflection to avoid package-private setters
            setAgentAssignments(stage, List.of("context-analyzer", "requirement-extractor"));

            when(stageConfig.findById("context")).thenReturn(Optional.of(stage));

            Event gatePassedEvent = buildGatePassedEvent("proj-1", "intent", "context", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(gatePassedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(2);
            assertThat(result.getOutputEvents())
                    .allSatisfy(e -> assertThat(e.getType())
                            .isEqualTo(AgentDispatchOperator.EVENT_AGENT_DISPATCHED));
        }

        @Test
        @DisplayName("should forward event unchanged when no agents are assigned")
        void shouldForwardEventWhenNoAgentsAssigned() {
            // GIVEN
            StageSpec stage = new StageSpec();
            // No agent assignments (default empty)
            when(stageConfig.findById("context")).thenReturn(Optional.of(stage));

            Event gatePassedEvent = buildGatePassedEvent("proj-2", "intent", "context", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(gatePassedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            // Forwarded unchanged — same event type
            assertThat(result.getOutputEvents().get(0).getType())
                    .isEqualTo(GateOrchestratorOperator.EVENT_GATE_PASSED);
        }

        /** Injects agentAssignments into a StageSpec (field is package-private JsonProperty). */
        private void setAgentAssignments(StageSpec stage, List<String> agents) {
            try {
                var field = StageSpec.class.getDeclaredField("agentAssignments");
                field.setAccessible(true);
                field.set(stage, agents);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // =========================================================================
    // LifecycleStatePublisherOperator tests
    // =========================================================================

    @Nested
    @DisplayName("LifecycleStatePublisherOperator")
    class PublisherOperatorTests {

        private LifecycleStatePublisherOperator operator;
        @Mock private AepEventBridge aepEventBridge;

        @BeforeEach
        void setUp() {
            when(aepEventBridge.publishRawEvent(anyString(), any(), any()))
                    .thenReturn(Promise.of(null));
            operator = new LifecycleStatePublisherOperator(aepEventBridge);
        }

        @Test
        @DisplayName("should emit lifecycle.phase.advanced for agent.dispatched events")
        void shouldEmitPhaseAdvancedForAgentDispatch() {
            // GIVEN
            Event agentDispatchedEvent = GEvent.builder()
                .typeTenantVersion("tenant-1", AgentDispatchOperator.EVENT_AGENT_DISPATCHED, "v1")
                .addPayload("projectId",    "proj-1")
                .addPayload("fromPhase",    "intent")
                .addPayload("toPhase",      "context")
                .addPayload("tenantId",     "tenant-1")
                .addPayload("requestedBy",  "test-user")
                .addPayload("agentId",      "context-analyzer")
                .build();

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(agentDispatchedEvent));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event output = result.getOutputEvents().get(0);
            assertThat(output.getType())
                    .isEqualTo(LifecycleStatePublisherOperator.EVENT_PHASE_ADVANCED);
            assertThat(output.getPayload("fromPhase")).isEqualTo("intent");
            assertThat(output.getPayload("toPhase")).isEqualTo("context");
            assertThat(output.getPayload("agentId")).isEqualTo("context-analyzer");
        }
    }
}
