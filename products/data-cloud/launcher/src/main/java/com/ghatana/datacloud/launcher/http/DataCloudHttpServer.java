package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.bytebuf.ByteBuf;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Server for Data-Cloud Standalone deployment.
 * Provides REST API endpoints for entity and event operations.
 *
 * @since 1.0.0
 */
public class DataCloudHttpServer {

    private static final Logger log = LoggerFactory.getLogger(DataCloudHttpServer.class);

    /** SSE queue capacity — 512 frames before back-pressure kicks in. */
    private static final int SSE_QUEUE_CAPACITY = 512;

    /** Heartbeat interval: block this long waiting for the next event before sending a heartbeat. */
    private static final long SSE_HEARTBEAT_TIMEOUT_SEC = 30L;

    private final DataCloudClient client;
    private final int port;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private Eventloop eventloop;

    /**
     * Optional brain for DC-6 brain routes.
     * When non-null, {@code /api/v1/brain/**} routes are activated.
     */
    private final DataCloudBrain brain;

    /**
     * Optional learning bridge for DC-8 learning routes.
     * When non-null, {@code /api/v1/learning/**} routes are activated.
     */
    private final DataCloudLearningBridge learningBridge;

    /**
     * Optional analytics engine for DC-9 analytics routes.
     * When non-null, {@code /api/v1/analytics/**} routes are activated.
     */
    private final AnalyticsQueryEngine analyticsEngine;

    /** Virtual-thread executor for off-loop blocking operations (JDBC, queue polls). */
    private final Executor blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Tracks active SSE subscriptions so they can be cancelled on server shutdown. */
    private final CopyOnWriteArrayList<EventLogStore.Subscription> sseSubscriptions =
            new CopyOnWriteArrayList<>();

    /**
     * Creates a new Data-Cloud HTTP server without optional extensions.
     *
     * @param client the Data-Cloud client instance
     * @param port the port to listen on
     */
    public DataCloudHttpServer(DataCloudClient client, int port) {
        this(client, port, null, null, null);
    }

    /**
     * Creates a new Data-Cloud HTTP server with optional brain integration (DC-6).
     *
     * @param client the Data-Cloud client instance
     * @param port   the port to listen on
     * @param brain  optional brain facade; may be {@code null} to disable brain routes
     */
    public DataCloudHttpServer(DataCloudClient client, int port, DataCloudBrain brain) {
        this(client, port, brain, null, null);
    }

    /**
     * Creates a fully-featured Data-Cloud HTTP server with brain, learning, and analytics (DC-6–9).
     *
     * <p>Route availability:
     * <ul>
     *   <li>DC-6: {@code GET /api/v1/brain/**} — requires non-null {@code brain}</li>
     *   <li>DC-8: {@code /api/v1/learning/**} — requires non-null {@code learningBridge}</li>
     *   <li>DC-9: {@code /api/v1/analytics/**} — requires non-null {@code analyticsEngine}</li>
     * </ul>
     *
     * @param client          the Data-Cloud client instance
     * @param port            the port to listen on
     * @param brain           optional brain facade; {@code null} disables brain routes
     * @param learningBridge  optional learning bridge; {@code null} disables learning routes
     * @param analyticsEngine optional analytics engine; {@code null} disables analytics routes
     */
    public DataCloudHttpServer(DataCloudClient client, int port, DataCloudBrain brain,
                               DataCloudLearningBridge learningBridge,
                               AnalyticsQueryEngine analyticsEngine) {
        this.client          = client;
        this.port            = port;
        this.brain           = brain;
        this.learningBridge  = learningBridge;
        this.analyticsEngine = analyticsEngine;
        this.objectMapper    = JsonUtils.getDefaultMapper();
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

            // Agent registry endpoints (DC-3)
            .with(HttpMethod.GET, "/api/v1/agents", this::handleListAgents)
            .with(HttpMethod.POST, "/api/v1/agents", this::handleRegisterAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", this::handleGetAgent)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", this::handleDeleteAgent)

            // Checkpoint management endpoints (DC-3)
            .with(HttpMethod.GET, "/api/v1/checkpoints", this::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/checkpoints", this::handleSaveCheckpoint)
            .with(HttpMethod.GET, "/api/v1/checkpoints/:checkpointId", this::handleGetCheckpoint)
            .with(HttpMethod.DELETE, "/api/v1/checkpoints/:checkpointId", this::handleDeleteCheckpoint)

            // Agent memory plane endpoints (DC-4)
            .with(HttpMethod.GET, "/api/v1/memory/:agentId", this::handleGetAgentMemory)
            .with(HttpMethod.GET, "/api/v1/memory/:agentId/:tier", this::handleGetAgentMemoryByTier)

            // Brain routes (DC-6) — active only when brain is wired
            .with(HttpMethod.GET, "/api/v1/brain/health",      this::handleBrainHealth)
            .with(HttpMethod.GET, "/api/v1/brain/config",      this::handleBrainConfig)
            .with(HttpMethod.GET, "/api/v1/brain/stats",       this::handleBrainStats)
            .with(HttpMethod.GET, "/api/v1/brain/workspace",   this::handleBrainWorkspace)

            // Learning routes (DC-8) — active only when learningBridge is wired
            .with(HttpMethod.POST, "/api/v1/learning/trigger",                    this::handleLearningTrigger)
            .with(HttpMethod.GET,  "/api/v1/learning/status",                     this::handleLearningStatus)
            .with(HttpMethod.GET,  "/api/v1/learning/review",                     this::handleLearningReviewQueue)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/approve",   this::handleLearningReviewApprove)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/reject",    this::handleLearningReviewReject)

            // Analytics routes (DC-9) — active only when analyticsEngine is wired
            .with(HttpMethod.POST, "/api/v1/analytics/query",                     this::handleAnalyticsQuery)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId",            this::handleAnalyticsGetResult)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId/plan",       this::handleAnalyticsGetPlan)
            .with(HttpMethod.POST, "/api/v1/analytics/aggregate",                 this::handleAnalyticsAggregate)

