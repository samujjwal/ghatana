/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified taxonomy of agent computational types.
 *
 * <p>Each value describes the dominant processing model of an agent.
 * For finer-grained classification, agents declare a subtype via their
 * {@link AgentDescriptor}.
 *
 * @doc.type enum
 * @doc.purpose Unified agent type taxonomy
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum AgentType {

    /** Rules, thresholds, FSM, pattern matching, policy evaluation. */
    DETERMINISTIC,

    /** ML models, Bayesian inference, statistical classifiers, LLMs. */
    PROBABILISTIC,

    /** Stateful event stream processor: CEP, windowed aggregations. */
    STREAM_PROCESSOR,

    /** Goal-directed multi-step: HTN, ReAct, workflow orchestration. */
    PLANNING,

    /** Combines deterministic fast-path with probabilistic fallback. */
    HYBRID,

    /** Self-tuning: multi-armed bandits, RL, A/B testing. */
    ADAPTIVE,

    /** Ensemble fan-out to multiple sub-agents with aggregation. */
    COMPOSITE,

    /** Stateless event trigger to immediate action. */
    REACTIVE,

    /**
     * Large Language Model agent.
     *
     * @deprecated Use {@link #PROBABILISTIC} with subtype "LLM" instead.
     */
    @Deprecated
    LLM,

    /** Extension point for domain-specific agent types. */
    CUSTOM;

    /** Label key for custom type name when using labels. */
    public static final String CUSTOM_TYPE_LABEL_KEY = "agent.custom.type";

    private static final Set<String> CUSTOM_TYPES = ConcurrentHashMap.newKeySet();

    /**
     * Registers a custom agent type name. Names are normalized to upper-case.
     *
     * @param customTypeName the custom type name
     */
    public static void registerCustomType(String customTypeName) {
        Objects.requireNonNull(customTypeName, "customTypeName must not be null");
        String normalized = customTypeName.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Custom type name must not be blank");
        }
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
}
