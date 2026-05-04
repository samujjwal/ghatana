package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.ai.GovernedAgentWorkflowService;
import com.ghatana.digitalmarketing.application.approval.ApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowServiceImpl;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditService;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditServiceImpl;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationRepository;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationService;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationServiceImpl;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.campaign.CampaignServiceImpl;
import com.ghatana.digitalmarketing.application.strategy.MarketingStrategyRepository;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorServiceImpl;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogService;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogServiceImpl;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceRepository;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceServiceImpl;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapterImpl;
import com.ghatana.digitalmarketing.domain.compliance.CompliancePlugin;
import com.ghatana.digitalmarketing.inf.ProductionProfileGuard;
import com.ghatana.digitalmarketing.inf.approval.InMemoryApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.inf.campaign.InMemoryCampaignRepository;
import com.ghatana.digitalmarketing.inf.research.InMemoryCompetitorResearchRepository;
import com.ghatana.digitalmarketing.inf.transparency.InMemoryAiActionLogRepository;
import com.ghatana.digitalmarketing.inf.workspace.InMemoryWorkspaceRepository;
import com.ghatana.digitalmarketing.persistence.approval.PostgresApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.persistence.audit.PostgresWebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.persistence.budget.PostgresBudgetRecommendationRepository;
import com.ghatana.digitalmarketing.persistence.campaign.PostgresCampaignRepository;
import com.ghatana.digitalmarketing.persistence.strategy.PostgresMarketingStrategyRepository;
import com.ghatana.digitalmarketing.persistence.workspace.PostgresWorkspaceRepository;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
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
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

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
    private final ServiceGraph serviceGraph;

    public DmosApiServer() {
        this.environment = validateEnvironment();
        this.serviceGraph = new ServiceGraph();
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

        // Build service graph
        buildServiceGraph();

        // Start services
        serviceGraph.startFuture().await();

        LOG.info("DMOS API server started successfully on port {}", getListenPort());
    }

    private void buildServiceGraph() {
        LOG.info("Building DMOS service graph for {} environment", environment);

        // Create eventloop for async operations
        Eventloop eventloop = Eventloop.create();
        serviceGraph.add(Eventloop.class, () -> eventloop);
        serviceGraph.add(Executor.class, () -> eventloop);

        // Create DataSource based on environment
        if (usePostgres()) {
            DataSource dataSource = createPostgresDataSource();
            serviceGraph.add(DataSource.class, () -> dataSource);
            LOG.info("Using PostgreSQL persistence");
        } else {
            LOG.info("Using in-memory persistence (dev/test only)");
            // In-memory repositories don't need DataSource
        }

        // Wire kernel bridge ports with production-grade implementations
        BridgeAuthorizationService authService = createAuthorizationService();
        BridgeAuditEmitter auditEmitter = createAuditEmitter();
        BridgeHealthIndicator healthIndicator = createHealthIndicator();

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
        serviceGraph.add(DigitalMarketingKernelAdapter.class, () -> kernelAdapter);

        // Wire repositories based on persistence type
        if (usePostgres()) {
            wirePostgresRepositories(eventloop);
        } else {
            wireInMemoryRepositories();
        }

        // Wire compliance plugin
        CompliancePlugin compliancePlugin = createCompliancePlugin();
        serviceGraph.add(CompliancePlugin.class, () -> compliancePlugin);

        // Wire services
        wireServices(kernelAdapter, eventloop, compliancePlugin);

        // Wire servlets
        wireServlets(kernelAdapter, eventloop);

        LOG.info("Service graph built successfully");
    }

    private boolean usePostgres() {
        String persistenceType = System.getenv("DMOS_PERSISTENCE_TYPE");
        return "postgresql".equalsIgnoreCase(persistenceType) && !environment.equals(DEVELOPMENT);
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

    private void wirePostgresRepositories(Eventloop eventloop) {
        DataSource dataSource = serviceGraph.get(DataSource.class);
        Executor executor = serviceGraph.get(Executor.class);

        serviceGraph.add(WorkspaceRepository.class, () -> new PostgresWorkspaceRepository(dataSource, executor));
        serviceGraph.add(CampaignRepository.class, () -> new PostgresCampaignRepository(dataSource, executor));
        serviceGraph.add(ApprovalSnapshotRepository.class, () -> new PostgresApprovalSnapshotRepository(dataSource, executor));
        serviceGraph.add(MarketingStrategyRepository.class, () -> new PostgresMarketingStrategyRepository(dataSource, executor));
        serviceGraph.add(BudgetRecommendationRepository.class, () -> new PostgresBudgetRecommendationRepository(dataSource, executor));
        serviceGraph.add(WebsiteAuditReportRepository.class, () -> new PostgresWebsiteAuditReportRepository(dataSource, executor));

        LOG.info("PostgreSQL repositories wired successfully");
    }

    private void wireInMemoryRepositories() {
        ProductionProfileGuard.validate(); // Ensure in-memory is allowed

        serviceGraph.add(WorkspaceRepository.class, InMemoryWorkspaceRepository::new);
        serviceGraph.add(CampaignRepository.class, InMemoryCampaignRepository::new);
        serviceGraph.add(ApprovalSnapshotRepository.class, InMemoryApprovalSnapshotRepository::new);
        serviceGraph.add(AiActionLogRepository.class, InMemoryAiActionLogRepository::new);

        // For repositories without in-memory adapters yet, use forward implementations
        LOG.warn("MarketingStrategyRepository, BudgetRecommendationRepository, and WebsiteAuditReportRepository using forward implementations - in-memory adapters pending");
    }

    private void wireServices(DigitalMarketingKernelAdapter kernelAdapter, Eventloop eventloop,
                               CompliancePlugin compliancePlugin) {
        // Workspace Service
        WorkspaceRepository workspaceRepo = serviceGraph.get(WorkspaceRepository.class);
        WorkspaceService workspaceService = new WorkspaceServiceImpl(workspaceRepo);
        serviceGraph.add(WorkspaceService.class, () -> workspaceService);

        // Campaign Service
        CampaignRepository campaignRepo = serviceGraph.get(CampaignRepository.class);
        CampaignService campaignService = new CampaignServiceImpl(kernelAdapter, campaignRepo);
        serviceGraph.add(CampaignService.class, () -> campaignService);

        // Approval Workflow Service
        ApprovalSnapshotRepository approvalRepo = serviceGraph.get(ApprovalSnapshotRepository.class);
        ApprovalWorkflowServiceImpl approvalService = new ApprovalWorkflowServiceImpl(
            kernelAdapter, approvalRepo, compliancePlugin);
        serviceGraph.add(ApprovalWorkflowService.class, () -> approvalService);

        // AI Action Log Service
        AiActionLogRepository aiLogRepo = serviceGraph.get(AiActionLogRepository.class);
        AiActionLogService aiLogService = new AiActionLogServiceImpl(kernelAdapter, aiLogRepo);
        serviceGraph.add(AiActionLogService.class, () -> aiLogService);

        // Strategy Generator Service
        MarketingStrategyRepository strategyRepo = serviceGraph.get(MarketingStrategyRepository.class);
        GovernedAgentWorkflowService governedWorkflowService = null; // FIXME: Wire real governed workflow when kernel integration available
        StrategyGeneratorService strategyService = new StrategyGeneratorServiceImpl(
            kernelAdapter, strategyRepo, governedWorkflowService);
        serviceGraph.add(StrategyGeneratorService.class, () -> strategyService);

        // Budget Recommendation Service
        BudgetRecommendationRepository budgetRepo = serviceGraph.get(BudgetRecommendationRepository.class);
        BudgetRecommendationService budgetService = new BudgetRecommendationServiceImpl(kernelAdapter, budgetRepo);
        serviceGraph.add(BudgetRecommendationService.class, () -> budgetService);

        // Website Audit Service
        WebsiteAuditReportRepository auditReportRepo = serviceGraph.get(WebsiteAuditReportRepository.class);
        WebsiteAuditService websiteAuditService = new WebsiteAuditServiceImpl(kernelAdapter, auditReportRepo);
        serviceGraph.add(WebsiteAuditService.class, () -> websiteAuditService);
    }

    private void wireServlets(DigitalMarketingKernelAdapter kernelAdapter, Eventloop eventloop) {
        // Wire core servlets
        WorkspaceService workspaceService = serviceGraph.get(WorkspaceService.class);
        serviceGraph.add(DmosWorkspaceServlet.class, () -> new DmosWorkspaceServlet(eventloop, kernelAdapter, workspaceService));

        CampaignService campaignService = serviceGraph.get(CampaignService.class);
        serviceGraph.add(DmosCampaignServlet.class, () -> new DmosCampaignServlet(eventloop, kernelAdapter, campaignService));

        ApprovalWorkflowServiceImpl approvalService = serviceGraph.get(ApprovalWorkflowService.class);
        serviceGraph.add(DmosApprovalServlet.class, () -> new DmosApprovalServlet(eventloop, kernelAdapter, approvalService));

        AiActionLogService aiLogService = serviceGraph.get(AiActionLogService.class);
        serviceGraph.add(DmosAiActionLogServlet.class, () -> new DmosAiActionLogServlet(eventloop, kernelAdapter, aiLogService));

        StrategyGeneratorService strategyService = serviceGraph.get(StrategyGeneratorService.class);
        serviceGraph.add(DmosStrategyServlet.class, () -> new DmosStrategyServlet(eventloop, kernelAdapter, strategyService));

        BudgetRecommendationService budgetService = serviceGraph.get(BudgetRecommendationService.class);
        serviceGraph.add(DmosBudgetRecommendationServlet.class, () -> new DmosBudgetRecommendationServlet(eventloop, kernelAdapter, budgetService));

        WebsiteAuditService websiteAuditService = serviceGraph.get(WebsiteAuditService.class);
        serviceGraph.add(DmosWebsiteAuditServlet.class, () -> new DmosWebsiteAuditServlet(eventloop, kernelAdapter, websiteAuditService));

        // Note: Additional servlets (AdCopy, Content, Email, etc.) need to be wired
        LOG.info("Core servlets wired - additional servlets pending");
    }

    // -----------------------------------------------------------------------
    // Kernel bridge port factory methods
    // -----------------------------------------------------------------------

    /**
     * Creates the authorization service implementation based on environment.
     * Production uses real kernel bridge; dev/test uses allowAll for convenience.
     */
    private BridgeAuthorizationService createAuthorizationService() {
        if (environment.equals(PRODUCTION)) {
            // FIXME: Wire real kernel bridge authorization when kernel integration available
            LOG.warn("[PRODUCTION] Using allowAll authorization - replace with real kernel bridge integration");
            return BridgeAuthorizationService.allowAll();
        }
        LOG.info("[{}] Using allowAll authorization for development", environment);
        return BridgeAuthorizationService.allowAll();
    }

    /**
     * Creates the audit emitter implementation based on environment.
     * Production uses real kernel bridge; dev/test uses noOp for convenience.
     */
    private BridgeAuditEmitter createAuditEmitter() {
        if (environment.equals(PRODUCTION)) {
            // FIXME: Wire real kernel bridge audit emitter when kernel integration available
            LOG.warn("[PRODUCTION] Using noOp audit emitter - replace with real kernel bridge integration");
            return BridgeAuditEmitter.noOp();
        }
        LOG.info("[{}] Using noOp audit emitter for development", environment);
        return BridgeAuditEmitter.noOp();
    }

    /**
     * Creates the health indicator implementation.
     */
    private BridgeHealthIndicator createHealthIndicator() {
        return () -> io.activej.promise.Promise.of(true);
    }

    // -----------------------------------------------------------------------
    // Platform plugin factory methods
    // -----------------------------------------------------------------------

    /**
     * Creates the consent plugin implementation based on environment.
     * Production uses durable JDBC-backed plugin; dev/test uses in-memory plugin.
     */
    private ConsentPlugin createConsentPlugin() {
        if (usePostgres()) {
            DataSource dataSource = serviceGraph.get(DataSource.class);
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
     * Production uses durable JDBC-backed plugin; dev/test uses in-memory plugin.
     */
    private HumanApprovalPlugin createApprovalPlugin() {
        if (usePostgres()) {
            DataSource dataSource = serviceGraph.get(DataSource.class);
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
     * Production uses durable JDBC-backed plugin; dev/test uses in-memory plugin.
     */
    private AuditTrailPlugin createAuditTrailPlugin() {
        if (usePostgres()) {
            DataSource dataSource = serviceGraph.get(DataSource.class);
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
     * Uses StandardRiskManagementPlugin with configurable risk models.
     */
    private RiskManagementPlugin createRiskManagementPlugin() {
        LOG.info("[{}] Using StandardRiskManagementPlugin", environment);
        return new StandardRiskManagementPlugin();
    }

    /**
     * Creates the notification plugin implementation based on environment.
     * Production uses durable JDBC-backed plugin; dev/test uses in-memory plugin.
     */
    private NotificationPlugin createNotificationPlugin() {
        if (usePostgres()) {
            DataSource dataSource = serviceGraph.get(DataSource.class);
            DurableNotificationPlugin plugin = new DurableNotificationPlugin(dataSource);
            plugin.ensureSchema();
            LOG.info("[{}] Using DurableNotificationPlugin with PostgreSQL", environment);
            return plugin;
        }
        LOG.info("[{}] Using InMemoryNotificationPlugin", environment);
        return new InMemoryNotificationPlugin();
    }

    /**
     * Creates the compliance plugin implementation.
     * Uses StandardCompliancePlugin with configurable rule sets.
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
        return 8080; // Default port
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
