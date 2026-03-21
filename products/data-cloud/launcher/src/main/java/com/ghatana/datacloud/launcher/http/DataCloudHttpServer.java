package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.brain.DataCloudBrain;
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
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;


import com.ghatana.datacloud.entity.storage.StorageConnector;
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

    /** Tracks active SSE subscriptions so they can be cancelled on server shutdown. */
    private final CopyOnWriteArrayList<EventLogStore.Subscription> sseSubscriptions =
            new CopyOnWriteArrayList<>();

    /**
     * Active WebSocket connections for real-time push notifications.
     * All mutations happen on the eventloop thread; {@link CopyOnWriteArrayList}
     * provides safe iteration even when a write removes a dead connection.
     */
    private final CopyOnWriteArrayList<IWebSocket> wsConnections = new CopyOnWriteArrayList<>();

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

        entityHandler = new EntityCrudHandler(client, httpSupport, this::broadcastWsEvent);
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
            .with(HttpMethod.GET, "/api/v1/entities/:collection/stream", this::handleEntityCdcStream)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/search", this::handleFullTextSearch)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/query/stream", this::handleStreamingQuerySse)
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
            .with(HttpMethod.GET,  "/api/v1/brain/workspace/stream",      this::handleBrainWorkspaceStream)
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
            .with(HttpMethod.GET, "/events/stream",              this::handleSseStream)
            .with(HttpMethod.GET, "/api/v1/agents/events/stream", this::handleAgentsEventStream)
            .with(HttpMethod.GET, "/api/v1/learning/stream",      this::handleLearningStream)

            // WebSocket endpoint for real-time collection change notifications (DC-12)
            .withWebSocket("/ws", this::handleWebSocketConnection)

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
        // Close all active WebSocket connections before stopping
        if (!wsConnections.isEmpty()) {
            log.info("Closing {} active WebSocket connections", wsConnections.size());
            for (IWebSocket ws : new ArrayList<>(wsConnections)) {
                try { ws.close(); } catch (Exception ignored) { /* best-effort */ }
            }
            wsConnections.clear();
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

    // ==================== Health Endpoints (delegated to HealthHandler) ====================

    /** Parses an integer query param, returning {@code defaultValue} on null or parse failure. */
    private int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
                try {
                    if (subscription.isCancelled()) {
                        return Promise.of(null);
                    }
                    Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                Map.of("ts", Instant.now().toString()))));
                    }
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null);
                }
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
                try {
                    if (subscription.isCancelled()) {
                        return Promise.of(null);
                    }
                    Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        // Timeout — send heartbeat to keep the TCP connection alive
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                                Map.of("ts", Instant.now().toString()))));
                    }
                    // Optional.empty() == EOS sentinel
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null); // end stream on interrupt
                }
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
            try {
                if (!subscription.isActive()) return Promise.of(null);
                Optional<byte[]> item =
                    queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (item == null) {
                    return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                        Map.of("ts", Instant.now().toString()))));
                }
                return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Promise.of(null);
            }
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
                try {
                    if (subscription.isCancelled()) return Promise.of(null);
                    Optional<byte[]> item =
                        queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                            Map.of("ts", Instant.now().toString()))));
                    }
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null);
                }
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
            {
                try {
                    Optional<byte[]> item =
                        queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (item == null) {
                        return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                            Map.of("ts", Instant.now().toString()))));
                    }
                    return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Promise.of(null);
                }
            }
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

    // ==================== WebSocket Endpoint (DC-12) ====================

    /**
     * Handles a new WebSocket connection on {@code /ws}.
     *
     * <p>Registers the connection, sends a {@code system.notification} welcome frame,
     * then starts a read-loop so that closed or errored connections are evicted promptly.
     * The endpoint is push-only: the server broadcasts collection-change events to all
     * connected clients; client frames are received but intentionally ignored.
     *
     * <p>Broadcasted event types:
     * <ul>
     *   <li>{@code collection.saved}        — single entity upserted</li>
     *   <li>{@code collection.deleted}      — single entity deleted</li>
     *   <li>{@code collection.batch-saved}  — bulk upsert completed</li>
     *   <li>{@code collection.batch-deleted} — bulk delete completed</li>
     * </ul>
     *
     * @param ws the newly established WebSocket connection
     *
     * @doc.type method
     * @doc.purpose Register WebSocket client and begin push-only event stream
     * @doc.layer product
     * @doc.pattern EventBroadcast
     */
    private void handleWebSocketConnection(IWebSocket ws) {
        wsConnections.add(ws);
        log.debug("WebSocket client connected; total active={}", wsConnections.size());

        // Send connection acknowledgement
        sendWsFrame(ws, "system.notification", Map.of(
            "message",    "Connected to Data-Cloud real-time stream",
            "serverTime", Instant.now().toString()
        ));

        // Start recursive read-loop to detect disconnects without polling
        readWsLoop(ws);
    }

    /**
     * Recursive Promise-based read-loop that keeps the connection registered
     * until the client closes it or a network error occurs.
     *
     * <p>The loop calls {@link IWebSocket#readMessage()} which returns a
     * {@code Promise<@Nullable Message>}. A {@code null} message signals a clean
     * close; an exception signals an error close. In either case the connection
     * is removed from {@link #wsConnections}.
     *
     * @param ws the WebSocket connection to monitor
     *
     * @doc.type method
     * @doc.purpose Drive connection eviction without blocking the eventloop
     * @doc.layer product
     * @doc.pattern EventDriven
     */
    private void readWsLoop(IWebSocket ws) {
        ws.readMessage()
            .whenComplete((msg, e) -> {
                if (e != null || msg == null) {
                    // Connection closed (null) or errored
                    wsConnections.remove(ws);
                    log.debug("WebSocket client disconnected; total active={}", wsConnections.size());
                } else {
                    // Client frames are accepted but ignored (push-only protocol); continue loop
                    readWsLoop(ws);
                }
            });
    }

    /**
     * Broadcasts a typed event payload to all connected WebSocket clients.
     *
     * <p>Dead connections are silently removed on write failure. The snapshot
     * iteration prevents {@link java.util.ConcurrentModificationException} when
     * a removal occurs mid-broadcast. This method is safe to call from the
     * eventloop thread; all writes are non-blocking ActiveJ Promises.
     *
     * @param type the event type string (e.g., {@code "collection.saved"})
     * @param data arbitrary payload serialised as the {@code "data"} field
     *
     * @doc.type method
     * @doc.purpose Push typed event to all connected WebSocket clients
     * @doc.layer product
     * @doc.pattern EventBroadcast
     */
    private void broadcastWsEvent(String type, Map<String, Object> data) {
        if (wsConnections.isEmpty()) return;
        String json = buildWsFrame(type, data);
        IWebSocket.Message msg = IWebSocket.Message.text(json);
        for (IWebSocket ws : new ArrayList<>(wsConnections)) {
            ws.writeMessage(msg).whenException(e -> wsConnections.remove(ws));
        }
    }

    /**
     * Sends a single typed WebSocket frame to one connection.
     * Used for per-connection messages such as the connection acknowledgement.
     *
     * @param ws   the target WebSocket connection
     * @param type the event type string
     * @param data arbitrary payload
     */
    private void sendWsFrame(IWebSocket ws, String type, Map<String, Object> data) {
        String json = buildWsFrame(type, data);
        ws.writeMessage(IWebSocket.Message.text(json))
            .whenException(e -> wsConnections.remove(ws));
    }

    /**
     * Serialises a typed event into the WebSocket frame JSON string.
     *
     * <p>Frame format:
     * <pre>{@code {"type": "<type>", "data": {...}, "timestamp": "<iso8601>"}}</pre>
     *
     * @param type the event type string
     * @param data payload map
     * @return the serialised JSON string; falls back to a minimal error frame on failure
     */
    private String buildWsFrame(String type, Map<String, Object> data) {
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", type);
            frame.put("data", data);
            frame.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("Failed to serialize WebSocket frame for type={}", type, e);
            return "{\"type\":\"error\",\"data\":{\"message\":\"serialization error\"}}";
        }
    }

    // ==================== Helper Methods ====================

    /** Resolves tenant from {@code X-Tenant-Id} header or query parameter. Rejects if missing. */
    private String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) return fromQuery;
        throw new IllegalArgumentException("Missing required X-Tenant-Id header or tenantId query parameter");
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
            try {
                if (subscription != null && subscription.isCancelled()) {
                    return Promise.of(null);
                }
                Optional<byte[]> item = queue.poll(SSE_HEARTBEAT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (item == null) {
                    // Heartbeat
                    return Promise.of(ByteBuf.wrapForReading(buildSseFrame("heartbeat",
                        Map.of("ts", Instant.now().toString()))));
                }
                return Promise.of(item.isPresent() ? ByteBuf.wrapForReading(item.get()) : null);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Promise.of(null);
            }
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
