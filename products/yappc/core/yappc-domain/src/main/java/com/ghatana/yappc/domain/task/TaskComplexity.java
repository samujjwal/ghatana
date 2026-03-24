package com.ghatana.products.yappc.domain.task;

/**
 * Task complexity levels.
 *
 * @doc.type enum
 * @doc.purpose Categorize task execution complexity
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum TaskComplexity {
    TRIVIAL(1, "< 1 second", 0.01),
    SIMPLE(5, "< 5 seconds", 0.05),
    MODERATE(15, "< 15 seconds", 0.15),
    COMPLEX(30, "< 30 seconds", 0.50),
    VERY_COMPLEX(60, "< 60 seconds", 1.00);

    private final int estimatedSeconds;
    private final String description;
    private final double estimatedCost;

    TaskComplexity(int estimatedSeconds, String description, double estimatedCost) {
        this.estimatedSeconds = estimatedSeconds;
        this.description = description;
        this.estimatedCost = estimatedCost;
    }

    public int getEstimatedSeconds() {
        return estimatedSeconds;
    }

    public String getDescription() {
        return description;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }
}
