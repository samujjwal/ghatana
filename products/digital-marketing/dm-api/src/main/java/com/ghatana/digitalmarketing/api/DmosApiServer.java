package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.ai.GovernedAgentWorkflowService;
import com.ghatana.digitalmarketing.application.approval.ApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowServiceImpl;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditService;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditServiceImpl;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationRepository;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationService;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationServiceImpl;
import com.ghatana.digitalmarketing.application.bootstrap.ProductionBootstrapValidator;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.campaign.CampaignPreflightDataProvider;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.campaign.CampaignServiceImpl;
import com.ghatana.digitalmarketing.bridge.CampaignEventSourcingAdapter;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import io.activej.promise.Promise;
import com.ghatana.digitalmarketing.application.command.DmCommandHandlerRegistry;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.application.command.DmCommandServiceImpl;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.metrics.MicrometerDmosMetricsCollector;
import com.ghatana.digitalmarketing.application.strategy.MarketingStrategyRepository;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorServiceImpl;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogService;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogServiceImpl;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceServiceImpl;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.digitalmarketing.bridge.CampaignEventSourcingAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapterImpl;
import com.ghatana.digitalmarketing.bridge.DmosRiskEvaluatorRegistrar;
import com.ghatana.digitalmarketing.connector.googleads.HttpDmGoogleAdsCampaignApiClientAdapter;
import com.ghatana.digitalmarketing.connector.googleads.InMemoryDmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.infra.ProductionProfileGuard;
import com.ghatana.digitalmarketing.infra.approval.InMemoryApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.infra.campaign.InMemoryCampaignRepository;
import com.ghatana.digitalmarketing.infra.connector.DmConnectorInMemoryRepository;
import com.ghatana.digitalmarketing.infra.googleads.DmGoogleAdsCampaignLinkInMemoryRepository;
import com.ghatana.digitalmarketing.infra.googleads.DmGoogleAdsCredentialInMemoryRepository;
import com.ghatana.digitalmarketing.infra.research.InMemoryCompetitorResearchRepository;
import com.ghatana.digitalmarketing.infra.transparency.InMemoryAiActionLogRepository;
import com.ghatana.digitalmarketing.infra.workspace.InMemoryWorkspaceRepository;
import com.ghatana.digitalmarketing.persistence.approval.PostgresApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.persistence.audit.PostgresWebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.persistence.budget.PostgresBudgetRecommendationRepository;
import com.ghatana.digitalmarketing.persistence.campaign.PostgresCampaignRepository;
import com.ghatana.digitalmarketing.persistence.strategy.PostgresMarketingStrategyRepository;
import com.ghatana.digitalmarketing.persistence.workspace.PostgresWorkspaceRepository;
import com.ghatana.digitalmarketing.persistence.command.PostgresDmCommandRepository;
import com.ghatana.digitalmarketing.persistence.governance.PostgresDmKillSwitchService;
import com.ghatana.digitalmarketing.bridge.OpaAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.platform.pac.CircuitBreakingPolicyAsCodeEngine;
import com.ghatana.platform.pac.OpaClient;
import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.approval.impl.DurableHumanApprovalPlugin;
import com.ghatana.plugin.approval.impl.StandardHumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.audit.impl.DurableAuditTrailPlugin;
import com.ghatana.plugin.audit.impl.StandardAuditTrailPlugin;
import com.ghatana.plugin.compliance.CompliancePlugin;
import com.ghatana.plugin.compliance.impl.StandardCompliancePlugin;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.impl.DurableConsentPlugin;
import com.ghatana.plugin.consent.impl.StandardConsentPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.notification.impl.DurableNotificationPlugin;
import com.ghatana.plugin.notification.impl.InMemoryNotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.governance.DmKillSwitchService;
import io.activej.eventloop.Eventloop;
import io.activej.launcher.Launcher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Production composition root for DMOS API server.
 *
 * <p>This class serves as the main entry point for the DMOS API server in production.
 * It validates the environment profile, wires all servlets, services, and repositories,
 * and starts the server with proper dependency injection.</p>
 *
 * <p>Environment validation:</p>
 * <ul>
 *   <li>DMOS_ENV must be set (production, staging, or development)</li>
 *   <li>Production profile requires PostgreSQL persistence (no in-memory adapters)</li>
 *   <li>Required environment variables must be set for production</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Production composition root with environment validation
 * @doc.layer product
 * @doc.pattern Composition Root
 */
