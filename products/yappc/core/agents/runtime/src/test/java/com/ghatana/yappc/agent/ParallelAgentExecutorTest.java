package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.DefaultAgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ParallelAgentExecutor} — parallel execution and result aggregation.
 *
 * @doc.type class
 * @doc.purpose Unit tests for parallel agent dispatch and aggregation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ParallelAgentExecutor Tests")
class ParallelAgentExecutorTest extends EventloopTestBase {

    private ParallelAgentExecutor executor;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        executor = new ParallelAgentExecutor();
        context = AgentContext.builder()
                .agentId("ParallelAgentExecutorTest")
                .turnId("turn-001")
                .tenantId("tenant-test")
                .sessionId("session-test")
                .memoryStore(new EventLogMemoryStore())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAll
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeAll()")
    class ExecuteAll {

        @Test
        @DisplayName("single agent → list with one result")
        void singleAgentProducesOneResult() {
            TypedAgent<String, String> agent = successAgent("agent-1", "result-1", 1.0);

            List<AgentResult<String>> results = runPromise(() ->
                    executor.executeAll(List.of(agent), context, "input"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isFailed()).isFalse();
            assertThat(results.get(0).getOutput()).isEqualTo("result-1");
        }

        @Test
        @DisplayName("three agents → list with three results in invocation order")
        void multipleAgentsProduceAllResults() {
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("a1", "out-1", 0.9),
                    successAgent("a2", "out-2", 0.7),
                    successAgent("a3", "out-3", 0.5));

            List<AgentResult<String>> results = runPromise(() ->
                    executor.executeAll(agents, context, "input"));

            assertThat(results).hasSize(3);
            assertThat(results).as("all succeed").noneMatch(AgentResult::isFailed);
        }

        @Test
        @DisplayName("agent that throws → failure captured in AgentResult, not propagated")
        void agentExceptionCapturedAsFailure() {
            TypedAgent<String, String> thrower = throwingAgent("thrower", new RuntimeException("boom"));

            List<AgentResult<String>> results = runPromise(() ->
                    executor.executeAll(List.of(thrower), context, "input"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isFailed()).isTrue();
        }

        @Test
        @DisplayName("mix of success and failure agents → all results captured")
        void mixedResultsAllCaptured() {
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("ok-agent", "ok-out", 1.0),
                    throwingAgent("bad-agent", new RuntimeException("oops")));

            List<AgentResult<String>> results = runPromise(() ->
                    executor.executeAll(agents, context, "input"));

            assertThat(results).hasSize(2);
            long successes = results.stream().filter(r -> !r.isFailed()).count();
            long failures  = results.stream().filter(AgentResult::isFailed).count();
            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);
        }

