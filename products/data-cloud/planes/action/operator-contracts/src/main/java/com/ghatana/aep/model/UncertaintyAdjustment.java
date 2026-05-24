package com.ghatana.aep.model;

/**
 * Penalties applied while propagating uncertainty through an operator.
 *
 * @doc.type record
 * @doc.purpose Captures late, missing, and duplicate event confidence penalties
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record UncertaintyAdjustment(
        double lateEventPenalty,
        double missingEventPenalty,
        double duplicateEventPenalty
) {

    public UncertaintyAdjustment {
        requireProbability(lateEventPenalty, "lateEventPenalty");
        requireProbability(missingEventPenalty, "missingEventPenalty");
        requireProbability(duplicateEventPenalty, "duplicateEventPenalty");
    }

    public static UncertaintyAdjustment none() {
        return new UncertaintyAdjustment(0.0, 0.0, 0.0);
    }

    double multiplier() {
        return (1.0 - lateEventPenalty) * (1.0 - missingEventPenalty) * (1.0 - duplicateEventPenalty);
    }

    private static void requireProbability(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