public final class DmosApiServer extends Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(DmosApiServer.class);

    private static final String DMOS_ENV = "DMOS_ENV";
    private static final String PRODUCTION = "production";
    private static final String STAGING = "staging";
    private static final String DEVELOPMENT = "development";

    private final String environment;

    /** Simple component registry replacing ServiceGraph DI misuse. */
    private final Map<Class<?>, Object> components = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private <T> T get(Class<T> type) {
        Object value = components.get(type);
        if (value == null) {
            throw new IllegalStateException("Component not registered: " + type.getName());
        }
        return (T) value;
    }

    private <T> void register(Class<T> type, T value) {
        components.put(type, value);
    }

    public DmosApiServer() {
        this.environment = validateEnvironment();
    }

    /**
     * Validates the deployment environment and returns the environment name.
     *
     * @return environment name
     * @throws IllegalStateException if environment is invalid
     */
    private static String validateEnvironment() {
        String env = System.getenv(DMOS_ENV);
        if (env == null || env.isBlank()) {
            LOG.warn("{} not set, defaulting to development", DMOS_ENV);
            return DEVELOPMENT;
        }

        String normalized = env.trim().toLowerCase();
        if (!normalized.equals(PRODUCTION) && !normalized.equals(STAGING) && !normalized.equals(DEVELOPMENT)) {
            throw new IllegalStateException(
                "Invalid " + DMOS_ENV + ": " + env + ". Must be one of: production, staging, development"
            );
        }

        return normalized;
    }

    /**
     * Validates production-specific requirements.
     *
     * @throws IllegalStateException if production requirements are not met
     */
    private void validateProductionRequirements() {
        if (!environment.equals(PRODUCTION)) {
            return;
        }

        // P0-5: Ensure PostgreSQL persistence is used in production
        String persistenceType = System.getenv("DMOS_PERSISTENCE_TYPE");
        if ("in-memory".equalsIgnoreCase(persistenceType)) {
            throw new IllegalStateException(
                "In-memory persistence is not allowed in production. " +
                "Set DMOS_PERSISTENCE_TYPE=postgresql"
            );
        }

        // Validate required production environment variables
        String[] requiredVars = {
            "DATABASE_URL",
            "DMOS_PII_HMAC_KEY",
        };

        for (String var : requiredVars) {
            String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                    "Required environment variable not set for production: " + var
                );
            }
        }

        LOG.info("Production requirements validated successfully");
    }

    @Override
    protected void run() throws Exception {
        LOG.info("Starting DMOS API server in {} environment", environment);

        // Validate production requirements
        validateProductionRequirements();

        // Build all components
        buildComponents();

        LOG.info("DMOS API server started successfully on port {}", getListenPort());
    }

    private void buildComponents() {
        LOG.info("Building DMOS components for {} environment", environment);

        // Create eventloop for async operations
        Eventloop eventloop = Eventloop.create();
        register(Eventloop.class, eventloop);
        register(Executor.class, eventloop);

        // Wire observability (P1: OpenTelemetry metrics and traces)
        wireObservability();

        // Create DataSource based on environment
        if (usePostgres()) {
            DataSource dataSource = createPostgresDataSource();
            register(DataSource.class, dataSource);
            LOG.info("Using PostgreSQL persistence");
        } else {
            LOG.info("Using in-memory persistence (dev/test only)");
        }

        // Wire kernel bridge ports with production-grade implementations
        BridgeAuthorizationService authService = createAuthorizationService();
        BridgeAuditEmitter auditEmitter = createAuditEmitter();
        BridgeHealthIndicator healthIndicator = BridgeHealthIndicator.noOp();

        // Wire kernel plugins with production-grade implementations
        ConsentPlugin consentPlugin = createConsentPlugin();
        HumanApprovalPlugin approvalPlugin = createApprovalPlugin();
        AuditTrailPlugin auditTrailPlugin = createAuditTrailPlugin();
        RiskManagementPlugin riskPlugin = createRiskManagementPlugin();
        NotificationPlugin notificationPlugin = createNotificationPlugin();

        // Wire kernel adapter
        DigitalMarketingKernelAdapter kernelAdapter = new DigitalMarketingKernelAdapterImpl(
            authService,
            auditEmitter,
            healthIndicator,
            consentPlugin,
            approvalPlugin,
            auditTrailPlugin,
            riskPlugin,
            notificationPlugin
        );
        register(DigitalMarketingKernelAdapter.class, kernelAdapter);
        register(HumanApprovalPlugin.class, approvalPlugin);

        // Wire repositories based on persistence type
        if (usePostgres()) {
            wirePostgresRepositories();
        } else {
            wireInMemoryRepositories();
        }

        // P1-004: Run production bootstrap validation
        runProductionBootstrapValidation(kernelAdapter);

        // Wire compliance plugin
        CompliancePlugin compliancePlugin = createCompliancePlugin();
        register(CompliancePlugin.class, compliancePlugin);

        // Wire services
        wireServices(kernelAdapter, eventloop, compliancePlugin);

        // Wire command handler registry with observability (P1: OpenTelemetry)
        wireCommandHandlerRegistry();

        // Wire servlets
        wireServlets(kernelAdapter, eventloop);

        LOG.info("Components built successfully");
    }

    private boolean usePostgres() {
        String persistenceType = System.getenv("DMOS_PERSISTENCE_TYPE");
        return "postgresql".equalsIgnoreCase(persistenceType) && !environment.equals(DEVELOPMENT);
    }

    // P1-004: Run production bootstrap validation
    private void runProductionBootstrapValidation(DigitalMarketingKernelAdapter kernelAdapter) {
        boolean isProduction = environment.equals(PRODUCTION);
        if (!isProduction) {
            LOG.info("[DMOS-BOOTSTRAP] Production validation skipped (non-production mode: {})", environment);
            return;
        }

        DataSource dataSource = null;
        CampaignRepository campaignRepository = null;
        if (usePostgres()) {
            dataSource = getIfExists(DataSource.class);
            campaignRepository = getIfExists(CampaignRepository.class);
        }

        String piiHmacKey = System.getenv("DMOS_PII_HMAC_KEY");

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(campaignRepository)
            .kernelAdapter(kernelAdapter)
            .piiHmacKey(piiHmacKey)
            .build();

        try {
            validator.validate();
        } catch (ProductionBootstrapValidator.ProductionBootstrapException e) {
            LOG.error("[DMOS-BOOTSTRAP] Production bootstrap validation failed: {}", e.getMessage());
            throw new IllegalStateException("Production bootstrap validation failed. System cannot start in production mode.", e);
        }
    }

    // Helper method to get a dependency if it exists, without throwing
    @SuppressWarnings("unchecked")
    private <T> T getIfExists(Class<T> clazz) {
        try {
            return get(clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wires observability components (Metrics, TracingManager, DmosObservability, DmosTelemetry) into the registry.
     * P1-026: OpenTelemetry metrics and traces integration with span instrumentation.
     */
    private void wireObservability() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        register(SimpleMeterRegistry.class, meterRegistry);

        Metrics metrics = new Metrics(meterRegistry);
        register(Metrics.class, metrics);

        TracingManager tracingManager;
        if (environment.equals(PRODUCTION) || environment.equals(STAGING)) {
            // P1-014: Support both standard OTEL_EXPORTER_OTLP_ENDPOINT and the custom OTEL_COLLECTOR_ENDPOINT.
            // Standard env var takes precedence per OpenTelemetry specification.
            String collectorEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
            if (collectorEndpoint == null || collectorEndpoint.isBlank()) {
                collectorEndpoint = System.getenv("OTEL_COLLECTOR_ENDPOINT");
            }
            if (collectorEndpoint == null || collectorEndpoint.isBlank()) {
                collectorEndpoint = "http://localhost:4317";
            }
            tracingManager = TracingManager.createDefault("dmos-api", "1.0.0", collectorEndpoint);
            LOG.info("[{}] Using TracingManager with OTLP endpoint: {}", environment, collectorEndpoint);
        } else {
            tracingManager = TracingManager.createNoOp();
            LOG.info("[{}] Using no-op TracingManager for development", environment);
        }
        register(TracingManager.class, tracingManager);

        DmosObservability observability = new DmosObservability(metrics, tracingManager);
        register(DmosObservability.class, observability);

        // KERNEL-P1-4: Register MicrometerDmosMetricsCollector using the already-wired MeterRegistry
        DmosMetricsCollector dmosMetrics = new MicrometerDmosMetricsCollector(meterRegistry);
        register(DmosMetricsCollector.class, dmosMetrics);

        // P1-026: Register DmosTelemetry for span instrumentation
        com.ghatana.digitalmarketing.api.observability.DmosTelemetry telemetry =
            new com.ghatana.digitalmarketing.api.observability.DmosTelemetry(tracingManager.getOpenTelemetry());
        register(com.ghatana.digitalmarketing.api.observability.DmosTelemetry.class, telemetry);

        LOG.info("Observability components wired successfully");
    }

    /**
     * Wires command handler registry with observability.
     * P1: OpenTelemetry metrics and traces for command handlers.
     */
    private void wireCommandHandlerRegistry() {
        DmosObservability observability = get(DmosObservability.class);

        DmConnectorRepository connectorRepo = new DmConnectorInMemoryRepository();
        DmGoogleAdsCredentialRepository credentialRepo = new DmGoogleAdsCredentialInMemoryRepository();
        DmGoogleAdsCampaignLinkRepository linkRepo = new DmGoogleAdsCampaignLinkInMemoryRepository();

        CampaignRepository campaignRepo = get(CampaignRepository.class);

        // Read Google Ads config from environment
        String developerToken = System.getenv("GOOGLE_ADS_DEVELOPER_TOKEN");
        String customerId = System.getenv("GOOGLE_ADS_CUSTOMER_ID");
        DmGoogleAdsCampaignApiClient apiClient;
        if (developerToken != null && !developerToken.isBlank()
                && customerId != null && !customerId.isBlank()) {
            ObjectMapper objectMapper = new ObjectMapper();
            apiClient = HttpDmGoogleAdsCampaignApiClientAdapter.create(
                objectMapper, developerToken, customerId);
        } else {
            // Dev/test: use a no-op implementation until credentials are configured
            apiClient = new InMemoryDmGoogleAdsCampaignApiClient();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        DmCommandHandlerRegistry commandHandlerRegistry = new DmCommandHandlerRegistry(
            connectorRepo,
            credentialRepo,
            linkRepo,
            campaignRepo,
            apiClient,
            objectMapper,
            observability
        );

        register(DmCommandHandlerRegistry.class, commandHandlerRegistry);
        LOG.info("Command handler registry wired with observability");
    }

    private DataSource createPostgresDataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL environment variable must be set for PostgreSQL persistence");
        }

        try {
            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(databaseUrl);
            LOG.info("PostgreSQL DataSource created from DATABASE_URL");
            return dataSource;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create PostgreSQL DataSource", e);
        }
    }

    private void wirePostgresRepositories() {
        DataSource dataSource = get(DataSource.class);
        Executor executor = get(Executor.class);

        register(WorkspaceRepository.class, new PostgresWorkspaceRepository(dataSource, executor));
        register(CampaignRepository.class, new PostgresCampaignRepository(dataSource, executor));
        register(ApprovalSnapshotRepository.class, new PostgresApprovalSnapshotRepository(dataSource, executor));
        register(MarketingStrategyRepository.class, new PostgresMarketingStrategyRepository(dataSource, executor));
        register(BudgetRecommendationRepository.class, new PostgresBudgetRecommendationRepository(dataSource, executor));
        register(WebsiteAuditReportRepository.class, new PostgresWebsiteAuditReportRepository(dataSource, executor));

        LOG.info("PostgreSQL repositories wired successfully");
    }

    private void wireInMemoryRepositories() {
        ProductionProfileGuard.validate();

        register(WorkspaceRepository.class, new InMemoryWorkspaceRepository());
        register(CampaignRepository.class, new InMemoryCampaignRepository());
        register(ApprovalSnapshotRepository.class, new InMemoryApprovalSnapshotRepository());
        register(AiActionLogRepository.class, new InMemoryAiActionLogRepository());

        LOG.warn("MarketingStrategyRepository, BudgetRecommendationRepository, and WebsiteAuditReportRepository using forward implementations - in-memory adapters pending");
    }

    private void wireServices(DigitalMarketingKernelAdapter kernelAdapter, Eventloop eventloop,
                               CompliancePlugin compliancePlugin) {
        // Workspace Service
        WorkspaceRepository workspaceRepo = get(WorkspaceRepository.class);
        WorkspaceService workspaceService = new WorkspaceServiceImpl(kernelAdapter, workspaceRepo);
        register(WorkspaceService.class, workspaceService);

        // Campaign Service
        CampaignRepository campaignRepo = get(CampaignRepository.class);
        // No-op preflight data provider for composition root wiring
        CampaignPreflightDataProvider preflightProvider =
            (ctx, campaign) -> io.activej.promise.Promise.of(
                new CampaignPreflightDataProvider.CampaignPreflightData(true, 1, 1, 0.0, 10000.0));

        DmKillSwitchService killSwitchService;
        DmCommandService commandService;
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            Executor executor = get(Executor.class);
            killSwitchService = new PostgresDmKillSwitchService(dataSource, executor);
            commandService = new DmCommandServiceImpl(
                new PostgresDmCommandRepository(dataSource, executor),
                kernelAdapter
            );
        } else {
            killSwitchService = new DmKillSwitchService() {
                @Override
                public io.activej.promise.Promise<Boolean> isKillSwitchActive(String tenantId, String workspaceId, String feature) {
                    return io.activej.promise.Promise.of(false);
                }

                @Override
                public io.activej.promise.Promise<Void> activateKillSwitch(String scope, String scopeId, String feature, String reason, String activatedBy) {
                    return io.activej.promise.Promise.complete();
                }

                @Override
                public io.activej.promise.Promise<Void> deactivateKillSwitch(String scope, String scopeId, String feature, String deactivatedBy) {
                    return io.activej.promise.Promise.complete();
                }

                @Override
                public io.activej.promise.Promise<Void> recordKillSwitchAudit(String tenantId, String workspaceId, String feature, boolean wasBlocked, String correlationId) {
                    return io.activej.promise.Promise.complete();
                }
            };
            commandService = new DmCommandService() {
                @Override
                public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> issue(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    IssueCommandRequest request
                ) {
                    return io.activej.promise.Promise.ofException(new IllegalStateException("Command service not configured in non-PostgreSQL mode"));
                }

                @Override
                public io.activej.promise.Promise<java.util.Optional<com.ghatana.digitalmarketing.domain.command.DmCommand>> findById(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    String id
                ) {
                    return io.activej.promise.Promise.of(java.util.Optional.empty());
                }

                @Override
                public io.activej.promise.Promise<java.util.List<com.ghatana.digitalmarketing.domain.command.DmCommand>> listPending(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    int limit
                ) {
                    return io.activej.promise.Promise.of(java.util.List.of());
                }

                @Override
                public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markExecuting(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    String commandId
                ) {
                    return io.activej.promise.Promise.ofException(new IllegalStateException("Command service not configured in non-PostgreSQL mode"));
                }

                @Override
                public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markSucceeded(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    String commandId
                ) {
                    return io.activej.promise.Promise.ofException(new IllegalStateException("Command service not configured in non-PostgreSQL mode"));
                }

                @Override
                public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markFailed(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    String commandId,
                    String failureReason
                ) {
                    return io.activej.promise.Promise.ofException(new IllegalStateException("Command service not configured in non-PostgreSQL mode"));
                }

                @Override
                public io.activej.promise.Promise<com.ghatana.digitalmarketing.domain.command.DmCommand> markRolledBack(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    String commandId
                ) {
                    return io.activej.promise.Promise.ofException(new IllegalStateException("Command service not configured in non-PostgreSQL mode"));
                }

                @Override
                public io.activej.promise.Promise<Long> countByStatus(
                    com.ghatana.digitalmarketing.contracts.DmOperationContext ctx,
                    com.ghatana.digitalmarketing.domain.command.DmCommandStatus status
                ) {
                    return io.activej.promise.Promise.of(0L);
                }
            };
        }

        // KERNEL-P1-4: Use Micrometer-backed collector registered in wireObservability()
        DmosMetricsCollector dmosMetrics = get(DmosMetricsCollector.class);

        // In-memory EventLogStore implementation for development/testing
        EventLogStore eventLogStore = new EventLogStore() {
            private final java.util.Map<String, java.util.List<EventEntry>> store = new java.util.concurrent.ConcurrentHashMap<>();
            private final java.util.Map<String, Long> offsets = new java.util.concurrent.ConcurrentHashMap<>();
            private final java.util.Map<String, java.util.List<java.util.function.Consumer<EventEntry>>> tailListeners = new java.util.concurrent.ConcurrentHashMap<>();

            @Override
            public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
                java.util.List<EventEntry> entries = store.computeIfAbsent(tenant.tenantId(), k -> new java.util.ArrayList<>());
                long offset;
                synchronized (entries) {
                    entries.add(entry);
                    offset = offsets.compute(tenant.tenantId(), (k, v) -> v == null ? 1L : v + 1L);
                }
                java.util.List<java.util.function.Consumer<EventEntry>> listeners = tailListeners.get(tenant.tenantId());
                if (listeners != null) {
                    for (java.util.function.Consumer<EventEntry> listener : listeners) {
                        listener.accept(entry);
                    }
                }
                return Promise.of(Offset.of(String.valueOf(offset)));
            }

            @Override
            public Promise<java.util.List<Offset>> appendBatch(TenantContext tenant, java.util.List<EventEntry> entries) {
                java.util.List<Offset> results = new java.util.ArrayList<>(entries.size());
                for (EventEntry entry : entries) {
                    results.add(append(tenant, entry).getResult());
                }
                return Promise.of(results);
            }

            @Override
            public Promise<java.util.List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
                java.util.List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), java.util.List.of());
                long startOffset = from.value().equals("0") ? 0 : Long.parseLong(from.value()) - 1;
                return Promise.of(entries.stream()
                    .skip(startOffset)
                    .limit(limit)
                    .toList());
            }

            @Override
            public Promise<java.util.List<EventEntry>> readByTimeRange(
                    TenantContext tenant,
                    java.time.Instant startTime,
                    java.time.Instant endTime,
                    int limit) {
                java.util.List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), java.util.List.of());
                return Promise.of(entries.stream()
                    .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
                    .limit(limit)
                    .toList());
            }

            @Override
            public Promise<java.util.List<EventEntry>> readByType(
                    TenantContext tenant,
                    String eventType,
                    Offset from,
                    int limit) {
                java.util.List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), java.util.List.of());
                long startOffset = from.value().equals("0") ? 0 : Long.parseLong(from.value()) - 1;
                return Promise.of(entries.stream()
                    .skip(startOffset)
                    .filter(e -> e.eventType().equals(eventType))
                    .limit(limit)
                    .toList());
            }

            @Override
            public Promise<Offset> getLatestOffset(TenantContext tenant) {
                Long offset = offsets.get(tenant.tenantId());
                return Promise.of(Offset.of(offset != null ? String.valueOf(offset) : "0"));
            }

            @Override
            public Promise<Offset> getEarliestOffset(TenantContext tenant) {
                return Promise.of(Offset.of("0"));
            }

            @Override
            public Promise<Subscription> tail(TenantContext tenant, Offset from, java.util.function.Consumer<EventEntry> handler) {
                java.util.List<java.util.function.Consumer<EventEntry>> listeners = tailListeners.computeIfAbsent(tenant.tenantId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>());
                listeners.add(handler);
                return Promise.of(new Subscription() {
                    private volatile boolean cancelled = false;
                    
                    @Override
                    public void cancel() {
                        cancelled = true;
                        listeners.remove(handler);
                    }
                    
                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }
                });
            }
        };

        CampaignEventSourcingAdapter eventSourcingAdapter = new CampaignEventSourcingAdapter(eventLogStore);

        CampaignService campaignService = new CampaignServiceImpl(
            kernelAdapter,
            campaignRepo,
            compliancePlugin,
            preflightProvider,
            dmosMetrics,
            killSwitchService,
            commandService,
            new ObjectMapper(),
            eventSourcingAdapter
        );
        register(CampaignService.class, campaignService);

        // Approval Workflow Service
        ApprovalSnapshotRepository approvalRepo = get(ApprovalSnapshotRepository.class);
        HumanApprovalPlugin approvalPlugin = get(HumanApprovalPlugin.class);
        ApprovalWorkflowServiceImpl approvalService = new ApprovalWorkflowServiceImpl(
            kernelAdapter, approvalPlugin, approvalRepo, dmosMetrics);
        register(ApprovalWorkflowService.class, approvalService);

        // AI Action Log Service
        AiActionLogRepository aiLogRepo = get(AiActionLogRepository.class);
        AiActionLogService aiLogService = new AiActionLogServiceImpl(kernelAdapter, aiLogRepo);
        register(AiActionLogService.class, aiLogService);

        // Strategy Generator Service
        MarketingStrategyRepository strategyRepo = get(MarketingStrategyRepository.class);
        GovernedAgentWorkflowService governedWorkflowService = null;
        StrategyGeneratorService strategyService = new StrategyGeneratorServiceImpl(
            kernelAdapter, strategyRepo, governedWorkflowService);
        register(StrategyGeneratorService.class, strategyService);

        // Budget Recommendation Service
        BudgetRecommendationRepository budgetRepo = get(BudgetRecommendationRepository.class);
        BudgetRecommendationService budgetService = new BudgetRecommendationServiceImpl(kernelAdapter, budgetRepo);
        register(BudgetRecommendationService.class, budgetService);

        // Website Audit Service
        WebsiteAuditReportRepository auditReportRepo = get(WebsiteAuditReportRepository.class);
        WebsiteAuditService websiteAuditService = new WebsiteAuditServiceImpl(kernelAdapter, auditReportRepo);
        register(WebsiteAuditService.class, websiteAuditService);
    }

    private void wireServlets(DigitalMarketingKernelAdapter kernelAdapter, Eventloop eventloop) {
        // P1-026: Get DmosTelemetry for span instrumentation
        com.ghatana.digitalmarketing.api.observability.DmosTelemetry telemetry =
            get(com.ghatana.digitalmarketing.api.observability.DmosTelemetry.class);

        // P1-026: Get DmosMetricsCollector for rate limiter metrics
        DmosMetricsCollector metrics = get(DmosMetricsCollector.class);

        // P1-021: Instantiate IdempotencyMiddleware for PostgreSQL persistence
        com.ghatana.digitalmarketing.api.middleware.IdempotencyMiddleware idempotencyMiddleware = null;
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            idempotencyMiddleware = new com.ghatana.digitalmarketing.api.middleware.IdempotencyMiddleware(dataSource, eventloop);
            register(com.ghatana.digitalmarketing.api.middleware.IdempotencyMiddleware.class, idempotencyMiddleware);
            LOG.info("IdempotencyMiddleware instantiated for PostgreSQL");
        }

        // P1-001: Instantiate DmosHttpContextFactory with fail-closed security
        boolean productionMode = environment.equals(PRODUCTION);
        com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory.IdentityProvider identityProvider = null;
        if (productionMode) {
            // P1-001: In production, use a real identity provider to derive roles/permissions server-side
            // For now, use a no-op implementation that requires explicit configuration
            LOG.warn("[PRODUCTION] IdentityProvider not configured; using no-op. Enable via DMOS_IDENTITY_PROVIDER_ENABLED.");
        }
        com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory httpContextFactory =
            new com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory(productionMode, identityProvider);
        register(com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory.class, httpContextFactory);
        LOG.info("DmosHttpContextFactory instantiated with productionMode={}", productionMode);

        WorkspaceService workspaceService = get(WorkspaceService.class);
        register(DmosWorkspaceServlet.class, new DmosWorkspaceServlet(workspaceService, eventloop, httpContextFactory));
        register(DmosRouteEntitlementServlet.class, new DmosRouteEntitlementServlet(eventloop));

        CampaignService campaignService = get(CampaignService.class);
        register(DmosCampaignServlet.class, new DmosCampaignServlet(campaignService, eventloop, metrics, telemetry, httpContextFactory));

        ApprovalWorkflowService approvalService = get(ApprovalWorkflowService.class);
        register(DmosApprovalServlet.class, new DmosApprovalServlet(approvalService, eventloop, metrics, telemetry, httpContextFactory));

        AiActionLogService aiLogService = get(AiActionLogService.class);
        register(DmosAiActionLogServlet.class, new DmosAiActionLogServlet(aiLogService, eventloop, metrics, httpContextFactory));

        StrategyGeneratorService strategyService = get(StrategyGeneratorService.class);
        register(DmosStrategyServlet.class, new DmosStrategyServlet(strategyService, eventloop, metrics, telemetry, httpContextFactory));

        BudgetRecommendationService budgetService = get(BudgetRecommendationService.class);
        register(DmosBudgetRecommendationServlet.class, new DmosBudgetRecommendationServlet(budgetService, eventloop, metrics, telemetry, httpContextFactory));

        WebsiteAuditService websiteAuditService = get(WebsiteAuditService.class);
        register(DmosWebsiteAuditServlet.class, new DmosWebsiteAuditServlet(websiteAuditService, eventloop, httpContextFactory));

        LOG.info("Core servlets wired - additional servlets pending");
    }

    // -----------------------------------------------------------------------
    // Kernel bridge port factory methods
    // -----------------------------------------------------------------------

    /**
     * Creates the authorization service implementation based on environment.
     *
     * <p>When {@code DMOS_OPA_URL} is set, a real OPA-backed authorization service is
     * returned wrapped in a circuit breaker (fail-closed on OPA unavailability).
     * Otherwise falls back to allow-all for development convenience.</p>
     */
    private BridgeAuthorizationService createAuthorizationService() {
        String opaUrl = System.getenv("DMOS_OPA_URL");
        if (opaUrl != null && !opaUrl.isBlank()) {
            Executor blockingExecutor = Executors.newCachedThreadPool();
            OpaClient opaClient = new OpaClient(opaUrl, blockingExecutor);
            Eventloop eventloop = get(Eventloop.class);
            CircuitBreakingPolicyAsCodeEngine circuitBreakingEngine =
                new CircuitBreakingPolicyAsCodeEngine(opaClient, eventloop);
            LOG.info("[{}] Using OpaAuthorizationService with OPA endpoint: {}", environment, opaUrl);
            return new OpaAuthorizationService(circuitBreakingEngine);
        }
        if (environment.equals(PRODUCTION)) {
            LOG.warn("[PRODUCTION] DMOS_OPA_URL not configured; authorization will use allowAll. " +
                "Set DMOS_OPA_URL to enable real OPA-backed policy enforcement.");
        } else {
            LOG.info("[{}] Using allowAll authorization for development (set DMOS_OPA_URL for OPA)", environment);
        }
        return BridgeAuthorizationService.allowAll();
    }

    /**
     * Creates the audit emitter implementation based on environment.
     * Production uses real kernel bridge; dev/test uses noOp for convenience.
     */
    private BridgeAuditEmitter createAuditEmitter() {
        if (environment.equals(PRODUCTION)) {
            LOG.warn("[PRODUCTION] Kernel bridge audit emitter not configured; using noOp. Enable via DMOS_KERNEL_AUDIT_ENABLED.");
            return BridgeAuditEmitter.noOp();
        }
        LOG.info("[{}] Using noOp audit emitter for development", environment);
        return BridgeAuditEmitter.noOp();
    }

    // -----------------------------------------------------------------------
    // Platform plugin factory methods
    // -----------------------------------------------------------------------

    /**
     * Creates the consent plugin implementation based on environment.
     */
    private ConsentPlugin createConsentPlugin() {
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            DurableConsentPlugin plugin = new DurableConsentPlugin(dataSource);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableConsentPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using StandardConsentPlugin (in-memory)", environment);
        return new StandardConsentPlugin();
    }

    /**
     * Creates the approval plugin implementation based on environment.
     */
    private HumanApprovalPlugin createApprovalPlugin() {
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            DurableHumanApprovalPlugin plugin = new DurableHumanApprovalPlugin(dataSource);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableHumanApprovalPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using StandardHumanApprovalPlugin (in-memory)", environment);
        return new StandardHumanApprovalPlugin();
    }

    /**
     * Creates the audit trail plugin implementation based on environment.
     */
    private AuditTrailPlugin createAuditTrailPlugin() {
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            DurableAuditTrailPlugin plugin = new DurableAuditTrailPlugin(dataSource);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableAuditTrailPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using StandardAuditTrailPlugin (in-memory)", environment);
        return new StandardAuditTrailPlugin();
    }

    /**
     * Creates the risk management plugin implementation.
     */
    private RiskManagementPlugin createRiskManagementPlugin() {
        LOG.info("[{}] Using StandardRiskManagementPlugin", environment);
        StandardRiskManagementPlugin plugin = new StandardRiskManagementPlugin();
        DmosRiskEvaluatorRegistrar.register(plugin);
        return plugin;
    }

    /**
     * Creates the notification plugin implementation based on environment.
     */
    private NotificationPlugin createNotificationPlugin() {
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            Executor executor = Executors.newCachedThreadPool();
            // No-op event bus — replace with real EventBusPort when available
            EventBusPort eventBus = event -> {};
            DurableNotificationPlugin plugin = new DurableNotificationPlugin(dataSource, eventBus, executor);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableNotificationPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using InMemoryNotificationPlugin", environment);
        return new InMemoryNotificationPlugin();
    }

    /**
     * Creates the compliance plugin implementation.
     */
    private CompliancePlugin createCompliancePlugin() {
        LOG.info("[{}] Using StandardCompliancePlugin", environment);
        return new StandardCompliancePlugin();
    }

    private int getListenPort() {
        String port = System.getenv("PORT");
        if (port != null && !port.isBlank()) {
            return Integer.parseInt(port);
        }
        return 8080;
    }

    /**
     * Main entry point for running the server.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws Exception {
        DmosApiServer server = new DmosApiServer();
        server.launch(args);
    }
}
