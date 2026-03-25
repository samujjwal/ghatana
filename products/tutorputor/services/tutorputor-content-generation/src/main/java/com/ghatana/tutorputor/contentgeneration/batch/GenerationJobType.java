package com.ghatana.tutorputor.contentgeneration.batch;

/**
 * Type of generation job — maps to the Fastify-side GenerationJobType enum.
 *
 * @doc.type enum
 * @doc.purpose Canonical generation job type classification
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public enum GenerationJobType {
    CLAIM,
    EXPLAINER,
    WORKED_EXAMPLE,
    SIMULATION,
    ANIMATION,
    ASSESSMENT,
    EVALUATION;

    /**
     * Parse from a case-insensitive string representation.
     *
     * @param value string value (e.g. "claim", "WORKED_EXAMPLE")
     * @return matching enum value
     * @throws IllegalArgumentException if value is unknown
     */
    public static GenerationJobType fromString(String value) {
        return valueOf(value.toUpperCase().replace("-", "_"));
    }
}
