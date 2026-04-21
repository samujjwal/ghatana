package com.ghatana.aep.server.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.aep.consent.ConsentService;
import com.ghatana.aep.consent.DefaultConsentService;
import com.ghatana.aep.server.compliance.AepComplianceService;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.query.AepQueryService;
import com.ghatana.aep.server.report.AepReportingService;
import com.ghatana.aep.server.store.DataCloudPatternStore;
import com.ghatana.aep.server.store.DataCloudPipelineStore;
import com.ghatana.aep.di.AepRuntimeProfile;
import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.security.AepSecurityFilter;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.security.PIIScanner;
import com.ghatana.aep.security.SessionFilter;
import com.ghatana.aep.server.http.controllers.AgentController;
import com.ghatana.aep.server.http.controllers.AgentMarketplaceController;
import com.ghatana.aep.server.http.controllers.AnalyticsController;
import com.ghatana.aep.server.http.controllers.CapabilitiesController;
import com.ghatana.aep.server.http.controllers.ComplianceController;
import com.ghatana.aep.server.http.controllers.CostController;
import com.ghatana.aep.server.http.controllers.DeploymentController;
import com.ghatana.aep.server.http.controllers.GovernanceController;
import com.ghatana.aep.server.http.controllers.LifecycleController;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import com.ghatana.aep.server.http.controllers.HealthController;
import com.ghatana.aep.server.http.controllers.HitlController;
import com.ghatana.aep.learning.EpisodeLearningPipeline;
import com.ghatana.aep.server.http.controllers.LearningController;
import com.ghatana.platform.incident.InMemoryGracefulDegradationManager;
import com.ghatana.platform.incident.InMemoryKillSwitchService;
import com.ghatana.platform.pac.InMemoryPolicyEngine;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import com.ghatana.platform.security.analytics.DefaultEgressMonitor;
import com.ghatana.platform.security.analytics.RegexPromptInjectionDetector;
import com.ghatana.aep.server.http.controllers.PatternController;
import com.ghatana.aep.server.http.controllers.PipelineController;
import com.ghatana.aep.server.learning.LearningScheduler;
import com.ghatana.aep.server.http.controllers.AiSuggestionsController;
import com.ghatana.aep.server.http.controllers.NlpController;
import com.ghatana.aep.server.http.controllers.SseController;
import com.ghatana.agent.learning.evaluation.CompositeEvaluationGate;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapter;
import com.ghatana.orchestrator.deployment.service.DeploymentOrchestrator;
import com.ghatana.orchestrator.deployment.service.EventCloudDeploymentEventPublisher;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.InMemoryPipelineRepository;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.pipeline.registry.service.CapabilitiesService;
import com.ghatana.pipeline.registry.validation.PipelineValidator;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.aep.eventcloud.store.EventCloudRunLedger;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.observability.RunLedgerService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.MDC;

/**
 * HTTP Server for AEP Standalone deployment.
 * Provides REST API endpoints for event processing, pattern management,
 * anomaly detection, and forecasting.
 *
 * @since 1.0.0
 */
public class AepHttpServer {

    private static final Logger log = LoggerFactory.getLogger(AepHttpServer.class);
    private static final String KAFKA_BOOTSTRAP_SERVERS_SETTING = "KAFKA_BOOTSTRAP_SERVERS";
    private static final double HEAP_WARNING_RATIO = 0.85d;
    private static final double HEAP_CRITICAL_RATIO = 0.95d;
    private static final int CONNECTIVITY_PROBE_TIMEOUT_MILLIS = 750;

    private final AepEngine engine;
    private final int port;
    private final ObjectMapper objectMapper;
    private final DeploymentHttpAdapter deploymentAdapter;
    private final PipelineRepository pipelineRepository;
    private final PipelineValidator pipelineValidator;
    private final CapabilitiesService capabilitiesService;
    /** Optional: wired in when AepLearningModule is active. Null-safe throughout. */
    @Nullable
    private final HumanReviewQueue humanReviewQueue;
    /**
     * Optional Data-Cloud client for agent registry queries (AEP-P5).
     * {@code null} if Data-Cloud is not configured; endpoint falls back to an empty response.
     */
    @Nullable
    private final DataCloudClient agentDataCloud;
    private HttpServer server;
    private Eventloop eventloop;
    private Thread serverThread;

    // Controllers (new architecture - Week 3 decomposition)
    private final HealthController healthController;
    private final AgentController agentController;
    private final AgentMarketplaceController marketplaceController;
    private final PatternController patternController;
    private final AnalyticsController analyticsController;
    private final CostController costController;
    private final DeploymentController deploymentController;
    private final HitlController hitlController;
    private final LearningController learningController;
    private final ComplianceController complianceController;
    private final SseController sseController;
    private final CapabilitiesController capabilitiesController;

    /** Governance endpoints controller. */
    private final GovernanceController governanceController;
    /** Lifecycle (change approval + recertification) endpoints controller. */
    private final LifecycleController lifecycleController;
    /** AI suggestions controller — surfaces anomaly-scored suggestions to the UI. */
    private final AiSuggestionsController aiSuggestionsController;
    /** NLQ controller — parses natural-language queries into structured intent + entities. */
    private final NlpController nlpController;
    /**
     * Optional periodic learning scheduler; non-null only when a DataCloud-backed
     * {@link EpisodeLearningPipeline} is available.
     */
    @Nullable
    private final LearningScheduler learningScheduler;

    /** Compliance services — non-null when agentDataCloud is configured. */
    @Nullable
    private final AepComplianceService complianceService;
    /** Consent service for event processing validation. */
    private final ConsentService consentService;
    /** P3-18: PII scanner for detecting sensitive data in events. */
    private final PIIScanner piiScanner;
    @Nullable
    private final DataCloudPatternStore patternStore;
    @Nullable
    private final DataCloudAnalyticsStore analyticsStore;
    @Nullable
    private final AepQueryService queryService;
    @Nullable
    private final AepReportingService reportingService;
    private final AepSoc2ControlFramework soc2Framework = new AepSoc2ControlFramework();

    /** Phase-6: SLO metrics recorder (intake latency, run rates, etc.). */
    private final AepSloMetrics sloMetrics;
    /** Phase-6: Durable run-ledger service for distributed trace correlation. */
    private final RunLedgerService runLedgerService;
    @Nullable
    private final EventCloudRunLedger runLedger;
    private final MeterRegistry integrationMeterRegistry;
    /**
     * Prometheus meter registry used to serve the {@code /metrics} scrape endpoint.
     * May be {@code null} for test/embedded deployments; in that case /metrics returns stub JSON.
     */
    @Nullable
    private final PrometheusMeterRegistry prometheusRegistry;
    @Nullable
    private final DataSource dataSource;
    @Nullable
    private final JedisPool jedisPool;
    private final MetricsCollector metricsCollector;

    /**
     * Whether pipelines are backed by Data-Cloud durable storage.
     * {@code false} means the in-memory repository is used (standalone / no-DC mode).
     */
    private final boolean durablePipelines;

    /** In-memory circular buffer of recent pipeline runs (event-loop thread only). */
    private final java.util.Deque<Map<String, Object>> recentRuns = new java.util.ArrayDeque<>();
    private static final int MAX_RECENT_RUNS = 1_000;
    private static final String PIPELINE_UPDATE_CONFLICTS = "aep.pipeline.update.conflicts";
    private static final String PIPELINE_VERSION_CONFLICT_CODE = "PIPELINE_VERSION_CONFLICT";
    private static final String PIPELINE_VERSION_REQUIRED_CODE = "PIPELINE_VERSION_REQUIRED";

    /** P0-4: In-memory set of processed idempotency keys for deduplication. */
    private final java.util.Set<String> processedIdempotencyKeys = new java.util.HashSet<>();

    private static final String ALLOW_IN_MEMORY_RUN_HISTORY_ENV = "AEP_ALLOW_IN_MEMORY_RUN_HISTORY";

    /**
     * Active SSE subscriber queues keyed by tenantId (event-loop thread only).
     * Managed by {@link SseController}.
     */
    @SuppressWarnings("unused")
    private final Map<String, List<Object>> sseSubscribers = new java.util.HashMap<>();

    /**
     * Creates a new AEP HTTP server.
     *
     * @param engine the AEP engine instance
     * @param port the port to listen on
     */
    public AepHttpServer(AepEngine engine, int port) {
        this(engine, port, null, null, MetricsCollectorFactory.createNoop());
    }

    // P2-12: Builder pattern to replace 9 constructor overloads
    /**
     * Builder for {@link AepHttpServer}.
     * Provides a fluent API for constructing AepHttpServer instances with various optional dependencies.
     */
    public static final class Builder {
        private AepEngine engine;
        private int port;
        private DataCloudClient agentDataCloud;
        private HumanReviewQueue humanReviewQueue;
        private MetricsCollector metricsCollector;
        private KillSwitchService killSwitchService;
        private GracefulDegradationManager degradationManager;
        private PolicyAsCodeEngine policyEngine;
        private EgressMonitor egressMonitor;
        private PromptInjectionDetector injectionDetector;
        private ChangeApprovalWorkflow changeApprovalWorkflow;
        private RecertificationPipeline recertificationPipeline;
        private PrometheusMeterRegistry prometheusRegistry;
        private DataSource dataSource;
        private JedisPool jedisPool;

        private Builder() {}

        /**
         * Sets the required AEP engine.
         */
        public Builder engine(AepEngine engine) {
            this.engine = engine;
            return this;
        }

        /**
         * Sets the required port.
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the optional DataCloud client for agent registry.
         */
        public Builder agentDataCloud(@Nullable DataCloudClient agentDataCloud) {
            this.agentDataCloud = agentDataCloud;
            return this;
        }

        /**
         * Sets the optional human review queue for HITL workflows.
         */
        public Builder humanReviewQueue(@Nullable HumanReviewQueue humanReviewQueue) {
            this.humanReviewQueue = humanReviewQueue;
            return this;
        }

        /**
         * Sets the metrics collector (defaults to noop if not set).
         */
        public Builder metricsCollector(@Nullable MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            return this;
        }

        /**
         * Sets the optional kill switch service.
         */
        public Builder killSwitchService(@Nullable KillSwitchService killSwitchService) {
            this.killSwitchService = killSwitchService;
            return this;
        }

        /**
         * Sets the optional graceful degradation manager.
         */
        public Builder degradationManager(@Nullable GracefulDegradationManager degradationManager) {
            this.degradationManager = degradationManager;
            return this;
        }

        /**
         * Sets the optional policy-as-code engine.
         */
        public Builder policyEngine(@Nullable PolicyAsCodeEngine policyEngine) {
            this.policyEngine = policyEngine;
            return this;
        }

        /**
         * Sets the optional egress monitor.
         */
        public Builder egressMonitor(@Nullable EgressMonitor egressMonitor) {
            this.egressMonitor = egressMonitor;
            return this;
        }

        /**
         * Sets the optional prompt injection detector.
         */
        public Builder injectionDetector(@Nullable PromptInjectionDetector injectionDetector) {
            this.injectionDetector = injectionDetector;
            return this;
        }

        /**
         * Sets the optional change approval workflow.
         */
        public Builder changeApprovalWorkflow(@Nullable ChangeApprovalWorkflow changeApprovalWorkflow) {
            this.changeApprovalWorkflow = changeApprovalWorkflow;
            return this;
        }

        /**
         * Sets the optional recertification pipeline.
         */
        public Builder recertificationPipeline(@Nullable RecertificationPipeline recertificationPipeline) {
            this.recertificationPipeline = recertificationPipeline;
            return this;
        }

