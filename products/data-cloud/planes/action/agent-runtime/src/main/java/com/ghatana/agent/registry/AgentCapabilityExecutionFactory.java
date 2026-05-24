/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.runtime.GaaAgentExecutor;
import com.ghatana.agent.spi.AgentRegistry;
import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Maps agent types to operator trees in the pipeline execution engine.
 *
 * <p>This factory bridges the agent framework with the operator / pipeline layer:
 * <pre>
 *   AgentType.DETERMINISTIC  → FilterOperator / PatternOperator / FSMOperator
 *   AgentType.PROBABILISTIC  → MLInferenceOperator
 *   AgentType.HYBRID         → HybridOperator (chains det + prob)
 *   AgentType.ADAPTIVE       → AdaptiveOperator (wraps with feedback loop)
 *   AgentType.COMPOSITE      → CompositeOperator (fan-out → aggregator)
 *   AgentType.REACTIVE       → ReactiveOperator (ReflexEngine integration)
 * </pre>
 *
 * <p>The factory is extensible: custom mappings can be registered for new agent types
 * or subtypes.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Factory for creating operators from agent definitions
 * @doc.layer platform
 * @doc.pattern Factory
 */
public class AgentCapabilityExecutionFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityExecutionFactory.class);

    private final AgentRegistry registry;
    private final AgentExecutionStrategyRegistry strategyRegistry;
    private final Map<String, CapabilityExecutionMapping> customMappings = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Operator Abstraction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lightweight operator wrapper around a TypedAgent.
     * Bridges the agent framework into operator chains.
     */
    @Value
    @Builder
    public static class AgentCapabilityStep {
        /** Operator name (typically agent-id). */
        @NotNull String name;
        /** The wrapped agent type. */
        @NotNull AgentType agentType;
        /** Human-readable operator description. */
        @Nullable String description;
        /** The wrapped agent. */
        @NotNull TypedAgent<Map<String, Object>, Map<String, Object>> agent;
        /** Pre-processing transformation (before agent). */
        @Nullable Function<Map<String, Object>, Map<String, Object>> preProcessor;
        /** Post-processing transformation (after agent). */
        @Nullable Function<AgentResult<Map<String, Object>>,
                AgentResult<Map<String, Object>>> postProcessor;

        /**
         * Executes this operator.
         */
        @NotNull
        public Promise<AgentResult<Map<String, Object>>> execute(
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            Map<String, Object> processed = preProcessor != null
                    ? preProcessor.apply(input) : input;
            return new GaaAgentExecutor().execute(agent, ctx, processed)
                    .map(result -> postProcessor != null
                            ? postProcessor.apply(result) : result);
        }
    }

    /**
     * An operator tree — may contain a single operator, a chain, or a DAG.
     */
    @Value
    @Builder
    public static class CapabilityExecutionTree {
        @NotNull String name;
        @NotNull List<AgentCapabilityStep> operators;
        @Nullable String description;

        /**
         * Executes the operator tree sequentially.
         * Each operator receives the output of the previous as input.
         */
        @NotNull
        public Promise<AgentResult<Map<String, Object>>> execute(
                @NotNull AgentContext ctx, @NotNull Map<String, Object> initialInput) {
            if (operators.isEmpty()) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                        .output(initialInput)
                        .confidence(1.0)
                        .status(com.ghatana.agent.AgentResultStatus.SKIPPED)
                        .explanation("Empty operator tree")
                        .build());
            }

            Promise<AgentResult<Map<String, Object>>> chain =
                    operators.get(0).execute(ctx, initialInput);

            for (int i = 1; i < operators.size(); i++) {
                final AgentCapabilityStep next = operators.get(i);
                chain = chain.then(result -> {
                    if (result.isFailed()) return Promise.of(result);
                    Map<String, Object> nextInput = result.getOutput() != null
                            ? result.getOutput() : Map.of();
                    return next.execute(ctx, nextInput);
                });
            }

            return chain;
        }
    }

    /**
     * Functional interface for operator mapping.
     */
    @FunctionalInterface
    public interface CapabilityExecutionMapping {
        CapabilityExecutionTree createTree(TypedAgent<?, ?> agent, AgentConfig config);
    }

    /** Type-specific strategy for operator-tree materialization. */
    public interface AgentExecutionStrategy {
        CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config);
    }

    /** Registry for explicit mappings from canonical AgentType to strategy. */
    public static class AgentExecutionStrategyRegistry {
        private final Map<AgentType, AgentExecutionStrategy> strategies = new EnumMap<>(AgentType.class);

        public AgentExecutionStrategyRegistry register(AgentType type, AgentExecutionStrategy strategy) {
            strategies.put(type, strategy);
            return this;
        }

        public AgentExecutionStrategy get(AgentType type) {
            AgentExecutionStrategy strategy = strategies.get(type);
            if (strategy == null) {
                throw new IllegalStateException("No execution strategy for type: " + type);
            }
            return strategy;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public AgentCapabilityExecutionFactory(@NotNull AgentRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        this.strategyRegistry = registerDefaultStrategies();
    }

    private AgentExecutionStrategyRegistry registerDefaultStrategies() {
        return new AgentExecutionStrategyRegistry()
                .register(AgentType.DETERMINISTIC, new DeterministicExecutionStrategy())
                .register(AgentType.PROBABILISTIC, new ProbabilisticExecutionStrategy())
                .register(AgentType.STREAM_PROCESSOR, new StreamProcessorExecutionStrategy())
                .register(AgentType.PLANNING, new PlanningExecutionStrategy())
                .register(AgentType.HYBRID, new HybridExecutionStrategy())
                .register(AgentType.ADAPTIVE, new AdaptiveExecutionStrategy())
                .register(AgentType.COMPOSITE, new CompositeExecutionStrategy())
                .register(AgentType.REACTIVE, new ReactiveExecutionStrategy())
                .register(AgentType.CUSTOM, new CustomExecutionStrategy());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an operator tree for the given agent ID.
     *
     * @param agentId the registered agent ID
     * @return a Promise of the operator tree
     */
    @NotNull
    public Promise<CapabilityExecutionTree> createCapabilityExecutionTree(@NotNull String agentId) {
        return registry.<Map<String, Object>, Map<String, Object>>resolve(agentId)
                .map(optAgent -> optAgent.orElseThrow(() ->
                    new IllegalStateException("Agent not found: " + agentId)))
                .map(agent -> {
                    AgentType type = agent.descriptor().getType();

                    // Check custom mapping first
                    CapabilityExecutionMapping custom = customMappings.get(agentId);
                    if (custom != null) {
                        return custom.createTree(agent, null);
                    }

                    return strategyRegistry.get(type).createCapabilityExecutionTree(agent, null);
                });
    }

    /**
     * Creates an operator tree for the given agent and configuration.
     *
     * @param agent  the agent
     * @param config the configuration
     * @return the operator tree
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public CapabilityExecutionTree createCapabilityExecutionTree(
            @NotNull TypedAgent<?, ?> agent, @NotNull AgentConfig config) {
        AgentType type = agent.descriptor().getType();
        CapabilityExecutionMapping mapping = customMappings.get(agent.descriptor().getAgentId());
        if (mapping != null) {
            return mapping.createTree(agent, config);
        }
        return strategyRegistry.get(type).createCapabilityExecutionTree(agent, config);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom Mappings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a custom operator mapping for a specific agent ID.
     */
    public void registerMapping(@NotNull String agentId, @NotNull CapabilityExecutionMapping mapping) {
        customMappings.put(agentId, mapping);
        log.info("Registered custom operator mapping for agent: {}", agentId);
    }

    /**
     * Registers a custom operator mapping for an agent type (overrides default).
     */
    public void registerTypeMapping(@NotNull AgentType type, @NotNull CapabilityExecutionMapping mapping) {
        strategyRegistry.register(type, (agent, config) -> mapping.createTree(agent, config));
        log.info("Registered custom operator mapping for type: {}", type);
    }

    @SuppressWarnings("unchecked")
    private static AgentCapabilityStep baseOperator(TypedAgent<?, ?> agent, String strategyTag) {
        TypedAgent<Map<String, Object>, Map<String, Object>> typed =
                (TypedAgent<Map<String, Object>, Map<String, Object>>) agent;
        return AgentCapabilityStep.builder()
                .name(agent.descriptor().getAgentId())
                .agentType(agent.descriptor().getType())
                .description(agent.descriptor().getDescription())
                .agent(typed)
                .preProcessor(input -> {
                    Map<String, Object> copy = new LinkedHashMap<>(input);
                    copy.putIfAbsent("__agentExecutionStrategy", strategyTag);
                    return Map.copyOf(copy);
                })
                .postProcessor(result -> result.toBuilder()
                        .diagnostics(merge(result.getDiagnostics(), "executionStrategy", strategyTag))
                        .build())
                .build();
    }

    private static Map<String, Object> merge(Map<String, Object> input, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(input);
        copy.put(key, value);
        return Map.copyOf(copy);
    }

    private static CapabilityExecutionTree tree(TypedAgent<?, ?> agent, String strategyTag, String description) {
        return CapabilityExecutionTree.builder()
                .name(agent.descriptor().getAgentId() + "-" + strategyTag + "-tree")
                .operators(List.of(baseOperator(agent, strategyTag)))
                .description(description)
                .build();
    }

    public static class DeterministicExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "deterministic", "Deterministic rule/policy operator tree");
        }
    }

    public static class ProbabilisticExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "probabilistic", "Probabilistic inference operator tree with confidence output");
        }
    }

    public static class HybridExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "hybrid-routing", "Hybrid routing tree for deterministic and probabilistic paths");
        }
    }

    public static class AdaptiveExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "adaptive-feedback", "Adaptive execution tree with feedback diagnostics");
        }
    }

    public static class CompositeExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "composite-fanout-fanin", "Composite fan-out/fan-in aggregation tree");
        }
    }

    public static class ReactiveExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "reactive-reflex", "Reactive stateless trigger/action tree");
        }
    }

    public static class StreamProcessorExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "stream-checkpoint", "Checkpoint-aware stream processor tree");
        }
    }

    public static class PlanningExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "planning-workflow", "Planning and workflow execution tree");
        }
    }

    public static class CustomExecutionStrategy implements AgentExecutionStrategy {
        public CapabilityExecutionTree createCapabilityExecutionTree(TypedAgent<?, ?> agent, AgentConfig config) {
            return tree(agent, "custom-registered", "Custom registered execution tree");
        }
    }
}
