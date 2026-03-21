package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Handles event append and query HTTP endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce the god-class size.
 *
 * @doc.type class
 * @doc.purpose Event append and query HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventHandler.class);

    private final DataCloudClient client;
    private final HttpHandlerSupport http;

    public EventHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        try {
            String tenantId = http.resolveTenantId(request);

            Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
            if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> eventData = http.objectMapper().readValue(body, Map.class);

            Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(eventData);
            if (payloadErr.isPresent()) return Promise.of(http.errorResponse(400, payloadErr.get()));

            String eventType = (String) eventData.getOrDefault("type", "unknown");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) eventData.getOrDefault("payload", Map.of());

            DataCloudClient.Event event = DataCloudClient.Event.of(eventType, payload);

            return client.appendEvent(tenantId, event)
                .map(offset -> http.jsonResponse(Map.of(
                    "offset", offset.value(),
                    "eventType", eventType,
                    "timestamp", Instant.now().toString()
                )));
        } catch (Exception e) {
            log.error("Error appending event", e);
            return Promise.of(http.errorResponse(400, "Invalid event data: " + e.getMessage()));
        }
    }

    public Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));

        String eventType = request.getQueryParameter("type");
        DataCloudClient.EventQuery query = eventType != null
            ? DataCloudClient.EventQuery.byType(eventType)
            : DataCloudClient.EventQuery.all();

        return client.queryEvents(tenantId, query)
            .map(events -> http.jsonResponse(Map.of(
                "events", events.stream().map(e -> Map.of(
                    "type", e.type(),
                    "payload", e.payload(),
                    "timestamp", e.timestamp().toString()
                )).toList(),
                "count", events.size(),
                "timestamp", Instant.now().toString()
            )));
    }
}
