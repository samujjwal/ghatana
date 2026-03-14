package com.ghatana.appplatform.eventstore.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Monitors Kafka producer buffer utilization and provides backpressure signals
 * to the outbox relay (STORY-K05-026).
 *
 * <p>When the producer's send buffer is under pressure ({@code buffer-available-bytes}
 * drops below a configured threshold), the relay should slow its polling to avoid
 * overwhelming the producer and causing {@code BufferExhaustedException}.
 *
 * <h2>Usage in the relay</h2>
 * <pre>{@code
 * if (flowControl.shouldThrottle()) {
 *     Thread.sleep(flowControl.throttleDelayMs());
 * }
 * }</pre>
 *
 * <h2>Kafka metrics used</h2>
 * <ul>
 *   <li>{@code buffer-available-bytes} — free bytes in the producer's buffer pool</li>
 *   <li>{@code buffer-total-bytes} — total capacity of the producer's buffer pool</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Kafka producer backpressure / flow control for the outbox relay (K05-026)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KafkaProducerFlowControl {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerFlowControl.class);

    /** Metric group for all producer metrics. */
    private static final String METRIC_GROUP = "producer-metrics";

    /** Fraction of buffer pool that must remain free before throttling kicks in. */
    private static final double DEFAULT_THROTTLE_THRESHOLD = 0.20;  // throttle when < 20% free

    /** Default delay injected between relay poll cycles when throttled. */
    private static final long DEFAULT_THROTTLE_DELAY_MS = 500L;

    private final KafkaProducer<?, ?> producer;
    private final double throttleThreshold;
    private final long throttleDelayMs;

    /**
     * Creates a flow controller with default thresholds (20% buffer free → throttle, 500 ms delay).
     *
     * @param producer the producer whose buffer utilization is monitored
     */
    public KafkaProducerFlowControl(KafkaProducer<?, ?> producer) {
        this(producer, DEFAULT_THROTTLE_THRESHOLD, DEFAULT_THROTTLE_DELAY_MS);
    }

    /**
     * Creates a flow controller with custom thresholds.
     *
     * @param producer          the producer to monitor
     * @param throttleThreshold fraction of buffer pool that must be free (0.0–1.0); throttle below this
     * @param throttleDelayMs   milliseconds of extra delay added per poll cycle when throttled
     */
    public KafkaProducerFlowControl(KafkaProducer<?, ?> producer,
                                     double throttleThreshold,
                                     long throttleDelayMs) {
        this.producer          = Objects.requireNonNull(producer, "producer");
        if (throttleThreshold < 0.0 || throttleThreshold > 1.0) {
            throw new IllegalArgumentException("throttleThreshold must be between 0.0 and 1.0");
        }
        this.throttleThreshold = throttleThreshold;
        this.throttleDelayMs   = throttleDelayMs;
    }

    /**
     * Returns {@code true} when the relay should slow down due to producer buffer pressure.
     *
     * <p>Reads the Kafka producer metric {@code buffer-available-bytes} and
     * {@code buffer-total-bytes}. If the available fraction falls below
     * {@link #throttleThreshold} (default: 20%), the relay should insert
     * {@link #throttleDelayMs()} ms of extra sleep before its next poll cycle.
     *
     * <p>Returns {@code false} if the metrics are unavailable (fail-safe — don't throttle
     * when we can't observe the state).
     *
     * @return true if the relay should throttle
     */
    public boolean shouldThrottle() {
        try {
            Map<MetricName, ? extends Metric> metrics = producer.metrics();
            double available = getMetricValue(metrics, "buffer-available-bytes");
            double total     = getMetricValue(metrics, "buffer-total-bytes");
            if (total <= 0) {
                return false;  // Can't determine ratio; fail-safe
            }
            double freeFraction = available / total;
            boolean throttle = freeFraction < throttleThreshold;
            if (throttle) {
                log.debug("Producer buffer pressure detected: available={}/{} ({}% free) — throttling relay",
                    (long) available, (long) total,
                    String.format("%.1f", freeFraction * 100));
            }
            return throttle;
        } catch (Exception e) {
            log.trace("Cannot read producer metrics: {} — not throttling", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the number of milliseconds the relay should sleep when {@link #shouldThrottle()} is true.
     *
     * @return throttle delay in milliseconds
     */
    public long throttleDelayMs() {
        return throttleDelayMs;
    }

    /**
     * Returns the current buffer utilization as a fraction (0.0 = empty, 1.0 = full).
     * Returns -1.0 if metrics are unavailable.
     *
     * @return buffer utilization fraction, or -1.0 if unavailable
     */
    public double bufferUtilization() {
        try {
            Map<MetricName, ? extends Metric> metrics = producer.metrics();
            double available = getMetricValue(metrics, "buffer-available-bytes");
            double total     = getMetricValue(metrics, "buffer-total-bytes");
            if (total <= 0) return -1.0;
            return 1.0 - (available / total);
        } catch (Exception e) {
            return -1.0;
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private double getMetricValue(Map<MetricName, ? extends Metric> metrics, String name) {
        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            if (entry.getKey().group().equals(METRIC_GROUP)
                    && entry.getKey().name().equals(name)) {
                Object value = entry.getValue().metricValue();
                if (value instanceof Number n) {
                    return n.doubleValue();
                }
            }
        }
        throw new IllegalStateException("Metric not found: " + name);
    }
}
