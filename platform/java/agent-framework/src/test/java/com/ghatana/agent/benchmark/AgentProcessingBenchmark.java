/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.benchmark;

import com.ghatana.agent.*;
import com.ghatana.agent.adaptive.AdaptiveAgent;
import com.ghatana.agent.adaptive.AdaptiveAgentConfig;
import com.ghatana.agent.deterministic.DeterministicAgent;
import com.ghatana.agent.deterministic.DeterministicAgentConfig;
import com.ghatana.agent.deterministic.DeterministicSubtype;
import com.ghatana.agent.deterministic.Rule;
import com.ghatana.agent.deterministic.RuleCondition;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.probabilistic.ProbabilisticAgent;
import com.ghatana.agent.probabilistic.ProbabilisticAgentConfig;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;

/**
 * JMH performance benchmarks for agent processing throughput.
 *
 * <p>Production targets:
 * <ul>
 *   <li>DETERMINISTIC: >100K events/sec (~10µs per process call)</li>
 *   <li>PROBABILISTIC: >10K events/sec (~100µs per process call)</li>
 *   <li>ADAPTIVE: >50K events/sec (~20µs per process call)</li>
 * </ul>
 *
 * Run via {@link AgentBenchmarkRunner} JUnit test or standalone:
 * {@code ./gradlew :platform:java:agent-framework:test --tests "*AgentBenchmarkRunner*"}
 */
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class AgentProcessingBenchmark {

    private DeterministicAgent deterministicAgent;
    private ProbabilisticAgent probabilisticAgent;
    private AdaptiveAgent adaptiveAgent;
    private AgentContext ctx;

    @Setup(Level.Trial)
    public void setup() {
        ctx = AgentContext.builder()
                .turnId("bench-turn")
                .agentId("bench-agent")
                .tenantId("bench-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();

        // Deterministic agent with rule-based evaluation
        deterministicAgent = new DeterministicAgent("bench-det");
        DeterministicAgentConfig detConfig = DeterministicAgentConfig.builder()
                .agentId("bench-det")
                .type(AgentType.DETERMINISTIC)
                .subtype(DeterministicSubtype.RULE_BASED)
                .rule(Rule.builder()
                        .id("high-temp").name("High Temp")
                        .condition(RuleCondition.gt("temperature", 100))
                        .action("alert", "high")
                        .build())
                .rule(Rule.builder()
                        .id("low-temp").name("Low Temp")
                        .condition(RuleCondition.lt("temperature", 0))
                        .action("alert", "low")
                        .build())
                .build();
        runOnEventloop(() -> deterministicAgent.initialize(detConfig));

        // Probabilistic agent with confidence threshold
        probabilisticAgent = new ProbabilisticAgent("bench-prob");
        ProbabilisticAgentConfig probConfig = ProbabilisticAgentConfig.builder()
                .agentId("bench-prob")
                .type(AgentType.PROBABILISTIC)
                .confidenceThreshold(0.5)
                .build();
        runOnEventloop(() -> probabilisticAgent.initialize(probConfig));

        // Adaptive agent with epsilon-greedy
        adaptiveAgent = new AdaptiveAgent("bench-adaptive");
        AdaptiveAgentConfig adaptConfig = AdaptiveAgentConfig.builder()
                .agentId("bench-adaptive")
                .type(AgentType.ADAPTIVE)
                .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY)
                .tunedParameter("threshold")
                .parameterMin(0.0)
                .parameterMax(1.0)
                .armCount(5)
                .explorationRate(0.1)
                .build();
        runOnEventloop(() -> adaptiveAgent.initialize(adaptConfig));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Benchmarks
    // ═══════════════════════════════════════════════════════════════════════════

    @Benchmark
    public void deterministicProcessing(Blackhole bh) {
        AgentResult<?> result = runOnEventloop(() -> 
                deterministicAgent.process(ctx, Map.of("temperature", 150)));
        bh.consume(result);
    }

    @Benchmark
    public void probabilisticProcessing(Blackhole bh) {
        AgentResult<?> result = runOnEventloop(() ->
                probabilisticAgent.process(ctx, Map.of("prompt", "classify this event")));
        bh.consume(result);
    }

    @Benchmark
    public void adaptiveArmSelection(Blackhole bh) {
        AgentResult<?> result = runOnEventloop(() ->
                adaptiveAgent.process(ctx, Map.of()));
        bh.consume(result);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(result::set)
                .whenException(err::set));
        eventloop.run();
        if (err.get() != null) throw new RuntimeException(err.get());
        return result.get();
    }
}
