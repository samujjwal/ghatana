package com.ghatana.finance.ai;

/**
 * Extracted fraud patterns.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for fraud pattern analysis
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class Patterns {
    private final double confidence;

    public Patterns(double confidence) {
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }
}
