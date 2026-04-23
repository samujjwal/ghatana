/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the YAPPC lifecycle pipeline operators.
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)} // GH-90000
 * to execute promises on the managed ActiveJ Eventloop without blocking.
 *
 * @doc.type class
 * @doc.purpose Tests for PhaseTransitionValidatorOperator, GateOrchestratorOperator,
 *              AgentDispatchOperator, and LifecycleStatePublisherOperator
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("YAPPC Lifecycle Operators")
class YappcLifecycleOperatorsTest extends EventloopTestBase {

    // ─── shared fixtures ─────────────────────────────────────────────────────

    private static Event buildTransitionRequestedEvent( // GH-90000
            String projectId, String fromPhase, String toPhase, String tenantId) {
        return GEvent.builder() // GH-90000
            .typeTenantVersion(tenantId, // GH-90000
                    PhaseTransitionValidatorOperator.EVENT_TRANSITION_REQUESTED, "v1")
            .addPayload("projectId",   projectId) // GH-90000
            .addPayload("fromPhase",   fromPhase) // GH-90000
            .addPayload("toPhase",     toPhase) // GH-90000
            .addPayload("tenantId",    tenantId) // GH-90000
            .addPayload("requestedBy", "test-user") // GH-90000
            .build(); // GH-90000
    }

    private static Event buildValidatedEvent( // GH-90000
            String projectId, String fromPhase, String toPhase, String tenantId, boolean gateOpen) {
        return GEvent.builder() // GH-90000
            .typeTenantVersion(tenantId, // GH-90000
                    PhaseTransitionValidatorOperator.EVENT_TRANSITION_VALIDATED, "v1")
            .addPayload("projectId",    projectId) // GH-90000
            .addPayload("fromPhase",    fromPhase) // GH-90000
            .addPayload("toPhase",      toPhase) // GH-90000
            .addPayload("tenantId",     tenantId) // GH-90000
            .addPayload("requestedBy",  "test-user") // GH-90000
            .addPayload("gateOpen",     String.valueOf(gateOpen)) // GH-90000
            .addPayload("unmetCriteria", List.of()) // GH-90000
            .build(); // GH-90000
    }