        @Test
        @DisplayName("null agents list → NullPointerException")
        void nullAgentsThrowsNPE() {
            assertThatThrownBy(() ->
                    runPromise(() -> executor.executeAll(null, context, "input")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null context → NullPointerException")
        void nullContextThrowsNPE() {
            assertThatThrownBy(() ->
                    runPromise(() -> executor.executeAll(List.of(successAgent("a", "o", 1.0)), null, "input")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null input → NullPointerException")
        void nullInputThrowsNPE() {
            assertThatThrownBy(() ->
                    runPromise(() -> executor.executeAll(List.of(successAgent("a", "o", 1.0)), context, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — FIRST_WINS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate FIRST_WINS")
    class FirstWinsAggregation {

        @Test
        @DisplayName("returns first successful result regardless of confidence")
        void returnsFirstSuccess() {
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("low-conf", "first-output", 0.3),
                    successAgent("high-conf", "second-output", 0.9));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).isEqualTo("first-output");
        }

        @Test
        @DisplayName("first agent fails → falls through to second success")
        void skipsFirstFailure() {
            List<TypedAgent<String, String>> agents = List.of(
                    throwingAgent("fails-first", new RuntimeException("fail")),
                    successAgent("ok-second", "good-output", 0.8));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).isEqualTo("good-output");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — HIGHEST_CONFIDENCE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate HIGHEST_CONFIDENCE")
    class HighestConfidenceAggregation {

        @Test
        @DisplayName("returns result with highest confidence score")
        void picksHighestConfidence() {
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("low",  "low-output",  0.3),
                    successAgent("high", "high-output", 0.95),
                    successAgent("mid",  "mid-output",  0.6));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).isEqualTo("high-output");
            assertThat(result.getConfidence()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("ties in confidence → any of the tied results acceptable")
        void tieHandledWithoutCrash() {
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("a1", "output-a", 0.9),
                    successAgent("a2", "output-b", 0.9));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isFalse();
            assertThat(List.of("output-a", "output-b")).contains(result.getOutput());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — MAJORITY_VOTE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate MAJORITY_VOTE")
    class MajorityVoteAggregation {

        @Test
        @DisplayName("returns output that appears most frequently")
        void picksMajorityOutput() {
            // Two agents agree on "consensus-output", one outlier
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("a1", "consensus-output", 0.8),
                    successAgent("a2", "consensus-output", 0.7),
                    successAgent("a3", "outlier-output",   0.95));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.MAJORITY_VOTE));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).isEqualTo("consensus-output");
        }

        @Test
        @DisplayName("tie in vote count → some non-failed result is returned")
        void tieInVoteCountReturnsNonFailedResult() {
            // Two groups of one — tie in group size; winner is implementation-defined
            // (HashMap iteration order), so we assert only that a valid result is returned.
            List<TypedAgent<String, String>> agents = List.of(
                    successAgent("voter-a", "option-A", 0.9),
                    successAgent("voter-b", "option-B", 0.2));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.MAJORITY_VOTE));

            assertThat(result.isFailed()).isFalse();
            assertThat(List.of("option-A", "option-B")).contains(result.getOutput());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All agents fail
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("all agents fail — aggregate returns failure")
    class AllAgentsFail {

        @Test
        @DisplayName("FIRST_WINS with all failures → aggregate failure result")
        void allFailFirstWins() {
            List<TypedAgent<String, String>> agents = List.of(
                    throwingAgent("fail-1", new RuntimeException("err1")),
                    throwingAgent("fail-2", new RuntimeException("err2")));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("HIGHEST_CONFIDENCE with all failures → aggregate failure result")
        void allFailHighestConfidence() {
            List<TypedAgent<String, String>> agents = List.of(
                    throwingAgent("fail-x", new RuntimeException("nope")));

            AgentResult<String> result = runPromise(() ->
                    executor.executeAndAggregate(agents, context, "input",
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — minimal stub agents
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a stub TypedAgent that always returns a SUCCESS result with the given output and confidence.
     */
    private static TypedAgent<String, String> successAgent(String agentId, String output, double confidence) {
        AgentDescriptor desc = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .type(AgentType.DETERMINISTIC)
                .build();

        return new TypedAgent<>() {
            @Override
            public @NotNull AgentDescriptor descriptor() { return desc; }

            @Override
            public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
                return Promise.complete();
            }

            @Override
            public @NotNull Promise<Void> shutdown() { return Promise.complete(); }

            @Override
            public @NotNull Promise<HealthStatus> healthCheck() {
                return Promise.of(HealthStatus.HEALTHY);
            }

            @Override
            public @NotNull Promise<AgentResult<String>> process(
                    @NotNull AgentContext ctx, @NotNull String input) {
                return Promise.of(AgentResult.successWithConfidence(output, confidence, agentId, Duration.ZERO, "stub"));
            }
        };
    }

    /**
     * Creates a stub TypedAgent whose {@code process()} call throws the given exception,
     * simulating a failed agent computation.
     */
    private static TypedAgent<String, String> throwingAgent(String agentId, RuntimeException ex) {
        AgentDescriptor desc = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .type(AgentType.DETERMINISTIC)
                .build();

        return new TypedAgent<>() {
            @Override
            public @NotNull AgentDescriptor descriptor() { return desc; }

            @Override
            public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
                return Promise.complete();
            }

            @Override
            public @NotNull Promise<Void> shutdown() { return Promise.complete(); }

            @Override
            public @NotNull Promise<HealthStatus> healthCheck() {
                return Promise.of(HealthStatus.UNHEALTHY);
            }

            @Override
            public @NotNull Promise<AgentResult<String>> process(
                    @NotNull AgentContext ctx, @NotNull String input) {
                return Promise.ofException(ex);
            }
        };
    }
}
