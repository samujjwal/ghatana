package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.mastery.MasteryEvidenceRepository;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryTransitionRepository;
import com.ghatana.agent.mastery.transition.DefaultMasteryTransitionPolicy;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;
import com.ghatana.agent.obsolescence.DefaultObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.promotion.DefaultPromotionEngine;
import com.ghatana.agent.promotion.PromotionEngine;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.application.DataCloudProductReleaseReadinessRepository;
import com.ghatana.datacloud.application.ProductReleaseReadinessService;
import com.ghatana.datacloud.api.controller.MasteryController;
import com.ghatana.datacloud.api.controller.MediaArtifactController;
import com.ghatana.datacloud.agent.learning.delta.DataCloudLearningDeltaRepository;
import com.ghatana.datacloud.agent.mastery.DataCloudMasteryEvidenceRepository;
import com.ghatana.datacloud.agent.mastery.DataCloudMasteryRegistry;
import com.ghatana.datacloud.agent.mastery.DataCloudMasteryTransitionRepository;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.platform.config.ConfigManager;
import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.launcher.anomaly.AnomalyDetectionTask;
import com.ghatana.datacloud.launcher.compaction.StorageCompactionTask;
import com.ghatana.datacloud.spi.TransactionManager;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.datacloud.spi.EntityWriteIdempotencyStore;
import com.ghatana.datacloud.spi.EntityWriteOutboxProcessor;
import com.ghatana.datacloud.spi.InMemoryEntityWriteOutboxProcessor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;
import java.util.function.Supplier;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;


import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.http.security.filter.RateLimitFilter;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityExportHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityAnomalyHandler;
import com.ghatana.datacloud.launcher.http.handlers.EntityValidationHandler;
import com.ghatana.datacloud.launcher.http.handlers.EventHandler;
import com.ghatana.datacloud.launcher.http.handlers.PipelineCheckpointHandler;
import com.ghatana.datacloud.launcher.http.handlers.WorkflowExecutionHandler;
import com.ghatana.datacloud.launcher.http.handlers.MemoryPlaneHandler;
import com.ghatana.datacloud.launcher.http.handlers.McpToolsHandler;
import com.ghatana.datacloud.launcher.http.handlers.BrainHandler;
import com.ghatana.datacloud.launcher.http.handlers.LearningHandler;
import com.ghatana.datacloud.launcher.http.handlers.AnalyticsHandler;
import com.ghatana.datacloud.launcher.http.handlers.AiModelHandler;
import com.ghatana.datacloud.launcher.http.handlers.HealthHandler;
import com.ghatana.datacloud.launcher.http.handlers.SseStreamingHandler;
import com.ghatana.datacloud.launcher.http.handlers.AiAssistHandler;
import com.ghatana.datacloud.launcher.http.handlers.VoiceGatewayHandler;
import com.ghatana.datacloud.launcher.http.handlers.DataLifecycleHandler;
import com.ghatana.datacloud.launcher.http.handlers.AutonomyHandler;
import com.ghatana.datacloud.launcher.http.handlers.AgentCatalogHandler;
import com.ghatana.datacloud.launcher.http.handlers.AlertingHandler;
import com.ghatana.datacloud.launcher.http.handlers.PluginInstallHandler;
import com.ghatana.datacloud.launcher.http.handlers.StorageCostHandler;
import com.ghatana.datacloud.launcher.http.handlers.FederatedQueryHandler;
import com.ghatana.datacloud.launcher.http.handlers.TierMigrationHandler;
import com.ghatana.datacloud.launcher.http.handlers.DataSourceRegistryHandler;
import com.ghatana.datacloud.launcher.http.handlers.SurfaceRegistryHandler;
import com.ghatana.datacloud.launcher.http.handlers.CollectionContextHandler;
import com.ghatana.datacloud.launcher.http.handlers.ComplianceHandler;
import com.ghatana.datacloud.launcher.http.handlers.ContextLayerHandler;
import com.ghatana.datacloud.launcher.http.handlers.ContextStore;
import com.ghatana.datacloud.launcher.http.handlers.DataProductHandler;
import com.ghatana.datacloud.launcher.http.handlers.InMemoryContextStore;
import com.ghatana.datacloud.launcher.http.handlers.LineageHandler;
import com.ghatana.datacloud.launcher.http.handlers.ProviderConformanceHandler;
import com.ghatana.datacloud.launcher.http.handlers.ProductReleaseReadinessHandler;
import com.ghatana.datacloud.launcher.http.handlers.SemanticSearchHandler;
import com.ghatana.datacloud.launcher.http.handlers.SettingsHandler;
import com.ghatana.datacloud.launcher.http.handlers.SovereignProfileHandler;
import com.ghatana.datacloud.launcher.http.handlers.UserActivityHandler;
import com.ghatana.datacloud.launcher.settings.InMemorySettingsStore;
import com.ghatana.datacloud.launcher.settings.SettingsStore;
import com.ghatana.datacloud.memory.media.DataCloudMediaArtifactRepository;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.ReportExecutionCapability;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import com.ghatana.datacloud.plugins.iceberg.TierMigrationScheduler;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.application.observability.TraceExportService;
import com.ghatana.datacloud.launcher.http.voice.HttpWhisperSttAdapter;
import com.ghatana.datacloud.launcher.http.voice.HttpSpeechTtsAdapter;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceSttAdapter;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceTtsAdapter;
import com.ghatana.datacloud.launcher.http.voice.VoiceSttPort;
import com.ghatana.datacloud.launcher.http.voice.VoiceTtsConfig;
import com.ghatana.datacloud.launcher.http.voice.WhisperSttConfig;

