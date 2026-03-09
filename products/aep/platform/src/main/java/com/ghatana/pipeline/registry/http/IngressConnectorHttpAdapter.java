package com.ghatana.pipeline.registry.http;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.security.TenantExtractor;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.pipeline.registry.ingress.IngressConnectorRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter for ingress connector routing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for event ingestion routed through registered
 * connectors. Handles tenant extraction, routing decision, and response
 * formatting.
 *
 * <p>
 * <b>Endpoints</b><br>
 * POST /api/v1/connectors/{connectorId}/events - Ingest event via HTTP
 * connector<br>
 * GET /api/v1/connectors/{connectorId}/health - Check connector health<br>
 * POST /api/v1/connectors - Register new connector<br>
 * DELETE /api/v1/connectors/{connectorId} - Unregister connector<br>
 *
 * <p>
 * <b>Tenant Isolation</b><br>
 * Tenant extracted from X-Tenant-ID header. All operations scoped to tenant.
 * Cross-tenant access rejected with 403 Forbidden.
 *
 * <p>
 * <b>Request/Response Format</b><br>
 * Request: JSON with event data (type, payload)<br>
 * Response: 202 Accepted with event ID, or 400/403/500 with error details<br>
 *
 * @see IngressConnectorRouter
 * @see ConnectorSpec
 * @doc.type class
 * @doc.purpose HTTP adapter for ingress connector API
 * @doc.layer product
 * @doc.pattern Adapter
 */
@Slf4j
@RequiredArgsConstructor
public class IngressConnectorHttpAdapter {

    private final IngressConnectorRouter router;
    private final MetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;

    /**
     * Handles POST /api/v1/connectors/{connectorId}/events
     *
     * @param request HTTP request
     * @return Promise with HTTP response
     */
    public Promise<HttpResponse> handleEventIngestion(HttpRequest request) {
        return extractTenantId(request)
                .then(tenantId -> {
                    String path = request.getPath();
                    String connectorId = extractConnectorId(path);

                    if (connectorId == null || connectorId.isEmpty()) {
                        metricsCollector.incrementCounter(
                                "aep.http.ingress.error",
                                "reason", "invalid_path",
                                "tenant", tenantId
                        );
                        return Promise.of(
                                ResponseBuilder.badRequest()
                                        .json("{\"error\": \"Invalid path format, expected /api/v1/connectors/{connectorId}/events\"}")
                                        .build()
                        );
                    }

                    return parseEventFromRequest(request)
                            .then(event -> {
                                // Verify tenant in event matches request tenant
                                if (!tenantId.equals(event.getTenantId())) {
                                    log.warn("Tenant mismatch: header={}, event={}", tenantId, event.getTenantId());
                                    metricsCollector.incrementCounter(
                                            "aep.http.ingress.error",
                                            "reason", "tenant_mismatch",
                                            "header_tenant", tenantId,
                                            "event_tenant", event.getTenantId()
                                    );
                                    return Promise.of(
                                            ResponseBuilder.forbidden()
                                                    .json("{\"error\": \"Tenant mismatch\"}")
                                                    .build()
                                    );
                                }

                                // Set MDC context
                                MDC.put("connectorId", connectorId);
                                MDC.put("tenantId", tenantId);
                                MDC.put("eventId", event.getId().getId());
                                MDC.put("eventType", event.getType());

                                // Route event
                                return router.routeHttpRequest(path, tenantId, event)
                                        .map(routed -> {
                                            metricsCollector.incrementCounter(
                                                    "aep.http.ingress.success",
                                                    "connector_id", connectorId,
                                                    "event_type", event.getType(),
                                                    "tenant", tenantId
                                            );

                                            log.debug("Event ingested successfully via connector '{}'", connectorId);

                                            return ResponseBuilder.accepted()
                                                    .json("{\"eventId\": \"" + event.getId() + "\", \"connectorId\": \"" + connectorId + "\", \"tenant\": \"" + tenantId + "\"}")
                                                    .build();
                                        })
                                        .then((response, ex) -> {
                                            if (ex == null) {
                                                return Promise.of(response);
                                            }
                                            metricsCollector.incrementCounter(
                                                    "aep.http.ingress.error",
                                                    "reason", "routing_failed",
                                                    "connector_id", connectorId,
                                                    "event_type", event.getType(),
                                                    "tenant", tenantId
                                            );

                                            log.error("Failed to route event via connector '{}': {}",
                                                    connectorId, ex.getMessage(), ex);

                                            return Promise.of(ResponseBuilder.internalServerError()
                                                    .json("{\"error\": \"" + ex.getMessage() + "\"}")
                                                    .build());
                                        }
                                        );
                            });
                })
                .then(
                        response -> Promise.of(response),
                        ex -> {
                            log.error("HTTP ingestion request failed: {}", ex.getMessage(), ex);
                            metricsCollector.incrementCounter(
                                    "aep.http.ingress.error",
                                    "reason", "parse_failed"
                            );
                            return Promise.of(ResponseBuilder.badRequest()
                                    .json("{\"error\": \"" + ex.getMessage() + "\"}")
                                    .build());
                        }
                );
    }

