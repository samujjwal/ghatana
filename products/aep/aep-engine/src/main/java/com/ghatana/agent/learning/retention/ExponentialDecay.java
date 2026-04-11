package com.ghatana.agent.learning.retention;

/**
 * Exponential decay: f(t) = e^(-λt) where λ = ln(2) / halfLifeHours.
 *
 * @doc.type class
 * @doc.purpose Exponential decay function
 * @doc.layer agent-learning
 */
public class ExponentialDecay implements DecayFunction {

    private final double lambda;

    /**
     * @param halfLifeHours Half-life in hours (time for value to drop to 0.5)
     */
    public ExponentialDecay(double halfLifeHours) {
        if (halfLifeHours <= 0) throw new IllegalArgumentException("halfLifeHours must be > 0");
        this.lambda = Math.log(2) / halfLifeHours;
    }

    /** Creates a decay with 7-day half-life. */
    public static ExponentialDecay sevenDay() {
        return new ExponentialDecay(7.0 * 24.0);
    }

    @Override
    public double compute(double ageHours) {
        return Math.exp(-lambda * ageHours);
    }
}
