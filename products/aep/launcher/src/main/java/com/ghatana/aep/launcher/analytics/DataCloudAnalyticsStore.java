/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.analytics;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.datacloud.DataCloudClient.Sort;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud backed analytics store providing advanced query capabilities for
 * AEP analytics data — KPI snapshots, anomaly detection results, and
 * aggregated time-series metrics.
 *
 * <h3>Collections</h3>
 * <ul>
 *   <li>{@value #KPI_COLLECTION} — tenant-scoped KPI snapshots with full history</li>
 *   <li>{@value #ANOMALY_COLLECTION} — detected anomaly records with severity + context</li>
 *   <li>{@value #METRICS_COLLECTION} — raw + pre-aggregated metric data points</li>
 * </ul>
 *
 * <h3>Query Capabilities</h3>
 * <ul>
 *   <li>Time-range queries on all collections</li>
 *   <li>Severity filtering on anomalies</li>
 *   <li>KPI name + time-window aggregation</li>
 *   <li>Pagination with offset/limit</li>
 *   <li>Cross-metric correlation by entity ID</li>
 * </ul>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>All writes use {@code data.get("id")} as the DataCloud entity key
 *       so we maintain stable UUIDs that survive read-modify-write cycles.</li>
 *   <li>All reads return immutable value objects — callers cannot mutate stored state.</li>
 *   <li>All I/O is Promise-based; the ActiveJ event loop is never blocked.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed analytics store for KPIs, anomalies, and metrics
 * @doc.layer product
 * @doc.pattern Repository, Adapter
 * @doc.gaa.memory episodic
 * @since 1.0.0
 */
public final class DataCloudAnalyticsStore {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAnalyticsStore.class);

    /** DataCloud collection for KPI snapshots. */
    public static final String KPI_COLLECTION = "aep_kpi_snapshots";
    /** DataCloud collection for anomaly detection results. */
    public static final String ANOMALY_COLLECTION = "aep_anomalies";
    /** DataCloud collection for aggregated metric data points. */
    public static final String METRICS_COLLECTION = "aep_metrics";

    private final DataCloudClient client;

    /**
     * Constructs a store backed by the given Data-Cloud client.
     *
     * @param client the Data-Cloud client; must not be {@code null}
     */
    public DataCloudAnalyticsStore(DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // =========================================================================
    // KPI Snapshots
    // =========================================================================

    /**
     * Persists a KPI snapshot for the given tenant.
     *
     * @param tenantId  tenant identifier
     * @param snapshot  KPI snapshot to store
     * @return promise of the persisted snapshot with assigned ID
     */
    public Promise<KpiSnapshot> saveKpiSnapshot(String tenantId, KpiSnapshot snapshot) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(snapshot, "snapshot");
        String id = snapshot.id() != null ? snapshot.id() : UUID.randomUUID().toString();
        Map<String, Object> data = kpiToData(id, tenantId, snapshot);
        return client.save(tenantId, KPI_COLLECTION, data)
                .map(entity -> toKpiSnapshot(entity))
                .whenException(e -> log.error("[analytics-store] saveKpiSnapshot failed tenant={} kpi={}: {}",
                        tenantId, snapshot.kpiName(), e.getMessage(), e));
    }

    /**
     * Queries KPI snapshots for a tenant within an optional time range.
     *
     * @param tenantId  tenant identifier
     * @param kpiName   KPI name filter (null = all KPIs)
     * @param from      start of time range (inclusive, null = unbounded)
     * @param to        end of time range (inclusive, null = now)
     * @param limit     maximum results (0 = use default 200)
     * @return promise of matching KPI snapshots, ordered by most recent first
     */
    public Promise<List<KpiSnapshot>> queryKpiSnapshots(
            String tenantId, String kpiName, Instant from, Instant to, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<Filter> filters = new ArrayList<>();
        if (kpiName != null && !kpiName.isBlank()) {
            filters.add(Filter.eq("kpiName", kpiName));
        }
        if (from != null) {
            filters.add(Filter.gte("capturedAt", from.toString()));
        }
        if (to != null) {
            filters.add(Filter.lte("capturedAt", to.toString()));
        }
        int effectiveLimit = limit > 0 ? limit : 200;
        Query query = Query.builder()
                .filters(filters)
                .sorts(List.of(Sort.desc("capturedAt")))
                .limit(effectiveLimit)
                .build();
        return client.query(tenantId, KPI_COLLECTION, query)
                .map(entities -> entities.stream().map(this::toKpiSnapshot).toList())
                .whenException(e -> log.error("[analytics-store] queryKpiSnapshots failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    /**
     * Retrieves the latest KPI snapshot for a specific KPI name.
     *
     * @param tenantId tenant identifier
     * @param kpiName  KPI name
     * @return promise of the latest snapshot or empty
     */
    public Promise<Optional<KpiSnapshot>> getLatestKpi(String tenantId, String kpiName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kpiName, "kpiName");
        return queryKpiSnapshots(tenantId, kpiName, null, null, 1)
                .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
    }

    /**
     * Deletes all KPI snapshots older than the given cutoff for a tenant.
     *
     * @param tenantId  tenant identifier
     * @param olderThan cutoff timestamp
     * @return promise of the count of deleted entries
     */
    public Promise<Integer> purgeOldKpiSnapshots(String tenantId, Instant olderThan) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(olderThan, "olderThan");
        Query query = Query.builder()
                .filter(Filter.lt("capturedAt", olderThan.toString()))
                .limit(10_000)
                .build();
        return client.query(tenantId, KPI_COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.of(0);
                    }
                    List<Promise<Void>> deletes = new ArrayList<>();
                    for (Entity e : entities) {
                        deletes.add(client.delete(tenantId, KPI_COLLECTION, e.id()));
                    }
                    return Promises.all(deletes).map(v -> entities.size());
                })
                .whenException(e -> log.error("[analytics-store] purgeOldKpiSnapshots failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    // =========================================================================
    // Anomaly Records
    // =========================================================================

    /**
     * Persists a detected anomaly for later querying and alerting.
     *
     * @param tenantId tenant identifier
     * @param anomaly  the detected anomaly record
     * @return promise of the persisted record with assigned ID
     */
    public Promise<AnomalyRecord> saveAnomaly(String tenantId, AnomalyRecord anomaly) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(anomaly, "anomaly");
        String id = anomaly.id() != null ? anomaly.id() : UUID.randomUUID().toString();
        Map<String, Object> data = anomalyToData(id, tenantId, anomaly);
        return client.save(tenantId, ANOMALY_COLLECTION, data)
                .map(entity -> toAnomalyRecord(entity))
                .whenException(e -> log.error("[analytics-store] saveAnomaly failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    /**
     * Queries anomaly records for a tenant with optional filters.
     *
     * @param tenantId  tenant identifier
     * @param severity  severity filter (null = all severities)
     * @param from      start of time range (null = unbounded)
     * @param to        end of time range (null = now)
     * @param limit     maximum results
     * @return promise of anomaly records, newest first
     */
    public Promise<List<AnomalyRecord>> queryAnomalies(
            String tenantId, String severity, Instant from, Instant to, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<Filter> filters = new ArrayList<>();
        if (severity != null && !severity.isBlank()) {
            filters.add(Filter.eq("severity", severity.toUpperCase()));
        }
        if (from != null) {
            filters.add(Filter.gte("detectedAt", from.toString()));
        }
        if (to != null) {
            filters.add(Filter.lte("detectedAt", to.toString()));
        }
        int effectiveLimit = limit > 0 ? limit : 100;
        Query query = Query.builder()
                .filters(filters)
                .sorts(List.of(Sort.desc("detectedAt")))
                .limit(effectiveLimit)
                .build();
        return client.query(tenantId, ANOMALY_COLLECTION, query)
                .map(entities -> entities.stream().map(this::toAnomalyRecord).toList())
                .whenException(e -> log.error("[analytics-store] queryAnomalies failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    /**
     * Counts unresolved anomalies above a given severity for the tenant.
     *
     * @param tenantId tenant identifier
     * @param severity minimum severity (e.g. "HIGH", "CRITICAL")
     * @return promise of the count
     */
    public Promise<Long> countUnresolvedAnomalies(String tenantId, String severity) {
        Objects.requireNonNull(tenantId, "tenantId");
        return queryAnomalies(tenantId, severity, null, null, 10_000)
                .map(list -> list.stream()
                        .filter(a -> !a.resolved())
                        .count());
    }

    /**
     * Marks an anomaly as resolved.
     *
     * @param tenantId  tenant identifier
     * @param anomalyId anomaly record ID
     * @param resolvedBy identifier of the resolver (user/system)
     * @return promise completing when persisted
     */
    public Promise<Void> resolveAnomaly(String tenantId, String anomalyId, String resolvedBy) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(anomalyId, "anomalyId");
        return client.findById(tenantId, ANOMALY_COLLECTION, anomalyId)
                .then(optEntity -> {
                    if (optEntity.isEmpty()) {
                        return Promise.ofException(
                                new IllegalArgumentException("Anomaly not found: " + anomalyId));
                    }
                    Map<String, Object> updated = new HashMap<>(optEntity.get().data());
                    updated.put("resolved", true);
                    updated.put("resolvedBy", resolvedBy != null ? resolvedBy : "system");
                    updated.put("resolvedAt", Instant.now().toString());
                    return client.save(tenantId, ANOMALY_COLLECTION, updated)
                            .map(e -> (Void) null);
                })
                .whenException(e -> log.error("[analytics-store] resolveAnomaly failed id={}: {}",
                        anomalyId, e.getMessage(), e));
    }

    // =========================================================================
    // Metrics Data Points
    // =========================================================================

    /**
     * Persists a metric data point for trend analysis and forecasting.
     *
     * @param tenantId  tenant identifier
     * @param dataPoint metric data point
     * @return promise of the persisted data point
     */
    public Promise<MetricDataPoint> saveMetricDataPoint(String tenantId, MetricDataPoint dataPoint) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataPoint, "dataPoint");
        String id = dataPoint.id() != null ? dataPoint.id() : UUID.randomUUID().toString();
        Map<String, Object> data = metricToData(id, tenantId, dataPoint);
        return client.save(tenantId, METRICS_COLLECTION, data)
                .map(entity -> toMetricDataPoint(entity))
                .whenException(e -> log.error("[analytics-store] saveMetricDataPoint failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    /**
     * Queries metric data points for a tenant with entity and name filters.
     *
     * @param tenantId   tenant identifier
     * @param entityId   entity the metric belongs to (null = all entities)
     * @param metricName metric name filter (null = all metrics)
     * @param from       time range start (null = unbounded)
     * @param to         time range end (null = now)
     * @param limit      maximum results
     * @return promise of metric data points, oldest first (suitable for time-series)
     */
    public Promise<List<MetricDataPoint>> queryMetrics(
            String tenantId, String entityId, String metricName,
            Instant from, Instant to, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<Filter> filters = new ArrayList<>();
        if (entityId != null && !entityId.isBlank()) {
            filters.add(Filter.eq("entityId", entityId));
        }
        if (metricName != null && !metricName.isBlank()) {
            filters.add(Filter.eq("metricName", metricName));
        }
        if (from != null) {
            filters.add(Filter.gte("recordedAt", from.toString()));
        }
        if (to != null) {
            filters.add(Filter.lte("recordedAt", to.toString()));
        }
        int effectiveLimit = limit > 0 ? limit : 500;
        Query query = Query.builder()
                .filters(filters)
                .sorts(List.of(Sort.asc("recordedAt")))
                .limit(effectiveLimit)
                .build();
        return client.query(tenantId, METRICS_COLLECTION, query)
                .map(entities -> entities.stream().map(this::toMetricDataPoint).toList())
                .whenException(e -> log.error("[analytics-store] queryMetrics failed tenant={}: {}",
                        tenantId, e.getMessage(), e));
    }

    /**
     * Batch-saves multiple metric data points in parallel.
     *
     * <p>Partial failures are tolerated — failed saves are logged but do not
     * abort the batch. The returned list contains only successfully persisted points.
     *
     * @param tenantId   tenant identifier
     * @param dataPoints metric data points to persist
     * @return promise of successfully persisted data points
     */
    public Promise<List<MetricDataPoint>> saveMetricsBatch(
            String tenantId, List<MetricDataPoint> dataPoints) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dataPoints, "dataPoints");
        if (dataPoints.isEmpty()) {
            return Promise.of(List.of());
        }
        List<Promise<Optional<MetricDataPoint>>> saves = new ArrayList<>(dataPoints.size());
        for (MetricDataPoint dp : dataPoints) {
            String id = dp.id() != null ? dp.id() : UUID.randomUUID().toString();
            Map<String, Object> data = metricToData(id, tenantId, dp);
            saves.add(
                client.save(tenantId, METRICS_COLLECTION, data)
                    .map(entity -> Optional.of(toMetricDataPoint(entity)))
                    .then(
                        v -> Promise.of(v),
                        e -> {
                            log.warn("[analytics-store] batch save failed for metric={}: {}",
                                    dp.metricName(), e.getMessage());
                            return Promise.of(Optional.empty());
                        }
                    )
            );
        }
        return Promises.toList(saves)
                .map(optionals -> optionals.stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList());
    }

    // =========================================================================
    // Serialization helpers — KPI
    // =========================================================================

    private Map<String, Object> kpiToData(String id, String tenantId, KpiSnapshot s) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("tenantId", tenantId);
        data.put("kpiName", s.kpiName());
        data.put("value", s.value());
        data.put("unit", s.unit() != null ? s.unit() : "");
        data.put("tags", s.tags() != null ? String.join(",", s.tags()) : "");
        data.put("capturedAt", (s.capturedAt() != null ? s.capturedAt() : Instant.now()).toString());
        if (s.previousValue() != null) {
            data.put("previousValue", s.previousValue());
        }
        if (s.changePercent() != null) {
            data.put("changePercent", s.changePercent());
        }
        return data;
    }

    private KpiSnapshot toKpiSnapshot(Entity entity) {
        Map<String, Object> d = entity.data();
        return new KpiSnapshot(
                (String) d.get("id"),
                (String) d.get("kpiName"),
                toDouble(d.get("value")),
                (String) d.getOrDefault("unit", ""),
                parseInstant(d.get("capturedAt")),
                splitTags((String) d.getOrDefault("tags", "")),
                toDouble(d.get("previousValue")),
                toDouble(d.get("changePercent"))
        );
    }

    // =========================================================================
    // Serialization helpers — Anomaly
    // =========================================================================

    private Map<String, Object> anomalyToData(String id, String tenantId, AnomalyRecord a) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("tenantId", tenantId);
        data.put("anomalyType", a.anomalyType());
        data.put("severity", a.severity() != null ? a.severity().toUpperCase() : "UNKNOWN");
        data.put("score", a.score());
        data.put("description", a.description() != null ? a.description() : "");
        data.put("entityId", a.entityId() != null ? a.entityId() : "");
        data.put("patternId", a.patternId() != null ? a.patternId() : "");
        data.put("detectedAt", (a.detectedAt() != null ? a.detectedAt() : Instant.now()).toString());
        data.put("resolved", false);
        return data;
    }

    private AnomalyRecord toAnomalyRecord(Entity entity) {
        Map<String, Object> d = entity.data();
        return new AnomalyRecord(
                (String) d.get("id"),
                (String) d.getOrDefault("anomalyType", "UNKNOWN"),
                (String) d.getOrDefault("severity", "UNKNOWN"),
                toDouble(d.get("score")),
                (String) d.getOrDefault("description", ""),
                (String) d.getOrDefault("entityId", null),
                (String) d.getOrDefault("patternId", null),
                parseInstant(d.get("detectedAt")),
                Boolean.TRUE.equals(d.get("resolved"))
        );
    }

    // =========================================================================
    // Serialization helpers — Metrics
    // =========================================================================

    private Map<String, Object> metricToData(String id, String tenantId, MetricDataPoint dp) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("tenantId", tenantId);
        data.put("entityId", dp.entityId() != null ? dp.entityId() : "");
        data.put("metricName", dp.metricName());
        data.put("value", dp.value());
        data.put("unit", dp.unit() != null ? dp.unit() : "");
        data.put("recordedAt", (dp.recordedAt() != null ? dp.recordedAt() : Instant.now()).toString());
        if (dp.tags() != null && !dp.tags().isEmpty()) {
            data.put("tags", String.join(",", dp.tags()));
        }
        return data;
    }

    private MetricDataPoint toMetricDataPoint(Entity entity) {
        Map<String, Object> d = entity.data();
        return new MetricDataPoint(
                (String) d.get("id"),
                (String) d.getOrDefault("entityId", null),
                (String) d.get("metricName"),
                toDouble(d.get("value")),
                (String) d.getOrDefault("unit", ""),
                parseInstant(d.get("recordedAt")),
                splitTags((String) d.getOrDefault("tags", ""))
        );
    }

    // =========================================================================
    // Shared deserialization utilities
    // =========================================================================

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static Instant parseInstant(Object v) {
        if (v == null) return Instant.now();
        try { return Instant.parse(v.toString()); } catch (Exception e) { return Instant.now(); }
    }

    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(","));
    }

    // =========================================================================
    // Value objects — part of the public API
    // =========================================================================

    /**
     * Immutable KPI snapshot captured at a point in time.
     *
     * @param id            entity ID in DataCloud (null before save)
     * @param kpiName       KPI identifier (e.g., "events.processed.per.second")
     * @param value         current KPI value
     * @param unit          unit of measure (e.g., "req/s", "%", "ms")
     * @param capturedAt    when the KPI was measured
     * @param tags          searchable labels
     * @param previousValue previous period value (for change analysis)
     * @param changePercent percentage change from previous value
     */
    public record KpiSnapshot(
            String id,
            String kpiName,
            double value,
            String unit,
            Instant capturedAt,
            List<String> tags,
            Double previousValue,
            Double changePercent
    ) {
        /** Factory for a simple named KPI with a scalar value. */
        public static KpiSnapshot of(String kpiName, double value, String unit) {
            return new KpiSnapshot(null, kpiName, value, unit, Instant.now(), List.of(), null, null);
        }
    }

    /**
     * Immutable anomaly detection result record.
     *
     * @param id          entity ID in DataCloud (null before save)
     * @param anomalyType type classification (e.g., "FREQUENCY_SPIKE", "PATTERN_DRIFT")
     * @param severity    severity level: LOW | MEDIUM | HIGH | CRITICAL
     * @param score       anomaly score in [0.0, 1.0]; higher → more anomalous
     * @param description human-readable description
     * @param entityId    the event/entity that triggered the anomaly (nullable)
     * @param patternId   the pattern context (nullable)
     * @param detectedAt  detection timestamp
     * @param resolved    whether this anomaly has been resolved
     */
    public record AnomalyRecord(
            String id,
            String anomalyType,
            String severity,
            double score,
            String description,
            String entityId,
            String patternId,
            Instant detectedAt,
            boolean resolved
    ) {
        /** Factory for a new unresolved anomaly. */
        public static AnomalyRecord of(
                String anomalyType, String severity, double score, String description) {
            return new AnomalyRecord(null, anomalyType, severity, score, description,
                    null, null, Instant.now(), false);
        }
    }

    /**
     * Immutable metric data point for time-series analysis and forecasting.
     *
     * @param id         entity ID in DataCloud (null before save)
     * @param entityId   owning entity (pattern, pipeline, agent, etc.) — nullable
     * @param metricName metric name (e.g., "cpu.usage", "event.latency.p99")
     * @param value      numeric metric value
     * @param unit       unit of measure
     * @param recordedAt measurement timestamp
     * @param tags       searchable labels
     */
    public record MetricDataPoint(
            String id,
            String entityId,
            String metricName,
            double value,
            String unit,
            Instant recordedAt,
            List<String> tags
    ) {
        /** Factory for a simple named metric with no entity or tags. */
        public static MetricDataPoint of(String metricName, double value, String unit) {
            return new MetricDataPoint(null, null, metricName, value, unit, Instant.now(), List.of());
        }

        /** Factory for an entity-scoped metric. */
        public static MetricDataPoint forEntity(
                String entityId, String metricName, double value, String unit) {
            return new MetricDataPoint(null, entityId, metricName, value, unit, Instant.now(), List.of());
        }
    }
}
