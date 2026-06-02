package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.DmosFeatureFlags;
import com.ghatana.digitalmarketing.application.ai.GovernedAgentWorkflowService;
import com.ghatana.digitalmarketing.application.ai.KernelAgentOrchestrationAdapter;
import com.ghatana.digitalmarketing.application.ai.AiPolicyCheckServiceImpl;
import com.ghatana.digitalmarketing.application.analytics.DashboardSummaryService;
import com.ghatana.digitalmarketing.application.analytics.DashboardSummaryServiceImpl;
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
import com.ghatana.digitalmarketing.domain.campaign.CampaignTransitionService;
import com.ghatana.digitalmarketing.api.security.DmosJwtIdentityProvider;
import com.ghatana.digitalmarketing.bridge.CampaignEventSourcingAdapter;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import io.activej.promise.Promise;
import com.ghatana.digitalmarketing.application.command.DmCommandHandlerRegistry;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.application.command.DmCommandServiceImpl;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadinessService;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadinessServiceImpl;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.metrics.MicrometerDmosMetricsCollector;
import com.ghatana.digitalmarketing.application.strategy.MarketingStrategyRepository;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorServiceImpl;
import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.application.suppression.SuppressionServiceImpl;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogService;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogServiceImpl;
import com.ghatana.digitalmarketing.application.privacy.ContactEncryptionService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceServiceImpl;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapterImpl;
import com.ghatana.digitalmarketing.bridge.DmosRiskEvaluatorRegistrar;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.connector.googleads.HttpDmGoogleAdsCampaignApiClientAdapter;
import com.ghatana.digitalmarketing.connector.googleads.EphemeralDmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.infra.ProductionProfileGuard;
import com.ghatana.digitalmarketing.infra.approval.EphemeralApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.infra.audit.EphemeralWebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.infra.budget.EphemeralBudgetRecommendationRepository;
import com.ghatana.digitalmarketing.infra.campaign.EphemeralCampaignRepository;
import com.ghatana.digitalmarketing.infra.connector.DmConnectorEphemeralRepository;
import com.ghatana.digitalmarketing.infra.googleads.DmGoogleAdsCampaignLinkEphemeralRepository;
import com.ghatana.digitalmarketing.infra.googleads.DmGoogleAdsCredentialEphemeralRepository;
import com.ghatana.digitalmarketing.infra.research.EphemeralCompetitorResearchRepository;
import com.ghatana.digitalmarketing.infra.suppression.EphemeralSuppressionRepository;
import com.ghatana.digitalmarketing.infra.strategy.EphemeralMarketingStrategyRepository;
import com.ghatana.digitalmarketing.infra.transparency.EphemeralAiActionLogRepository;
import com.ghatana.digitalmarketing.infra.workspace.EphemeralWorkspaceRepository;
import com.ghatana.digitalmarketing.persistence.approval.PostgresApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.persistence.audit.PostgresWebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.persistence.budget.PostgresBudgetRecommendationRepository;
import com.ghatana.digitalmarketing.persistence.campaign.PostgresCampaignRepository;
import com.ghatana.digitalmarketing.persistence.connector.PostgresDmConnectorRepository;
import com.ghatana.digitalmarketing.persistence.eventstore.PostgresEventLogStore;
import com.ghatana.digitalmarketing.persistence.googleads.PostgresDmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.persistence.googleads.PostgresDmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.persistence.preflight.PostgresCampaignPreflightDataProvider;
import com.ghatana.digitalmarketing.persistence.strategy.PostgresMarketingStrategyRepository;
import com.ghatana.digitalmarketing.persistence.suppression.PostgresSuppressionRepository;
import com.ghatana.digitalmarketing.persistence.transparency.PostgresAiActionLogRepository;
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
import com.ghatana.plugin.risk.RiskManagementPlugin;
import com.ghatana.plugin.risk.impl.StandardRiskManagementPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.ProductEntitlementContext;
import com.ghatana.platform.http.security.RoleEvaluator;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.consent.impl.DurableConsentPlugin;
import com.ghatana.plugin.consent.impl.StandardConsentPlugin;
import com.ghatana.plugin.featureflag.FeatureFlagPlugin;
import com.ghatana.plugin.notification.impl.DurableNotificationPlugin;
import com.ghatana.plugin.notification.impl.EphemeralNotificationPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.governance.DmKillSwitchService;
import com.ghatana.platform.security.encryption.HashingService;
import com.ghatana.platform.security.encryption.impl.DefaultHashingProvider;
import com.ghatana.platform.security.port.HashingPort;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import io.activej.launcher.Launcher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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

    static boolean isStrictAuthEnvironment(String environment) {
        return PRODUCTION.equals(environment) || STAGING.equals(environment);
    }

    private String resolvePiiHmacKey() {
        String key = System.getenv("DMOS_PII_HMAC_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        if (environment.equals(PRODUCTION)) {
            throw new IllegalStateException("DMOS_PII_HMAC_KEY is required for production hashing");
        }
        return "development-dmos-pii-hmac-key";
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
            "DMOS_CONTACT_ENCRYPTION_KEY",
            "DMOS_OPA_URL",
        };

        for (String var : requiredVars) {
            String value = System.getenv(var);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                    "Required environment variable not set for production: " + var
                );
            }
        }

        String otelEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        String secondaryOtelEndpoint = System.getenv("OTEL_COLLECTOR_ENDPOINT");
        if ((otelEndpoint == null || otelEndpoint.isBlank())
            && (secondaryOtelEndpoint == null || secondaryOtelEndpoint.isBlank())) {
            throw new IllegalStateException(
                "OTEL_EXPORTER_OTLP_ENDPOINT or OTEL_COLLECTOR_ENDPOINT is required for production telemetry"
            );
        }

        String governedAiEnabled = System.getenv("DMOS_GOVERNED_AI_ENABLED");
        if ("false".equalsIgnoreCase(governedAiEnabled)) {
            throw new IllegalStateException(
                "DMOS_GOVERNED_AI_ENABLED=false is not allowed in production. Governed AI artifact workflow is mandatory."
            );
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

        startHttpServer(get(Eventloop.class));
    }

    private void startHttpServer(Eventloop eventloop) throws Exception {
        int listenPort = getListenPort();
        HttpServer server = HttpServer.builder(eventloop, buildHttpRouter(eventloop))
            .withListenPort(listenPort)
            .build();

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping DMOS API server");
            try {
                server.close();
                get(DigitalMarketingKernelAdapter.class).stop();
            } catch (Exception exception) {
                LOG.warn("DMOS API server close failed", exception);
            } finally {
                eventloop.keepAlive(false);
                shutdownLatch.countDown();
            }
        }, "dmos-api-shutdown"));

        eventloop.keepAlive(true);
        eventloop.execute(() -> {
            try {
                server.listen();
                LOG.info("DMOS API server started successfully on port {}", listenPort);
            } catch (Exception exception) {
                LOG.error("Failed to start DMOS API server", exception);
                shutdownLatch.countDown();
                throw new RuntimeException("Failed to start DMOS API server", exception);
            }
        });
        eventloop.run();
        shutdownLatch.await();
    }

    private AsyncServlet buildHttpRouter(Eventloop eventloop) {
        AsyncServlet workspace = get(DmosWorkspaceServlet.class).getServlet();
        AsyncServlet routeEntitlements = get(DmosRouteEntitlementServlet.class).getServlet();
        AsyncServlet dashboard = get(DmosDashboardServlet.class).getServlet();
        AsyncServlet releaseReadiness = get(DmosReleaseReadinessServlet.class).getServlet();
        AsyncServlet campaigns = get(DmosCampaignServlet.class).getServlet();
        AsyncServlet connectors = get(DmosConnectorReadinessServlet.class).getServlet();
        AsyncServlet approvals = get(DmosApprovalServlet.class).routes();
        AsyncServlet aiActions = get(DmosAiActionLogServlet.class).routes();
        AsyncServlet strategy = get(DmosStrategyServlet.class).getServlet();
        AsyncServlet budget = get(DmosBudgetRecommendationServlet.class).getServlet();
        AsyncServlet audit = get(DmosWebsiteAuditServlet.class).getServlet();
        AsyncServlet consent = get(DmosConsentServlet.class).getServlet();
        AsyncServlet boundaryReporting = get(DmosBoundaryReportingServlet.class).getServlet();
        DmosHealthServlet healthServlet = getIfExists(DmosHealthServlet.class);
        AsyncServlet health = healthServlet == null ? null : healthServlet.getServlet();

        AsyncServlet routed = request -> {
            String path = request.getPath();
            if (request.getMethod() == HttpMethod.OPTIONS) {
                return Promise.of(corsPreflight(request.getHeader(HttpHeaders.of("Origin"))));
            }
            if (path.equals("/health") || path.startsWith("/health/")) {
                if (health != null) {
                    return health.serve(request);
                }
                if ("/health/live".equals(path)) {
                    return HttpResponse.ok200().withPlainText("ok").toPromise();
                }
                if ("/health/ready".equals(path)) {
                    return HttpResponse.ok200().withPlainText("ready").toPromise();
                }
                if ("/health".equals(path) || "/health/startup".equals(path)) {
                    return Promise.of(fallbackHealthResponse());
                }
            }
            try {
                Promise<HttpResponse> response;
                if (path.equals("/v1/route-entitlements")) {
                    response = routeEntitlements.serve(request);
                } else if (path.contains("/release-readiness")) {
                    response = releaseReadiness.serve(request);
                } else if (path.contains("/dashboard")) {
                    response = dashboard.serve(request);
                } else if (path.contains("/connectors")) {
                    response = connectors.serve(request);
                } else if (path.contains("/campaigns")) {
                    response = campaigns.serve(request);
                } else if (path.contains("/approvals")) {
                    response = approvals.serve(request);
                } else if (path.contains("/ai-actions")) {
                    response = aiActions.serve(request);
                } else if (path.contains("/strategy")) {
                    response = strategy.serve(request);
                } else if (path.contains("/budget")) {
                    response = budget.serve(request);
                } else if (path.contains("/funnel-analytics") || path.contains("/attribution") || path.contains("/roi-roas")) {
                    response = boundaryReporting.serve(request);
                } else if (path.contains("/audit")) {
                    response = audit.serve(request);
                } else if (path.contains("/consent") || path.contains("/suppression") || path.contains("/unsubscribe")) {
                    response = consent.serve(request);
                } else if (path.startsWith("/v1/workspaces")) {
                    response = workspace.serve(request);
                } else {
                    response = Promise.of(HttpResponse.ofCode(404).build());
                }
                return response;
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        };
        if (usePostgres()) {
            return get(com.ghatana.digitalmarketing.api.middleware.IdempotencyMiddleware.class).wrap(routed);
        }
        return routed;
    }

    private static HttpResponse corsPreflight(String origin) {
        String allowOrigin = origin == null || origin.isBlank() ? "*" : origin;
        return HttpResponse.ofCode(204)
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), allowOrigin)
            .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), "GET,POST,PUT,PATCH,DELETE,OPTIONS")
            .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"),
                "Authorization,Content-Type,X-Tenant-ID,X-Principal-ID,X-Session-ID,X-Roles,X-Permissions,X-Idempotency-Key,X-Correlation-ID")
            .withHeader(HttpHeaders.of("Access-Control-Max-Age"), "600")
            .withHeader(HttpHeaders.of("Vary"), "Origin")
            .build();
    }

    private HttpResponse fallbackHealthResponse() {
        try {
            Map<String, DmosBridgeHealthIndicator.BridgeStatus> bridgeStatus =
                get(DmosBridgeHealthIndicator.class).snapshot();
            boolean bridgesHealthy = bridgeStatus.values().stream()
                .noneMatch(status -> "DOWN".equalsIgnoreCase(status.status()));
            Map<String, Object> body = Map.of(
                "status", bridgesHealthy ? "UP" : "DOWN",
                "timestamp", java.time.Instant.now().toString(),
                "checks", Map.of(
                    "kernelBridge", Map.of(
                        "status", bridgesHealthy ? "UP" : "DOWN",
                        "component", "BridgeHealthIndicator",
                        "bridges", bridgeStatus
                    ),
                    "eventloop", Map.of(
                        "status", "UP",
                        "component", "ActiveJ Eventloop"
                    )
                )
            );
            return HttpResponse.ofCode(bridgesHealthy ? 200 : 503)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .withBody(new ObjectMapper().writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(500).withPlainText("health serialization failed").build();
        }
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
        DmosBridgeHealthIndicator healthIndicator = new DmosBridgeHealthIndicator();
        register(DmosBridgeHealthIndicator.class, healthIndicator);
        register(BridgeHealthIndicator.class, healthIndicator);

        // Wire kernel plugins with production-grade implementations
        ConsentPlugin consentPlugin = createConsentPlugin();
        HumanApprovalPlugin approvalPlugin = createApprovalPlugin();
        AuditTrailPlugin auditTrailPlugin = createAuditTrailPlugin();
        RiskManagementPlugin riskPlugin = createRiskManagementPlugin();
        NotificationPlugin notificationPlugin = createNotificationPlugin();
        FeatureFlagPlugin featureFlagPlugin = new EnvironmentFeatureFlagPlugin();

        // Wire kernel adapter
        DigitalMarketingKernelAdapter kernelAdapter = new DigitalMarketingKernelAdapterImpl(
            authService,
            auditEmitter,
            healthIndicator,
            consentPlugin,
            approvalPlugin,
            auditTrailPlugin,
            riskPlugin,
            notificationPlugin,
            featureFlagPlugin,
            environment.equals(PRODUCTION)
        );
        kernelAdapter.start();
        register(DigitalMarketingKernelAdapter.class, kernelAdapter);
        register(ConsentPlugin.class, consentPlugin);
        register(HumanApprovalPlugin.class, approvalPlugin);
        register(FeatureFlagPlugin.class, featureFlagPlugin);

        // Wire repositories based on persistence type
        if (usePostgres()) {
            wirePostgresRepositories();
        } else {
            wireEphemeralRepositories();
        }

        // Wire compliance plugin
        CompliancePlugin compliancePlugin = createCompliancePlugin();
        register(CompliancePlugin.class, compliancePlugin);

        // Wire services
        wireServices(kernelAdapter, eventloop, compliancePlugin);

        // Wire command handler registry with observability (P1: OpenTelemetry)
        wireCommandHandlerRegistry();

        // P1-004: Run production bootstrap validation after service and command wiring
        runProductionBootstrapValidation(kernelAdapter);

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
        String contactEncryptionKey = System.getenv("DMOS_CONTACT_ENCRYPTION_KEY");
        DmCommandService commandService = getIfExists(DmCommandService.class);

        ProductionBootstrapValidator validator = new ProductionBootstrapValidator.Builder()
            .isProduction(true)
            .dataSource(dataSource)
            .campaignRepository(campaignRepository)
            .kernelAdapter(kernelAdapter)
            .piiHmacKey(piiHmacKey)
            .contactEncryptionKey(contactEncryptionKey)
            .googleAdsOutboxExecutor(commandService)
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
                if (environment.equals(PRODUCTION)) {
                    throw new IllegalStateException("Production telemetry endpoint is required");
                }
                collectorEndpoint = "http://localhost:4317";
            }
            tracingManager = TracingManager.createDefault("dmos-api", "1.0.0", collectorEndpoint);
            LOG.info("[{}] Using TracingManager with OTLP endpoint: {}", environment, collectorEndpoint);
        } else {
            tracingManager = TracingManager.createNoOp();
            LOG.info("[{}] Using local TracingManager for development", environment);
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
        boolean productionMode = environment.equals(PRODUCTION);

        DmConnectorRepository connectorRepo;
        DmGoogleAdsCredentialRepository credentialRepo;
        DmGoogleAdsCampaignLinkRepository linkRepo;
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            Executor executor = get(Executor.class);
            connectorRepo = new PostgresDmConnectorRepository(dataSource, executor);
            credentialRepo = new PostgresDmGoogleAdsCredentialRepository(
                dataSource,
                new ContactEncryptionService(),
                executor);
            linkRepo = new PostgresDmGoogleAdsCampaignLinkRepository(dataSource, executor);
        } else {
            connectorRepo = new DmConnectorEphemeralRepository();
            credentialRepo = new DmGoogleAdsCredentialEphemeralRepository();
            linkRepo = new DmGoogleAdsCampaignLinkEphemeralRepository();
        }

        CampaignRepository campaignRepo = get(CampaignRepository.class);

        // Read Google Ads config from environment
        boolean googleAdsEnabled = Boolean.parseBoolean(System.getenv("DMOS_GOOGLE_ADS_ENABLED"));
        String developerToken = System.getenv("GOOGLE_ADS_DEVELOPER_TOKEN");
        String customerId = System.getenv("GOOGLE_ADS_CUSTOMER_ID");
        DmGoogleAdsCampaignApiClient apiClient;
        DmosBridgeHealthIndicator bridgeHealthIndicator = get(DmosBridgeHealthIndicator.class);
        boolean hasCredentials = developerToken != null && !developerToken.isBlank()
            && customerId != null && !customerId.isBlank();
        if (hasCredentials) {
            ObjectMapper objectMapper = new ObjectMapper();
            apiClient = HttpDmGoogleAdsCampaignApiClientAdapter.create(
                objectMapper, developerToken, customerId);
            bridgeHealthIndicator.reportHealthy("connector.google-ads");
        } else if (productionMode && googleAdsEnabled) {
            bridgeHealthIndicator.reportUnhealthy("connector.google-ads", "enabled but Google Ads credentials are missing");
            throw new IllegalStateException(
                "Google Ads connector is enabled in production but credentials are missing. " +
                    "Set GOOGLE_ADS_DEVELOPER_TOKEN and GOOGLE_ADS_CUSTOMER_ID or disable DMOS_GOOGLE_ADS_ENABLED.");
        } else if (productionMode) {
            // Fail closed in production when connector is disabled.
            bridgeHealthIndicator.reportDegraded("connector.google-ads", "disabled in production");
            apiClient = new DmGoogleAdsCampaignApiClient() {
                @Override
                public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
                    return Promise.ofException(new IllegalStateException("Google Ads connector is disabled in production"));
                }

                @Override
                public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
                    return Promise.ofException(new IllegalStateException("Google Ads connector is disabled in production"));
                }

                @Override
                public Promise<GoogleAdsConnectorReadinessState> checkReadiness(String accessToken) {
                    return Promise.of(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED);
                }
            };
        } else {
            // Dev/test uses an in-memory connector until credentials are configured.
            apiClient = new EphemeralDmGoogleAdsCampaignApiClient();
            bridgeHealthIndicator.reportDegraded("connector.google-ads", "ephemeral development connector");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        DmGoogleAdsConnectorReadinessService googleAdsReadinessService =
            new DmGoogleAdsConnectorReadinessServiceImpl(
                connectorRepo,
                credentialRepo,
                apiClient,
                get(DigitalMarketingKernelAdapter.class)
            );

        register(DmConnectorRepository.class, connectorRepo);
        register(DmGoogleAdsCredentialRepository.class, credentialRepo);
        register(DmGoogleAdsCampaignLinkRepository.class, linkRepo);
        register(DmGoogleAdsCampaignApiClient.class, apiClient);
        register(DmGoogleAdsConnectorReadinessService.class, googleAdsReadinessService);

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
        register(AiActionLogRepository.class, new PostgresAiActionLogRepository(dataSource, executor));
        register(MarketingStrategyRepository.class, new PostgresMarketingStrategyRepository(dataSource, executor));
        register(BudgetRecommendationRepository.class, new PostgresBudgetRecommendationRepository(dataSource, executor));
        register(WebsiteAuditReportRepository.class, new PostgresWebsiteAuditReportRepository(dataSource, executor));
        register(SuppressionRepository.class, new PostgresSuppressionRepository(dataSource, executor));

        LOG.info("PostgreSQL repositories wired successfully");
    }

    private void wireEphemeralRepositories() {
        ProductionProfileGuard.validate();

        register(WorkspaceRepository.class, new EphemeralWorkspaceRepository());
        register(CampaignRepository.class, new EphemeralCampaignRepository());
        register(ApprovalSnapshotRepository.class, new EphemeralApprovalSnapshotRepository());
        register(AiActionLogRepository.class, new EphemeralAiActionLogRepository());
        register(SuppressionRepository.class, new EphemeralSuppressionRepository());
        register(MarketingStrategyRepository.class, new EphemeralMarketingStrategyRepository());
        register(BudgetRecommendationRepository.class, new EphemeralBudgetRecommendationRepository());
        register(WebsiteAuditReportRepository.class, new EphemeralWebsiteAuditReportRepository());

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
        CampaignPreflightDataProvider preflightProvider;
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            Executor executor = get(Executor.class);
            preflightProvider = new PostgresCampaignPreflightDataProvider(dataSource, executor);
        } else {
            // Development fallback to keep local developer loops ergonomic.
            preflightProvider =
                (ctx, campaign) -> io.activej.promise.Promise.of(
                    new CampaignPreflightDataProvider.CampaignPreflightData(
                        true,
                        1,
                        1,
                        0.0,
                        10000.0,
                        true,
                        "campaign-activation"
                    ));
        }

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

        EventLogStore eventLogStore;
        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            Executor executor = get(Executor.class);
            eventLogStore = new PostgresEventLogStore(dataSource, executor);
        } else {
            // Development-only in-memory EventLogStore implementation.
            eventLogStore = new EventLogStore() {
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
                    private volatile SubscriptionState state = SubscriptionState.ACTIVE;
                    private volatile java.util.function.Consumer<Throwable> errorHandler;
                    
                    @Override
                    public void cancel() {
                        cancelled = true;
                        state = SubscriptionState.CLOSED;
                        listeners.remove(handler);
                    }
                    
                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }

                    @Override
                    public SubscriptionState getState() {
                        return state;
                    }

                    @Override
                    public void setErrorHandler(java.util.function.Consumer<Throwable> errorHandler) {
                        this.errorHandler = errorHandler;
                    }
                });
            }
            };
        }

        CampaignEventSourcingAdapter eventSourcingAdapter = new CampaignEventSourcingAdapter(eventLogStore);

        CampaignTransitionService transitionService = new CampaignTransitionService();

        CampaignService campaignService = new CampaignServiceImpl(
            kernelAdapter,
            campaignRepo,
            compliancePlugin,
            preflightProvider,
            dmosMetrics,
            killSwitchService,
            commandService,
            new ObjectMapper(),
            eventSourcingAdapter,
            transitionService
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
        if (environment.equals(PRODUCTION)) {
            boolean governedEnabled = !"false".equalsIgnoreCase(System.getenv("DMOS_GOVERNED_AI_ENABLED"));
            if (governedEnabled) {
                String kernelEndpoint = System.getenv("DMOS_KERNEL_AGENT_ENDPOINT");
                if (kernelEndpoint == null || kernelEndpoint.isBlank()) {
                    throw new IllegalStateException("DMOS_KERNEL_AGENT_ENDPOINT is required for production governed AI");
                }
                KernelAgentOrchestrationAdapter agentPort =
                    new KernelAgentOrchestrationAdapter(kernelEndpoint, true);
                governedWorkflowService = new GovernedAgentWorkflowService(
                    agentPort,
                    aiLogRepo,
                    new AiPolicyCheckServiceImpl()
                );
            } else {
                throw new IllegalStateException("Governed AI workflow cannot be disabled in production");
            }
        }
        StrategyGeneratorService strategyService = new StrategyGeneratorServiceImpl(
            kernelAdapter, strategyRepo, governedWorkflowService);
        register(StrategyGeneratorService.class, strategyService);

        // Budget Recommendation Service
        BudgetRecommendationRepository budgetRepo = get(BudgetRecommendationRepository.class);
        BudgetRecommendationService budgetService = new BudgetRecommendationServiceImpl(kernelAdapter, budgetRepo);
        register(BudgetRecommendationService.class, budgetService);

        HashingPort hashingPort = new DefaultHashingProvider(new HashingService(resolvePiiHmacKey(), eventloop));
        register(HashingPort.class, hashingPort);
        SuppressionService suppressionService = new SuppressionServiceImpl(
            kernelAdapter,
            get(SuppressionRepository.class),
            hashingPort
        );
        register(SuppressionService.class, suppressionService);

        DashboardSummaryService dashboardSummaryService = new DashboardSummaryServiceImpl(
            campaignRepo,
            approvalRepo,
            budgetRepo
        );
        register(DashboardSummaryService.class, dashboardSummaryService);

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

        // P1-001 / DMOS-013 / DMOS-014: staging and production use strict server-derived identity.
        boolean strictAuthMode = isStrictAuthEnvironment(environment);
        com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory.IdentityProvider identityProvider = null;
        if (strictAuthMode) {
            identityProvider = DmosJwtIdentityProvider.fromEnvironment();
        }
        com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory httpContextFactory =
            new com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory(strictAuthMode, identityProvider);
        register(com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory.class, httpContextFactory);
        LOG.info("DmosHttpContextFactory instantiated with strictAuthMode={}", strictAuthMode);

        WorkspaceService workspaceService = get(WorkspaceService.class);
        register(DmosWorkspaceServlet.class, new DmosWorkspaceServlet(workspaceService, eventloop, httpContextFactory));

        // Instantiate entitlement dependencies
        ProductEntitlementContext entitlementContext = new ProductEntitlementContext.FailClosed(
            "system", "system", "admin", null, null, null);
        RoleEvaluator roleEvaluator = new RoleEvaluator.FailClosed();
        RouteEntitlementEvaluator routeEntitlementEvaluator = new RouteEntitlementEvaluator(roleEvaluator);
        IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache = 
            new IdentityAwareBoundedCache<>(1000, 300000L);
        register(DmosRouteEntitlementServlet.class, new DmosRouteEntitlementServlet(
            eventloop, entitlementContext, roleEvaluator, routeEntitlementEvaluator, entitlementCache));

        DashboardSummaryService dashboardSummaryService = get(DashboardSummaryService.class);
        register(DmosDashboardServlet.class, new DmosDashboardServlet(dashboardSummaryService, eventloop, httpContextFactory));
        register(DmosReleaseReadinessServlet.class, new DmosReleaseReadinessServlet(eventloop, httpContextFactory));
        register(DmosConnectorReadinessServlet.class, new DmosConnectorReadinessServlet(
            get(DmGoogleAdsConnectorReadinessService.class),
            eventloop,
            httpContextFactory
        ));

        register(DmosConsentServlet.class, new DmosConsentServlet(
            get(ConsentPlugin.class),
            get(SuppressionService.class),
            eventloop,
            httpContextFactory
        ));

        CampaignService campaignService = get(CampaignService.class);
        register(DmosCampaignServlet.class, new DmosCampaignServlet(campaignService, workspaceService, eventloop, metrics, telemetry, httpContextFactory));

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
        register(DmosBoundaryReportingServlet.class, new DmosBoundaryReportingServlet(eventloop, httpContextFactory));

        if (usePostgres()) {
            DataSource dataSource = get(DataSource.class);
            CampaignRepository campaignRepository = get(CampaignRepository.class);
            DmosBridgeHealthIndicator bridgeHealthIndicator = get(DmosBridgeHealthIndicator.class);
            register(
                DmosHealthServlet.class,
                new DmosHealthServlet(dataSource, campaignRepository, kernelAdapter, eventloop, bridgeHealthIndicator)
            );
        }

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
     * Otherwise falls back to a development-only permissive implementation.</p>
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
            throw new IllegalStateException("DMOS_OPA_URL is required in production; allow-all authorization is not permitted.");
        } else {
            LOG.info("[{}] Using development authorization for local mode (set DMOS_OPA_URL for OPA)", environment);
        }
        return (context, resource, action) -> io.activej.promise.Promise.of(Boolean.TRUE);
    }

    /**
     * Creates the audit emitter implementation based on environment.
     * Production uses real kernel bridge; dev/test uses an in-process emitter.
     */
    private BridgeAuditEmitter createAuditEmitter() {
        if (environment.equals(PRODUCTION)) {
            throw new IllegalStateException(
                "Production audit emitter is not configured; no-op audit is not permitted."
            );
        }
        LOG.info("[{}] Using development audit emitter", environment);
        return event -> { };
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
            EventBusPort eventBus = event -> {};
            DurableNotificationPlugin plugin = new DurableNotificationPlugin(dataSource, eventBus, executor);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableNotificationPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using EphemeralNotificationPlugin", environment);
        return new EphemeralNotificationPlugin();
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

    private static final class EnvironmentFeatureFlagPlugin implements FeatureFlagPlugin {
        private static final Map<String, FlagDefinition> KNOWN_FLAGS = Map.ofEntries(
            Map.entry(DmosFeatureFlags.AI_ENABLED, new FlagDefinition("DMOS_AI_ENABLED", false)),
            Map.entry(
                DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED,
                new FlagDefinition("DMOS_GOOGLE_ADS_CONNECTOR_ENABLED", false)
            ),
            Map.entry(DmosFeatureFlags.KILL_SWITCH_ENABLED, new FlagDefinition("DMOS_KILL_SWITCH_ENABLED", true)),
            Map.entry(
                DmosFeatureFlags.ROLLBACK_WORKFLOW_ENABLED,
                new FlagDefinition("DMOS_ROLLBACK_WORKFLOW_ENABLED", true)
            ),
            Map.entry(DmosFeatureFlags.ROLLBACK_ENABLED, new FlagDefinition("DMOS_ROLLBACK_ENABLED", true)),
            Map.entry(DmosFeatureFlags.OBSERVABILITY_ENABLED, new FlagDefinition("DMOS_OBSERVABILITY_ENABLED", true)),
            Map.entry(
                DmosFeatureFlags.DASHBOARD_GROWTH_METRICS,
                new FlagDefinition("DMOS_DASHBOARD_GROWTH_METRICS_ENABLED", false)
            ),
            Map.entry(DmosFeatureFlags.BUDGET_PAGE_ENABLED, new FlagDefinition("DMOS_BUDGET_PAGE_ENABLED", false)),
            Map.entry(DmosFeatureFlags.STRATEGY_PAGE_ENABLED, new FlagDefinition("DMOS_STRATEGY_PAGE_ENABLED", false)),
            Map.entry(DmosFeatureFlags.CAMPAIGNS_PAGE_ENABLED, new FlagDefinition("DMOS_CAMPAIGNS_PAGE_ENABLED", false))
        );

        @Override
        public Promise<Boolean> isEnabled(String flagKey, String tenantId) {
            return getBoolean(flagKey, tenantId, defaultBoolean(flagKey, false));
        }

        @Override
        public Promise<String> getString(String flagKey, String tenantId, String defaultValue) {
            String value = System.getenv(envNameFor(flagKey));
            return Promise.of(value == null || value.isBlank() ? defaultValue : value.trim());
        }

        @Override
        public Promise<Integer> getInt(String flagKey, String tenantId, int defaultValue) {
            String value = System.getenv(envNameFor(flagKey));
            if (value == null || value.isBlank()) {
                return Promise.of(defaultValue);
            }
            try {
                return Promise.of(Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
                return Promise.of(defaultValue);
            }
        }

        @Override
        public Promise<Boolean> getBoolean(String flagKey, String tenantId, boolean defaultValue) {
            String value = System.getenv(envNameFor(flagKey));
            if (value == null || value.isBlank()) {
                return Promise.of(defaultValue);
            }
            return Promise.of("true".equalsIgnoreCase(value.trim()));
        }

        @Override
        public Promise<Map<String, Object>> getAllFlags(String tenantId) {
            Map<String, Object> values = new LinkedHashMap<>();
            KNOWN_FLAGS.forEach((flagKey, definition) ->
                values.put(flagKey, "true".equalsIgnoreCase(
                    System.getenv().getOrDefault(definition.envName, Boolean.toString(definition.defaultEnabled))
                ))
            );
            return Promise.of(Map.copyOf(values));
        }

        private static boolean defaultBoolean(String flagKey, boolean fallback) {
            FlagDefinition definition = KNOWN_FLAGS.get(flagKey);
            return definition == null ? fallback : definition.defaultEnabled;
        }

        private static String envNameFor(String flagKey) {
            FlagDefinition definition = KNOWN_FLAGS.get(flagKey);
            if (definition != null) {
                return definition.envName;
            }
            return "DMOS_FEATURE_" + flagKey.toUpperCase()
                .replace('.', '_')
                .replace('-', '_');
        }

        private static final class FlagDefinition {
            private final String envName;
            private final boolean defaultEnabled;

            private FlagDefinition(String envName, boolean defaultEnabled) {
                this.envName = envName;
                this.defaultEnabled = defaultEnabled;
            }
        }
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
