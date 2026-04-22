package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
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
@DisplayName("ParallelAgentExecutor Tests [GH-90000]")
class ParallelAgentExecutorTest extends EventloopTestBase {

    private ParallelAgentExecutor executor;
    private AgentContext context;

    @BeforeEach
    void setUp() { // GH-90000
        executor = new ParallelAgentExecutor(); // GH-90000
        context = AgentContext.builder() // GH-90000
                .agentId("ParallelAgentExecutorTest [GH-90000]")
                .turnId("turn-001 [GH-90000]")
                .tenantId("tenant-test [GH-90000]")
                .sessionId("session-test [GH-90000]")
                .memoryStore(new EventLogMemoryStore()) // GH-90000
                .build(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAll
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeAll() [GH-90000]")
    class ExecuteAll {

        @Test
        @DisplayName("single agent → list with one result [GH-90000]")
        void singleAgentProducesOneResult() { // GH-90000
            TypedAgent<String, String> agent = successAgent("agent-1", "result-1", 1.0); // GH-90000

            List<AgentResult<String>> results = runPromise(() -> // GH-90000
                    executor.executeAll(List.of(agent), context, "input")); // GH-90000

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).isFailed()).isFalse(); // GH-90000
            assertThat(results.get(0).getOutput()).isEqualTo("result-1 [GH-90000]");
        }

        @Test
        @DisplayName("three agents → list with three results in invocation order [GH-90000]")
        void multipleAgentsProduceAllResults() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("a1", "out-1", 0.9), // GH-90000
                    successAgent("a2", "out-2", 0.7), // GH-90000
                    successAgent("a3", "out-3", 0.5)); // GH-90000

            List<AgentResult<String>> results = runPromise(() -> // GH-90000
                    executor.executeAll(agents, context, "input")); // GH-90000

