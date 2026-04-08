/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for P8-T7: composition policy, voting orchestration, and scatter-gather orchestration.
 *
 * @doc.type class
 * @doc.purpose Tests for CompositionPolicy, VotingOrchestration, ScatterGatherOrchestration
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CompositeOrchestration (P8-T7)")
@ExtendWith(MockitoExtension.class)
class CompositeOrchestrationTest extends EventloopTestBase {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("orchestrator")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();
    }

    // ─── CompositionPolicy ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CompositionPolicy")
    class CompositionPolicyTests {

        @Test
        @DisplayName("valid VOTING policy is constructed")
        void validVotingPolicy() {
            CompositionPolicy p = new CompositionPolicy(
                    "c1", CompositionPattern.VOTING, List.of("a", "b", "c"),
                    "tenant-1", VotingPolicy.MAJORITY, null, 5000L);
            assertThat(p.pattern()).isEqualTo(CompositionPattern.VOTING);
            assertThat(p.votingPolicy()).isEqualTo(VotingPolicy.MAJORITY);
        }

        @Test
        @DisplayName("VOTING without votingPolicy is rejected")
        void votingWithoutPolicyRejected() {
            assertThatThrownBy(() -> new CompositionPolicy(
                    "c2", CompositionPattern.VOTING, List.of("a"), "t", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("votingPolicy");
        }

        @Test
        @DisplayName("SCATTER_GATHER without aggregation is rejected")
        void scatterGatherWithoutAggregationRejected() {
            assertThatThrownBy(() -> new CompositionPolicy(
                    "c3", CompositionPattern.SCATTER_GATHER, List.of("a"), "t", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("aggregation");
        }

        @Test
        @DisplayName("empty memberAgentIds is rejected")
        void emptyMembersRejected() {
            assertThatThrownBy(() -> new CompositionPolicy(
                    "c4", CompositionPattern.PIPELINE, List.of(), "t", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("memberAgentIds");
        }

        @Test
        @DisplayName("memberAgentIds is immutable")
        void memberAgentIdsImmutable() {
            CompositionPolicy p = new CompositionPolicy(
                    "c5", CompositionPattern.PIPELINE, List.of("x"), "t", null, null, 0);
            assertThatThrownBy(() -> p.memberAgentIds().add("y"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ─── VotingOrchestration ─────────────────────────────────────────────────

    @Nested
    @DisplayName("VotingOrchestration")
    class VotingTests {

        private AgentInvoker buildInvoker(AgentResultStatus statusForAll) {
            return (agentId, input, context) -> Promise.of(AgentResult.<Object>builder()
                    .status(statusForAll)
                    .output(statusForAll == AgentResultStatus.SUCCESS ? "result-" + agentId : null)
                    .confidence(0.9)
                    .agentId(agentId)
                    .processingTime(Duration.ZERO)
                    .build());
        }

        @Test
        @DisplayName("MAJORITY: 2 of 3 succeed → returns success")
        void majorityQuorumMet() {
            int[] callCount = {0};
            AgentInvoker invoker = (agentId, input, context) -> {
                callCount[0]++;
                AgentResultStatus status = callCount[0] <= 2
                        ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
                return Promise.of(AgentResult.<Object>builder()
                        .status(status)
                        .output(status == AgentResultStatus.SUCCESS ? "ok" : null)
                        .confidence(0.9)
                        .agentId(agentId)
                        .processingTime(Duration.ZERO)
                        .build());
            };
            CompositionPolicy policy = new CompositionPolicy(
                    "vote-1", CompositionPattern.VOTING, List.of("a", "b", "c"),
                    "tenant-1", VotingPolicy.MAJORITY, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "input", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }

        @Test
        @DisplayName("MAJORITY: 1 of 3 succeeds → quorum not met → FAILED")
        void majorityQuorumNotMet() {
            int[] callCount = {0};
            AgentInvoker invoker = (agentId, input, context) -> {
                callCount[0]++;
                AgentResultStatus status = callCount[0] == 1
                        ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
                return Promise.of(AgentResult.<Object>builder()
                        .status(status)
                        .confidence(0.5)
                        .agentId(agentId)
                        .processingTime(Duration.ZERO)
                        .build());
            };
            CompositionPolicy policy = new CompositionPolicy(
                    "vote-2", CompositionPattern.VOTING, List.of("a", "b", "c"),
                    "tenant-1", VotingPolicy.MAJORITY, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "input", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
        }

        @Test
        @DisplayName("ANY_ONE: at least one success is sufficient")
        void anyOnePolicy() {
            AgentInvoker invoker = buildInvoker(AgentResultStatus.SUCCESS);
            CompositionPolicy policy = new CompositionPolicy(
                    "vote-3", CompositionPattern.VOTING, List.of("x"),
                    "tenant-1", VotingPolicy.ANY_ONE, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "in", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }

        @Test
        @DisplayName("wrong pattern throws IllegalArgumentException")
        void wrongPatternThrows() {
            AgentInvoker invoker = buildInvoker(AgentResultStatus.SUCCESS);
            CompositionPolicy policy = new CompositionPolicy(
                    "sg-1", CompositionPattern.SCATTER_GATHER, List.of("a"),
                    "tenant-1", null, AggregationStrategy.FIRST_SUCCESS, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker);
            assertThatThrownBy(() -> runPromise(() -> voting.execute(policy, "in", ctx)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── ScatterGatherOrchestration ───────────────────────────────────────────

    @Nested
    @DisplayName("ScatterGatherOrchestration")
    class ScatterGatherTests {

        private AgentInvoker successInvoker(double confidence) {
            return (agentId, input, context) -> Promise.of(AgentResult.<Object>builder()
                    .status(AgentResultStatus.SUCCESS)
                    .output("result-" + agentId)
                    .confidence(confidence)
                    .agentId(agentId)
                    .processingTime(Duration.ZERO)
                    .build());
        }

        @Test
        @DisplayName("FIRST_SUCCESS returns first successful result")
        void firstSuccess() {
            AgentInvoker invoker = successInvoker(0.8);
            CompositionPolicy policy = new CompositionPolicy(
                    "sg-1", CompositionPattern.SCATTER_GATHER, List.of("a", "b"),
                    "tenant-1", null, AggregationStrategy.FIRST_SUCCESS, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        }

        @Test
        @DisplayName("HIGHEST_CONFIDENCE returns the result with highest confidence")
        void highestConfidence() {
            int[] call = {0};
            AgentInvoker invoker = (agentId, input, context) -> {
                call[0]++;
                double conf = call[0] == 1 ? 0.5 : 0.9;
                return Promise.of(AgentResult.<Object>builder()
                        .status(AgentResultStatus.SUCCESS)
                        .output("result-" + call[0])
                        .confidence(conf)
                        .agentId(agentId)
                        .processingTime(Duration.ZERO)
                        .build());
            };
            CompositionPolicy policy = new CompositionPolicy(
                    "sg-2", CompositionPattern.SCATTER_GATHER, List.of("a", "b"),
                    "tenant-1", null, AggregationStrategy.HIGHEST_CONFIDENCE, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx));
            assertThat(result.getConfidence()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("COLLECT_ALL returns all results as output")
        void collectAll() {
            AgentInvoker invoker = successInvoker(0.7);
            CompositionPolicy policy = new CompositionPolicy(
                    "sg-3", CompositionPattern.SCATTER_GATHER, List.of("a", "b", "c"),
                    "tenant-1", null, AggregationStrategy.COLLECT_ALL, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker);
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
            assertThat(result.getOutput()).isInstanceOf(List.class);
            assertThat((List<?>) result.getOutput()).hasSize(3);
        }
    }
}
