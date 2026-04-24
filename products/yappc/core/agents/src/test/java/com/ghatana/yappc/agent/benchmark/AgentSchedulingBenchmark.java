package com.ghatana.yappc.agent.benchmark;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.yappc.agent.ParallelAgentExecutor;
import com.ghatana.yappc.agent.ParallelAgentExecutor.AggregationStrategy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmarks for YAPPC agent scheduling and parallel execution.
 *
 * <p>Validates latency targets for the critical scheduling paths:
 * <ul>
 *   <li>Single-agent dispatch: &lt;5 ms warm-path</li>
 *   <li>Parallel fan-out (10 agents): &lt;50 ms aggregate</li>
 *   <li>FIRST_WINS aggregation: &lt;10 ms</li>
 *   <li>MAJORITY_VOTE aggregation (5 agents): &lt;20 ms</li>
 *   <li>Large concurrent batch (50 tasks): throughput &gt;1000 tasks/sec</li>
 * </ul>
 *
 * <p>Uses JUnit 5 (no JMH) with timed iterations so these run in the standard test tier.
 * For micro-benchmark precision, promote to a dedicated JMH source set.
 *
 * @doc.type    class
 * @doc.purpose Agent scheduling and parallel execution performance benchmarks
 * @doc.layer   product
 * @doc.pattern Benchmark
 */
@DisplayName("Agent Scheduling Benchmarks")
@Tag("benchmark")
class AgentSchedulingBenchmark {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURED_ITERATIONS = 200;

    private Eventloop eventloop;
    private Thread eventloopThread;

