/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.hybrid;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for {@link HybridAgent}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for hybrid agent decision blending
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class HybridAgentConfig extends AgentConfig {

    /**
     * Routing strategy between deterministic and probabilistic paths.
     */
    public enum RoutingStrategy {
        /** Try deterministic first; escalate to probabilistic on no-match. */
        DETERMINISTIC_FIRST,
        /** Try probabilistic first; fallback to deterministic on error/timeout. */
        PROBABILISTIC_FIRST,
        /** Run both in parallel; merge results. */
        PARALLEL
    }

    @Builder.Default @NotNull RoutingStrategy strategy = RoutingStrategy.DETERMINISTIC_FIRST;

    /** Agent ID for the deterministic sub-agent. */
    @Nullable String deterministicAgentId;

    /** Agent ID for the probabilistic sub-agent. */
    @Nullable String probabilisticAgentId;

    /** Confidence threshold for escalation from deterministic to probabilistic. */
    @Builder.Default double escalationConfidenceThreshold = 0.7;

    /** Fallback target when the primary path fails. */
    @Builder.Default @NotNull RoutingStrategy fallbackPath = RoutingStrategy.DETERMINISTIC_FIRST;
}
