/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified taxonomy of agent computational types in the Ghatana platform.
 *
 * <p>Each type describes the <em>dominant processing model</em> of an agent.
 * For finer-grained classification, agents declare a {@code subtype} via their
 * {@link AgentDescriptor} (e.g., {@code DETERMINISTIC} + subtype {@code "RULE_ENGINE"}).
 *
 * <h2>The 9 Built-in Types</h2>
 * <table>
 *   <tr><th>Type</th><th>Use When</th><th>Latency</th><th>Determinism</th><th>Examples</th></tr>
 *   <tr><td>DETERMINISTIC</td><td>Rules, thresholds, FSM, policy, pattern</td><td>sub-ms</td><td>100%</td><td>Event routing, validation, governance checks</td></tr>
 *   <tr><td>PROBABILISTIC</td><td>ML model, Bayesian, LLM, classifier</td><td>ms–s</td><td>0%</td><td>Anomaly detection, NLP, scoring</td></tr>
 *   <tr><td>STREAM_PROCESSOR</td><td>Stateful event streams, CEP, windowed agg</td><td>sub-ms</td><td>varies</td><td>AEP operators, ingestion, transformation</td></tr>
 *   <tr><td>PLANNING</td><td>Goal-directed multi-step, HTN, ReAct, workflow</td><td>s–min</td><td>none</td><td>Orchestrators, task decomposition</td></tr>
 *   <tr><td>HYBRID</td><td>Fast-path + probabilistic fallback</td><td>sub-ms–ms</td><td>partial</td><td>Expert systems with LLM escalation</td></tr>
 *   <tr><td>ADAPTIVE</td><td>Bandits, RL, self-tuning, A/B</td><td>ms</td><td>0%</td><td>Parameter tuning, online learning</td></tr>
 *   <tr><td>COMPOSITE</td><td>Ensemble, voting, fan-out/fan-in</td><td>depends</td><td>varies</td><td>Multi-model consensus, parallel execution</td></tr>
 *   <tr><td>REACTIVE</td><td>Stateless event trigger, alerts</td><td>sub-ms</td><td>100%</td><td>Circuit-breakers, simple alerts</td></tr>
 *   <tr><td>CUSTOM</td><td>Domain-specific types via registry</td><td>–</td><td>–</td><td>Healthcare-specific, finance-specific</td></tr>
 * </table>
 *
 * <h2>Type Boundaries (Disambiguation)</h2>
 * <ul>
 *   <li><b>DETERMINISTIC vs REACTIVE</b>: DETERMINISTIC has structured config logic (rules, FSM,
 *       policy chains); REACTIVE is a simple stateless trigger→action reflex with no complex reasoning.</li>
 *   <li><b>STREAM_PROCESSOR vs REACTIVE</b>: STREAM_PROCESSOR is stateful (windows, checkpoints,
 *       backpressure); REACTIVE is stateless and immediate.</li>
 *   <li><b>PROBABILISTIC vs HYBRID</b>: PROBABILISTIC uses only stochastic reasoning; HYBRID
 *       explicitly routes between deterministic and probabilistic sub-agents.</li>
 *   <li><b>PLANNING vs COMPOSITE</b>: PLANNING creates an explicit plan before executing steps;
 *       COMPOSITE fans out to parallel sub-agents and aggregates results.</li>
 *   <li><b>LLM is a PROBABILISTIC subtype</b>: Use {@code PROBABILISTIC} with subtype {@code "LLM"}
 *       ({@link com.ghatana.agent.probabilistic.ProbabilisticSubtype#LLM}) instead of the
 *       deprecated {@link #LLM} top-level type.</li>
 * </ul>
 *
 * <h2>Custom Types</h2>
 * <p>Register custom types at startup via {@link #registerCustomType(String)} and use
 * {@link #CUSTOM}. The subtype name is carried in the agent descriptor's {@code subtype} field.
 *
 * <pre>{@code
 * // At bootstrap:
 * AgentType.registerCustomType("RAG_RETRIEVER");
 *
 * // In agent descriptor:
 * AgentDescriptor.builder()
 *     .type(AgentType.CUSTOM)
 *     .subtype("RAG_RETRIEVER")
 *     .build();
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Unified 9-type agent taxonomy — covers all agentic processing paradigms
 * @doc.layer core
 * @doc.pattern ValueObject
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum AgentType {

    /**
     * Deterministic agent: rules, thresholds, FSM, pattern matching, policy evaluation.
     *
     * <p>Guaranteed: same input always produces same output. Sub-millisecond latency.
     * No stochastic components. Fully testable with exact-match assertions.
     *
     * <p>Subtypes ({@link com.ghatana.agent.deterministic.DeterministicSubtype}):
     * {@code RULE_BASED}, {@code THRESHOLD}, {@code FSM}, {@code PATTERN},
     * {@code POLICY}, {@code OPERATOR}, {@code EXACT_MATCH}, {@code TEMPLATE}.
     */
    DETERMINISTIC,

    /**
     * Probabilistic agent: ML models, Bayesian inference, statistical classifiers, LLMs.
     *
     * <p>Non-deterministic: output depends on model weights, sampling, or external calls.
     * Returns confidence scores. Test with statistical bounds, not exact assertions.
     *
     * <p>Subtypes ({@link com.ghatana.agent.probabilistic.ProbabilisticSubtype}):
     * {@code ML_MODEL}, {@code BAYESIAN}, {@code STATISTICAL}, {@code LLM}, {@code CLASSIFIER}.
     *
     * <p><b>For LLM agents</b>: use this type with subtype {@code "LLM"} instead of
     * the deprecated {@link #LLM}.
     */
    PROBABILISTIC,

    /**
     * Stateful event stream processor: CEP, windowed aggregations, ingestion, transformation.
     *
     * <p>Distinguishing characteristics: maintains window state and checkpoints,
     * processes ordered event streams with backpressure support, survives restarts
     * via checkpoint recovery. Primary type for all AEP operators.
     *
     * <p>Subtypes ({@link com.ghatana.agent.stream.StreamProcessorSubtype}):
     * {@code INGESTION}, {@code ROUTING}, {@code TRANSFORMATION}, {@code CEP},
     * {@code ENRICHMENT}, {@code WINDOW_AGGREGATION}, {@code FILTER}.
     */
    STREAM_PROCESSOR,

    /**
     * Goal-directed planning agent: HTN, ReAct, Tree-of-Thought, workflow orchestration.
     *
     * <p>Distinguishing characteristics: decomposes a high-level goal into a plan,
     * executes steps over multiple turns, handles blocked/waiting lifecycle states,
     * and revises the plan when sub-steps fail. Primary type for YAPPC orchestrators.
     *
     * <p>Subtypes ({@link com.ghatana.agent.planning.PlanningSubtype}):
     * {@code HTN}, {@code REACT}, {@code TOT}, {@code WORKFLOW},
     * {@code OBJECTIVE_DECOMPOSITION}.
     */
    PLANNING,

    /**
     * Hybrid agent: combines two or more reasoning modes with intelligent routing.
     *
     * <p>Distinguishing from COMPOSITE: HYBRID has a single logical identity with
     * multiple internal reasoners; COMPOSITE fans work out to independent sub-agents.
     * Routing strategies: DETERMINISTIC_FIRST, PROBABILISTIC_FIRST, PARALLEL with
     * confidence-based escalation.
     */
    HYBRID,

    /**
     * Self-tuning adaptive agent: multi-armed bandits, reinforcement learning, A/B testing.
     *
     * <p>Adapts parameters (thresholds, weights, routes) based on feedback signals.
     * Requires {@code learningLevel ≥ L2} in spec and explicit drift controls.
     */
    ADAPTIVE,

    /**
     * Composite ensemble agent: fan-out to multiple sub-agents with aggregation.
     *
     * <p>Aggregation strategies: FIRST_MATCH, MAJORITY_VOTE, WEIGHTED_AVERAGE, UNANIMOUS.
     * Sub-agents may run in parallel or sequentially. Requires
     * {@code interoperability.agentToAgent.enabled: true} and ≥2 sub-agent delegates.
     */
    COMPOSITE,

    /**
     * Reactive reflex agent: stateless event trigger → immediate action.
     *
     * <p>Must be stateless (no persistent window state), respond immediately,
     * and implement simple trigger→action logic only. For complex stream
     * processing with state, use {@link #STREAM_PROCESSOR} instead.
     * For conditional rules, use {@link #DETERMINISTIC}.
     */
    REACTIVE,

    /**
     * Large Language Model agent.
     *
     * @deprecated Use {@link #PROBABILISTIC} with
     *             {@link com.ghatana.agent.probabilistic.ProbabilisticSubtype#LLM} instead.
     *             {@code LLM} is retained for backward compatibility with existing
     *             agent definitions; it resolves to {@code PROBABILISTIC} at runtime.
     *             Will be removed in v3.0.0.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    LLM,

    /**
     * Extension point for domain-specific agent types not covered by the built-in taxonomy.
     *
     * <p>Custom types must be registered at startup via {@link #registerCustomType(String)}.
     * The specific custom type name is carried in the agent descriptor's {@code subtype} field.
     *
     * @see #registerCustomType(String)
     * @see #isCustomTypeRegistered(String)
     */
    CUSTOM;

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom Type Registry
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Label key used to carry the specific custom type name on an agent
     * when labels are used instead of the {@code subtype} field.
     */
    public static final String CUSTOM_TYPE_LABEL_KEY = "agent.custom.type";

    /** Thread-safe registry of known custom type names. */
    private static final Set<String> CUSTOM_TYPES = ConcurrentHashMap.newKeySet();

    /**
     * Registers a custom agent type name. Must be called before agents of this
     * custom type are created. Names are normalized to upper-case.
     *
     * @param customTypeName the custom type name (e.g., "RAG_RETRIEVER")
     * @throws NullPointerException     if customTypeName is null
     * @throws IllegalArgumentException if customTypeName is blank or conflicts
     *                                  with a built-in type
     */
    public static void registerCustomType(String customTypeName) {
        Objects.requireNonNull(customTypeName, "customTypeName must not be null");
        String normalized = customTypeName.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Custom type name must not be blank");
        }
        // Reject names that would shadow built-in types
        for (AgentType builtIn : values()) {
            if (builtIn != CUSTOM && builtIn.name().equals(normalized)) {
                throw new IllegalArgumentException(
                        "Custom type name '" + normalized + "' conflicts with built-in type");
            }
        }
        CUSTOM_TYPES.add(normalized);
    }

    /**
     * Returns whether the given custom type name has been registered.
     *
     * @param customTypeName the name to check (case-insensitive)
     * @return true if registered
     */
    public static boolean isCustomTypeRegistered(String customTypeName) {
        if (customTypeName == null) return false;
        return CUSTOM_TYPES.contains(customTypeName.trim().toUpperCase());
    }

    /**
     * Returns an unmodifiable view of all registered custom type names.
     */
    public static Set<String> registeredCustomTypes() {
        return Collections.unmodifiableSet(CUSTOM_TYPES);
    }

    /**
     * Resolves a type string to an {@code AgentType}.
     *
     * <p>Handles aliases for forward-compatibility:
     * <ul>
     *   <li>{@code "LLM"} / {@code "llm"} → {@link #PROBABILISTIC} (deprecated top-level LLM type)</li>
     *   <li>{@code "RULE_BASED"} / {@code "rule-based"} → {@link #DETERMINISTIC}</li>
     *   <li>{@code "POLICY"} / {@code "PATTERN"} → {@link #DETERMINISTIC}</li>
     *   <li>{@code "STREAM_PROCESSOR"} / {@code "stream-processor"} → {@link #STREAM_PROCESSOR}</li>
     *   <li>{@code "PLANNING"} → {@link #PLANNING}</li>
     * </ul>
     *
     * @param typeName the type name string (case-insensitive)
     * @return the resolved AgentType
     * @throws IllegalArgumentException if the name does not match any built-in or registered custom type
     */
    public static AgentType resolve(String typeName) {
        Objects.requireNonNull(typeName, "typeName must not be null");
        String normalized = typeName.trim().toUpperCase().replace('-', '_');
        // Backward-compat aliases
        switch (normalized) {
            case "RULE_BASED":
            case "POLICY":
            case "PATTERN":
                return DETERMINISTIC;
            case "STREAM_PROCESSOR":
                return STREAM_PROCESSOR;
            case "PLANNING":
                return PLANNING;
        }
        try {
            AgentType resolved = valueOf(normalized);
            return resolved;
        } catch (IllegalArgumentException e) {
            if (isCustomTypeRegistered(normalized)) {
                return CUSTOM;
            }
            throw new IllegalArgumentException(
                    "Unknown agent type: '" + typeName + "'. Built-in types: " +
                            java.util.Arrays.toString(values()) +
                            " (current non-deprecated: DETERMINISTIC, PROBABILISTIC, STREAM_PROCESSOR, PLANNING," +
                            " HYBRID, ADAPTIVE, COMPOSITE, REACTIVE, CUSTOM)" +
                            ", registered custom types: " + CUSTOM_TYPES);
        }
    }

    /**
     * Returns whether this type is the canonical, non-deprecated form.
     * Use as a lint/validation check to catch legacy type usage.
     */
    @SuppressWarnings("deprecation")
    public boolean isCanonical() {
        return this != LLM;
    }
}
