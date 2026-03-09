/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
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
public class AgentOperatorFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentOperatorFactory.class);

    private final AgentFrameworkRegistry registry;
    private final Map<AgentType, OperatorMapping> defaultMappings = new EnumMap<>(AgentType.class);
    private final Map<String, OperatorMapping> customMappings = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Operator Abstraction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lightweight operator wrapper around a TypedAgent.
     * Bridges the agent framework into operator chains.
     */
    @Value
    @Builder
    public static class AgentOperator {
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
            return agent.process(ctx, processed)
                    .map(result -> postProcessor != null
                            ? postProcessor.apply(result) : result);
        }
    }

    /**
     * An operator tree — may contain a single operator, a chain, or a DAG.
     */
    @Value
    @Builder
    public static class OperatorTree {
        @NotNull String name;
        @NotNull List<AgentOperator> operators;
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
                final AgentOperator next = operators.get(i);
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
    public interface OperatorMapping {
        OperatorTree createTree(TypedAgent<?, ?> agent, AgentConfig config);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public AgentOperatorFactory(@NotNull AgentFrameworkRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        registerDefaultMappings();
    }

    @SuppressWarnings("unchecked")
    private void registerDefaultMappings() {
        // Each default mapping wraps the agent in a single-operator tree
        for (AgentType type : AgentType.values()) {
            defaultMappings.put(type, (agent, config) -> {
                TypedAgent<Map<String, Object>, Map<String, Object>> typed =
                        (TypedAgent<Map<String, Object>, Map<String, Object>>) agent;
                AgentOperator op = AgentOperator.builder()
                        .name(agent.descriptor().getAgentId())
                        .agentType(agent.descriptor().getType())
                        .description(agent.descriptor().getDescription())
                        .agent(typed)
                        .build();
                return OperatorTree.builder()
                        .name(agent.descriptor().getAgentId() + "-tree")
                        .operators(List.of(op))
                        .description("Operator tree for " + agent.descriptor().getAgentId())
                        .build();
            });
        }
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
    public Promise<OperatorTree> createOperatorTree(@NotNull String agentId) {
        return registry.<Map<String, Object>, Map<String, Object>>resolve(agentId)
                .map(agent -> {
                    AgentType type = agent.descriptor().getType();

                    // Check custom mapping first
                    OperatorMapping custom = customMappings.get(agentId);
                    if (custom != null) {
                        return custom.createTree(agent, null);
                    }

                    // Fall back to type-based default
                    OperatorMapping mapping = defaultMappings.get(type);
                    if (mapping == null) {
                        throw new IllegalStateException("No operator mapping for type: " + type);
                    }
                    return mapping.createTree(agent, null);
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
    public OperatorTree createOperatorTree(
            @NotNull TypedAgent<?, ?> agent, @NotNull AgentConfig config) {
        AgentType type = agent.descriptor().getType();
        OperatorMapping mapping = customMappings.getOrDefault(
                agent.descriptor().getAgentId(),
                defaultMappings.get(type));
        if (mapping == null) {
            throw new IllegalStateException("No operator mapping for type: " + type);
        }
        return mapping.createTree(agent, config);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom Mappings
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a custom operator mapping for a specific agent ID.
     */
    public void registerMapping(@NotNull String agentId, @NotNull OperatorMapping mapping) {
        customMappings.put(agentId, mapping);
        log.info("Registered custom operator mapping for agent: {}", agentId);
    }

    /**
     * Registers a custom operator mapping for an agent type (overrides default).
     */
    public void registerTypeMapping(@NotNull AgentType type, @NotNull OperatorMapping mapping) {
        defaultMappings.put(type, mapping);
        log.info("Registered custom operator mapping for type: {}", type);
    }
}
