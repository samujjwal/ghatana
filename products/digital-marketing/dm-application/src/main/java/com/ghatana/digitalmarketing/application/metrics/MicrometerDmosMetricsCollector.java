package com.ghatana.digitalmarketing.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed implementation of {@link DmosMetricsCollector}.
 *
 * <p>Bridges the DMOS product-local metrics port to the platform Micrometer
 * {@link MeterRegistry}.  Each {@link #increment} call resolves (or creates) a
 * Micrometer {@link Counter} keyed by the metric name and the supplied label set,
 * then increments it by one.  The {@link #observe} default is overridden to use a
 * native Micrometer {@link Timer} so histograms are scraped correctly by Prometheus.</p>
 *
 * <p><b>Thread-safety:</b> {@link MeterRegistry} is thread-safe; all public methods
 * delegate to it without additional synchronisation.</p>
 *
 * <p><b>Null-safety:</b> A {@code null} label map is treated as an empty tag set.</p>
 *
 * <h3>Migration note</h3>
 * <p>Prior to this class, {@link LoggingDmosMetricsCollector} was the sole production
 * implementation.  That class remains on the classpath as a fallback for environments
 * where a {@link MeterRegistry} is not available (e.g., CLI tooling), but all server
 * deployments should prefer this implementation wired via
 * {@code DmosApiServer#wireObservability}.</p>
 *
 * @see DmosMetricsCollector for the port contract and canonical metric names
 * @see LoggingDmosMetricsCollector for the SLF4J-only fallback
 *
 * @doc.type class
 * @doc.purpose Micrometer bridge for DMOS business KPI metrics (KERNEL-P1-4)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class MicrometerDmosMetricsCollector implements DmosMetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerDmosMetricsCollector.class);

    private final MeterRegistry registry;

    /**
     * Creates a collector backed by the supplied {@link MeterRegistry}.
     *
     * @param registry Micrometer registry; must not be {@code null}
     */
    public MicrometerDmosMetricsCollector(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the {@code labels} map to Micrometer {@link Tags} and delegates
     * to {@code registry.counter(counterName, tags).increment()}.</p>
     */
    @Override
    public void increment(String counterName, Map<String, String> labels) {
        if (counterName == null || counterName.isBlank()) {
            LOG.warn("[DMOS-METRICS] Received blank counterName — metric dropped");
            return;
        }
        Tags tags = toMicrometerTags(labels);
        try {
            Counter counter = registry.counter(counterName, tags);
            counter.increment();
        } catch (Exception e) {
            // Metrics failures must never propagate to the caller; log and continue.
            LOG.warn("[DMOS-METRICS] Failed to increment counter '{}': {}", counterName, e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the default fall-back implementation to record the observation
     * on a Micrometer {@link Timer}, enabling native histogram and quantile support
     * in Prometheus / Grafana.</p>
     */
    @Override
    public void observe(String metricName, long durationMs, Map<String, String> labels) {
        if (metricName == null || metricName.isBlank()) {
            LOG.warn("[DMOS-METRICS] Received blank metricName for observe — dropped");
            return;
        }
        Tags tags = toMicrometerTags(labels);
        try {
            Timer timer = Timer.builder(metricName)
                    .tags(tags)
                    .register(registry);
            timer.record(Duration.ofMillis(durationMs));
        } catch (Exception e) {
            LOG.warn("[DMOS-METRICS] Failed to record timer '{}': {}", metricName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static Tags toMicrometerTags(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Tags.empty();
        }
        List<Tag> tagList = new ArrayList<>(labels.size());
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && !key.isBlank()) {
                tagList.add(Tag.of(key, value != null ? value : "unknown"));
            }
        }
        return Tags.of(tagList);
    }
}
