package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;


import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandler;
import com.ghatana.datacloud.launcher.http.handlers.EventHandler;
import com.ghatana.datacloud.launcher.http.handlers.AgentRegistryHandler;
import com.ghatana.datacloud.launcher.http.handlers.MemoryPlaneHandler;
import com.ghatana.datacloud.launcher.http.handlers.BrainHandler;
import com.ghatana.datacloud.launcher.http.handlers.LearningHandler;
import com.ghatana.datacloud.launcher.http.handlers.AnalyticsHandler;
import com.ghatana.datacloud.launcher.http.handlers.AiModelHandler;
import com.ghatana.datacloud.launcher.http.handlers.HealthHandler;
import com.ghatana.datacloud.launcher.http.handlers.SseStreamingHandler;

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
    /**
     * Allowed CORS origin(s). Defaults to the value of {@code DATACLOUD_CORS_ALLOWED_ORIGINS}
     * environment variable. Falls back to {@code "http://localhost:5173"} (Vite dev server)
     * so that local development works out-of-the-box without opening access to all origins.
     * <p>
     * In production, set to the actual frontend origin (e.g. {@code "https://app.ghatana.com"}).
     */
    private static final String CORS_ALLOW_ORIGIN;
    static {
        String env = System.getenv("DATACLOUD_CORS_ALLOWED_ORIGINS");
        CORS_ALLOW_ORIGIN = (env != null && !env.isBlank()) ? env : "http://localhost:5173";
    }
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
     * Optional report-generation service for DC-10 reporting routes.
     * When non-null, {@code /api/v1/reports/**} routes are active.
     * When {@code null} those routes return HTTP 503.
     */
    private ReportService reportService;

    /**
     * Optional AI model manager for DC-11 model-registry routes.
     * When non-null, {@code /api/v1/models/**} routes are active.
     * When {@code null} those routes return HTTP 503.
     */
    private AIModelManager aiModelManager;

    /**
     * Optional feature store service for DC-11 feature-store routes.
     * When non-null, {@code /api/v1/features/**} routes are active.
     * When {@code null} those routes return HTTP 503.
     */
    private FeatureStoreService featureStoreService;

    /**
     * Observability metrics collector. Defaults to Noop if not explicitly set
     * via {@link #withMetricsCollector(MetricsCollector)}.
     */
    private MetricsCollector metricsCollector = MetricsCollectorFactory.createNoop();

    // ==================== Extracted Handler Delegates ====================
    private HttpHandlerSupport httpSupport;
    private EntityCrudHandler entityHandler;
    private EventHandler eventHandler;
    private AgentRegistryHandler agentHandler;
    private MemoryPlaneHandler memoryHandler;
    private BrainHandler brainHandler;
    private LearningHandler learningHandler;
    private AnalyticsHandler analyticsHandler;
    private AiModelHandler aiModelHandler;
    private HealthHandler healthHandler;
    private SseStreamingHandler sseHandler;  // DCHTTP-1: extracted SSE + WebSocket handler

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
     * Attaches a {@link ReportService} to enable on-demand report generation (DC-10).
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code POST /api/v1/reports}            — generate a report</li>
     *   <li>{@code GET  /api/v1/reports/:reportId}  — retrieve a cached report</li>
     *   <li>{@code GET  /api/v1/reports}             — list cached reports</li>
     * </ul>
     *
     * @param service the report service; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withReportService(ReportService service) {
        this.reportService = service;
        return this;
    }

    /**
     * Attaches an {@link AIModelManager} to enable ML model-registry routes (DC-11).
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code GET  /api/v1/models}                       — list models</li>
     *   <li>{@code POST /api/v1/models}                       — register a model</li>
     *   <li>{@code GET  /api/v1/models/:modelName}            — get active model</li>
     *   <li>{@code POST /api/v1/models/:modelName/promote}    — promote to production</li>
     * </ul>
     *
     * @param manager the AI model manager; {@code null} disables model routes
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach AI model manager to enable DC-11 model-registry routes
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withAiModelManager(AIModelManager manager) {
        this.aiModelManager = manager;
        return this;
    }

    /**
     * Attaches a {@link FeatureStoreService} to enable ML feature-store routes (DC-11).
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code POST /api/v1/features}             — ingest a feature vector</li>
     *   <li>{@code GET  /api/v1/features/:entityId}   — retrieve features for an entity</li>
     * </ul>
     *
     * @param service the feature store service; {@code null} disables feature routes
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach feature store service to enable DC-11 feature routes
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withFeatureStoreService(FeatureStoreService service) {
        this.featureStoreService = service;
        return this;
    }

    /**
     * Attaches a {@link MetricsCollector} for production observability.
     *
     * <p>When not called, a no-op collector is used. In production, callers
     * should provide a real Micrometer-backed collector via
     * {@link MetricsCollectorFactory#create(io.micrometer.core.instrument.MeterRegistry)}.
     *
     * @param collector the metrics collector; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withMetricsCollector(MetricsCollector collector) {
        this.metricsCollector = collector;
        return this;
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        eventloop = Eventloop.create();

        // ---- Instantiate extracted handler delegates ----
        httpSupport = new HttpHandlerSupport(objectMapper, CORS_ALLOW_ORIGIN, CORS_ALLOW_METHODS, CORS_ALLOW_HEADERS);

        sseHandler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, httpSupport);
        if (openSearchConnector != null) sseHandler.withOpenSearchConnector(openSearchConnector);

        entityHandler = new EntityCrudHandler(client, httpSupport, sseHandler.broadcastFunction());
        if (schemaValidator != null) entityHandler.withSchemaValidator(schemaValidator);
        if (exportService != null) entityHandler.withExportService(exportService);
        if (anomalyDetector != null) entityHandler.withAnomalyDetector(anomalyDetector);

        eventHandler = new EventHandler(client, httpSupport);
        agentHandler = new AgentRegistryHandler(client, httpSupport);
        memoryHandler = new MemoryPlaneHandler(client, httpSupport);
        brainHandler = new BrainHandler(brain, httpSupport);
        learningHandler = new LearningHandler(learningBridge, httpSupport);

        analyticsHandler = new AnalyticsHandler(analyticsEngine, httpSupport);
        if (reportService != null) analyticsHandler.withReportService(reportService);

        aiModelHandler = new AiModelHandler(aiModelManager, featureStoreService, httpSupport);

        healthHandler = new HealthHandler(httpSupport);

        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints — delegated to HealthHandler (P7-2b)
            .with(HttpMethod.GET, "/health", healthHandler::handleHealth)
            .with(HttpMethod.GET, "/ready", healthHandler::handleReady)
            .with(HttpMethod.GET, "/live", healthHandler::handleLive)
            
            // Info endpoints — delegated to HealthHandler (P7-2b)
            .with(HttpMethod.GET, "/info", healthHandler::handleInfo)
            .with(HttpMethod.GET, "/metrics", healthHandler::handleMetrics)
            
            // Entity endpoints — delegated to EntityCrudHandler
            .with(HttpMethod.POST, "/api/v1/entities/:collection", entityHandler::handleSaveEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/stream", sseHandler::handleEntityCdcStream)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/search", entityHandler::handleFullTextSearch)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/query/stream", sseHandler::handleStreamingQuerySse)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id", entityHandler::handleGetEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection", entityHandler::handleQueryEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/:id", entityHandler::handleDeleteEntity)
            // Bulk entity endpoints — upsert/delete multiple entities in a single request
            .with(HttpMethod.POST, "/api/v1/entities/:collection/batch", entityHandler::handleBatchSaveEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/batch", entityHandler::handleBatchDeleteEntities)
            // Bulk export and anomaly detection endpoints
            .with(HttpMethod.GET, "/api/v1/entities/:collection/export", entityHandler::handleExportEntities)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/anomalies", entityHandler::handleDetectAnomalies)
            
            // Event endpoints — delegated to EventHandler
            .with(HttpMethod.POST, "/api/v1/events", eventHandler::handleAppendEvent)
            .with(HttpMethod.GET, "/api/v1/events", eventHandler::handleQueryEvents)

            // Agent registry endpoints (DC-3) — delegated to AgentRegistryHandler
            .with(HttpMethod.GET, "/api/v1/agents", agentHandler::handleListAgents)
            .with(HttpMethod.POST, "/api/v1/agents", agentHandler::handleRegisterAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", agentHandler::handleGetAgent)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", agentHandler::handleDeleteAgent)

            // Pipeline registry endpoints — delegated to AgentRegistryHandler
            .with(HttpMethod.GET,    "/api/v1/pipelines",                agentHandler::handleListPipelines)
            .with(HttpMethod.POST,   "/api/v1/pipelines",                agentHandler::handleSavePipeline)
            .with(HttpMethod.GET,    "/api/v1/pipelines/:pipelineId",    agentHandler::handleGetPipeline)
            .with(HttpMethod.PUT,    "/api/v1/pipelines/:pipelineId",    agentHandler::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId",    agentHandler::handleDeletePipeline)

            // Checkpoint management endpoints (DC-3) — delegated to AgentRegistryHandler
            .with(HttpMethod.GET, "/api/v1/checkpoints", agentHandler::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/checkpoints", agentHandler::handleSaveCheckpoint)
            .with(HttpMethod.GET, "/api/v1/checkpoints/:checkpointId", agentHandler::handleGetCheckpoint)
            .with(HttpMethod.DELETE, "/api/v1/checkpoints/:checkpointId", agentHandler::handleDeleteCheckpoint)

            // Agent memory plane endpoints (DC-4) — delegated to MemoryPlaneHandler
            .with(HttpMethod.GET,    "/api/v1/memory/:agentId",                  memoryHandler::handleGetAgentMemory)
            .with(HttpMethod.GET,    "/api/v1/memory/:agentId/:tier",            memoryHandler::handleGetAgentMemoryByTier)
            .with(HttpMethod.POST,   "/api/v1/memory/:agentId/search",           memoryHandler::handleSearchAgentMemory)
            .with(HttpMethod.DELETE, "/api/v1/memory/:agentId/:memoryId",        memoryHandler::handleDeleteMemory)
            .with(HttpMethod.PUT,    "/api/v1/memory/:agentId/:memoryId/retain", memoryHandler::handleRetainMemory)

            // Brain routes (DC-6) — non-streaming delegated to BrainHandler
            .with(HttpMethod.GET,  "/api/v1/brain/health",                brainHandler::handleBrainHealth)
            .with(HttpMethod.GET,  "/api/v1/brain/config",                brainHandler::handleBrainConfig)
            .with(HttpMethod.GET,  "/api/v1/brain/stats",                 brainHandler::handleBrainStats)
            .with(HttpMethod.GET,  "/api/v1/brain/workspace",             brainHandler::handleBrainWorkspace)
            .with(HttpMethod.GET,  "/api/v1/brain/workspace/stream",      sseHandler::handleBrainWorkspaceStream)
            .with(HttpMethod.POST, "/api/v1/brain/attention/elevate",     brainHandler::handleBrainAttentionElevate)
            .with(HttpMethod.GET,  "/api/v1/brain/attention/thresholds",  brainHandler::handleBrainAttentionThresholds)
            .with(HttpMethod.PUT,  "/api/v1/brain/attention/thresholds",  brainHandler::handleBrainAttentionThresholdsUpdate)
            .with(HttpMethod.GET,  "/api/v1/brain/patterns",              brainHandler::handleBrainPatterns)
            .with(HttpMethod.POST, "/api/v1/brain/patterns/match",        brainHandler::handleBrainPatternsMatch)
            .with(HttpMethod.GET,  "/api/v1/brain/salience/:itemId",      brainHandler::handleBrainSalience)

            // Learning routes (DC-8) — non-streaming delegated to LearningHandler
            .with(HttpMethod.POST, "/api/v1/learning/trigger",                    learningHandler::handleLearningTrigger)
            .with(HttpMethod.GET,  "/api/v1/learning/status",                     learningHandler::handleLearningStatus)
            .with(HttpMethod.GET,  "/api/v1/learning/review",                     learningHandler::handleLearningReviewQueue)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/approve",   learningHandler::handleLearningReviewApprove)
            .with(HttpMethod.POST, "/api/v1/learning/review/:reviewId/reject",    learningHandler::handleLearningReviewReject)

            // Analytics routes (DC-9) — delegated to AnalyticsHandler
            .with(HttpMethod.POST, "/api/v1/analytics/query",                     analyticsHandler::handleAnalyticsQuery)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId",            analyticsHandler::handleAnalyticsGetResult)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId/plan",       analyticsHandler::handleAnalyticsGetPlan)
            .with(HttpMethod.POST, "/api/v1/analytics/aggregate",                 analyticsHandler::handleAnalyticsAggregate)

            // Reporting routes (DC-10) — delegated to AnalyticsHandler
            .with(HttpMethod.POST, "/api/v1/reports",             analyticsHandler::handleCreateReport)
            .with(HttpMethod.GET,  "/api/v1/reports",             analyticsHandler::handleListReports)
            .with(HttpMethod.GET,  "/api/v1/reports/:reportId",   analyticsHandler::handleGetReport)

            // AI/ML — Model Registry routes (DC-11) — delegated to AiModelHandler
            .with(HttpMethod.GET,  "/api/v1/models",                          aiModelHandler::handleListAiModels)
            .with(HttpMethod.POST, "/api/v1/models",                          aiModelHandler::handleRegisterAiModel)
            .with(HttpMethod.GET,  "/api/v1/models/:modelName",               aiModelHandler::handleGetAiModel)
            .with(HttpMethod.POST, "/api/v1/models/:modelName/promote",       aiModelHandler::handlePromoteAiModel)

            // AI/ML — Feature Store routes (DC-11) — delegated to AiModelHandler
            .with(HttpMethod.POST, "/api/v1/features",                        aiModelHandler::handleIngestFeature)
            .with(HttpMethod.GET,  "/api/v1/features/:entityId",              aiModelHandler::handleGetFeatures)

            // Server-Sent Events for real-time UI updates (DC-3, DC-9)
            .with(HttpMethod.GET, "/events/stream",              sseHandler::handleSseStream)
            .with(HttpMethod.GET, "/api/v1/agents/events/stream", sseHandler::handleAgentsEventStream)
            .with(HttpMethod.GET, "/api/v1/learning/stream",      sseHandler::handleLearningStream)

            // WebSocket endpoint for real-time collection change notifications (DC-12)
            .withWebSocket("/ws", sseHandler::handleWebSocketConnection)

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
        // Delegate SSE + WebSocket cleanup to the extracted streaming handler
        if (sseHandler != null) {
            sseHandler.shutdown();
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


    // ==================== HTTP Middleware Filters ====================

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

}