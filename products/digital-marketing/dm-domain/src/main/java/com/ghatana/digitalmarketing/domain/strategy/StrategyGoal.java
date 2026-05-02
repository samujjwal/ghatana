package com.ghatana.digitalmarketing.domain.strategy;

import java.util.Objects;

/**
 * A single measurable goal within a 30-day marketing strategy.
 *
 * @doc.type class
 * @doc.purpose Represents one measurable goal in a marketing strategy with rationale and measurement method
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record StrategyGoal(
    String goalType,
    String description,
    String targetMetric,
    String measurementMethod
) {
    /**
     * Compact constructor that validates all fields.
     */
    public StrategyGoal {
        Objects.requireNonNull(goalType, "goalType must not be null");
        if (goalType.isBlank()) {
            throw new IllegalArgumentException("goalType must not be blank");
        }
        Objects.requireNonNull(description, "description must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        Objects.requireNonNull(targetMetric, "targetMetric must not be null");
        if (targetMetric.isBlank()) {
            throw new IllegalArgumentException("targetMetric must not be blank");
        }
        Objects.requireNonNull(measurementMethod, "measurementMethod must not be null");
        if (measurementMethod.isBlank()) {
            throw new IllegalArgumentException("measurementMethod must not be blank");
        }
    }
}
