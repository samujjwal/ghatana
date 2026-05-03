package com.ghatana.kernel.observability;

import java.util.Map;

/**
 * Port for collecting and recording metrics in the kernel.
 *
 * <p>Provides a generic interface for metric collection that can be implemented
 * by various backends (Prometheus, OpenTelemetry, Micrometer, etc.).</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel port for metric collection (DMOS-P1-7)
 * @doc.layer core
 * @doc.pattern Port
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface MetricCollectorPort {

    /**
     * Records a counter increment.
     *
     * <p>Counters are monotonically increasing values (e.g., request count, error count).</p>
     *
     * @param name the metric name
     * @param increment the increment value
     * @param tags optional tags/dimensions for the metric
     */
    void incrementCounter(String name, long increment, String... tags);

    /**
     * Records a gauge value.
     *
     * <p>Gauges are values that can go up and down (e.g., queue size, active connections).</p>
     *
     * @param name the metric name
     * @param value the gauge value
     * @param tags optional tags/dimensions for the metric
     */
    void recordGauge(String name, double value, String... tags);

    /**
     * Records a histogram value.
     *
     * <p>Histograms observe values and count them in configurable buckets (e.g., request latency).</p>
     *
     * @param name the metric name
     * @param value the value to record
     * @param tags optional tags/dimensions for the metric
     */
    void recordHistogram(String name, double value, String... tags);

    /**
     * Records a timer duration.
     *
     * @param name the timer name
     * @param durationMillis the duration in milliseconds
     * @param tags optional tags/dimensions for the metric
     */
    void recordTimer(String name, long durationMillis, String... tags);

    /**
     * Starts a timer for measuring duration.
     *
     * @param name the timer name
     * @param tags optional tags/dimensions for the metric
     * @return timer instance
     */
    Timer startTimer(String name, String... tags);

    /**
     * Records a generic metric with tags.
     *
     * @param name the metric name
     * @param value the metric value
     * @param tags optional tags/dimensions for the metric
     */
    void recordMetric(String name, double value, Map<String, String> tags);

    /**
     * Timer for measuring duration.
     */
    interface Timer {
        /**
         * Stops the timer and records the duration.
         */
        void stop();

        /**
         * Gets the elapsed time in milliseconds.
         *
         * @return elapsed time
         */
        long getElapsedMillis();
    }
}
