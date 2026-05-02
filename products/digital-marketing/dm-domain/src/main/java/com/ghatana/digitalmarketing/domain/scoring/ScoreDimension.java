package com.ghatana.digitalmarketing.domain.scoring;

import java.util.Objects;

/**
 * A single scored dimension contributing to the overall lead score.
 *
 * @doc.type class
 * @doc.purpose Represents one scoring factor and its contribution in F1-012 lead scoring
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ScoreDimension(
        String dimension,
        int points,
        String rationale
) {
    /**
     * Validates the dimension record on construction.
     */
    public ScoreDimension {
        Objects.requireNonNull(dimension, "dimension must not be null");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("points must be between 0 and 100");
        }
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