            assertThat(results).hasSize(3); // GH-90000
            assertThat(results).as("all succeed [GH-90000]").noneMatch(AgentResult::isFailed);
        }

        @Test
        @DisplayName("agent that throws → failure captured in AgentResult, not propagated [GH-90000]")
        void agentExceptionCapturedAsFailure() { // GH-90000
            TypedAgent<String, String> thrower = throwingAgent("thrower", new RuntimeException("boom [GH-90000]"));

            List<AgentResult<String>> results = runPromise(() -> // GH-90000
                    executor.executeAll(List.of(thrower), context, "input")); // GH-90000

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.get(0).isFailed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("mix of success and failure agents → all results captured [GH-90000]")
        void mixedResultsAllCaptured() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("ok-agent", "ok-out", 1.0), // GH-90000
                    throwingAgent("bad-agent", new RuntimeException("oops [GH-90000]")));

            List<AgentResult<String>> results = runPromise(() -> // GH-90000
                    executor.executeAll(agents, context, "input")); // GH-90000

            assertThat(results).hasSize(2); // GH-90000
            long successes = results.stream().filter(r -> !r.isFailed()).count(); // GH-90000
            long failures  = results.stream().filter(AgentResult::isFailed).count(); // GH-90000
            assertThat(successes).isEqualTo(1); // GH-90000
            assertThat(failures).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("null agents list → NullPointerException [GH-90000]")
        void nullAgentsThrowsNPE() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> executor.executeAll(null, context, "input"))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null context → NullPointerException [GH-90000]")
        void nullContextThrowsNPE() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> executor.executeAll(List.of(successAgent("a", "o", 1.0)), null, "input"))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null input → NullPointerException [GH-90000]")
        void nullInputThrowsNPE() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> executor.executeAll(List.of(successAgent("a", "o", 1.0)), context, null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — FIRST_WINS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate FIRST_WINS [GH-90000]")
    class FirstWinsAggregation {

        @Test
        @DisplayName("returns first successful result regardless of confidence [GH-90000]")
        void returnsFirstSuccess() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("low-conf", "first-output", 0.3), // GH-90000
                    successAgent("high-conf", "second-output", 0.9)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("first-output [GH-90000]");
        }

        @Test
        @DisplayName("first agent fails → falls through to second success [GH-90000]")
        void skipsFirstFailure() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    throwingAgent("fails-first", new RuntimeException("fail [GH-90000]")),
                    successAgent("ok-second", "good-output", 0.8)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("good-output [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — HIGHEST_CONFIDENCE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate HIGHEST_CONFIDENCE [GH-90000]")
    class HighestConfidenceAggregation {

        @Test
        @DisplayName("returns result with highest confidence score [GH-90000]")
        void picksHighestConfidence() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("low",  "low-output",  0.3), // GH-90000
                    successAgent("high", "high-output", 0.95), // GH-90000
                    successAgent("mid",  "mid-output",  0.6)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("high-output [GH-90000]");
            assertThat(result.getConfidence()).isEqualTo(0.95); // GH-90000
        }

        @Test
        @DisplayName("ties in confidence → any of the tied results acceptable [GH-90000]")
        void tieHandledWithoutCrash() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("a1", "output-a", 0.9), // GH-90000
                    successAgent("a2", "output-b", 0.9)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(List.of("output-a", "output-b")).contains(result.getOutput()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeAndAggregate — MAJORITY_VOTE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("aggregate MAJORITY_VOTE [GH-90000]")
    class MajorityVoteAggregation {

        @Test
        @DisplayName("returns output that appears most frequently [GH-90000]")
        void picksMajorityOutput() { // GH-90000
            // Two agents agree on "consensus-output", one outlier
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("a1", "consensus-output", 0.8), // GH-90000
                    successAgent("a2", "consensus-output", 0.7), // GH-90000
                    successAgent("a3", "outlier-output",   0.95)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.MAJORITY_VOTE));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).isEqualTo("consensus-output [GH-90000]");
        }

        @Test
        @DisplayName("tie in vote count → some non-failed result is returned [GH-90000]")
        void tieInVoteCountReturnsNonFailedResult() { // GH-90000
            // Two groups of one — tie in group size; winner is implementation-defined
            // (HashMap iteration order), so we assert only that a valid result is returned. // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    successAgent("voter-a", "option-A", 0.9), // GH-90000
                    successAgent("voter-b", "option-B", 0.2)); // GH-90000

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.MAJORITY_VOTE));

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(List.of("option-A", "option-B")).contains(result.getOutput()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All agents fail
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("all agents fail — aggregate returns failure [GH-90000]")
    class AllAgentsFail {

        @Test
        @DisplayName("FIRST_WINS with all failures → aggregate failure result [GH-90000]")
        void allFailFirstWins() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    throwingAgent("fail-1", new RuntimeException("err1 [GH-90000]")),
                    throwingAgent("fail-2", new RuntimeException("err2 [GH-90000]")));

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.FIRST_WINS));

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("HIGHEST_CONFIDENCE with all failures → aggregate failure result [GH-90000]")
        void allFailHighestConfidence() { // GH-90000
            List<TypedAgent<String, String>> agents = List.of( // GH-90000
                    throwingAgent("fail-x", new RuntimeException("nope [GH-90000]")));

            AgentResult<String> result = runPromise(() -> // GH-90000
                    executor.executeAndAggregate(agents, context, "input", // GH-90000
                            ParallelAgentExecutor.AggregationStrategy.HIGHEST_CONFIDENCE));

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — minimal stub agents
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a stub TypedAgent that always returns a SUCCESS result with the given output and confidence.
     */
    private static TypedAgent<String, String> successAgent(String agentId, String output, double confidence) { // GH-90000
        AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name(agentId) // GH-90000
                .type(AgentType.DETERMINISTIC) // GH-90000
                .build(); // GH-90000

        return new TypedAgent<>() { // GH-90000
            @Override
            public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

            @Override
            public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) { // GH-90000
                return Promise.complete(); // GH-90000
            }

            @Override
            public @NotNull Promise<Void> shutdown() { return Promise.complete(); } // GH-90000

            @Override
            public @NotNull Promise<HealthStatus> healthCheck() { // GH-90000
                return Promise.of(HealthStatus.healthy("Agent is healthy [GH-90000]"));
            }

            @Override
            public @NotNull Promise<AgentResult<String>> process( // GH-90000
                    @NotNull AgentContext ctx, @NotNull String input) {
                return Promise.of(AgentResult.successWithConfidence(output, confidence, agentId, Duration.ZERO, "stub")); // GH-90000
            }
        };
    }

    /**
     * Creates a stub TypedAgent whose {@code process()} call throws the given exception, // GH-90000
     * simulating a failed agent computation.
     */
    private static TypedAgent<String, String> throwingAgent(String agentId, RuntimeException ex) { // GH-90000
        AgentDescriptor desc = AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name(agentId) // GH-90000
                .type(AgentType.DETERMINISTIC) // GH-90000
                .build(); // GH-90000

        return new TypedAgent<>() { // GH-90000
            @Override
            public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

            @Override
            public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) { // GH-90000
                return Promise.complete(); // GH-90000
            }

            @Override
            public @NotNull Promise<Void> shutdown() { return Promise.complete(); } // GH-90000

            @Override
            public @NotNull Promise<HealthStatus> healthCheck() { // GH-90000
                return Promise.of(HealthStatus.unhealthy("Agent is unhealthy", (Throwable) null)); // GH-90000
            }

            @Override
            public @NotNull Promise<AgentResult<String>> process( // GH-90000
                    @NotNull AgentContext ctx, @NotNull String input) {
                return Promise.ofException(ex); // GH-90000
            }
        };
    }
}