    /**
     * Handles GET /api/v1/connectors/{connectorId}/health
     *
     * @param request HTTP request
     * @return Promise with HTTP response
     */
    public Promise<HttpResponse> handleConnectorHealthCheck(HttpRequest request) {
        return extractTenantId(request)
                .map(tenantId -> {
                    String path = request.getPath();
                    String connectorId = extractConnectorId(path);

                    if (connectorId == null || connectorId.isEmpty()) {
                        return ResponseBuilder.badRequest()
                                .json("{\"error\": \"Invalid path format\"}")
                                .build();
                    }

                    ConnectorSpec connector = router.getConnector(connectorId);
                    if (connector == null) {
                        metricsCollector.incrementCounter(
                                "aep.http.connector.health.error",
                                "reason", "not_found",
                                "connector_id", connectorId,
                                "tenant", tenantId
                        );
                        return ResponseBuilder.notFound()
                                .json("{\"error\": \"Connector not found\"}")
                                .build();
                    }

                    // Verify tenant ownership
                    if (!tenantId.equals(connector.getTenantId())) {
                        log.warn("Tenant mismatch in health check: header={}, connector={}",
                                tenantId, connector.getTenantId());
                        return ResponseBuilder.forbidden()
                                .json("{\"error\": \"Connector belongs to different tenant\"}")
                                .build();
                    }

                    metricsCollector.incrementCounter(
                            "aep.http.connector.health.success",
                            "connector_id", connectorId,
                            "tenant", tenantId
                    );

                    return ResponseBuilder.ok()
                            .json("{\"connectorId\": \"" + connectorId + "\", \"status\": \"healthy\", \"type\": \"" + connector.getType() + "\"}")
                            .build();
                });
    }

    /**
     * Handles POST /api/v1/connectors - Register new connector
     *
     * @param request HTTP request
     * @return Promise with HTTP response
     */
    public Promise<HttpResponse> handleConnectorRegistration(HttpRequest request) {
        return extractTenantId(request)
                .then(tenantId -> request.loadBody().then(() -> {
            try {
                String body = request.getBody().asString(StandardCharsets.UTF_8);
                ConnectorSpec connector = objectMapper.readValue(body, ConnectorSpec.class);

                // Enforce tenant isolation
                if (!tenantId.equals(connector.getTenantId())) {
                    log.warn("Tenant mismatch in registration: header={}, spec={}",
                            tenantId, connector.getTenantId());
                    return Promise.of(
                            ResponseBuilder.forbidden()
                                    .json("{\"error\": \"Cannot register connector for different tenant\"}")
                                    .build()
                    );
                }

                // Validate connector
                if (connector.getId() == null || connector.getId().isEmpty()) {
                    return Promise.of(
                            ResponseBuilder.badRequest()
                                    .json("{\"error\": \"Connector ID required\"}")
                                    .build()
                    );
                }

                // Register
                router.registerConnector(connector);

                metricsCollector.incrementCounter(
                        "aep.http.connector.registration.success",
                        "connector_type", connector.getType().toString(),
                        "tenant", tenantId
                );

                log.info("Connector '{}' registered via HTTP", connector.getId());

                return Promise.of(
                        ResponseBuilder.created()
                                .json("{\"connectorId\": \"" + connector.getId() + "\", \"type\": \"" + connector.getType() + "\"}")
                                .build()
                );
            } catch (Exception ex) {
                log.error("Failed to register connector: {}", ex.getMessage(), ex);
                metricsCollector.incrementCounter(
                        "aep.http.connector.registration.error",
                        "reason", "parse_error",
                        "tenant", tenantId
                );
                return Promise.of(
                        ResponseBuilder.badRequest()
                                .json("{\"error\": \"" + ex.getMessage() + "\"}")
                                .build()
                );
            }
        }));
    }

