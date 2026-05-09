package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
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
    private TenantQuotaService tenantQuotaService;
    /** DC-BE-002: Generic idempotency store for event append operations. */
    private WriteIdempotencyStore idempotencyStore;

    public EventHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    public EventHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    public EventHandler withTenantQuotaService(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
        return this;
    }

    /**
     * DC-BE-002: Attaches a generic idempotency store for event append operations.
     *
     * @param idempotencyStore the idempotency store
     * @return {@code this} for method chaining
     */
    public EventHandler withIdempotencyStore(WriteIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    /**
     * P0.5: Check tenant quota before event append operations.
     * Returns an error promise if quota is exceeded, otherwise null.
     */
    private Promise<HttpResponse> checkQuotaOrNull(String tenantId, String operationType, int resourceAmount) {
        if (tenantQuotaService == null) return null;
        QuotaCheckResult result = tenantQuotaService.checkQuota(tenantId, operationType, resourceAmount);
        if (!result.isAllowed()) {
            return Promise.of(http.errorResponse(429,
                "Quota exceeded: " + result.message() + " (quota=" + result.quotaValue()
                    + ", used=" + result.usedAmount() + ")"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        // DC-BE-002: Check idempotency for event append
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "events:append";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached event append response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }

        Promise<HttpResponse> quotaErr = checkQuotaOrNull(tenantId, "EVENT", 1);
        if (quotaErr != null) return quotaErr;

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

                Optional<String> requestErr = ApiInputValidator.validateEntityPayload(eventData);
                if (requestErr.isPresent()) return Promise.of(http.errorResponse(400, requestErr.get()));

                String eventType = (String) eventData.get("type");
                if (eventType == null || eventType.isBlank()) {
                    return Promise.of(http.errorResponse(400, "type must not be null or blank"));
                }

                Object payloadCandidate = eventData.containsKey("payload")
                    ? eventData.get("payload")
                    : eventData.get("data");
                if (!(payloadCandidate instanceof Map<?, ?> payloadMap)) {
                    return Promise.of(http.errorResponse(400, "payload must be a JSON object"));
                }

                Map<String, Object> payload = (Map<String, Object>) payloadMap;
                if (eventData.containsKey("data")) {
                    Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(payload);
                    if (payloadErr.isPresent()) return Promise.of(http.errorResponse(400, payloadErr.get()));
                }

                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type(eventType)
                    .payload(payload)
                    .source("datacloud.launcher.event-handler")
                    .build();

                return traceSupport.trace(
                    request,
                    tenantId,
                    "datacloud.event.store.append",
                    handlerSpan.spanId(),
                    Map.of("event.type", eventType),
                    () -> client.appendEvent(tenantId, event))
                    .map(offset -> {
                        Map<String, Object> responseBody = Map.of(
                            "offset", offset.value(),
                            "type", eventType,
                            "eventType", eventType,
                            "timestamp", Instant.now().toString()
                        );
                        // DC-BE-002: Store idempotency response
                        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                            idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                        }
                        return http.jsonResponse(responseBody);
                    });
            } catch (Exception e) {
                log.error("Error appending event", e);
                return Promise.of(http.errorResponse(400, "Invalid event data: " + e.getMessage()));
            }
        }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        // P1-3: Support both offset-based and timestamp-based pagination
        String fromParam = request.getQueryParameter("from");
        String fromTimestampParam = request.getQueryParameter("fromTimestamp");
        int fromOffset = 0;
        Instant fromTimestamp = null;

        if (fromTimestampParam != null && !fromTimestampParam.isBlank()) {
            // P1-3: Use timestamp-based pagination if provided
            try {
                fromTimestamp = Instant.parse(fromTimestampParam.trim());
                log.debug("[P1-3] Using timestamp-based pagination fromTimestamp={}", fromTimestamp);
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid 'fromTimestamp' parameter: must be ISO-8601 format (e.g., 2024-01-01T00:00:00Z)"));
            }
        } else if (fromParam != null && !fromParam.isBlank()) {
            // Fallback to offset-based pagination
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
        final Instant finalFromTimestamp = fromTimestamp;
        DataCloudClient.Offset queryOffset = DataCloudClient.Offset.of(Math.max(0, finalFromOffset));
        DataCloudClient.EventQuery query = new DataCloudClient.EventQuery(
            eventType != null ? List.of(eventType) : List.of(),
            null,
            null,
            queryOffset,
            limitResult.getValue());

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.event.store.query",
            handlerSpan.spanId(),
            eventType == null 
                ? (fromTimestamp != null ? Map.of("fromTimestamp", finalFromTimestamp.toString()) : Map.of("fromOffset", finalFromOffset))
                : (fromTimestamp != null ? Map.of("fromTimestamp", finalFromTimestamp.toString(), "event.type", eventType) : Map.of("fromOffset", finalFromOffset, "event.type", eventType)),
            () -> client.queryEvents(tenantId, query))
            .map(events -> {
                var filtered = events.stream()
                    .filter(e -> finalFromTimestamp == null || e.timestamp().isAfter(finalFromTimestamp))
                    .limit(limitResult.getValue())
                    .toList();

                var eventResponses = new java.util.ArrayList<Map<String, Object>>();
                for (int i = 0; i < filtered.size(); i++) {
                    var e = filtered.get(i);
                    long eventOffset = finalFromOffset + i;
                    String eventOffsetHeader = e.headers().get("_x_dc_offset");
                    if (eventOffsetHeader != null) {
                        try {
                            eventOffset = Long.parseLong(eventOffsetHeader);
                        } catch (NumberFormatException ignored) {
                            // Keep compatibility fallback offset if header is not numeric.
                        }
                    }
                    eventResponses.add(Map.of(
                        "offset", eventOffset,
                        "type", e.type(),
                        "payload", e.payload(),
                        "timestamp", e.timestamp().toString()
                    ));
                }

                // P1-3: Include pagination metadata for both offset and timestamp modes
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("events", eventResponses);
                response.put("count", filtered.size());
                response.put("tenantId", tenantId);
                response.put("timestamp", Instant.now().toString());
                
                if (finalFromTimestamp != null) {
                    // Timestamp-based pagination metadata
                    response.put("fromTimestamp", finalFromTimestamp.toString());
                    if (!filtered.isEmpty()) {
                        response.put("nextTimestamp", filtered.get(filtered.size() - 1).timestamp().toString());
                    }
                } else {
                    // Offset-based pagination metadata
                    response.put("fromOffset", finalFromOffset);
                    response.put("nextOffset", (long)(finalFromOffset + filtered.size()));
                }

                return http.jsonResponse(response);
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleGetEventByOffset(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

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
