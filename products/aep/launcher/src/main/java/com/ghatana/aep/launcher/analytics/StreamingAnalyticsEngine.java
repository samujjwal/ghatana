/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.analytics;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Event;
import com.ghatana.datacloud.DataCloudClient.Subscription;
import com.ghatana.datacloud.DataCloudClient.TailRequest;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Real-time streaming analytics engine that pushes live AEP events to registered
 * subscribers via Data-Cloud's event-tailing API.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Anomaly streaming</b> — subscribe to real-time anomaly detection results
 *       filtered by severity and/or minimum anomaly score.</li>
 *   <li><b>KPI streaming</b> — subscribe to live KPI snapshot events filtered by
 *       KPI name.</li>
 *   <li><b>Fine-grained filtering</b> — server-side event type routing combined with
 *       client-side payload filtering via {@link AnomalyFilter} and
 *       {@link KpiFilter}.</li>
 *   <li><b>Lifecycle management</b> — individual subscription handles with
 *       {@link #unsubscribe(String)} tear-down and graceful {@link #close()}.</li>
 *   <li><b>Observability</b> — active subscription count tracked via
 *       {@link MetricsCollector}.</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>All public methods are thread-safe. The internal subscription map uses a
 * {@link ConcurrentHashMap}.  Event callbacks are delivered on the Data-Cloud
 * tailing thread and MUST NOT block (they are dispatched synchronously inside the
 * event loop).</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * StreamingAnalyticsEngine engine = new StreamingAnalyticsEngine(dataCloudClient, metrics);
 *
 * // Subscribe to CRITICAL anomalies with score > 0.8
 * AnomalyFilter filter = AnomalyFilter.of("tenant-1", "CRITICAL", 0.8);
 * String subId = engine.subscribeToAnomalies(filter, anomaly -> {
 *     alertingService.send(anomaly);
 * });
 *
 * // Later — cancel the subscription
 * engine.unsubscribe(subId);
 *
 * // Shutdown and cancel all subscriptions
 * engine.close();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Real-time streaming analytics — push-based anomaly and KPI event delivery
 * @doc.layer product
 * @doc.pattern Service, Observer, Facade
 * @doc.gaa.lifecycle perceive
 * @since 1.0.0
 */
public final class StreamingAnalyticsEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StreamingAnalyticsEngine.class);

    // Event type constants — must match what producers write to DataCloud
    static final String EVENT_TYPE_ANOMALY = "aep.anomaly";
    static final String EVENT_TYPE_KPI     = "aep.kpi";

    private static final String METRIC_SUBS_ACTIVE  = "streaming.subscriptions.active";
    private static final String METRIC_EVENTS_RCVD  = "streaming.events.received";
    private static final String METRIC_EVENTS_DROPPED = "streaming.events.dropped";

    private final DataCloudClient dataCloud;
    private final MetricsCollector metrics;

    /** Subscription ID → managed subscription.  ConcurrentHashMap for thread safety. */
    private final ConcurrentHashMap<String, ManagedSubscription> subscriptions =
            new ConcurrentHashMap<>();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates a new engine backed by the given Data-Cloud client and metrics collector.
     *
     * @param dataCloud Data-Cloud client used for event-tailing; must not be {@code null}
     * @param metrics   metrics collector for observability; must not be {@code null}
     */
    public StreamingAnalyticsEngine(DataCloudClient dataCloud, MetricsCollector metrics) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.metrics   = Objects.requireNonNull(metrics, "metrics");
    }

    // =========================================================================
    // Anomaly subscriptions
    // =========================================================================

    /**
     * Subscribes to real-time anomaly detection events for a tenant.
     *
     * <p>Events are sourced via {@link DataCloudClient#tailEvents} from the
     * latest offset. Only events with {@code type = "aep.anomaly"} pass through;
     * additional payload filtering is applied via {@code filter}.
     *
     * @param filter  anomaly filter specifying tenant, minimum severity, and
     *                minimum anomaly score (must not be {@code null})
     * @param handler callback invoked on every matching anomaly; MUST NOT block
     * @return a subscription ID that can be passed to {@link #unsubscribe(String)}
     */
    public String subscribeToAnomalies(AnomalyFilter filter, Consumer<DataCloudAnalyticsStore.AnomalyRecord> handler) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(handler, "handler");

        String subId = UUID.randomUUID().toString();
        TailRequest tailReq = TailRequest.fromLatest();

        Consumer<Event> eventHandler = event -> {
            if (!EVENT_TYPE_ANOMALY.equals(event.type())) {
                return;
            }
            metrics.incrementCounter(METRIC_EVENTS_RCVD, "type", "ANOMALY");
            try {
                DataCloudAnalyticsStore.AnomalyRecord record = toAnomalyRecord(event);
                if (filter.matches(record)) {
                    handler.accept(record);
                }
            } catch (Exception e) {
                metrics.incrementCounter(METRIC_EVENTS_DROPPED, "type", "ANOMALY", "reason", "parse_error");
                log.warn("[streaming-engine] Failed to parse anomaly event: {}", e.getMessage(), e);
            }
        };

        Subscription dcSub = dataCloud.tailEvents(filter.tenantId(), tailReq, eventHandler);
        subscriptions.put(subId, new ManagedSubscription(subId, filter.tenantId(), "ANOMALY", dcSub, Instant.now()));

        metrics.incrementCounter(METRIC_SUBS_ACTIVE, "type", "ANOMALY");
        log.info("[streaming-engine] Subscribed to anomalies tenant='{}' subId='{}' filter={}",
                filter.tenantId(), subId, filter);
        return subId;
    }

    // =========================================================================
    // KPI subscriptions
    // =========================================================================

    /**
     * Subscribes to real-time KPI snapshot events for a tenant.
     *
     * <p>Events are sourced via {@link DataCloudClient#tailEvents} from the
     * latest offset. Only events with {@code type = "aep.kpi"} pass through;
     * KPI name filtering is applied via {@code filter}.
     *
     * @param filter  KPI filter specifying tenant and optional KPI name
     *                (must not be {@code null})
     * @param handler callback invoked on every matching KPI snapshot; MUST NOT block
     * @return a subscription ID that can be passed to {@link #unsubscribe(String)}
     */
    public String subscribeToKPIs(KpiFilter filter, Consumer<DataCloudAnalyticsStore.KpiSnapshot> handler) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(handler, "handler");

        String subId = UUID.randomUUID().toString();
        TailRequest tailReq = TailRequest.fromLatest();

        Consumer<Event> eventHandler = event -> {
            if (!EVENT_TYPE_KPI.equals(event.type())) {
                return;
            }
            metrics.incrementCounter(METRIC_EVENTS_RCVD, "type", "KPI");
            try {
                DataCloudAnalyticsStore.KpiSnapshot snapshot = toKpiSnapshot(event);
                if (filter.matches(snapshot)) {
                    handler.accept(snapshot);
                }
            } catch (Exception e) {
                metrics.incrementCounter(METRIC_EVENTS_DROPPED, "type", "KPI", "reason", "parse_error");
                log.warn("[streaming-engine] Failed to parse KPI event: {}", e.getMessage(), e);
            }
        };

        Subscription dcSub = dataCloud.tailEvents(filter.tenantId(), tailReq, eventHandler);
        subscriptions.put(subId, new ManagedSubscription(subId, filter.tenantId(), "KPI", dcSub, Instant.now()));

        metrics.incrementCounter(METRIC_SUBS_ACTIVE, "type", "KPI");
        log.info("[streaming-engine] Subscribed to KPIs tenant='{}' subId='{}' filter={}",
                filter.tenantId(), subId, filter);
        return subId;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Cancels an individual subscription.
     *
     * <p>If no subscription with the given ID is found, this method is a no-op.
     *
     * @param subscriptionId subscription ID returned by the subscribe methods
     */
    public void unsubscribe(String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        ManagedSubscription managed = subscriptions.remove(subscriptionId);
        if (managed == null) {
            log.debug("[streaming-engine] unsubscribe called for unknown id='{}'", subscriptionId);
            return;
        }
        managed.dcSubscription().cancel();
        log.info("[streaming-engine] Unsubscribed tenant='{}' type='{}' subId='{}'",
                managed.tenantId(), managed.type(), subscriptionId);
    }

    /**
     * Returns an immutable snapshot of all currently active subscription descriptors.
     *
     * @return list of active subscription infos (may be empty; never {@code null})
     */
    public List<SubscriptionInfo> getActiveSubscriptions() {
        List<SubscriptionInfo> result = new ArrayList<>(subscriptions.size());
        for (ManagedSubscription ms : subscriptions.values()) {
            result.add(ms.toInfo());
        }
        return List.copyOf(result);
    }

    /**
     * Cancels ALL active subscriptions and releases resources.
     *
     * <p>Safe to call multiple times.
     */
    @Override
    public void close() {
        int count = subscriptions.size();
        subscriptions.forEach((id, ms) -> {
            try {
                ms.dcSubscription().cancel();
            } catch (Exception e) {
                log.warn("[streaming-engine] Error cancelling subscription '{}': {}", id, e.getMessage());
            }
        });
        subscriptions.clear();
        if (count > 0) {
            log.info("[streaming-engine] Closed — cancelled {} active subscription(s)", count);
        }
    }

    // =========================================================================
    // Event → Domain Object Converters
    // =========================================================================

    private static DataCloudAnalyticsStore.AnomalyRecord toAnomalyRecord(Event event) {
        Map<String, Object> payload = event.payload();
        return new DataCloudAnalyticsStore.AnomalyRecord(
                getString(payload, "id"),
                getString(payload, "anomalyType"),
                getString(payload, "severity"),
                getDouble(payload, "score"),
                getString(payload, "description"),
                getString(payload, "entityId"),
                getString(payload, "patternId"),
                event.timestamp(),
                getBoolean(payload, "resolved")
        );
    }

    private static DataCloudAnalyticsStore.KpiSnapshot toKpiSnapshot(Event event) {
        Map<String, Object> payload = event.payload();
        Object tagsObj = payload.get("tags");
        @SuppressWarnings("unchecked")
        List<String> tags = tagsObj instanceof List<?> rawList
                ? rawList.stream().map(Object::toString).toList()
                : List.of();
        return new DataCloudAnalyticsStore.KpiSnapshot(
                getString(payload, "id"),
                getString(payload, "kpiName"),
                getDouble(payload, "value"),
                getString(payload, "unit"),
                event.timestamp(),
                tags,
                getDoubleOrNull(payload, "previousValue"),
                getDoubleOrNull(payload, "changePercent")
        );
    }

    // =========================================================================
    // Payload helpers
    // =========================================================================

    private static String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static double getDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }

    private static Double getDoubleOrNull(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static boolean getBoolean(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    // =========================================================================
    // Supporting Types
    // =========================================================================

    /**
     * Filter for anomaly streaming subscriptions.
     *
     * @param tenantId       tenant whose events to tail (must not be {@code null})
     * @param minimumSeverity optional minimum severity filter: {@code null} = all severities;
     *                        otherwise one of "LOW", "MEDIUM", "HIGH", "CRITICAL"
     * @param minimumScore   minimum anomaly score [0.0, 1.0]; events below this
     *                       threshold are silently dropped
     *
     * @doc.type record
     * @doc.purpose Immutable anomaly subscription filter
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record AnomalyFilter(
            String tenantId,
            String minimumSeverity,
            double minimumScore
    ) {
        /** Ordered severity levels for comparison. */
        private static final List<String> SEVERITY_ORDER =
                List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

        public AnomalyFilter {
            Objects.requireNonNull(tenantId, "tenantId");
            if (minimumScore < 0.0 || minimumScore > 1.0) {
                throw new IllegalArgumentException("minimumScore must be in [0.0, 1.0]; got " + minimumScore);
            }
        }

        /** Creates a filter that accepts all anomalies for the tenant. */
        public static AnomalyFilter allFor(String tenantId) {
            return new AnomalyFilter(tenantId, null, 0.0);
        }

        /**
         * Creates a filter accepting anomalies at or above the given severity and score.
         *
         * @param tenantId        tenant identifier
         * @param minimumSeverity minimum severity (case-insensitive)
         * @param minimumScore    minimum anomaly score
         */
        public static AnomalyFilter of(String tenantId, String minimumSeverity, double minimumScore) {
            return new AnomalyFilter(tenantId, minimumSeverity == null ? null : minimumSeverity.toUpperCase(), minimumScore);
        }

        /**
         * Returns {@code true} if the given anomaly record satisfies this filter.
         *
         * @param record the anomaly record to test
         */
        public boolean matches(DataCloudAnalyticsStore.AnomalyRecord record) {
            if (record == null) return false;
            if (record.score() < minimumScore) return false;
            if (minimumSeverity == null || minimumSeverity.isBlank()) return true;
            int minIdx = SEVERITY_ORDER.indexOf(minimumSeverity.toUpperCase());
            if (minIdx < 0) return true; // unknown severity — let it through
            int recIdx = SEVERITY_ORDER.indexOf(
                    record.severity() != null ? record.severity().toUpperCase() : "");
            return recIdx >= minIdx;
        }
    }

    /**
     * Filter for KPI streaming subscriptions.
     *
     * @param tenantId  tenant whose events to tail (must not be {@code null})
     * @param kpiName   optional KPI name to restrict to; {@code null} = all KPIs
     *
     * @doc.type record
     * @doc.purpose Immutable KPI subscription filter
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record KpiFilter(
            String tenantId,
            String kpiName
    ) {
        public KpiFilter {
            Objects.requireNonNull(tenantId, "tenantId");
        }

        /** Creates a filter that accepts all KPI events for the tenant. */
        public static KpiFilter allFor(String tenantId) {
            return new KpiFilter(tenantId, null);
        }

        /** Creates a filter accepting only events for the specified KPI. */
        public static KpiFilter forKpi(String tenantId, String kpiName) {
            return new KpiFilter(tenantId, kpiName);
        }

        /**
         * Returns {@code true} if the given KPI snapshot satisfies this filter.
         *
         * @param snapshot the KPI snapshot to test
         */
        public boolean matches(DataCloudAnalyticsStore.KpiSnapshot snapshot) {
            if (snapshot == null) return false;
            if (kpiName == null || kpiName.isBlank()) return true;
            return kpiName.equals(snapshot.kpiName());
        }
    }

    /**
     * Immutable descriptor for an active streaming subscription.
     *
     * @param id        subscription ID
     * @param tenantId  tenant identifier
     * @param type      subscription type: {@code "ANOMALY"} or {@code "KPI"}
     * @param createdAt creation timestamp
     *
     * @doc.type record
     * @doc.purpose Public-facing descriptor for an active streaming subscription
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record SubscriptionInfo(
            String id,
            String tenantId,
            String type,
            Instant createdAt
    ) {}

    /**
     * Internal wrapper pairing a subscription ID with its Data-Cloud handle.
     */
    private record ManagedSubscription(
            String id,
            String tenantId,
            String type,
            Subscription dcSubscription,
            Instant createdAt
    ) {
        SubscriptionInfo toInfo() {
            return new SubscriptionInfo(id, tenantId, type, createdAt);
        }
    }
}
