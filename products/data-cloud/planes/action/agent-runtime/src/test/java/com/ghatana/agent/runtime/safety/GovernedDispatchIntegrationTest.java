/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.registry.AgentEventOperatorCapabilityAdapter;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorRuntimeContext;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Integration checks for governed adapter behavior on the current runtime contracts
 * @doc.layer agent-runtime
 * @doc.pattern Integration Test
 */
@DisplayName("Governed Dispatch Integration Tests")
class GovernedDispatchIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("governed adapter requires trace and correlation in runtime context")
    void governedAdapterRequiresTraceAndCorrelation() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(new TestAgent("trace-agent"));

        OperatorRuntimeContext missingTrace = new OperatorRuntimeContext(
            "tenant-a",
            Optional.empty(),
            Optional.of("corr-1"),
            Map.of(),
            Map.of());

        assertThatThrownBy(() -> runPromise(() -> adapter.process(eventContext(Map.of("request", "x")), missingTrace)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traceId is required");

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
    @DisplayName("governed adapter returns auditable pending-approval result")
    void governedAdapterPendingApprovalIsAuditable() {
        AgentEventOperatorCapabilityAdapter adapter = adapter(new PendingApprovalAgent("approval-agent"));

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "approval-needed")),
            runtimeContext()));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.evidence())
            .containsEntry("approvalStatus", "PENDING_APPROVAL")
            .containsEntry("tenantId", "tenant-a")
            .containsEntry("traceId", "trace-1")
            .containsEntry("correlationId", "corr-1");
    }

    @Test
    @DisplayName("side-effecting governed adapter requires explicit controls")
    void sideEffectingGovernedAdapterRequiresControls() {
        TestAgent agent = new TestAgent("action-agent");

        assertThatThrownBy(() -> new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/action-agent@1.0.0",
            AgentCapabilityRole.AGENT_ACTION,
            "schema://agent/input",
            "schema://agent/output",
            AgentSideEffectProfile.SIDE_EFFECTING,
            policy("model"),
            Map.of("policyRef", "tool"),
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

    private static AgentEventOperatorCapabilityAdapter adapter(TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        return new AgentEventOperatorCapabilityAdapter(
            agent,
            "agents/" + agent.descriptor().getAgentId() + "@1.0.0",
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
            policy("observability"),
            memoryStore());
    }

    private static MemoryStore memoryStore() {
        return mock(MemoryStore.class);
    }

    private static Map<String, Object> policy(String policyRef) {
        return Map.of("policyRef", policyRef, "enforcement", "required", "enabled", true);
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
            List.of(),
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

        private TestAgent(String agentId) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name("Test Agent")
                .namespace("test")
                .type(AgentType.PROBABILISTIC)
                .build();
        }

        @Override
        public AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        public Promise<com.ghatana.platform.health.HealthStatus> healthCheck() {
            return Promise.of(com.ghatana.platform.health.HealthStatus.healthy("ok"));
        }

        @Override
        public Promise<AgentResult<Map<String, Object>>> process(AgentContext ctx, Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of(
                    "agentId", descriptor.getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "traceId", ctx.getTraceId(),
                    "correlationId", ctx.getMetadata().get("correlationId"),
                    "request", input.get("request")))
                .confidence(0.85)
                .status(AgentResultStatus.SUCCESS)
                .agentId(descriptor.getAgentId())
                .processingTime(Duration.ofMillis(1))
                .build());
        }
    }

    private static final class PendingApprovalAgent extends TestAgent {
        private PendingApprovalAgent(String agentId) {
            super(agentId);
        }

        @Override
        public Promise<AgentResult<Map<String, Object>>> process(AgentContext ctx, Map<String, Object> input) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of(
                    "agentId", descriptor().getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "traceId", ctx.getTraceId(),
                    "correlationId", ctx.getMetadata().get("correlationId"),
                    "request", input.get("request")))
                .confidence(0.6)
                .status(AgentResultStatus.PENDING_APPROVAL)
                .explanation("Agent execution requires approval")
                .agentId(descriptor().getAgentId())
                .processingTime(Duration.ofMillis(1))
                .build());
        }
    }
}