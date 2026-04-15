package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
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
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();

    public EventHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    public EventHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            tenantId,
            "datacloud.http.event.append",
            traceSupport.requestSpanId(request),
            Map.of());

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> eventData = http.objectMapper().readValue(body, Map.class);

                Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(eventData);
                if (payloadErr.isPresent()) return Promise.of(http.errorResponse(400, payloadErr.get()));

                String eventType = (String) eventData.getOrDefault("type", "unknown");
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) eventData.getOrDefault("payload", Map.of());

                DataCloudClient.Event event = DataCloudClient.Event.of(eventType, payload);

                return traceSupport.trace(
                    request,
                    tenantId,
                    "datacloud.event.store.append",
                    handlerSpan.spanId(),
                    Map.of("event.type", eventType),
                    () -> client.appendEvent(tenantId, event))
                    .map(offset -> http.jsonResponse(Map.of(
                        "offset", offset.value(),
                        "type", eventType,
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error appending event", e);
                return Promise.of(http.errorResponse(400, "Invalid event data: " + e.getMessage()));
            }
        }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        // Handle 'from' parameter for offset-based querying
        String fromParam = request.getQueryParameter("from");
        int fromOffset = 0;
        if (fromParam != null && !fromParam.isBlank()) {
            try {
                fromOffset = Integer.parseInt(fromParam.trim());
            } catch (NumberFormatException e) {
                return Promise.of(http.errorResponse(400, "Invalid 'from' parameter: must be an integer"));
            }
        }

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.event.query",
                traceSupport.requestSpanId(request),
                Map.of());

        String eventType = request.getQueryParameter("type");
        final int finalFromOffset = fromOffset;
        DataCloudClient.EventQuery query = eventType != null
            ? DataCloudClient.EventQuery.byType(eventType)
            : DataCloudClient.EventQuery.all();

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.event.store.query",
            handlerSpan.spanId(),
            eventType == null ? Map.of("fromOffset", finalFromOffset) : Map.of("fromOffset", finalFromOffset, "event.type", eventType),
            () -> client.queryEvents(tenantId, query))
            .map(events -> {
                var filtered = events.stream()
                    .skip(finalFromOffset)
                    .toList();

                var eventResponses = new java.util.ArrayList<Map<String, Object>>();
                for (int i = 0; i < filtered.size(); i++) {
                    var e = filtered.get(i);
                    eventResponses.add(Map.of(
                        "offset", (long)(finalFromOffset + i),
                        "type", e.type(),
                        "payload", e.payload(),
                        "timestamp", e.timestamp().toString()
                    ));
                }

                return http.jsonResponse(Map.of(
                    "events", eventResponses,
                    "nextOffset", (long)(finalFromOffset + filtered.size()),
                    "count", filtered.size(),
                    "fromOffset", finalFromOffset,
                    "tenantId", tenantId,
                    "timestamp", Instant.now().toString()
                ));
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleGetEventByOffset(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        String offsetParam = request.getPathParameter("offset");
        if (offsetParam == null || offsetParam.isBlank()) {
            return Promise.of(http.errorResponse(400, "offset path parameter is required"));
        }

        int offset;
        try {
            offset = Integer.parseInt(offsetParam.trim());
        } catch (NumberFormatException e) {
            return Promise.of(http.errorResponse(400, "Invalid offset parameter: must be an integer"));
        }

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.event.get_by_offset",
                traceSupport.requestSpanId(request),
                Map.of("offset", offset));

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.event.store.query",
            handlerSpan.spanId(),
            Map.of("offset", offset),
            () -> client.queryEvents(tenantId, DataCloudClient.EventQuery.all()))
            .map(events -> {
                if (offset < 0 || offset >= events.size()) {
                    return http.errorResponse(404, "Event not found at offset: " + offset);
                }
                var e = events.get(offset);
                return http.jsonResponse(Map.of(
                    "offset", (long)offset,
                    "type", e.type(),
                    "payload", e.payload(),
                    "timestamp", e.timestamp().toString()
                ));
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }
}
