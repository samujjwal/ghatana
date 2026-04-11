package com.ghatana.agent.learning.retention;

/**
 * Step decay: value drops to a lower level at discrete intervals.
 * Useful for tiered retention policies.
 *
 * @doc.type class
 * @doc.purpose Step decay function
 * @doc.layer agent-learning
 */
public class StepDecay implements DecayFunction {

    private final double[] thresholdHours;
    private final double[] values;

    /**
     * @param thresholdHours Age thresholds in hours (ascending)
     * @param values Decay values for each tier (should be same length + 1)
     */
    public StepDecay(double[] thresholdHours, double[] values) {
        if (thresholdHours.length + 1 != values.length) {
            throw new IllegalArgumentException("values must have exactly thresholdHours.length + 1 elements");
        }
        this.thresholdHours = thresholdHours.clone();
        this.values = values.clone();
    }

    /**
     * Creates a default 3-tier step decay:
     * <ul>
     *   <li>0-24h: 1.0</li>
     *   <li>1-7d: 0.7</li>
     *   <li>7-30d: 0.3</li>
     *   <li>30d+: 0.1</li>
     * </ul>
     */
    public static StepDecay defaultTiered() {
        return new StepDecay(
                new double[]{24.0, 168.0, 720.0},
                new double[]{1.0, 0.7, 0.3, 0.1}
        );
    }

    @Override
    public double compute(double ageHours) {
        for (int i = 0; i < thresholdHours.length; i++) {
            if (ageHours < thresholdHours[i]) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }
}
