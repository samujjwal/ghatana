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
 * Extensible taxonomy of agent computational types.
 *
 * <p>Each type describes the fundamental processing model of an agent
 * and determines what configuration knobs, runtime guarantees, and
 * operational characteristics the agent exposes.
 *
 * <h2>Built-in Types</h2>
 * <table>
 *   <tr><th>Type</th><th>Use When</th><th>Latency</th><th>Determinism</th></tr>
 *   <tr><td>DETERMINISTIC</td><td>Rules, thresholds, FSM</td><td>sub-ms</td><td>100%</td></tr>
 *   <tr><td>PROBABILISTIC</td><td>ML model, Bayesian, LLM</td><td>ms–s</td><td>0%</td></tr>
 *   <tr><td>HYBRID</td><td>Fast-path + fallback</td><td>sub-ms to ms</td><td>Partial</td></tr>
 *   <tr><td>ADAPTIVE</td><td>Bandits, RL, self-tuning</td><td>ms</td><td>0%</td></tr>
 *   <tr><td>COMPOSITE</td><td>Ensembles, voting, DAGs</td><td>depends</td><td>varies</td></tr>
 *   <tr><td>REACTIVE</td><td>Triggers, reflex, circuit-breaker</td><td>sub-ms</td><td>100%</td></tr>
 * </table>
 *
 * <h2>Custom Types</h2>
 * <p>Register custom agent types at startup via {@link #registerCustomType(String)}
 * and use the {@link #CUSTOM} variant. The custom type name is carried in the
 * agent descriptor's {@code subtype} field or labels.
 *
 * <pre>{@code
 * // At bootstrap:
 * AgentType.registerCustomType("RAG_RETRIEVER");
 * AgentType.registerCustomType("ORCHESTRATOR");
 *
 * // In agent descriptor:
 * AgentDescriptor.builder()
 *     .type(AgentType.CUSTOM)
 *     .subtype("RAG_RETRIEVER")
 *     .build();
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Extensible agent type taxonomy
 * @doc.layer core
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum AgentType {

    /**
     * Rule-based, exact-match, threshold, finite-state-machine, or pattern agent.
     * Guaranteed deterministic: same input always produces same output.
     * Sub-millisecond latency. No stochastic components.
     */
    DETERMINISTIC,

    /**
     * Machine-learning model, Bayesian inference, statistical, or LLM-backed agent.
     * Non-deterministic: output depends on model weights, sampling,
     * or external service. Returns confidence scores.
     */
    PROBABILISTIC,

    /**
     * Combines a deterministic fast-path with a probabilistic fallback (or vice versa).
     * Configurable routing strategy: DETERMINISTIC_FIRST, PROBABILISTIC_FIRST,
     * or PARALLEL. Confidence-based escalation between sub-agents.
     */
    HYBRID,

    /**
     * Self-tuning agent using online learning, multi-armed bandits,
     * reinforcement learning, or A/B testing.
     * Adapts parameters (thresholds, weights) based on feedback signals
     * over time. Maintains exploration/exploitation balance.
     */
    ADAPTIVE,

    /**
     * Ensemble of sub-agents with aggregation (weighted average, majority vote,
     * first match, unanimous), fan-out/fan-in, or conditional routing.
     */
    COMPOSITE,

    /**
     * Event-driven trigger agent with sub-millisecond response.
     * Evaluates conditions (thresholds, windows, counters) and fires
     * actions (alerts, scaling, suppression). Integrates with data-cloud
     * ReflexEngine.
     */
    REACTIVE,

    /**
     * Large Language Model (LLM) backed agent.
     * Uses an external LLM service (e.g., GPT, Claude) for reasoning,
     * generation, or classification. Requires a system prompt and supports
     * tool declarations, temperature, and token limits.
     */
    LLM,

    /**
     * Extension point for user-defined agent types not covered by the built-in taxonomy.
     *
     * <p>Custom types must be registered at startup via {@link #registerCustomType(String)}
     * before use. The specific custom type name is conveyed via the agent descriptor's
     * {@code subtype} field or via a label keyed by {@link #CUSTOM_TYPE_LABEL_KEY}.
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
     * Resolves a type string to an {@code AgentType}. First attempts
     * {@link #valueOf(String)}; if that fails for a non-built-in name,
     * checks the custom type registry and returns {@link #CUSTOM}.
     *
     * @param typeName the type name string
     * @return the resolved AgentType
     * @throws IllegalArgumentException if the name does not match any built-in
     *         or registered custom type
     */
    public static AgentType resolve(String typeName) {
        Objects.requireNonNull(typeName, "typeName must not be null");
        String normalized = typeName.trim().toUpperCase();
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            if (isCustomTypeRegistered(normalized)) {
                return CUSTOM;
            }
            throw new IllegalArgumentException(
                    "Unknown agent type: '" + typeName + "'. Built-in types: " +
                            java.util.Arrays.toString(values()) +
                            ", registered custom types: " + CUSTOM_TYPES);
        }
    }
}
