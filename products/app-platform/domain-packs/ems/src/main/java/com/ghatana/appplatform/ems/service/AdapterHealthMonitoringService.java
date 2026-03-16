package com.ghatana.appplatform.ems.service;

import com.ghatana.appplatform.ems.port.ExchangeAdapterPort;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Monitors registered exchange adapters for connectivity, latency, and rejection rates.
 *                Publishes Micrometer metrics and emits alert events on threshold breaches.
 * @doc.layer     Application
 * @doc.pattern   Observer — periodic health evaluation + Micrometer gauges/histograms
 *
 * Metrics published per exchange:
 *   - exchange.connected{exchange}          (gauge: 1.0/0.0)
 *   - exchange.latency.ms{exchange}         (histogram)
 *   - exchange.rejections.total{exchange}   (counter)
 *   - exchange.message.rate{exchange}       (gauge: msgs/sec)
 *
 * Alerts emitted when:
 *   - Adapter disconnects (AdapterDisconnectedEvent)
 *   - P99 latency > 50 ms (AdapterHighLatencyEvent)
 *   - Rejection rate > 5% in a rolling 1-minute window (AdapterRejectionSpikeEvent)
 *
 * Story: D02-013
 */
public class AdapterHealthMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(AdapterHealthMonitoringService.class);
    private static final double LATENCY_ALERT_MS        = 50.0;
    private static final double REJECTION_RATE_THRESHOLD = 0.05;

    private final ConcurrentHashMap<String, ExchangeAdapterPort> adapters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AdapterMetrics>      metrics  = new ConcurrentHashMap<>();
    private final Consumer<Object>                                eventPublisher;
    private final MeterRegistry                                   meterRegistry;

    public AdapterHealthMonitoringService(Consumer<Object> eventPublisher, MeterRegistry meterRegistry) {
        this.eventPublisher = eventPublisher;
        this.meterRegistry  = meterRegistry;
    }

    /**
     * Registers an exchange adapter for health monitoring.
     */
    public void register(ExchangeAdapterPort adapter) {
        String id = adapter.exchangeId();
        adapters.put(id, adapter);
        AdapterMetrics m = new AdapterMetrics(id);
        metrics.put(id, m);

        Gauge.builder("exchange.connected", adapter, a -> a.isConnected() ? 1.0 : 0.0)
                .tag("exchange", id).register(meterRegistry);
        Gauge.builder("exchange.message.rate", m, am -> am.messageRatePerSec())
                .tag("exchange", id).register(meterRegistry);

        log.info("AdapterHealthMonitor: registered adapter exchangeId={}", id);
    }

    /**
     * Records a message send or receive event for latency and throughput tracking.
     *
     * @param exchangeId  the exchange adapter ID
     * @param latencyMs   round-trip or processing latency in milliseconds
     */
    public void recordMessage(String exchangeId, double latencyMs) {
        AdapterMetrics m = metrics.get(exchangeId);
        if (m == null) return;

        m.recordMessage(latencyMs);
        meterRegistry.timer("exchange.latency.ms", "exchange", exchangeId)
                .record(java.time.Duration.ofMillis((long) latencyMs));

        if (latencyMs > LATENCY_ALERT_MS) {
            log.warn("AdapterHealthMonitor: high latency detected exchange={} latencyMs={}", exchangeId, latencyMs);
            eventPublisher.accept(new AdapterHighLatencyEvent(exchangeId, latencyMs));
        }
    }

    /**
     * Records an order rejection from the exchange for rate tracking.
     */
    public void recordRejection(String exchangeId) {
        AdapterMetrics m = metrics.get(exchangeId);
        if (m == null) return;

        m.recordRejection();
        meterRegistry.counter("exchange.rejections.total", "exchange", exchangeId).increment();

        double rate = m.rejectionRate();
        if (rate > REJECTION_RATE_THRESHOLD) {
            log.warn("AdapterHealthMonitor: rejection spike exchange={} rate={}%",
                    exchangeId, String.format("%.1f", rate * 100));
            eventPublisher.accept(new AdapterRejectionSpikeEvent(exchangeId, rate));
        }
    }

    /**
     * Evaluates all adapter connections; emits disconnect events for unhealthy adapters.
     * Should be called periodically (e.g. every 10 seconds via a scheduler).
     */
    public void evaluate() {
        adapters.forEach((id, adapter) -> {
            boolean ok = adapter.isConnected();
            AdapterMetrics m = metrics.get(id);
            if (m == null) return;

            boolean wasConnected = m.lastKnownConnected;
            m.lastKnownConnected = ok;

            if (wasConnected && !ok) {
                log.error("AdapterHealthMonitor: adapter disconnected exchangeId={}", id);
                eventPublisher.accept(new AdapterDisconnectedEvent(id, Instant.now()));
            } else if (!wasConnected && ok) {
                log.info("AdapterHealthMonitor: adapter reconnected exchangeId={}", id);
                eventPublisher.accept(new AdapterReconnectedEvent(id, Instant.now()));
            }
        });
    }

    /**
     * Returns a health snapshot for all registered adapters (for the admin API).
     */
    public List<AdapterHealthSnapshot> getAllAdapterStatuses() {
        List<AdapterHealthSnapshot> result = new ArrayList<>();
        adapters.forEach((id, adapter) -> {
            AdapterMetrics m = metrics.getOrDefault(id, new AdapterMetrics(id));
            result.add(new AdapterHealthSnapshot(
                    id, adapter.isConnected(), m.totalMessages,
                    m.totalRejections, m.avgLatencyMs(), Instant.now()));
        });
        return result;
    }

    // ─── Internal metrics holder ──────────────────────────────────────────────

    private static class AdapterMetrics {
        final String exchangeId;
        volatile boolean lastKnownConnected = false;
        long totalMessages   = 0;
        long totalRejections = 0;
        double latencySum    = 0.0;
        long windowStart     = System.currentTimeMillis();
        long windowMessages  = 0;
        long windowRejections = 0;

        AdapterMetrics(String exchangeId) { this.exchangeId = exchangeId; }

        synchronized void recordMessage(double latencyMs) {
            totalMessages++;
            latencySum += latencyMs;
            maybeRollWindow();
            windowMessages++;
        }

        synchronized void recordRejection() {
            totalRejections++;
            maybeRollWindow();
            windowRejections++;
        }

        synchronized double rejectionRate() {
            maybeRollWindow();
            if (windowMessages == 0) return 0.0;
            return (double) windowRejections / windowMessages;
        }

        synchronized double messageRatePerSec() {
            long elapsed = Math.max(1, (System.currentTimeMillis() - windowStart) / 1000);
            return (double) windowMessages / elapsed;
        }

        double avgLatencyMs() {
            return totalMessages == 0 ? 0.0 : latencySum / totalMessages;
        }

        private void maybeRollWindow() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 60_000) {
                windowStart     = now;
                windowMessages  = 0;
                windowRejections = 0;
            }
        }
    }

    // ─── Domain records & events ──────────────────────────────────────────────

    public record AdapterHealthSnapshot(
            String exchangeId,
            boolean connected,
            long totalMessages,
            long totalRejections,
            double avgLatencyMs,
            Instant checkedAt
    ) {}

    public record AdapterDisconnectedEvent(String exchangeId, Instant detectedAt) {}
    public record AdapterReconnectedEvent(String exchangeId, Instant detectedAt) {}
    public record AdapterHighLatencyEvent(String exchangeId, double latencyMs) {}
    public record AdapterRejectionSpikeEvent(String exchangeId, double rejectionRate) {}
}
