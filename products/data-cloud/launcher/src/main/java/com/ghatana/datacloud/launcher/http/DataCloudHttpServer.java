package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.launcher.anomaly.AnomalyDetectionTask;
import com.ghatana.datacloud.launcher.compaction.StorageCompactionTask;
import com.ghatana.datacloud.storage.H2SovereignEntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.function.Supplier;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;


import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityExportHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityAnomalyHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityValidationHandler;
import com.ghatana.datacloud.launcher.http.handlers.EventHandler;
import com.ghatana.datacloud.launcher.http.handlers.PipelineCheckpointHandler;
import com.ghatana.datacloud.launcher.http.handlers.MemoryPlaneHandler;
import com.ghatana.datacloud.launcher.http.handlers.BrainHandler;
import com.ghatana.datacloud.launcher.http.handlers.LearningHandler;
import com.ghatana.datacloud.launcher.http.handlers.AnalyticsHandler;
import com.ghatana.datacloud.launcher.http.handlers.AiModelHandler;
import com.ghatana.datacloud.launcher.http.handlers.HealthHandler;
import com.ghatana.datacloud.launcher.http.handlers.WorkflowExecutionHandler;
import com.ghatana.datacloud.launcher.http.handlers.SseStreamingHandler;
import com.ghatana.datacloud.launcher.http.handlers.AiAssistHandler;
import com.ghatana.datacloud.launcher.http.handlers.VoiceGatewayHandler;
import com.ghatana.datacloud.launcher.http.handlers.DataLifecycleHandler;
import com.ghatana.datacloud.launcher.http.handlers.AutonomyHandler;
import com.ghatana.datacloud.launcher.http.handlers.AgentCatalogHandler;
import com.ghatana.datacloud.launcher.http.handlers.PluginInstallHandler;
import com.ghatana.datacloud.launcher.http.handlers.StorageCostHandler;
import com.ghatana.datacloud.launcher.http.handlers.FederatedQueryHandler;
import com.ghatana.datacloud.launcher.http.handlers.TierMigrationHandler;
import com.ghatana.datacloud.launcher.http.handlers.CapabilityRegistryHandler;
import com.ghatana.datacloud.launcher.http.handlers.ContextLayerHandler;
import com.ghatana.datacloud.launcher.http.handlers.DataProductHandler;
import com.ghatana.datacloud.launcher.http.handlers.LineageHandler;
import com.ghatana.datacloud.launcher.http.handlers.SemanticSearchHandler;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.ReportExecutionCapability;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import com.ghatana.datacloud.plugins.iceberg.TierMigrationScheduler;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.launcher.http.voice.HttpWhisperSttAdapter;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceSttAdapter;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceTtsAdapter;
import com.ghatana.datacloud.launcher.http.voice.VoiceSttPort;
import com.ghatana.datacloud.launcher.http.voice.WhisperSttConfig;

import static java.util.Objects.requireNonNull;

/**
 * HTTP Server for Data-Cloud Standalone deployment.
 * Provides REST API endpoints for entity and event operations.
 *
 * @since 1.0.0
 */
public class DataCloudHttpServer {

    private static final Logger log = LoggerFactory.getLogger(DataCloudHttpServer.class);

    /** SSE queue capacity — 512 frames before back-pressure kicks in. */

    // ==================== CORS Constants ====================
    /** Local-only CORS origin fallback used when the launcher is explicitly running in local mode. */
    private static final String DEFAULT_LOCAL_CORS_ALLOW_ORIGIN = "http://localhost:5173";
    private static final String CORS_ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS, PATCH";
    private static final String CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Tenant-ID, X-Request-ID";
    private static final String CORS_MAX_AGE       = "86400";

    // ==================== Request Validation Constants ====================
    /** Maximum JSON body size accepted: 10 MB. Larger payloads return HTTP 413. */
    private static final long MAX_BODY_BYTES = 10 * 1024 * 1024L;
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final Set<Pattern> BODYLESS_MUTATION_ROUTES = Set.of(
        Pattern.compile("^/api/v1/plugins/[^/]+/(enable|disable)$"),
        Pattern.compile("^/api/v1/plugins/[^/]+/upgrade$"),
        Pattern.compile("^/api/v1/collections/[^/]+/migrate$"),
        Pattern.compile("^/api/v1/pipelines/[^/]+/executions/[^/]+/cancel$"),
        Pattern.compile("^/api/v1/executions/[^/]+/cancel$"),
        Pattern.compile("^/api/v1/learning/review/[^/]+/(approve|reject)$"),
        Pattern.compile("^/api/v1/learning/review/completed$"),
        Pattern.compile("^/api/v1/models/[^/]+/promote$")
    );

    // ==================== Rate Limiting Constants ====================
    /** Default maximum requests per IP per window (can be overridden via {@code DATACLOUD_RATE_LIMIT_REQUESTS}). */
    private static final int DEFAULT_RATE_LIMIT_REQUESTS = 200;
    /** Default sliding-window size in seconds (can be overridden via {@code DATACLOUD_RATE_LIMIT_WINDOW_SECONDS}). */
    private static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60L;

    /** Tunable rate limit: requests per IP per window. Defaults to {@link #DEFAULT_RATE_LIMIT_REQUESTS}. */
    private int rateLimitRequests = DEFAULT_RATE_LIMIT_REQUESTS;
    /** Tunable rate limit: sliding-window size in seconds. Defaults to {@link #DEFAULT_RATE_LIMIT_WINDOW_SECONDS}. */
    private long rateLimitWindowSeconds = DEFAULT_RATE_LIMIT_WINDOW_SECONDS;

    /** Heartbeat interval: block this long waiting for the next event before sending a heartbeat. */