        /**
         * Sets the optional Prometheus meter registry.
         */
        public Builder prometheusRegistry(@Nullable PrometheusMeterRegistry prometheusRegistry) {
            this.prometheusRegistry = prometheusRegistry;
            return this;
        }

        /**
         * Sets the optional data source for persistence.
         */
        public Builder dataSource(@Nullable DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /**
         * Sets the optional Jedis pool for Redis.
         */
        public Builder jedisPool(@Nullable JedisPool jedisPool) {
            this.jedisPool = jedisPool;
            return this;
        }

        /**
         * Builds the AepHttpServer instance.
         *
         * @throws IllegalArgumentException if required fields (engine, port) are not set
         */
        public AepHttpServer build() {
            if (engine == null) {
                throw new IllegalArgumentException("engine must be set");
            }
            if (port <= 0) {
                throw new IllegalArgumentException("port must be positive");
            }
            MetricsCollector actualMetricsCollector = metricsCollector != null
                ? metricsCollector
                : MetricsCollectorFactory.createNoop();

            return new AepHttpServer(
                engine, port, agentDataCloud, humanReviewQueue, actualMetricsCollector,
                killSwitchService, degradationManager, policyEngine,
                egressMonitor, injectionDetector, changeApprovalWorkflow,
                recertificationPipeline, prometheusRegistry, dataSource, jedisPool);
        }
    }

    /**
     * Creates a new Builder for AepHttpServer.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new AEP HTTP server with Data-Cloud agent registry.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param agentDataCloud   Data-Cloud client for agent registry queries (AEP-P5);
     *                         may be {@code null} if Data-Cloud is not configured
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud) {
        this(engine, port, agentDataCloud, null, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with a HITL review queue (no DataCloud).
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param humanReviewQueue human review queue for HITL workflows; may be {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable HumanReviewQueue humanReviewQueue) {
        this(engine, port, null, humanReviewQueue, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with a HITL review queue and a Data-Cloud client.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param humanReviewQueue human review queue for HITL workflows; may be {@code null}
     * @param agentDataCloud   Data-Cloud client for agent registry queries; may be {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         @Nullable DataCloudClient agentDataCloud) {
        this(engine, port, agentDataCloud, humanReviewQueue, MetricsCollectorFactory.createNoop());
    }

    /**
     * Creates a new AEP HTTP server with observability but no HITL queue.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param agentDataCloud   Data-Cloud client for agent registry queries (AEP-P5);
     *                         may be {@code null} if Data-Cloud is not configured
     * @param metricsCollector metrics collector for observability; never {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud,
                         MetricsCollector metricsCollector) {
        this(engine, port, agentDataCloud, null, metricsCollector);
    }

    /**
     * Creates a new AEP HTTP server with full observability support.
     *
     * @param engine           the AEP engine instance
     * @param port             the port to listen on
     * @param agentDataCloud   Data-Cloud client for agent registry queries (AEP-P5);
     *                         may be {@code null} if Data-Cloud is not configured
     * @param humanReviewQueue human review queue for HITL workflows; may be {@code null}
     * @param metricsCollector metrics collector for observability; never {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         MetricsCollector metricsCollector) {
        this(engine, port, agentDataCloud, humanReviewQueue, metricsCollector,
            null, null, null, null, null, null, null);
    }

    /**
     * Full constructor that accepts injected governance services.
     *
     * @param killSwitchService      production kill-switch; if {@code null} falls back to in-memory
     * @param degradationManager     production degradation manager; if {@code null} falls back to in-memory
     * @param policyEngine           production policy engine; if {@code null} falls back to in-memory
     * @param egressMonitor          egress monitor; if {@code null} uses default
     * @param injectionDetector      prompt injection detector; if {@code null} uses regex default
     * @param changeApprovalWorkflow change approval workflow; if {@code null} falls back to in-memory
     * @param recertificationPipeline recertification pipeline; if {@code null} falls back to in-memory
     * @param prometheusRegistry     Prometheus registry for /metrics scrape; may be {@code null}
     */
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         MetricsCollector metricsCollector,
                         @Nullable KillSwitchService killSwitchService,
                         @Nullable GracefulDegradationManager degradationManager,
                         @Nullable PolicyAsCodeEngine policyEngine,
                         @Nullable EgressMonitor egressMonitor,
                         @Nullable PromptInjectionDetector injectionDetector,
                         @Nullable ChangeApprovalWorkflow changeApprovalWorkflow,
                         @Nullable RecertificationPipeline recertificationPipeline) {
        this(engine, port, agentDataCloud, humanReviewQueue, metricsCollector,
            killSwitchService, degradationManager, policyEngine,
            egressMonitor, injectionDetector, changeApprovalWorkflow,
            recertificationPipeline, null);
    }

