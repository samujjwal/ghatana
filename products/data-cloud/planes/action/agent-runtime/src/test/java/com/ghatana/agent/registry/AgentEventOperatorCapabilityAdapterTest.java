/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.aep.agent.capability.CapabilityInvocation;
import com.ghatana.aep.agent.capability.CapabilityResult;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Verifies TypedAgent event capabilities are adapted without unsafe defaults
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentEventOperatorCapabilityAdapter")
class AgentEventOperatorCapabilityAdapterTest extends EventloopTestBase {

    @Test
    @DisplayName("requires explicit schemas, side effects, memory, and governance policies")
    void requiresExplicitGovernanceMetadata() {
        TestAgent agent = new TestAgent("governed-agent");

        assertThatThrownBy(() -> adapter(agent, "", policy("model"), memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inputSchema");

        assertThatThrownBy(() -> adapter(agent, "schema://agent/input", Map.of(), memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("modelPolicy");

        assertThatThrownBy(() -> adapter(agent, "schema://agent/input", policy("model"), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("memoryStore");
    }

    @Test
    @DisplayName("empty toolPolicy fails instantiation")
    void emptyToolPolicyFails() {
        TestAgent agent = new TestAgent("tool-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/tool-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            Map.of(),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("toolPolicy");
    }

    @Test
    @DisplayName("empty memoryPolicy fails instantiation")
    void emptyMemoryPolicyFails() {
        TestAgent agent = new TestAgent("memory-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/memory-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            Map.of(),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("memoryPolicy");
    }

    @Test
    @DisplayName("empty retrievalPolicy fails instantiation")
    void emptyRetrievalPolicyFails() {
        TestAgent agent = new TestAgent("retrieval-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/retrieval-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            Map.of(),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retrievalPolicy");
    }

    @Test
    @DisplayName("P4-06: validate checks input/output schema mismatch")
    void validateChecksSchemaMismatch() {
        TestAgent agent = new TestAgent("schema-agent");
        var adapter = adapter(agent, "schema://agent/input", "schema://agent/output");

        var spec = new com.ghatana.aep.operator.contract.OperatorSpec(
            "test-operator",
            com.ghatana.aep.operator.contract.OperatorKind.TRANSFORM,
            "schema://wrong/input",  // Wrong input schema
            "schema://agent/output",
            Map.of(),
            Map.of()
        );

        var ctx = new com.ghatana.aep.operator.contract.ValidationContext(
            "tenant-1",
            Map.of("traceId", "trace-123"),
            Map.of("correlationId", "correlation-456")
        );

        var result = adapter.validate(spec, ctx);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("inputSchema mismatch"));
    }

    @Test
    @DisplayName("P4-06: validate requires humanReviewPolicy for SIDE_EFFECTING agents")
    void validateRequiresHumanReviewPolicyForSideEffecting() {
        TestAgent agent = new TestAgent("side-effect-agent");
        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/side-effect-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.SIDE_EFFECTING,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            Map.of(),
            policy("observability"),
            memoryStore()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("humanReviewPolicy");
    }

    @Test
    @DisplayName("P4-06: validate rejects blank tenant in ValidationContext")
    void validateRejectsBlankTenant() {
        TestAgent agent = new TestAgent("trace-agent");
        var adapter = adapter(agent, "schema://agent/input", "schema://agent/output");

        var spec = new com.ghatana.aep.operator.contract.OperatorSpec(
            "test-operator",
            com.ghatana.aep.operator.contract.OperatorKind.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            Map.of(),
            Map.of()
        );

                assertThatThrownBy(() -> new com.ghatana.aep.operator.contract.ValidationContext(
                        "",
                        Map.of("traceId", "trace-123"),
                        Map.of("correlationId", "correlation-456")
                )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    @DisplayName("P4-06: compile produces complete runtime plan")
    void compileProducesCompleteRuntimePlan() {
        TestAgent agent = new TestAgent("compile-agent");
        var adapter = adapter(agent, "schema://agent/input", "schema://agent/output");

        var spec = new com.ghatana.aep.operator.contract.OperatorSpec(
            "test-operator",
            com.ghatana.aep.operator.contract.OperatorKind.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            Map.of(),
            Map.of()
        );

        var ctx = new com.ghatana.aep.operator.contract.CompileContext(
            "tenant-1",
            Map.of("traceId", "trace-123")
        );

        var plan = adapter.compile(spec, ctx);

        assertThat(plan).isNotNull();
        assertThat(plan.planId()).isNotEmpty();
        assertThat(plan.operatorIds()).isNotEmpty();
        assertThat(plan.executionHints()).containsKeys("operatorKind", "inputSchema", "outputSchema", "sideEffectProfile");
        assertThat(plan.observability()).containsKeys("operatorType", "replayMode", "idempotencyRequired");
    }

    @Test
    @DisplayName("empty guardrailPolicy fails instantiation")
    void emptyGuardrailPolicyFails() {
        TestAgent agent = new TestAgent("guardrail-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/guardrail-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            Map.of(),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("guardrailPolicy");
    }

    @Test
    @DisplayName("empty replayPolicy fails instantiation")
    void emptyReplayPolicyFails() {
        TestAgent agent = new TestAgent("replay-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/replay-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            Map.of(),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("replayPolicy");
    }

    @Test
    @DisplayName("empty uncertaintyPolicy fails instantiation")
    void emptyUncertaintyPolicyFails() {
        TestAgent agent = new TestAgent("uncertainty-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/uncertainty-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            Map.of(),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uncertaintyPolicy");
    }

    @Test
    @DisplayName("empty humanReviewPolicy fails instantiation")
    void emptyHumanReviewPolicyFails() {
        TestAgent agent = new TestAgent("human-review-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/human-review-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            Map.of(),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("humanReviewPolicy");
    }

    @Test
    @DisplayName("empty observabilityPolicy fails instantiation")
    void emptyObservabilityPolicyFails() {
        TestAgent agent = new TestAgent("observability-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/observability-agent@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            Map.of(),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("observabilityPolicy");
    }

    @Test
    @DisplayName("rejects no-op memory stores unless explicitly policy disabled")
    void rejectsNoOpMemoryStoreUnlessPolicyDisabled() {
        TestAgent agent = new TestAgent("memory-agent");

        assertThatThrownBy(() -> adapter(
            agent,
            "schema://agent/input",
            policy("model"),
            policy("memory"),
            MemoryStore.noOp()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("durable MemoryStore");

        AgentEventOperatorCapabilityAdapter adapter = adapter(
            agent,
            "schema://agent/input",
            policy("model"),
            Map.of("policyRef", "memory-disabled-for-replay-test", "allowNoOpMemoryStore", true),
            MemoryStore.noOp());

        assertThat(adapter.descriptor().policies().get("memoryPolicy"))
            .isEqualTo(Map.of("policyRef", "memory-disabled-for-replay-test", "allowNoOpMemoryStore", true));
    }

    @Test
    @DisplayName("is an EventOperatorCapability and delegates TypedAgent execution")
    void exposesGovernanceMetadataAndDelegatesProcessing() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        assertThat(adapter.descriptor().inputSchema()).isEqualTo("schema://agent/input");
        assertThat(adapter.descriptor().outputSchema()).isEqualTo("schema://agent/output");
        assertThat(adapter.descriptor().sideEffectProfile()).isEqualTo(AgentSideEffectProfile.PROPOSE_ACTION);
        assertThat(adapter.getType()).isEqualTo(OperatorType.EVENT_OPERATOR_CAPABILITY);
        assertThat(adapter.descriptor().policies()).containsKeys(
            "modelPolicy",
            "toolPolicy",
            "memoryPolicy",
            "retrievalPolicy",
            "guardrailPolicy",
            "replayPolicy",
            "uncertaintyPolicy",
            "humanReviewPolicy",
            "observabilityPolicy");

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "summarize")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(Map.of(
            "agentId", "review-agent",
            "tenantId", "tenant-a",
            "traceId", "trace-1",
            "correlationId", "corr-1",
            "request", "summarize"));
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.73);
        assertThat(adapter.getMetrics())
            .containsEntry("invocations", 1L)
            .containsEntry("success", 1L)
            .containsEntry("capabilityId", adapter.capabilityId().value());
    }

    @Test
    @DisplayName("fails fast when production trace context is missing")
    void requiresRuntimeTraceAndCorrelation() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        OperatorRuntimeContext missingTrace = new OperatorRuntimeContext(
            "tenant-a",
            Optional.empty(),
            Optional.of("corr-1"),
            Map.of(),
            Map.of());

        assertThatThrownBy(() -> runPromise(() -> adapter.process(eventContext(Map.of("request", "x")), missingTrace)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traceId is required");
    }

    @Test
    @DisplayName("fails fast when correlation ID is missing")
    void requiresCorrelationId() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        OperatorRuntimeContext missingCorrelation = new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.empty(),
            Map.of(),
            Map.of());

        assertThatThrownBy(() -> runPromise(() -> adapter.process(eventContext(Map.of("request", "x")), missingCorrelation)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("correlationId is required");
    }

    @Test
    @DisplayName("fails fast when tenant ID is blank")
    void requiresNonBlankTenantId() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        assertThatThrownBy(() -> runPromise(() -> adapter.process(
            eventContextWithTenant("", Map.of("request", "x")),
            runtimeContext())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("valid context maps into AgentContext with required fields")
    void validContextMapsIntoAgentContext() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "valid-context")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output).containsEntry("tenantId", "tenant-a")
                .containsEntry("traceId", "trace-1")
                .containsEntry("correlationId", "corr-1"));
    }

    @Test
    @DisplayName("modelConfidence equals clamped agent confidence")
    void modelConfidenceEqualsClampedAgentConfidence() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("confidence-agent", 0.85),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "confidence-test")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("retrievalConfidence comes from agent metrics when present")
    void retrievalConfidenceFromAgentMetrics() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("retrieval-agent", 0.75, Map.of("retrievalConfidence", 0.92)),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "retrieval-test")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.uncertainty().retrievalConfidence()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("calibrationScore comes from agent metrics when present")
    void calibrationScoreFromAgentMetrics() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("calibration-agent", 0.80, Map.of("calibrationScore", 0.88)),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "calibration-test")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.uncertainty().calibrationScore()).isEqualTo(0.88);
    }

    @Test
    @DisplayName("evidence includes required fields for replay and audit")
    void evidenceIncludesRequiredFields() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("evidence-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "evidence-test")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.evidence())
            .containsEntry("agentRef", "agents/evidence-agent@1.0.0")
            .containsEntry("capabilityId", adapter.capabilityId().value())
            .containsEntry("operatorId", adapter.id().toString())
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("traceId", "trace-1")
            .containsEntry("correlationId", "corr-1")
            .containsEntry("sideEffectProfile", "PROPOSE_ACTION")
            .containsKey("idempotencyRequired");
    }

    @Test
    @DisplayName("NaN confidence clamps to 0.0")
    void nanConfidenceClampsToZero() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("nan-agent", Double.NaN),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "nan-test")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.uncertainty().modelConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("side-effecting capabilities require tool approval audit and idempotency controls")
    void sideEffectingCapabilitiesRequireControls() {
        TestAgent agent = new TestAgent("action-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/action-agent@1.0.0",
            AgentCapabilityRole.AGENT_ACTION,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.SIDE_EFFECTING,
            policy("model"),
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("allowedTools");
    }

    @Test
    @DisplayName("capability invocation delegates through canonical event context path")
    void invokeUsesCanonicalEventContextPath() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        CapabilityInvocation<EventContext<Map<String, Object>>> invocation = new CapabilityInvocation<>(
            adapter.capabilityId(),
            AgentContext.builder()
                .tenantId("tenant-a")
                .agentId("agents/review-agent@1.0.0")
                .turnId("turn-1")
                .memoryStore(memoryStore())
                .build(),
            eventContext(Map.of("request", "invoke")),
            Map.of("operatorRuntimeContext", runtimeContext()));

        CapabilityResult<EventOperatorResult<Map<String, Object>>> result =
            runPromise(() -> adapter.invoke(invocation));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasValueSatisfying(output ->
            assertThat(output.output()).hasValueSatisfying(payload ->
                assertThat(payload).containsEntry("request", "invoke")));
        assertThat(adapter.getMetrics())
            .containsEntry("invocations", 1L)
            .containsEntry("success", 1L);
    }

    @Test
    @DisplayName("event entrypoint delegates through typed conversion without duplicate logic")
    void processEventDelegatesThroughTypedConversion() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());

        Event event = Event.builder()
            .typeTenantVersion("tenant-a", "agent.requested", "v1")
            .headers(Map.of("traceId", "trace-1", "correlationId", "corr-1"))
            .payload(Map.of("request", "event-entrypoint"))
            .build();

        runPromise(() -> adapter.process(event));

        assertThat(adapter.getMetrics())
            .containsEntry("invocations", 1L)
            .containsEntry("success", 1L);
    }

    @Test
    @DisplayName("tenant mismatch between event and runtime context fails fast")
    void rejectsTenantMismatch() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(
            new TestAgent("review-agent"),
            "schema://agent/input",
            policy("model"),
            memoryStore());
        OperatorRuntimeContext runtimeTenant = new OperatorRuntimeContext(
            "tenant-b",
            Optional.of("trace-1"),
            Optional.of("corr-1"),
            Map.of(),
            Map.of());

        assertThatThrownBy(() -> runPromise(() -> adapter.process(
            eventContext(Map.of("request", "x")),
            runtimeTenant)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId must match");
    }

    @Test
    @DisplayName("side-effecting capabilities pass with explicit tool approval audit and idempotency controls")
    void acceptsSideEffectingCapabilityWithControls() {
        AgentEventOperatorCapabilityAdapter adapter = new AgentEventOperatorCapabilityAdapter(
            new TestAgent("action-agent"),
            "agents/action-agent@1.0.0",
            AgentCapabilityRole.AGENT_ACTION,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.SIDE_EFFECTING,
            policy("model"),
            Map.of("allowedTools", java.util.List.of("pagerduty.incident.create")),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            Map.of("idempotencyRequired", true, "mode", "recorded_output"),
            policy("uncertainty"),
            Map.of("approvalPolicy", "human_required"),
            Map.of("auditPolicy", Map.of("sink", "eventcloud")),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "side-effect")),
            runtimeContext()));

        assertThat(result.success()).isTrue();
        assertThat(result.evidence())
            .containsEntry("sideEffectProfile", "SIDE_EFFECTING")
            .containsEntry("auditPolicy", Map.of("sink", "eventcloud"))
            .containsEntry("idempotencyRequired", true);
        assertThat(adapter.getMetrics())
            .containsEntry("sideEffectProfile", "SIDE_EFFECTING")
            .containsEntry("success", 1L);
    }

    @Test
    @DisplayName("PENDING_APPROVAL status maps to non-success result with approval evidence")
    void pendingApprovalMapsToNonSuccessResult() {
        TestAgent pendingAgent = new TestAgent("pending-approval-agent", 0.5, Map.of()) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(
                    @NotNull AgentContext ctx,
                    @NotNull Map<String, Object> input) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("agentId", descriptor().getAgentId(), "pendingAction", "create-incident"))
                    .confidence(0.5)
                    .status(com.ghatana.agent.AgentResultStatus.PENDING_APPROVAL)
                    .agentId(descriptor().getAgentId())
                    .explanation("Action requires human approval before execution")
                    .processingTime(Duration.ofMillis(10))
                    .metrics(Map.of("requiresApproval", true))
                    .build());
            }
        };

        AgentEventOperatorCapabilityAdapter adapter = adapter(
            pendingAgent,
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "approval-required")),
            runtimeContext()));

        // TEST-P1-006: Verify pending approval is not reported as success
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0)).contains("approval");

        // Verify evidence includes approval status
        assertThat(result.evidence())
            .containsEntry("approvalStatus", "PENDING_APPROVAL")
            .containsEntry("agentRef", "agents/pending-approval-agent@1.0.0")
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("traceId", "trace-1")
            .containsEntry("correlationId", "corr-1");

        // Verify metrics track pending approval separately from success
        assertThat(adapter.getMetrics())
            .containsEntry("invocations", 1L)
            .containsEntry("success", 0L)
            .containsEntry("pendingApproval", 1L)
            .containsEntry("denied", 1L) // pending approval counts as denied for metrics
            .containsEntry("lastOutcome", "pending_approval");
    }

    @Test
    @DisplayName("DENIED status increments guardrailDenied when denialCategory is guardrail")
    void deniedStatusTracksGuardrailDenials() {
        TestAgent deniedAgent = new TestAgent("guardrail-denied-agent", 0.3, Map.of("denialCategory", "guardrail")) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(
                    @NotNull AgentContext ctx,
                    @NotNull Map<String, Object> input) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .confidence(0.3)
                    .status(com.ghatana.agent.AgentResultStatus.DENIED)
                    .agentId(descriptor().getAgentId())
                    .explanation("Denied by guardrail policy")
                    .processingTime(Duration.ofMillis(5))
                    .metrics(Map.of("denialCategory", "guardrail"))
                    .build());
            }
        };

        AgentEventOperatorCapabilityAdapter adapter = adapter(
            deniedAgent,
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "denied-guardrail")),
            runtimeContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.evidence())
            .containsEntry("approvalStatus", "DENIED")
            .containsEntry("denialCategory", "guardrail")
            .containsEntry("outcome", "DENIED");
        assertThat(adapter.getMetrics())
            .containsEntry("denied", 1L)
            .containsEntry("guardrailDenied", 1L)
            .containsEntry("policyDenied", 0L)
            .containsEntry("lastOutcome", "guardrail_denied");
    }

    @Test
    @DisplayName("DENIED status increments policyDenied when denialCategory is policy")
    void deniedStatusTracksPolicyDenials() {
        TestAgent deniedAgent = new TestAgent("policy-denied-agent", 0.2, Map.of("denialCategory", "policy")) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(
                    @NotNull AgentContext ctx,
                    @NotNull Map<String, Object> input) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .confidence(0.2)
                    .status(com.ghatana.agent.AgentResultStatus.DENIED)
                    .agentId(descriptor().getAgentId())
                    .explanation("Denied by policy engine")
                    .processingTime(Duration.ofMillis(5))
                    .metrics(Map.of("denialCategory", "policy"))
                    .build());
            }
        };

        AgentEventOperatorCapabilityAdapter adapter = adapter(
            deniedAgent,
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "denied-policy")),
            runtimeContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.evidence())
            .containsEntry("approvalStatus", "DENIED")
            .containsEntry("denialCategory", "policy")
            .containsEntry("outcome", "DENIED");
        assertThat(adapter.getMetrics())
            .containsEntry("denied", 1L)
            .containsEntry("guardrailDenied", 0L)
            .containsEntry("policyDenied", 1L)
            .containsEntry("lastOutcome", "policy_denied");
    }

    @Test
    @DisplayName("FAILED status emits audit evidence with outcome metadata")
    void failedStatusEmitsEvidence() {
        TestAgent failedAgent = new TestAgent("failed-agent", 0.0, Map.of()) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(
                    @NotNull AgentContext ctx,
                    @NotNull Map<String, Object> input) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .confidence(0.0)
                    .status(com.ghatana.agent.AgentResultStatus.FAILED)
                    .agentId(descriptor().getAgentId())
                    .explanation("Agent execution failed for downstream dependency")
                    .processingTime(Duration.ofMillis(7))
                    .metrics(Map.of("errorCode", "DEPENDENCY_FAILURE"))
                    .build());
            }
        };

        AgentEventOperatorCapabilityAdapter adapter = adapter(
            failedAgent,
            "schema://agent/input",
            policy("model"),
            memoryStore());

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "failed-case")),
            runtimeContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("failed"));
        assertThat(result.evidence())
            .containsEntry("agentRef", "agents/failed-agent@1.0.0")
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("traceId", "trace-1")
            .containsEntry("correlationId", "corr-1")
            .containsEntry("outcome", "FAILED");
        assertThat(adapter.getMetrics())
            .containsEntry("invocations", 1L)
            .containsEntry("failure", 1L)
            .containsEntry("lastOutcome", "failure");
    }

    private static AgentEventOperatorCapabilityAdapter adapter(
            TestAgent agent,
            String inputSchema,
            Map<String, Object> modelPolicy,
            MemoryStore memoryStore) {
        return new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/" + agent.descriptor().getAgentId() + "@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            inputSchema,
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            modelPolicy,
            policy("tool"),
            policy("memory"),
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore);
    }

            private static AgentEventOperatorCapabilityAdapter adapter(
                TestAgent agent,
                String inputSchema,
                String outputSchema) {
            return new AgentEventOperatorCapabilityAdapter(
                agent,
                "agents/" + agent.descriptor().getAgentId() + "@1.0.0",
                AgentCapabilityRole.AGENT_REVIEW,
                inputSchema,
                outputSchema,
                AgentSideEffectProfile.PROPOSE_ACTION,
                policy("model"),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability"),
                memoryStore());
            }

    private static AgentEventOperatorCapabilityAdapter adapter(
            TestAgent agent,
            String inputSchema,
            Map<String, Object> modelPolicy,
            Map<String, Object> memoryPolicy,
            MemoryStore memoryStore) {
        return new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/" + agent.descriptor().getAgentId() + "@1.0.0",
            AgentCapabilityRole.AGENT_REVIEW,
            inputSchema,
            "schema://agent/output",
            AgentSideEffectProfile.PROPOSE_ACTION,
            modelPolicy,
            policy("tool"),
            memoryPolicy,
            policy("retrieval"),
            policy("guardrail"),
            policy("replay"),
            policy("uncertainty"),
            policy("human-review"),
            policy("observability"),
            memoryStore);
    }

    private static Map<String, Object> policy(String policyRef) {
        return Map.of(
            "policyRef", policyRef,
            "enforcement", "required",
            "enabled", true,
            "idempotencyRequired", true);
    }

    private static MemoryStore memoryStore() {
        return mock(MemoryStore.class);
    }

    private static OperatorRuntimeContext runtimeContext() {
        return new OperatorRuntimeContext(
            "tenant-a",
            Optional.of("trace-1"),
            Optional.of("corr-1"),
            Map.of("runtimePolicy", "required"),
            Map.of("profile", "production"));
    }

    private static EventContext<Map<String, Object>> eventContext(Map<String, Object> input) {
        return new EventContext<>(
            "tenant-a",
            java.util.List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(
                ReplayContext.ReplayMode.LIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()),
            Optional.of(input));
    }

    private static EventContext<Map<String, Object>> eventContextWithTenant(String tenantId, Map<String, Object> input) {
        return new EventContext<>(
            tenantId,
            java.util.List.of(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            new EventTimeContext(
                EventTimeContext.TimeMode.EVENT_TIME,
                Optional.empty(),
                Duration.ZERO,
                EventTimeContext.LateEventBehavior.INCORPORATE,
                Optional.empty()),
            UncertaintyContext.certain(),
            new ReplayContext(
                ReplayContext.ReplayMode.LIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()),
            Optional.of(input));
    }

    private static class TestAgent implements TypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;
        private final double confidence;
        private final Map<String, Object> metrics;

        private TestAgent(String agentId) {
            this(agentId, 0.73, Map.of("retrievalConfidence", 0.67));
        }

        private TestAgent(String agentId, double confidence) {
            this(agentId, confidence, Map.of("retrievalConfidence", 0.67));
        }

        private TestAgent(String agentId, double confidence, Map<String, Object> metrics) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Agent")
                .namespace("test")
                .type(AgentType.PROBABILISTIC)
                .build();
            this.confidence = confidence;
            this.metrics = metrics;
        }

        @Override
        public @NotNull AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.healthy("ok"));
        }

        @Override
        public @NotNull Promise<AgentResult<Map<String, Object>>> process(
                @NotNull AgentContext ctx,
                @NotNull Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of(
                    "agentId", descriptor.getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "traceId", ctx.getTraceId(),
                    "correlationId", ctx.getMetadata().get("correlationId"),
                    "request", input.get("request")))
                .confidence(confidence)
                .status(com.ghatana.agent.AgentResultStatus.SUCCESS)
                .agentId(descriptor.getAgentId())
                .processingTime(Duration.ofMillis(1))
                .metrics(metrics)
                .evidence(Map.of("scenario", "adapter-test"))
                .build());
        }
    }
}