    @BeforeEach
    void setUp() throws InterruptedException {
        eventloop = Eventloop.builder().build();
        CountDownLatch started = new CountDownLatch(1);
        eventloopThread = new Thread(() -> {
            started.countDown();
            eventloop.run();
        }, "benchmark-eventloop");
        eventloopThread.setDaemon(true);
        eventloopThread.start();
        started.await(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        eventloop.execute(eventloop::breakEventloop);
    }

    // -------------------------------------------------------------------------
    // Benchmark 1: Single-agent dispatch latency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("single-agent dispatch: warm-path p99 < 5 ms")
    void singleAgentDispatchP99Under5ms() throws InterruptedException {
        TypedAgent<String, String> fastAgent = instantAgent("fast-dispatch");
        AgentContext ctx = syntheticContext("bench-tenant");

        warmUp(() -> dispatchSync(fastAgent, ctx, "input"), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            dispatchSync(fastAgent, ctx, "input-" + i);
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p99Ns = percentile(latenciesNs, 99);
        long p99Ms = TimeUnit.NANOSECONDS.toMillis(p99Ns);

        assertThat(p99Ms)
                .as("single-agent dispatch p99 must be < 5 ms, was %d ms".formatted(p99Ms))
                .isLessThan(5);
    }

    // -------------------------------------------------------------------------
    // Benchmark 2: Parallel fan-out latency (10 agents)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parallel fan-out (10 agents): aggregate p95 < 50 ms")
    void parallelFanOut10AgentsP95Under50ms() throws InterruptedException {
        int agentCount = 10;
        List<TypedAgent<String, String>> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            agents.add(instantAgent("fan-out-" + i));
        }
        ParallelAgentExecutor executor = new ParallelAgentExecutor();
        AgentContext ctx = syntheticContext("bench-tenant");

        warmUp(() -> parallelDispatchSync(executor, agents, ctx, "input", AggregationStrategy.FIRST_WINS), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            parallelDispatchSync(executor, agents, ctx, "input-" + i, AggregationStrategy.FIRST_WINS);
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p95Ns = percentile(latenciesNs, 95);
        long p95Ms = TimeUnit.NANOSECONDS.toMillis(p95Ns);

        assertThat(p95Ms)
                .as("parallel fan-out (10) p95 must be < 50 ms, was %d ms".formatted(p95Ms))
                .isLessThan(50);
    }

    // -------------------------------------------------------------------------
    // Benchmark 3: MAJORITY_VOTE aggregation (5 agents)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MAJORITY_VOTE (5 agents): p95 < 20 ms")
    void majorityVote5AgentsP95Under20ms() throws InterruptedException {
        int agentCount = 5;
        List<TypedAgent<String, String>> agents = new ArrayList<>();
        for (int i = 0; i < agentCount; i++) {
            // All return same value so majority is unambiguous
            agents.add(instantAgent("vote-agent"));
        }
        ParallelAgentExecutor executor = new ParallelAgentExecutor();
        AgentContext ctx = syntheticContext("bench-tenant");

        warmUp(() -> parallelDispatchSync(executor, agents, ctx, "vote-input", AggregationStrategy.MAJORITY_VOTE), WARMUP_ITERATIONS);

        long[] latenciesNs = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            parallelDispatchSync(executor, agents, ctx, "vote-" + i, AggregationStrategy.MAJORITY_VOTE);
            latenciesNs[i] = System.nanoTime() - start;
        }

        long p95Ns = percentile(latenciesNs, 95);
        long p95Ms = TimeUnit.NANOSECONDS.toMillis(p95Ns);

        assertThat(p95Ms)
                .as("MAJORITY_VOTE (5 agents) p95 must be < 20 ms, was %d ms".formatted(p95Ms))
                .isLessThan(20);
    }

    // -------------------------------------------------------------------------
    // Benchmark 4: Large concurrent batch throughput
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("50-task concurrent batch: throughput > 1000 tasks/sec")
    void largeConurrentBatchThroughputOver1000TasksPerSec() throws InterruptedException {
        TypedAgent<String, String> fastAgent = instantAgent("throughput-agent");
        AgentContext ctx = syntheticContext("bench-tenant");
        int batchSize = 50;

        // Warmup
        for (int i = 0; i < 20; i++) {
            dispatchSync(fastAgent, ctx, "warmup");
        }

        long startNs = System.nanoTime();
        for (int i = 0; i < batchSize; i++) {
            dispatchSync(fastAgent, ctx, "batch-" + i);
        }
        long elapsedNs = System.nanoTime() - startNs;

        double elapsedSec = elapsedNs / 1_000_000_000.0;
        double throughput = batchSize / elapsedSec;

        assertThat(throughput)
                .as("batch throughput must exceed 1000 tasks/sec, was %.1f".formatted(throughput))
                .isGreaterThan(1000.0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a zero-latency in-process agent returning the input unchanged. */
    private TypedAgent<String, String> instantAgent(String name) {
        return new TypedAgent<>() {
            @Override
            public Promise<AgentResult<String>> process(AgentContext ctx, String input) {
                return Promise.of(AgentResult.success(input));
            }

            @Override
            public String getAgentId() { return name; }

            @Override
            public Class<String> getInputType() { return String.class; }

            @Override
            public Class<String> getOutputType() { return String.class; }
        };
    }

    private AgentContext syntheticContext(String tenantId) {
        return AgentContext.builder()
                .tenantId(tenantId)
                .correlationId("bench-corr-id")
                .build();
    }

    private String dispatchSync(TypedAgent<String, String> agent, AgentContext ctx, String input)
            throws InterruptedException {
        AtomicLong result = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(1);
        eventloop.execute(() ->
                agent.process(ctx, input)
                        .whenComplete((r, e) -> {
                            result.set(0);
                            latch.countDown();
                        }));
        latch.await(2, TimeUnit.SECONDS);
        return input;
    }

    private void parallelDispatchSync(ParallelAgentExecutor executor,
                                      List<TypedAgent<String, String>> agents,
                                      AgentContext ctx,
                                      String input,
                                      AggregationStrategy strategy) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        eventloop.execute(() ->
                executor.executeParallel(agents, ctx, input, strategy)
                        .whenComplete((r, e) -> latch.countDown()));
        latch.await(5, TimeUnit.SECONDS);
    }

    private void warmUp(RunnableWithInterrupt task, int iterations) throws InterruptedException {
        for (int i = 0; i < iterations; i++) {
            task.run();
        }
    }

    private static long percentile(long[] values, int pct) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int index = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    @FunctionalInterface
    interface RunnableWithInterrupt {
        void run() throws InterruptedException;
    }
}