    private static Event buildGatePassedEvent( // GH-90000
            String projectId, String fromPhase, String toPhase, String tenantId) {
        return GEvent.builder() // GH-90000
            .typeTenantVersion(tenantId, GateOrchestratorOperator.EVENT_GATE_PASSED, "v1") // GH-90000
            .addPayload("projectId",   projectId) // GH-90000
            .addPayload("fromPhase",   fromPhase) // GH-90000
            .addPayload("toPhase",     toPhase) // GH-90000
            .addPayload("tenantId",    tenantId) // GH-90000
            .addPayload("requestedBy", "test-user") // GH-90000
            .build(); // GH-90000
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
        void setUp() { // GH-90000
            operator = new PhaseTransitionValidatorOperator(transitionConfig, stageConfig, gateEvaluator); // GH-90000
        }

        @Test
        @DisplayName("should emit validated event when transition is allowed and gate is open")
        void shouldEmitValidatedEventForAllowedTransition() { // GH-90000
            // GIVEN
            TransitionSpec spec = new TransitionSpec(); // GH-90000
            StageSpec targetStage = new StageSpec(); // GH-90000
            GateEvaluator.GateResult gateResult =
                    new GateEvaluator.GateResult(true, 0, 0, List.of()); // GH-90000

            when(transitionConfig.findTransition("intent", "context")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000
            when(stageConfig.findById("context"))
                    .thenReturn(Optional.of(targetStage)); // GH-90000
            when(gateEvaluator.evaluateEntry(any(), any())) // GH-90000
                    .thenReturn(gateResult); // GH-90000

            Event requestedEvent = buildTransitionRequestedEvent( // GH-90000
                    "proj-1", "intent", "context", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event outputEvent = result.getOutputEvents().get(0); // GH-90000
            assertThat(outputEvent.getType()) // GH-90000
                    .isEqualTo(PhaseTransitionValidatorOperator.EVENT_TRANSITION_VALIDATED); // GH-90000
            assertThat(outputEvent.getPayload("toPhase")).isEqualTo("context");
            assertThat(outputEvent.getPayload("gateOpen")).isEqualTo(true);
        }

        @Test
        @DisplayName("should fail when no transition rule matches")
        void shouldFailForUnknownTransition() { // GH-90000
            // GIVEN
            when(transitionConfig.findTransition("intent", "ship")) // GH-90000
                    .thenReturn(Optional.empty()); // GH-90000

            Event requestedEvent = buildTransitionRequestedEvent( // GH-90000
                    "proj-2", "intent", "ship", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).contains("INVALID_TRANSITION");
        }

        @Test
        @DisplayName("should fail when target stage is unknown")
        void shouldFailForUnknownTargetStage() { // GH-90000
            // GIVEN
            TransitionSpec spec = new TransitionSpec(); // GH-90000
            when(transitionConfig.findTransition("intent", "unknown-stage")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000
            when(stageConfig.findById("unknown-stage"))
                    .thenReturn(Optional.empty()); // GH-90000

            Event requestedEvent = buildTransitionRequestedEvent( // GH-90000
                    "proj-3", "intent", "unknown-stage", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(requestedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
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
        void setUp() { // GH-90000
            operator = new GateOrchestratorOperator(policyEngine, humanApprovalService); // GH-90000
        }

        @Test
        @DisplayName("should emit gate.passed when policy passes and gate is open")
        void shouldEmitGatePassedWhenPolicyPassesAndGateOpen() { // GH-90000
            // GIVEN
            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(true)); // GH-90000

            Event validatedEvent = buildValidatedEvent( // GH-90000
                    "proj-1", "intent", "context", "tenant-1", true);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            assertThat(result.getOutputEvents().get(0).getType()) // GH-90000
                    .isEqualTo(GateOrchestratorOperator.EVENT_GATE_PASSED); // GH-90000
        }

        @Test
        @DisplayName("should fail result when policy denies the transition")
        void shouldFailWhenPolicyDenies() { // GH-90000
            // GIVEN
            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(false)); // GH-90000

            Event validatedEvent = buildValidatedEvent( // GH-90000
                    "proj-2", "intent", "context", "tenant-1", true);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).contains("POLICY_DENIED");
        }

        @Test
        @DisplayName("should request human approval when gate has unmet criteria")
        void shouldRequestHumanApprovalForUnmetCriteria() { // GH-90000
            // GIVEN
            when(policyEngine.evaluate(anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(true)); // GH-90000

            ApprovalRequest fakeRequest = new ApprovalRequest( // GH-90000
                    "req-1", "proj-3", "agent", ApprovalRequest.ApprovalType.PHASE_ADVANCE,
                    null, ApprovalRequest.ApprovalStatus.PENDING, "tenant-1",
                    null, null, null, null
            );
            when(humanApprovalService.requestApproval( // GH-90000
                    anyString(), anyString(), anyString(), // GH-90000
                    any(ApprovalRequest.ApprovalType.class), any())) // GH-90000
                .thenReturn(Promise.of(fakeRequest)); // GH-90000

            Event validatedEvent = buildValidatedEvent( // GH-90000
                    "proj-3", "intent", "context", "tenant-1", false);

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(validatedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            assertThat(result.getOutputEvents().get(0).getType()) // GH-90000
                    .isEqualTo(GateOrchestratorOperator.EVENT_APPROVAL_REQUESTED); // GH-90000
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
        void setUp() { // GH-90000
            operator = new AgentDispatchOperator(stageConfig); // GH-90000
        }

        @Test
        @DisplayName("should emit one dispatch event per agent assignment")
        void shouldEmitDispatchEventsForEachAgent() { // GH-90000
            // GIVEN
            StageSpec stage = new StageSpec(); // GH-90000
            // Inject agent assignments via reflection to avoid package-private setters
            setAgentAssignments(stage, List.of("context-analyzer", "requirement-extractor")); // GH-90000

            when(stageConfig.findById("context")).thenReturn(Optional.of(stage));

            Event gatePassedEvent = buildGatePassedEvent("proj-1", "intent", "context", "tenant-1"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(gatePassedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(2); // GH-90000
            assertThat(result.getOutputEvents()) // GH-90000
                    .allSatisfy(e -> assertThat(e.getType()) // GH-90000
                            .isEqualTo(AgentDispatchOperator.EVENT_AGENT_DISPATCHED)); // GH-90000
        }

        @Test
        @DisplayName("should forward event unchanged when no agents are assigned")
        void shouldForwardEventWhenNoAgentsAssigned() { // GH-90000
            // GIVEN
            StageSpec stage = new StageSpec(); // GH-90000
            // No agent assignments (default empty) // GH-90000
            when(stageConfig.findById("context")).thenReturn(Optional.of(stage));

            Event gatePassedEvent = buildGatePassedEvent("proj-2", "intent", "context", "tenant-1"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(gatePassedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            // Forwarded unchanged — same event type
            assertThat(result.getOutputEvents().get(0).getType()) // GH-90000
                    .isEqualTo(GateOrchestratorOperator.EVENT_GATE_PASSED); // GH-90000
        }

        /** Injects agentAssignments into a StageSpec (field is package-private JsonProperty). */ // GH-90000
        private void setAgentAssignments(StageSpec stage, List<String> agents) { // GH-90000
            try {
                var field = StageSpec.class.getDeclaredField("agentAssignments");
                field.setAccessible(true); // GH-90000
                field.set(stage, agents); // GH-90000
            } catch (Exception e) { // GH-90000
                throw new RuntimeException(e); // GH-90000
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
        void setUp() { // GH-90000
            when(aepEventBridge.publishRawEvent(anyString(), any(), any())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000
            operator = new LifecycleStatePublisherOperator(aepEventBridge); // GH-90000
        }

        @Test
        @DisplayName("should emit lifecycle.phase.advanced for agent.dispatched events")
        void shouldEmitPhaseAdvancedForAgentDispatch() { // GH-90000
            // GIVEN
            Event agentDispatchedEvent = GEvent.builder() // GH-90000
                .typeTenantVersion("tenant-1", AgentDispatchOperator.EVENT_AGENT_DISPATCHED, "v1") // GH-90000
                .addPayload("projectId",    "proj-1") // GH-90000
                .addPayload("fromPhase",    "intent") // GH-90000
                .addPayload("toPhase",      "context") // GH-90000
                .addPayload("tenantId",     "tenant-1") // GH-90000
                .addPayload("requestedBy",  "test-user") // GH-90000
                .addPayload("agentId",      "context-analyzer") // GH-90000
                .build(); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(agentDispatchedEvent)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event output = result.getOutputEvents().get(0); // GH-90000
            assertThat(output.getType()) // GH-90000
                    .isEqualTo(LifecycleStatePublisherOperator.EVENT_PHASE_ADVANCED); // GH-90000
            assertThat(output.getPayload("fromPhase")).isEqualTo("intent");
            assertThat(output.getPayload("toPhase")).isEqualTo("context");
            assertThat(output.getPayload("agentId")).isEqualTo("context-analyzer");
        }
    }
}