            // Server-Sent Events for real-time UI updates (DC-3)
            .with(HttpMethod.GET, "/events/stream", this::handleSseStream)

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
        // Cancel all active SSE polling subscriptions before stopping
        if (!sseSubscriptions.isEmpty()) {
            log.info("Cancelling {} active SSE subscriptions", sseSubscriptions.size());
            sseSubscriptions.forEach(EventLogStore.Subscription::cancel);
            sseSubscriptions.clear();
        }
        if (eventloop != null) {
            // server.close() must be called from the reactor thread;
            // schedule it there and then break the eventloop.
            eventloop.execute(() -> {
                if (server != null) {
                    server.close();
                }
                eventloop.breakEventloop();
            });
        } else if (server != null) {
            // eventloop was never started — safe to call directly
            server.close();
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

    // ==================== Agent Registry Endpoints (DC-3) ====================

    /**
     * Lists all agents registered in Data-Cloud for a tenant.
     *
     * <p>Agents are stored as entities in the {@code "dc_agents"} collection.
     * Tenant is resolved from the {@code X-Tenant-Id} header (defaults to {@code "default"}).
     */
    private Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return client.query(tenantId, "dc_agents", DataCloudClient.Query.limit(1000))
            .map(entities -> {
                List<Map<String, Object>> agentSummaries = entities.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.id(),
                        "collection", e.collection(),
                        "data", e.data()
                    ))
                    .toList();
                return jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "agents", agentSummaries,
                    "count", agentSummaries.size(),
                    "timestamp", Instant.now().toString()
                ));
            });
    }

    /**
     * Registers a new agent definition in Data-Cloud.
     *
     * <p>Request body should contain the agent definition as JSON.
     * The {@code id} field is required. Stored in the {@code "dc_agents"} collection.
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleRegisterAgent(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> agentData = objectMapper.readValue(body, Map.class);
                return client.save(tenantId, "dc_agents", agentData)
                    .map(entity -> jsonResponse(Map.of(
                        "id", entity.id(),
                        "tenantId", tenantId,
                        "registeredAt", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.warn("Failed to register agent for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid agent definition: " + e.getMessage()));
            }
        });
    }

    /**
     * Gets an agent definition by ID.
     *
     * @return 200 with agent data, or 404 if not found
     */
    private Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        return client.findById(tenantId, "dc_agents", agentId)
            .map(optEntity -> optEntity
                .map(e -> jsonResponse(Map.of("id", e.id(), "data", e.data(), "tenantId", tenantId)))
                .orElse(errorResponse(404, "Agent not found: " + agentId)));
    }

    /**
     * Removes an agent definition from Data-Cloud.
     *
     * @return 200 on success
     */
    private Promise<HttpResponse> handleDeleteAgent(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        return client.delete(tenantId, "dc_agents", agentId)
            .map(v -> jsonResponse(Map.of(
                "deleted", true,
                "agentId", agentId,
                "tenantId", tenantId,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Checkpoint Endpoints (DC-3) ====================

    /**
     * Lists all checkpoints stored in Data-Cloud for a tenant.
     *
     * <p>Checkpoints are stored in the {@code "dc_checkpoints"} collection.
     */
    private Promise<HttpResponse> handleListCheckpoints(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String limitStr = request.getQueryParameter("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
        return client.query(tenantId, "dc_checkpoints", DataCloudClient.Query.limit(limit))
            .map(entities -> jsonResponse(Map.of(
                "tenantId", tenantId,
                "checkpoints", entities.stream()
                    .map(e -> Map.<String, Object>of("id", e.id(), "data", e.data()))
                    .toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    /**
     * Saves (upserts) a checkpoint to Data-Cloud.
     *
     * <p>Request body should contain the checkpoint data as JSON (must include {@code id}).
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleSaveCheckpoint(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> checkpointData = objectMapper.readValue(body, Map.class);
                return client.save(tenantId, "dc_checkpoints", checkpointData)
                    .map(entity -> jsonResponse(Map.of(
                        "id", entity.id(),
                        "tenantId", tenantId,
                        "savedAt", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.warn("Failed to save checkpoint for tenant {}: {}", tenantId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid checkpoint data: " + e.getMessage()));
            }
        });
    }

    /**
     * Gets a checkpoint by ID.
     *
     * @return 200 with checkpoint data, or 404 if not found
     */
    private Promise<HttpResponse> handleGetCheckpoint(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String checkpointId = request.getPathParameter("checkpointId");
        if (checkpointId == null || checkpointId.isBlank()) {
            return Promise.of(errorResponse(400, "checkpointId path parameter is required"));
        }
        return client.findById(tenantId, "dc_checkpoints", checkpointId)
            .map(optEntity -> optEntity
                .map(e -> jsonResponse(Map.of("id", e.id(), "data", e.data(), "tenantId", tenantId)))
                .orElse(errorResponse(404, "Checkpoint not found: " + checkpointId)));
    }

    /**
     * Deletes a checkpoint by ID.
     *
     * @return 200 on success
     */
    private Promise<HttpResponse> handleDeleteCheckpoint(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String checkpointId = request.getPathParameter("checkpointId");
        if (checkpointId == null || checkpointId.isBlank()) {
            return Promise.of(errorResponse(400, "checkpointId path parameter is required"));
        }
        return client.delete(tenantId, "dc_checkpoints", checkpointId)
            .map(v -> jsonResponse(Map.of(
                "deleted", true,
                "checkpointId", checkpointId,
                "tenantId", tenantId,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Memory Plane Endpoints (DC-4) ====================

    /**
     * Returns a memory summary for a given agent across all tiers.
     *
     * <p>Queries the {@code dc_memory} collection filtered by {@code agentId} and returns
     * item counts grouped by memory type (EPISODIC, SEMANTIC, PROCEDURAL, PREFERENCE).
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code limit} – maximum items to load for counting (default 10000)</li>
     * </ul>
     *
     * @return 200 with memory summary
     *
     * @doc.type method
     * @doc.purpose Agent memory summary across all tiers (DC-4)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }

        int limit = parseLimitParam(request.getQueryParameter("limit"), 10_000);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("agentId", agentId))
            .limit(limit)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .map(items -> {
                long episodic = items.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = items.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = items.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = items.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = items.size() - episodic - semantic - procedural - preference;

                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", items.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    /**
     * Returns memory items for a given agent filtered to a specific tier.
     *
     * <p>Valid tier values: {@code episodic}, {@code semantic}, {@code procedural}, {@code preference}.
     * Case-insensitive — normalised to uppercase before querying.
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code limit}  – page size (default 100, max 1000)</li>
     *   <li>{@code offset} – pagination offset (default 0)</li>
     * </ul>
     *
     * @return 200 with paginated list of memory items, 400 on invalid tier
     *
     * @doc.type method
     * @doc.purpose Agent memory retrieval by tier (DC-4)
     * @doc.layer product
     * @doc.pattern Query, Paginated
     */
    private Promise<HttpResponse> handleGetAgentMemoryByTier(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        String rawTier = request.getPathParameter("tier");

        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (rawTier == null || rawTier.isBlank()) {
            return Promise.of(errorResponse(400, "tier path parameter is required"));
        }

        String tier = rawTier.toUpperCase();
        if (!List.of("EPISODIC", "SEMANTIC", "PROCEDURAL", "PREFERENCE").contains(tier)) {
            return Promise.of(errorResponse(400,
                "Invalid tier '" + rawTier + "'. Valid values: episodic, semantic, procedural, preference"));
        }

        int limit = Math.min(parseLimitParam(request.getQueryParameter("limit"), 100), 1000);
        int offset = parseLimitParam(request.getQueryParameter("offset"), 0);

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", tier)
            ))
            .limit(limit)
            .offset(offset)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> itemData = items.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.id(),
                        "agentId", e.data().getOrDefault("agentId", agentId),
                        "type", tier,
                        "content", e.data().getOrDefault("content", ""),
                        "createdAt", e.data().getOrDefault("createdAt", Instant.now().toString()),
                        "metadata", e.data().getOrDefault("metadata", Map.of())
                    ))
                    .toList();

                return jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "tier", tier.toLowerCase(),
                    "items", itemData,
                    "count", itemData.size(),
                    "offset", offset,
                    "limit", limit,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory-by-tier query failed for agentId={} tier={}: {}",
                    agentId, tier, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    // ==================== Brain Endpoints (DC-6) ====================

    /**
     * Returns the overall health status of the brain.
     *
     * <p>Returns HTTP 503 when the brain is not wired (standalone deployment without brain).
     *
     * @param request the incoming HTTP request
     * @return 200 with health JSON, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain health endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Health Check
     */
    private Promise<HttpResponse> handleBrainHealth(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        return brain.health()
            .map(h -> {
                Map<String, Object> componentsMap = new java.util.LinkedHashMap<>();
                h.getComponents().forEach((k, v) -> componentsMap.put(k, v.name()));
                return jsonResponse(Map.of(
                    "status",     h.getStatus().name(),
                    "components", componentsMap,
                    "messages",   h.getMessages(),
                    "timestamp",  Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] brain health check failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(503, "Brain health check failed: " + e.getMessage()));
            });
    }

    /**
     * Returns the current brain configuration.
     *
     * <p>Returns HTTP 503 when the brain is not wired.
     *
     * @param request the incoming HTTP request
     * @return 200 with config JSON, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain config endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Configuration Query
     */
    private Promise<HttpResponse> handleBrainConfig(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        try {
            BrainConfig cfg = brain.getConfig();
            return Promise.of(jsonResponse(Map.of(
                "brainId",          cfg.getBrainId(),
                "name",             cfg.getName(),
                "learningEnabled",  cfg.isLearningEnabled(),
                "reflexesEnabled",  cfg.isReflexesEnabled(),
                "salienceThreshold", cfg.getSalienceThreshold(),
                "timestamp",        Instant.now().toString()
            )));
        } catch (Exception e) {
            log.error("[DC-6] brain config retrieval failed: {}", e.getMessage(), e);
            return Promise.of(errorResponse(500, "Failed to retrieve brain config: " + e.getMessage()));
        }
    }

    /**
     * Returns runtime statistics for the brain.
     *
     * <p>Honours the {@code X-Tenant-Id} header (defaults to {@code "default"}).
     * Returns HTTP 503 when the brain is not wired.
     *
     * @param request the incoming HTTP request
     * @return 200 with stats JSON, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain stats endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Statistics Query
     */
    private Promise<HttpResponse> handleBrainStats(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        BrainContext ctx = BrainContext.forTenant(tenantId);
        return brain.getStats(ctx)
            .map(s -> jsonResponse(Map.of(
                "totalRecordsProcessed", s.getTotalRecordsProcessed(),
                "activePatterns",        s.getActivePatterns(),
                "activeRules",           s.getActiveRules(),
                "hotTierRecords",        s.getHotTierRecords(),
                "warmTierRecords",       s.getWarmTierRecords(),
                "avgProcessingTimeMs",   s.getAvgProcessingTimeMs(),
                "uptimeSeconds",         s.getUptimeSeconds(),
                "tenantId",              tenantId,
                "timestamp",             Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[DC-6] brain stats retrieval failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(503, "Failed to retrieve brain stats: " + e.getMessage()));
            });
    }

    /**
     * Returns a summary of the global workspace state.
     *
     * <p>Returns HTTP 503 when the brain is not wired.
     * Full spotlight items are available via {@code /api/v1/brain/stats}.
     *
     * @param request the incoming HTTP request
     * @return 200 with workspace summary JSON, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain workspace summary endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Workspace Inspection
     */
    private Promise<HttpResponse> handleBrainWorkspace(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        BrainConfig cfg = brain.getConfig();
        return Promise.of(jsonResponse(Map.of(
            "status",    "active",
            "brainId",   cfg.getBrainId(),
            "note",      "Detailed spotlight items available via GET /api/v1/brain/stats",
            "timestamp", Instant.now().toString()
        )));
    }

    // ==================== Learning Endpoints (DC-8) ====================

    /**
     * Manually triggers a brain learning cycle for the request tenant.
     *
     * <p>Returns HTTP 503 when the learning bridge is not wired.
     * The learning cycle is run on a virtual-thread executor to avoid blocking the eventloop.
     *
     * @param request the incoming HTTP request
     * @return 200 with learning result, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Manual learning trigger endpoint (DC-8)
     * @doc.layer product
     * @doc.pattern Command, Async
     */
    private Promise<HttpResponse> handleLearningTrigger(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        return Promise.ofBlocking(blockingExecutor, () -> learningBridge.runLearning(tenantId, true))
            .map(result -> {
                Map<String, Object> resp = new LinkedHashMap<>(result);
                resp.put("timestamp", Instant.now().toString());
                return jsonResponse(Map.copyOf(resp));
            })
            .then(Promise::of, e -> {
                log.error("[DC-8] learning trigger failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(500, "Learning trigger failed: " + e.getMessage()));
            });
    }

    /**
     * Returns the current status of the learning bridge.
     *
     * <p>Returns HTTP 503 when the learning bridge is not wired.
     *
     * @param request the incoming HTTP request
     * @return 200 with status JSON, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Learning status endpoint (DC-8)
     * @doc.layer product
     * @doc.pattern Status Query
     */
    private Promise<HttpResponse> handleLearningStatus(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503, "Learning bridge not available in this deployment"));
        }
        Map<String, Object> status = learningBridge.getStatus();
        Map<String, Object> resp = new LinkedHashMap<>(status);
        resp.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(Map.copyOf(resp)));
    }

    /**
     * Returns all items currently in the pattern review queue.
     *
     * <p>Returns HTTP 503 when the learning bridge is not wired.
     *
     * @param request the incoming HTTP request
     * @return 200 with review queue JSON, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Review-queue listing endpoint (DC-8)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleLearningReviewQueue(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503, "Learning bridge not available in this deployment"));
        }
        Map<String, Map<String, Object>> items = learningBridge.getReviewQueue();
        return Promise.of(jsonResponse(Map.of(
            "items",     items.values(),
            "count",     items.size(),
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Approves a pattern review item.
     *
     * <p>Returns HTTP 503 when the learning bridge is not wired, or 404 if the review item
     * is not found.
     *
     * @param request the incoming HTTP request
     * @return 200 on success, 404 if not found, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Review-approve endpoint (DC-8)
     * @doc.layer product
     * @doc.pattern Command
     */
    private Promise<HttpResponse> handleLearningReviewApprove(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String reviewId = request.getPathParameter("reviewId");
        boolean applied = learningBridge.approveReview(reviewId);
        if (!applied) {
            return Promise.of(errorResponse(404, "Review item not found: " + reviewId));
        }
        return Promise.of(jsonResponse(Map.of(
            "reviewId",  reviewId,
            "decision",  "APPROVED",
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * Rejects a pattern review item.
     *
     * <p>Returns HTTP 503 when the learning bridge is not wired, or 404 if the review item
     * is not found.
     *
     * @param request the incoming HTTP request
     * @return 200 on success, 404 if not found, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Review-reject endpoint (DC-8)
     * @doc.layer product
     * @doc.pattern Command
     */
    private Promise<HttpResponse> handleLearningReviewReject(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503, "Learning bridge not available in this deployment"));
        }
        String reviewId = request.getPathParameter("reviewId");
        boolean applied = learningBridge.rejectReview(reviewId);
        if (!applied) {
            return Promise.of(errorResponse(404, "Review item not found: " + reviewId));
        }
        return Promise.of(jsonResponse(Map.of(
            "reviewId",  reviewId,
            "decision",  "REJECTED",
            "timestamp", Instant.now().toString()
        )));
    }

    // ==================== Analytics Endpoints (DC-9) ====================

    /**
     * Submits a SQL analytics query and returns the result synchronously.
     *
     * <p>Expected request body: {@code {"query": "SELECT ...", "parameters": {...}}}.
     * Returns HTTP 503 when the analytics engine is not wired.
     *
     * @param request the incoming HTTP request
     * @return 200 with query result, 400 on bad input, or 503 if engine unavailable
     *
     * @doc.type method
     * @doc.purpose Analytics query-submission endpoint (DC-9)
     * @doc.layer product
     * @doc.pattern Query, Async
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleAnalyticsQuery(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(errorResponse(503, "Analytics engine not available in this deployment"));
        }
        try {
            String tenantId = resolveTenantId(request);
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(body, Map.class);
            String queryText = (String) payload.get("query");
            if (queryText == null || queryText.isBlank()) {
                return Promise.of(errorResponse(400, "Missing required field: 'query'"));
            }
            Map<String, Object> params = payload.containsKey("parameters")
                ? (Map<String, Object>) payload.get("parameters")
                : Map.of();
            return analyticsEngine.submitQuery(tenantId, queryText, params)
                .map(result -> jsonResponse(Map.of(
                    "queryId",         result.getQueryId(),
                    "queryType",       result.getQueryType(),
                    "rowCount",        result.getRowCount(),
                    "columnCount",     result.getColumnCount(),
                    "rows",            result.getRows(),
                    "executionTimeMs", result.getExecutionTimeMs(),
                    "optimized",       result.isOptimized(),
                    "timestamp",       Instant.now().toString()
                )))
                .then(Promise::of, e -> {
                    log.error("[DC-9] analytics query failed: {}", e.getMessage(), e);
                    return Promise.of(errorResponse(500, "Query execution failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("[DC-9] analytics query request parse error: {}", e.getMessage(), e);
            return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
        }
    }

    /**
     * Retrieves the cached result of a previously submitted analytics query.
     *
     * <p>Returns HTTP 503 when the analytics engine is not wired, or 404 if the query ID
     * is unknown / the result has expired from the cache.
     *
     * @param request the incoming HTTP request
     * @return 200 with result JSON, 404 if not found, or 503 if engine unavailable
     *
     * @doc.type method
     * @doc.purpose Analytics result-retrieval endpoint (DC-9)
     * @doc.layer product
     * @doc.pattern Query, Cache
     */
    private Promise<HttpResponse> handleAnalyticsGetResult(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        return analyticsEngine.getResult(queryId)
            .map(result -> {
                if (result == null) {
                    return errorResponse(404, "No result found for queryId: " + queryId);
                }
                return jsonResponse(Map.of(
                    "queryId",         result.getQueryId(),
                    "queryType",       result.getQueryType(),
                    "rowCount",        result.getRowCount(),
                    "columnCount",     result.getColumnCount(),
                    "rows",            result.getRows(),
                    "executionTimeMs", result.getExecutionTimeMs(),
                    "optimized",       result.isOptimized(),
                    "timestamp",       Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-9] analytics getResult failed queryId={}: {}", queryId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to retrieve result: " + e.getMessage()));
            });
    }

    /**
     * Returns the execution plan for a previously submitted analytics query.
     *
     * <p>Returns HTTP 503 when the analytics engine is not wired, or 404 if the query ID
     * is unknown.
     *
     * @param request the incoming HTTP request
     * @return 200 with plan JSON, 404 if not found, or 503 if engine unavailable
     *
     * @doc.type method
     * @doc.purpose Analytics query-plan endpoint (DC-9)
     * @doc.layer product
     * @doc.pattern Query Plan
     */
    private Promise<HttpResponse> handleAnalyticsGetPlan(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        return analyticsEngine.getPlan(queryId)
            .map(plan -> {
                if (plan == null) {
                    return errorResponse(404, "No query plan found for queryId: " + queryId);
                }
                return jsonResponse(Map.of(
                    "queryId",       plan.getQueryId(),
                    "queryType",     plan.getQueryType().name(),
                    "dataSources",   plan.getDataSources(),
                    "estimatedCost", plan.getEstimatedCost(),
                    "optimized",     plan.isOptimized(),
                    "timestamp",     Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-9] analytics getPlan failed queryId={}: {}", queryId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Failed to retrieve query plan: " + e.getMessage()));
            });
    }

    /**
     * Submits an aggregate-style SQL query via the analytics engine.
     *
     * <p>Accepts the same body format as {@code POST /api/v1/analytics/query} — this endpoint
     * is a semantic convenience alias that validates the query contains an aggregation keyword.
     * Expected body: {@code {"query": "SELECT COUNT(*) FROM ...", "parameters": {...}}}.
     *
     * @param request the incoming HTTP request
     * @return 200 with aggregation result, 400 on bad input, or 503 if engine unavailable
     *
     * @doc.type method
     * @doc.purpose Analytics aggregate endpoint (DC-9)
     * @doc.layer product
     * @doc.pattern Query, Aggregate
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleAnalyticsAggregate(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr       = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = objectMapper.readValue(bodyStr, Map.class);
                    String queryText = (String) payload.get("query");
                    if (queryText == null || queryText.isBlank()) {
                        return Promise.of(errorResponse(400, "Missing required field: 'query'"));
                    }
                    String upperQuery = queryText.toUpperCase();
                    if (!upperQuery.contains("GROUP BY") && !upperQuery.contains("COUNT(")
                            && !upperQuery.contains("SUM(") && !upperQuery.contains("AVG(")) {
                        return Promise.of(errorResponse(400,
                            "Aggregate endpoint requires a query with GROUP BY, COUNT, SUM, or AVG"));
                    }
                    Map<String, Object> params = payload.containsKey("parameters")
                        ? (Map<String, Object>) payload.get("parameters")
                        : Map.of();
                    return analyticsEngine.submitQuery(tenantId, queryText, params)
                        .map(result -> jsonResponse(Map.of(
                            "queryId",         result.getQueryId(),
                            "queryType",       result.getQueryType(),
                            "rowCount",        result.getRowCount(),
                            "rows",            result.getRows(),
                            "executionTimeMs", result.getExecutionTimeMs(),
                            "optimized",       result.isOptimized(),
                            "timestamp",       Instant.now().toString()
                        )))
                        .then(Promise::of, e -> {
                            log.error("[DC-9] analytics aggregate failed: {}", e.getMessage(), e);
                            return Promise.of(errorResponse(500, "Aggregate query failed: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("[DC-9] analytics aggregate request parse error: {}", e.getMessage(), e);
                    return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-9] analytics aggregate body load error: {}", e.getMessage(), e);
                return Promise.of(errorResponse(400, "Failed to read request body: " + e.getMessage()));
            });
    }

    // ==================== SSE Endpoints (DC-3) ====================

    /**
     * Server-Sent Events stream for real-time Data-Cloud event-log tailing.
     *
     * <p>Opens a long-lived HTTP/1.1 connection and pushes new events to the
     * client as they are committed to the event log. Uses the
     * {@link EventLogStore#tail} polling subscription under the hood, bridged
     * to ActiveJ's reactive {@link ChannelSupplier} via a bounded
     * {@link LinkedBlockingQueue}.
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code fromOffset} – starting log offset (inclusive, default 0)</li>
     *   <li>{@code eventType}  – comma-separated event-type filter (default: all)</li>
     * </ul>
     *
     * <h3>Tenant resolution</h3>
     * Tenant id is resolved from the {@code X-Tenant-Id} request header or the
     * {@code tenantId} query parameter, falling back to {@code "default"}.
     *
     * <h3>SSE frame types</h3>
     * <ul>
     *   <li>{@code connected} – sent immediately on connection</li>
     *   <li>{@code event}     – one frame per log entry</li>
     *   <li>{@code heartbeat} – sent every {@value #SSE_HEARTBEAT_TIMEOUT_SEC} s when idle</li>
     * </ul>
     *
     * @param request the incoming HTTP request
     * @return a streaming {@code text/event-stream} response
     *
     * @doc.type method
     * @doc.purpose Real-time SSE push for Data-Cloud event-log consumers (DC-4)
     * @doc.layer product
     * @doc.pattern SSE Adapter, Event Tailing
     */
    private Promise<HttpResponse> handleSseStream(HttpRequest request) {
        String tenantId = resolveTenantId(request);

        // fromOffset: explicit offset or 0 (from beginning)
        long fromOffsetVal = parseLongParam(request.getQueryParameter("fromOffset"), 0L);

        // optional event-type filter (comma-separated)
        List<String> eventTypesFilter = parseEventTypeFilter(request.getQueryParameter("eventType"));

        // Thread-safe bridge: background tail handler writes; eventloop ChannelSupplier reads.
        // Optional.empty() is the EOS sentinel.
        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        // Immediately enqueue the "connected" acknowledgement so the client gets
        // something as soon as the subscription is established.
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
                "service", "data-cloud",
                "tenantId", tenantId,
                "fromOffset", String.valueOf(fromOffsetVal),
                "timestamp", Instant.now().toString()
        ))));

        TenantContext tenant = TenantContext.of(tenantId);
        EventLogStore eventLogStore = client.eventLogStore();

        // tail() is a Promise<Subscription>; the handler is called from a blocking executor thread.
        return eventLogStore.tail(tenant, Offset.of(fromOffsetVal), entry -> {
            // Apply event-type filter
            if (!eventTypesFilter.isEmpty() && !eventTypesFilter.contains(entry.eventType())) {
                return;
            }
            try {
                byte[] frame = buildEventSseFrame(entry);
                if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[SSE] queue full for tenant={}, dropping event type={}", tenantId, entry.eventType());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("[SSE] serialization error for tenant={}: {}", tenantId, ex.getMessage());
            }
        }).map(subscription -> {
            // Register so it can be cancelled on server shutdown
            sseSubscriptions.add(subscription);

            // Async supplier: blocks up to SSE_HEARTBEAT_TIMEOUT_SEC waiting for the next
            // event, sending a heartbeat on timeout to keep the connection alive.
            ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
                if (subscription.isCancelled()) {
                    return Promise.of(null); // signal EOS to ActiveJ
                }
                return Promise.ofBlocking(blockingExecutor, () -> {
                    if (subscription.isCancelled()) {
                        return null;
                    }
                    try {
                        Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                        if (item == null) {
                            // Timeout — send heartbeat to keep the TCP connection alive
                            return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                    Map.of("ts", Instant.now().toString())));
                        }
                        // Optional.empty() == EOS sentinel
                        return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null; // end stream on interrupt
                    }
                });
            });

            log.info("[SSE] stream opened for tenant={} fromOffset={} filter={}",
                    tenantId, fromOffsetVal, eventTypesFilter.isEmpty() ? "*" : eventTypesFilter);

            return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
                    .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
                    .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
                    .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
                    .withBodyStream(bodyStream)
                    .build();
        }).mapException(e -> {
            log.error("[SSE] failed to open tail subscription for tenant={}: {}", tenantId, e.getMessage(), e);
            return new HttpException("SSE subscription failed: " + e.getMessage(), e);
        });
    }

    /**
     * Builds an SSE frame for the given event type and JSON payload.
     *
     * @param eventType SSE event name
     * @param payload   data payload (serialised to JSON)
     * @return UTF-8 encoded SSE frame bytes
     */
    private byte[] buildSseFrame(String eventType, Map<String, ?> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String frame = "event: " + eventType + "\n" + "data: " + json + "\n\n";
            return frame.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE] frame build error for event={}: {}", eventType, e.getMessage());
            return ("event: " + eventType + "\ndata: {}\n\n").getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Builds an SSE {@code event} frame from a stored {@link EventLogStore.EventEntry}.
     *
     * @param entry event log entry
     * @return UTF-8 encoded SSE event frame bytes
     */
    private byte[] buildEventSseFrame(EventLogStore.EventEntry entry) {
        try {
            byte[] payloadBytes = new byte[entry.payload().remaining()];
            entry.payload().duplicate().get(payloadBytes);
            String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);

            Map<String, Object> data = Map.of(
                    "eventId", entry.eventId().toString(),
                    "eventType", entry.eventType(),
                    "eventVersion", entry.eventVersion(),
                    "timestamp", entry.timestamp().toString(),
                    "contentType", entry.contentType(),
                    "payload", payloadStr
            );
            String json = objectMapper.writeValueAsString(data);
            String frame = "event: event\n" + "data: " + json + "\n\n";
            return frame.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE] buildEventSseFrame error: {}", e.getMessage());
            return ("event: event\ndata: {\"error\":\"serialization failure\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses a long query parameter, returning the default value on parse failure or null.
     *
     * @param param        raw string value (may be {@code null})
     * @param defaultValue value returned when {@code param} is absent or unparseable
     * @return parsed long
     */
    private static long parseLongParam(String param, long defaultValue) {
        if (param == null || param.isBlank()) return defaultValue;
        try {
            return Long.parseLong(param.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses an integer query parameter (e.g., limit, offset), returning the default value on failure.
     *
     * @param param        raw string value (may be {@code null})
     * @param defaultValue value returned when {@code param} is absent or unparseable
     * @return parsed int
     */
    private static int parseLimitParam(String param, int defaultValue) {
        if (param == null || param.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses an event-type filter from a comma-separated query parameter.
     *
     * @param param raw string (may be {@code null})
     * @return non-null, possibly-empty list of event types
     */
    private static List<String> parseEventTypeFilter(String param) {
        if (param == null || param.isBlank()) return List.of();
        return Arrays.asList(param.trim().split("\\s*,\\s*"));
    }

    // ==================== Helper Methods ====================

    /** Resolves tenant from {@code X-Tenant-Id} header or query parameter, defaulting to {@code "default"}. */
    private String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

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