    /**
     * Handles DELETE /api/v1/connectors/{connectorId}
     *
     * @param request HTTP request
     * @return Promise with HTTP response
     */
    public Promise<HttpResponse> handleConnectorUnregistration(HttpRequest request) {
        return extractTenantId(request)
                .map(tenantId -> {
                    String path = request.getPath();
                    String connectorId = extractConnectorId(path);

                    if (connectorId == null || connectorId.isEmpty()) {
                        return ResponseBuilder.badRequest()
                                .json("{\"error\": \"Invalid path format\"}")
                                .build();
                    }

                    ConnectorSpec connector = router.getConnector(connectorId);
                    if (connector == null) {
                        return ResponseBuilder.notFound()
                                .json("{\"error\": \"Connector not found\"}")
                                .build();
                    }

                    // Verify tenant ownership
                    if (!tenantId.equals(connector.getTenantId())) {
                        log.warn("Tenant mismatch in unregistration: header={}, connector={}",
                                tenantId, connector.getTenantId());
                        return ResponseBuilder.forbidden()
                                .json("{\"error\": \"Connector belongs to different tenant\"}")
                                .build();
                    }

                    // Unregister
                    router.unregisterConnector(connectorId);

                    metricsCollector.incrementCounter(
                            "aep.http.connector.unregistration.success",
                            "connector_id", connectorId,
                            "tenant", tenantId
                    );

                    log.info("Connector '{}' unregistered via HTTP", connectorId);

                    return ResponseBuilder.ok()
                            .json("{\"message\": \"Connector unregistered\"}")
                            .build();
                });
    }

    // ==================== Helper Methods ====================
    /**
     * Extracts tenant ID from X-Tenant-ID header.
     */
    private Promise<String> extractTenantId(HttpRequest request) {
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        if (tenantId == null) {
            log.warn("Missing X-Tenant-Id header in request");
            return Promise.ofException(
                    new IllegalArgumentException("X-Tenant-Id header required"));
        }
        return Promise.of(tenantId);
    }

    /**
     * Extracts connector ID from path.
     *
     * Expected format: /api/v1/connectors/{connectorId}/events
     */
    private String extractConnectorId(String path) {
        String pattern = "/api/v1/connectors/";
        if (path.startsWith(pattern)) {
            int start = pattern.length();
            int end = path.indexOf("/", start);
            if (end > start) {
                return path.substring(start, end);
            } else if (end < 0) {
                return path.substring(start);
            }
        }
        return null;
    }

    /**
     * Parses Event from HTTP request body.
     */
    private Promise<Event> parseEventFromRequest(HttpRequest request) {
        return request.loadBody().then(() -> {
            try {
                String body = request.getBody().asString(StandardCharsets.UTF_8);
                if (body == null || body.trim().isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Request body cannot be empty"));
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> eventData = objectMapper.readValue(body, Map.class);

                String type = (String) eventData.get("type");
                Object payloadObj = eventData.get("payload");
                String tenantId = (String) eventData.get("tenantId");
                String version = (String) eventData.getOrDefault("version", "1.0");

                if (type == null || type.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Event type required"));
                }

                if (tenantId == null || tenantId.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Tenant ID required"));
                }

                // Create EventId
                EventId eventId = EventId.create(
                        UUID.randomUUID().toString(),
                        type,
                        version,
                        tenantId
                );

                // Create EventTime
                EventTime eventTime = EventTime.now();

                // Build payload map
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (payloadObj instanceof Map)
                        ? (Map<String, Object>) payloadObj
                        : Map.of("data", payloadObj);

                // Create GEvent
                Event event = GEvent.builder()
                        .id(eventId)
                        .time(eventTime)
                        .payload(payloadMap)
                        .headers(Map.of())
                        .build();

                return Promise.of(event);
            } catch (Exception ex) {
                return Promise.ofException(ex);
            }
        });
    }
}
