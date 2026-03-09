/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

/**
 * Subtypes of probabilistic agent behaviour.
 *
 * @since 2.0.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of probabilistic agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ProbabilisticSubtype {
    /** Machine learning model inference. */
    ML_MODEL,
    /** Bayesian inference / probabilistic graphical model. */
    BAYESIAN,
    /** Statistical analysis (z-score, percentile, etc.). */
    STATISTICAL,
    /** Large Language Model (via LangChain4j or similar). */
    LLM
}
