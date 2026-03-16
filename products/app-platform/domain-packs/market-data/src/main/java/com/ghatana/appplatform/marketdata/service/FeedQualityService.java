package com.ghatana.appplatform.marketdata.service;

import com.ghatana.appplatform.marketdata.domain.TickSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Tracks and exposes feed quality metrics per data source (D04-008).
 *              Metrics: tick_rate (ticks/sec), latency_ms (feed→store), gap_count, error_rate.
 *              All metrics exposed as Prometheus counters/gauges under {@code marketdata.*}.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — application service; Micrometer metrics
 */
public class FeedQualityService {

    private static final Logger log = LoggerFactory.getLogger(FeedQualityService.class);

    private final Map<String, SourceMetrics> metricsMap = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final Executor executor;

    public FeedQualityService(MeterRegistry meterRegistry, Executor executor) {
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /** Record a successfully processed tick from a source. */
    public void recordTick(TickSource source, long feedTimestampMs, long storeTimestampMs) {
        var m = getOrCreate(source.name());
        m.tickCount.incrementAndGet();
        long latency = storeTimestampMs - feedTimestampMs;
        if (latency >= 0) {
            m.totalLatencyMs.addAndGet(latency);
        }
    }

    /** Record a sequence gap (missing tick) detected from a source. */
    public void recordGap(TickSource source, long expectedSeq, long receivedSeq) {
        getOrCreate(source.name()).gapCount.addAndGet(receivedSeq - expectedSeq);
        log.warn("Tick gap detected: source={} expected={} received={}", source, expectedSeq, receivedSeq);
    }

    /** Record a feed processing error. */
    public void recordError(TickSource source, String errorType) {
        getOrCreate(source.name()).errorCount.incrementAndGet();
        meterRegistry.counter("marketdata.errors.total",
                "source", source.name(), "type", errorType).increment();
    }

    /** Snapshot current quality metrics for a source. */
    public Promise<QualitySnapshot> snapshot(TickSource source) {
        return Promise.ofBlocking(executor, () -> {
            var m = getOrCreate(source.name());
            long ticks = m.tickCount.get();
            long gaps  = m.gapCount.get();
            long errors = m.errorCount.get();
            double avgLatency = ticks == 0 ? 0.0 : (double) m.totalLatencyMs.get() / ticks;
            return new QualitySnapshot(source, ticks, avgLatency, gaps, errors);
        });
    }

    // ─── Internal Metrics State ───────────────────────────────────────────────

    private SourceMetrics getOrCreate(String sourceName) {
        return metricsMap.computeIfAbsent(sourceName, name -> {
            var m = new SourceMetrics();
            meterRegistry.gauge("marketdata.tick_rate",
                    Tags.of("source", name), m.tickCount, AtomicLong::doubleValue);
            meterRegistry.gauge("marketdata.gaps_total",
                    Tags.of("source", name), m.gapCount, AtomicLong::doubleValue);
            meterRegistry.gauge("marketdata.latency_ms",
                    Tags.of("source", name), m.totalLatencyMs, AtomicLong::doubleValue);
            return m;
        });
    }

    private static final class SourceMetrics {
        final AtomicLong tickCount      = new AtomicLong(0);
        final AtomicLong totalLatencyMs = new AtomicLong(0);
        final AtomicLong gapCount       = new AtomicLong(0);
        final AtomicLong errorCount     = new AtomicLong(0);
    }

    // ─── Supporting Types ─────────────────────────────────────────────────────

    public record QualitySnapshot(TickSource source, long tickCount,
                                   double avgLatencyMs, long gapCount, long errorCount) {}
}
