package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Anomaly;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles entity anomaly-detection HTTP endpoints.
 *
 * <p>Extracted from {@code EntityCrudHandler} to respect the single-responsibility
 * principle (DC-004). Registered in the server via method reference:
 * <pre>{@code
 * .with(HttpMethod.POST, "/api/v1/entities/:collection/anomalies", anomalyHandler::handleDetectAnomalies)
 * }</pre>
 *
 * <p>Returns {@code 501 Not Implemented} when no {@link StatisticalAnomalyDetector} is
 * configured on this instance.
 *
 * @doc.type    class
 * @doc.purpose HTTP handler for statistical anomaly detection on entity collections
 * @doc.layer   product
 * @doc.pattern Handler
 */
public class EntityAnomalyHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityAnomalyHandler.class);

    /** Anomaly event stream name — parallel to {@code __audit}. */
    static final String ANOMALY_STREAM = "__anomalies";

    /** Anomaly event type stored in EventLogStore. */
    static final String ANOMALY_EVENT_TYPE = "ANOMALY_DETECTED";

    /** Maximum anomaly events returned per query. */
    static final int MAX_ANOMALY_QUERY_LIMIT = 200;

    private final StatisticalAnomalyDetector anomalyDetector;
    private final HttpHandlerSupport http;
    /** Optional event log store for durable anomaly persistence. */
    private final EventLogStore eventLogStore;
    /** ObjectMapper for anomaly event serialisation. */
    private final ObjectMapper objectMapper;

    /**
     * Creates an anomaly handler without durable persistence (legacy).
     *
     * @param anomalyDetector the detector; may be {@code null} — handler returns 501 in that case
     * @param http            shared HTTP helpers
     */
    public EntityAnomalyHandler(StatisticalAnomalyDetector anomalyDetector, HttpHandlerSupport http) {
        this(anomalyDetector, http, null, null);
    }

    /**
     * Creates an anomaly handler with optional durable persistence.
     *
     * @param anomalyDetector the detector; may be {@code null}
     * @param http            shared HTTP helpers
     * @param eventLogStore   optional event store for durable anomaly persistence; may be {@code null}
     * @param objectMapper    Jackson mapper for anomaly serialisation; may be {@code null} when eventLogStore is null
     */
    public EntityAnomalyHandler(
            StatisticalAnomalyDetector anomalyDetector,
            HttpHandlerSupport http,
            EventLogStore eventLogStore,
            ObjectMapper objectMapper) {
        this.anomalyDetector = anomalyDetector;
        this.http = http;
        this.eventLogStore = eventLogStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Detects statistical anomalies in an entity collection.
     *
     * <p>Optional JSON body parameters:
     * <ul>
     *   <li>{@code threshold} — Z-score threshold (double, default {@value StatisticalAnomalyDetector#DEFAULT_Z_THRESHOLD})</li>
     *   <li>{@code detectionType} — one of the {@link DetectionType} enum values</li>
     * </ul>
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        if (anomalyDetector == null) {
            return Promise.of(http.serviceUnavailableResponse(
                "Anomaly detection capability is not configured on this server",
                60));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        final String finalTenant     = tenantId;
        final String finalCollection = collection;

        final Map<String, Object> responseEnvelope = Map.of(
                "collection", finalCollection,
                "tenant", finalTenant,
                "timestamp", Instant.now().toString());

        return request.loadBody().then(buf -> {
            try {
                String rawBody = buf.getString(StandardCharsets.UTF_8);

                // Mutable holders so lambda-captured variables remain effectively final.
                double[] threshold   = {StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD};
                DetectionType[] type = {DetectionType.DATA_QUALITY};

                if (rawBody != null && !rawBody.isBlank()) {
                    Map<String, Object> bodyMap = http.objectMapper().readValue(rawBody, Map.class);
                    if (bodyMap.containsKey("threshold")) {
                        Object t = bodyMap.get("threshold");
                        threshold[0] = t instanceof Number n ? n.doubleValue() : Double.parseDouble(t.toString());
                    }
                    if (bodyMap.containsKey("detectionType")) {
                        try {
                            type[0] = DetectionType.valueOf(bodyMap.get("detectionType").toString());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(http.errorResponse(400, "Unknown detectionType: " + bodyMap.get("detectionType")));
                        }
                    }
                }

                AnomalyContext ctx = AnomalyContext.builder()
                        .tenantId(finalTenant)
                        .collectionName(finalCollection)
                        .detectionType(type[0])
                        .threshold(threshold[0])
                        .build();

                return anomalyDetector.detect(ctx)
                        .then(anomalies -> {
                            Map<String, Object> body = new LinkedHashMap<>(responseEnvelope);
                            body.put("count", anomalies.size());
                            body.put("anomalies", anomalies);
                            body.put("persisted", eventLogStore != null);
                            HttpResponse response = http.jsonResponse(body);
                            // Durably persist anomalies to the __anomalies event stream
                            if (eventLogStore != null && !anomalies.isEmpty()) {
                                persistAnomalies(finalTenant, finalCollection, anomalies)
                                        .whenException(e -> log.warn(
                                                "Failed to persist {} anomaly events tenant={} collection={}: {}",
                                                anomalies.size(), finalTenant, finalCollection, e.getMessage()));
                            }
                            return Promise.of(response);
                        })
                        .then(Promise::of, e -> {
                            log.error("Anomaly detection failed tenant={} collection={}", finalTenant, finalCollection, e);
                            return Promise.of(http.errorResponse(500, "Anomaly detection failed: " + e.getMessage()));
                        });
            } catch (Exception e) {
                log.error("Error processing anomaly detection request", e);
                return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/anomalies?collection={}&since={}&limit={} ───────────────

    /**
     * Queries previously detected anomalies from the durable event store.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code collection} — filter by collection name (required)</li>
     *   <li>{@code since} — ISO-8601 start time, defaults to 24 hours ago</li>
     *   <li>{@code limit} — maximum results (default 50, max {@value #MAX_ANOMALY_QUERY_LIMIT})</li>
     * </ul>
     */
    public Promise<HttpResponse> handleQueryAnomalies(HttpRequest request) {
        if (eventLogStore == null) {
            return Promise.of(http.serviceUnavailableResponse(
                "Anomaly event store is not configured — durable persistence unavailable",
                60));
        }

        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String collection = request.getPathParameter("collection");
        if (collection == null || collection.isBlank()) {
            collection = request.getQueryParameter("collection");
        }

        String finalTenantId = tenantId;
        String finalCollection = collection;

        // Parse since
        Instant since;
        try {
            String sinceParam = request.getQueryParameter("since");
            since = sinceParam != null && !sinceParam.isBlank()
                    ? Instant.parse(sinceParam)
                    : Instant.now().minusSeconds(86_400); // 24h default
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Invalid 'since' parameter: expected ISO-8601 instant"));
        }

        // Parse limit
        int limit;
        try {
            String limitParam = request.getQueryParameter("limit");
            limit = limitParam != null ? Math.min(Integer.parseInt(limitParam), MAX_ANOMALY_QUERY_LIMIT) : 50;
            if (limit <= 0) limit = 50;
        } catch (NumberFormatException e) {
            return Promise.of(http.errorResponse(400, "Invalid 'limit' parameter: expected integer"));
        }

        Instant sinceInstant = since;
        int finalLimit = limit;

        TenantContext tenantCtx = TenantContext.of(finalTenantId, Map.of("stream", ANOMALY_STREAM));
        return eventLogStore.readByTimeRange(tenantCtx, sinceInstant, Instant.now(), finalLimit)
                .map(entries -> {
                    // Filter by collection if specified
                    List<Map<String, Object>> results = entries.stream()
                            .filter(e -> {
                                if (finalCollection == null || finalCollection.isBlank()) return true;
                                return finalCollection.equals(e.headers().get("collection"));
                            })
                            .map(e -> {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("eventId", e.eventId().toString());
                                item.put("eventType", e.eventType());
                                item.put("timestamp", e.timestamp().toString());
                                item.put("collection", e.headers().getOrDefault("collection", "unknown"));
                                item.put("tenantId", e.headers().getOrDefault("tenantId", finalTenantId));
                                String payloadStr = StandardCharsets.UTF_8.decode(
                                        e.payload().duplicate()).toString();
                                item.put("anomalyPayload", payloadStr);
                                return item;
                            })
                            .toList();

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("tenantId", finalTenantId);
                    body.put("collection", finalCollection);
                    body.put("since", sinceInstant.toString());
                    body.put("count", results.size());
                    body.put("anomalies", results);
                    String reqId = request.getQueryParameter("requestId");
                    if (reqId != null) body.put("requestId", reqId);
                    return http.jsonResponse(body);
                })
                .then(Promise::of, e -> {
                    log.error("Failed to query anomaly events tenant={}", finalTenantId, e);
                    return Promise.of(http.errorResponse(500, "Anomaly query failed: " + e.getMessage()));
                });
    }

    // ─── Durable persistence helpers ─────────────────────────────────────────

    /**
     * Persists detected anomalies as events in the {@value #ANOMALY_STREAM} stream.
     *
     * @param tenantId   tenant scope
     * @param collection collection name for header metadata
     * @param anomalies  detected anomalies to persist
     * @return a Promise completing when all anomaly events are appended
     */
    public Promise<Void> persistAnomalies(String tenantId, String collection, List<Anomaly> anomalies) {
        if (eventLogStore == null || anomalies == null || anomalies.isEmpty()) {
            return Promise.of(null);
        }
        TenantContext ctx = TenantContext.of(tenantId, Map.of(
                "stream", ANOMALY_STREAM,
                "collection", collection,
                "tenantId", tenantId));

        List<EventEntry> entries = anomalies.stream()
                .map(a -> {
                    String payload;
                    try {
                        payload = objectMapper.writeValueAsString(Map.of(
                                "anomalyId", a.getAnomalyId() != null ? a.getAnomalyId() : UUID.randomUUID().toString(),
                                "type", a.getType() != null ? a.getType().name() : "UNKNOWN",
                                "severity", a.getSeverity() != null ? a.getSeverity().name() : "UNKNOWN",
                                "confidence", a.getConfidence(),
                                "anomalyScore", a.getAnomalyScore(),
                                "title", a.getTitle(),
                                "affectedEntity", a.getAffectedEntity(),
                                "collection", collection,
                                "tenantId", tenantId,
                                "detectedAt", a.getDetectedAt() != null ? a.getDetectedAt().toString() : Instant.now().toString()
                        ));
                    } catch (Exception e) {
                        payload = "{\"error\":\"serialization failed\"}";
                    }
                    return EventEntry.builder()
                            .eventType(ANOMALY_EVENT_TYPE)
                            .payload(payload.getBytes(StandardCharsets.UTF_8))
                            .headers(Map.of(
                                    "collection", collection,
                                    "tenantId", tenantId,
                                    "severity", a.getSeverity() != null ? a.getSeverity().name() : "UNKNOWN"))
                            .build();
                })
                .toList();

        return eventLogStore.appendBatch(ctx, entries)
                .map(offsets -> {
                    log.debug("Persisted {} anomaly events tenant={} collection={}", offsets.size(), tenantId, collection);
                    return null;
                });
    }
}
