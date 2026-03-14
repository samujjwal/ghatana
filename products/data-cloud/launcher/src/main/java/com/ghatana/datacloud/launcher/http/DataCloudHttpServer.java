package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import com.ghatana.platform.types.identity.Offset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.bytebuf.ByteBuf;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import com.ghatana.datacloud.entity.storage.StorageConnector;

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

    // ==================== CORS Constants ====================
    private static final String CORS_ALLOW_ORIGIN  = "*";
    private static final String CORS_ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS, PATCH";
    private static final String CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Tenant-ID, X-Request-ID";
    private static final String CORS_MAX_AGE       = "86400";

    // ==================== Request Validation Constants ====================
    /** Maximum JSON body size accepted: 10 MB. Larger payloads return HTTP 413. */
    private static final long MAX_BODY_BYTES = 10 * 1024 * 1024L;

    // ==================== Rate Limiting Constants ====================
    /** Maximum number of requests allowed per IP per window. */
    private static final int RATE_LIMIT_REQUESTS = 200;
    /** Fixed-window duration in milliseconds (60 seconds). */
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    /** Maximum distinct IP entries kept in memory before oldest are evicted. */
    private static final int RATE_LIMIT_MAX_ENTRIES = 10_000;

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

    /**
     * Per-IP rate limit state: maps remote address → [requestCount, windowStartMs].
     * Entries are invalidated when the window expires. The map is bounded to
     * {@link #RATE_LIMIT_MAX_ENTRIES} to prevent unbounded memory growth from
     * IP-spoofing attacks.
     */
    private final Map<String, long[]> rateLimitState =
            new ConcurrentHashMap<>(RATE_LIMIT_MAX_ENTRIES);

    /** Tracks active SSE subscriptions so they can be cancelled on server shutdown. */
    private final CopyOnWriteArrayList<EventLogStore.Subscription> sseSubscriptions =
            new CopyOnWriteArrayList<>();

    /**
     * Optional schema validator for entity data.
     * When set, {@code handleSaveEntity} and {@code handleBatchSaveEntities} will
     * validate entity data against registered schemas before persisting.
     * If no schema is registered for the collection the entity passes through.
     */
    private EntitySchemaValidator schemaValidator;

    /**
     * Optional OpenSearch connector for full-text search and streaming query operations.
     * When non-null, {@code GET /api/v1/entities/:collection/search} and
     * {@code GET /api/v1/entities/:collection/query/stream} are backed by
     * OpenSearch. When {@code null}, those routes return HTTP 501.
     */
    private OpenSearchConnector openSearchConnector;

    /**
     * Optional bulk-export service for CSV and NDJSON entity downloads.
     * When non-null, {@code GET /api/v1/entities/:collection/export} is active.
     * When {@code null} the route returns HTTP 501.
     */
    private EntityExportService exportService;

    /**
     * Optional statistical anomaly detector.
     * When non-null, {@code POST /api/v1/entities/:collection/anomalies} is active.
     * When {@code null} the route returns HTTP 501.
     */
    private StatisticalAnomalyDetector anomalyDetector;

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
     * Attaches an {@link EntitySchemaValidator} to this server.
     *
     * <p>Once attached, every {@code POST /api/v1/entities/:collection} and
     * {@code POST /api/v1/entities/:collection/batch} request will be validated
     * against schemas registered with the validator before the entity is
     * persisted. Requests for collections without a registered schema pass
     * through unchanged.
     *
     * @param validator the schema validator; must not be {@code null}
     * @return {@code this} for chaining
     */
    public DataCloudHttpServer withSchemaValidator(EntitySchemaValidator validator) {
        this.schemaValidator = validator;
        return this;
    }

    /**
     * Attaches an {@link OpenSearchConnector} to enable full-text search and
     * streaming query endpoints.
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code GET /api/v1/entities/:collection/search?q=<lucene-expr>} — snapshot
     *       full-text search via OpenSearch {@code query_string} DSL.</li>
     *   <li>{@code GET /api/v1/entities/:collection/query/stream?q=<expr>&follow=true} —
     *       SSE streaming that first pushes a snapshot then tails the event log
     *       for matching entity changes.</li>
     * </ul>
     *
     * <p>When this method is not called, both routes return {@code 501 Not Implemented}.
     *
     * @param connector the OpenSearch connector; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withOpenSearchConnector(OpenSearchConnector connector) {
        this.openSearchConnector = connector;
        return this;
    }

    /**
     * Attaches an {@link EntityExportService} to enable bulk CSV/NDJSON export.
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code GET /api/v1/entities/:collection/export?format=csv|ndjson&limit=N}</li>
     * </ul>
     *
     * @param service the export service; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withExportService(EntityExportService service) {
        this.exportService = service;
        return this;
    }

    /**
     * Attaches a {@link StatisticalAnomalyDetector} to enable statistical anomaly detection.
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code POST /api/v1/entities/:collection/anomalies}</li>
     * </ul>
     *
     * @param detector the anomaly detector; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withAnomalyDetector(StatisticalAnomalyDetector detector) {
        this.anomalyDetector = detector;
        return this;
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
            .with(HttpMethod.GET, "/api/v1/entities/:collection/stream", this::handleEntityCdcStream)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/search", this::handleFullTextSearch)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/query/stream", this::handleStreamingQuerySse)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id", this::handleGetEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection", this::handleQueryEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/:id", this::handleDeleteEntity)
            // Bulk entity endpoints — upsert/delete multiple entities in a single request
            .with(HttpMethod.POST, "/api/v1/entities/:collection/batch", this::handleBatchSaveEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/batch", this::handleBatchDeleteEntities)
            // Bulk export and anomaly detection endpoints
            .with(HttpMethod.GET, "/api/v1/entities/:collection/export", this::handleExportEntities)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/anomalies", this::handleDetectAnomalies)
            
            // Event endpoints
            .with(HttpMethod.POST, "/api/v1/events", this::handleAppendEvent)
            .with(HttpMethod.GET, "/api/v1/events", this::handleQueryEvents)

            // Agent registry endpoints (DC-3)
            .with(HttpMethod.GET, "/api/v1/agents", this::handleListAgents)
            .with(HttpMethod.POST, "/api/v1/agents", this::handleRegisterAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", this::handleGetAgent)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", this::handleDeleteAgent)

            // Pipeline registry endpoints — stores AEP pipeline definitions in dc_pipelines
            .with(HttpMethod.GET,    "/api/v1/pipelines",                this::handleListPipelines)
            .with(HttpMethod.POST,   "/api/v1/pipelines",                this::handleSavePipeline)
            .with(HttpMethod.GET,    "/api/v1/pipelines/:pipelineId",    this::handleGetPipeline)
            .with(HttpMethod.PUT,    "/api/v1/pipelines/:pipelineId",    this::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId",    this::handleDeletePipeline)

            // Checkpoint management endpoints (DC-3)
            .with(HttpMethod.GET, "/api/v1/checkpoints", this::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/checkpoints", this::handleSaveCheckpoint)
            .with(HttpMethod.GET, "/api/v1/checkpoints/:checkpointId", this::handleGetCheckpoint)
            .with(HttpMethod.DELETE, "/api/v1/checkpoints/:checkpointId", this::handleDeleteCheckpoint)

            // Agent memory plane endpoints (DC-4)
            .with(HttpMethod.GET,    "/api/v1/memory/:agentId",                  this::handleGetAgentMemory)
            .with(HttpMethod.GET,    "/api/v1/memory/:agentId/:tier",            this::handleGetAgentMemoryByTier)
            .with(HttpMethod.POST,   "/api/v1/memory/:agentId/search",           this::handleSearchAgentMemory)
            .with(HttpMethod.DELETE, "/api/v1/memory/:agentId/:memoryId",        this::handleDeleteMemory)
            .with(HttpMethod.PUT,    "/api/v1/memory/:agentId/:memoryId/retain", this::handleRetainMemory)

            // Brain routes (DC-6) — active only when brain is wired
            .with(HttpMethod.GET,  "/api/v1/brain/health",                this::handleBrainHealth)
            .with(HttpMethod.GET,  "/api/v1/brain/config",                this::handleBrainConfig)
            .with(HttpMethod.GET,  "/api/v1/brain/stats",                 this::handleBrainStats)
            .with(HttpMethod.GET,  "/api/v1/brain/workspace",             this::handleBrainWorkspace)
            .with(HttpMethod.GET,  "/api/v1/brain/workspace/stream",      this::handleBrainWorkspaceStream)
            .with(HttpMethod.POST, "/api/v1/brain/attention/elevate",     this::handleBrainAttentionElevate)
            .with(HttpMethod.GET,  "/api/v1/brain/attention/thresholds",  this::handleBrainAttentionThresholds)
            .with(HttpMethod.PUT,  "/api/v1/brain/attention/thresholds",  this::handleBrainAttentionThresholdsUpdate)
            .with(HttpMethod.GET,  "/api/v1/brain/patterns",              this::handleBrainPatterns)
            .with(HttpMethod.POST, "/api/v1/brain/patterns/match",        this::handleBrainPatternsMatch)
            .with(HttpMethod.GET,  "/api/v1/brain/salience/:itemId",      this::handleBrainSalience)

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

            // Server-Sent Events for real-time UI updates (DC-3, DC-9)
            .with(HttpMethod.GET, "/events/stream",              this::handleSseStream)
            .with(HttpMethod.GET, "/api/v1/agents/events/stream", this::handleAgentsEventStream)
            .with(HttpMethod.GET, "/api/v1/learning/stream",      this::handleLearningStream)

            .build();

        server = HttpServer.builder(eventloop,
                corsFilter(rateLimitFilter(payloadSizeLimitFilter(contentTypeFilter(router)))))
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
            final String resolvedTenantId = tenantId;

            // API boundary validation
            Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
            if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
            Optional<String> collErr = ApiInputValidator.validateCollection(collection);
            if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(body, Map.class);

            Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(data);
            if (payloadErr.isPresent()) return Promise.of(errorResponse(400, payloadErr.get()));

            // Schema validation (no-op when validator is absent or schema unregistered)
            if (schemaValidator != null) {
                ValidationResult vr = schemaValidator.validate(resolvedTenantId, collection, data);
                if (!vr.valid()) {
                    return Promise.of(errorResponse(422, "Schema validation failed: " + vr.violationSummary()));
                }
            }
            
            return client.save(resolvedTenantId, collection, data)
                .then(entity -> {
                    // Emit entity CDC event for real-time stream subscribers
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.saved", Map.of(
                        "collection", entity.collection(),
                        "id", entity.id(),
                        "version", entity.version(),
                        "operation", "upsert"
                    ));
                    return client.appendEvent(resolvedTenantId, cdcEvent)
                        .map(ignored -> entity);
                })
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

        // API boundary validation
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(errorResponse(400, idErr.get()));

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

        // API boundary validation
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(errorResponse(400, limitResult.getError().orElseThrow()));
        int limit = limitResult.getValue();

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
        final String resolvedTenantId = tenantId;

        // API boundary validation
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(resolvedTenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));
        Optional<String> idErr = ApiInputValidator.validateId(id);
        if (idErr.isPresent()) return Promise.of(errorResponse(400, idErr.get()));

        return client.delete(resolvedTenantId, collection, id)
            .then(v -> {
                // Emit entity CDC event for real-time stream subscribers
                DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.deleted", Map.of(
                    "collection", collection,
                    "id", id,
                    "operation", "delete"
                ));
                return client.appendEvent(resolvedTenantId, cdcEvent)
                    .map(ignored -> v);
            })
            .map(v -> jsonResponse(Map.of(
                "deleted", true,
                "id", id,
                "collection", collection,
                "timestamp", Instant.now().toString()
            )));
    }

    // ==================== Bulk Entity Endpoints ====================

    /**
     * POST /api/v1/entities/:collection/batch
     *
     * <p>Upserts up to 500 entities atomically within a single collection.
     * Request body: {@code {"entities": [{...}, ...]}}
     * Response: {@code {"saved": N, "ids": [...], "errors": []}}
     *
     * <p>Each entity is saved independently via {@code Promise.all()}; partial
     * failure is reported per-item in the {@code errors} array while successful
     * saves are still committed.
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleBatchSaveEntities(HttpRequest request) {
        try {
            String collection = request.getPathParameter("collection");
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";

            // API boundary validation
            Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
            if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
            Optional<String> collErr = ApiInputValidator.validateCollection(collection);
            if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

            final String resolvedTenant = tenantId;

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(body, Map.class);

            Object rawEntities = payload.get("entities");
            if (!(rawEntities instanceof List)) {
                return Promise.of(errorResponse(400, "Request body must contain an 'entities' array"));
            }

            List<Map<String, Object>> entityList = (List<Map<String, Object>>) rawEntities;
            Optional<String> batchErr = ApiInputValidator.validateBatchSize(entityList);
            if (batchErr.isPresent()) return Promise.of(errorResponse(400, batchErr.get()));

            // Schema validation — validate all entities up-front before any save
            if (schemaValidator != null) {
                List<String> allViolations = new ArrayList<>();
                for (int i = 0; i < entityList.size(); i++) {
                    ValidationResult vr = schemaValidator.validate(resolvedTenant, collection, entityList.get(i));
                    if (!vr.valid()) {
                        allViolations.add("[" + i + "] " + vr.violationSummary());
                    }
                }
                if (!allViolations.isEmpty()) {
                    return Promise.of(errorResponse(422, "Batch schema validation failed: " + String.join("; ", allViolations)));
                }
            }

            List<Promise<DataCloudClient.Entity>> savePromises = entityList.stream()
                .map(data -> client.save(resolvedTenant, collection, data))
                .toList();

            return Promises.toList(savePromises)
                .then(savedEntities -> {
                    List<String> ids = savedEntities.stream()
                        .map(DataCloudClient.Entity::id)
                        .toList();
                    // Emit a single bulk CDC event for batch operations
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-saved", Map.of(
                        "collection", collection,
                        "count", savedEntities.size(),
                        "ids", ids,
                        "operation", "batch-upsert"
                    ));
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> jsonResponse(Map.of(
                            "saved", savedEntities.size(),
                            "collection", collection,
                            "ids", ids,
                            "errors", List.of(),
                            "timestamp", Instant.now().toString()
                        )));
                })
                .then(Promise::of, e -> {
                    log.error("Batch save failed for collection {}", collection, e);
                    return Promise.of(errorResponse(500, "Batch save failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("Error parsing batch save request", e);
            return Promise.of(errorResponse(400, "Invalid batch request body: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/v1/entities/:collection/batch
     *
     * <p>Deletes up to 500 entities by ID in a single request.
     * Request body: {@code {"ids": ["id1", "id2", ...]}}
     * Response: {@code {"deleted": N, "ids": [...]}}
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleBatchDeleteEntities(HttpRequest request) {
        try {
            String collection = request.getPathParameter("collection");
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";

            // API boundary validation
            Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
            if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
            Optional<String> collErr = ApiInputValidator.validateCollection(collection);
            if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

            final String resolvedTenant = tenantId;

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(body, Map.class);

            Object rawIds = payload.get("ids");
            if (!(rawIds instanceof List)) {
                return Promise.of(errorResponse(400, "Request body must contain an 'ids' array"));
            }

            List<String> ids = (List<String>) rawIds;
            Optional<String> batchErr = ApiInputValidator.validateDeleteBatch(ids);
            if (batchErr.isPresent()) return Promise.of(errorResponse(400, batchErr.get()));

            List<Promise<Void>> deletePromises = ids.stream()
                .map(id -> client.delete(resolvedTenant, collection, id))
                .toList();

            return Promises.all(deletePromises)
                .then(v -> {
                    // Emit a single bulk CDC event for the batch delete
                    DataCloudClient.Event cdcEvent = DataCloudClient.Event.of("entity.batch-deleted", Map.of(
                        "collection", collection,
                        "count", ids.size(),
                        "ids", ids,
                        "operation", "batch-delete"
                    ));
                    return client.appendEvent(resolvedTenant, cdcEvent)
                        .map(ignored -> jsonResponse(Map.of(
                            "deleted", ids.size(),
                            "collection", collection,
                            "ids", ids,
                            "timestamp", Instant.now().toString()
                        )));
                })
                .then(Promise::of, e -> {
                    log.error("Batch delete failed for collection {}", collection, e);
                    return Promise.of(errorResponse(500, "Batch delete failed: " + e.getMessage()));
                });
        } catch (Exception e) {
            log.error("Error parsing batch delete request", e);
            return Promise.of(errorResponse(400, "Invalid batch request body: " + e.getMessage()));
        }
    }

    // ==================== Export & Anomaly Endpoints ====================

    /**
     * GET /api/v1/entities/:collection/export
     *
     * <p>Exports all matching entities as CSV or NDJSON.
     * <p>Query parameters:
     * <ul>
     *   <li>{@code tenantId} — tenant scope (default: {@code "default"})</li>
     *   <li>{@code format}   — {@code csv} (default) or {@code ndjson}</li>
     *   <li>{@code limit}    — max entities to export (default/max: 100 000)</li>
     * </ul>
     */
    private Promise<HttpResponse> handleExportEntities(HttpRequest request) {
        if (exportService == null) {
            return Promise.of(errorResponse(501, "Export service not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

        String format   = request.getQueryParameter("format");
        if (format == null) format = "csv";

        int limit = 10_000;
        String limitStr = request.getQueryParameter("limit");
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                return Promise.of(errorResponse(400, "Invalid 'limit' query parameter: " + limitStr));
            }
        }

        final String finalTenant    = tenantId;
        final String finalCollection = collection;
        final int    finalLimit     = limit;

        if ("ndjson".equalsIgnoreCase(format)) {
            return exportService.exportNdjson(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/x-ndjson; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("NDJSON export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        } else {
            return exportService.exportCsv(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/csv; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("CSV export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        }
    }

    /**
     * POST /api/v1/entities/:collection/anomalies
     *
     * <p>Runs statistical anomaly detection over the collection and returns
     * a list of anomaly records.
     * <p>Request body (all fields optional):
     * <pre>{@code
     * {
     *   "threshold":     3.0,
     *   "detectionType": "DATA_QUALITY"
     * }
     * }</pre>
     * <p>Response: {@code {"anomalies": [...], "count": N, "collection": "..."}}
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleDetectAnomalies(HttpRequest request) {
        if (anomalyDetector == null) {
            return Promise.of(errorResponse(501, "Anomaly detection not configured on this server"));
        }

        try {
            String collection = request.getPathParameter("collection");
            String tenantId   = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";

            Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
            if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));
            Optional<String> collErr = ApiInputValidator.validateCollection(collection);
            if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

            final String finalTenant    = tenantId;
            final String finalCollection = collection;

            // Parse optional body
            double threshold = StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD;
            DetectionType detectionType = DetectionType.DATA_QUALITY;

            String rawBody = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            if (rawBody != null && !rawBody.isBlank()) {
                Map<String, Object> body = objectMapper.readValue(rawBody, Map.class);
                if (body.containsKey("threshold")) {
                    Object t = body.get("threshold");
                    threshold = t instanceof Number n ? n.doubleValue() : Double.parseDouble(t.toString());
                }
                if (body.containsKey("detectionType")) {
                    try {
                        detectionType = DetectionType.valueOf(body.get("detectionType").toString());
                    } catch (IllegalArgumentException e) {
                        return Promise.of(errorResponse(400, "Unknown detectionType: " + body.get("detectionType")));
                    }
                }
            }

            AnomalyContext ctx = AnomalyContext.builder()
                    .tenantId(finalTenant)
                    .collectionName(finalCollection)
                    .detectionType(detectionType)
                    .threshold(threshold)
                    .build();

            final Map<String, Object> responseEnvelope = Map.of(
                    "collection", finalCollection,
                    "tenant", finalTenant,
                    "timestamp", Instant.now().toString());

            return anomalyDetector.detect(ctx)
                    .map(anomalies -> {
                        Map<String, Object> body2 = new LinkedHashMap<>(responseEnvelope);
                        body2.put("count", anomalies.size());
                        body2.put("anomalies", anomalies);
                        return jsonResponse(body2);
                    })
                    .then(Promise::of, e -> {
                        log.error("Anomaly detection failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(errorResponse(500, "Anomaly detection failed: " + e.getMessage()));
                    });
        } catch (Exception e) {
            log.error("Error processing anomaly detection request", e);
            return Promise.of(errorResponse(400, "Invalid request body: " + e.getMessage()));
        }
    }

    // ==================== Event Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        try {
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null) tenantId = "default";

            // API boundary validation
            Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
            if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));

            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> eventData = objectMapper.readValue(body, Map.class);

            Optional<String> payloadErr = ApiInputValidator.validateEntityPayload(eventData);
            if (payloadErr.isPresent()) return Promise.of(errorResponse(400, payloadErr.get()));

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

        // API boundary validation
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(errorResponse(400, limitResult.getError().orElseThrow()));
        int limit = limitResult.getValue();

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

    // ==================== Pipeline Registry Endpoints (AEP integration) ====================

    private static final String DC_PIPELINES_COLLECTION = "aep_pipelines";

    /**
     * Lists all pipeline definitions for the requesting tenant.
     *
     * <p>Supports optional {@code ?tenantId=} query parameter; falls back to the
     * {@code X-Tenant-Id} header (used by AEP's PipelineRegistryClient).
     */
    private Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        String tenantId = resolveQueryOrHeaderTenantId(request);
        int limit = parseIntParam(request.getQueryParameter("limit"), 500);
        return client.query(tenantId, DC_PIPELINES_COLLECTION, DataCloudClient.Query.limit(limit))
                .map(entities -> {
                    List<Map<String, Object>> pipelines = entities.stream()
                            .map(e -> flattenPipelineEntity(e, tenantId))
                            .toList();
                    return jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "pipelines", pipelines,
                            "count", pipelines.size(),
                            "timestamp", Instant.now().toString()));
                });
    }

    /**
     * Saves (creates) a new pipeline definition in Data-Cloud.
     *
     * <p>Request body must be valid JSON. The {@code id} field is required.
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleSavePipeline(HttpRequest request) {
        String tenantId = resolveQueryOrHeaderTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, Map.class);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> jsonResponse(flattenPipelineEntity(entity, tenantId)));
            } catch (Exception e) {
                log.warn("[DC-Pipelines] save failed tenant={}: {}", tenantId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid pipeline definition: " + e.getMessage()));
            }
        });
    }

    /**
     * Gets a single pipeline definition by ID.
     */
    private Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        String tenantId = resolveQueryOrHeaderTenantId(request);
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.findById(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(opt -> opt
                        .map(e -> jsonResponse(flattenPipelineEntity(e, tenantId)))
                        .orElse(errorResponse(404, "Pipeline not found: " + pipelineId)));
    }

    /**
     * Updates an existing pipeline definition.
     */
    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleUpdatePipeline(HttpRequest request) {
        String tenantId = resolveQueryOrHeaderTenantId(request);
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(errorResponse(400, "pipelineId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> data = objectMapper.readValue(body, Map.class);
                // Ensure id consistency
                data.put("id", pipelineId);
                return client.save(tenantId, DC_PIPELINES_COLLECTION, data)
                        .map(entity -> jsonResponse(flattenPipelineEntity(entity, tenantId)));
            } catch (Exception e) {
                log.warn("[DC-Pipelines] update failed pipelineId={} tenant={}: {}", pipelineId, tenantId, e.getMessage());
                return Promise.of(errorResponse(400, "Invalid pipeline update: " + e.getMessage()));
            }
        });
    }

    /**
     * Deletes a pipeline definition from Data-Cloud.
     */
    private Promise<HttpResponse> handleDeletePipeline(HttpRequest request) {
        String tenantId = resolveQueryOrHeaderTenantId(request);
        String pipelineId = request.getPathParameter("pipelineId");
        if (pipelineId == null || pipelineId.isBlank()) {
            return Promise.of(errorResponse(400, "pipelineId path parameter is required"));
        }
        return client.delete(tenantId, DC_PIPELINES_COLLECTION, pipelineId)
                .map(v -> jsonResponse(Map.of(
                        "deleted", true,
                        "pipelineId", pipelineId,
                        "tenantId", tenantId,
                        "timestamp", Instant.now().toString())));
    }

    /** Flattens a DataCloud entity into a pipeline-shaped map. */
    private Map<String, Object> flattenPipelineEntity(DataCloudClient.Entity e, String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.id());
        result.put("tenantId", tenantId);
        if (e.data() != null) {
            result.putAll(e.data());
        }
        return result;
    }

    /** Resolves tenantId from query param first, then header, falling back to "default". */
    private String resolveQueryOrHeaderTenantId(HttpRequest request) {
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }
        return resolveTenantId(request);
    }

    /** Parses an integer query param, returning {@code defaultValue} on null or parse failure. */
    private int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
     * Entity CDC (Change Data Capture) Server-Sent Events stream.
     *
     * <p>Tails the event log for entity mutation events in the requested collection,
     * pushing real-time change notifications to the client as entities are saved
     * or deleted. Every successful {@code POST /api/v1/entities/:collection},
     * {@code DELETE /api/v1/entities/:collection/:id}, or batch equivalent
     * appends an event ({@code entity.saved}, {@code entity.deleted},
     * {@code entity.batch-saved}, {@code entity.batch-deleted}) to the log,
     * which this stream surfaces.
     *
     * <h3>Route</h3>
     * {@code GET /api/v1/entities/:collection/stream}
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code fromOffset} – starting log offset (inclusive, default 0)</li>
     * </ul>
     *
     * <h3>SSE frame types</h3>
     * <ul>
     *   <li>{@code connected}     – sent immediately on connection</li>
     *   <li>{@code entity-change} – one frame per entity mutation</li>
     *   <li>{@code heartbeat}     – sent every {@value #SSE_HEARTBEAT_TIMEOUT_SEC} s when idle</li>
     * </ul>
     *
     * @param request the incoming HTTP request
     * @return a streaming {@code text/event-stream} response
     *
     * @doc.type method
     * @doc.purpose Entity CDC real-time push stream filtered by collection (DC-3)
     * @doc.layer product
     * @doc.pattern SSE Adapter, CDC, Event Tailing
     */
    private Promise<HttpResponse> handleEntityCdcStream(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String collection = request.getPathParameter("collection");

        if (collection == null || collection.isBlank()) {
            return Promise.of(errorResponse(400, "collection path parameter is required"));
        }

        long fromOffsetVal = parseLongParam(request.getQueryParameter("fromOffset"), 0L);

        // Entity CDC event types - emitted by save/delete handlers
        Set<String> entityEventTypes = Set.of(
            "entity.saved", "entity.deleted",
            "entity.batch-saved", "entity.batch-deleted"
        );

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        // Send "connected" acknowledgement immediately
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
                "service", "data-cloud",
                "tenantId", tenantId,
                "collection", collection,
                "fromOffset", String.valueOf(fromOffsetVal),
                "timestamp", Instant.now().toString()
        ))));

        EventLogStore eventLogStore = client.eventLogStore();

        return eventLogStore.tail(TenantContext.of(tenantId), Offset.of(fromOffsetVal), entry -> {
            // Filter: only entity mutation events for the requested collection
            if (!entityEventTypes.contains(entry.eventType())) {
                return;
            }
            try {
                // Parse the payload to check the collection field
                byte[] payloadBytes = new byte[entry.payload().remaining()];
                entry.payload().duplicate().get(payloadBytes);
                String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = objectMapper.readValue(payloadStr, Map.class);

                if (!collection.equals(payloadMap.get("collection"))) {
                    return; // Event is for a different collection
                }

                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("collection", collection);
                frame.put("operation", payloadMap.getOrDefault("operation", "unknown"));
                frame.put("eventType", entry.eventType());
                frame.put("tenantId", tenantId);
                frame.put("timestamp", entry.timestamp().toString());
                if (payloadMap.containsKey("id")) {
                    frame.put("id", payloadMap.get("id"));
                }
                if (payloadMap.containsKey("ids")) {
                    frame.put("ids", payloadMap.get("ids"));
                }
                if (payloadMap.containsKey("version")) {
                    frame.put("version", payloadMap.get("version"));
                }
                if (payloadMap.containsKey("count")) {
                    frame.put("count", payloadMap.get("count"));
                }

                byte[] sseFrame = buildSseFrame("entity-change", frame);
                if (!queue.offer(Optional.of(sseFrame), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[CDC] queue full for tenant={} collection={}, dropping change event",
                        tenantId, collection);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("[CDC] frame build error for tenant={} collection={}: {}",
                    tenantId, collection, ex.getMessage());
            }
        }).map(subscription -> {
            sseSubscriptions.add(subscription);

            ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
                if (subscription.isCancelled()) {
                    return Promise.of(null);
                }
                return Promise.ofBlocking(blockingExecutor, () -> {
                    if (subscription.isCancelled()) {
                        return null;
                    }
                    try {
                        Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                        if (item == null) {
                            return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                    Map.of("ts", Instant.now().toString())));
                        }
                        return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                });
            });

            log.info("[CDC] stream opened for tenant={} collection={} fromOffset={}",
                    tenantId, collection, fromOffsetVal);

            return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
                    .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
                    .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
                    .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                    .withBodyStream(bodyStream)
                    .build();
        }).mapException(e -> {
            log.error("[CDC] failed to open tail subscription for tenant={} collection={}: {}",
                tenantId, collection, e.getMessage(), e);
            return new HttpException("CDC subscription failed: " + e.getMessage(), e);
        });
    }

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
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
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

    // ==================== Memory - Additional Endpoints (DC-4) ====================

    /**
     * Full-text / attribute search across an agent's memory.
     *
     * <p>Accepts a JSON body with optional {@code query} (string), {@code type} (memory tier),
     * and {@code limit} (max results, capped at 1 000).
     *
     * @param request the incoming HTTP request
     * @return 200 with matching memory items, 400 on invalid body
     *
     * @doc.type method
     * @doc.purpose Memory search endpoint (DC-4)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleSearchAgentMemory(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body.asArray(), Map.class);
                    String queryStr = (String) req.getOrDefault("query", "");
                    String type    = (String) req.get("type");
                    int limit = req.containsKey("limit")
                        ? Math.min(((Number) req.get("limit")).intValue(), 1000)
                        : 100;

                    List<DataCloudClient.Filter> filters = new ArrayList<>();
                    filters.add(DataCloudClient.Filter.eq("agentId", agentId));
                    if (type != null && !type.isBlank()) {
                        filters.add(DataCloudClient.Filter.eq("type", type.toUpperCase()));
                    }
                    if (!queryStr.isBlank()) {
                        filters.add(DataCloudClient.Filter.like("content", "%" + queryStr + "%"));
                    }

                    DataCloudClient.Query dcQuery = DataCloudClient.Query.builder()
                        .filters(filters)
                        .limit(limit)
                        .build();

                    return client.query(tenantId, "dc_memory", dcQuery)
                        .map(items -> {
                            List<Map<String, Object>> results = items.stream()
                                .map(e -> Map.<String, Object>of(
                                    "id",        e.id(),
                                    "agentId",   e.data().getOrDefault("agentId", agentId),
                                    "type",      e.data().getOrDefault("type", ""),
                                    "content",   e.data().getOrDefault("content", ""),
                                    "createdAt", e.data().getOrDefault("createdAt", ""),
                                    "metadata",  e.data().getOrDefault("metadata", Map.of())
                                ))
                                .toList();
                            return jsonResponse(Map.of(
                                "agentId",   agentId,
                                "tenantId",  tenantId,
                                "query",     queryStr,
                                "results",   results,
                                "count",     results.size(),
                                "timestamp", Instant.now().toString()
                            ));
                        });
                } catch (Exception e) {
                    return Promise.of(errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory search failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Memory search failed: " + e.getMessage()));
            });
    }

    /**
     * Deletes a specific memory item for an agent.
     *
     * @param request the incoming HTTP request
     * @return 200 on success, 400 on missing params
     *
     * @doc.type method
     * @doc.purpose Memory delete endpoint (DC-4)
     * @doc.layer product
     * @doc.pattern Command
     */
    private Promise<HttpResponse> handleDeleteMemory(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId  = request.getPathParameter("agentId");
        String memoryId = request.getPathParameter("memoryId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (memoryId == null || memoryId.isBlank()) {
            return Promise.of(errorResponse(400, "memoryId path parameter is required"));
        }
        return client.delete(tenantId, "dc_memory", memoryId)
            .map(v -> jsonResponse(Map.of(
                "deleted",   true,
                "memoryId",  memoryId,
                "agentId",   agentId,
                "tenantId",  tenantId,
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[DC-4] memory delete failed id={}: {}", memoryId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Memory delete failed: " + e.getMessage()));
            });
    }

    /**
     * Marks a memory item as retained, preventing automatic eviction.
     *
     * <p>Accepts JSON body with {@code reason} (string) and optional {@code retainUntilEpoch} (ms).
     * Returns 404 when the item does not exist.
     *
     * @param request the incoming HTTP request
     * @return 200 on success, 404 if not found, 400 on invalid body
     *
     * @doc.type method
     * @doc.purpose Memory retain endpoint (DC-4)
     * @doc.layer product
     * @doc.pattern Command
     */
    private Promise<HttpResponse> handleRetainMemory(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String agentId  = request.getPathParameter("agentId");
        String memoryId = request.getPathParameter("memoryId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(errorResponse(400, "agentId path parameter is required"));
        }
        if (memoryId == null || memoryId.isBlank()) {
            return Promise.of(errorResponse(400, "memoryId path parameter is required"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body.asArray(), Map.class);
                    long retainUntilEpoch = req.containsKey("retainUntilEpoch")
                        ? ((Number) req.get("retainUntilEpoch")).longValue() : 0L;
                    String reason = (String) req.getOrDefault("reason", "manual-retain");

                    DataCloudClient.Query query = DataCloudClient.Query.builder()
                        .filter(DataCloudClient.Filter.eq("id", memoryId))
                        .limit(1)
                        .build();

                    return client.query(tenantId, "dc_memory", query)
                        .then(items -> {
                            if (items.isEmpty()) {
                                return Promise.of(errorResponse(404,
                                    "Memory item not found: " + memoryId));
                            }
                            DataCloudClient.Entity entity = items.get(0);
                            Map<String, Object> updated = new LinkedHashMap<>(entity.data());
                            updated.put("retained", true);
                            updated.put("retainReason", reason);
                            if (retainUntilEpoch > 0L) {
                                updated.put("retainUntil",
                                    Instant.ofEpochMilli(retainUntilEpoch).toString());
                            }
                            return client.save(tenantId, "dc_memory", Map.copyOf(updated))
                                .map(v -> jsonResponse(Map.of(
                                    "retained",  true,
                                    "memoryId",  memoryId,
                                    "agentId",   agentId,
                                    "tenantId",  tenantId,
                                    "reason",    reason,
                                    "timestamp", Instant.now().toString()
                                )));
                        });
                } catch (Exception e) {
                    return Promise.of(errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] retain memory failed id={}: {}", memoryId, e.getMessage(), e);
                return Promise.of(errorResponse(500, "Memory retain failed: " + e.getMessage()));
            });
    }

    // ==================== Brain - Additional Endpoints (DC-6) ====================

    /**
     * SSE stream of spotlight updates from the global brain workspace.
     *
     * <p>Immediately pushes the current spotlight snapshot for the tenant, then streams
     * live {@code spotlight} events as items are added/evicted.  Heartbeats are sent every
     * {@value #SSE_HEARTBEAT_TIMEOUT_SEC} seconds when idle to keep the TCP connection alive.
     * Returns HTTP 503 when the brain or workspace is unavailable.
     *
     * @param request the incoming HTTP request
     * @return SSE response, or 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Brain workspace SSE stream (DC-6)
     * @doc.layer product
     * @doc.pattern Publish-Subscribe, SSE
     */
    private Promise<HttpResponse> handleBrainWorkspaceStream(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        Optional<GlobalWorkspace> wsOpt = brain.getWorkspace();
        if (wsOpt.isEmpty()) {
            return Promise.of(errorResponse(503,
                "Workspace stream not available for this brain implementation"));
        }
        GlobalWorkspace workspace = wsOpt.get();
        String tenantId = resolveTenantId(request);

        LinkedBlockingQueue<Optional<byte[]>> queue =
            new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        // Push current spotlight snapshot for this tenant
        workspace.getByTenant(tenantId).forEach(item ->
            queue.offer(Optional.of(buildWorkspaceSseFrame(item))));

        // "connected" ack
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",   "data-cloud-brain",
            "tenantId",  tenantId,
            "timestamp", Instant.now().toString()
        ))));

        GlobalWorkspace.Subscription subscription = workspace.subscribe(item -> {
            // Filter to the requested tenant (pass-through for "default")
            if (!"default".equals(tenantId) && !tenantId.equals(item.getTenantId())) {
                return;
            }
            try {
                if (!queue.offer(Optional.of(buildWorkspaceSseFrame(item)),
                        100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[SSE-WS] queue full for tenant={}, dropping item", tenantId);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
            if (!subscription.isActive()) return Promise.of(null);
            return Promise.ofBlocking(blockingExecutor, () -> {
                if (!subscription.isActive()) return null;
                try {
                    Optional<byte[]> item =
                        queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                            Map.of("ts", Instant.now().toString())));
                    }
                    return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
        });

        log.info("[SSE-WS] brain workspace stream opened for tenant={}", tenantId);
        return Promise.of(HttpResponse.ok200()
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
            .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
            .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
            .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
            .withBodyStream(bodyStream)
            .build());
    }

    /**
     * Directly elevates a data record to the global workspace, bypassing salience scoring.
     *
     * <p>Accepts a JSON body with {@code id}, {@code content}, {@code reason}, and optional
     * {@code emergency} (boolean).  Returns 503 when the brain or attention manager is unavailable.
     *
     * @param request the incoming HTTP request
     * @return 200 with elevation result, or 503 / 400 on error
     *
     * @doc.type method
     * @doc.purpose Attention elevation endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Command
     */
    private Promise<HttpResponse> handleBrainAttentionElevate(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        Optional<AttentionManager> amOpt = brain.getAttentionManager();
        if (amOpt.isEmpty()) {
            return Promise.of(errorResponse(503,
                "Attention manager not available for this brain implementation"));
        }
        AttentionManager am = amOpt.get();
        String tenantId = resolveTenantId(request);

        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body.asArray(), Map.class);
                    String id       = (String) req.getOrDefault("id",
                        java.util.UUID.randomUUID().toString());
                    String content  = (String) req.getOrDefault("content", "");
                    String reason   = (String) req.getOrDefault("reason", "manual-api-elevation");
                    boolean emergency = Boolean.TRUE.equals(req.get("emergency"));

                    EntityRecord record = EntityRecord.builder()
                        .id(java.util.UUID.fromString(id))
                        .tenantId(tenantId)
                        .collectionName("api-elevation")
                        .data(Map.of("content", content))
                        .build();

                    return am.elevate(record, reason, emergency)
                        .map(result -> jsonResponse(Map.of(
                            "elevated",  result.wasElevated(),
                            "emergency", result.wasEmergency(),
                            "action",    result.getAction().name(),
                            "recordId",  id,
                            "reason",    reason,
                            "tenantId",  tenantId,
                            "timestamp", Instant.now().toString()
                        )));
                } catch (IllegalArgumentException e) {
                    return Promise.of(errorResponse(400,
                        "Invalid 'id' — must be a valid UUID: " + e.getMessage()));
                } catch (Exception e) {
                    return Promise.of(errorResponse(400,
                        "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] attention elevate failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(500,
                    "Attention elevation failed: " + e.getMessage()));
            });
    }

    /**
     * Returns the current attention thresholds and statistics.
     *
     * <p>Returns 503 when the brain is unavailable.
     *
     * @param request the incoming HTTP request
     * @return 200 with threshold values
     *
     * @doc.type method
     * @doc.purpose Attention thresholds read endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Configuration Query
     */
    private Promise<HttpResponse> handleBrainAttentionThresholds(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        Optional<AttentionManager> amOpt = brain.getAttentionManager();
        BrainConfig cfg = brain.getConfig();
        if (amOpt.isEmpty()) {
            return Promise.of(jsonResponse(Map.of(
                "elevationThreshold", AttentionManager.DEFAULT_ELEVATION_THRESHOLD,
                "emergencyThreshold", AttentionManager.DEFAULT_EMERGENCY_THRESHOLD,
                "salienceThreshold",  (double) cfg.getSalienceThreshold(),
                "source",             "defaults",
                "timestamp",          Instant.now().toString()
            )));
        }
        AttentionManager.AttentionStats stats = amOpt.get().getStats();
        return Promise.of(jsonResponse(Map.of(
            "elevationThreshold", stats.elevationThreshold(),
            "emergencyThreshold", stats.emergencyThreshold(),
            "salienceThreshold",  (double) cfg.getSalienceThreshold(),
            "totalProcessed",     stats.totalProcessed(),
            "elevatedCount",      stats.elevatedCount(),
            "emergencyCount",     stats.emergencyCount(),
            "elevationRate",      stats.elevationRate(),
            "timestamp",          Instant.now().toString()
        )));
    }

    /**
     * Acknowledges a threshold update request.
     *
     * <p>Because the {@link AttentionManager} is immutable after construction, live threshold
     * changes require a restart with updated configuration.  This endpoint surfaces that
     * constraint to callers rather than silently ignoring the request.
     *
     * @param request the incoming HTTP request
     * @return 200 with acknowledgement and restart guidance
     *
     * @doc.type method
     * @doc.purpose Attention thresholds update endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Configuration
     */
    private Promise<HttpResponse> handleBrainAttentionThresholdsUpdate(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        return request.loadBody()
            .map(body -> jsonResponse(Map.of(
                "acknowledged", true,
                "note",         "Threshold changes require restarting the brain with updated BrainConfig. "
                              + "Set DATACLOUD_BRAIN_ELEVATION_THRESHOLD and DATACLOUD_BRAIN_EMERGENCY_THRESHOLD env vars.",
                "timestamp",    Instant.now().toString()
            )));
    }

    /**
     * Lists active patterns known to the brain.
     *
     * <p>Optional query parameter {@code limit} (default 100, max 1 000).
     * Returns 503 when the brain is unavailable.
     *
     * @param request the incoming HTTP request
     * @return 200 with pattern list
     *
     * @doc.type method
     * @doc.purpose Brain pattern listing endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleBrainPatterns(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        int limit = Math.min(parseLimitParam(request.getQueryParameter("limit"), 100), 1000);
        BrainContext ctx = BrainContext.forTenant(tenantId);

        return brain.listPatterns(limit, ctx)
            .map(patterns -> {
                List<Map<String, Object>> patternList = patterns.stream()
                    .map(p -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id",          p.getId());
                        entry.put("name",        p.getName() != null ? p.getName() : "");
                        entry.put("type",        p.getType() != null ? p.getType().name() : "UNKNOWN");
                        entry.put("description", p.getDescription() != null ? p.getDescription() : "");
                        entry.put("confidence",  p.getConfidence());
                        entry.put("observations", p.getObservationCount());
                        entry.put("discoveredAt", p.getDiscoveredAt().toString());
                        entry.put("updatedAt",   p.getUpdatedAt().toString());
                        return Map.copyOf(entry);
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "patterns",  patternList,
                    "count",     patternList.size(),
                    "limit",     limit,
                    "tenantId",  tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] list patterns failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(500,
                    "Failed to list patterns: " + e.getMessage()));
            });
    }

    /**
     * Matches a record payload against the brain's known patterns.
     *
     * <p>Accepts a JSON body with {@code id} (UUID, optional), {@code content} (string),
     * {@code type} (string), and {@code attributes} (object).
     * Returns 503 when brain unavailable, 400 on invalid body.
     *
     * @param request the incoming HTTP request
     * @return 200 with match list
     *
     * @doc.type method
     * @doc.purpose Pattern-match endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleBrainPatternsMatch(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        BrainContext ctx = BrainContext.forTenant(tenantId);

        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body.asArray(), Map.class);
                    String rawId   = (String) req.get("id");
                    java.util.UUID id = rawId != null
                        ? java.util.UUID.fromString(rawId)
                        : java.util.UUID.randomUUID();
                    String content = (String) req.getOrDefault("content", "");
                    String type    = (String) req.getOrDefault("type", "QUERY");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes =
                        (Map<String, Object>) req.getOrDefault("attributes", Map.of());

                    EntityRecord record = EntityRecord.builder()
                        .id(id)
                        .tenantId(tenantId)
                        .collectionName("api-match")
                        .data(Map.of("content", content, "type", type))
                        .metadata(attributes)
                        .build();

                    return brain.matchPatterns(record, ctx)
                        .map(matches -> {
                            List<Map<String, Object>> matchList = matches.stream()
                                .map(m -> {
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    if (m.getPattern() != null) {
                                        entry.put("patternId",   m.getPattern().getId());
                                        entry.put("patternName", m.getPattern().getName());
                                    }
                                    entry.put("score",       m.getScore());
                                    entry.put("confidence",  m.getConfidence());
                                    entry.put("explanation", m.getExplanation() != null
                                        ? m.getExplanation() : "");
                                    return Map.copyOf(entry);
                                })
                                .toList();
                            return jsonResponse(Map.of(
                                "recordId",  id.toString(),
                                "matches",   matchList,
                                "count",     matchList.size(),
                                "tenantId",  tenantId,
                                "timestamp", Instant.now().toString()
                            ));
                        });
                } catch (IllegalArgumentException e) {
                    return Promise.of(errorResponse(400,
                        "Invalid 'id' — must be a valid UUID: " + e.getMessage()));
                } catch (Exception e) {
                    return Promise.of(errorResponse(400,
                        "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] pattern match failed: {}", e.getMessage(), e);
                return Promise.of(errorResponse(500,
                    "Pattern match failed: " + e.getMessage()));
            });
    }

    /**
     * Returns the current salience information for a specific spotlight item.
     *
     * <p>Looks up the item in the global workspace spotlight by ID.
     * Returns 404 when the item is not currently in the spotlight.
     * Returns 503 when the brain or workspace is unavailable.
     *
     * @param request the incoming HTTP request
     * @return 200 with salience details, 404 if not in spotlight, 503 if brain unavailable
     *
     * @doc.type method
     * @doc.purpose Salience lookup endpoint (DC-6)
     * @doc.layer product
     * @doc.pattern Query
     */
    private Promise<HttpResponse> handleBrainSalience(HttpRequest request) {
        if (brain == null) {
            return Promise.of(errorResponse(503, "Brain not available in this deployment"));
        }
        String itemId = request.getPathParameter("itemId");
        if (itemId == null || itemId.isBlank()) {
            return Promise.of(errorResponse(400, "itemId path parameter is required"));
        }
        Optional<GlobalWorkspace> wsOpt = brain.getWorkspace();
        if (wsOpt.isEmpty()) {
            return Promise.of(errorResponse(503,
                "Workspace not available for this brain implementation"));
        }
        Optional<SpotlightItem> item = wsOpt.get().get(itemId);
        if (item.isEmpty()) {
            return Promise.of(errorResponse(404,
                "Item not found in workspace spotlight: " + itemId));
        }
        SpotlightItem si = item.get();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("itemId",        itemId);
        resp.put("salienceScore", si.getSalienceScore().getScore());
        resp.put("isHigh",        si.getSalienceScore().isHigh());
        resp.put("isEmergency",   si.isEmergency());
        resp.put("priority",      si.getPriority());
        resp.put("summary",       si.getSummary() != null ? si.getSummary() : "");
        resp.put("tenantId",      si.getTenantId());
        resp.put("spotlightedAt", si.getSpotlightedAt().toString());
        resp.put("timestamp",     Instant.now().toString());
        return Promise.of(jsonResponse(Map.copyOf(resp)));
    }

    // ==================== SSE Streams - Additional (DC-9) ====================

    /**
     * SSE stream of agent-lifecycle events (create, start, stop, fail, checkpoint).
     *
     * <p>Tails the event log and filters to agent-specific event types.
     * Heartbeats are sent every {@value #SSE_HEARTBEAT_TIMEOUT_SEC} seconds when idle.
     *
     * @param request the incoming HTTP request
     * @return SSE response
     *
     * @doc.type method
     * @doc.purpose Agent events SSE stream (DC-9)
     * @doc.layer product
     * @doc.pattern Publish-Subscribe, SSE
     */
    private Promise<HttpResponse> handleAgentsEventStream(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        LinkedBlockingQueue<Optional<byte[]>> queue =
            new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        List<String> agentEventTypes = List.of(
            "AGENT_CREATED", "AGENT_STARTED", "AGENT_STOPPED",
            "AGENT_FAILED", "AGENT_UPDATED", "CHECKPOINT_SAVED");

        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",   "data-cloud-agents",
            "tenantId",  tenantId,
            "filter",    agentEventTypes,
            "timestamp", Instant.now().toString()
        ))));

        TenantContext tenant = TenantContext.of(tenantId);
        EventLogStore eventLogStore = client.eventLogStore();

        return eventLogStore.tail(tenant, Offset.of(0L), entry -> {
            if (!agentEventTypes.contains(entry.eventType())) return;
            try {
                byte[] frame = buildEventSseFrame(entry);
                if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                    log.warn("[SSE-AGENTS] queue full for tenant={}, dropping event type={}",
                        tenantId, entry.eventType());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("[SSE-AGENTS] serialization error for tenant={}: {}",
                    tenantId, ex.getMessage());
            }
        }).map(subscription -> {
            sseSubscriptions.add(subscription);
            ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
                if (subscription.isCancelled()) return Promise.of(null);
                return Promise.ofBlocking(blockingExecutor, () -> {
                    if (subscription.isCancelled()) return null;
                    try {
                        Optional<byte[]> item =
                            queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                        if (item == null) {
                            return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                Map.of("ts", Instant.now().toString())));
                        }
                        return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                });
            });
            log.info("[SSE-AGENTS] agents event stream opened for tenant={}", tenantId);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
                .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
                .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
                .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                .withBodyStream(bodyStream)
                .build();
        }).mapException(e -> {
            log.error("[SSE-AGENTS] failed to open tail for tenant={}: {}",
                tenantId, e.getMessage(), e);
            return new HttpException("Agents SSE subscription failed: " + e.getMessage(), e);
        });
    }

    /**
     * SSE stream of learning-bridge status updates, pushed every 30 seconds.
     *
     * <p>Returns 503 when the learning bridge is not wired.
     * Heartbeats are sent when idle to keep the connection alive.
     *
     * @param request the incoming HTTP request
     * @return SSE response, or 503 if bridge unavailable
     *
     * @doc.type method
     * @doc.purpose Learning status SSE stream (DC-9)
     * @doc.layer product
     * @doc.pattern Publish-Subscribe, SSE
     */
    private Promise<HttpResponse> handleLearningStream(HttpRequest request) {
        if (learningBridge == null) {
            return Promise.of(errorResponse(503,
                "Learning bridge not available in this deployment"));
        }
        String tenantId = resolveTenantId(request);
        LinkedBlockingQueue<Optional<byte[]>> queue =
            new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",   "data-cloud-learning",
            "tenantId",  tenantId,
            "timestamp", Instant.now().toString()
        ))));

        // Background virtual thread: push periodic status updates
        Thread.ofVirtual().name("learning-sse-" + tenantId).start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(java.time.Duration.ofSeconds(30));
                    Map<String, Object> status = learningBridge.getStatus();
                    Map<String, Object> payload = new LinkedHashMap<>(status);
                    payload.put("tenantId",  tenantId);
                    payload.put("timestamp", Instant.now().toString());
                    byte[] frame = buildSseFrame("learning-status", Map.copyOf(payload));
                    if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                        log.warn("[SSE-LEARN] queue full for tenant={}", tenantId);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("[SSE-LEARN] error pushing status for tenant={}: {}",
                        tenantId, e.getMessage());
                }
            }
            // EOS sentinel
            queue.offer(Optional.empty());
        });

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() ->
            Promise.ofBlocking(blockingExecutor, () -> {
                try {
                    Optional<byte[]> item =
                        queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                            Map.of("ts", Instant.now().toString())));
                    }
                    return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            })
        );

        log.info("[SSE-LEARN] learning stream opened for tenant={}", tenantId);
        return Promise.of(HttpResponse.ok200()
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
            .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
            .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
            .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
            .withBodyStream(bodyStream)
            .build());
    }

    /**
     * Builds an SSE {@code spotlight} frame from a {@link SpotlightItem}.
     *
     * @param item spotlight item
     * @return UTF-8 encoded SSE frame bytes
     */
    private byte[] buildWorkspaceSseFrame(SpotlightItem item) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",           item.getId());
            data.put("tenantId",     item.getTenantId());
            data.put("summary",      item.getSummary() != null ? item.getSummary() : "");
            data.put("salienceScore", item.getSalienceScore().getScore());
            data.put("isHigh",        item.getSalienceScore().isHigh());
            data.put("isEmergency",  item.isEmergency());
            data.put("priority",     item.getPriority());
            data.put("spotlightedAt", item.getSpotlightedAt().toString());
            String json = objectMapper.writeValueAsString(Map.copyOf(data));
            return ("event: spotlight\ndata: " + json + "\n\n")
                .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[SSE-WS] frame build error: {}", e.getMessage());
            return "event: spotlight\ndata: {\"error\":\"serialization failure\"}\n\n"
                .getBytes(StandardCharsets.UTF_8);
        }
    }

    // ==================== Helper Methods ====================

    /** Resolves tenant from {@code X-Tenant-Id} header or query parameter, defaulting to {@code "default"}. */
    private String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

    /**
     * Wraps an {@link AsyncServlet} with a CORS filter.
     *
     * <p>Responds to OPTIONS preflight requests immediately with the CORS policy headers.
     * All other requests are forwarded to the delegate unchanged; individual response
     * builders are responsible for adding CORS headers via {@link #jsonResponse} /
     * {@link #errorResponse}.
     *
     * @param delegate the upstream servlet to forward non-OPTIONS requests to
     * @return CORS-filtered servlet
     *
     * @doc.type method
     * @doc.purpose CORS preflight handler middleware
     * @doc.layer product
     * @doc.pattern Middleware
     */
    private AsyncServlet corsFilter(AsyncServlet delegate) {
        return request -> {
            if (request.getMethod() == HttpMethod.OPTIONS) {
                return Promise.of(HttpResponse.ok200()
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(CORS_ALLOW_METHODS))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(CORS_ALLOW_HEADERS))
                    .withHeader(HttpHeaders.of("Access-Control-Max-Age"),      HttpHeaderValue.of(CORS_MAX_AGE))
                    .build());
            }
            return delegate.serve(request);
        };
    }

    /**
     * Middleware: fixed-window per-IP rate limiter.
     *
     * <p>Each unique remote address is allowed at most {@link #RATE_LIMIT_REQUESTS} requests
     * within a {@link #RATE_LIMIT_WINDOW_MS}-millisecond sliding window.
     * Requests that exceed the limit receive HTTP 429 with a {@code Retry-After} header
     * indicating the seconds remaining until the window resets.
     *
     * <p>The internal state map is bounded to {@link #RATE_LIMIT_MAX_ENTRIES} entries.
     * When the bound is reached the oldest entries are evicted to prevent memory exhaustion
     * under IP-spoofing / amplification attacks.
     *
     * @doc.type method
     * @doc.purpose Protect the HTTP API from per-IP request flooding
     * @doc.layer product
     * @doc.pattern Middleware
     */
    private AsyncServlet rateLimitFilter(AsyncServlet delegate) {
        return request -> {
            String ip = remoteIp(request);
            long now = System.currentTimeMillis();

            long[] state = rateLimitState.compute(ip, (key, existing) -> {
                if (existing == null || (now - existing[1]) >= RATE_LIMIT_WINDOW_MS) {
                    // New window: [count=1, windowStart=now]
                    return new long[]{1L, now};
                }
                existing[0]++;
                return existing;
            });

            long count       = state[0];
            long windowStart = state[1];
            long windowRemainingMs = RATE_LIMIT_WINDOW_MS - (now - windowStart);
            long retryAfterSec = Math.max(1L, (windowRemainingMs + 999) / 1000);

            if (count > RATE_LIMIT_REQUESTS) {
                log.warn("Rate limit exceeded for ip={} count={}", ip, count);
                String body = String.format(
                        "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d}",
                        retryAfterSec);
                return Promise.of(HttpResponse.ofCode(429)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                        .withHeader(HttpHeaders.of("Retry-After"),
                                HttpHeaderValue.of(String.valueOf(retryAfterSec)))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                        .withBody(body.getBytes(StandardCharsets.UTF_8))
                        .build());
            }

            // Evict excess entries after successful admit to keep the map bounded
            if (rateLimitState.size() > RATE_LIMIT_MAX_ENTRIES) {
                rateLimitState.entrySet().removeIf(e ->
                        (now - e.getValue()[1]) >= RATE_LIMIT_WINDOW_MS);
            }

            return delegate.serve(request);
        };
    }

    /**
     * Extracts the originating IP address from the request, honouring
     * {@code X-Forwarded-For} when running behind an NGINX/ingress proxy.
     */
    private static String remoteIp(HttpRequest request) {
        String xff = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may be "client, proxy1, proxy2" — take the first
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).strip();
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(Object::toString)
                .orElse("unknown");
    }

    /**
     * Middleware: enforces {@code Content-Type: application/json} on mutating requests.
     * POST, PUT, and PATCH without a JSON content-type header receive HTTP 415.
     *
     * @doc.type method
     * @doc.purpose Reject non-JSON mutation requests at the servlet boundary
     * @doc.layer product
     * @doc.pattern Middleware
     */
    private AsyncServlet contentTypeFilter(AsyncServlet delegate) {
        return request -> {
            HttpMethod method = request.getMethod();
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                String ct = request.getHeader(HttpHeaders.CONTENT_TYPE);
                if (ct == null || !ct.contains("application/json")) {
                    return Promise.of(HttpResponse.ofCode(415)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                            HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                            HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                        .withBody(("{\"error\":\"Content-Type must be application/json\"}").getBytes(StandardCharsets.UTF_8))
                        .build());
                }
            }
            return delegate.serve(request);
        };
    }

    /**
     * Middleware: rejects requests whose declared {@code Content-Length} exceeds
     * {@link #MAX_BODY_BYTES}.  Guards against payload-based amplification / DoS.
     * Requests with an absent or malformed Content-Length header pass through unchanged.
     * HTTP 413 is returned for oversized payloads.
     *
     * @doc.type method
     * @doc.purpose Prevent excess memory consumption from large request bodies
     * @doc.layer product
     * @doc.pattern Middleware
     */
    private AsyncServlet payloadSizeLimitFilter(AsyncServlet delegate) {
        return request -> {
            String contentLengthHeader = request.getHeader(HttpHeaders.of("Content-Length"));
            if (contentLengthHeader != null) {
                try {
                    long size = Long.parseLong(contentLengthHeader.trim());
                    if (size > MAX_BODY_BYTES) {
                        String msg = String.format(
                            "{\"error\":\"Request body too large: %d bytes (limit %d bytes)\"}",
                            size, MAX_BODY_BYTES);
                        return Promise.of(HttpResponse.ofCode(413)
                            .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                                HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                            .withBody(msg.getBytes(StandardCharsets.UTF_8))
                            .build());
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed Content-Length — pass through; body loading will catch it naturally
                }
            }
            return delegate.serve(request);
        };
    }

    private HttpResponse jsonResponse(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(CORS_ALLOW_METHODS))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(CORS_ALLOW_HEADERS))
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
                .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(CORS_ALLOW_METHODS))
                .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(CORS_ALLOW_HEADERS))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code)
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    // -------------------------------------------------------------------------
    // Full-text search — GET /api/v1/entities/:collection/search
    // -------------------------------------------------------------------------

    /**
     * Point-in-time full-text search over a collection via OpenSearch.
     *
     * <p>Translates the user-supplied Lucene {@code query_string} expression into
     * an OpenSearch search request via {@link OpenSearchConnector#query} and returns
     * a paginated JSON response.
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code q}        – Lucene / OpenSearch {@code query_string} expression
     *                          (required, e.g. {@code title:foo AND status:active})</li>
     *   <li>{@code limit}    – max results per page (default 20, max 500)</li>
     *   <li>{@code offset}   – zero-based pagination offset (default 0)</li>
     *   <li>{@code tenantId} – tenant identifier; falls back to {@code X-Tenant-Id}
     *                          header then {@code "default"}</li>
     * </ul>
     *
     * <h3>Response (200 OK)</h3>
     * <pre>{@code
     * {
     *   "results":     [ { "id": "...", "collectionName": "...", "data": {...} }, ... ],
     *   "total":       142,
     *   "limit":       20,
     *   "offset":      0,
     *   "hasMore":     true,
     *   "executionMs": 37
     * }
     * }</pre>
     *
     * <p>Returns {@code 501 Not Implemented} when no {@link OpenSearchConnector} is
     * configured. Returns {@code 400 Bad Request} when the {@code q} parameter is
     * absent or blank.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     *
     * @doc.type     method
     * @doc.purpose  REST handler for OpenSearch full-text entity search
     * @doc.layer    product
     * @doc.pattern  Handler
     */
    private Promise<HttpResponse> handleFullTextSearch(HttpRequest request) {
        if (openSearchConnector == null) {
            return Promise.of(errorResponse(501,
                "Full-text search is not enabled; configure an OpenSearchConnector"));
        }

        String collection = request.getPathParameter("collection");
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

        String q = request.getQueryParameter("q");
        Optional<String> qErr = ApiInputValidator.validateSearchQuery(q);
        if (qErr.isPresent()) return Promise.of(errorResponse(400, qErr.get()));

        String tenantId = resolveTenantId(request);
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 20);
        if (!limitResult.isValid()) return Promise.of(errorResponse(400, limitResult.getError().orElseThrow()));
        int limit  = limitResult.getValue();
        int offset = Math.max(parseIntParam(request.getQueryParameter("offset"), 0), 0);

        QuerySpec spec = QuerySpec.builder()
            .filter(q)
            .limit(limit)
            .offset(offset)
            .build();

        log.debug("[search] tenant={} collection={} q='{}' limit={} offset={}", tenantId, collection, q, limit, offset);

        return openSearchConnector.query((java.util.UUID) null, tenantId, spec)
            .map(qr -> {
                List<Map<String, Object>> results = qr.entities().stream()
                    .map(e -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", e.getId() != null ? e.getId().toString() : null);
                        item.put("collectionName", e.getCollectionName());
                        item.put("data", e.getData());
                        item.put("version", e.getVersion());
                        item.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                        item.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
                        return item;
                    })
                    .toList();

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("results",     results);
                body.put("total",       qr.total());
                body.put("limit",       qr.limit());
                body.put("offset",      qr.offset());
                body.put("hasMore",     qr.hasMore());
                body.put("executionMs", qr.executionTimeMs());
                return jsonResponse(body);
            })
            .mapException(e -> {
                log.error("[search] tenant={} collection={} q='{}': {}",
                    tenantId, collection, q, e.getMessage(), e);
                return new HttpException("Search failed: " + e.getMessage(), e);
            });
    }

    // -------------------------------------------------------------------------
    // Streaming query SSE — GET /api/v1/entities/:collection/query/stream
    // -------------------------------------------------------------------------

    /**
     * Server-Sent Events endpoint that streams full-text search results in real time.
     *
     * <p>Two-phase behavior:
     * <ol>
     *   <li><b>Snapshot phase</b> – executes the supplied Lucene {@code query_string}
     *       expression via {@link OpenSearchConnector#query} and pushes all matching
     *       entities as a single {@code query-snapshot} SSE frame.</li>
     *   <li><b>Follow phase</b> (only when {@code follow=true}) – subscribes to the
     *       event log via {@link EventLogStore#tail} and pushes subsequent
     *       {@code entity.saved} / {@code entity.deleted} / batch-mutation events
     *       for the requested collection as {@code query-update} SSE frames.</li>
     * </ol>
     *
     * <h3>Query parameters</h3>
     * <ul>
     *   <li>{@code q}          – Lucene expression (required)</li>
     *   <li>{@code follow}     – {@code true} to tail the event log after snapshot
     *                            (default: {@code false})</li>
     *   <li>{@code fromOffset} – starting log offset for the follow phase
     *                            (default: 0)</li>
     *   <li>{@code limit}      – snapshot result limit (default 100, max 1000)</li>
     *   <li>{@code tenantId}   – tenant; falls back to header / {@code "default"}</li>
     * </ul>
     *
     * <h3>SSE frame types</h3>
     * <ul>
     *   <li>{@code connected}       – sent immediately on open</li>
     *   <li>{@code query-snapshot}  – full snapshot of matching entities</li>
     *   <li>{@code query-update}    – entity mutation event from the event log</li>
     *   <li>{@code heartbeat}       – every {@value #SSE_HEARTBEAT_TIMEOUT_SEC} s when idle</li>
     * </ul>
     *
     * <p>Returns {@code 501 Not Implemented} when no OpenSearchConnector is configured.
     * Returns {@code 400 Bad Request} when {@code q} is absent or blank.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to a streaming {@code text/event-stream} HTTP response
     *
     * @doc.type     method
     * @doc.purpose  SSE handler for live full-text query streaming over a collection
     * @doc.layer    product
     * @doc.pattern  Handler
     */
    private Promise<HttpResponse> handleStreamingQuerySse(HttpRequest request) {
        if (openSearchConnector == null) {
            return Promise.of(errorResponse(501,
                "Streaming query is not enabled; configure an OpenSearchConnector"));
        }

        String collection = request.getPathParameter("collection");
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(errorResponse(400, collErr.get()));

        String q = request.getQueryParameter("q");
        Optional<String> qErr = ApiInputValidator.validateSearchQuery(q);
        if (qErr.isPresent()) return Promise.of(errorResponse(400, qErr.get()));

        String tenantId    = resolveTenantId(request);
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(errorResponse(400, tenantErr.get()));

        boolean follow     = "true".equalsIgnoreCase(request.getQueryParameter("follow"));
        long fromOffsetVal = parseLongParam(request.getQueryParameter("fromOffset"), 0L);
        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(errorResponse(400, limitResult.getError().orElseThrow()));
        int snapshotLimit  = limitResult.getValue();

        LinkedBlockingQueue<Optional<byte[]>> queue = new LinkedBlockingQueue<>(SSE_QUEUE_CAPACITY);

        // Phase 1 – snapshot: execute query and push results as a single frame
        QuerySpec snapshotSpec = QuerySpec.builder()
            .filter(q)
            .limit(snapshotLimit)
            .offset(0)
            .build();

        // "connected" frame sent immediately so the client knows the stream is alive
        queue.offer(Optional.of(buildSseFrame("connected", Map.of(
            "service",     "data-cloud",
            "tenantId",    tenantId,
            "collection",  collection,
            "q",           q,
            "follow",      String.valueOf(follow),
            "fromOffset",  String.valueOf(fromOffsetVal),
            "timestamp",   Instant.now().toString()
        ))));

        // Snapshot query runs on the blocking executor to avoid blocking the event loop
        Promise<StorageConnector.QueryResult> snapshotPromise =
            Promise.ofBlocking(blockingExecutor, () -> {
                // Wrap blocking ActiveJ promise resolution; connector is already async-capable
                // but we need the result inline here before opening the tail subscription
                try {
                    return openSearchConnector.query((java.util.UUID) null, tenantId, snapshotSpec)
                        // toCompletableFuture() bridges ActiveJ Promise to JDK for blocking get
                        .toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    log.warn("[query-stream] snapshot failed tenant={} collection={} q='{}': {}",
                        tenantId, collection, q, ex.getMessage(), ex);
                    return StorageConnector.QueryResult.empty();
                }
            });

        Set<String> entityEventTypes = Set.of(
            "entity.saved", "entity.deleted",
            "entity.batch-saved", "entity.batch-deleted"
        );

        // Phase 2 – optionally tail the event log after snapshot
        if (follow) {
            return client.eventLogStore()
                .tail(TenantContext.of(tenantId), Offset.of(fromOffsetVal), entry -> {
                    if (!entityEventTypes.contains(entry.eventType())) {
                        return;
                    }
                    try {
                        byte[] payloadBytes = new byte[entry.payload().remaining()];
                        entry.payload().duplicate().get(payloadBytes);
                        String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payloadMap = objectMapper.readValue(payloadStr, Map.class);

                        if (!collection.equals(payloadMap.get("collection"))) {
                            return;
                        }

                        Map<String, Object> frame = new LinkedHashMap<>();
                        frame.put("collection",  collection);
                        frame.put("operation",   payloadMap.getOrDefault("operation", "unknown"));
                        frame.put("eventType",   entry.eventType());
                        frame.put("tenantId",    tenantId);
                        frame.put("timestamp",   entry.timestamp().toString());
                        if (payloadMap.containsKey("id"))      frame.put("id",      payloadMap.get("id"));
                        if (payloadMap.containsKey("ids"))     frame.put("ids",     payloadMap.get("ids"));
                        if (payloadMap.containsKey("version")) frame.put("version", payloadMap.get("version"));
                        if (payloadMap.containsKey("count"))   frame.put("count",   payloadMap.get("count"));

                        byte[] sseFrame = buildSseFrame("query-update", frame);
                        if (!queue.offer(Optional.of(sseFrame), 100L, TimeUnit.MILLISECONDS)) {
                            log.warn("[query-stream] queue full for tenant={} collection={}, dropping update",
                                tenantId, collection);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        log.warn("[query-stream] frame error tenant={} collection={}: {}", tenantId, collection, ex.getMessage());
                    }
                })
                .then(subscription -> snapshotPromise.map(qr -> {
                    sseSubscriptions.add(subscription);
                    enqueueSnapshot(queue, qr, collection, tenantId, q);
                    return buildSseChannelResponse(queue, subscription, tenantId, collection);
                }))
                .mapException(e -> {
                    log.error("[query-stream] tail failed tenant={} collection={}: {}", tenantId, collection, e.getMessage(), e);
                    return new HttpException("Streaming query failed: " + e.getMessage(), e);
                });
        } else {
            // Snapshot-only mode: no event-log subscription
            return snapshotPromise.map(qr -> {
                enqueueSnapshot(queue, qr, collection, tenantId, q);
                // Signal end-of-stream
                queue.offer(Optional.empty());
                return buildSseChannelResponse(queue, null, tenantId, collection);
            });
        }
    }

    /**
     * Encodes the OpenSearch snapshot result as a {@code query-snapshot} SSE frame
     * and places it on the given queue.
     *
     * @param queue      target queue
     * @param qr         query result to encode
     * @param collection collection name (for logging)
     * @param tenantId   tenant id (for logging)
     * @param q          original query expression (included in frame metadata)
     *
     * @doc.type    method
     * @doc.purpose Push a query snapshot as an SSE frame onto the subscriber queue
     * @doc.layer   product
     * @doc.pattern Helper
     */
    private void enqueueSnapshot(
            LinkedBlockingQueue<Optional<byte[]>> queue,
            StorageConnector.QueryResult qr,
            String collection,
            String tenantId,
            String q) {
        try {
            List<Map<String, Object>> results = qr.entities().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id",             e.getId() != null ? e.getId().toString() : null);
                    item.put("collectionName", e.getCollectionName());
                    item.put("data",           e.getData());
                    item.put("version",        e.getVersion());
                    item.put("createdAt",      e.getCreatedAt()  != null ? e.getCreatedAt().toString()  : null);
                    item.put("updatedAt",      e.getUpdatedAt()  != null ? e.getUpdatedAt().toString()  : null);
                    return item;
                })
                .toList();

            Map<String, Object> snapshotFrame = new LinkedHashMap<>();
            snapshotFrame.put("q",           q);
            snapshotFrame.put("collection",  collection);
            snapshotFrame.put("tenantId",    tenantId);
            snapshotFrame.put("total",       qr.total());
            snapshotFrame.put("limit",       qr.limit());
            snapshotFrame.put("offset",      qr.offset());
            snapshotFrame.put("hasMore",     qr.hasMore());
            snapshotFrame.put("executionMs", qr.executionTimeMs());
            snapshotFrame.put("results",     results);
            snapshotFrame.put("timestamp",   Instant.now().toString());

            byte[] frame = buildSseFrame("query-snapshot", snapshotFrame);
            if (!queue.offer(Optional.of(frame), 100L, TimeUnit.MILLISECONDS)) {
                log.warn("[query-stream] queue full when enqueuing snapshot tenant={} collection={}", tenantId, collection);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("[query-stream] snapshot serialization error tenant={} collection={}: {}", tenantId, collection, ex.getMessage());
        }
    }

    /**
     * Builds the {@code text/event-stream} HTTP response backed by a
     * {@link LinkedBlockingQueue} of pre-serialized SSE frames.
     *
     * <p>Heartbeats are injected automatically whenever the queue is empty for
     * {@value #SSE_HEARTBEAT_TIMEOUT_SEC} seconds. When a {@code null} item is
     * dequeued (end-of-stream sentinel) or the subscription is cancelled, the
     * stream terminates gracefully.
     *
     * @param queue        SSE frame queue (capacity {@link #SSE_QUEUE_CAPACITY})
     * @param subscription active event-log subscription, or {@code null} for
     *                     snapshot-only mode
     * @param tenantId     tenant identifier (for logging)
     * @param collection   collection name (for logging)
     * @return the fully-configured streaming HTTP response
     *
     * @doc.type    method
     * @doc.purpose Construct a streaming SSE HttpResponse from a blocking queue
     * @doc.layer   product
     * @doc.pattern Helper
     */
    private HttpResponse buildSseChannelResponse(
            LinkedBlockingQueue<Optional<byte[]>> queue,
            EventLogStore.Subscription subscription,
            String tenantId,
            String collection) {

        ChannelSupplier<ByteBuf> bodyStream = ChannelSuppliers.ofAsyncSupplier(() -> {
            boolean cancelled = subscription != null && subscription.isCancelled();
            if (cancelled) {
                return Promise.of(null);
            }
            return Promise.ofBlocking(blockingExecutor, () -> {
                if (subscription != null && subscription.isCancelled()) {
                    return null;
                }
                try {
                    Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        // Heartbeat
                        return ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                            Map.of("ts", Instant.now().toString())));
                    }
                    return item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
        });

        log.info("[query-stream] stream opened tenant={} collection={}", tenantId, collection);

        return HttpResponse.ok200()
            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
            .withHeader(HttpHeaders.of("Cache-Control"),            HttpHeaderValue.of("no-cache"))
            .withHeader(HttpHeaders.of("X-Accel-Buffering"),        HttpHeaderValue.of("no"))
            .withHeader(HttpHeaders.of("Connection"),               HttpHeaderValue.of("keep-alive"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(CORS_ALLOW_ORIGIN))
            .withBodyStream(bodyStream)
            .build();
    }
}
