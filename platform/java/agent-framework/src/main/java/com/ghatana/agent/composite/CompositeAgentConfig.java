/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.composite;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configuration for {@link CompositeAgent}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for composite agent orchestration strategy
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class CompositeAgentConfig extends AgentConfig {

    public enum CompositeSubtype { ENSEMBLE, VOTING, PIPELINE, ROUTER }

    public enum AggregationStrategy {
        /** Weighted average of numeric outputs from sub-agents. */
        WEIGHTED_AVERAGE,
        /** Majority vote on categorical outputs. */
        MAJORITY_VOTE,
        /** Use the result of the first sub-agent that matches. */
        FIRST_MATCH,
        /** All sub-agents must agree. */
        UNANIMOUS
    }

    @Builder.Default @NotNull CompositeSubtype subtype = CompositeSubtype.ENSEMBLE;

    @Builder.Default @NotNull AggregationStrategy aggregationStrategy =
            AggregationStrategy.WEIGHTED_AVERAGE;

    /** Weights for sub-agents (parallel array with sub-agent IDs). */
    @Singular @NotNull List<Double> weights;

    /** Sub-agent IDs. */
    @Singular @NotNull List<String> subAgentIds;

    /** The output field to use for voting (e.g. "decision"). */
    @Builder.Default @NotNull String votingField = "decision";

    /** The output field to aggregate numerically (e.g. "riskScore"). */
    @Builder.Default @NotNull String numericField = "score";
}
