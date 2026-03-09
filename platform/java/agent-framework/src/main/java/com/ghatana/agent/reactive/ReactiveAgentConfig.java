/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.reactive;

import com.ghatana.agent.AgentConfig;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for {@link ReactiveAgent}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for reactive agent event handling
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ReactiveAgentConfig extends AgentConfig {

    public enum ReactiveSubtype { TRIGGER, CIRCUIT_BREAKER, RATE_LIMITER, REFLEX }

    @Builder.Default @NotNull ReactiveSubtype subtype = ReactiveSubtype.TRIGGER;

    /** Trigger definitions for TRIGGER subtype. */
    @Singular @NotNull List<TriggerDefinition> triggers;

    /**
     * A trigger definition: event condition → action with cooldown.
     */
    @Value
    @Builder(toBuilder = true)
    public static class TriggerDefinition {
        /** Trigger name. */
        @NotNull String name;
        /** Event type field (dot-path) to match. */
        @NotNull String eventTypeField;
        /** Event type value to match. */
        @NotNull String eventTypeValue;
        /** Additional condition field (dot-path). May be null for unconditional. */
        @Nullable String conditionField;
        /** Operator for the additional condition. */
        @Nullable String conditionOperator;
        /** Value for the additional condition. */
        @Nullable Object conditionValue;
        /** Threshold: number of events within the counting window. 0 = fire on first match. */
        @Builder.Default int threshold = 0;
        /** Counting window for threshold-based triggers. */
        @Builder.Default @NotNull Duration countingWindow = Duration.ofMinutes(5);
        /** Cooldown period after firing (no re-fire during this period). */
        @Builder.Default @NotNull Duration cooldown = Duration.ZERO;
        /** Action to emit when trigger fires. */
        @Singular @NotNull Map<String, Object> actions;
        /** Priority (lower = higher). */
        @Builder.Default int priority = 100;
    }
}
