package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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

    private final StatisticalAnomalyDetector anomalyDetector;
    private final HttpHandlerSupport http;

    /**
     * Creates an anomaly handler.
     *
     * @param anomalyDetector the detector; may be {@code null} — handler returns 501 in that case
     * @param http            shared HTTP helpers
     */
    public EntityAnomalyHandler(StatisticalAnomalyDetector anomalyDetector, HttpHandlerSupport http) {
        this.anomalyDetector = anomalyDetector;
        this.http = http;
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
            return Promise.of(http.errorResponse(501, "Anomaly detection not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = http.resolveTenantId(request);

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
                        .map(anomalies -> {
                            Map<String, Object> body = new LinkedHashMap<>(responseEnvelope);
                            body.put("count", anomalies.size());
                            body.put("anomalies", anomalies);
                            return http.jsonResponse(body);
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
}
