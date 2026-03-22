/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Threshold evaluator with hysteresis support.
 *
 * <p>Hysteresis prevents rapid oscillation when a metric hovers near a threshold.
 * Two separate thresholds define:
 * <ul>
 *   <li><b>activationThreshold</b> — value must cross this to activate</li>
 *   <li><b>deactivationThreshold</b> — value must fall below this to deactivate</li>
 * </ul>
 *
 * <p>Example: CPU alert activates at 90%, deactivates at 70%.
 *
 * <p>Thread-safe: uses {@link AtomicReference} for state.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Evaluates numeric thresholds for deterministic decision boundaries
 * @doc.layer platform
 * @doc.pattern Service
 */
@Getter
public class ThresholdEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluator.class);

    private final String id;
    private final String field;
    private final double activationThreshold;
    private final double deactivationThreshold;
    private final boolean upperBound;

    private final AtomicReference<ThresholdState> currentState =
            new AtomicReference<>(ThresholdState.INACTIVE);

    public enum ThresholdState { INACTIVE, ACTIVE }

    @Value
    @Builder
    public static class ThresholdResult {
        boolean active;
        @NotNull ThresholdState previousState;
        @NotNull ThresholdState currentState;
        boolean stateChanged;
        double evaluatedValue;
        @NotNull String field;
    }

    @Builder
    public ThresholdEvaluator(
            @NotNull String id,
            @NotNull String field,
            double activationThreshold,
            double deactivationThreshold,
            boolean upperBound) {
        this.id = Objects.requireNonNull(id, "id");
        this.field = Objects.requireNonNull(field, "field");
        this.activationThreshold = activationThreshold;
        this.deactivationThreshold = Double.isNaN(deactivationThreshold)
                ? activationThreshold : deactivationThreshold;
        this.upperBound = upperBound;
    }

    @NotNull
    public ThresholdResult evaluate(@NotNull Map<String, Object> input) {
        Objects.requireNonNull(input, "input must not be null");

        Object raw = RuleCondition.resolve(field, input);
        if (raw == null) {
            throw new IllegalArgumentException("Field not found: " + field);
        }

        double value;
        if (raw instanceof Number n) {
            value = n.doubleValue();
        } else {
            try {
                value = Double.parseDouble(raw.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Field is not numeric: " + field + "=" + raw);
            }
        }

        ThresholdState previous = currentState.get();
        ThresholdState next = computeNext(value, previous);
        currentState.set(next);

        boolean changed = previous != next;
        if (changed) {
            log.debug("Threshold state change: id={}, field={}, value={}, {} → {}",
                    id, field, value, previous, next);
        }

        return ThresholdResult.builder()
                .active(next == ThresholdState.ACTIVE)
                .previousState(previous)
                .currentState(next)
                .stateChanged(changed)
                .evaluatedValue(value)
                .field(field)
                .build();
    }

    public void reset() { currentState.set(ThresholdState.INACTIVE); }

    public ThresholdState getState() { return currentState.get(); }

    private ThresholdState computeNext(double value, ThresholdState previous) {
        if (upperBound) {
            if (previous == ThresholdState.INACTIVE) {
                return value >= activationThreshold ? ThresholdState.ACTIVE : ThresholdState.INACTIVE;
            } else {
                return value < deactivationThreshold ? ThresholdState.INACTIVE : ThresholdState.ACTIVE;
            }
        } else {
            if (previous == ThresholdState.INACTIVE) {
                return value <= activationThreshold ? ThresholdState.ACTIVE : ThresholdState.INACTIVE;
            } else {
                return value > deactivationThreshold ? ThresholdState.INACTIVE : ThresholdState.ACTIVE;
            }
        }
    }
}
