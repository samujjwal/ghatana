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
        assertThat(adapter.getMetrics())
            .containsEntry("sideEffectProfile", "SIDE_EFFECTING")
            .containsEntry("success", 1L);
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

    private static Map<String, Object> policy(String policyRef) {
        return Map.of("policyRef", policyRef, "enforcement", "required", "enabled", true);
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

    private static final class TestAgent implements TypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        private TestAgent(String agentId) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Agent")
                .namespace("test")
                .type(AgentType.PROBABILISTIC)
                .build();
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
                .confidence(0.73)
                .status(com.ghatana.agent.AgentResultStatus.SUCCESS)
                .agentId(descriptor.getAgentId())
                .processingTime(Duration.ofMillis(1))
                .metrics(Map.of("retrievalConfidence", 0.67))
                .evidence(Map.of("scenario", "adapter-test"))
                .build());
        }
    }
}
