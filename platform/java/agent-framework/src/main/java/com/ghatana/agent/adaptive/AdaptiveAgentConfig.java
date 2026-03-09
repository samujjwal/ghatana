/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.adaptive;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for {@link AdaptiveAgent}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for adaptive agent behavior and learning parameters
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class AdaptiveAgentConfig extends AgentConfig {

    public enum AdaptiveSubtype { BANDIT, REINFORCEMENT, SELF_TUNING, AB_TEST }

    public enum BanditAlgorithm { UCB1, THOMPSON_SAMPLING, EPSILON_GREEDY }

    @Builder.Default @NotNull AdaptiveSubtype subtype = AdaptiveSubtype.BANDIT;

    @Builder.Default @NotNull BanditAlgorithm banditAlgorithm = BanditAlgorithm.UCB1;

    /** Exploration rate for EPSILON_GREEDY (probability of exploring). */
    @Builder.Default double explorationRate = 0.1;

    /** Name of the parameter being tuned (e.g. "threshold"). */
    @NotNull String tunedParameter;

    /** Lower bound for the tuned parameter. */
    @Builder.Default double parameterMin = 0.0;

    /** Upper bound for the tuned parameter. */
    @Builder.Default double parameterMax = 1.0;

    /** Number of discrete arms/bins for bandit. */
    @Builder.Default int armCount = 10;

    /** Objective metric name (e.g. "f1_score", "precision"). */
    @Builder.Default @NotNull String objectiveMetric = "f1_score";

    /** Whether to maximize (true) or minimize (false) the objective. */
    @Builder.Default boolean maximize = true;
}
