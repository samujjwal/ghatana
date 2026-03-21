/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private final AepEngine engine;

    public AnalyticsController(AepEngine engine) {
        this.engine = engine;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = HttpHelper.mapper().readValue(body, Map.class);

                String tenantId = (String) data.getOrDefault("tenantId", "default");
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
                    .map(anomalies -> HttpHelper.jsonResponse(Map.of(
                        "anomalies", anomalies,
                        "count", anomalies.size(),
                        "timestamp", Instant.now().toString()
                    )));
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

                String tenantId = (String) data.getOrDefault("tenantId", "default");
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
}
