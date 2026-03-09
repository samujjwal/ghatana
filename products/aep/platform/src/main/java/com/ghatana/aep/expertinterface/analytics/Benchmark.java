package com.ghatana.aep.expertinterface.analytics;

/**
 * Represents a benchmark value for pattern performance comparison.
 *
 * <p>Benchmark stores baseline performance metrics used to evaluate
 * pattern effectiveness against established standards.
 *
 * @doc.type class
 * @doc.purpose Stores benchmark values for pattern performance comparison
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class Benchmark {
    private double benchmark;
    public double getBenchmark() { return benchmark; }
}
