/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

/**
 * Subtypes of probabilistic agent behaviour.
 *
 * <p>All subtypes share: output confidence is non-trivial (< 1.0), results
 * may vary across invocations, and tests must use statistical bounds rather
 * than exact-match assertions.
 *
 * @since 2.0.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of probabilistic agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ProbabilisticSubtype {
    /** Machine learning model inference (batch or online). */
    ML_MODEL,
    /** Bayesian inference / probabilistic graphical model. */
    BAYESIAN,
    /** Statistical analysis (z-score, percentile, IQR, etc.). */
    STATISTICAL,
    /**
     * Large Language Model (via LangChain4j, OpenAI, Anthropic, or Gemini).
     *
     * <p>Use {@code AgentType.PROBABILISTIC} with this subtype instead of the
     * deprecated {@code AgentType.LLM} top-level enum value.
     */
    LLM,
    /**
     * Supervised or zero-shot classifier (multi-label, embedding-based,
     * or rule-supplemented NLP classifier).
     */
    CLASSIFIER
}