import com.ghatana.datacloud.launcher.RuntimeProfileValidator;
import com.ghatana.datacloud.launcher.http.SurfaceRecord;
import com.ghatana.datacloud.launcher.http.DependencyProbeResult;

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
        Pattern.compile("^/api/v1/action/executions/[^/]+/(cancel|retry|rollback|restore|checkpoint)$"),
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
    private final ConfigManager config;
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
    /** True only when metrics collector has been explicitly configured by caller. */
    private boolean metricsCollectorConfigured = false;
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

    /** Optional tenant quota service for storage, entity count, rate limiting (P0.5). */
    private TenantQuotaService tenantQuotaService;

    /**
     * Optional API-key resolver for the security filter (DC-E1).
     * When {@code null}, the security filter is not activated.
     */
    private ApiKeyResolver apiKeyResolver;

    /**
     * Optional persistent {@link SettingsStore} for admin settings (DC-S14).
     * When {@code null}, an {@link InMemorySettingsStore} is used.
     */
    private SettingsStore settingsStore;

    /**
     * Optional durable idempotency store for entity writes (DC-P1-008).
     * When {@code null}, falls back to an in-memory ConcurrentHashMap (local/embedded only).
     */
    private EntityWriteIdempotencyStore entityWriteIdempotencyStore;

    /**
     * Optional entity write outbox processor for transaction/audit side effects.
     * Production-like deployments must provide a durable implementation.
     */
    private EntityWriteOutboxProcessor entityWriteOutboxProcessor;

    /**
     * Optional durable generic idempotency store for all mutating routes (DC-BE-002).
     * When {@code null}, falls back to an in-memory ConcurrentHashMap (local/embedded only).
     */
    private WriteIdempotencyStore genericIdempotencyStore;

    /**
     * Optional transaction manager for atomic multi-step writes (DC-BE-003).
     * When {@code null}, multi-step writes execute without transaction boundaries.
     */
    private TransactionManager transactionManager;

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
     * When {@code null}, CRITICAL routes fail-closed when enforcing=true (default);
     * audit-only mode ({@code enforcing=false}) allows passthrough.
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
    private String listenHost;

    // ==================== Extracted Handler Delegates ====================
    private HttpHandlerSupport httpSupport;
    private String corsAllowOrigin = DEFAULT_LOCAL_CORS_ALLOW_ORIGIN;
    private boolean strictTenantResolution = false;
    /** Deployment mode label for observability and UI warnings (DC-AUD-024). */
    private String deploymentMode = "local";
    /**
     * DC-P1-003: Registry-driven set of collection names requiring transactional all-or-nothing delete.
     * Null means use {@link com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandler}'s built-in defaults.
     */
    private Set<String> criticalCollections = null;
    private EntityCrudHandler entityHandler;
    private EntityExportHandler exportHandler;
    private EntityAnomalyHandler anomalyHandler;
    /** Optional event log store for durable anomaly persistence. */
    private EventLogStore eventLogStore;
    private EntityValidationHandler validationHandler;
    private EventHandler eventHandler;
    private PipelineCheckpointHandler pipelineCheckpointHandler;
    private WorkflowExecutionHandler workflowExecutionHandler;
    /**
     * Optional pre-wired workflow execution capability for testing or embedded deployments.
     * When set, it takes precedence over the runtime plugin manager discovery.
     */
    private WorkflowExecutionCapability workflowExecutionCapabilityOverride;
    private AlertingHandler alertingHandler;
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
    private boolean pluginUpgradeEnabled = false;
    private DataCloudRuntimePluginManager runtimePluginManager;
    private SurfaceRegistryHandler surfaceRegistryHandler; // P2.7: runtime surface registry API
    private ContextLayerHandler contextLayerHandler; // P3.1: tenant-scoped context layer API
    private ContextStore contextStore = new InMemoryContextStore();
    private CollectionContextHandler collectionContextHandler; // P3.1: unified collection context API
    private McpToolsHandler mcpToolsHandler; // P3.1.2: MCP tool registry and invocation
    private DataProductHandler dataProductHandler; // P4.4.1: data products publish/discover/subscribe
    private LineagePlugin lineagePlugin;              // P3.9.1: entity lineage tracking
    private KnowledgeGraphPlugin knowledgeGraphPlugin; // P3.1: relationship enrichment for context APIs
    private LineageHandler lineageHandler;           // P3.9.1: entity lineage HTTP handler
    private SemanticSearchHandler semanticSearchHandler; // P4.5.1: semantic similarity and RAG
    private TierMigrationScheduler warmMigrationScheduler; // B10: L1→L2 warm tier scheduler
    private ArchiveMigrationScheduler coldMigrationScheduler; // B10: L2→L3 cold tier scheduler
    private TierMigrationHandler tierMigrationHandler; // B10: manual tier migration API (wired in start())
    private SettingsHandler settingsHandler; // admin settings CRUD API
    private ProductReleaseReadinessHandler productReleaseReadinessHandler;
    private UserActivityHandler userActivityHandler;
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
        this.client          = requireNonNull(client, "client must not be null");
        this.port            = port;
        this.brain           = brain;
        this.learningBridge  = learningBridge;
        this.analyticsEngine = analyticsEngine;
        this.objectMapper    = JsonUtils.getDefaultMapper();
        this.config          = ConfigManager.createDefault("data-cloud");
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
     * <p>When this method is not called, both routes return {@code 501 Unavailable}.
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
     * Attaches a {@link KnowledgeGraphPlugin} to enrich context APIs with relationships.
     *
     * @param plugin the knowledge graph plugin; {@code null} disables relationship enrichment
     * @return this server for chaining
     */
    public DataCloudHttpServer withKnowledgeGraphPlugin(KnowledgeGraphPlugin plugin) {
        this.knowledgeGraphPlugin = plugin;
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
        this.metricsCollectorConfigured = true;
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
     * Attaches a persistent {@link SettingsStore} for admin settings (DC-S14).
     *
     * <p>When not called, an {@link InMemorySettingsStore} is used. Production
     * deployments should provide a persistent {@code JdbcSettingsStore} backed by a
     * real JDBC {@link javax.sql.DataSource}.
     *
     * @param store the settings store implementation; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Enable persistent settings storage instead of in-memory map
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withSettingsStore(SettingsStore store) {
        this.settingsStore = store;
        return this;
    }

    /**
     * Attaches a durable {@link EntityWriteIdempotencyStore} so idempotency entries survive
     * process restarts (DC-P1-008).
     *
     * <p>When not called, the entity handler falls back to an in-memory store which is
     * lost on restart. Non-embedded deployments must call this with a durable implementation.
     *
     * @param store the idempotency store implementation; must not be {@code null}
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Enable durable entity-write idempotency storage
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withIdempotencyStore(EntityWriteIdempotencyStore store) {
        this.entityWriteIdempotencyStore = store;
        return this;
    }

    /**
     * Attaches the entity-write outbox processor used for atomic entity/event/audit side effects.
     * Non-local deployments must provide a durable processor so pending outbox entries survive restarts.
     *
     * @param processor the outbox processor implementation; must not be {@code null}
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withEntityWriteOutboxProcessor(EntityWriteOutboxProcessor processor) {
        this.entityWriteOutboxProcessor = requireNonNull(processor, "processor must not be null");
        return this;
    }

    /**
     * Attaches a generic idempotency store for all mutating routes (DC-BE-002).
     *
     * <p>When {@code null}, falls back to an in-memory ConcurrentHashMap (local/embedded only).
     *
     * @param store the generic idempotency store
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach generic idempotency store for all mutating routes
     * @doc.layer product
     */
    public DataCloudHttpServer withGenericIdempotencyStore(WriteIdempotencyStore store) {
        this.genericIdempotencyStore = store;
        return this;
    }

    /**
     * Attaches a transaction manager for atomic multi-step writes (DC-BE-003).
     *
     * <p>When {@code null}, multi-step writes execute without transaction boundaries.
     *
     * @param transactionManager the transaction manager
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Attach transaction manager for atomic multi-step writes
     * @doc.layer product
     */
    public DataCloudHttpServer withTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
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
     * Configures the interface/host used by the embedded HTTP listener.
     *
     * <p>When unset, the server binds using the existing port-only behavior.
     */
    public DataCloudHttpServer withListenHost(String listenHost) {
        this.listenHost = listenHost;
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
     * Attaches a {@link DataCloudRuntimePluginManager} to enable runtime plugin management.
     *
     * <p>An externally-created plugin manager can be wired in for testing, or to share a
     * plugin manager between multiple subsystems. When this method is not called, the server
     * creates and owns its own plugin manager during {@link #start()}.
     *
     * @param pluginManager the runtime plugin manager to attach; must not be {@code null}
     * @return {@code this} for method chaining
     * @doc.type method
     * @doc.purpose Wire an external runtime plugin manager into the server
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withPluginManager(DataCloudRuntimePluginManager pluginManager) {
        this.runtimePluginManager = pluginManager;
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
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withTenantQuotaService(TenantQuotaService service) {
        this.tenantQuotaService = service;
        return this;
    }

    /**
     * Pre-wires a {@link WorkflowExecutionCapability} directly, bypassing runtime plugin discovery.
     *
     * <p>Intended for integration testing or embedded deployments where the plugin manager
     * is not available. When set before {@link #start()}, this capability is applied to
     * the workflow execution handler and takes precedence over plugin manager discovery.
     *
     * @param capability the capability to wire; {@code null} clears the override
     * @return {@code this} for method chaining
     *
     * @doc.type method
     * @doc.purpose Allow direct capability injection for testing and embedded deployments
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withWorkflowExecutionCapability(WorkflowExecutionCapability capability) {
        this.workflowExecutionCapabilityOverride = capability;
        return this;
    }

    /**
     * Enables the built-in workflow execution plugin (simulation mode).
     * @param enabled {@code true} to activate the upgrade route
     * @return this server (fluent)
     * @doc.type method
     * @doc.purpose Hard-gate plugin hot-swap to prevent accidental activation in production
     * @doc.layer product
     * @doc.pattern Builder
     */
    public DataCloudHttpServer withPluginUpgradeEnabled(boolean enabled) {
        this.pluginUpgradeEnabled = enabled;
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
     * Sets the deployment mode label for observability and UI warnings (DC-AUD-024).
     *
     * <p>Valid values: {@code local}, {@code development}, {@code staging}, {@code production}.
     * The mode is exposed via capability snapshot so the UI can warn when using
     * in-memory fallbacks or local data.
     *
     * @param mode deployment mode label
     * @return {@code this} for method chaining
     */
    public DataCloudHttpServer withDeploymentMode(String mode) {
        this.deploymentMode = (mode == null || mode.isBlank()) ? "local" : mode;
        return this;
    }

    /**
     * DC-P1-003: Registers a config/registry-driven set of collection names that must be
     * deleted all-or-nothing (transactional). When set, overrides the built-in critical list
     * in {@link com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandler}.
     *
     * @param collections non-null, non-empty set of critical collection names
     * @return {@code this} for chaining
     */
    public DataCloudHttpServer withCriticalCollections(Set<String> collections) {
        this.criticalCollections = (collections == null || collections.isEmpty()) ? null : Set.copyOf(collections);
        return this;
    }

    /**
     * Injects the Context Plane backing store.
     *
     * <p>Production-like deployment modes must provide a durable implementation.
     * The default in-memory store is reserved for local and test use.
     */
    public DataCloudHttpServer withContextStore(ContextStore store) {
        this.contextStore = requireNonNull(store, "store");
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
                                              String deploymentMode,
                                              Logger logger) {
        requireNonNull(logger, "logger");

        if (authConfigured) {
            return;
        }

        // DC-P3: Fail-closed security - require authentication in production profiles
        boolean isProduction = isProductionMode(deploymentMode);
        if (isProduction) {
            throw new IllegalStateException(
                "Authentication is REQUIRED in deployment profile '" + deploymentMode + "'. " +
                    "Production/staging/sovereign profiles must configure authentication. " +
                    "Call withApiKeyResolver() or withJwtProvider()."
            );
        }

        if (strictTenantResolution) {
            throw new IllegalStateException(
                "Security filter must be configured for non-local profiles. " +
                    "Call withApiKeyResolver() or withJwtProvider()."
            );
        }

        logger.warn("Running without authentication — LOCAL profile only.");
    }

    /**
     * DC-P1-005: Insecure mode loopback enforcement.
     *
     * <p>When running without authentication (local/insecure profile), the server must
     * only bind to the loopback interface (127.0.0.1) to prevent accidental network
     * exposure. If an explicit non-loopback listen host has been set in insecure mode,
     * startup is aborted.
     *
     * @param authConfigured         {@code true} if API-key or JWT auth is configured
     * @param strictTenantResolution {@code true} if running in a non-local profile
     * @param listenHost             the configured listen host (may be {@code null})
     * @param logger                 logger used for warnings
     * @throws IllegalStateException if a non-loopback host is used without auth
     */
    static void enforceLoopbackInInsecureMode(boolean authConfigured,
                                              boolean strictTenantResolution,
                                              String listenHost,
                                              Logger logger) {
        requireNonNull(logger, "logger");
        if (authConfigured || strictTenantResolution) {
            return;
        }
        boolean explicitNonLoopback = listenHost != null
                && !listenHost.isBlank()
                && !listenHost.equals("127.0.0.1")
                && !listenHost.equals("localhost");
        if (explicitNonLoopback) {
            throw new IllegalStateException(
                "Insecure mode (no auth) must not bind to non-loopback address '" + listenHost
                    + "'. Configure authentication or use listenHost=127.0.0.1.");
        }
    }

    /**
     * P0.5: Production fail-closed validation.
     * Blocks startup when critical dependencies (audit, policy, tenant resolver) are unavailable in production profiles.
     */
    static void validateProductionDependencies(boolean strictTenantResolution,
                                                String deploymentMode,
                                                boolean authConfigured,
                                                boolean auditAvailable,
                                                boolean policyAvailable,
                                                boolean idempotencyStoreAvailable,
                                                boolean eventStoreAvailable,
                                                boolean entityStoreDurable,
                                                boolean metricsConfigured,
                                                boolean traceExportAvailable,
                                                boolean tenantResolverAvailable,
                                                Logger logger) {
        validateProductionDependencies(
            strictTenantResolution,
            deploymentMode,
            authConfigured,
            auditAvailable,
            policyAvailable,
            idempotencyStoreAvailable,
            eventStoreAvailable,
            entityStoreDurable,
            eventStoreAvailable,
            metricsConfigured,
            traceExportAvailable,
            tenantResolverAvailable,
            logger);
    }

    static void validateProductionDependencies(boolean strictTenantResolution,
                                                String deploymentMode,
                                                boolean authConfigured,
                                                boolean auditAvailable,
                                                boolean policyAvailable,
                                                boolean idempotencyStoreAvailable,
                                                boolean eventStoreAvailable,
                                                boolean entityStoreDurable,
                                                boolean coreEventStoreDurable,
                                                boolean metricsConfigured,
                                                boolean traceExportAvailable,
                                                boolean tenantResolverAvailable,
                                                Logger logger) {
        requireNonNull(logger, "logger");
        boolean isProduction = isProductionMode(deploymentMode);
        if (!isProduction && !strictTenantResolution) {
            return;
        }
        if (isProduction && !authConfigured) {
            throw new IllegalStateException(
                "P1.18: Authentication is required for production profiles. "
                    + "Call withApiKeyResolver() and/or withJwtProvider() before start()."
            );
        }
        if (!auditAvailable) {
            throw new IllegalStateException(
                "P0.5: Audit service is required for production or strict-tenant profiles. " +
                "Call withAuditService() before start()."
            );
        }
        if (!policyAvailable) {
            throw new IllegalStateException(
                "P0.5: Policy engine is required for production or strict-tenant profiles. " +
                "Call withPolicyEngine() before start()."
            );
        }
        if (isProduction && !idempotencyStoreAvailable) {
            throw new IllegalStateException(
                "P0.5: Durable entity idempotency store is required for production profiles. "
                    + "Call withIdempotencyStore() before start()."
            );
        }
        if (isProduction && !eventStoreAvailable) {
            throw new IllegalStateException(
                "P1.18: Durable event log store is required for production profiles. "
                    + "Call withEventLogStore() before start()."
            );
        }
        if (isProduction && !entityStoreDurable) {
            throw new IllegalStateException(
                "P1.18: Durable entity store backing is required for production profiles. "
                    + "Configure DataCloudClient with a non in-memory EntityStore provider."
            );
        }
        if (isProduction && !coreEventStoreDurable) {
            throw new IllegalStateException(
                "P1.18: Durable core event store backing is required for production profiles. "
                    + "Configure DataCloudClient with a non in-memory EventLogStore provider."
            );
        }
        if (isProduction && !metricsConfigured) {
            throw new IllegalStateException(
                "P1.18: Metrics collector must be explicitly configured for production profiles. "
                    + "Call withMetricsCollector() before start()."
            );
        }
        if (isProduction && !traceExportAvailable) {
            throw new IllegalStateException(
                "P1.18: Trace export service is required for production profiles. "
                    + "Call withTraceExportService() before start()."
            );
        }
        if (!tenantResolverAvailable && strictTenantResolution) {
            throw new IllegalStateException(
                "P0.5: Tenant resolver is required when strict tenant resolution is enabled."
            );
        }
        logger.info("[DC-P0.5][DC-P1.18] Production dependencies validated: auth={}, audit={}, policy={}, idempotency={}, eventStore={}, entityStoreDurable={}, coreEventStoreDurable={}, metrics={}, traceExport={}, tenantResolver={}",
            authConfigured,
            auditAvailable,
            policyAvailable,
            idempotencyStoreAvailable,
            eventStoreAvailable,
            entityStoreDurable,
            coreEventStoreDurable,
            metricsConfigured,
            traceExportAvailable,
            tenantResolverAvailable);
    }

    static boolean isDurableStoreBacking(Object store) {
        if (store == null) {
            return false;
        }
        String className = store.getClass().getName().toLowerCase(Locale.ROOT);
        return !className.contains("inmemory");
    }

    static void validateCriticalRuntimeDependencies(String deploymentMode,
                                                    boolean genericIdempotencyStoreAvailable,
                                                    boolean transactionManagerAvailable,
                                                    boolean completionServiceAvailable,
                                                    Logger logger) {
        requireNonNull(logger, "logger");
        if (!isProductionMode(deploymentMode)) {
            return;
        }
        if (!genericIdempotencyStoreAvailable) {
            throw new IllegalStateException(
                "P0.5: Durable generic idempotency store is required for production profiles. "
                    + "Call withGenericIdempotencyStore() before start()."
            );
        }
        if (!transactionManagerAvailable) {
            throw new IllegalStateException(
                "P0.5: Transaction manager is required for production profiles. "
                    + "Call withTransactionManager() before start()."
            );
        }
        if (!completionServiceAvailable) {
            throw new IllegalStateException(
                "P0.5: AI completion service is required for production profiles. "
                    + "Call withCompletionService() before start()."
            );
        }
    }

    static void validateContextStoreConfiguration(String deploymentMode, ContextStore configuredStore) {
        if (isProductionMode(deploymentMode) && configuredStore instanceof InMemoryContextStore) {
            throw new IllegalStateException(
                "Context Plane requires a durable ContextStore in deployment mode '" + deploymentMode + "'");
        }
    }

    /**
     * P0.1: Production/strict profiles must not run settings APIs on in-memory storage.
     */
    static void validateSettingsStorageConfiguration(boolean strictTenantResolution,
                                                     String deploymentMode,
                                                     SettingsStore configuredStore,
                                                     Logger logger) {
        requireNonNull(logger, "logger");
        boolean isProduction = isProductionMode(deploymentMode);
        if (!isProduction && !strictTenantResolution) {
            return;
        }

        if (configuredStore == null) {
            throw new IllegalStateException(
                "P0.1: Persistent settings storage is required for production or strict-tenant profiles. "
                    + "Call withSettingsStore() with a durable implementation (for example JdbcSettingsStore)."
            );
        }

        String mode = configuredStore.getStorageMode();
        if (mode == null || mode.isBlank() || "in-memory".equalsIgnoreCase(mode.trim())) {
            throw new IllegalStateException(
                "P0.1: In-memory settings storage is not allowed for production or strict-tenant profiles. "
                    + "Configure a durable SettingsStore implementation."
            );
        }

        logger.info("[DC-P0.1] Settings storage validated for deployment mode {} using {}", deploymentMode, mode);
    }

    static boolean isProductionMode(String deploymentMode) {
        if (deploymentMode == null) return false;
        String lower = deploymentMode.toLowerCase();
        return lower.contains("prod") || lower.contains("staging") || lower.contains("sovereign");
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
        boolean authConfigured = apiKeyResolver != null || jwtProvider != null;
        boolean tenantResolverAvailable = authConfigured;
        boolean entityStoreDurable = isDurableStoreBacking(client.entityStore());
        boolean coreEventStoreDurable = isDurableStoreBacking(client.eventLogStore());

        validateSecurityConfiguration(apiKeyResolver != null || jwtProvider != null, strictTenantResolution, deploymentMode, log);
        enforceLoopbackInInsecureMode(authConfigured, strictTenantResolution, listenHost, log);
        validateSettingsStorageConfiguration(strictTenantResolution, deploymentMode, settingsStore, log);
        validateProductionDependencies(strictTenantResolution, deploymentMode,
            authConfigured,
            auditService != null,
            policyEngine != null,
            entityWriteIdempotencyStore != null,
            eventLogStore != null,
            entityStoreDurable,
            coreEventStoreDurable,
            metricsCollectorConfigured,
            traceExportService != null,
            tenantResolverAvailable,
            log);
        validateCriticalRuntimeDependencies(
            deploymentMode,
            genericIdempotencyStore != null,
            transactionManager != null,
            completionService != null,
            log);
        corsAllowOrigin = resolveCorsAllowOrigin(System.getenv("DATACLOUD_CORS_ALLOWED_ORIGINS"), strictTenantResolution, log);

        platformRateLimiter = new RateLimitFilter(
                rateLimitRequests,
                rateLimitWindowSeconds,
                DataCloudHttpServer::rateLimitClientKey);

        eventloop = Eventloop.create();

        // ---- Instantiate extracted handler delegates ----
        httpSupport = new HttpHandlerSupport(objectMapper, corsAllowOrigin, CORS_ALLOW_METHODS, CORS_ALLOW_HEADERS, strictTenantResolution, deploymentMode);
        DataCloudBusinessMetrics businessMetrics = new DataCloudBusinessMetrics(metricsCollector);
        TraceSpanSupport traceSpanSupport = new TraceSpanSupport(traceExportService);

        sseHandler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, httpSupport);
        if (openSearchConnector != null) sseHandler.withOpenSearchConnector(openSearchConnector);
        if (tenantQuotaService != null) sseHandler.withTenantQuotaService(tenantQuotaService);

        // Configure embedding mode (DETERMINISTIC_HASH or REAL_EMBEDDING)
        SemanticSearchHandler.EmbeddingMode embeddingMode = SemanticSearchHandler.EmbeddingMode.valueOf(
            config.getString("EMBEDDING_MODE").orElse("DETERMINISTIC_HASH")
        );
        String aiInferenceServiceUrl = config.getString("AI_INFERENCE_SERVICE_URL")
            .orElse("http://localhost:8083");
        String internalApiKey = config.getString("INTERNAL_API_KEY").orElse("");

        VectorMemoryPlugin vectorMemoryPlugin = VectorMemoryPlugin.builder()
            .dimension(128)
            .embeddingModel(embeddingMode.name())
            .embeddingFunction(SemanticSearchHandler::embedText)
            .build();
        vectorMemoryPlugin.initialize(Map.of());

        semanticSearchHandler = new SemanticSearchHandler(
            vectorMemoryPlugin, client, httpSupport, objectMapper, 
            embeddingMode, aiInferenceServiceUrl, internalApiKey
        );
        dataProductHandler = new DataProductHandler(client, httpSupport, objectMapper, lineagePlugin);

        entityHandler = new EntityCrudHandler(client, httpSupport, sseHandler.broadcastFunction());
        if (entityWriteIdempotencyStore != null) entityHandler.withIdempotencyStore(entityWriteIdempotencyStore);
        if (schemaValidator != null) entityHandler.withSchemaValidator(schemaValidator);
        if (openSearchConnector != null) entityHandler.withOpenSearchConnector(openSearchConnector);
        if (tenantQuotaService != null) entityHandler.withTenantQuotaService(tenantQuotaService);
        if (transactionManager != null) entityHandler.withTransactionManager(transactionManager);
        
        EntityWriteOutboxProcessor configuredOutboxProcessor = entityWriteOutboxProcessor;
        if (configuredOutboxProcessor == null) {
            if (isProductionMode(deploymentMode)) {
                throw new IllegalStateException(
                    "Durable entity write outbox processor is required for deployment profile '" + deploymentMode + "'");
            }
            configuredOutboxProcessor = new InMemoryEntityWriteOutboxProcessor();
            log.info("[DC-P1-02] Configured local entity write outbox processor for deployment profile '{}'", deploymentMode);
        }
        entityHandler.withOutboxProcessor(configuredOutboxProcessor);
        
        entityHandler.withTraceSupport(traceSpanSupport);
        entityHandler.withSemanticSearchPorts(semanticSearchHandler::indexEntity, semanticSearchHandler::deleteEntity);
        // DC-P1-05: Set deployment profile for production validation
        entityHandler.withDeploymentProfile(deploymentMode);
        // DC-P1-003: Propagate config-driven critical collections (if set); otherwise defaults apply in handler
        if (criticalCollections != null) {
            entityHandler.withCriticalCollections(criticalCollections);
        }
        // DC-P1-04: Wire audit service for transaction/outbox lifecycle
        if (auditService != null) {
            entityHandler.withAuditService(auditService);
        }

        exportHandler     = new EntityExportHandler(exportService, httpSupport);
        anomalyHandler    = new EntityAnomalyHandler(anomalyDetector, httpSupport, eventLogStore, objectMapper);
        validationHandler = new EntityValidationHandler(schemaValidator, httpSupport);

        // DC-P1-10: Configure event handler
        eventHandler = new EventHandler(client, httpSupport);
        eventHandler.withTraceSupport(traceSpanSupport);
        if (tenantQuotaService != null) eventHandler.withTenantQuotaService(tenantQuotaService);
        if (genericIdempotencyStore != null) eventHandler.withIdempotencyStore(genericIdempotencyStore);
        eventHandler.withDeploymentProfile(deploymentMode);

        pipelineCheckpointHandler = new PipelineCheckpointHandler(client, httpSupport, auditService);
        if (genericIdempotencyStore != null) pipelineCheckpointHandler.withIdempotencyStore(genericIdempotencyStore);
        workflowExecutionHandler = new WorkflowExecutionHandler(client, httpSupport);
        // DC-OPS-002: Emit dc.http.requests, dc.http.request.latency, and dc.http.errors for pipeline/workflow routes.
        workflowExecutionHandler.withMetrics(new DataCloudHttpMetrics(metricsCollector));
        alertingHandler = new AlertingHandler(client, httpSupport).withAutonomyController(autonomyController);
        if (genericIdempotencyStore != null) alertingHandler.withIdempotencyStore(genericIdempotencyStore);
        if (runtimePluginManager == null) {
            runtimePluginManager = new DataCloudRuntimePluginManager();
        }
        try {
            runtimePluginManager.registerBuiltInPlugins();
            log.info("[DC-AUD-005] Built-in OOB plugins registered: entity-storage, event-log, semantic-search, lineage, notifications, brain, learning, autonomy");
            runtimePluginManager.findCapability(WorkflowExecutionCapability.class)
                .ifPresent(workflowExecutionHandler::withExecutionCapability);
        } catch (Exception e) {
            log.error("[DC-AUD-005] Failed to register OOB plugins", e);
        }
        if (workflowExecutionCapabilityOverride != null) {
            workflowExecutionHandler.withExecutionCapability(workflowExecutionCapabilityOverride);
        }
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

        aiModelHandler = new AiModelHandler(aiModelManager, featureStoreService, httpSupport);
        aiModelHandler.withMetrics(new DataCloudHttpMetrics(metricsCollector));

        // DC-OPS-001: Register trace export health supplier before HealthHandler snapshot is taken
        // HealthHandler uses Map.copyOf(), so all suppliers must be registered before construction.
        healthSubsystemSuppliers.putIfAbsent("trace_export", () -> {
            if (traceExportService != null) {
                return Map.of("status", "UP", "exporter", "clickhouse");
            }
            return Map.of("status", "NOT_CONFIGURED",
                          "detail", "CLICKHOUSE_HOST not set — spans are not exported");
        });

        healthHandler = new HealthHandler(httpSupport, healthSubsystemSuppliers, metricsCollector);

        // DC-E3: AI assist handler — nullable completionService enables graceful degradation
        aiAssistHandler = new AiAssistHandler(
            completionService,
            objectMapper,
            httpSupport,
            blockingExecutor,
            new AiRecommendationMetrics(metricsCollector));
        if (tenantQuotaService != null) aiAssistHandler.withTenantQuotaService(tenantQuotaService);
        aiAssistHandler.withClient(client);
        // DC-P0-007: Disable heuristic fallback when running in production/staging/sovereign profile
        aiAssistHandler.withProductionMode(isProductionMode(deploymentMode));

        // DC-E4: Voice gateway handler — wire Whisper STT adapter if DC_STT_URL is configured
        WhisperSttConfig sttConfig = WhisperSttConfig.fromEnv();
        VoiceSttPort sttPort = sttConfig.enabled()
            ? new HttpWhisperSttAdapter(sttConfig, objectMapper, blockingExecutor)
            : NopVoiceSttAdapter.INSTANCE;
        VoiceTtsConfig ttsConfig = VoiceTtsConfig.fromEnv();

        if (completionService != null || sttConfig.enabled() || ttsConfig.enabled()) {
            healthSubsystemSuppliers.putIfAbsent("voice_gateway", () -> {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", "UP");
                snapshot.put("stt", sttConfig.enabled() ? "UP" : "NOT_CONFIGURED");
                snapshot.put("tts", ttsConfig.enabled() ? "UP" : "NOT_CONFIGURED");
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
        // P2-001: Add health indicator for embedding mode
        healthSubsystemSuppliers.putIfAbsent("semantic_search", () -> Map.of(
            "status", "UP",
            "embeddingMode", semanticSearchHandler.getEmbeddingMode().name(),
            "aiInferenceServiceUrl", aiInferenceServiceUrl
        ));

        voiceHandler = new VoiceGatewayHandler(
            client,
            completionService,
            auditService,
            objectMapper,
            httpSupport,
            blockingExecutor,
            sttPort,
            ttsConfig.enabled()
                ? new HttpSpeechTtsAdapter(ttsConfig, objectMapper, blockingExecutor)
                : NopVoiceTtsAdapter.INSTANCE);

        // DC-E5: Data lifecycle and governance handler
        dataLifecycleHandler = new DataLifecycleHandler(client, objectMapper, httpSupport, auditService);
        dataLifecycleHandler.withTraceSupport(traceSpanSupport);
        if (genericIdempotencyStore != null) dataLifecycleHandler.withIdempotencyStore(genericIdempotencyStore);
        if (transactionManager != null) dataLifecycleHandler.withTransactionManager(transactionManager);
        dataLifecycleHandler.withDeploymentProfile(deploymentMode);

        // B9: Autonomy management handler — nullable controller enables graceful 503
        autonomyHandler = new AutonomyHandler(autonomyController, httpSupport);

        // B3: Agent catalog runtime handler — loads YAML definitions from classpath
        agentCatalogHandler = new AgentCatalogHandler(httpSupport, metricsCollector);

        // P0-10: SurfaceRegistryHandler now uses typed SurfaceRecord contract only
        surfaceRegistryHandler = new SurfaceRegistryHandler(
            httpSupport,
            objectMapper,
            this::buildTypedSurfaceSnapshot);

        validateContextStoreConfiguration(deploymentMode, contextStore);
        contextLayerHandler = new ContextLayerHandler(httpSupport, objectMapper, knowledgeGraphPlugin, contextStore);
        collectionContextHandler = new CollectionContextHandler(
            client,
            httpSupport,
            objectMapper,
            lineagePlugin,
            knowledgeGraphPlugin);
        mcpToolsHandler = new McpToolsHandler(
            client,
            httpSupport,
            objectMapper,
            collectionContextHandler::getCollectionContextDocument);

        // P3.9.1: Entity lineage tracking and visualization
        lineageHandler = new LineageHandler(httpSupport, objectMapper, lineagePlugin);


        pluginInstallHandler = new PluginInstallHandler(
                httpSupport,
                com.ghatana.datacloud.spi.StoragePluginRegistry.getInstance(),
                runtimePluginManager,
                metricsCollector)
                .withPluginUpgradeEnabled(pluginUpgradeEnabled);

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

        // DC-S14: Admin settings handler — use injected persistent store or fall back to in-memory
        // P0-2/P0-6: Validate that production profiles do not use in-memory settings
        SettingsStore resolvedStore = settingsStore != null ? settingsStore : new InMemorySettingsStore();
        if ("in-memory".equals(resolvedStore.getStorageMode()) && strictTenantResolution) {
            throw new IllegalStateException(
                "PRODUCTION VALIDATION ERROR: Settings persistence is required in non-embedded profiles. " +
                "In-memory settings store cannot be used in production environments. " +
                "Configure DATACLOUD_DB_* environment variables to enable JDBC-backed settings storage. " +
                "Current storage mode: " + resolvedStore.getStorageMode());
        }
        settingsHandler = new SettingsHandler(httpSupport, resolvedStore);
        log.info("[SETTINGS] Settings handler configured with storage mode: {}", resolvedStore.getStorageMode());

        ProductReleaseReadinessService productReleaseReadinessService = new ProductReleaseReadinessService(
            new DataCloudProductReleaseReadinessRepository(client),
            metricsCollector
        );
        productReleaseReadinessHandler = new ProductReleaseReadinessHandler(httpSupport, productReleaseReadinessService);

        userActivityHandler = new UserActivityHandler(httpSupport);

        // P1.1: Data source connector registry handler — persists connection metadata in dc_connections
        DataSourceRegistryHandler dataSourceRegistryHandler = new DataSourceRegistryHandler(
            client, httpSupport, null /* no DataFabricConnector implementation yet */, auditService);

        // P3.6: Compliance handler for legal holds and evidence packages
        ComplianceHandler complianceHandler = new ComplianceHandler(client, httpSupport, objectMapper);

        // P3.3: Sovereign profile handler for air-gapped, model routing, and policy enforcement
        SovereignProfileHandler sovereignProfileHandler = new SovereignProfileHandler(client, httpSupport, objectMapper);

        // P3.1: Provider conformance suite handler for EntityStore and EventLogStore validation
        ProviderConformanceHandler conformanceHandler = new ProviderConformanceHandler(httpSupport, client);

        log.info("[DC-SURFACE] Runtime surface summary {}", buildSurfaceSummaryLog());

        MasteryController masteryController = buildMasteryController();
        MediaArtifactRepository mediaArtifactRepository = new DataCloudMediaArtifactRepository();
        MediaArtifactController mediaArtifactController = new MediaArtifactController(mediaArtifactRepository, objectMapper);

        RoutingServlet router = new DataCloudRouterBuilder(eventloop)
            .withHealthRoutes(healthHandler)
            .withEntityRoutes(entityHandler, sseHandler, semanticSearchHandler, exportHandler, anomalyHandler, validationHandler)
            .withSseRoutes(sseHandler)
            .withEventRoutes(eventHandler)
            .withPipelineRoutes(pipelineCheckpointHandler, workflowExecutionHandler)
            .withCheckpointRoutes(pipelineCheckpointHandler)
            .withAlertRoutes(alertingHandler, sseHandler)
            .withMemoryRoutes(memoryHandler)
            .withMediaArtifactRoutes(mediaArtifactController)
            .withBrainRoutes(brainHandler, sseHandler)
            .withLearningRoutes(learningHandler)
            .withMasteryRoutes(masteryController)
            .withAnalyticsRoutes(analyticsHandler, workflowExecutionHandler)
            .withReportingRoutes(analyticsHandler, workflowExecutionHandler)
            .withExecutionRoutes(workflowExecutionHandler)
            .withModelRoutes(aiModelHandler)
            .withFeatureRoutes(aiModelHandler)
            .withWebSocketRoutes(sseHandler)
            .withAiAssistRoutes(aiAssistHandler)
            .withVoiceRoutes(voiceHandler)
            .withGovernanceRoutes(dataLifecycleHandler)
            .withSurfaceRoutes(surfaceRegistryHandler)
            .withLineageRoutes(lineageHandler)
            .withContextRoutes(contextLayerHandler, collectionContextHandler, semanticSearchHandler)
            .withMcpRoutes(mcpToolsHandler)
            .withDataProductRoutes(dataProductHandler)
            .withAutonomyRoutes(autonomyHandler)
            .withAgentCatalogRoutes(agentCatalogHandler)
            .withPluginRoutes(pluginInstallHandler)
            .withStorageCostRoutes(storageCostHandler, httpSupport)
            .withFederatedQueryRoutes(federatedQueryHandler, httpSupport)
            .withTierMigrationRoutes(tierMigrationHandler, httpSupport)
            .withConnectorRoutes(dataSourceRegistryHandler, httpSupport)
            // TODO: Re-enable when storageProfileHandler is available as class field
            // .withStorageProfileRoutes(storageProfileHandler, httpSupport)
            .withSettingsRoutes(settingsHandler)
            .withProductReleaseReadinessRoutes(productReleaseReadinessHandler)
            .withComplianceRoutes(complianceHandler)
            .withSovereignProfileRoutes(sovereignProfileHandler)
            .withConformanceRoutes(conformanceHandler)
            .withUserActivityRoutes(userActivityHandler)
            .build();

        // DC-P1-10: Unified production validation using RuntimeProfileValidator
        // All subsystems register their requirements, and the validator collects all violations
        RuntimeProfileValidator runtimeProfileValidator = RuntimeProfileValidator.builder()
            .deploymentProfile(deploymentMode)
            .strictTenantResolution(strictTenantResolution)
            .authConfigured(apiKeyResolver != null || jwtProvider != null)
            .auditConfigured(auditService != null)
            .policyEngineConfigured(policyEngine != null)
            .durableEntityStore(isDurableStoreBacking(client.entityStore()))
            .durableEventStore(eventLogStore != null)
            .durableIdempotencyStore(entityWriteIdempotencyStore != null)
            .transactionManagerConfigured(transactionManager != null)
            .metricsConfigured(metricsCollector != null)
            .traceExportConfigured(traceExportService != null)
            .completionServiceConfigured(completionService != null)
            .build();
        
        // Validate all production requirements in one centralized check
        runtimeProfileValidator.validate();
        
        // Subsystem-specific validation for handlers that have additional requirements
        entityHandler.validateProductionRequirements();
        eventHandler.validateProductionRequirements();
        dataLifecycleHandler.validateProductionRequirements();

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
                .strictTenantResolution(strictTenantResolution)
                .deploymentProfile(deploymentMode) // DC-P0-04: Pass deployment mode into filter
                .build();
            rootServlet = securityFilter.apply(filteredRouter);
            log.info("[DC-E1] security filter active (apiKey: {}, jwt: {}, policy engine: {}, deployment profile: {})",
                apiKeyResolver != null ? "enabled" : "disabled",
                jwtProvider != null ? "enabled" : "disabled",
                policyEngine != null ? "enabled" : "fail-closed",
                deploymentMode);
        } else {
            rootServlet = filteredRouter;
            log.info("[DC-E1] security filter inactive — withApiKeyResolver/withJwtProvider not called");
        }

        rootServlet = new RequestObservationFilter(httpSupport, businessMetrics, traceExportService, traceSamplingRate).apply(rootServlet);

        HttpServer.Builder serverBuilder = HttpServer.builder(eventloop,
            corsFilter(rateLimitFilter(rootServlet)));
        if (listenHost != null && !listenHost.isBlank()) {
            serverBuilder.withListenAddress(new InetSocketAddress(listenHost.trim(), port));
        } else {
            serverBuilder.withListenPort(port);
        }
        server = serverBuilder.build();

        CountDownLatch startupLatch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Exception> startupFailure = new java.util.concurrent.atomic.AtomicReference<>();

        blockingExecutor.execute(() -> {
            try {
                server.listen();
                log.info("Data-Cloud HTTP Server started on port {}", port);
                startupLatch.countDown();
                // Start background anomaly detection scanning (P3.6.1) if detector + event store are both available
                if (anomalyDetector != null && eventLogStore != null) {
                    anomalyDetectionTask = new AnomalyDetectionTask(
                        anomalyDetector,
                        (tenantId, collection, anomalies) -> anomalyHandler.persistAnomalies(tenantId, collection, anomalies),
                        eventloop);
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
                startupFailure.set(e);
                startupLatch.countDown();
                log.error("Failed to start HTTP server", e);
            }
        });

        if (!startupLatch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for Data-Cloud HTTP server to start on port " + port);
        }
        if (startupFailure.get() != null) {
            throw startupFailure.get();
        }
    }

    private MasteryController buildMasteryController() {
        DataCloudEntityRepositoryAdapter entityRepository = new DataCloudEntityRepositoryAdapter(client.entityStore());
        MasteryTransitionPolicy transitionPolicy = new DefaultMasteryTransitionPolicy();
        MasteryTransitionRepository transitionRepository = new DataCloudMasteryTransitionRepository(entityRepository);
        MasteryEvidenceRepository evidenceRepository = new DataCloudMasteryEvidenceRepository(entityRepository);
        MasteryRegistry masteryRegistry = new DataCloudMasteryRegistry(
            entityRepository,
            transitionRepository,
            evidenceRepository,
            transitionPolicy);
        LearningDeltaRepository learningDeltaRepository = new DataCloudLearningDeltaRepository(entityRepository);
        PromotionEngine promotionEngine = new DefaultPromotionEngine(masteryRegistry, learningDeltaRepository);
        ObsolescenceDetector obsolescenceDetector = new DefaultObsolescenceDetector(masteryRegistry);
        return new MasteryController(
            masteryRegistry,
            ApprovalService.getInstance(),
            obsolescenceDetector,
            learningDeltaRepository,
            promotionEngine);
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

    private Map<String, Object> buildSurfaceSnapshot() {
        Map<String, Object> surfaces = new LinkedHashMap<>();
        // DC-AUD-024: Expose deployment mode for UI/consumer awareness
        boolean authConfigured = apiKeyResolver != null || jwtProvider != null;
        String settingsStorageMode = settingsStore != null ? settingsStore.getStorageMode() : "in-memory";
        boolean settingsDurable = settingsStorageMode != null
            && !settingsStorageMode.isBlank()
            && !"in-memory".equalsIgnoreCase(settingsStorageMode.trim());
        boolean entityStoreDurable = isDurableStoreBacking(client.entityStore());
        boolean coreEventStoreDurable = isDurableStoreBacking(client.eventLogStore());

        // DC-P0-001: Publish the canonical validator posture so /api/v1/surfaces is an authoritative truth.
        RuntimeProfileValidator validator = RuntimeProfileValidator.builder()
            .deploymentProfile(deploymentMode)
            .strictTenantResolution(strictTenantResolution)
            .authConfigured(authConfigured)
            .auditConfigured(auditService != null)
            .policyEngineConfigured(policyEngine != null)
            .durableEntityStore(entityStoreDurable)
            .durableEventStore(coreEventStoreDurable)
            .durableIdempotencyStore(genericIdempotencyStore != null)
            .transactionManagerConfigured(transactionManager != null)
            .metricsConfigured(metricsCollectorConfigured)
            .traceExportConfigured(traceExportService != null)
            .completionServiceConfigured(completionService != null)
            .build();

        Map<String, Object> runtimePosture = buildRuntimePostureSnapshot(
            validator,
            settingsStorageMode,
            settingsDurable,
            entityStoreDurable,
            coreEventStoreDurable,
            authConfigured);

        surfaces.put("_meta", Map.of(
            "deploymentMode", deploymentMode,
            "strictTenantResolution", strictTenantResolution,
            "generatedAt", Instant.now().toString(),
            "runtimePosture", runtimePosture
        ));
        boolean workflowExecutionAvailable = runtimePluginManager.findCapability(WorkflowExecutionCapability.class).isPresent();
        Map<String, Object> workflowExecution = capabilityEntry(workflowExecutionAvailable, null);
        workflowExecution.put("executionStore", workflowExecutionAvailable ? "datacloud" : "none");
        workflowExecution.put("lifecycleModel", workflowExecutionAvailable ? "durable-single-process" : "absent");
        workflowExecution.put("gated", true);
        surfaces.put("pipelines.metadata", capabilityEntry(true, null));
        surfaces.put("pipelines.execution", workflowExecution);
        surfaces.put("authentication.apiKey", capabilityEntry(apiKeyResolver != null, null));
        surfaces.put("authentication.jwt", capabilityEntry(jwtProvider != null, null));
        surfaces.put("brain", capabilityEntry(brain != null, null));
        surfaces.put("learning", capabilityEntry(learningBridge != null, null));
        surfaces.put("analytics", capabilityEntry(analyticsEngine != null, null));
        // DC-P1-001: Cancellation requires a distributed query tracker; unavailable in single-process deployment
        Map<String, Object> analyticsCancellation = capabilityEntry(false, null);
        analyticsCancellation.put("reason", "Query cancellation requires a distributed query tracker — not available in single-process deployment");
        surfaces.put("analytics.cancellation", analyticsCancellation);
        surfaces.put("reporting", capabilityEntry(reportService != null, null));
        // DC-P1-002: Data Fabric is a preview capability; disabled unless DATA_CLOUD_DATA_FABRIC=true AND connector is wired
        boolean dataFabricEnabled = DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_DATA_FABRIC);
        Map<String, Object> dataFabricCap = capabilityEntry(dataFabricEnabled, null);
        dataFabricCap.put("maturity", dataFabricEnabled ? "preview" : "unavailable");
        dataFabricCap.put("mode",     dataFabricEnabled ? "preview" : "unavailable");
        dataFabricCap.put("reason",   dataFabricEnabled
            ? "Data Fabric is enabled in preview mode — live connector implementation may not be present"
            : "Data Fabric requires DATA_CLOUD_DATA_FABRIC=true and a live DataFabricConnector implementation");
        surfaces.put("dataFabric", dataFabricCap);
        surfaces.put("search.openSearch", capabilityEntry(openSearchConnector != null, null));
        surfaces.put("entityExport", capabilityEntry(exportService != null, null));
        surfaces.put("anomalyDetection", capabilityEntry(anomalyDetector != null, null));
        surfaces.put("schemaValidation", capabilityEntry(schemaValidator != null, null));
        surfaces.put("ai.modelRegistry", capabilityEntry(aiModelManager != null, null));
        surfaces.put("ai.featureStore", capabilityEntry(featureStoreService != null, resolveSubsystemStatus("ai_inference")));
        surfaces.put("ai.assist", capabilityEntry(completionService != null, null));
        surfaces.put(
            "voiceGateway",
            capabilityEntry(completionService != null || WhisperSttConfig.fromEnv().enabled(), resolveSubsystemStatus("voice_gateway")));
        surfaces.put("governance.audit", capabilityEntry(auditService != null, resolveSubsystemStatus("audit_service")));
        surfaces.put("governance.policyEngine", capabilityEntry(policyEngine != null, resolveSubsystemStatus("policy_engine")));
        surfaces.put("federatedQuery.trino", capabilityEntry(trinoUrl != null && !trinoUrl.isBlank(), null));
        surfaces.put("autonomy", capabilityEntry(autonomyController != null, null));
        surfaces.put("storage.compaction", capabilityEntry(storageCompactionTask != null, resolveSubsystemStatus("storage_compaction")));
        surfaces.put("tierMigration.warm", capabilityEntry(warmMigrationScheduler != null, null));
        surfaces.put("tierMigration.cold", capabilityEntry(coldMigrationScheduler != null, null));
        // DC-P1-012: Optional 501/503 services are represented in Runtime Truth with capabilityEntry()
        // - search.openSearch, entityExport, anomalyDetection, reporting, ai.modelRegistry, ai.featureStore
        // - federatedQuery.trino, tierMigration.warm, tierMigration.cold
        // These are marked available/unavailable based on handler/service presence, enabling UI to gate actions
        // P1.4: Document tier routing policy in capability registry
        List<Map<String, Object>> tiers = new ArrayList<>();
        Map<String, Object> hotTier = new java.util.LinkedHashMap<>();
        hotTier.put("name", "hot");
        hotTier.put("description", "Recent, frequently accessed data — in-memory / high-performance storage");
        hotTier.put("defaultRetentionDays", 7);
        hotTier.put("autoMigrateAfterDays", 7);
        tiers.add(hotTier);
        Map<String, Object> warmTier = new java.util.LinkedHashMap<>();
        warmTier.put("name", "warm");
        warmTier.put("description", "Older, less frequently accessed data — columnar / Iceberg catalog");
        warmTier.put("defaultRetentionDays", 90);
        warmTier.put("autoMigrateAfterDays", 30);
        tiers.add(warmTier);
        Map<String, Object> coldTier = new LinkedHashMap<>();
        coldTier.put("name", "cold");
        coldTier.put("description", "Archived data — compressed, object storage with point-in-time recovery");
        coldTier.put("defaultRetentionDays", 2555);
        coldTier.put("autoMigrateAfterDays", 90);
        tiers.add(coldTier);
        Map<String, Object> defaultRules = new LinkedHashMap<>();
        defaultRules.put("hotToWarm", "lastAccess > 7d OR recordAge > 7d");
        defaultRules.put("warmToCold", "lastAccess > 30d OR recordAge > 90d");
        defaultRules.put("coldToWarm", "explicitRestoreRequest OR scheduledQuery");
        defaultRules.put("costModel", "hot = 10x base, warm = 2x base, cold = 0.5x base");
        Map<String, Object> tierRoutingPolicy = new LinkedHashMap<>();
        tierRoutingPolicy.put("tiers", tiers);
        tierRoutingPolicy.put("defaultRules", defaultRules);
        tierRoutingPolicy.put("schedulerAvailable", warmMigrationScheduler != null || coldMigrationScheduler != null);
        surfaces.put("tierRoutingPolicy", tierRoutingPolicy);
        surfaces.put("health.database", capabilityEntry(healthSubsystemSuppliers.containsKey("database"), resolveSubsystemStatus("database")));
        surfaces.put("health.aiInference", capabilityEntry(healthSubsystemSuppliers.containsKey("ai_inference"), resolveSubsystemStatus("ai_inference")));
        surfaces.put("health.eventStore", capabilityEntry(healthSubsystemSuppliers.containsKey("event_store"), resolveSubsystemStatus("event_store")));
        surfaces.put("health.storageCompaction", capabilityEntry(healthSubsystemSuppliers.containsKey("storage_compaction"), resolveSubsystemStatus("storage_compaction")));

        // DC-AUD-017: Runtime capability registry as universal truth — align with registered routes
        surfaces.put("settings", capabilityEntry(settingsHandler != null, null));
        surfaces.put("events.streaming", capabilityEntry(sseHandler != null, null));
        surfaces.put("events.webSocket", capabilityEntry(sseHandler != null, null));
        surfaces.put("dataProducts", capabilityEntry(dataProductHandler != null, null));
        surfaces.put("contextLayer", capabilityEntry(contextLayerHandler != null, null));
        surfaces.put("collectionContext", capabilityEntry(collectionContextHandler != null, null));
        surfaces.put("mcpTools", capabilityEntry(mcpToolsHandler != null, null));
        surfaces.put("lineage", capabilityEntry(lineageHandler != null, null));
        surfaces.put("semanticSearch", capabilityEntry(semanticSearchHandler != null, null));
        surfaces.put("ai.operations", capabilityEntry(aiAssistHandler != null, null));
        surfaces.put("plugins", capabilityEntry(runtimePluginManager != null, null));
        surfaces.put("agentCatalog", capabilityEntry(agentCatalogHandler != null, null));
        // DC-P1-006: Alerting surface capability — required for UI to gate alert creation/viewing
        surfaces.put("alerts", capabilityEntry(alertingHandler != null, null));

        // P0.1: Wire SDK feature flags into capability registry so clients know
        // which optional capabilities are enabled for this deployment.
        Map<String, Object> featureFlags = new LinkedHashMap<>();
        for (DataCloudFeature feature : DataCloudFeature.values()) {
            boolean enabled = DataCloudFeatureFlags.isEnabled(feature);
            featureFlags.put(feature.name(), Map.of(
                "enabled", enabled,
                "default", feature.defaultEnabled(),
                "source", enabled == feature.defaultEnabled() ? "default" : "override"
            ));
        }
        surfaces.put("featureFlags", featureFlags);

        return surfaces;
    }

    private java.util.List<SurfaceRecord> buildTypedSurfaceSnapshot() {
        boolean authConfigured = apiKeyResolver != null || jwtProvider != null;
        String settingsStorageMode = settingsStore != null ? settingsStore.getStorageMode() : "in-memory";
        boolean settingsDurable = settingsStorageMode != null
            && !settingsStorageMode.isBlank()
            && !"in-memory".equalsIgnoreCase(settingsStorageMode.trim());
        boolean entityStoreDurable = isDurableStoreBacking(client.entityStore());
        boolean coreEventStoreDurable = isDurableStoreBacking(client.eventLogStore());

        RuntimeProfileValidator validator = RuntimeProfileValidator.builder()
            .deploymentProfile(deploymentMode)
            .strictTenantResolution(strictTenantResolution)
            .authConfigured(authConfigured)
            .auditConfigured(auditService != null)
            .policyEngineConfigured(policyEngine != null)
            .durableEntityStore(entityStoreDurable)
            .durableEventStore(coreEventStoreDurable)
            .durableIdempotencyStore(genericIdempotencyStore != null)
            .transactionManagerConfigured(transactionManager != null)
            .metricsConfigured(metricsCollectorConfigured)
            .traceExportConfigured(traceExportService != null)
            .completionServiceConfigured(completionService != null)
            .build();

        Map<String, Object> runtimePostureDetails = buildRuntimePostureSnapshot(
            validator,
            settingsStorageMode,
            settingsDurable,
            entityStoreDurable,
            coreEventStoreDurable,
            authConfigured);
        RuntimePosture runtimePosture = RuntimePosture.builder()
            .authEnabled(authConfigured)
            .durabilityEnabled(settingsDurable && entityStoreDurable && coreEventStoreDurable && !(contextStore instanceof InMemoryContextStore))
            .auditEnabled(auditService != null)
            .policyEnabled(policyEngine != null)
            .metricsEnabled(metricsCollectorConfigured)
            .tracingEnabled(traceExportService != null)
            .eventStoreEnabled(coreEventStoreDurable)
            .idempotencyEnabled(entityWriteIdempotencyStore != null)
            .details(runtimePostureDetails)
            .build();

        java.util.List<SurfaceRecord> records = new java.util.ArrayList<>();

        records.add(SurfaceRecord.builder("authentication.apiKey")
            .ownerPlane("governance")
            .state(apiKeyResolver != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_API_KEYS"))
            .probe(apiKeyResolver != null
                ? DependencyProbeResult.pass("api-key-resolver", "API key resolver is configured")
                : DependencyProbeResult.fail("api-key-resolver", "DATACLOUD_API_KEYS not set"))
            .tenantScope("global")
            .runtimeProfile(deploymentMode)
            .actionsAllowed(List.of("authenticate"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("authentication.jwt")
            .ownerPlane("governance")
            .state(jwtProvider != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_JWT_SECRET"))
            .probe(jwtProvider != null
                ? DependencyProbeResult.pass("jwt-provider", "JWT provider is configured")
                : DependencyProbeResult.fail("jwt-provider", "DATACLOUD_JWT_SECRET not set"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .actionsAllowed(List.of("authenticate", "exchange-token"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("governance.audit")
            .ownerPlane("governance")
            .state(auditService != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_AUDIT_ENABLED", "event-log-store"))
            .probe(auditService != null
                ? DependencyProbeResult.pass("audit-service", "Audit service is active")
                : DependencyProbeResult.fail("audit-service", "DATACLOUD_AUDIT_ENABLED=false or event store absent"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .actionsAllowed(List.of("emit-audit-event", "query-audit-log"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("governance.policyEngine")
            .ownerPlane("governance")
            .state(policyEngine != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_POLICY_ENGINE_URL"))
            .probe(policyEngine != null
                ? DependencyProbeResult.pass("policy-engine", "Policy engine is configured")
                : DependencyProbeResult.fail("policy-engine", "DATACLOUD_POLICY_ENGINE_URL not set"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .actionsAllowed(List.of("evaluate-policy", "enforce-policy"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("data.entityStore")
            .ownerPlane("data")
            .state(entityStoreDurable ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DEGRADED)
            .requiredDependencies(List.of("DATACLOUD_DB_ENABLED", "DATACLOUD_DB_URL"))
            .probe(entityStoreDurable
                ? DependencyProbeResult.pass("entity-store", "Durable JDBC-backed entity store")
                : DependencyProbeResult.degraded("entity-store", "In-memory entity store — data lost on restart"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(entityStoreDurable ? null : "Data is non-durable — in-memory only, lost on process restart")
            .actionsAllowed(List.of("read-entity", "write-entity", "delete-entity", "query"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("event.store")
            .ownerPlane("event")
            .state(coreEventStoreDurable ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DEGRADED)
            .requiredDependencies(List.of("DATACLOUD_KAFKA_ENABLED", "DATACLOUD_KAFKA_BOOTSTRAP"))
            .probe(coreEventStoreDurable
                ? DependencyProbeResult.pass("event-log-store", "Durable event log store")
                : DependencyProbeResult.degraded("event-log-store", "In-memory event log — events lost on restart"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(coreEventStoreDurable ? null : "Events are non-durable — lost on process restart")
            .actionsAllowed(List.of("append-event", "tail-events", "replay-events"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("operations.idempotency")
            .ownerPlane("operations")
            .state(genericIdempotencyStore != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DEGRADED)
            .requiredDependencies(List.of("DATACLOUD_DB_ENABLED"))
            .probe(genericIdempotencyStore != null
                ? DependencyProbeResult.pass("generic-idempotency-store", "Durable write idempotency store")
                : DependencyProbeResult.degraded("generic-idempotency-store", "In-memory idempotency — keys lost on restart"))
            .tenantScope("global")
            .runtimeProfile(deploymentMode)
            .limitations(genericIdempotencyStore != null ? null : "Idempotency keys are in-memory only — retries may cause duplicate writes after restart")
            .actionsAllowed(List.of("idempotent-write"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("operations.transactionManager")
            .ownerPlane("operations")
            .state(transactionManager != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_DB_ENABLED"))
            .probe(transactionManager != null
                ? DependencyProbeResult.pass("transaction-manager", "Transaction manager active for atomic multi-step writes")
                : DependencyProbeResult.fail("transaction-manager", "No transaction manager — multi-step writes are NOT atomic"))
            .tenantScope("global")
            .runtimeProfile(deploymentMode)
            .limitations(transactionManager != null ? null : "Multi-step writes (entity + event + audit) are not atomic")
            .actionsAllowed(List.of("atomic-write"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("observability.metrics")
            .ownerPlane("operations")
            .state(metricsCollectorConfigured ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("DATACLOUD_METRICS_ENABLED"))
            .probe(metricsCollectorConfigured
                ? DependencyProbeResult.pass("metrics-collector", "Prometheus metrics collector active")
                : DependencyProbeResult.fail("metrics-collector", "DATACLOUD_METRICS_ENABLED=false — metrics are noop"))
            .tenantScope("global")
            .runtimeProfile(deploymentMode)
            .actionsAllowed(List.of("emit-metric", "scrape-prometheus"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("observability.tracing")
            .ownerPlane("operations")
            .state(traceExportService != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("CLICKHOUSE_HOST"))
            .probe(traceExportService != null
                ? DependencyProbeResult.pass("trace-export-service", "Spans exported to ClickHouse")
                : DependencyProbeResult.fail("trace-export-service", "CLICKHOUSE_HOST not set — spans are discarded"))
            .tenantScope("global")
            .runtimeProfile(deploymentMode)
            .limitations(traceExportService == null ? "Spans are generated but not persisted — no distributed tracing" : null)
            .actionsAllowed(List.of("export-span", "query-trace"))
            .runtimePosture(runtimePosture)
            .build());

        records.add(SurfaceRecord.builder("intelligence.aiCompletion")
            .ownerPlane("intelligence")
            .state(completionService != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("AI_PROVIDER or OPENAI_API_KEY or OLLAMA_HOST"))
            .probe(completionService != null
                ? DependencyProbeResult.pass("completion-service", "LLM completion service is wired")
                : DependencyProbeResult.fail("completion-service", "No AI provider configured — AI routes return 503"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(completionService == null ? "AI assist, voice gateway, and heuristic AI routes return 503" : null)
            .actionsAllowed(List.of("ai-assist", "recommend", "explain", "voice-intent"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add media artifacts surface
        // TODO: Re-enable when mediaArtifactRepository is available as class field
        // records.add(SurfaceRecord.builder("media.artifacts")
        //     .ownerPlane("data")
        //     .state(mediaArtifactRepository != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
        //     .requiredDependencies(List.of("mediaArtifactRepository", "entityStore"))
        //     .probe(mediaArtifactRepository != null
        //         ? DependencyProbeResult.pass("media-artifact-repository", "Media artifact repository is configured")
        //         : DependencyProbeResult.fail("media-artifact-repository", "Media artifact repository not configured"))
        //     .tenantScope("tenant")
        //     .runtimeProfile(deploymentMode)
        //     .limitations(mediaArtifactRepository == null ? "Media artifact CRUD unavailable" : null)
        //     .actionsAllowed(List.of("create-artifact", "list-artifacts", "get-artifact", "delete-artifact", "transcribe", "analyze"))
        //     .runtimePosture(runtimePosture)
        //     .build());

        // I1: Add audio-video STT surface
        // TODO: Re-enable when sttPort is available as class field
        // records.add(SurfaceRecord.builder("audioVideo.stt")
        //     .ownerPlane("intelligence")
        //     .state(sttPort != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
        //     .requiredDependencies(List.of("sttPort", "sttService"))
        //     .probe(sttPort != null
        //         ? DependencyProbeResult.pass("stt-port", "Speech-to-text service is configured")
        //         : DependencyProbeResult.fail("stt-port", "Speech-to-text service not configured"))
        //     .tenantScope("tenant")
        //     .runtimeProfile(deploymentMode)
        //     .limitations(sttPort == null ? "Speech-to-text unavailable" : null)
        //     .actionsAllowed(List.of("transcribe-audio", "batch-transcribe"))
        //     .runtimePosture(runtimePosture)
        //     .build());

        // I1: Add audio-video TTS surface
        // TODO: Re-enable when ttsConfig is available as class field
        // records.add(SurfaceRecord.builder("audioVideo.tts")
        //     .ownerPlane("intelligence")
        //     .state(ttsConfig.enabled() ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
        //     .requiredDependencies(List.of("ttsConfig", "ttsService"))
        //     .probe(ttsConfig.enabled()
        //         ? DependencyProbeResult.pass("tts-config", "Text-to-speech service is configured")
        //         : DependencyProbeResult.fail("tts-config", "Text-to-speech service not configured"))
        //     .tenantScope("tenant")
        //     .runtimeProfile(deploymentMode)
        //     .limitations(!ttsConfig.enabled() ? "Text-to-speech unavailable" : null)
        //     .actionsAllowed(List.of("synthesize-speech", "batch-synthesize"))
        //     .runtimePosture(runtimePosture)
        //     .build());

        // I1: Add audio-video Vision surface
        records.add(SurfaceRecord.builder("audioVideo.vision")
            .ownerPlane("intelligence")
            .state(RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("visionService"))
            .probe(DependencyProbeResult.fail("vision-service", "Vision service not yet implemented"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations("Vision analysis not yet available")
            .actionsAllowed(List.of("analyze-image", "detect-objects", "extract-text"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add audio-video Multimodal surface
        records.add(SurfaceRecord.builder("audioVideo.multimodal")
            .ownerPlane("intelligence")
            .state(RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("multimodalService"))
            .probe(DependencyProbeResult.fail("multimodal-service", "Multimodal service not yet implemented"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations("Multimodal analysis not yet available")
            .actionsAllowed(List.of("multimodal-analyze", "cross-modal-reasoning"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add connectors surface
        // TODO: Re-enable when dataSourceRegistryHandler is available as class field
        // records.add(SurfaceRecord.builder("data.connectors")
        //     .ownerPlane("data")
        //     .state(dataSourceRegistryHandler != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
        //     .requiredDependencies(List.of("dataSourceRegistryHandler", "entityStore"))
        //     .probe(dataSourceRegistryHandler != null
        //         ? DependencyProbeResult.pass("data-source-registry", "Data source registry handler is configured")
        //         : DependencyProbeResult.fail("data-source-registry", "Data source registry handler not configured"))
        //     .tenantScope("tenant")
        //     .runtimeProfile(deploymentMode)
        //     .limitations(dataSourceRegistryHandler == null ? "Connector CRUD unavailable" : null)
        //     .actionsAllowed(List.of("register-connector", "list-connectors", "test-connector", "enable-connector", "disable-connector", "sync-connector"))
        //     .runtimePosture(runtimePosture)
        //     .build());

        // I1: Add storage profiles surface
        records.add(SurfaceRecord.builder("data.storageProfiles")
            .ownerPlane("data")
            .state(RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("storageProfileHandler", "entityStore"))
            .probe(DependencyProbeResult.fail("storage-profile-handler", "Storage profile handler not yet implemented"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations("Storage profile CRUD not yet available")
            .actionsAllowed(List.of("create-profile", "list-profiles", "get-profile", "update-profile", "delete-profile", "set-default"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add agent runtime surface
        records.add(SurfaceRecord.builder("action.agentRuntime")
            .ownerPlane("action")
            .state(agentCatalogHandler != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("agentCatalogHandler", "entityStore", "eventStore"))
            .probe(agentCatalogHandler != null
                ? DependencyProbeResult.pass("agent-catalog", "Agent catalog handler is configured")
                : DependencyProbeResult.fail("agent-catalog", "Agent catalog handler not configured"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(agentCatalogHandler == null ? "Agent runtime unavailable" : null)
            .actionsAllowed(List.of("list-agents", "get-agent", "run-agent", "list-runs", "get-run"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add action execution surface
        boolean workflowExecutionAvailable = runtimePluginManager != null
            && runtimePluginManager.findCapability(WorkflowExecutionCapability.class).isPresent();
        records.add(SurfaceRecord.builder("action.execution")
            .ownerPlane("action")
            .state(workflowExecutionAvailable ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("workflowExecutionCapability", "entityStore", "eventStore"))
            .probe(workflowExecutionAvailable
                ? DependencyProbeResult.pass("workflow-execution", "Workflow execution capability is configured")
                : DependencyProbeResult.fail("workflow-execution", "Workflow execution capability not configured"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(workflowExecutionAvailable ? null : "Action execution unavailable")
            .actionsAllowed(List.of("execute-workflow", "list-executions", "get-execution", "cancel-execution"))
            .runtimePosture(runtimePosture)
            .build());

        // I1: Add AI assist surface (renamed from intelligence.aiCompletion for clarity)
        records.add(SurfaceRecord.builder("intelligence.aiAssist")
            .ownerPlane("intelligence")
            .state(completionService != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("AI_PROVIDER or OPENAI_API_KEY or OLLAMA_HOST"))
            .probe(completionService != null
                ? DependencyProbeResult.pass("completion-service", "LLM completion service is wired")
                : DependencyProbeResult.fail("completion-service", "No AI provider configured — AI routes return 503"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(completionService == null ? "AI assist routes return 503" : null)
            .actionsAllowed(List.of("ai-assist", "recommend", "explain", "voice-intent"))
            .runtimePosture(runtimePosture)
            .build());

        // E4: Add media/audio-video surface (legacy, kept for compatibility)
        records.add(SurfaceRecord.builder("media.audioVideo")
            .ownerPlane("intelligence")
            .state(voiceHandler != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("voiceHandler", "sttService", "ttsService"))
            .probe(voiceHandler != null
                ? DependencyProbeResult.pass("voice-gateway", "Voice gateway is configured")
                : DependencyProbeResult.fail("voice-gateway", "Voice gateway not configured"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(voiceHandler == null ? "Audio/video processing unavailable" : null)
            .actionsAllowed(List.of("speech-to-text", "text-to-speech", "voice-intent"))
            .runtimePosture(runtimePosture)
            .build());

        // E4: Add Context Plane surface truth
        records.add(SurfaceRecord.builder("context.plane")
            .ownerPlane("context")
            .state(contextLayerHandler != null ? RuntimeTruthStatus.LIVE : RuntimeTruthStatus.DISABLED)
            .requiredDependencies(List.of("contextStore", "knowledgeGraphPlugin"))
            .probe(contextLayerHandler != null
                ? DependencyProbeResult.pass("context-layer", "Context layer is configured")
                : DependencyProbeResult.fail("context-layer", "Context layer not configured"))
            .tenantScope("tenant")
            .runtimeProfile(deploymentMode)
            .limitations(contextLayerHandler == null ? "Context plane unavailable" : null)
            .actionsAllowed(List.of("query-context", "update-context", "context-search"))
            .runtimePosture(runtimePosture)
            .build());

        return java.util.Collections.unmodifiableList(records);
    }

    private Map<String, Object> buildRuntimePostureSnapshot(
            RuntimeProfileValidator validator,
            String settingsStorageMode,
            boolean settingsDurable,
            boolean entityStoreDurable,
            boolean coreEventStoreDurable,
            boolean authConfigured) {
        Map<String, Object> runtimePosture = new LinkedHashMap<>(validator.toPostureSnapshot());
        runtimePosture.put("authenticationConfigured", authConfigured);
        runtimePosture.put("transactionManager", transactionManager != null);
        runtimePosture.put("settingsStorageMode", settingsStorageMode);
        runtimePosture.put("settingsDurable", settingsDurable);
        runtimePosture.put("entityStoreDurable", entityStoreDurable);
        runtimePosture.put("coreEventStoreDurable", coreEventStoreDurable);
        runtimePosture.put("eventStoreWired", eventLogStore != null);
        runtimePosture.put("eventTail", buildEventTailPosture(client.eventLogStore()));
        runtimePosture.put("idempotencyStoreDurable", entityWriteIdempotencyStore != null);
        runtimePosture.put("contextStoreMode", contextStore.getClass().getSimpleName());
        runtimePosture.put("contextStoreDurable", !(contextStore instanceof InMemoryContextStore));
        runtimePosture.put("transactionOrchestrationMode", transactionManager != null ? "transactional" : "non-transactional");
        runtimePosture.put("entityWriteOutboxMode", entityWriteOutboxProcessor != null
            ? entityWriteOutboxProcessor.getClass().getSimpleName()
            : "in-memory-fallback");
        return runtimePosture;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEventTailPosture(Object coreEventStore) {
        if (coreEventStore == null) {
            return Map.of(
                "available", false,
                "configurable", false,
                "reason", "Core event store is not wired");
        }

        try {
            var snapshotMethod = coreEventStore.getClass().getMethod("tailRuntimeSnapshot");
            Object snapshot = snapshotMethod.invoke(coreEventStore);
            if (snapshot instanceof Map<?, ?> mapSnapshot) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("available", true);
                normalized.put("configurable", true);
                ((Map<Object, Object>) mapSnapshot).forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return normalized;
            }
        } catch (ReflectiveOperationException ignored) {
            // Store does not expose tail runtime metrics snapshot.
        }

        return Map.of(
            "available", true,
            "configurable", false,
            "storeType", coreEventStore.getClass().getSimpleName());
    }

    private String buildSurfaceSummaryLog() {
        return buildSurfaceSnapshot().entrySet().stream()
            .filter(entry -> !"_meta".equals(entry.getKey()))
            .map(entry -> entry.getKey() + "=" + ((Map<?, ?>) entry.getValue()).get("status"))
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    /**
     * Builds a capability entry exposing both legacy and descriptive readiness fields.
     *
     * <p>DC-BE-001: Uses centralized RuntimeTruthStatus enum to prevent status value drift.
     * The {@code status} field preserves legacy contract values used by existing
     * UI and tests ({@code ACTIVE}/{@code DEGRADED}/{@code NOT_CONFIGURED}).
     * The {@code mode} field exposes descriptive labels ({@code live}/{@code degraded}/{@code preview}/{@code unavailable}).
     */
    private Map<String, Object> capabilityEntry(boolean configured, String subsystemStatus) {
        return capabilityEntry(configured, subsystemStatus, null);
    }

    private Map<String, Object> capabilityEntry(boolean configured, String subsystemStatus, String docsUrl) {
        Map<String, Object> entry = new LinkedHashMap<>();
        RuntimeTruthStatus status = RuntimeTruthStatus.fromRuntimeState(configured, subsystemStatus);
        // P0.1: Runtime capability truth — unified schema
        entry.put("status", status.toLegacyValue()); // legacy contract value
        entry.put("mode", status.toJsonValue()); // canonical taxonomy value
        entry.put("maturity", status.toJsonValue()); // backward compatibility
        entry.put("configured", configured);
        entry.put("dependency", subsystemStatus != null ? "healthCheck" : "runtime");
        entry.put("probe", subsystemStatus != null ? "healthCheck" : "runtime");
        entry.put("lastCheckedAt", Instant.now().toString());
        entry.put("lastProbe", Instant.now().toString()); // backward compatibility
        entry.put("source", subsystemStatus != null ? "healthCheck" : "runtime"); // backward compatibility
        if (docsUrl != null && !docsUrl.isBlank()) {
            entry.put("docsLink", docsUrl);
            entry.put("documentationLink", docsUrl); // backward compatibility
        }
        if (subsystemStatus != null) {
            entry.put("dependencyStatus", subsystemStatus); // backward compatibility
            if ("DEGRADED".equals(subsystemStatus)) {
                String degradedReason = "Dependency health check returned DEGRADED — some features may be limited.";
                entry.put("degradedReason", degradedReason);
            } else if ("DOWN".equals(subsystemStatus)) {
                String degradedReason = "Dependency health check returned DOWN — feature is currently unavailable.";
                entry.put("degradedReason", degradedReason);
            }
        }
        return entry;
    }

    /**
     * DC-BE-001: Returns legacy contract status value using centralized RuntimeTruthStatus enum.
     */
    private String legacyCapabilityStatus(boolean configured, String subsystemStatus) {
        RuntimeTruthStatus status = RuntimeTruthStatus.fromRuntimeState(configured, subsystemStatus);
        return status.toLegacyValue();
    }

    /**
     * DC-BE-001: Maps subsystem health status to canonical Runtime Truth status using centralized enum.
     * Replaces generic ACTIVE/DEGRADED/NOT_CONFIGURED with consumer-friendly live/degraded/preview/unavailable labels.
     */
    private String subsystemStatusToMaturityStatus(boolean configured, String subsystemStatus) {
        RuntimeTruthStatus status = RuntimeTruthStatus.fromRuntimeState(configured, subsystemStatus);
        return status.toJsonValue();
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
     * builders are responsible for adding CORS headers via the JSON response helpers.
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
                // When credentials are enabled, use the specific origin from request instead of "*"
                String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
                String corsOrigin = corsAllowOrigin;
                if ("*".equals(corsAllowOrigin) && requestOrigin != null && !requestOrigin.isEmpty()) {
                    corsOrigin = requestOrigin;
                }
                
                return Promise.of(HttpResponse.ok200()
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),  HttpHeaderValue.of(corsOrigin))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), HttpHeaderValue.of(CORS_ALLOW_METHODS))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), HttpHeaderValue.of(CORS_ALLOW_HEADERS))
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of("true"))
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
                    .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                            HttpHeaderValue.of("true"))
                    .withBody(body.getBytes(StandardCharsets.UTF_8))
                    .build();
        });
    }

    private static String rateLimitClientKey(HttpRequest request) {
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String clientIp = remoteIp(request);
        if (tenantId == null) {
            return clientIp;
        }
        return tenantId + "|" + clientIp;
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