    private final DataCloudClient client;
    private final int port;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    private Eventloop eventloop;
    private AutonomyController autonomyController;

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
     * Sliding-window rate limiter (platform:java:governance).
    * Wraps the delegate servlet and enforces {@link #rateLimitRequests} requests
    * per {@link #rateLimitWindowSeconds}-second sliding window per tenant/client bucket.
     * The raw 429 from the platform filter is upgraded in {@link #rateLimitFilter}
     * to include a JSON body and CORS headers required by the Data-Cloud API contract.
     * Initialised in {@link #start()} after tunable config has been applied.
     */
    private RateLimitFilter platformRateLimiter;

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
    private long storageCompactionIntervalSeconds = 300L;
    private int storageCompactionTombstoneThreshold = 25;
    private AnomalyDetectionTask anomalyDetectionTask;
    private StorageCompactionTask storageCompactionTask;

    // ==================== AI, Voice & Security (E3, E4, E1) ====================

    /**
     * Optional LLM completion service for AI assist routes (DC-E3).
     * When {@code null}, AI routes use static heuristic fallbacks.
     */
    private CompletionService completionService;

    /**
     * Optional audit service for voice gateway and security filter (DC-E1, DC-E4).
     * When {@code null}, audit emission is silently skipped.
     */
    private AuditService auditService;

    /**
     * Optional API-key resolver for the security filter (DC-E1).
     * When {@code null}, the security filter is not activated.
     */
    private ApiKeyResolver apiKeyResolver;

    /**
     * Optional JWT provider for bearer authentication (DC-E1).
     * When {@code null}, JWT bearer authentication is disabled.
     */
    private JwtTokenProvider jwtProvider;

    /**
     * JWT claim name used to resolve tenant identity; defaults to {@code tenant_id}.
     */
    private String jwtTenantClaim = DataCloudSecurityFilter.DEFAULT_TENANT_CLAIM;

    /**
     * Optional policy engine for CRITICAL-route enforcement (DC-E1).
     * When {@code null}, policy checks are skipped (advisory mode).
     */
    private PolicyEngine policyEngine;

    /**
     * Optional trace export service for flushing spans to ClickHouse (B4).
     * When {@code null}, spans are generated but not persisted.
     */
    private TraceExportService traceExportService;

    /**
     * Trace sampling ratio applied when requests do not provide an upstream traceparent decision.
     */
    private double traceSamplingRate = 1.0;

    /**
     * Optional Trino coordinator JDBC URL for federated cross-tier queries (B13).
     * When {@code null}, federated queries fall back to the local analytics engine.
     */
    private String trinoUrl;

    // ==================== Extracted Handler Delegates ====================
    private HttpHandlerSupport httpSupport;
    private String corsAllowOrigin = DEFAULT_LOCAL_CORS_ALLOW_ORIGIN;
    private boolean strictTenantResolution = false;
    private EntityCrudHandler entityHandler;
    private EntityExportHandler exportHandler;
    private EntityAnomalyHandler anomalyHandler;
    /** Optional event log store for durable anomaly persistence. */
    private EventLogStore eventLogStore;
    private EntityValidationHandler validationHandler;
    private EventHandler eventHandler;
    private PipelineCheckpointHandler pipelineCheckpointHandler;
    private WorkflowExecutionHandler workflowExecutionHandler;
    private MemoryPlaneHandler memoryHandler;
    private BrainHandler brainHandler;
    private LearningHandler learningHandler;
    private AnalyticsHandler analyticsHandler;
    private AiModelHandler aiModelHandler;
    private HealthHandler healthHandler;
    private SseStreamingHandler sseHandler;  // DCHTTP-1: extracted SSE + WebSocket handler
    private AiAssistHandler aiAssistHandler; // DC-E3: pervasive AI assist endpoints
    private VoiceGatewayHandler voiceHandler; // DC-E4: voice intent gateway
    private DataLifecycleHandler dataLifecycleHandler; // DC-E5: data lifecycle and governance
    private AutonomyHandler autonomyHandler; // B9: emergency autonomy shutoff
    private AgentCatalogHandler agentCatalogHandler; // B3: agent catalog runtime API
    private PluginInstallHandler pluginInstallHandler; // B6: plugin install/upgrade lifecycle API
    private DataCloudRuntimePluginManager runtimePluginManager;
    private CapabilityRegistryHandler capabilityRegistryHandler; // P2.7: runtime capability registry API
    private ContextLayerHandler contextLayerHandler; // P3.1: tenant-scoped context layer API
    private DataProductHandler dataProductHandler; // P4.4.1: data products publish/discover/subscribe
    private LineagePlugin lineagePlugin;              // P3.9.1: entity lineage tracking
    private LineageHandler lineageHandler;           // P3.9.1: entity lineage HTTP handler
    private SemanticSearchHandler semanticSearchHandler; // P4.5.1: semantic similarity and RAG
    private TierMigrationScheduler warmMigrationScheduler; // B10: L1→L2 warm tier scheduler
    private ArchiveMigrationScheduler coldMigrationScheduler; // B10: L2→L3 cold tier scheduler
    private TierMigrationHandler tierMigrationHandler; // B10: manual tier migration API (wired in start())
    private final Map<String, Supplier<Map<String, Object>>> healthSubsystemSuppliers = new LinkedHashMap<>();

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
     * Attaches an {@link EventLogStore} to enable durable anomaly event persistence
     * and continuous background anomaly detection (P3.6.1).
     *
     * @param store the event log store; must not be {@code null}
     * @return this server for chaining
     */
    public DataCloudHttpServer withEventLogStore(EventLogStore store) {
        this.eventLogStore = store;
        return this;
    }

    /**
     * Attaches a {@link LineagePlugin} to enable entity lineage tracking and visualization (P3.9.1).
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code GET /api/v1/lineage/:collection}        — full upstream/downstream DAG</li>
     *   <li>{@code GET /api/v1/lineage/:collection/impact} — impact analysis</li>
     * </ul>
     *
     * @param plugin the lineage plugin; must not be {@code null}
     * @return this server for chaining
     */
    public DataCloudHttpServer withLineagePlugin(LineagePlugin plugin) {
        this.lineagePlugin = plugin;
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

    public DataCloudHttpServer withStorageCompactionConfig(long intervalSeconds, int tombstoneThreshold) {
        this.storageCompactionIntervalSeconds = intervalSeconds;
        this.storageCompactionTombstoneThreshold = tombstoneThreshold;
        return this;
    }

    /**
     * Attaches a {@link CompletionService} to enable AI assist routes (DC-E3).
     *
     * <p>When not called, AI assist endpoints operate in heuristic-fallback mode.
     *
     * @param service the LLM completion service; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach LLM completion service for pervasive AI assist (DC-E3)
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withCompletionService(CompletionService service) {
        this.completionService = service;
        return this;
    }

    /**
     * Attaches a {@link TraceExportService} so spans produced during request handling
     * are flushed to ClickHouse (B4).
     *
     * <p>When not set, spans are generated but silently discarded.
     *
     * @param service the trace export service; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach trace export service for B4 observability
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withTraceExportService(TraceExportService service) {
        this.traceExportService = service;
        return this;
    }

    /**
     * Configures request trace sampling ratio for launcher-generated spans.
     *
     * @param samplingRate value in the inclusive range {@code [0.0, 1.0]}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withTraceSamplingRate(double samplingRate) {
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException("samplingRate must be between 0.0 and 1.0");
        }
        this.traceSamplingRate = samplingRate;
        return this;
    }

    /**
     * Configures the Trino coordinator JDBC URL for federated cross-tier queries (B13).
     *
     * <p>When set, {@code POST /api/v1/queries/federated} routes queries through the
     * Trino {@code EventCloudConnector}. When absent, the endpoint falls back to the
     * local {@link AnalyticsQueryEngine}.
     *
     * @param trinoUrl Trino coordinator JDBC URL, e.g. {@code jdbc:trino://host:8080/eventcloud}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Configure Trino URL for federated queries
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withTrinoUrl(String trinoUrl) {
        this.trinoUrl = trinoUrl;
        return this;
    }

    /**
     * Wires the tier migration schedulers so the B10 manual migration endpoint is available.
     *
     * <p>Both parameters are optional: pass {@code null} for any tier that is not configured.
     * The corresponding migration target tier will return {@code 503} to the caller.
     *
     * @param warm scheduler for L1→L2 Iceberg migration; may be {@code null}
     * @param cold scheduler for L2→L3 S3 archive migration; may be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Wire tier migration schedulers for on-demand migration (B10)
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withTierMigrationSchedulers(TierMigrationScheduler warm,
                                                           ArchiveMigrationScheduler cold) {
        this.warmMigrationScheduler = warm;
        this.coldMigrationScheduler = cold;
        return this;
    }

    /**
     * Attaches an {@link ApiKeyResolver} and activates the security filter (DC-E1).
     *
     * <p>When called, all non-public routes will require a valid API key.  Pair with
     * {@link #withPolicyEngine} to also enforce CRITICAL-route governance policies.
     *
     * @param resolver the API-key resolver; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Enable API-key authentication for all protected routes
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withApiKeyResolver(ApiKeyResolver resolver) {
        this.apiKeyResolver = resolver;
        return this;
    }

    /**
     * Attaches a JWT provider and activates bearer-token authentication for protected routes.
     *
     * <p>JWT authentication is evaluated after API key authentication when both are configured.
     * Tenant identity is extracted from the configured claim name (default {@code tenant_id}).
     *
     * @param provider JWT token provider; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withJwtProvider(JwtTokenProvider provider) {
        this.jwtProvider = provider;
        return this;
    }

    /**
     * Configures the JWT claim name used to resolve tenant identity.
     *
     * @param tenantClaim claim name; blank values reset to {@code tenant_id}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withJwtTenantClaim(String tenantClaim) {
        this.jwtTenantClaim = tenantClaim != null && !tenantClaim.isBlank()
                ? tenantClaim
                : DataCloudSecurityFilter.DEFAULT_TENANT_CLAIM;
        return this;
    }

    /**
     * Attaches a {@link PolicyEngine} for CRITICAL-route governance enforcement (DC-E1).
     *
     * <p>Requires {@link #withApiKeyResolver} to also be called — the security filter
     * only activates when an {@link ApiKeyResolver} is present.
     *
     * @param engine the policy engine; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Enforce governance policies on CRITICAL routes (DC-E1)
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withPolicyEngine(PolicyEngine engine) {
        this.policyEngine = engine;
        return this;
    }

    /**
     * Attaches an {@link AuditService} for security and governance audit persistence.
     *
     * @param service audit service; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach audit persistence for launcher HTTP flows
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withAuditService(AuditService service) {
        this.auditService = service;
        return this;
    }

    /**
     * Attaches an {@link AutonomyController} to enable autonomy management routes (B9).
     *
     * <p>Required for:
     * <ul>
     *   <li>{@code PUT  /api/v1/autonomy/level}           — emergency global shutoff</li>
     *   <li>{@code GET  /api/v1/autonomy/level}           — get current override level</li>
     *   <li>{@code GET  /api/v1/autonomy/domains}         — list domain states</li>
     *   <li>{@code GET  /api/v1/autonomy/domains/:domain} — get domain state</li>
     *   <li>{@code GET  /api/v1/autonomy/logs}            — autonomy audit log</li>
     * </ul>
     *
     * @param controller the autonomy controller; must not be {@code null}
     * @return {@code this} for chaining
     *
     * @doc.type method
     * @doc.purpose Attach autonomy controller for B9 emergency shutoff routes
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withAutonomyController(AutonomyController controller) {
        this.autonomyController = controller;
        return this;
    }

    /**
     * Registers a dynamic subsystem health supplier used by {@code GET /health/detail}.
     *
     * <p>The supplied snapshot should include at least a {@code status} field with
     * one of {@code UP}, {@code DEGRADED}, {@code DOWN}, or {@code UNKNOWN}.
     */
    public DataCloudHttpServer withHealthSubsystem(String name, Supplier<Map<String, Object>> supplier) {
        this.healthSubsystemSuppliers.put(name, supplier);
        return this;
    }

    /**
     * Enables strict tenant resolution: requests missing X-Tenant-Id are rejected in non-local profiles.
     * Should be called from bootstrap if DATACLOUD_PROFILE != local.
     */
    public DataCloudHttpServer withStrictTenantResolution(boolean enabled) {
        this.strictTenantResolution = enabled;
        return this;
    }

    /**
     * Overrides the default rate-limit thresholds used by the platform rate limiter.
     *
     * <p>Call this method before {@link #start()} to apply env-configurable limits.
     * If not called, defaults are {@code 200} requests per {@code 60}-second window.
     *
     * @param requests   maximum requests per IP allowed within the window
     * @param windowSec  sliding-window size in seconds
     * @return {@code this} for chaining
     */
    public DataCloudHttpServer withRateLimitConfig(int requests, long windowSec) {
        this.rateLimitRequests = requests;
        this.rateLimitWindowSeconds = windowSec;
        return this;
    }

    static void validateSecurityConfiguration(boolean authConfigured,
                                              boolean strictTenantResolution,
                                              Logger logger) {
        requireNonNull(logger, "logger");

        if (authConfigured) {
            return;
        }

        if (strictTenantResolution) {
            throw new IllegalStateException(
                "Security filter must be configured for non-local profiles. " +
                    "Call withApiKeyResolver() or withJwtProvider()."
            );
        }

        logger.warn("Running without authentication — LOCAL profile only.");
    }

    static String resolveCorsAllowOrigin(String configuredOrigins,
                                         boolean strictTenantResolution,
                                         Logger logger) {
        requireNonNull(logger, "logger");

        if (configuredOrigins != null && !configuredOrigins.isBlank()) {
            return configuredOrigins;
        }

        if (strictTenantResolution) {
            throw new IllegalStateException(
                "DATACLOUD_CORS_ALLOWED_ORIGINS must be configured for non-local profiles.");
        }

        logger.warn("Running with default localhost CORS origin — LOCAL profile only.");
        return DEFAULT_LOCAL_CORS_ALLOW_ORIGIN;
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        validateSecurityConfiguration(apiKeyResolver != null || jwtProvider != null, strictTenantResolution, log);
        corsAllowOrigin = resolveCorsAllowOrigin(System.getenv("DATACLOUD_CORS_ALLOWED_ORIGINS"), strictTenantResolution, log);

        platformRateLimiter = new RateLimitFilter(
                rateLimitRequests,
                rateLimitWindowSeconds,
                DataCloudHttpServer::rateLimitClientKey);

        eventloop = Eventloop.create();

        // ---- Instantiate extracted handler delegates ----
        httpSupport = new HttpHandlerSupport(objectMapper, corsAllowOrigin, CORS_ALLOW_METHODS, CORS_ALLOW_HEADERS, strictTenantResolution);
        DataCloudBusinessMetrics businessMetrics = new DataCloudBusinessMetrics(metricsCollector);
        TraceSpanSupport traceSpanSupport = new TraceSpanSupport(traceExportService);

        sseHandler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, httpSupport);
        if (openSearchConnector != null) sseHandler.withOpenSearchConnector(openSearchConnector);

        VectorMemoryPlugin vectorMemoryPlugin = VectorMemoryPlugin.builder()
            .dimension(128)
            .embeddingModel("deterministic-hash")
            .embeddingFunction(SemanticSearchHandler::embedText)
            .build();
        vectorMemoryPlugin.initialize(Map.of());

        semanticSearchHandler = new SemanticSearchHandler(vectorMemoryPlugin, client, httpSupport, objectMapper);
        dataProductHandler = new DataProductHandler(client, httpSupport, objectMapper, lineagePlugin);

        entityHandler = new EntityCrudHandler(client, httpSupport, sseHandler.broadcastFunction());
        if (schemaValidator != null) entityHandler.withSchemaValidator(schemaValidator);
        if (openSearchConnector != null) entityHandler.withOpenSearchConnector(openSearchConnector);
        entityHandler.withTraceSupport(traceSpanSupport);
        entityHandler.withSemanticSearchHandler(semanticSearchHandler);

        exportHandler     = new EntityExportHandler(exportService, httpSupport);
        anomalyHandler    = new EntityAnomalyHandler(anomalyDetector, httpSupport, eventLogStore, objectMapper);
        validationHandler = new EntityValidationHandler(schemaValidator, httpSupport);

        eventHandler = new EventHandler(client, httpSupport);
        eventHandler.withTraceSupport(traceSpanSupport);
        pipelineCheckpointHandler = new PipelineCheckpointHandler(client, httpSupport);
        runtimePluginManager = new DataCloudRuntimePluginManager();
        runtimePluginManager.registerWorkflowPlugin(client);
        memoryHandler = new MemoryPlaneHandler(client, httpSupport);
        brainHandler = new BrainHandler(brain, httpSupport);
        learningHandler = new LearningHandler(learningBridge, httpSupport);

        analyticsHandler = new AnalyticsHandler(analyticsEngine, httpSupport);
        if (reportService != null) {
            runtimePluginManager.registerReportPlugin(reportService);
            analyticsHandler.withReportCapability(
                runtimePluginManager.findCapability(ReportExecutionCapability.class).orElse(null));
            analyticsHandler.withReportService(reportService);
        }
        analyticsHandler.withMetrics(new DataCloudHttpMetrics(metricsCollector));

        workflowExecutionHandler = new WorkflowExecutionHandler(httpSupport, runtimePluginManager);

        aiModelHandler = new AiModelHandler(aiModelManager, featureStoreService, httpSupport);
        aiModelHandler.withMetrics(new DataCloudHttpMetrics(metricsCollector));

        healthHandler = new HealthHandler(httpSupport, healthSubsystemSuppliers, metricsCollector);

        // DC-E3: AI assist handler — nullable completionService enables graceful degradation
        aiAssistHandler = new AiAssistHandler(completionService, objectMapper, httpSupport, blockingExecutor);

        // DC-E4: Voice gateway handler — wire Whisper STT adapter if DC_STT_URL is configured
        WhisperSttConfig sttConfig = WhisperSttConfig.fromEnv();
        VoiceSttPort sttPort = sttConfig.enabled()
            ? new HttpWhisperSttAdapter(sttConfig, objectMapper, blockingExecutor)
            : NopVoiceSttAdapter.INSTANCE;

        if (completionService != null || sttConfig.enabled()) {
            healthSubsystemSuppliers.putIfAbsent("voice_gateway", () -> {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", "UP");
                snapshot.put("stt", sttConfig.enabled() ? "UP" : "NOT_CONFIGURED");
                snapshot.put("llm", completionService != null ? "UP" : "NOT_CONFIGURED");
                return snapshot;
            });
        }
        if (auditService != null) {
            healthSubsystemSuppliers.putIfAbsent("audit_service", () -> Map.of("status", "UP", "mode", "in-process"));
        }
        if (policyEngine != null) {
            healthSubsystemSuppliers.putIfAbsent("policy_engine", () -> Map.of("status", "UP", "mode", "in-process"));
        }

        voiceHandler = new VoiceGatewayHandler(
            completionService,
            auditService,
            objectMapper,
            httpSupport,
            blockingExecutor,
            sttPort,
            NopVoiceTtsAdapter.INSTANCE);

        // DC-E5: Data lifecycle and governance handler
        dataLifecycleHandler = new DataLifecycleHandler(client, objectMapper, httpSupport, auditService);
        dataLifecycleHandler.withTraceSupport(traceSpanSupport);

        // B9: Autonomy management handler — nullable controller enables graceful 503
        autonomyHandler = new AutonomyHandler(autonomyController, httpSupport);

        // B3: Agent catalog runtime handler — loads YAML definitions from classpath
        agentCatalogHandler = new AgentCatalogHandler(httpSupport, metricsCollector);

        capabilityRegistryHandler = new CapabilityRegistryHandler(
            httpSupport,
            objectMapper,
            this::buildCapabilitySnapshot);

        // P3.1: Tenant-scoped runtime context layer — in-memory key-value store
        contextLayerHandler = new ContextLayerHandler(httpSupport, objectMapper);

        // P3.9.1: Entity lineage tracking and visualization
        lineageHandler = new LineageHandler(httpSupport, objectMapper, lineagePlugin);


        pluginInstallHandler = new PluginInstallHandler(
                httpSupport,
                com.ghatana.datacloud.spi.StoragePluginRegistry.getInstance(),
            runtimePluginManager,
                metricsCollector);

        // B11: Storage cost estimation handler
        StorageCostHandler storageCostHandler = analyticsEngine != null
                ? new StorageCostHandler(httpSupport, analyticsEngine, metricsCollector)
                : null;

        // B13: Federated Trino query handler — routes to Trino when TRINO_URL is set
        FederatedQueryHandler federatedQueryHandler = analyticsEngine != null
                ? new FederatedQueryHandler(httpSupport, analyticsEngine, metricsCollector, trinoUrl)
                : null;

        // B10: Tier migration handler — uses schedulers when they are configured via withTierMigrationSchedulers()
        tierMigrationHandler = new TierMigrationHandler(httpSupport, warmMigrationScheduler, coldMigrationScheduler);

        log.info("[DC-CAP] Runtime capability summary {}", buildCapabilitySummaryLog());

        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints — delegated to HealthHandler (P7-2b)
            .with(HttpMethod.GET, "/health", healthHandler::handleHealth)
            .with(HttpMethod.GET, "/health/detail", healthHandler::handleHealthDetail)
            .with(HttpMethod.GET, "/ready", healthHandler::handleReady)
            .with(HttpMethod.GET, "/live", healthHandler::handleLive)

            // Info endpoints — delegated to HealthHandler (P7-2b)
            .with(HttpMethod.GET, "/info", healthHandler::handleInfo)
            .with(HttpMethod.GET, "/metrics", healthHandler::handleMetrics)

            // Entity endpoints — delegated to EntityCrudHandler
            .with(HttpMethod.POST, "/api/v1/entities/:collection", entityHandler::handleSaveEntity)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/stream", sseHandler::handleEntityCdcStream)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/search", entityHandler::handleFullTextSearch)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/similar", semanticSearchHandler::handleSimilarEntities)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/query/stream", sseHandler::handleStreamingQuerySse)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id", entityHandler::handleGetEntity)
            // Point-in-time entity snapshot — GET /api/v1/entities/:collection/:id/history?asOf= (B14)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/:id/history", entityHandler::handleGetEntityAsOf)
            .with(HttpMethod.GET, "/api/v1/entities/:collection", entityHandler::handleQueryEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/:id", entityHandler::handleDeleteEntity)
            // Bulk entity endpoints — upsert/delete multiple entities in a single request
            .with(HttpMethod.POST, "/api/v1/entities/:collection/batch", entityHandler::handleBatchSaveEntities)
            .with(HttpMethod.DELETE, "/api/v1/entities/:collection/batch", entityHandler::handleBatchDeleteEntities)
            // Bulk export and anomaly detection endpoints — delegated to dedicated handlers (DC-004)
            .with(HttpMethod.GET, "/api/v1/entities/:collection/export", exportHandler::handleExportEntities)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/anomalies", anomalyHandler::handleDetectAnomalies)
            .with(HttpMethod.GET,  "/api/v1/anomalies", anomalyHandler::handleQueryAnomalies)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/validate", validationHandler::handleValidateEntity)
            .with(HttpMethod.POST, "/api/v1/entities/:collection/validate/batch", validationHandler::handleBatchValidateEntities)

            // Event endpoints — delegated to EventHandler
            .with(HttpMethod.POST, "/api/v1/events", eventHandler::handleAppendEvent)
            .with(HttpMethod.GET, "/api/v1/events", eventHandler::handleQueryEvents)
            .with(HttpMethod.GET, "/api/v1/events/:offset", eventHandler::handleGetEventByOffset)

            // Pipeline registry endpoints — delegated to PipelineCheckpointHandler
            .with(HttpMethod.GET,    "/api/v1/pipelines",                pipelineCheckpointHandler::handleListPipelines)
            .with(HttpMethod.POST,   "/api/v1/pipelines",                pipelineCheckpointHandler::handleSavePipeline)
            .with(HttpMethod.GET,    "/api/v1/pipelines/:pipelineId",    pipelineCheckpointHandler::handleGetPipeline)
            .with(HttpMethod.PUT,    "/api/v1/pipelines/:pipelineId",    pipelineCheckpointHandler::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId",    pipelineCheckpointHandler::handleDeletePipeline)
            .with(HttpMethod.POST,   "/api/v1/pipelines/:pipelineId/execute", workflowExecutionHandler::handleExecutePipeline)
            .with(HttpMethod.GET,    "/api/v1/pipelines/:pipelineId/executions", workflowExecutionHandler::handleListExecutions)
            .with(HttpMethod.GET,    "/api/v1/pipelines/:pipelineId/executions/:executionId", workflowExecutionHandler::handleGetWorkflowExecution)
            .with(HttpMethod.POST,   "/api/v1/pipelines/:pipelineId/executions/:executionId/cancel", workflowExecutionHandler::handleCancelExecution)

            // Checkpoint management endpoints (DC-3) — delegated to PipelineCheckpointHandler
            .with(HttpMethod.GET, "/api/v1/checkpoints", pipelineCheckpointHandler::handleListCheckpoints)
            .with(HttpMethod.POST, "/api/v1/checkpoints", pipelineCheckpointHandler::handleSaveCheckpoint)
            .with(HttpMethod.GET, "/api/v1/checkpoints/:checkpointId", pipelineCheckpointHandler::handleGetCheckpoint)
            .with(HttpMethod.DELETE, "/api/v1/checkpoints/:checkpointId", pipelineCheckpointHandler::handleDeleteCheckpoint)

            // Agent memory plane endpoints (P4.6.1) — delegated to MemoryPlaneHandler
            .with(HttpMethod.GET,    "/api/v1/memory",                           memoryHandler::handleListMemory)
            .with(HttpMethod.POST,   "/api/v1/memory/:agentId",                  memoryHandler::handleStoreMemory)
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
            .with(HttpMethod.POST,   "/api/v1/learning/review/:reviewId/approve",   learningHandler::handleLearningReviewApprove)
            .with(HttpMethod.POST,   "/api/v1/learning/review/:reviewId/reject",    learningHandler::handleLearningReviewReject)
            .with(HttpMethod.DELETE, "/api/v1/learning/review/completed",           learningHandler::handlePurgeCompletedReviews)

            // Analytics routes (DC-9) — delegated to AnalyticsHandler
            .with(HttpMethod.POST, "/api/v1/analytics/query",                     analyticsHandler::handleAnalyticsQuery)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId",            analyticsHandler::handleAnalyticsGetResult)
            .with(HttpMethod.GET,  "/api/v1/analytics/query/:queryId/plan",       analyticsHandler::handleAnalyticsGetPlan)
            .with(HttpMethod.POST, "/api/v1/analytics/aggregate",                 analyticsHandler::handleAnalyticsAggregate)
            .with(HttpMethod.POST, "/api/v1/analytics/explain",                   analyticsHandler::handleAnalyticsExplain)

            // Reporting routes (DC-10) — delegated to AnalyticsHandler
            .with(HttpMethod.POST, "/api/v1/reports",             analyticsHandler::handleCreateReport)
            .with(HttpMethod.GET,  "/api/v1/reports",             analyticsHandler::handleListReports)
            .with(HttpMethod.GET,  "/api/v1/reports/:reportId",   analyticsHandler::handleGetReport)
            .with(HttpMethod.GET,  "/api/v1/executions/:executionId", workflowExecutionHandler::handleGetExecution)
            .with(HttpMethod.GET,  "/api/v1/executions/:executionId/logs", workflowExecutionHandler::handleExecutionLogs)
            .with(HttpMethod.POST, "/api/v1/executions/:executionId/cancel", workflowExecutionHandler::handleCancelExecution)

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
            .with(HttpMethod.GET, "/api/v1/learning/stream",      sseHandler::handleLearningStream)

            // WebSocket endpoint for real-time collection change notifications (DC-12)
            .withWebSocket("/ws", sseHandler::handleWebSocketConnection)

            // AI Assist routes (DC-E3) — gracefully degrade to heuristics when LLM unavailable
            .with(HttpMethod.POST, "/api/v1/entities/:collection/suggest",    aiAssistHandler::handleEntitySuggest)
            .with(HttpMethod.POST, "/api/v1/analytics/suggest",               aiAssistHandler::handleAnalyticsSuggest)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/optimise-hint", aiAssistHandler::handlePipelineOptimiseHint)
            .with(HttpMethod.POST, "/api/v1/brain/explain",                   aiAssistHandler::handleBrainExplain)

            // Voice gateway routes (DC-E4)
            .with(HttpMethod.POST, "/api/v1/voice/intent",          voiceHandler::handleVoiceIntent)
            .with(HttpMethod.GET,  "/api/v1/voice/intents",         voiceHandler::handleListIntents)
            .with(HttpMethod.POST, "/api/v1/voice/intent/classify",  voiceHandler::handleClassifyOnly)

            // Governance / data-lifecycle routes (DC-E5)
            .with(HttpMethod.POST, "/api/v1/governance/retention/classify", dataLifecycleHandler::handleClassifyRetention)
            .with(HttpMethod.GET,  "/api/v1/governance/retention/policy",   dataLifecycleHandler::handleGetRetentionPolicy)
            .with(HttpMethod.POST, "/api/v1/governance/retention/purge",    dataLifecycleHandler::handlePurge)
            .with(HttpMethod.POST, "/api/v1/governance/privacy/redact",     dataLifecycleHandler::handleRedact)
            .with(HttpMethod.GET,  "/api/v1/governance/privacy/pii-fields", dataLifecycleHandler::handleListPiiFields)
            .with(HttpMethod.GET,  "/api/v1/governance/compliance/summary", dataLifecycleHandler::handleComplianceSummary)

            // Runtime capability registry (P2.7)
            .with(HttpMethod.GET,  "/api/v1/capabilities",                  capabilityRegistryHandler::handleCapabilities)

            // Entity lineage graph and impact analysis (P3.9.1)
            .with(HttpMethod.GET, "/api/v1/lineage/:collection",        lineageHandler::handleGetLineage)
            .with(HttpMethod.GET, "/api/v1/lineage/:collection/impact",  lineageHandler::handleGetImpact)

            // Tenant-scoped context layer (P3.1) — lightweight runtime key-value store
            .with(HttpMethod.GET,    "/api/v1/context",                     contextLayerHandler::handleGetContext)
            .with(HttpMethod.PUT,    "/api/v1/context",                     contextLayerHandler::handlePutContext)
            .with(HttpMethod.DELETE, "/api/v1/context/keys/:key",           contextLayerHandler::handleDeleteContextKey)
            .with(HttpMethod.GET,    "/api/v1/context/snapshot",            contextLayerHandler::handleGetSnapshot)
            .with(HttpMethod.POST,   "/api/v1/context/:collection/rag",     semanticSearchHandler::handleCollectionRag)

            // Data product catalog routes (P4.4.1)
            .with(HttpMethod.GET,    "/api/v1/data-products",               dataProductHandler::handleListDataProducts)
            .with(HttpMethod.POST,   "/api/v1/data-products",               dataProductHandler::handlePublishDataProduct)
            .with(HttpMethod.POST,   "/api/v1/data-products/:productId/subscribe", dataProductHandler::handleSubscribe)

            // Autonomy management routes (B9) — emergency global shutoff and domain-level controls
            .with(HttpMethod.PUT, "/api/v1/autonomy/level",              autonomyHandler::handleSetGlobalLevel)
            .with(HttpMethod.GET, "/api/v1/autonomy/level",              autonomyHandler::handleGetGlobalLevel)
            .with(HttpMethod.GET, "/api/v1/autonomy/domains",            autonomyHandler::handleListDomains)
            .with(HttpMethod.GET, "/api/v1/autonomy/domains/:domain",    autonomyHandler::handleGetDomain)
            .with(HttpMethod.GET, "/api/v1/autonomy/logs",               autonomyHandler::handleGetLogs)

            // Agent catalog runtime API (B3) — YAML-backed catalog served as JSON
            .with(HttpMethod.GET, "/api/v1/agents/catalog",              agentCatalogHandler::handleListCatalog)
            .with(HttpMethod.GET, "/api/v1/agents/catalog/:id",          agentCatalogHandler::handleGetAgent)

            // Plugin lifecycle management API (B6) — install, enable/disable, upgrade
            .with(HttpMethod.GET,  "/api/v1/plugins",                    pluginInstallHandler::handleListPlugins)
            .with(HttpMethod.GET,  "/api/v1/plugins/:id",                pluginInstallHandler::handleGetPlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/enable",         pluginInstallHandler::handleEnablePlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/disable",        pluginInstallHandler::handleDisablePlugin)
            .with(HttpMethod.POST, "/api/v1/plugins/:id/upgrade",        pluginInstallHandler::handleUpgradePlugin)

            // Storage cost estimate + per-collection cost report (B11)
            .with(HttpMethod.GET,  "/api/v1/queries/estimate",
                    storageCostHandler != null ? storageCostHandler::handleEstimateQuery
                            : req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")))
            .with(HttpMethod.GET,  "/api/v1/collections/:id/cost-report",
                    storageCostHandler != null ? storageCostHandler::handleCollectionCostReport
                            : req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")))

            // Federated Trino query endpoint (B13)
            .with(HttpMethod.POST, "/api/v1/queries/federated",
                    federatedQueryHandler != null ? federatedQueryHandler::handleFederatedQuery
                            : req -> Promise.of(httpSupport.errorResponse(503, "Analytics engine not available")))

            // Manual storage-tier migration (B10)
            .with(HttpMethod.POST, "/api/v1/collections/:id/migrate",
                    tierMigrationHandler != null ? tierMigrationHandler::handleMigrateCollection
                            : req -> Promise.of(httpSupport.errorResponse(503, "Tier migration schedulers are not configured")))

            .build();

        AsyncServlet filteredRouter = payloadSizeLimitFilter(contentTypeFilter(router));

        // DC-E1: wrap router with security filter when API key or JWT auth is configured
        AsyncServlet rootServlet;
        if (apiKeyResolver != null || jwtProvider != null) {
            DataCloudSecurityFilter securityFilter = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .jwtProvider(jwtProvider)
                .jwtTenantClaim(jwtTenantClaim)
                .policyEngine(policyEngine)
                .auditService(auditService)
                .build();
            rootServlet = securityFilter.apply(filteredRouter);
            log.info("[DC-E1] security filter active (apiKey: {}, jwt: {}, policy engine: {})",
                apiKeyResolver != null ? "enabled" : "disabled",
                jwtProvider != null ? "enabled" : "disabled",
                policyEngine != null ? "enabled" : "advisory-only");
        } else {
            rootServlet = filteredRouter;
            log.info("[DC-E1] security filter inactive — withApiKeyResolver/withJwtProvider not called");
        }

        rootServlet = new RequestObservationFilter(httpSupport, businessMetrics, traceExportService, traceSamplingRate).apply(rootServlet);

        server = HttpServer.builder(eventloop,
                corsFilter(rateLimitFilter(rootServlet)))
            .withListenPort(port)
            .build();

        blockingExecutor.execute(() -> {
            try {
                server.listen();
                log.info("Data-Cloud HTTP Server started on port {}", port);
                // Start background anomaly detection scanning (P3.6.1) if detector + event store are both available
                if (anomalyDetector != null && eventLogStore != null) {
                    anomalyDetectionTask = new AnomalyDetectionTask(anomalyDetector, anomalyHandler, eventloop);
                    anomalyDetectionTask.start();
                    log.info("[DC-P3.6] Continuous anomaly detection active — scan interval {} min",
                            AnomalyDetectionTask.SCAN_INTERVAL_MINUTES);
                }
                if (client.entityStore() instanceof H2SovereignEntityStore sovereignEntityStore) {
                    storageCompactionTask = new StorageCompactionTask(
                        sovereignEntityStore,
                        autonomyController,
                        auditService,
                        metricsCollector,
                        eventloop,
                        storageCompactionIntervalSeconds,
                        storageCompactionTombstoneThreshold);
                    storageCompactionTask.start();
                    healthSubsystemSuppliers.put("storage_compaction", storageCompactionTask::status);
                    log.info("Sovereign storage compaction active — interval={}s threshold={}",
                        storageCompactionIntervalSeconds,
                        storageCompactionTombstoneThreshold);
                }
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
        if (runtimePluginManager != null) {
            runtimePluginManager.close();
        }
        if (anomalyDetectionTask != null) {
            anomalyDetectionTask.close();
        }
        if (storageCompactionTask != null) {
            storageCompactionTask.close();
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

    private Map<String, Object> buildCapabilitySnapshot() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("authentication.apiKey", capabilityEntry(apiKeyResolver != null, null));
        capabilities.put("authentication.jwt", capabilityEntry(jwtProvider != null, null));
        capabilities.put("brain", capabilityEntry(brain != null, null));
        capabilities.put("learning", capabilityEntry(learningBridge != null, null));
        capabilities.put("analytics", capabilityEntry(analyticsEngine != null, null));
        capabilities.put("reporting", capabilityEntry(reportService != null, null));
        capabilities.put("search.openSearch", capabilityEntry(openSearchConnector != null, null));
        capabilities.put("entityExport", capabilityEntry(exportService != null, null));
        capabilities.put("anomalyDetection", capabilityEntry(anomalyDetector != null, null));
        capabilities.put("schemaValidation", capabilityEntry(schemaValidator != null, null));
        capabilities.put("ai.modelRegistry", capabilityEntry(aiModelManager != null, null));
        capabilities.put("ai.featureStore", capabilityEntry(featureStoreService != null, resolveSubsystemStatus("ai_inference")));
        capabilities.put("ai.assist", capabilityEntry(completionService != null, null));
        capabilities.put(
            "voiceGateway",
            capabilityEntry(completionService != null || WhisperSttConfig.fromEnv().enabled(), resolveSubsystemStatus("voice_gateway")));
        capabilities.put("governance.audit", capabilityEntry(auditService != null, resolveSubsystemStatus("audit_service")));
        capabilities.put("governance.policyEngine", capabilityEntry(policyEngine != null, resolveSubsystemStatus("policy_engine")));
        capabilities.put("federatedQuery.trino", capabilityEntry(trinoUrl != null && !trinoUrl.isBlank(), null));
        capabilities.put("autonomy", capabilityEntry(autonomyController != null, null));
        capabilities.put("storage.compaction", capabilityEntry(storageCompactionTask != null, resolveSubsystemStatus("storage_compaction")));
        capabilities.put("tierMigration.warm", capabilityEntry(warmMigrationScheduler != null, null));
        capabilities.put("tierMigration.cold", capabilityEntry(coldMigrationScheduler != null, null));
        capabilities.put("health.database", capabilityEntry(healthSubsystemSuppliers.containsKey("database"), resolveSubsystemStatus("database")));
        capabilities.put("health.aiInference", capabilityEntry(healthSubsystemSuppliers.containsKey("ai_inference"), resolveSubsystemStatus("ai_inference")));
        capabilities.put("health.eventStore", capabilityEntry(healthSubsystemSuppliers.containsKey("event_store"), resolveSubsystemStatus("event_store")));
        capabilities.put("health.storageCompaction", capabilityEntry(healthSubsystemSuppliers.containsKey("storage_compaction"), resolveSubsystemStatus("storage_compaction")));
        return capabilities;
    }

    private String buildCapabilitySummaryLog() {
        return buildCapabilitySnapshot().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + ((Map<?, ?>) entry.getValue()).get("status"))
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private Map<String, Object> capabilityEntry(boolean configured, String subsystemStatus) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", subsystemStatusToCapabilityStatus(configured, subsystemStatus));
        entry.put("configured", configured);
        if (subsystemStatus != null) {
            entry.put("dependencyStatus", subsystemStatus);
        }
        return entry;
    }

    private String subsystemStatusToCapabilityStatus(boolean configured, String subsystemStatus) {
        if (!configured) {
            return "NOT_CONFIGURED";
        }
        if (subsystemStatus == null || subsystemStatus.isBlank() || "UP".equals(subsystemStatus)) {
            return "ACTIVE";
        }
        if ("DOWN".equals(subsystemStatus) || "DEGRADED".equals(subsystemStatus)) {
            return "DEGRADED";
        }
        if ("NOT_CONFIGURED".equals(subsystemStatus)) {
            return "NOT_CONFIGURED";
        }
        return "ACTIVE";
    }

    private String resolveSubsystemStatus(String subsystemName) {
        Supplier<Map<String, Object>> supplier = healthSubsystemSuppliers.get(subsystemName);
        if (supplier == null) {
            return null;
        }
        try {
            Map<String, Object> snapshot = supplier.get();
            Object status = snapshot.get("status");
            return status == null ? null : String.valueOf(status);
        } catch (RuntimeException exception) {
            return "DOWN";
        }
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
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsAllowOrigin))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(CORS_ALLOW_METHODS))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(CORS_ALLOW_HEADERS))
                    .withHeader(HttpHeaders.of("Access-Control-Max-Age"),      HttpHeaderValue.of(CORS_MAX_AGE))
                    .build());
            }
            return delegate.serve(request);
        };
    }

    /**
    * Middleware: sliding-window tenant/IP-aware rate limiter, backed by
     * {@code platform:java:governance} {@link RateLimitFilter}.
     *
    * <p>Each unique tenant/client bucket is allowed at most {@link #rateLimitRequests}
    * requests within a {@link #rateLimitWindowSeconds}-second sliding window (evaluated by
     * the platform filter's deque-based algorithm).
     *
     * <p>The raw 429 response emitted by the platform filter (plain text, no CORS) is
     * upgraded here to include:
     * <ul>
     *   <li>A JSON body: {@code {"error":"Too Many Requests","retryAfterSeconds":60}}</li>
     *   <li>{@code Access-Control-Allow-Origin} matching the configured CORS policy</li>
     * </ul>
     * so that browser clients receive a parsable, CORS-safe error response.
     *
     * @doc.type method
     * @doc.purpose Protect the HTTP API from per-IP request flooding using platform rate limiter
     * @doc.layer product
     * @doc.pattern Middleware
     */
    private AsyncServlet rateLimitFilter(AsyncServlet delegate) {
        AsyncServlet platformGuarded = platformRateLimiter.wrap(delegate);
        return request -> platformGuarded.serve(request).map(response -> {
            if (response.getCode() != 429) {
                return response;
            }
            // Upgrade platform's plain-text 429 → JSON 429 with CORS header
            String body = String.format(
                    "{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d}",
                    rateLimitWindowSeconds);
            return HttpResponse.ofCode(429)
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                            HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                    .withHeader(HttpHeaders.of("Retry-After"),
                            HttpHeaderValue.of(String.valueOf(rateLimitWindowSeconds)))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                            HttpHeaderValue.of(corsAllowOrigin))
                    .withBody(body.getBytes(StandardCharsets.UTF_8))
                    .build();
        });
    }

    private static String rateLimitClientKey(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        String clientIp = remoteIp(request);
        if (tenantId == null || tenantId.isBlank()) {
            return clientIp;
        }
        return tenantId.trim() + "|" + clientIp;
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
            if (isMutationMethod(method) && requiresJsonBody(request)) {
                String ct = request.getHeader(HttpHeaders.CONTENT_TYPE);
                if (ct == null || !ct.contains(JSON_CONTENT_TYPE)) {
                    return Promise.of(HttpResponse.ofCode(415)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                            HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                            HttpHeaderValue.of(corsAllowOrigin))
                        .withBody(("{\"error\":\"Content-Type must be application/json\"}").getBytes(StandardCharsets.UTF_8))
                        .build());
                }
            }
            return delegate.serve(request);
        };
    }

    private static boolean isMutationMethod(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private static boolean requiresJsonBody(HttpRequest request) {
        String path = request.getPath();
        for (Pattern route : BODYLESS_MUTATION_ROUTES) {
            if (route.matcher(path).matches()) {
                return false;
            }
        }
        return true;
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
                                HttpHeaderValue.of(corsAllowOrigin))
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
