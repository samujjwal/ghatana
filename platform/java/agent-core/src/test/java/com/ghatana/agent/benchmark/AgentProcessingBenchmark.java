/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;

/**
 * JMH performance benchmarks for agent processing throughput.
 *
 * <p>Production targets:
 * <ul>
 *   <li>DETERMINISTIC: >100K events/sec (~10µs per process call)</li> // GH-90000
 *   <li>PROBABILISTIC: >10K events/sec (~100µs per process call)</li> // GH-90000
 *   <li>ADAPTIVE: >50K events/sec (~20µs per process call)</li> // GH-90000
 * </ul>
 *
 * Run via {@link AgentBenchmarkRunner} JUnit test or standalone:
 * {@code ./gradlew :platform:java:agent-framework:test --tests "*AgentBenchmarkRunner*"}
 */
@Fork(1) // GH-90000
@Warmup(iterations = 2, time = 1) // GH-90000
@Measurement(iterations = 3, time = 1) // GH-90000
@BenchmarkMode(Mode.Throughput) // GH-90000
@OutputTimeUnit(TimeUnit.SECONDS) // GH-90000
@State(Scope.Benchmark) // GH-90000
public class AgentProcessingBenchmark {

    private DeterministicAgent deterministicAgent;
    private ProbabilisticAgent probabilisticAgent;
    private AdaptiveAgent adaptiveAgent;
    private AgentContext ctx;

    @Setup(Level.Trial) // GH-90000
    public void setup() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("bench-turn")
                .agentId("bench-agent")
                .tenantId("bench-tenant")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000

        // Deterministic agent with rule-based evaluation
        deterministicAgent = new DeterministicAgent("bench-det");
        DeterministicAgentConfig detConfig = DeterministicAgentConfig.builder() // GH-90000
                .agentId("bench-det")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .subtype(DeterministicSubtype.RULE_BASED) // GH-90000
                .rule(Rule.builder() // GH-90000
                        .id("high-temp").name("High Temp")
                        .condition(RuleCondition.gt("temperature", 100)) // GH-90000
                        .action("alert", "high") // GH-90000
                        .build()) // GH-90000
                .rule(Rule.builder() // GH-90000
                        .id("low-temp").name("Low Temp")
                        .condition(RuleCondition.lt("temperature", 0)) // GH-90000
                        .action("alert", "low") // GH-90000
                        .build()) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> deterministicAgent.initialize(detConfig)); // GH-90000

        // Probabilistic agent with confidence threshold
        probabilisticAgent = new ProbabilisticAgent("bench-prob");
        ProbabilisticAgentConfig probConfig = ProbabilisticAgentConfig.builder() // GH-90000
                .agentId("bench-prob")
                .type(AgentType.PROBABILISTIC) // GH-90000
                .confidenceThreshold(0.5) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> probabilisticAgent.initialize(probConfig)); // GH-90000

        // Adaptive agent with epsilon-greedy
        adaptiveAgent = new AdaptiveAgent("bench-adaptive");
        AdaptiveAgentConfig adaptConfig = AdaptiveAgentConfig.builder() // GH-90000
                .agentId("bench-adaptive")
                .type(AgentType.ADAPTIVE) // GH-90000
                .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY) // GH-90000
                .tunedParameter("threshold")
                .parameterMin(0.0) // GH-90000
                .parameterMax(1.0) // GH-90000
                .armCount(5) // GH-90000
                .explorationRate(0.1) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> adaptiveAgent.initialize(adaptConfig)); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Benchmarks
    // ═══════════════════════════════════════════════════════════════════════════

    @Benchmark
    public void deterministicProcessing(Blackhole bh) { // GH-90000
        AgentResult<?> result = runOnEventloop(() -> // GH-90000
                deterministicAgent.process(ctx, Map.of("temperature", 150))); // GH-90000
        bh.consume(result); // GH-90000
    }

    @Benchmark
    public void probabilisticProcessing(Blackhole bh) { // GH-90000
        AgentResult<?> result = runOnEventloop(() -> // GH-90000
                probabilisticAgent.process(ctx, Map.of("prompt", "classify this event"))); // GH-90000
        bh.consume(result); // GH-90000
    }

    @Benchmark
    public void adaptiveArmSelection(Blackhole bh) { // GH-90000
        AgentResult<?> result = runOnEventloop(() -> // GH-90000
                adaptiveAgent.process(ctx, Map.of())); // GH-90000
        bh.consume(result); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }
}
