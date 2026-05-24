/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.model.EventTimeContext;
import com.ghatana.aep.model.ReplayContext;
import com.ghatana.aep.model.UncertaintyContext;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.core.operator.agent.AgentOperatorKind;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;
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

/**
 * Regression coverage for governed TypedAgent to AgentOperator adaptation.
 *
 * @doc.type class
 * @doc.purpose Verifies AgentOperatorAdapter requires explicit governance metadata
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentOperatorAdapter")
class AgentOperatorAdapterTest extends EventloopTestBase {

    @Test
    @DisplayName("requires explicit schemas, side effects, and governance policies")
    void requiresExplicitGovernanceMetadata() {
        TestAgent agent = new TestAgent("governed-agent");

        assertThatThrownBy(() -> new AgentOperatorAdapter(
                agent,
                "agents/governed-agent@1.0.0",
                AgentOperatorKind.AGENT_REVIEW,
                "",
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
                policy("observability")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inputSchema");

        assertThatThrownBy(() -> new AgentOperatorAdapter(
                agent,
                "agents/governed-agent@1.0.0",
                AgentOperatorKind.AGENT_REVIEW,
                "schema://agent/input",
                "schema://agent/output",
                AgentSideEffectProfile.PROPOSE_ACTION,
                Map.of(),
                policy("tool"),
                policy("memory"),
                policy("retrieval"),
                policy("guardrail"),
                policy("replay"),
                policy("uncertainty"),
                policy("human-review"),
                policy("observability")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("modelPolicy");
    }

    @Test
    @DisplayName("exposes explicit governance metadata and delegates processing")
    void exposesGovernanceMetadataAndDelegatesProcessing() {
        AgentOperatorAdapter adapter = new AgentOperatorAdapter(
            new TestAgent("review-agent"),
            "agents/review-agent@1.0.0",
            AgentOperatorKind.AGENT_REVIEW,
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
            policy("observability"));

        assertThat(adapter.inputSchema()).isEqualTo("schema://agent/input");
        assertThat(adapter.outputSchema()).isEqualTo("schema://agent/output");
        assertThat(adapter.sideEffectProfile()).isEqualTo(AgentSideEffectProfile.PROPOSE_ACTION);
        assertThat(adapter.modelPolicy()).containsEntry("policyRef", "model");
        assertThat(adapter.toolPolicy()).containsEntry("policyRef", "tool");
        assertThat(adapter.replayPolicy()).containsEntry("policyRef", "replay");
        assertThat(adapter.humanReviewPolicy()).containsEntry("policyRef", "human-review");

        EventOperatorResult<Map<String, Object>> result = runPromise(() -> adapter.process(
            eventContext(Map.of("request", "summarize")),
            null));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(Map.of(
            "agentId", "review-agent",
            "tenantId", "tenant-a",
            "request", "summarize"));
    }

    private static Map<String, Object> policy(String policyRef) {
        return Map.of("policyRef", policyRef, "enforcement", "required");
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
            return Promise.of(AgentResult.success(
                Map.of(
                    "agentId", descriptor.getAgentId(),
                    "tenantId", ctx.getTenantId(),
                    "request", input.get("request")),
                descriptor.getAgentId(),
                Duration.ofMillis(1)));
        }
    }
}
