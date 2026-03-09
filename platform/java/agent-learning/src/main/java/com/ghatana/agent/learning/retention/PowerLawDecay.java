package com.ghatana.agent.learning.retention;

/**
 * Power-law decay: f(t) = 1 / (1 + t/scale)^exponent.
 * Decays slower than exponential for old items ("long tail").
 *
 * @doc.type class
 * @doc.purpose Power-law decay function
 * @doc.layer agent-learning
 */
public class PowerLawDecay implements DecayFunction {

    private final double scale;
    private final double exponent;

    /**
     * @param scaleHours Scale parameter in hours
     * @param exponent Decay exponent (higher = faster decay)
     */
    public PowerLawDecay(double scaleHours, double exponent) {
        if (scaleHours <= 0) throw new IllegalArgumentException("scaleHours must be > 0");
        if (exponent <= 0) throw new IllegalArgumentException("exponent must be > 0");
        this.scale = scaleHours;
        this.exponent = exponent;
    }

    /** Creates a default power-law with 168-hour (1 week) scale and exponent 1.5. */
    public static PowerLawDecay defaultDecay() {
        return new PowerLawDecay(168.0, 1.5);
    }

    @Override
    public double compute(double ageHours) {
        return 1.0 / Math.pow(1.0 + ageHours / scale, exponent);
    }
}
