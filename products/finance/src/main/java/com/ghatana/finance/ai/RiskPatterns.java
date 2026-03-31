package com.ghatana.finance.ai;

/**
 * Risk patterns from learning.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for risk pattern analysis
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class RiskPatterns {
    private final double confidence;

    public RiskPatterns(double confidence) {
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }
}