    /**
     * Full constructor with Prometheus registry.
     */
    @SuppressWarnings("java:S107") // large constructor is intentional for full DI wiring
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         MetricsCollector metricsCollector,
                         @Nullable KillSwitchService killSwitchService,
                         @Nullable GracefulDegradationManager degradationManager,
                         @Nullable PolicyAsCodeEngine policyEngine,
                         @Nullable EgressMonitor egressMonitor,
                         @Nullable PromptInjectionDetector injectionDetector,
                         @Nullable ChangeApprovalWorkflow changeApprovalWorkflow,
                         @Nullable RecertificationPipeline recertificationPipeline,
                         @Nullable PrometheusMeterRegistry prometheusRegistry) {
        this(engine, port, agentDataCloud, humanReviewQueue, metricsCollector,
            killSwitchService, degradationManager, policyEngine,
            egressMonitor, injectionDetector, changeApprovalWorkflow,
            recertificationPipeline, prometheusRegistry, null, null);
    }

    /**
     * Full constructor with Prometheus registry and infrastructure probes.
     */
    @SuppressWarnings("java:S107") // large constructor is intentional for full DI wiring
    public AepHttpServer(AepEngine engine, int port,
                         @Nullable DataCloudClient agentDataCloud,
                         @Nullable HumanReviewQueue humanReviewQueue,
                         MetricsCollector metricsCollector,
                         @Nullable KillSwitchService killSwitchService,
                         @Nullable GracefulDegradationManager degradationManager,
                         @Nullable PolicyAsCodeEngine policyEngine,
                         @Nullable EgressMonitor egressMonitor,
                         @Nullable PromptInjectionDetector injectionDetector,
                         @Nullable ChangeApprovalWorkflow changeApprovalWorkflow,
                         @Nullable RecertificationPipeline recertificationPipeline,
                         @Nullable PrometheusMeterRegistry prometheusRegistry,
                         @Nullable DataSource dataSource,
                         @Nullable JedisPool jedisPool) {
        this.prometheusRegistry = prometheusRegistry;
        this.dataSource = dataSource;
        this.jedisPool = jedisPool;
        this.metricsCollector = metricsCollector != null
            ? metricsCollector
            : MetricsCollectorFactory.createNoop();
        this.engine = engine;
        this.port = port;
        this.agentDataCloud = agentDataCloud;
        this.humanReviewQueue = humanReviewQueue;
        this.complianceService = agentDataCloud != null
            ? new AepComplianceService(agentDataCloud, List.of(reportCleanupHook()))
            : null;
        this.consentService = new DefaultConsentService();
        // P3-18: Initialize PII scanner for event data validation
        this.piiScanner = new PIIScanner();
        this.integrationMeterRegistry = new SimpleMeterRegistry();
        this.patternStore = agentDataCloud != null ? new DataCloudPatternStore(agentDataCloud) : null;
        this.analyticsStore = agentDataCloud != null ? new DataCloudAnalyticsStore(agentDataCloud) : null;
        this.queryService = agentDataCloud != null ? new AepQueryService(agentDataCloud, integrationMeterRegistry) : null;
        this.reportingService = agentDataCloud != null ? new AepReportingService(agentDataCloud, integrationMeterRegistry) : null;
        this.sloMetrics = new AepSloMetrics(this.metricsCollector);
        EventCloudRunLedger runLedger = (agentDataCloud != null && agentDataCloud.eventLogStore() != null)
            ? new EventCloudRunLedger(agentDataCloud.eventLogStore())
            : null;
        this.runLedger = runLedger;
        this.runLedgerService = runLedger != null
            ? new RunLedgerService(runLedger)
            : new RunLedgerService();
        this.objectMapper = JsonUtils.getDefaultMapper();
        DeploymentOrchestrator orchestrator = new DeploymentOrchestrator(
            new EventCloudDeploymentEventPublisher(engine.eventCloud()),
            this.metricsCollector);
        this.deploymentAdapter = new DeploymentHttpAdapter(orchestrator);
        if (agentDataCloud != null) {
            this.pipelineRepository = new DataCloudPipelineStore(agentDataCloud);
            this.durablePipelines = true;
            log.info("[init] PipelineRepository backed by Data-Cloud (durable storage)");
        } else {
            this.pipelineRepository = new InMemoryPipelineRepository();
            this.durablePipelines = false;
            log.info("[init] PipelineRepository backed by in-memory store (set DC_SERVER_URL for durable pipelines)");
        }

        // P0 hardening: fail closed in explicit production profile when run history is non-durable.
        if (agentDataCloud == null || agentDataCloud.eventLogStore() == null) {
            if (isExplicitProductionProfile() && !isBooleanSettingEnabled(ALLOW_IN_MEMORY_RUN_HISTORY_ENV)) {
                throw new IllegalStateException(
                    "Data Cloud EventLogStore is required for durable run history in production. "
                        + "Set " + ALLOW_IN_MEMORY_RUN_HISTORY_ENV + "=true only for explicit embedded/test deployments "
                        + "where non-durable in-memory run history is acceptable.");
            }
            log.warn("[STARTUP WARNING] Run history is stored in-memory (max {} entries) and will be lost on restart. "
                + "Configure Data Cloud with EventLogStore for durable run history.", MAX_RECENT_RUNS);
        }
        this.pipelineValidator = new PipelineValidator();
        this.capabilitiesService = new CapabilitiesService();
        this.capabilitiesController = new CapabilitiesController(this.capabilitiesService, this::jsonResponse);

        // Initialize controllers (Week 3 decomposition)
        this.healthController = new HealthController("1.0.0-SNAPSHOT");
        this.healthController.addDependencyCheck("data-cloud",
            () -> this.agentDataCloud != null ? "ok" : "disabled");
        this.healthController.addDependencyCheck("review-queue",
            () -> this.humanReviewQueue != null ? "ok" : "disabled");
        this.healthController.addDependencyCheck("run-ledger",
            () -> this.agentDataCloud == null ? "disabled"
                : (this.agentDataCloud.eventLogStore() != null ? "ok" : "misconfigured"));
        this.healthController.addDependencyCheck("database",
            this::databaseHealthStatus);
        this.healthController.addDependencyCheck("redis",
            this::redisHealthStatus);
        this.healthController.addDependencyCheck("event-loop",
            this::eventLoopHealthStatus);
        this.healthController.addDependencyCheck("heap-memory",
            this::heapMemoryHealthStatus);
        this.healthController.addDependencyCheck("governance",
            () -> "ok");
        this.healthController.addDependencyCheck("lifecycle",
            () -> "ok");
        this.healthController.addDeepDependencyCheck("data-cloud.entity-store",
            () -> this.agentDataCloud == null ? "disabled"
                : (this.agentDataCloud.entityStore() != null ? "ok" : "misconfigured"));
        this.healthController.addDeepDependencyCheck("data-cloud.event-log",
            () -> this.agentDataCloud == null ? "disabled"
                : (this.agentDataCloud.eventLogStore() != null ? "ok" : "misconfigured"));
        this.healthController.addDeepDependencyCheck("pipeline-storage",
            () -> this.durablePipelines ? "ok" : "in-memory");
        this.healthController.addDeepDependencyCheck("memory-store",
            () -> this.agentDataCloud == null ? "disabled"
                : (this.agentDataCloud.entityStore() != null ? "ok" : "misconfigured"));
        this.healthController.addDeepDependencyCheck("execution-history",
            () -> this.agentDataCloud != null && this.agentDataCloud.eventLogStore() != null ? "ok" : "disabled");
        this.healthController.addAsyncDeepDependencyCheck("data-cloud.connectivity", this::dataCloudConnectivityStatus);
        this.healthController.addAsyncDeepDependencyCheck("kafka.connectivity", this::kafkaConnectivityStatus);
        // P0-2: Removed discarded PipelineController instantiation - pipeline routes are handled inline
        this.agentController = new AgentController(this.engine, this.agentDataCloud, this.sloMetrics);
        this.marketplaceController = new AgentMarketplaceController(this.agentDataCloud);
        this.patternController = new PatternController(this.engine, this.patternStore);
        this.sseController = new SseController();
        this.analyticsController = new AnalyticsController(
            this.engine,
            this.agentDataCloud,
            this.analyticsStore,
            this.queryService,
            this.reportingService,
            (tenantId, eventType, payload) -> this.sseController.broadcastSseEvent(tenantId, eventType, payload));
        this.costController = new CostController(
            this.analyticsStore,
            this.agentDataCloud,
            () -> new ArrayList<>(this.recentRuns));
        this.deploymentController = new DeploymentController(this.deploymentAdapter);
        this.hitlController = new HitlController(this.humanReviewQueue,
            (tenantId, data) -> sseController.publishSseTo(tenantId, "hitl.update", data),
            this.sloMetrics,
            this.metricsCollector,
            resolveHitlEscalationTimeoutSeconds(),
            resolveHitlTimeoutPolicies());
        CompositeEvaluationGate evaluationGate = CompositeEvaluationGate.defaultGates();
        EpisodeLearningPipeline learningPipeline = agentDataCloud != null
            ? new EpisodeLearningPipeline(agentDataCloud, evaluationGate, humanReviewQueue)
            : null;
        this.learningController = new LearningController(this.agentDataCloud, this.humanReviewQueue, learningPipeline);
        this.complianceController = new ComplianceController(this.complianceService, this.soc2Framework);
        this.governanceController = new GovernanceController(
            killSwitchService  != null ? killSwitchService  : new InMemoryKillSwitchService(),
            degradationManager != null ? degradationManager : new InMemoryGracefulDegradationManager(),
            policyEngine       != null ? policyEngine       : new InMemoryPolicyEngine(),
            egressMonitor      != null ? egressMonitor      : new DefaultEgressMonitor(),
            injectionDetector  != null ? injectionDetector  : new RegexPromptInjectionDetector(),
            this::jsonResponse,
            this.complianceService,
            this.soc2Framework);
        this.lifecycleController = new LifecycleController(
            changeApprovalWorkflow  != null ? changeApprovalWorkflow  : new com.ghatana.platform.toolruntime.change.InMemoryChangeApprovalWorkflow(),
            recertificationPipeline != null ? recertificationPipeline : new com.ghatana.platform.toolruntime.recertification.InMemoryRecertificationPipeline());
        this.aiSuggestionsController = new AiSuggestionsController(this.analyticsStore, this.sloMetrics);
        this.nlpController = new NlpController();
        this.learningScheduler = learningPipeline != null ? new LearningScheduler(learningPipeline) : null;
    }

    private Promise<String> dataCloudConnectivityStatus() {
        if (this.agentDataCloud == null) {
            return Promise.of("disabled");
        }
        if (this.agentDataCloud.eventLogStore() == null) {
            return Promise.of("misconfigured");
        }

        return this.agentDataCloud.queryEvents("health-check-tenant", DataCloudClient.EventQuery.all())
            .map(ignored -> "ok")
            .then(Promise::of, error -> Promise.of("error: " + error.getClass().getSimpleName()));
    }

    private static boolean isExplicitProductionProfile() {
        String explicitProfile = resolveSetting("AEP_PROFILE");
        if (explicitProfile != null && !explicitProfile.isBlank()) {
            return "production".equalsIgnoreCase(explicitProfile.trim());
        }

        String explicitEnv = resolveSetting("AEP_ENV");
        if (explicitEnv == null || explicitEnv.isBlank()) {
            return false;
        }
        return AepRuntimeProfile.isProduction(Map.of("AEP_ENV", explicitEnv));
    }

    private static boolean isBooleanSettingEnabled(String key) {
        String value = resolveSetting(key);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    private static String resolveSetting(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return System.getenv(key);
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        eventloop = Eventloop.create();

        RoutingServlet router = RoutingServlet.builder(eventloop)
            // Health endpoints (delegated to HealthController)
            .with(HttpMethod.GET, "/health", request -> healthController.handle(request, "health"))
            .with(HttpMethod.GET, "/health/deep", request -> healthController.handle(request, "health/deep"))
            .with(HttpMethod.GET, "/ready", request -> healthController.handle(request, "ready"))
            .with(HttpMethod.GET, "/live", request -> healthController.handle(request, "live"))

            // Info endpoints
            .with(HttpMethod.GET, "/info", this::handleInfo)
            .with(HttpMethod.GET, "/metrics", this::handleMetrics)
            .with(HttpMethod.GET, "/metrics/slo", this::handleGetSloMetrics)

            // Event processing endpoints
            .with(HttpMethod.POST, "/api/v1/events", this::handleProcessEvent)
            .with(HttpMethod.POST, "/api/v1/events/batch", this::handleProcessBatch)

            // Deployment orchestration endpoints (delegated to DeploymentController)
            .with(HttpMethod.POST, "/api/v1/deployments", deploymentController::handleCreateDeployment)
            .with(HttpMethod.PUT, "/api/v1/deployments/:deploymentId", deploymentController::handleUpdateDeployment)
            .with(HttpMethod.DELETE, "/api/v1/deployments/:deploymentId", deploymentController::handleDeleteDeployment)

            // Pattern management endpoints (delegated to PatternController)
            .with(HttpMethod.GET, "/api/v1/patterns", patternController::handleListPatterns)
            .with(HttpMethod.POST, "/api/v1/patterns", patternController::handleRegisterPattern)
            .with(HttpMethod.GET, "/api/v1/patterns/:patternId", patternController::handleGetPattern)
            .with(HttpMethod.DELETE, "/api/v1/patterns/:patternId", patternController::handleDeletePattern)

            // Pipeline management endpoints (UI integration)
            .with(HttpMethod.GET, "/api/v1/pipelines", this::handleListPipelines)
            .with(HttpMethod.POST, "/api/v1/pipelines", this::handleCreatePipeline)
            .with(HttpMethod.POST, "/api/v1/pipelines/validate", this::handleValidatePipeline)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId", this::handleGetPipeline)
            .with(HttpMethod.PUT, "/api/v1/pipelines/:pipelineId", this::handleUpdatePipeline)
            .with(HttpMethod.DELETE, "/api/v1/pipelines/:pipelineId", this::handleDeletePipeline)

            // Pipeline versioning endpoints (AEP-07: draft → named version → rollback)
            .with(HttpMethod.GET, "/api/v1/pipelines/:pipelineId/versions", this::handleGetPipelineVersions)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/publish", this::handlePublishPipeline)
            .with(HttpMethod.POST, "/api/v1/pipelines/:pipelineId/rollback", this::handleRollbackPipeline)

            // Capability endpoints (delegated to CapabilitiesController – P7-2c)
            .with(HttpMethod.GET, "/admin/capabilities/schemas", capabilitiesController::handleSchemaCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/connectors", capabilitiesController::handleConnectorCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/encodings", capabilitiesController::handleEncodingCapabilities)
            .with(HttpMethod.GET, "/admin/capabilities/transforms", capabilitiesController::handleTransformCapabilities)

            // Analytics endpoints (delegated to AnalyticsController)
            .with(HttpMethod.POST, "/api/v1/analytics/anomalies", analyticsController::handleDetectAnomalies)
            .with(HttpMethod.GET, "/api/v1/analytics/anomalies", analyticsController::handleQueryAnomalies)
            .with(HttpMethod.POST, "/api/v1/analytics/forecast", analyticsController::handleForecast)
            .with(HttpMethod.POST, "/api/v1/analytics/kpis", analyticsController::handleSaveKpi)
            .with(HttpMethod.GET, "/api/v1/analytics/kpis", analyticsController::handleQueryKpis)
            .with(HttpMethod.POST, "/api/v1/analytics/metrics", analyticsController::handleSaveMetrics)
            .with(HttpMethod.GET, "/api/v1/analytics/metrics", analyticsController::handleQueryMetrics)
            .with(HttpMethod.POST, "/api/v1/analytics/query", analyticsController::handleAnalyticsQuery)
            .with(HttpMethod.POST, "/api/v1/analytics/aggregate", analyticsController::handleAnalyticsAggregate)
            .with(HttpMethod.POST, "/api/v1/reports", analyticsController::handleCreateReport)

            // Agent management endpoints (delegated to AgentController)
            .with(HttpMethod.POST, "/api/v1/agents", agentController::handleRegisterAgent)
            .with(HttpMethod.GET, "/api/v1/agents", agentController::handleListAgents)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId", agentController::handleGetAgent)
            .with(HttpMethod.POST, "/api/v1/agents/:agentId/execute", agentController::handleExecuteAgent)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory", agentController::handleGetAgentMemory)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/episodes", agentController::handleGetAgentEpisodes)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/facts", agentController::handleGetAgentFacts)
            .with(HttpMethod.GET, "/api/v1/agents/:agentId/memory/policies", agentController::handleGetAgentPolicies)
            .with(HttpMethod.DELETE, "/api/v1/agents/:agentId", agentController::handleDeregisterAgent)

            // Marketplace endpoints (Phase 3 foundation)
            .with(HttpMethod.GET, "/api/v1/catalog/marketplace/agents", marketplaceController::handleListAgents)
            .with(HttpMethod.POST, "/api/v1/catalog/marketplace/agents", marketplaceController::handlePublishAgent)
            .with(HttpMethod.GET, "/api/v1/catalog/marketplace/agents/:agentId", marketplaceController::handleGetAgent)
            .with(HttpMethod.GET, "/api/v1/catalog/marketplace/agents/:agentId/reviews", marketplaceController::handleListReviews)
            .with(HttpMethod.POST, "/api/v1/catalog/marketplace/agents/:agentId/reviews", marketplaceController::handleCreateReview)

            // Pipeline run & metrics endpoints (AEP-P7)
            .with(HttpMethod.GET, "/api/v1/runs", this::handleListPipelineRuns)
            .with(HttpMethod.GET, "/api/v1/runs/:runId", this::handleGetRunDetail)
            .with(HttpMethod.POST, "/api/v1/runs/:runId/cancel", this::handleCancelRun)
            .with(HttpMethod.GET, "/api/v1/metrics/pipelines", this::handleGetPipelineMetrics)
            .with(HttpMethod.GET, "/api/v1/costs/summary", costController::handleGetCostSummary)

            // HITL (Human-in-the-Loop) endpoints (delegated to HitlController)
            .with(HttpMethod.GET, "/api/v1/hitl/pending", hitlController::handleListPending)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/approve", hitlController::handleApprove)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/reject", hitlController::handleReject)
            .with(HttpMethod.POST, "/api/v1/hitl/:reviewId/escalate", hitlController::handleEscalate)

            // Learning system endpoints (delegated to LearningController)
            .with(HttpMethod.GET, "/api/v1/learning/episodes", learningController::handleListEpisodes)
            .with(HttpMethod.GET, "/api/v1/learning/policies", learningController::handleListPolicies)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/approve", learningController::handleApprovePolicy)
            .with(HttpMethod.POST, "/api/v1/learning/policies/:policyId/reject", learningController::handleRejectPolicy)
            .with(HttpMethod.POST, "/api/v1/learning/reflect", learningController::handleTriggerReflection)

            // AI suggestions endpoint (delegated to AiSuggestionsController)
            .with(HttpMethod.GET, "/api/v1/ai/suggestions", aiSuggestionsController::handleGetSuggestions)
            .with(HttpMethod.GET, "/api/v1/ai/suggestions/metrics", aiSuggestionsController::handleGetMetrics)

            // NLQ (Natural Language Query) endpoint (delegated to NlpController)
            .with(HttpMethod.POST, "/api/v1/nlp/parse", nlpController::handleParseQuery)

            // Server-Sent Events endpoints (delegated to SseController)
            .with(HttpMethod.GET, "/events/stream", sseController::handleSseStream)

            // Compliance endpoints (delegated to ComplianceController)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/access", complianceController::handleGdprAccess)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/erasure", complianceController::handleGdprErasure)
            .with(HttpMethod.POST, "/api/v1/compliance/gdpr/portability", complianceController::handleGdprPortability)
            .with(HttpMethod.POST, "/api/v1/compliance/ccpa/opt-out", complianceController::handleCcpaOptOut)
            .with(HttpMethod.GET,  "/api/v1/compliance/soc2/report", complianceController::handleSoc2Report)

            // Governance endpoints (delegated to GovernanceController)
            .with(HttpMethod.GET, "/governance/kill-switch", governanceController::handleKillSwitchStatus)
            .with(HttpMethod.POST, "/governance/kill-switch/activate", governanceController::handleActivateKillSwitch)
            .with(HttpMethod.POST, "/governance/kill-switch/deactivate", governanceController::handleDeactivateKillSwitch)
            .with(HttpMethod.GET, "/governance/degradation", governanceController::handleDegradationStatus)
            .with(HttpMethod.POST, "/governance/degradation", governanceController::handleSetDegradation)
            .with(HttpMethod.GET, "/governance/compliance/summary", governanceController::handleComplianceSummary)
            .with(HttpMethod.GET, "/governance/audit/summary", this::handleGovernanceAuditSummary)
            .with(HttpMethod.POST, "/governance/policy/evaluate", governanceController::handlePolicyEvaluate)
            .with(HttpMethod.GET,  "/governance/security/egress", governanceController::handleEgressStats)
            .with(HttpMethod.POST, "/governance/security/scan", governanceController::handleInjectionScan)

            // Lifecycle endpoints — change approval (delegated to LifecycleController)
            .with(HttpMethod.POST, "/lifecycle/changes", lifecycleController::handleSubmitChange)
            .with(HttpMethod.GET,  "/lifecycle/changes", lifecycleController::handleListPendingChanges)
            .with(HttpMethod.GET,  "/lifecycle/changes/:changeId", lifecycleController::handleGetChange)
            .with(HttpMethod.POST, "/lifecycle/changes/:changeId/approve", lifecycleController::handleApproveChange)
            .with(HttpMethod.POST, "/lifecycle/changes/:changeId/reject", lifecycleController::handleRejectChange)
            .with(HttpMethod.POST, "/lifecycle/changes/:changeId/withdraw", lifecycleController::handleWithdrawChange)

            // Lifecycle endpoints — recertification (delegated to LifecycleController)
            .with(HttpMethod.POST, "/lifecycle/recertification/campaigns", lifecycleController::handleCreateCampaign)
            .with(HttpMethod.GET,  "/lifecycle/recertification/campaigns", lifecycleController::handleListCampaigns)
            .with(HttpMethod.GET,  "/lifecycle/recertification/campaigns/:campaignId", lifecycleController::handleGetCampaign)
            .with(HttpMethod.GET,  "/lifecycle/recertification/campaigns/:campaignId/items", lifecycleController::handleGetCampaignItems)
            .with(HttpMethod.POST, "/lifecycle/recertification/campaigns/:campaignId/items/:itemId/certify", lifecycleController::handleCertifyItem)
            .with(HttpMethod.POST, "/lifecycle/recertification/campaigns/:campaignId/items/:itemId/revoke", lifecycleController::handleRevokeItem)
            .with(HttpMethod.GET,  "/lifecycle/recertification/campaigns/:campaignId/report", lifecycleController::handleGenerateReport)

            .build();

        // Wrap the router with the OWASP security filter (headers, CORS, rate limiting, payload size)
        String allowedOrigins = System.getenv().getOrDefault("AEP_CORS_ORIGINS", "*");
        String trustedProxyCidrs = System.getenv().getOrDefault("AEP_TRUSTED_PROXY_CIDRS", "");
        AepSecurityFilter securityFilter = new AepSecurityFilter(
            router,
            allowedOrigins,
            trustedProxyCidrs,
            metricsCollector);
        SessionFilter sessionFilter = new SessionFilter(securityFilter);

        // Wrap with authentication filter - enforces JWT auth when AEP_JWT_SECRET is set
        // Public endpoints (/health, /ready, /live, /info, /metrics, /events/stream) bypass auth
        AepAuthFilter authFilter = new AepAuthFilter(sessionFilter);
        AsyncServlet observedServlet = applyRequestTraceObservation(authFilter);

        server = HttpServer.builder(eventloop, observedServlet)
            .withListenPort(port)
            .build();

        // Initialize SSE heartbeat via SseController before the event loop starts.
        sseController.init(eventloop);
        // Start periodic learning reflection (no-op when DataCloud is not configured).
        if (learningScheduler != null) {
            learningScheduler.init(eventloop);
        }
        serverThread = new Thread(() -> {
            try {
                server.listen();
                log.info("AEP HTTP Server started on port {}", port);
                eventloop.run();
            } catch (Exception e) {
                log.error("Failed to start HTTP server", e);
            }
        }, "aep-http-server");
        serverThread.start();
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (learningScheduler != null) {
            learningScheduler.stop();
        }
        sseController.shutdown();
        if (eventloop != null) {
            CountDownLatch shutdownLatch = new CountDownLatch(1);
            eventloop.execute(() -> {
                try {
                    if (server != null) {
                        server.close();
                    }
                } finally {
                    eventloop.breakEventloop();
                    shutdownLatch.countDown();
                }
            });
            awaitShutdown(shutdownLatch);
        } else if (server != null) {
            server.close();
        }
        if (serverThread != null && serverThread != Thread.currentThread()) {
            try {
                serverThread.join(2_000);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for AEP HTTP server thread to stop", interruptedException);
            }
        }

        log.info("AEP HTTP Server stopped");
    }

    private void awaitShutdown(CountDownLatch shutdownLatch) {
        try {
            if (!shutdownLatch.await(2, TimeUnit.SECONDS)) {
                log.warn("Timed out waiting for AEP HTTP server shutdown to finish");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for AEP HTTP server shutdown", interruptedException);
        }
    }

    // ==================== Info Endpoints ====================

    private Promise<HttpResponse> handleInfo(HttpRequest request) {
        return Promise.of(jsonResponse(Map.of(
            "service", "AEP",
            "version", "1.0.0-SNAPSHOT",
            "description", "Agentic Event Processor",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleMetrics(HttpRequest request) {
        if (prometheusRegistry != null) {
            String scrape = prometheusRegistry.scrape();
            return Promise.of(HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE,
                    HttpHeaderValue.of("text/plain; version=0.0.4; charset=utf-8"))
                .withBody(scrape.getBytes(StandardCharsets.UTF_8))
                .build());
        }
        // Fallback when no Prometheus registry is configured (e.g. tests / embedded mode)
        return Promise.of(jsonResponse(Map.of(
            "service", "aep",
            "uptime_seconds", System.currentTimeMillis() / 1000,
            "memory_used_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024),
            "memory_free_mb", Runtime.getRuntime().freeMemory() / (1024 * 1024),
            "processors", Runtime.getRuntime().availableProcessors(),
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleGetSloMetrics(HttpRequest request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("runCounts", sloMetrics.runCountSnapshot());
        body.put("replay", sloMetrics.replaySnapshot());
        body.put("agentExecution", sloMetrics.agentExecutionSnapshot());
        body.put("metricsLink", "/metrics");
        body.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse(body));
    }

    private String databaseHealthStatus() {
        if (dataSource == null) {
            return "disabled";
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? "ok" : "unhealthy";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String redisHealthStatus() {
        if (jedisPool == null) {
            return "disabled";
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping()) ? "ok" : "unhealthy";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String eventLoopHealthStatus() {
        if (eventloop == null || serverThread == null) {
            return "initializing";
        }
        return serverThread.isAlive() ? "ok" : "unhealthy";
    }

    private String heapMemoryHealthStatus() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        if (maxMemory <= 0) {
            return "unknown";
        }

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usageRatio = (double) usedMemory / maxMemory;
        if (usageRatio >= HEAP_CRITICAL_RATIO) {
            return String.format(Locale.ROOT, "critical: %.1f%% heap used", usageRatio * 100.0);
        }
        if (usageRatio >= HEAP_WARNING_RATIO) {
            return String.format(Locale.ROOT, "warning: %.1f%% heap used", usageRatio * 100.0);
        }
        return "ok";
    }

    private Promise<String> kafkaConnectivityStatus() {
        String bootstrapServers = resolveRuntimeSetting(KAFKA_BOOTSTRAP_SERVERS_SETTING);
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return Promise.of("disabled");
        }
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> probeBootstrapConnectivity(bootstrapServers));
    }

    private String probeBootstrapConnectivity(String bootstrapServers) {
        String[] brokers = bootstrapServers.split(",");
        List<String> failures = new ArrayList<>();
        for (String broker : brokers) {
            String trimmed = broker.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.lastIndexOf(':');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                return "misconfigured";
            }
            String host = trimmed.substring(0, separator);
            int portNumber;
            try {
                portNumber = Integer.parseInt(trimmed.substring(separator + 1));
            } catch (NumberFormatException exception) {
                return "misconfigured";
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, portNumber), CONNECTIVITY_PROBE_TIMEOUT_MILLIS);
                return "ok";
            } catch (Exception exception) {
                failures.add(trimmed + "=" + exception.getClass().getSimpleName());
            }
        }

        if (failures.isEmpty()) {
            return "misconfigured";
        }
        return "error: " + String.join(", ", failures);
    }

    @Nullable
    private String resolveRuntimeSetting(String key) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String environmentValue = System.getenv(key);
        return environmentValue == null || environmentValue.isBlank() ? null : environmentValue;
    }

    private AepComplianceService.ErasureCleanupHook reportCleanupHook() {
        return (tenantId, subjectId, report) -> {
            if (patternStore != null) {
                patternStore.invalidateCache();
            }
            return Promise.of(null);
        };
    }

    // ==================== Request Tracing Helpers ====================

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACESTATE_HEADER = "tracestate";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private AsyncServlet applyRequestTraceObservation(AsyncServlet delegate) {
        return request -> {
            initializeRequestTrace(request);
            try {
                return delegate.serve(request)
                    .whenComplete((response, error) -> clearRequestTrace());
            } catch (Exception exception) {
                clearRequestTrace();
                return Promise.ofException(exception);
            }
        };
    }

    private RequestTraceContext initializeRequestTrace(HttpRequest request) {
        String correlationId = request.getHeader(HttpHeaders.of(CORRELATION_ID_HEADER));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        ParsedTraceParent traceParent = parseTraceParent(request.getHeader(HttpHeaders.of(TRACEPARENT_HEADER)));
        String traceId = traceParent != null ? traceParent.traceId() : newTraceId();
        String spanId = newSpanId();
        boolean sampled = traceParent == null || traceParent.sampled();
        String tracestate = request.getHeader(HttpHeaders.of(TRACESTATE_HEADER));
        RequestTraceContext traceContext = new RequestTraceContext(
            correlationId,
            traceId,
            spanId,
            sampled,
            tracestate);
        request.attach(RequestTraceContext.class, traceContext);
        RequestTraceSupport.setCurrent(new RequestTraceSupport.TraceHeaders(
            traceContext.correlationId(),
            traceContext.traceId(),
            traceContext.spanId(),
            traceContext.sampled(),
            traceContext.tracestate()));
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        return traceContext;
    }

    private void clearRequestTrace() {
        RequestTraceSupport.clearCurrent();
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    private static ParsedTraceParent parseTraceParent(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String[] parts = headerValue.trim().split("-");
        if (parts.length < 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return null;
        }
        boolean sampled;
        try {
            sampled = (Integer.parseInt(parts[3], 16) & 0x01) == 0x01;
        } catch (NumberFormatException exception) {
            return null;
        }
        return new ParsedTraceParent(parts[1], parts[2], sampled);
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private long resolveHitlEscalationTimeoutSeconds() {
        String configured = System.getProperty("AEP_HITL_ESCALATION_TIMEOUT_SECONDS");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("AEP_HITL_ESCALATION_TIMEOUT_SECONDS");
        }
        if (configured == null || configured.isBlank()) {
            return HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            return parsed > 0 ? parsed : HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS;
        } catch (NumberFormatException exception) {
            log.warn("Invalid AEP_HITL_ESCALATION_TIMEOUT_SECONDS value '{}'; using default {}",
                configured,
                HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS);
            return HitlController.DEFAULT_ESCALATION_TIMEOUT_SECONDS;
        }
    }

    private Map<String, HitlController.TenantHitlPolicy> resolveHitlTimeoutPolicies() {
        String configured = System.getProperty("AEP_HITL_TIMEOUT_POLICIES");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("AEP_HITL_TIMEOUT_POLICIES");
        }
        if (configured == null || configured.isBlank()) {
            return Map.of();
        }

        Map<String, HitlController.TenantHitlPolicy> policies = new LinkedHashMap<>();
        for (String rawEntry : configured.split(";")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separatorIndex = entry.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex == entry.length() - 1) {
                log.warn("Ignoring invalid AEP_HITL_TIMEOUT_POLICIES entry '{}'", entry);
                continue;
            }

            String tenantKey = entry.substring(0, separatorIndex).trim();
            String[] parts = entry.substring(separatorIndex + 1).trim().split(":", 4);
            try {
                long thresholdSeconds = Long.parseLong(parts[0].trim());
                HitlController.OverdueAction overdueAction = parts.length > 1
                    ? HitlController.OverdueAction.from(parts[1])
                    : HitlController.OverdueAction.ESCALATE;
                String destinationType = parts.length > 2 ? trimToNull(parts[2]) : null;
                String destination = parts.length > 3 ? trimToNull(parts[3]) : null;
                policies.put(tenantKey, new HitlController.TenantHitlPolicy(
                    thresholdSeconds,
                    overdueAction,
                    destinationType,
                    destination));
            } catch (NumberFormatException exception) {
                log.warn("Ignoring invalid HITL timeout threshold entry '{}'", entry);
            }
        }
        return policies.isEmpty() ? Map.of() : Collections.unmodifiableMap(policies);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ==================== Event Processing Endpoints ====================

    @SuppressWarnings("unchecked")
    private Promise<HttpResponse> handleProcessEvent(HttpRequest request) {
        Instant receivedAt = Instant.now();
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> eventData = objectMapper.readValue(body, Map.class);

                String tenantId = AepInputValidator.validateTenantId(
                    (String) eventData.getOrDefault("tenantId", "default"));
                String eventType = AepInputValidator.validateEventType(
                    (String) eventData.getOrDefault("type", "unknown"));
                Map<String, Object> rawPayload = rawObjectMap(eventData.get("payload"));
                Map<String, Object> payload = AepInputValidator.validatePayload(rawPayload);

                // P0-4: Check idempotency key for deduplication
                String idempotencyKey = request.getHeader(HttpHeaders.of("Idempotency-Key"));
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    synchronized (processedIdempotencyKeys) {
                        if (processedIdempotencyKeys.contains(idempotencyKey)) {
                            log.info("Duplicate event detected with idempotency key for tenantId={}, eventType={}", tenantId, eventType);
                            return Promise.of(errorResponse(409, "Duplicate event: idempotency key already processed"));
                        }
                    }
                }

                AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
                Instant startedAt = Instant.now();

                // P0-1: Check consent before processing event
                return consentService.evaluateConsent(tenantId, event)
                    .then(consentDecision -> {
                        if (!consentDecision.allowed()) {
                            log.warn("Event processing denied by consent service for tenantId={}, eventType={}, reason={}",
                                tenantId, eventType, consentDecision.reason());
                            return Promise.of(errorResponse(403, "Event processing denied: " + consentDecision.reason()));
                        }

                        // P3-18: Scan event data for PII before processing
                        PIIScanner.PIIResult piiResult = piiScanner.scanMap(payload);
                        if (piiResult.hasPII()) {
                            log.warn("[PIIScanner] PII detected in event for tenantId={}, eventId={}, types={}",
                                tenantId, "n/a", piiResult.items().stream()
                                    .map(PIIScanner.PIIItem::type)
                                    .distinct()
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(""));
                        }

                        return engine.process(tenantId, event)
                            .map(result -> {
                                // P0-4: Store idempotency key after successful processing
                                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                                    synchronized (processedIdempotencyKeys) {
                                        processedIdempotencyKeys.add(idempotencyKey);
                                    }
                                }

                                recordRun(result.eventId(), tenantId, null,
                                    result.success() ? "SUCCEEDED" : "FAILED", startedAt);
                                sloMetrics.recordIntakeLatency(receivedAt, startedAt, tenantId);
                                return jsonResponse(Map.of(
                                    "eventId", result.eventId(),
                                    "success", result.success(),
                                    "detections", result.detections().size(),
                                    "timestamp", Instant.now().toString()
                                ));
                            });
                    })
                    .whenComplete((result, error) -> {
                    });
            } catch (Exception e) {
                log.error("Error processing event", e);
                return Promise.of(errorResponse(400, "Invalid event data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read event body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private record ParsedTraceParent(String traceId, String parentSpanId, boolean sampled) {
    }

    private record RequestTraceContext(String correlationId,
                                       String traceId,
                                       String spanId,
                                       boolean sampled,
                                       @Nullable String tracestate) {
    }

    private Promise<HttpResponse> handleProcessBatch(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> batchData = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                String tenantId = (String) batchData.getOrDefault("tenantId", "default");
                List<Map<String, Object>> eventsData = rawMapList(batchData.get("events"));

                AepInputValidator.validateBatchSize(eventsData.size());
                if (eventsData.isEmpty()) {
                    return Promise.of(errorResponse(400, "Batch request must include non-empty events array"));
                }

                List<Promise<AepEngine.ProcessingResult>> processingPromises = eventsData.stream()
                    .map(eventData -> {
                        String eventType = (String) eventData.getOrDefault("type", "unknown");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload =
                            (Map<String, Object>) eventData.getOrDefault("payload", Map.of());
                        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
                        return engine.process(tenantId, event);
                    })
                    .toList();

                return Promises.toList(processingPromises)
                    .map(results -> {
                        long successCount = results.stream().filter(AepEngine.ProcessingResult::success).count();
                        int totalDetections =
                            results.stream().mapToInt(result -> result.detections().size()).sum();
                        List<Map<String, Object>> events = results.stream()
                            .map(result -> Map.<String, Object>of(
                                "eventId", result.eventId(),
                                "success", result.success(),
                                "detections", result.detections().size()))
                            .toList();
                        return jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "total", results.size(),
                            "successCount", successCount,
                            "failureCount", results.size() - successCount,
                            "totalDetections", totalDetections,
                            "events", events,
                            "timestamp", Instant.now().toString()
                        ));
                    })
                    .mapException(e -> new RuntimeException("Batch processing failed: " + e.getMessage(), e))
                    .then(Promise::of, e -> Promise.of(errorResponse(500, e.getMessage())));
            } catch (Exception e) {
                log.error("Error processing batch events", e);
                return Promise.of(errorResponse(400, "Invalid batch data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read batch body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    // ==================== Pipeline Management Endpoints ====================

    private Promise<HttpResponse> handleListPipelines(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String nameFilter = request.getQueryParameter("name");
        Boolean activeOnly = parseBooleanQuery(request.getQueryParameter("activeOnly"));
        int page = parseIntQuery(request.getQueryParameter("page"), 1);
        int size = parseIntQuery(request.getQueryParameter("size"), 50);

        return pipelineRepository.findAll(TenantId.of(tenantId), nameFilter, activeOnly, page, size)
            .map(result -> {
                List<Map<String, Object>> pipelines = result.content().stream()
                    .map(this::toPipelineResponse)
                    .toList();
                return jsonResponse(Map.of(
                    "pipelines", pipelines,
                    "count", pipelines.size(),
                    "total", result.totalElements(),
                    "page", result.pageNumber() + 1,
                    "size", result.pageSize(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to list pipelines: " + e.getMessage())));
    }

    private Promise<HttpResponse> handleGetPipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        return pipelineRepository.findById(pipelineId, TenantId.of(tenantId))
            .map(optPipeline -> optPipeline
                .map(pipeline -> jsonResponse(toPipelineResponse(pipeline)))
                .orElseGet(() -> errorResponse(404, "Pipeline not found: " + pipelineId)))
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to get pipeline: " + e.getMessage())));
    }

    private Promise<HttpResponse> handleCreatePipeline(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> pipeline = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String tenantId = resolveTenantId(request, pipeline);
                Pipeline candidate = mapToPipeline(pipeline, tenantId);
                if (candidate.getId() == null || candidate.getId().isBlank()) {
                    candidate.setId(java.util.UUID.randomUUID().toString());
                }

                if (candidate.getName() == null || candidate.getName().isBlank()) {
                    return Promise.of(errorResponse(400, "Pipeline name is required"));
                }

                return pipelineRepository.nextVersion(candidate.getName(), TenantId.of(tenantId))
                    .then(nextVersion -> {
                        candidate.setVersion(candidate.getVersion() > 0 ? candidate.getVersion() : nextVersion);
                        List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, null));
                        errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                        if (!errors.isEmpty()) {
                            return Promise.of(jsonResponse(Map.of(
                                "valid", false,
                                "errors", errors,
                                "warnings", List.of(),
                                "timestamp", Instant.now().toString()
                            )));
                        }
                        return pipelineRepository.save(candidate)
                            .map(saved -> jsonResponse(toPipelineResponse(saved)));
                    })
                    .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to create pipeline: " + e.getMessage())));
            } catch (Exception e) {
                log.error("Error creating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleUpdatePipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> updateData = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                TenantId tenant = TenantId.of(tenantId);

                return pipelineRepository.findById(pipelineId, tenant)
                    .then(optExisting -> {
                        if (optExisting.isEmpty()) {
                            return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                        }

                        PipelineRegistration existing = optExisting.get();
                        Integer expectedVersion = resolveExpectedPipelineVersion(request, updateData);
                        if (expectedVersion == null) {
                            return Promise.of(jsonResponse(428, Map.of(
                                "error", "Pipeline updates require an expected version via request body version, expectedVersion, or If-Match header",
                                "errorCode", PIPELINE_VERSION_REQUIRED_CODE,
                                "pipelineId", pipelineId,
                                "currentVersion", existing.getVersion(),
                                "suggestion", "Reload the latest pipeline and retry the update with the current version.",
                                "timestamp", Instant.now().toString()
                            )));
                        }
                        if (expectedVersion.intValue() != existing.getVersion()) {
                            recordPipelineUpdateConflict(tenantId, pipelineId, expectedVersion.intValue(), existing.getVersion());
                            return Promise.of(jsonResponse(409, Map.of(
                                "error", "Pipeline update conflict: the pipeline has been modified since the caller last loaded it",
                                "errorCode", PIPELINE_VERSION_CONFLICT_CODE,
                                "pipelineId", pipelineId,
                                "expectedVersion", expectedVersion.intValue(),
                                "currentVersion", existing.getVersion(),
                                "suggestion", "Reload the latest pipeline definition, merge your changes, and retry with the new version.",
                                "timestamp", Instant.now().toString()
                            )));
                        }

                        Pipeline updatePatch = mapToPipeline(updateData, tenantId);
                        updatePatch.setName(updatePatch.getName() != null ? updatePatch.getName() : existing.getName());
                        updatePatch.setTenantId(existing.getTenantId());
                        updatePatch.setVersion(existing.getVersion() + 1);

                        PipelineRegistration candidate = existing.newVersion();
                        candidate.setId(existing.getId());
                        candidate.setVersion(existing.getVersion() + 1);
                        candidate.updateFrom(updatePatch);
                        if (candidate.getUpdatedBy() == null || candidate.getUpdatedBy().isBlank()) {
                            candidate.setUpdatedBy("aep-http");
                        }

                        List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, existing));
                        errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                        if (!errors.isEmpty()) {
                            return Promise.of(jsonResponse(Map.of(
                                "valid", false,
                                "errors", errors,
                                "warnings", List.of(),
                                "timestamp", Instant.now().toString()
                            )));
                        }

                        return pipelineRepository.save(candidate)
                            .map(saved -> jsonResponse(toPipelineResponse(saved)));
                    })
                    .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to update pipeline: " + e.getMessage())));
            } catch (Exception e) {
                log.error("Error updating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline update body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Promise<HttpResponse> handleDeletePipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        TenantId tenant = TenantId.of(tenantId);
        return pipelineRepository.exists(pipelineId, tenant)
            .then(exists -> {
                if (!exists) {
                    return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                }
                return pipelineRepository.delete(pipelineId, tenant, false, "aep-http")
                    .map(ignored -> jsonResponse(Map.of(
                        "deleted", true,
                        "pipelineId", pipelineId,
                        "timestamp", Instant.now().toString()
                    )));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to delete pipeline: " + e.getMessage())));
    }

    // ==================== Pipeline Versioning Handlers (AEP-07) ====================

    /**
     * GET /api/v1/pipelines/:pipelineId/versions
     * Returns the full version history for the pipeline ordered by version number ascending.
     */
    private Promise<HttpResponse> handleGetPipelineVersions(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");

        return pipelineRepository.findVersionHistory(pipelineId, tenantId)
            .map(history -> {
                List<Map<String, Object>> versions = history.stream()
                    .map(this::toPipelineVersionResponse)
                    .toList();
                return jsonResponse(Map.of(
                    "pipelineId", pipelineId,
                    "versions", versions,
                    "count", versions.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to retrieve version history: " + e.getMessage())));
    }

    /**
     * POST /api/v1/pipelines/:pipelineId/publish
     * Publishes the current DRAFT pipeline under a named version label.
     *
     * <p>Expected body: {@code {"versionLabel": "v1.0.0"}}
     *
     * <ul>
     *   <li>Archives the previous PUBLISHED snapshot.</li>
     *   <li>Saves the current state as a PUBLISHED snapshot.</li>
     *   <li>The live pipeline record retains the new version number and label.</li>
     * </ul>
     */
    private Promise<HttpResponse> handlePublishPipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        TenantId tenant = TenantId.of(tenantId);

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String versionLabel = asString(payload.get("versionLabel"));
                if (versionLabel == null || versionLabel.isBlank()) {
                    return Promise.of(errorResponse(400, "versionLabel is required to publish a pipeline version"));
                }

                return pipelineRepository.findById(pipelineId, tenant)
                    .then(optPipeline -> {
                        if (optPipeline.isEmpty()) {
                            return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                        }

                        com.ghatana.pipeline.registry.model.PipelineRegistration existing = optPipeline.get();

                        // Build the published snapshot (copy of current draft)
                        com.ghatana.pipeline.registry.model.PipelineRegistration snapshot = existing.newVersion();
                        snapshot.setId(existing.getId());
                        snapshot.setVersion(existing.getVersion());
                        snapshot.setVersionLabel(versionLabel);
                        snapshot.setVersionStatus(com.ghatana.pipeline.registry.model.PipelineVersionStatus.PUBLISHED);
                        snapshot.setUpdatedAt(Instant.now());

                        // Persist snapshot to version history
                        return pipelineRepository.saveVersionSnapshot(pipelineId, snapshot)
                            .then(ignored -> {
                                // Update the live pipeline to carry the new label and published status
                                existing.setVersionLabel(versionLabel);
                                existing.setVersionStatus(com.ghatana.pipeline.registry.model.PipelineVersionStatus.PUBLISHED);
                                existing.setUpdatedAt(Instant.now());
                                return pipelineRepository.save(existing);
                            })
                            .map(saved -> {
                                log.info("Published pipeline id={} as version '{}' (v{})",
                                    pipelineId, versionLabel, existing.getVersion());
                                return jsonResponse(Map.of(
                                    "published", true,
                                    "pipelineId", pipelineId,
                                    "versionLabel", versionLabel,
                                    "version", existing.getVersion(),
                                    "timestamp", Instant.now().toString()
                                ));
                            });
                    })
                    .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to publish pipeline: " + e.getMessage())));
            } catch (Exception e) {
                log.error("Error reading publish pipeline body", e);
                return Promise.of(errorResponse(400, "Invalid publish request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read publish pipeline body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    /**
     * POST /api/v1/pipelines/:pipelineId/rollback?toVersion=N
     * Restores the pipeline to a specific version snapshot from history.
     *
     * <p>The restored snapshot becomes the current DRAFT; the previous live state
     * is NOT automatically snapshotted (publish first if preservation is needed).
     */
    private Promise<HttpResponse> handleRollbackPipeline(HttpRequest request) {
        String tenantId = resolveTenantId(request, null);
        String pipelineId = request.getPathParameter("pipelineId");
        String toVersionParam = request.getQueryParameter("toVersion");

        if (toVersionParam == null || toVersionParam.isBlank()) {
            return Promise.of(errorResponse(400, "Query parameter 'toVersion' is required for rollback"));
        }

        int toVersion;
        try {
            toVersion = Integer.parseInt(toVersionParam);
        } catch (NumberFormatException e) {
            return Promise.of(errorResponse(400, "Invalid 'toVersion' value — must be an integer"));
        }

        final int targetVersion = toVersion;
        return pipelineRepository.findVersionSnapshot(pipelineId, targetVersion, tenantId)
            .then(optSnapshot -> {
                if (optSnapshot.isEmpty()) {
                    return Promise.of(errorResponse(404,
                        "No version snapshot found for pipeline " + pipelineId + " at version " + targetVersion));
                }

                com.ghatana.pipeline.registry.model.PipelineRegistration snapshot = optSnapshot.get();

                // Restore the snapshot as the current live pipeline in DRAFT status
                com.ghatana.pipeline.registry.model.PipelineRegistration restored = snapshot.newVersion();
                restored.setId(pipelineId);
                restored.setVersion(snapshot.getVersion());
                restored.setVersionLabel(null);
                restored.setVersionStatus(com.ghatana.pipeline.registry.model.PipelineVersionStatus.DRAFT);
                restored.setUpdatedAt(Instant.now());

                return pipelineRepository.save(restored)
                    .map(saved -> {
                        log.info("Rolled back pipeline id={} to version {}", pipelineId, targetVersion);
                        return jsonResponse(Map.of(
                            "rolledBack", true,
                            "pipelineId", pipelineId,
                            "restoredVersion", targetVersion,
                            "status", com.ghatana.pipeline.registry.model.PipelineVersionStatus.DRAFT.name(),
                            "timestamp", Instant.now().toString()
                        ));
                    });
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to rollback pipeline: " + e.getMessage())));
    }

    private Map<String, Object> toPipelineVersionResponse(com.ghatana.pipeline.registry.model.PipelineRegistration snapshot) {
        var base = new java.util.HashMap<String, Object>();
        base.put("version", snapshot.getVersion());
        base.put("versionLabel", snapshot.getVersionLabel() != null ? snapshot.getVersionLabel() : "");
        base.put("versionStatus", snapshot.getVersionStatus() != null ? snapshot.getVersionStatus().name() : "DRAFT");
        base.put("name", snapshot.getName() != null ? snapshot.getName() : "");
        base.put("createdAt", snapshot.getCreatedAt() != null ? snapshot.getCreatedAt().toString() : "");
        base.put("updatedAt", snapshot.getUpdatedAt() != null ? snapshot.getUpdatedAt().toString() : "");
        base.put("updatedBy", snapshot.getUpdatedBy() != null ? snapshot.getUpdatedBy() : "");
        return java.util.Collections.unmodifiableMap(base);
    }

    private Promise<HttpResponse> handleValidatePipeline(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> pipeline = objectMapper.readValue(
                    body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                String tenantId = resolveTenantId(request, pipeline);
                Pipeline candidate = mapToPipeline(pipeline, tenantId);
                List<String> errors = new ArrayList<>(pipelineValidator.validate(candidate, null));
                errors.addAll(pipelineValidator.validateDag(candidate.getConfig()));
                List<String> warnings = new ArrayList<>();
                if (candidate.getId() == null || candidate.getId().isBlank()) {
                    warnings.add("Pipeline has no explicit id");
                }

                return Promise.of(jsonResponse(Map.of(
                    "valid", errors.isEmpty(),
                    "errors", errors,
                    "warnings", warnings,
                    "timestamp", Instant.now().toString()
                )));
            } catch (Exception e) {
                log.error("Error validating pipeline", e);
                return Promise.of(errorResponse(400, "Invalid pipeline validation payload: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pipeline validate body", e);
            return Promise.of(errorResponse(400, "Failed to read request body"));
        });
    }

    private Pipeline mapToPipeline(Map<String, Object> payload, String tenantId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(asString(payload.get("id")));
        pipeline.setTenantId(TenantId.of(tenantId));
        pipeline.setName(asString(payload.get("name")));
        pipeline.setDescription(asString(payload.get("description")));
        pipeline.setActive(payload.get("active") == null || Boolean.TRUE.equals(payload.get("active")));
        pipeline.setVersion(payload.get("version") instanceof Number n ? n.intValue() : 0);
        pipeline.setConfig(buildPipelineConfig(payload));
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());
        pipeline.setCreatedBy(asString(payload.getOrDefault("createdBy", "aep-http")));
        pipeline.setUpdatedBy(asString(payload.getOrDefault("updatedBy", "aep-http")));
        return pipeline;
    }

    private String buildPipelineConfig(Map<String, Object> payload) {
        Object rawConfig = payload.get("config");
        try {
            if (rawConfig instanceof String configText && !configText.isBlank()) {
                return configText;
            }
            if (rawConfig != null) {
                return objectMapper.writeValueAsString(rawConfig);
            }
            Object stages = payload.get("stages");
            if (stages instanceof List<?>) {
                return objectMapper.writeValueAsString(Map.of("stages", stages));
            }
            return objectMapper.writeValueAsString(Map.of("stages", List.of()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline config payload", e);
        }
    }

    private Map<String, Object> toPipelineResponse(PipelineRegistration pipeline) {
        var response = new java.util.HashMap<String, Object>();
        response.put("id", pipeline.getId() != null ? pipeline.getId() : "");
        response.put("tenantId", pipeline.getTenantId() != null ? pipeline.getTenantId().value() : "default");
        response.put("name", pipeline.getName() != null ? pipeline.getName() : "");
        response.put("description", pipeline.getDescription() != null ? pipeline.getDescription() : "");
        response.put("version", pipeline.getVersion());
        response.put("versionLabel", pipeline.getVersionLabel() != null ? pipeline.getVersionLabel() : "");
        response.put("versionStatus", pipeline.getVersionStatus() != null ? pipeline.getVersionStatus().name() : "DRAFT");
        response.put("active", pipeline.isActive());
        response.put("config", pipeline.getConfig() != null ? parseJsonObject(pipeline.getConfig()) : Map.of());
        response.put("createdAt", pipeline.getCreatedAt() != null ? pipeline.getCreatedAt().toString() : Instant.now().toString());
        response.put("updatedAt", pipeline.getUpdatedAt() != null ? pipeline.getUpdatedAt().toString() : Instant.now().toString());
        response.put("updatedBy", pipeline.getUpdatedBy() != null ? pipeline.getUpdatedBy() : "aep-http");
        return java.util.Collections.unmodifiableMap(response);
    }

    private Object parseJsonObject(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ignored) {
            return json;
        }
    }

    // ==================== Pipeline Runs & Metrics Endpoints (AEP-P7) ====================

    /**
     * Lists recent pipeline runs tracked in-memory.
     * Runs are recorded whenever an event or batch is processed.
     * Optionally filtered by {@code pipelineId} query parameter.
     *
     * @return 200 with list of recent runs
     */
    private Promise<HttpResponse> handleListPipelineRuns(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String pipelineFilter = request.getQueryParameter("pipelineId");
        return listRunsForTenant(tenantId, pipelineFilter)
            .map(runs -> jsonResponse(Map.of(
                "runs", runs,
                "count", runs.size(),
                "tenantId", tenantId,
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to list runs: " + e.getMessage())));
    }

    /**
     * Returns the detail of a single pipeline run by its ID.
     *
     * @return 200 with the run record, 404 if not found in the in-memory buffer
     *
     * @doc.type method
     * @doc.purpose Retrieve a single pipeline run by runId for the unified run detail view
     * @doc.layer product
     * @doc.pattern Service
     */
    private Promise<HttpResponse> handleGetRunDetail(HttpRequest request) {
        String runId = request.getPathParameter("runId");
        if (runId == null || runId.isBlank()) {
            return Promise.of(errorResponse(400, "runId path parameter is required"));
        }
        String tenantId = resolveTenantId(request);
        return readRunEvidence(tenantId, runId)
            .map(evidence -> {
                Map<String, Object> baseRun = recentRuns.stream()
                    .filter(r -> runId.equals(r.get("runId")) && tenantId.equals(r.get("tenantId")))
                    .findFirst()
                    .map(java.util.LinkedHashMap::new)
                    .orElseGet(java.util.LinkedHashMap::new);

                if (baseRun.isEmpty() && evidence.isEmpty()) {
                    return errorResponse(404, "Run not found: " + runId);
                }

                return jsonResponse(reconstructRunDetail(runId, tenantId, evidence, baseRun));
            })
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to load run detail: " + e.getMessage())));
    }

    /**
     * Cancels a pipeline run. In-memory runs cannot be preempted mid-flight; returns 200
     * once the run entry is marked CANCELLED (idempotent — run may already be complete).
     *
     * @return 200 with cancel confirmation, 404 if run not found
     */
    private Promise<HttpResponse> handleCancelRun(HttpRequest request) {
        String runId = request.getPathParameter("runId");
        if (runId == null || runId.isBlank()) {
            return Promise.of(errorResponse(400, "runId path parameter is required"));
        }
        boolean found = recentRuns.stream().anyMatch(r -> runId.equals(r.get("runId")));
        if (!found) {
            return Promise.of(errorResponse(404, "Run not found: " + runId));
        }
        recentRuns.stream()
            .filter(r -> runId.equals(r.get("runId")))
            .findFirst()
            .ifPresent(r -> r.put("status", "CANCELLED"));
        return Promise.of(jsonResponse(Map.of(
            "runId", runId,
            "status", "CANCELLED",
            "timestamp", Instant.now().toString()
        )));
    }

    private Promise<HttpResponse> handleGovernanceAuditSummary(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        int limit = parseIntQuery(request.getQueryParameter("limit"), 20);
        return readGovernanceAuditEntries(tenantId, limit)
            .map(entries -> jsonResponse(Map.of(
                "tenantId", tenantId,
                "configured", runLedger != null,
                "entries", entries,
                "count", entries.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> Promise.of(errorResponse(500, "Failed to load governance audit summary: " + e.getMessage())));
    }

    /**
     * Returns aggregate pipeline metrics computed from the in-memory run buffer.
     *
     * @return 200 with pipeline metrics summary
     */
    private Promise<HttpResponse> handleGetPipelineMetrics(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        List<Map<String, Object>> tenantRuns = recentRuns.stream()
            .filter(r -> tenantId.equals(r.get("tenantId")))
            .collect(java.util.stream.Collectors.toList());
        long succeeded = tenantRuns.stream().filter(r -> "SUCCEEDED".equals(r.get("status"))).count();
        long failed = tenantRuns.stream().filter(r -> "FAILED".equals(r.get("status"))).count();
        long cancelled = tenantRuns.stream().filter(r -> "CANCELLED".equals(r.get("status"))).count();
        double successRate = tenantRuns.isEmpty() ? 0.0 : (double) succeeded / tenantRuns.size();

        return pipelineRepository.findAll(TenantId.of(tenantId), null, null, 1, 1_000)
            .map(result -> {
                List<Map<String, Object>> metrics = result.content().stream()
                    .map(p -> {
                        long pRuns = tenantRuns.stream()
                            .filter(r -> p.getId() != null && p.getId().equals(r.get("pipelineId")))
                            .count();
                        return Map.<String, Object>of(
                            "pipelineId", p.getId() != null ? p.getId() : "",
                            "pipelineName", p.getName() != null ? p.getName() : "",
                            "runCount", pRuns,
                            "active", p.isActive()
                        );
                    })
                    .toList();
                return jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "metrics", metrics,
                    "summary", Map.of(
                        "totalRuns", tenantRuns.size(),
                        "succeeded", succeeded,
                        "failed", failed,
                        "cancelled", cancelled,
                        "successRate", successRate
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e ->
                Promise.of(errorResponse(500, "Failed to get pipeline metrics: " + e.getMessage())));
    }

    // ==================== Run Tracking ====================

    /**
     * Records a processing result into the bounded in-memory run buffer (event-loop thread only)
     * and publishes a {@code run.update} SSE event to all subscribers of that tenant.
     */
    private void recordRun(String runId, String tenantId, @Nullable String pipelineId,
                           String status, Instant startedAt) {
        if (recentRuns.size() >= MAX_RECENT_RUNS) {
            recentRuns.pollFirst();
        }
        Instant completedAt = Instant.now();
        long durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        String pipeline = pipelineId != null && !pipelineId.isBlank() ? pipelineId : "event";
        Map<String, Object> run = new java.util.HashMap<>();
        run.put("runId", runId);
        run.put("tenantId", tenantId);
        run.put("pipelineId", pipeline);
        run.put("status", status);
        run.put("startedAt", startedAt.toString());
        run.put("completedAt", completedAt.toString());
        run.put("durationMs", durationMs);
        recentRuns.addLast(run);
        sseController.publishSseTo(tenantId, "run.update", run);
        // Phase-6: SLO metrics + durable run ledger (fire-and-forget)
        if ("SUCCEEDED".equals(status)) {
            sloMetrics.recordRunCompleted(tenantId, pipeline, durationMs);
            runLedgerService.recordRunCompleted(runId, tenantId, pipelineId, null, startedAt, 0);
        } else {
            sloMetrics.recordRunFailed(tenantId, pipeline, durationMs, "engine_error");
            runLedgerService.recordRunFailed(runId, tenantId, pipelineId, null, startedAt, "engine_error", null);
        }
    }

    // ==================== SSE Broadcast (Public API) ====================

    /**
     * Broadcasts an SSE event to all active subscribers of the given tenant.
     * Thread-safe: delegates to {@link SseController#broadcastSseEvent}.
     *
     * @param tenantId target tenant (or {@code "*"} to broadcast to all tenants)
     * @param type     SSE event type
     * @param data     payload map
     */
    public void broadcastSseEvent(String tenantId, String type, Map<String, Object> data) {
        sseController.broadcastSseEvent(tenantId, type, data);
    }

    // ==================== Helper Methods ====================

    /**
     * Resolves the tenant ID from the {@code X-Tenant-Id} header or {@code tenantId} query parameter,
     * defaulting to {@code "default"}.
     */
    private String resolveTenantId(HttpRequest request) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String fromQuery = request.getQueryParameter("tenantId");
        return (fromQuery != null && !fromQuery.isBlank()) ? fromQuery : "default";
    }

    private String resolveTenantId(HttpRequest request, Map<String, Object> payload) {
        String fromHeader = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader;
        String tenantId = request.getQueryParameter("tenantId");
        if ((tenantId == null || tenantId.isBlank()) && payload != null) {
            tenantId = asString(payload.get("tenantId"));
        }
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }

    private int parseIntQuery(String value, int fallback) {
        try {
            return value != null ? Integer.parseInt(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Boolean parseBooleanQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Integer resolveExpectedPipelineVersion(HttpRequest request, Map<String, Object> payload) {
        Object expectedVersionValue = payload.get("expectedVersion");
        if (expectedVersionValue instanceof Number number) {
            return number.intValue();
        }

        Object versionValue = payload.get("version");
        if (versionValue instanceof Number number) {
            return number.intValue();
        }

        String ifMatch = request.getHeader(HttpHeaders.of("If-Match"));
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }

        String normalized = ifMatch.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void recordPipelineUpdateConflict(String tenantId, String pipelineId, int expectedVersion, int currentVersion) {
        metricsCollector.incrementCounter(
            PIPELINE_UPDATE_CONFLICTS,
            "tenant", tenantId,
            "pipeline", pipelineId,
            "expected_version", Integer.toString(expectedVersion),
            "current_version", Integer.toString(currentVersion)
        );
    }

    private Promise<List<Map<String, Object>>> readRunEvidence(String tenantId, String runId) {
        if (runLedger == null) {
            return Promise.of(List.of());
        }
        return runLedger.readRunEvents(tenantId, Offset.zero(), 500)
            .map(entries -> entries.stream()
                .map(this::toLedgerEvent)
                .filter(event -> runId.equals(asString(event.get("runId"))))
                .toList());
    }

    private Promise<List<Map<String, Object>>> readGovernanceAuditEntries(String tenantId, int limit) {
        if (runLedger == null) {
            return Promise.of(recentRuns.stream()
                .filter(run -> tenantId.equals(run.get("tenantId")))
                .sorted((left, right) -> String.valueOf(right.getOrDefault("completedAt", ""))
                    .compareTo(String.valueOf(left.getOrDefault("completedAt", ""))))
                .limit(limit)
                .map(run -> Map.<String, Object>of(
                    "eventType", "run.summary",
                    "timestamp", asString(run.get("completedAt")) != null ? asString(run.get("completedAt")) : Instant.now().toString(),
                    "runId", asString(run.get("runId")),
                    "pipelineId", asString(run.get("pipelineId")),
                    "status", asString(run.get("status"))
                ))
                .toList());
        }

        return runLedger.readRunEvents(tenantId, Offset.zero(), Math.max(limit * 10, 100))
            .map(entries -> entries.stream()
                .map(this::toLedgerEvent)
                .sorted((left, right) -> String.valueOf(right.getOrDefault("timestamp", ""))
                    .compareTo(String.valueOf(left.getOrDefault("timestamp", ""))))
                .limit(limit)
                .toList());
    }

    private Promise<List<Map<String, Object>>> listRunsForTenant(String tenantId, @Nullable String pipelineFilter) {
        List<Map<String, Object>> inMemoryRuns = recentRuns.stream()
            .filter(run -> tenantId.equals(run.get("tenantId")))
            .filter(run -> pipelineFilter == null || pipelineFilter.equals(run.get("pipelineId")))
            .map(run -> (Map<String, Object>) new java.util.LinkedHashMap<String, Object>(run))
            .collect(java.util.stream.Collectors.toList());

        if (runLedger == null) {
            return Promise.of(sortRunsDescending(inMemoryRuns));
        }

        return runLedger.readRunEvents(tenantId, Offset.zero(), 2_000)
            .map(entries -> mergeRunSummaries(
                summarizePersistedRuns(tenantId, pipelineFilter, entries),
                inMemoryRuns));
    }

    private List<Map<String, Object>> summarizePersistedRuns(String tenantId,
                                                             @Nullable String pipelineFilter,
                                                             List<EventEntry> entries) {
        Map<String, List<Map<String, Object>>> evidenceByRunId = new LinkedHashMap<>();
        for (EventEntry entry : entries) {
            Map<String, Object> event = toLedgerEvent(entry);
            String runId = asString(event.get("runId"));
            if (runId == null || runId.isBlank()) {
                continue;
            }
            evidenceByRunId.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(event);
        }

        return sortRunsDescending(evidenceByRunId.entrySet().stream()
            .map(entry -> reconstructRunDetail(entry.getKey(), tenantId, entry.getValue(), Map.of()))
            .filter(run -> pipelineFilter == null || pipelineFilter.equals(run.get("pipelineId")))
            .map(run -> (Map<String, Object>) new java.util.LinkedHashMap<String, Object>(run))
            .collect(java.util.stream.Collectors.toList()));
    }

    private List<Map<String, Object>> mergeRunSummaries(List<Map<String, Object>> persistedRuns,
                                                        List<Map<String, Object>> inMemoryRuns) {
        Map<String, Map<String, Object>> runsById = new LinkedHashMap<>();
        for (Map<String, Object> persistedRun : persistedRuns) {
            String runId = asString(persistedRun.get("runId"));
            if (runId != null && !runId.isBlank()) {
                runsById.put(runId, new java.util.LinkedHashMap<>(persistedRun));
            }
        }
        for (Map<String, Object> inMemoryRun : inMemoryRuns) {
            String runId = asString(inMemoryRun.get("runId"));
            if (runId == null || runId.isBlank()) {
                continue;
            }
            Map<String, Object> merged = new java.util.LinkedHashMap<>(runsById.getOrDefault(runId, Map.of()));
            merged.putAll(inMemoryRun);
            runsById.put(runId, merged);
        }
        return sortRunsDescending(new ArrayList<>(runsById.values()));
    }

    private List<Map<String, Object>> sortRunsDescending(List<Map<String, Object>> runs) {
        return runs.stream()
            .sorted((left, right) -> runSortTimestamp(right).compareTo(runSortTimestamp(left)))
            .toList();
    }

    private String runSortTimestamp(Map<String, Object> run) {
        String completedAt = asString(run.get("completedAt"));
        if (completedAt != null && !completedAt.isBlank()) {
            return completedAt;
        }
        String startedAt = asString(run.get("startedAt"));
        return startedAt != null ? startedAt : "";
    }

    private Map<String, Object> toLedgerEvent(EventEntry entry) {
        Map<String, Object> payload = parsePayload(entry.payload());
        Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("eventId", entry.eventId().toString());
        event.put("eventType", entry.eventType());
        event.put("timestamp", entry.timestamp().toString());
        event.put("runId", entry.headers().getOrDefault("runId", asString(payload.get("runId"))));
        event.put("pipelineId", entry.headers().getOrDefault("pipelineId", asString(payload.get("pipelineId"))));
        event.put("payload", payload);
        return Map.copyOf(event);
    }

    private Map<String, Object> parsePayload(ByteBuffer payloadBuffer) {
        try {
            ByteBuffer duplicate = payloadBuffer.asReadOnlyBuffer();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            if (bytes.length == 0) {
                return Map.of();
            }
            return rawObjectMap(objectMapper.readValue(bytes, Map.class));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> reconstructRunDetail(
            String runId,
            String tenantId,
            List<Map<String, Object>> evidence,
            Map<String, Object> baseRun) {
        List<Map<String, Object>> lineage = evidence.stream()
            .map(event -> {
                Map<String, Object> payload = rawObjectMap(event.get("payload"));
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("eventType", event.get("eventType"));
                row.put("timestamp", event.get("timestamp"));
                row.put("pipelineId", event.get("pipelineId"));
                row.put("stepType", payload.getOrDefault("stepType", event.get("eventType")));
                row.put("status", payload.getOrDefault("status", baseRun.getOrDefault("status", "UNKNOWN")));
                row.put("details", payload);
                return Map.copyOf(row);
            })
            .toList();

        List<Map<String, Object>> decisions = evidence.stream()
            .map(event -> rawObjectMap(event.get("payload")))
            .filter(payload -> "review.decision".equals(payload.get("stepType")))
            .map(payload -> Map.<String, Object>of(
                "reviewItemId", payload.getOrDefault("reviewItemId", ""),
                "skillId", payload.getOrDefault("skillId", ""),
                "decision", payload.getOrDefault("decision", ""),
                "decidedAt", payload.getOrDefault("decidedAt", ""),
                "stepType", payload.getOrDefault("stepType", "review.decision")
            ))
            .toList();

        List<Map<String, Object>> policies = evidence.stream()
            .map(event -> rawObjectMap(event.get("payload")))
            .filter(payload -> "policy.promoted".equals(payload.get("stepType")))
            .map(payload -> Map.<String, Object>of(
                "policyId", payload.getOrDefault("policyId", ""),
                "skillId", payload.getOrDefault("skillId", ""),
                "version", payload.getOrDefault("version", ""),
                "promotedAt", payload.getOrDefault("promotedAt", ""),
                "stepType", payload.getOrDefault("stepType", "policy.promoted")
            ))
            .toList();

        String pipelineId = asString(baseRun.get("pipelineId"));
        if (pipelineId == null || pipelineId.isBlank()) {
            pipelineId = evidence.stream()
                .map(event -> asString(event.get("pipelineId")))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("event");
        }

        String status = asString(baseRun.get("status"));
        if (status == null || status.isBlank()) {
            status = evidence.stream()
                .map(event -> rawObjectMap(event.get("payload")))
                .map(payload -> asString(payload.get("status")))
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second)
                .orElse("RUNNING");
        }

        String startedAt = asString(baseRun.get("startedAt"));
        if (startedAt == null || startedAt.isBlank()) {
            startedAt = evidence.stream()
                .map(event -> rawObjectMap(event.get("payload")))
                .map(payload -> asString(payload.get("startedAt")))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> evidence.stream()
                    .map(event -> asString(event.get("timestamp")))
                    .findFirst()
                    .orElse(Instant.now().toString()));
        }

        String finishedAt = asString(baseRun.get("completedAt"));
        if (finishedAt == null || finishedAt.isBlank()) {
            finishedAt = evidence.stream()
                .map(event -> rawObjectMap(event.get("payload")))
                .map(payload -> {
                    String completedAt = asString(payload.get("completedAt"));
                    if (completedAt != null && !completedAt.isBlank()) {
                        return completedAt;
                    }
                    String failedAt = asString(payload.get("failedAt"));
                    return failedAt != null && !failedAt.isBlank() ? failedAt : null;
                })
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
        }

        long durationMs = baseRun.get("durationMs") instanceof Number number
            ? number.longValue()
            : evidence.stream()
                .map(event -> rawObjectMap(event.get("payload")))
                .map(payload -> payload.get("durationMs"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .max()
                .orElse(0L);

        Map<String, Object> reconstructed = new java.util.LinkedHashMap<>();
        reconstructed.put("id", baseRun.getOrDefault("runId", runId));
        reconstructed.put("runId", baseRun.getOrDefault("runId", runId));
        reconstructed.put("tenantId", tenantId);
        reconstructed.put("pipelineId", pipelineId);
        reconstructed.put("pipelineName", baseRun.getOrDefault("pipelineName", pipelineId));
        reconstructed.put("status", status);
        reconstructed.put("startedAt", startedAt);
        if (finishedAt != null && !finishedAt.isBlank()) {
            reconstructed.put("completedAt", finishedAt);
            reconstructed.put("finishedAt", finishedAt);
        }
        reconstructed.put("durationMs", durationMs);
        reconstructed.put("eventsProcessed", baseRun.getOrDefault("eventsProcessed", lineage.size()));
        reconstructed.put("errorsCount", baseRun.getOrDefault("errorsCount", evidence.stream()
            .map(event -> asString(event.get("eventType")))
            .filter("run.failed"::equals)
            .count()));
        reconstructed.put("lineage", lineage);
        reconstructed.put("decisions", decisions);
        reconstructed.put("policies", policies);
        reconstructed.put("evidence", evidence);
        return Map.copyOf(reconstructed);
    }

    private Map<String, Object> rawObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> converted = new java.util.HashMap<>();
        rawMap.forEach((key, item) -> converted.put(String.valueOf(key), item));
        return converted;
    }

    private List<Map<String, Object>> rawMapList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Object item : rawList) {
            converted.add(rawObjectMap(item));
        }
        return converted;
    }

    private HttpResponse jsonResponse(Map<String, Object> data) {
        return jsonResponse(200, data);
    }

    private HttpResponse jsonResponse(int code, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withBody(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        try {
            String safeMessage = message != null ? message.replace("\\", "\\\\").replace("\"", "\\\"") : "error";
            String json = objectMapper.writeValueAsString(Map.of(
                "error", safeMessage,
                "code", code,
                "timestamp", Instant.now().toString()
            ));
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            return RequestTraceSupport.applyTo(HttpResponse.ofCode(code))
                .withBody(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}
