package com.ghatana.agent.learning.retention;

/**
 * Function that computes a decay factor for a memory item based on age.
 * Returns values in [0.0, 1.0] where 1.0 = fully fresh, 0.0 = fully decayed.
 *
 * @doc.type interface
 * @doc.purpose Memory decay function SPI
 * @doc.layer agent-learning
 */
public interface DecayFunction {

    /**
     * Computes the decay factor for the given age.
     *
     * @param ageHours Age of the memory item in hours
     * @return Decay factor in [0.0, 1.0]
     */
    double compute(double ageHours);
}
