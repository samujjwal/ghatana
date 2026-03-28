/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.server.query.AepQueryService;
import com.ghatana.aep.server.report.AepReportingService;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for analytics endpoints (anomaly detection, forecasting).
 *
 * @doc.type class
 * @doc.purpose Analytics and anomaly detection operations
 * @doc.layer product
 * @doc.pattern Service
 */
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private static final String ANOMALY_EVENT_TYPE = "aep.anomaly";
    private static final String KPI_EVENT_TYPE = "aep.kpi";

    private final AepEngine engine;
    @Nullable
    private final DataCloudClient dataCloud;
    @Nullable
    private final DataCloudAnalyticsStore analyticsStore;
    @Nullable
    private final AepQueryService queryService;
    @Nullable
    private final AepReportingService reportingService;
    @Nullable
    private final AnalyticsEventPublisher analyticsEventPublisher;

    public AnalyticsController(AepEngine engine) {
        this(engine, null, null, null, null, null);
    }

    public AnalyticsController(
            AepEngine engine,
            @Nullable DataCloudClient dataCloud,
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable AepQueryService queryService,
            @Nullable AepReportingService reportingService,
            @Nullable AnalyticsEventPublisher analyticsEventPublisher) {
        this.engine = engine;
        this.dataCloud = dataCloud;
        this.analyticsStore = analyticsStore;
        this.queryService = queryService;
        this.reportingService = reportingService;
        this.analyticsEventPublisher = analyticsEventPublisher;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = HttpHelper.mapper().readValue(body, Map.class);

                String tenantId = HttpHelper.resolveTenantId(request, data);
                List<Map<String, Object>> eventsData =
                    (List<Map<String, Object>>) data.getOrDefault("events", List.of());

                List<AepEngine.Event> events = eventsData.stream()
                    .map(e -> new AepEngine.Event(
                        (String) e.getOrDefault("type", "unknown"),
                        (Map<String, Object>) e.getOrDefault("payload", Map.of()),
                        Map.of(),
                        Instant.now()
                    ))
                    .toList();

                return engine.detectAnomalies(tenantId, events)
                    .then(anomalies -> persistDetectedAnomalies(tenantId, anomalies)
                        .map(v -> HttpHelper.jsonResponse(Map.of(
                            "anomalies", anomalies,
                            "count", anomalies.size(),
                            "timestamp", Instant.now().toString()
                        ))));
            } catch (Exception e) {
                log.error("Error detecting anomalies", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read anomaly detection body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleForecast(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = HttpHelper.mapper().readValue(body, Map.class);

                String tenantId = HttpHelper.resolveTenantId(request, data);
                String metric = (String) data.getOrDefault("metric", "default");
                List<Map<String, Object>> pointsData =
                    (List<Map<String, Object>>) data.getOrDefault("points", List.of());

                List<AepEngine.DataPoint> points = pointsData.stream()
                    .map(p -> new AepEngine.DataPoint(
                        Instant.parse((String) p.get("timestamp")),
                        ((Number) p.get("value")).doubleValue()
                    ))
                    .toList();

                AepEngine.TimeSeriesData tsData = new AepEngine.TimeSeriesData(metric, points);

                return engine.forecast(tenantId, tsData)
                    .map(forecast -> HttpHelper.jsonResponse(Map.of(
                        "metric", forecast.metric(),
                        "predictions", forecast.predictions(),
                        "confidence", forecast.confidence(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error forecasting", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read forecast body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveKpi(HttpRequest request) {
        if (analyticsStore == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Analytics store not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> data = HttpHelper.mapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, data);
                DataCloudAnalyticsStore.KpiSnapshot snapshot = new DataCloudAnalyticsStore.KpiSnapshot(
                    null,
                    String.valueOf(data.get("kpiName")),
                    ((Number) data.getOrDefault("value", 0.0)).doubleValue(),
                    String.valueOf(data.getOrDefault("unit", "count")),
                    parseInstantValue(data.get("capturedAt"), Instant.now()),
                    rawStringList(data.get("tags")),
                    asNullableDouble(data.get("previousValue")),
                    asNullableDouble(data.get("changePercent"))
                );

                return analyticsStore.saveKpiSnapshot(tenantId, snapshot)
                    .then(saved -> emitKpiEvent(tenantId, saved)
                        .map(v -> HttpHelper.jsonResponse(Map.of(
                            "kpi", saved,
                            "timestamp", Instant.now().toString()
                        ))));
            } catch (Exception e) {
                log.error("Error saving KPI snapshot", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid KPI request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read KPI body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    public Promise<HttpResponse> handleQueryAnomalies(HttpRequest request) {
        if (analyticsStore == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Analytics store not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String severity = request.getQueryParameter("severity");
        Instant from = parseInstantParam(request.getQueryParameter("from"));
        Instant to = parseInstantParam(request.getQueryParameter("to"));
        int limit = parseIntParam(request.getQueryParameter("limit"), 100);
        return analyticsStore.queryAnomalies(tenantId, severity, from, to, limit)
            .map(anomalies -> HttpHelper.jsonResponse(Map.of(
                "anomalies", anomalies,
                "count", anomalies.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    public Promise<HttpResponse> handleQueryKpis(HttpRequest request) {
        if (analyticsStore == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Analytics store not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String kpiName = request.getQueryParameter("kpiName");
        Instant from = parseInstantParam(request.getQueryParameter("from"));
        Instant to = parseInstantParam(request.getQueryParameter("to"));
        int limit = parseIntParam(request.getQueryParameter("limit"), 200);
        return analyticsStore.queryKpiSnapshots(tenantId, kpiName, from, to, limit)
            .map(kpis -> HttpHelper.jsonResponse(Map.of(
                "kpis", kpis,
                "count", kpis.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    public Promise<HttpResponse> handleQueryMetrics(HttpRequest request) {
        if (analyticsStore == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Analytics store not configured"));
        }
        String tenantId = HttpHelper.resolveTenantId(request);
        String entityId = request.getQueryParameter("entityId");
        String metricName = request.getQueryParameter("metricName");
        Instant from = parseInstantParam(request.getQueryParameter("from"));
        Instant to = parseInstantParam(request.getQueryParameter("to"));
        int limit = parseIntParam(request.getQueryParameter("limit"), 500);
        return analyticsStore.queryMetrics(tenantId, entityId, metricName, from, to, limit)
            .map(metrics -> HttpHelper.jsonResponse(Map.of(
                "metrics", metrics,
                "count", metrics.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSaveMetrics(HttpRequest request) {
        if (analyticsStore == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Analytics store not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> data = HttpHelper.mapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, data);
                List<Map<String, Object>> rawMetrics =
                    (List<Map<String, Object>>) data.getOrDefault("metrics", List.of());
                List<DataCloudAnalyticsStore.MetricDataPoint> metrics = rawMetrics.stream()
                    .map(this::toMetricDataPoint)
                    .toList();

                return analyticsStore.saveMetricsBatch(tenantId, metrics)
                    .map(saved -> HttpHelper.jsonResponse(Map.of(
                        "metrics", saved,
                        "count", saved.size(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error saving metrics", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid metrics request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read metrics body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsQuery(HttpRequest request) {
        if (queryService == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Advanced query service not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> data = HttpHelper.mapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, data);
                String collection = normalizeCollection(String.valueOf(data.get("collection")));
                AepQueryService.QuerySpec.Builder builder = AepQueryService.QuerySpec.builder()
                    .offset(parseIntValue(data.get("offset"), 0))
                    .limit(parseIntValue(data.get("limit"), 50));

                for (Map<String, Object> filter : rawMapList(data.get("filters"))) {
                    builder.filter(toFilter(filter));
                }
                for (Map<String, Object> sort : rawMapList(data.get("sorts"))) {
                    builder.sort(toSort(sort));
                }
                AepQueryService.QuerySpec spec = builder.build();

                return switch (collection) {
                    case AepQueryService.COLLECTION_PATTERNS -> queryService.queryPatterns(tenantId, spec)
                        .map(result -> queryResponse(collection, result));
                    case AepQueryService.COLLECTION_PIPELINES -> queryService.queryPipelines(tenantId, spec)
                        .map(result -> queryResponse(collection, result));
                    case AepQueryService.COLLECTION_ANOMALIES -> queryService.queryAnomalies(tenantId, spec)
                        .map(result -> queryResponse(collection, result));
                    case AepQueryService.COLLECTION_KPI -> queryService.queryKpis(tenantId, spec)
                        .map(result -> queryResponse(collection, result));
                    default -> Promise.of(HttpHelper.errorResponse(400, "Unsupported query collection: " + collection));
                };
            } catch (Exception e) {
                log.error("Error running analytics query", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid query request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read analytics query body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsAggregate(HttpRequest request) {
        if (queryService == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Advanced query service not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> data = HttpHelper.mapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, data);
                String collection = normalizeCollection(String.valueOf(data.get("collection")));
                List<DataCloudClient.Filter> filters = rawMapList(data.get("filters")).stream()
                    .map(this::toFilter)
                    .toList();
                AepQueryService.AggregateSpec spec = new AepQueryService.AggregateSpec(
                    filters,
                    rawString(data.get("groupByField")),
                    rawString(data.get("sumField")),
                    rawString(data.get("minMaxField")),
                    rawString(data.get("distinctField")),
                    parseIntValue(data.get("sampleLimit"), 1_000)
                );
                return queryService.aggregate(tenantId, collection, spec)
                    .map(result -> HttpHelper.jsonResponse(Map.of(
                        "collection", collection,
                        "result", result,
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error running aggregation", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid aggregate request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read aggregate body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateReport(HttpRequest request) {
        if (reportingService == null) {
            return Promise.of(HttpHelper.errorResponse(503, "Reporting service not configured"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> data = HttpHelper.mapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, data);
                AepReportingService.ReportType reportType = AepReportingService.ReportType.valueOf(
                    String.valueOf(data.get("reportType")).toUpperCase());
                AepReportingService.ReportRequest reportRequest = new AepReportingService.ReportRequest(
                    tenantId,
                    reportType,
                    parseInstantValue(data.get("from"), null),
                    parseInstantValue(data.get("to"), null)
                );
                return reportingService.generate(reportRequest)
                    .map(report -> HttpHelper.jsonResponse(Map.of(
                        "report", report,
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error creating report", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid report request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read report body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<Void> persistDetectedAnomalies(String tenantId, List<AepEngine.Anomaly> anomalies) {
        if (anomalies.isEmpty() || (analyticsStore == null && dataCloud == null)) {
            return Promise.complete();
        }
        List<Promise<Void>> writes = new ArrayList<>(anomalies.size());
        for (AepEngine.Anomaly anomaly : anomalies) {
            DataCloudAnalyticsStore.AnomalyRecord record = new DataCloudAnalyticsStore.AnomalyRecord(
                null,
                anomaly.anomalyType(),
                severityForScore(anomaly.score()),
                anomaly.score(),
                "Detected from event type " + String.valueOf(anomaly.details().getOrDefault("event_type", "unknown")),
                anomaly.eventId(),
                null,
                Instant.now(),
                false
            );
            Map<String, Object> payload = Map.of(
                "anomalyType", record.anomalyType(),
                "severity", record.severity(),
                "score", record.score(),
                "description", record.description(),
                "entityId", Objects.toString(record.entityId(), ""),
                "detectedAt", record.detectedAt().toString()
            );
            Promise<Void> persist = analyticsStore != null
                ? analyticsStore.saveAnomaly(tenantId, record).map(saved -> (Void) null)
                : Promise.complete();
            Promise<Void> eventWrite = dataCloud != null
                ? dataCloud.appendEvent(tenantId, DataCloudClient.Event.of(ANOMALY_EVENT_TYPE, payload)).map(offset -> (Void) null)
                : Promise.complete();
            writes.add(persist.then(v -> eventWrite)
                .map(v -> {
                    if (analyticsEventPublisher != null) {
                        analyticsEventPublisher.publish(tenantId, "analytics.anomaly", payload);
                    }
                    return null;
                }));
        }
        return Promises.all(writes).map(v -> null);
    }

    private Promise<Void> emitKpiEvent(String tenantId, DataCloudAnalyticsStore.KpiSnapshot snapshot) {
        if (dataCloud == null && analyticsEventPublisher == null) {
            return Promise.complete();
        }
        Map<String, Object> payload = Map.of(
            "kpiName", snapshot.kpiName(),
            "value", snapshot.value(),
            "unit", snapshot.unit(),
            "capturedAt", snapshot.capturedAt().toString(),
            "tags", snapshot.tags()
        );
        Promise<Void> eventWrite = dataCloud != null
            ? dataCloud.appendEvent(tenantId, DataCloudClient.Event.of(KPI_EVENT_TYPE, payload)).map(offset -> (Void) null)
            : Promise.complete();
        return eventWrite.map(v -> {
            if (analyticsEventPublisher != null) {
                analyticsEventPublisher.publish(tenantId, "analytics.kpi", payload);
            }
            return null;
        });
    }

    private DataCloudAnalyticsStore.MetricDataPoint toMetricDataPoint(Map<String, Object> metric) {
        return new DataCloudAnalyticsStore.MetricDataPoint(
            null,
            rawString(metric.get("entityId")),
            String.valueOf(metric.get("metricName")),
            ((Number) metric.getOrDefault("value", 0.0)).doubleValue(),
            String.valueOf(metric.getOrDefault("unit", "count")),
            parseInstantValue(metric.get("recordedAt"), Instant.now()),
            rawStringList(metric.get("tags"))
        );
    }

    private HttpResponse queryResponse(String collection, Object result) {
        return HttpHelper.jsonResponse(Map.of(
            "collection", collection,
            "result", result,
            "timestamp", Instant.now().toString()
        ));
    }

    private DataCloudClient.Filter toFilter(Map<String, Object> raw) {
        String field = String.valueOf(raw.get("field"));
        String operator = String.valueOf(raw.getOrDefault("operator", "eq"));
        Object value = raw.get("value");
        return switch (operator.toLowerCase()) {
            case "ne" -> DataCloudClient.Filter.ne(field, value);
            case "gt" -> DataCloudClient.Filter.gt(field, value);
            case "gte" -> DataCloudClient.Filter.gte(field, value);
            case "lt" -> DataCloudClient.Filter.lt(field, value);
            case "lte" -> DataCloudClient.Filter.lte(field, value);
            case "like" -> DataCloudClient.Filter.like(field, String.valueOf(value));
            default -> DataCloudClient.Filter.eq(field, value);
        };
    }

    private DataCloudClient.Sort toSort(Map<String, Object> raw) {
        String field = String.valueOf(raw.get("field"));
        boolean ascending = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("ascending", true)));
        return ascending ? DataCloudClient.Sort.asc(field) : DataCloudClient.Sort.desc(field);
    }

    private String normalizeCollection(String rawCollection) {
        return switch (rawCollection) {
            case "patterns", AepQueryService.COLLECTION_PATTERNS -> AepQueryService.COLLECTION_PATTERNS;
            case "pipelines", AepQueryService.COLLECTION_PIPELINES -> AepQueryService.COLLECTION_PIPELINES;
            case "anomalies", AepQueryService.COLLECTION_ANOMALIES -> AepQueryService.COLLECTION_ANOMALIES;
            case "kpis", AepQueryService.COLLECTION_KPI -> AepQueryService.COLLECTION_KPI;
            case "metrics", AepQueryService.COLLECTION_METRICS -> AepQueryService.COLLECTION_METRICS;
            default -> rawCollection;
        };
    }

    private String severityForScore(double score) {
        if (score >= 0.98) return "CRITICAL";
        if (score >= 0.95) return "HIGH";
        if (score >= 0.90) return "MEDIUM";
        return "LOW";
    }

    private Instant parseInstantParam(String raw) {
        return raw == null || raw.isBlank() ? null : Instant.parse(raw);
    }

    private Instant parseInstantValue(Object raw, Instant fallback) {
        if (raw == null) {
            return fallback;
        }
        return Instant.parse(String.valueOf(raw));
    }

    private int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseIntValue(Object raw, int defaultValue) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            return parseIntParam(String.valueOf(raw), defaultValue);
        }
        return defaultValue;
    }

    private Double asNullableDouble(Object raw) {
        return raw instanceof Number number ? number.doubleValue() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rawMapList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        }
        return List.of();
    }

    private List<String> rawStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String rawString(Object raw) {
        return raw != null ? String.valueOf(raw) : null;
    }

    @FunctionalInterface
    public interface AnalyticsEventPublisher {
        void publish(String tenantId, String eventType, Map<String, Object> payload);
    }
}
