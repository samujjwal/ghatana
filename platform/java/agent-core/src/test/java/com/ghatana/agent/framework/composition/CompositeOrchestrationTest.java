/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("CompositeOrchestration (P8-T7) [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class CompositeOrchestrationTest extends EventloopTestBase {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("orchestrator [GH-90000]")
                .tenantId("tenant-1 [GH-90000]")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000
    }

    // ─── CompositionPolicy ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CompositionPolicy [GH-90000]")
    class CompositionPolicyTests {

        @Test
        @DisplayName("valid VOTING policy is constructed [GH-90000]")
        void validVotingPolicy() { // GH-90000
            CompositionPolicy p = new CompositionPolicy( // GH-90000
                    "c1", CompositionPattern.VOTING, List.of("a", "b", "c"), // GH-90000
                    "tenant-1", VotingPolicy.MAJORITY, null, 5000L);
            assertThat(p.pattern()).isEqualTo(CompositionPattern.VOTING); // GH-90000
            assertThat(p.votingPolicy()).isEqualTo(VotingPolicy.MAJORITY); // GH-90000
        }

        @Test
        @DisplayName("VOTING without votingPolicy is rejected [GH-90000]")
        void votingWithoutPolicyRejected() { // GH-90000
            assertThatThrownBy(() -> new CompositionPolicy( // GH-90000
                    "c2", CompositionPattern.VOTING, List.of("a [GH-90000]"), "t", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("votingPolicy [GH-90000]");
        }

        @Test
        @DisplayName("SCATTER_GATHER without aggregation is rejected [GH-90000]")
        void scatterGatherWithoutAggregationRejected() { // GH-90000
            assertThatThrownBy(() -> new CompositionPolicy( // GH-90000
                    "c3", CompositionPattern.SCATTER_GATHER, List.of("a [GH-90000]"), "t", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("aggregation [GH-90000]");
        }

        @Test
        @DisplayName("empty memberAgentIds is rejected [GH-90000]")
        void emptyMembersRejected() { // GH-90000
            assertThatThrownBy(() -> new CompositionPolicy( // GH-90000
                    "c4", CompositionPattern.PIPELINE, List.of(), "t", null, null, 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("memberAgentIds [GH-90000]");
        }

        @Test
        @DisplayName("memberAgentIds is immutable [GH-90000]")
        void memberAgentIdsImmutable() { // GH-90000
            CompositionPolicy p = new CompositionPolicy( // GH-90000
                    "c5", CompositionPattern.PIPELINE, List.of("x [GH-90000]"), "t", null, null, 0);
            assertThatThrownBy(() -> p.memberAgentIds().add("y [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // ─── VotingOrchestration ─────────────────────────────────────────────────

    @Nested
    @DisplayName("VotingOrchestration [GH-90000]")
    class VotingTests {

        private AgentInvoker buildInvoker(AgentResultStatus statusForAll) { // GH-90000
            return (agentId, input, context) -> Promise.of(AgentResult.<Object>builder() // GH-90000
                    .status(statusForAll) // GH-90000
                    .output(statusForAll == AgentResultStatus.SUCCESS ? "result-" + agentId : null) // GH-90000
                    .confidence(0.9) // GH-90000
                    .agentId(agentId) // GH-90000
                    .processingTime(Duration.ZERO) // GH-90000
                    .build()); // GH-90000
        }

        @Test
        @DisplayName("MAJORITY: 2 of 3 succeed → returns success [GH-90000]")
        void majorityQuorumMet() { // GH-90000
            int[] callCount = {0};
            AgentInvoker invoker = (agentId, input, context) -> { // GH-90000
                callCount[0]++;
                AgentResultStatus status = callCount[0] <= 2
                        ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
                return Promise.of(AgentResult.<Object>builder() // GH-90000
                        .status(status) // GH-90000
                        .output(status == AgentResultStatus.SUCCESS ? "ok" : null) // GH-90000
                        .confidence(0.9) // GH-90000
                        .agentId(agentId) // GH-90000
                        .processingTime(Duration.ZERO) // GH-90000
                        .build()); // GH-90000
            };
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "vote-1", CompositionPattern.VOTING, List.of("a", "b", "c"), // GH-90000
                    "tenant-1", VotingPolicy.MAJORITY, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "input", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("MAJORITY: 1 of 3 succeeds → quorum not met → FAILED [GH-90000]")
        void majorityQuorumNotMet() { // GH-90000
            int[] callCount = {0};
            AgentInvoker invoker = (agentId, input, context) -> { // GH-90000
                callCount[0]++;
                AgentResultStatus status = callCount[0] == 1
                        ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
                return Promise.of(AgentResult.<Object>builder() // GH-90000
                        .status(status) // GH-90000
                        .confidence(0.5) // GH-90000
                        .agentId(agentId) // GH-90000
                        .processingTime(Duration.ZERO) // GH-90000
                        .build()); // GH-90000
            };
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "vote-2", CompositionPattern.VOTING, List.of("a", "b", "c"), // GH-90000
                    "tenant-1", VotingPolicy.MAJORITY, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "input", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); // GH-90000
        }

        @Test
        @DisplayName("ANY_ONE: at least one success is sufficient [GH-90000]")
        void anyOnePolicy() { // GH-90000
            AgentInvoker invoker = buildInvoker(AgentResultStatus.SUCCESS); // GH-90000
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "vote-3", CompositionPattern.VOTING, List.of("x [GH-90000]"),
                    "tenant-1", VotingPolicy.ANY_ONE, null, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> voting.execute(policy, "in", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("wrong pattern throws IllegalArgumentException [GH-90000]")
        void wrongPatternThrows() { // GH-90000
            AgentInvoker invoker = buildInvoker(AgentResultStatus.SUCCESS); // GH-90000
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "sg-1", CompositionPattern.SCATTER_GATHER, List.of("a [GH-90000]"),
                    "tenant-1", null, AggregationStrategy.FIRST_SUCCESS, 0);
            VotingOrchestration voting = new VotingOrchestration(invoker); // GH-90000
            assertThatThrownBy(() -> runPromise(() -> voting.execute(policy, "in", ctx))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─── ScatterGatherOrchestration ───────────────────────────────────────────

    @Nested
    @DisplayName("ScatterGatherOrchestration [GH-90000]")
    class ScatterGatherTests {

        private AgentInvoker successInvoker(double confidence) { // GH-90000
            return (agentId, input, context) -> Promise.of(AgentResult.<Object>builder() // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .output("result-" + agentId) // GH-90000
                    .confidence(confidence) // GH-90000
                    .agentId(agentId) // GH-90000
                    .processingTime(Duration.ZERO) // GH-90000
                    .build()); // GH-90000
        }

        @Test
        @DisplayName("FIRST_SUCCESS returns first successful result [GH-90000]")
        void firstSuccess() { // GH-90000
            AgentInvoker invoker = successInvoker(0.8); // GH-90000
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "sg-1", CompositionPattern.SCATTER_GATHER, List.of("a", "b"), // GH-90000
                    "tenant-1", null, AggregationStrategy.FIRST_SUCCESS, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("HIGHEST_CONFIDENCE returns the result with highest confidence [GH-90000]")
        void highestConfidence() { // GH-90000
            int[] call = {0};
            AgentInvoker invoker = (agentId, input, context) -> { // GH-90000
                call[0]++;
                double conf = call[0] == 1 ? 0.5 : 0.9;
                return Promise.of(AgentResult.<Object>builder() // GH-90000
                        .status(AgentResultStatus.SUCCESS) // GH-90000
                        .output("result-" + call[0]) // GH-90000
                        .confidence(conf) // GH-90000
                        .agentId(agentId) // GH-90000
                        .processingTime(Duration.ZERO) // GH-90000
                        .build()); // GH-90000
            };
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "sg-2", CompositionPattern.SCATTER_GATHER, List.of("a", "b"), // GH-90000
                    "tenant-1", null, AggregationStrategy.HIGHEST_CONFIDENCE, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx)); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.9); // GH-90000
        }

        @Test
        @DisplayName("COLLECT_ALL returns all results as output [GH-90000]")
        void collectAll() { // GH-90000
            AgentInvoker invoker = successInvoker(0.7); // GH-90000
            CompositionPolicy policy = new CompositionPolicy( // GH-90000
                    "sg-3", CompositionPattern.SCATTER_GATHER, List.of("a", "b", "c"), // GH-90000
                    "tenant-1", null, AggregationStrategy.COLLECT_ALL, 0);
            ScatterGatherOrchestration sgo = new ScatterGatherOrchestration(invoker); // GH-90000
            AgentResult<?> result = runPromise(() -> sgo.execute(policy, "in", ctx)); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
            assertThat(result.getOutput()).isInstanceOf(List.class); // GH-90000
            assertThat((List<?>) result.getOutput()).hasSize(3); // GH-90000
        }
    }
}
