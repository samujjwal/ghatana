package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP Server for Data-Cloud Standalone deployment.
 * Provides REST API endpoints for entity and event operations.
 *
 * @since 1.0.0
 */
public class DataCloudHttpServer {

    private static final Logger log = LoggerFactory.getLogger(DataCloudHttpServer.class);
    
    private final DataCloudClient client;
    private final int port;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private Eventloop eventloop;

    /**
     * Creates a new Data-Cloud HTTP server.
     *
     * @param client the Data-Cloud client instance
     * @param port the port to listen on
     */
    public DataCloudHttpServer(DataCloudClient client, int port) {
        this.client = client;
        this.port = port;
        this.objectMapper = JsonUtils.getDefaultMapper();
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        eventloop = Eventloop.create();
        
        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints
            .with(HttpMethod.GET, "/health", this::handleHealth)
            .with(HttpMethod.GET, "/ready", this::handleReady)
            .with(HttpMethod.GET, "/live", this::handleLive)
            
            // Info endpoints
            .with(HttpMethod.GET, "/info", this::handleInfo)
            .with(HttpMethod.GET, "/metrics", this::handleMetrics)
            
            // Entity endpoints
            .with(HttpMethod.POST, "/api/v1/entities/:collection", this::handleSaveEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id", this::handleGetEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection", this::handleQueryEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/:id", this::handleDeleteEntity)
            
            // Event endpoints
            .with(HttpMethod.POST, "/api/v1/events", this::handleAppendEvent)
            .with(HttpMethod.GET, "/api/v1/events", this::handleQueryEvents)
            
            .build();

        server = HttpServer.builder(eventloop, router)
            .withListenPort(port)
            .build();

        CompletableFuture.runAsync(() -> {
            try {
                server.listen();
                log.info("Data-Cloud HTTP Server started on port {}", port);
                eventloop.run();
            } catch (Exception e) {
                log.error("Failed to start HTTP server", e);
            }
        });
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.close();
        }
        if (eventloop != null) {
            eventloop.breakEventloop();
        }
        log.info("Data-Cloud HTTP Server stopped");
    }

    // ==================== Health Endpoints ====================

    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "service", "datacloud"
        )));
    }

    private Promise<HttpResponse> handleReady(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "READY",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleLive(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "status", "LIVE",
            "timestamp", Instant.now().toString()
        )));
    }

    // ==================== Info Endpoints ====================

    private Promise<HttpResponse> handleInfo(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "service", "Data-Cloud",
            "version", "1.0.0-SNAPSHOT",
            "description", "Unified Data Platform",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleMetrics(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "service", "datacloud",
            "uptime_seconds", System.currentTimeMillis() / 1000,
            "memory_used_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024),
            "memory_free_mb", Runtime.getRuntime().freeMemory() / (1024 * 1024),
            "processors", Runtime.getRuntime().availableProcessors(),
            "timestamp", Instant.now().toString()
        )));
    }

    // ==================== Entity Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleSaveEntity(HttpRequest request) {
        try {
            String collection = request.getPathParameter("collection");
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";
            
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(body, Map.class);
            
            return client.save(tenantId, collection, data)
                .map(entity -> jsonResponse(Map.of(
                    "id", entity.id(),
                    "collection", entity.collection(),
                    "version", entity.version(),
                    "createdAt", entity.createdAt().toString(),
                    "timestamp", Instant.now().toString()
                )));
        } catch (Exception e) {
            log.error("Error saving entity", e);
            return Promise.of(errorResponse(400, "Invalid entity data: " + e.getMessage()));
        }
    }

    private Promise<HttpResponse> handleGetEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        
        return client.findById(tenantId, collection, id)
            .map(optEntity -> {
                if (optEntity.isPresent()) {
                    DataCloudClient.Entity entity = optEntity.get();
                    return jsonResponse(Map.of(
                        "id", entity.id(),
                        "collection", entity.collection(),
                        "data", entity.data(),
                        "version", entity.version(),
                        "createdAt", entity.createdAt().toString(),
                        "updatedAt", entity.updatedAt().toString()
                    ));
                } else {
                    return errorResponse(404, "Entity not found: " + id);
                }
            });
    }

    private Promise<HttpResponse> handleQueryEntities(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        
        String limitStr = request.getQueryParameter("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
        
        DataCloudClient.Query query = DataCloudClient.Query.limit(limit);
        
        return client.query(tenantId, collection, query)
            .map(entities -> jsonResponse(Map.of(
                "entities", entities.stream().map(e -> Map.of(
                    "id", e.id(),
                    "collection", e.collection(),
                    "data", e.data(),
                    "version", e.version()
                )).toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    private Promise<HttpResponse> handleDeleteEntity(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String id = request.getPathParameter("id");
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        
        return client.delete(tenantId, collection, id)
            .map(v -> jsonResponse(Map.of(
                "deleted", true,
                "id", id,
                "collection", collection,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Event Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        try {
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";
            
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> eventData = objectMapper.readValue(body, Map.class);
            
            String eventType = (String) eventData.getOrDefault("type", "unknown");
            Map<String, Object> payload = (Map<String, Object>) eventData.getOrDefault("payload", Map.of());
            
            DataCloudClient.Event event = DataCloudClient.Event.of(eventType, payload);
            
            return client.appendEvent(tenantId, event)
                .map(offset -> jsonResponse(Map.of(
                    "offset", offset.value(),
                    "eventType", eventType,
                    "timestamp", Instant.now().toString()
                )));
        } catch (Exception e) {
            log.error("Error appending event", e);
            return Promise.of(errorResponse(400, "Invalid event data: " + e.getMessage()));
        }
    }

    private Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        
        String limitStr = request.getQueryParameter("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
        
        String eventType = request.getQueryParameter("type");
        DataCloudClient.EventQuery query = eventType != null 
            ? DataCloudClient.EventQuery.byType(eventType)
            : DataCloudClient.EventQuery.all();
        
        return client.queryEvents(tenantId, query)
            .map(events -> jsonResponse(Map.of(
                "events", events.stream().map(e -> Map.of(
                    "type", e.type(),
                    "payload", e.payload(),
                    "timestamp", e.timestamp().toString()
                )).toList(),
                "count", events.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Helper Methods ====================

    private HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500)
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "error", message,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
