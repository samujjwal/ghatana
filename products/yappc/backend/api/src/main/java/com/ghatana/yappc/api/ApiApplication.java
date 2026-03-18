/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api;

import static io.activej.http.HttpMethod.*;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.observability.MicrometerMetricsCollector;
import com.ghatana.yappc.api.ai.AISuggestionsController;
import com.ghatana.yappc.api.config.FlywayConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import com.ghatana.yappc.api.approval.ApprovalController;
import com.ghatana.yappc.api.architecture.ArchitectureController;
import com.ghatana.yappc.api.audit.AuditController;
import com.ghatana.yappc.api.auth.AuthenticationController;
import com.ghatana.yappc.api.auth.AuthorizationController;
import com.ghatana.yappc.api.build.BuildController;
import com.ghatana.yappc.api.config.ProductionModule;
import com.ghatana.yappc.api.outbox.OutboxRelayService;
import com.ghatana.yappc.api.controller.ConfigController;
import com.ghatana.yappc.api.controller.DashboardController;
import com.ghatana.yappc.api.controller.RailController;
import com.ghatana.yappc.api.controller.StaticFileController;
import com.ghatana.yappc.api.controller.WorkflowAgentController;
import com.ghatana.yappc.api.middleware.CorsMiddleware;
import com.ghatana.yappc.api.middleware.GlobalExceptionHandler;
import com.ghatana.yappc.api.security.JwtTokenProvider;
import com.ghatana.yappc.api.security.SecurityMiddleware;
import com.ghatana.yappc.api.requirements.RequirementsController;
import com.ghatana.yappc.api.version.VersionController;
import com.ghatana.yappc.api.workspace.WorkspaceController;
import com.ghatana.yappc.api.controller.GraphQLController;
import com.ghatana.yappc.api.controller.WebSocketController;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import com.ghatana.yappc.api.routes.AgentRoutes;
import com.ghatana.yappc.api.routes.AiRoutes;
import com.ghatana.yappc.api.routes.ArchitectureApprovalRoutes;
import com.ghatana.yappc.api.routes.AuditRoutes;
import com.ghatana.yappc.api.routes.AuthRoutes;
import com.ghatana.yappc.api.routes.BuildRoutes;
import com.ghatana.yappc.api.routes.ConfigRoutes;
import com.ghatana.yappc.api.routes.OperationsRoutes;
import com.ghatana.yappc.api.routes.PlatformRoutes;
import com.ghatana.yappc.api.routes.RequirementsRoutes;
import com.ghatana.yappc.api.routes.VersionRoutes;
import com.ghatana.yappc.api.routes.WorkspaceRoutes;
import io.activej.inject.annotation.Provides;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YAPPC API Server - ActiveJ HTTP application.
 *
 * <p><b>Purpose</b><br>
 * Main entry point for YAPPC backend API. Configures routing, dependency injection, CORS, and error
 * handling.
 *
 * <p><b>Architecture</b><br>
 * - ActiveJ HTTP server (non-blocking, high-performance) - Dependency injection via ActiveJ Inject
 * - RESTful API design - Multi-tenant isolation enforced at API layer - RBAC authorization on all
 * endpoints
 *
 * <p><b>API Modules</b><br>
 *
 * <pre>
 * /api/audit/*         - Audit logging
 * /api/version/*       - Version control
 * /api/auth/*          - Authorization & personas
 * /api/requirements/*  - Requirements CRUD & funnel
 * /api/ai/suggestions/* - AI suggestions generation & approval
 * /api/workspaces/*    - Workspace management & members
 * /api/architecture/*  - Architecture analysis & impact
 * /api/approvals/*     - Approval workflow management
 * </pre>
 *
 * @doc.type class
 * @doc.purpose HTTP server application
 * @doc.layer api
 * @doc.pattern Launcher, Application Entry Point
 */
public class ApiApplication extends HttpServerLauncher {

  private static final Logger logger = LoggerFactory.getLogger(ApiApplication.class);

  /**
   * Prometheus-backed MetricsCollector created before the DI module so the same instance
   * can be both passed to {@link com.ghatana.yappc.api.config.ProductionModule} (for service
   * injection) and referenced by the /metrics scrape endpoint closure.
   */
  private MicrometerMetricsCollector micrometerMetricsCollector;

  /** Provides API routing configuration. */
  @Provides
  AsyncServlet servlet(
      Reactor reactor,
      org.flywaydb.core.Flyway flyway,  // Ensure migrations run before servlet starts
      JwtTokenProvider jwtTokenProvider,
      AuditController auditController,
      VersionController versionController,
      AuthorizationController authController,
      AuthenticationController authenticationController,
      RequirementsController requirementsController,
      AISuggestionsController aiSuggestionsController,
      WorkspaceController workspaceController,
      ArchitectureController architectureController,
      ApprovalController approvalController,
      ConfigController configController,
      DashboardController dashboardController,
      RailController railController,
      BuildController buildController,
      WorkflowAgentController workflowAgentController,
      GraphQLController graphQLController,
      WebSocketController webSocketController,
      com.ghatana.yappc.api.codegen.CodeGenerationController codeGenerationController,
      com.ghatana.yappc.api.testing.TestGenerationController testGenerationController,
      com.ghatana.yappc.api.observability.HealthAggregationController healthAggregationController,
      com.ghatana.yappc.api.workflow.WorkflowExecutionController workflowExecutionController,
      com.ghatana.yappc.api.dlq.DlqController dlqController,
      com.ghatana.yappc.api.history.AgentHistoryController agentHistoryController,
      com.ghatana.yappc.api.policy.LearnedPolicyController learnedPolicyController,
      OutboxRelayService outboxRelayService,
      com.ghatana.agent.catalog.CatalogRegistry catalogRegistry) {

    // Assemble routes from domain-focused sub-routers
    RoutingServlet.Builder builder = RoutingServlet.builder(reactor);
    PlatformRoutes.register(builder, reactor, webSocketController, graphQLController);
    BuildRoutes.register(builder, buildController);
    AuditRoutes.register(builder, auditController);
    VersionRoutes.register(builder, versionController);
    AuthRoutes.register(builder, authController, authenticationController);
    RequirementsRoutes.register(builder, requirementsController);
    AiRoutes.register(builder, aiSuggestionsController);
    WorkspaceRoutes.register(builder, workspaceController);
    ArchitectureApprovalRoutes.register(builder, architectureController, approvalController);
    ConfigRoutes.register(builder, configController, dashboardController, railController);
    AgentRoutes.register(builder, workflowAgentController, codeGenerationController,
        testGenerationController, agentHistoryController, learnedPolicyController);
    OperationsRoutes.register(builder, micrometerMetricsCollector,
        healthAggregationController, workflowExecutionController, dlqController);
    // outboxRelayService and catalogRegistry are injected here to ensure they are
    // initialised by ActiveJ DI before the servlet begins serving requests.
    logger.info("Registered {} domain route groups; outbox={}, catalog={}",
        12, outboxRelayService.getClass().getSimpleName(),
        catalogRegistry.getClass().getSimpleName());

    AsyncServlet routingServlet = builder.build();

    // Wrap: CorrelationId → CORS → GlobalExceptionHandler → SecurityMiddleware → RoutingServlet
    return new com.ghatana.yappc.api.middleware.CorrelationIdFilter(
        new CorsMiddleware(
            new GlobalExceptionHandler(
                SecurityMiddleware.create(jwtTokenProvider, routingServlet))));
  }

  /** Override the default module with ProductionModule for real service implementations. */
  @Override
  protected io.activej.inject.module.Module getBusinessLogicModule() {
    logger.info("Configuring YAPPC API with ProductionModule");

    // Create MetricsCollector instance - in production this would be configured properly
    MetricsCollector metricsCollector = createMetricsCollector();

    return new ProductionModule(metricsCollector);
  }

  /**
   * Creates a production {@link MicrometerMetricsCollector} backed by a Prometheus registry.
   *
   * <p>The reference is stored in {@link #micrometerMetricsCollector} so the same instance
   * can be served at the /metrics scrape endpoint.
   */
  private MetricsCollector createMetricsCollector() {
    micrometerMetricsCollector = MicrometerMetricsCollector.create();
    logger.info("Created MicrometerMetricsCollector — Prometheus scrape endpoint active at /metrics");
    return micrometerMetricsCollector;
  }

  /**
   * Creates and configures the database connection pool.
   */
  private static DataSource createDataSource() {
    String jdbcUrl = System.getenv("DATABASE_URL");
    if (jdbcUrl == null) {
      jdbcUrl = "jdbc:postgresql://localhost:5432/yappc";
      logger.warn("DATABASE_URL not set, using default: {}", jdbcUrl);
    }

    String username = System.getenv("DATABASE_USERNAME");
    if (username == null) {
      username = "yappc";
      logger.warn("DATABASE_USERNAME not set, using default: {}", username);
    }

    String password = System.getenv("DATABASE_PASSWORD");
    if (password == null || password.isBlank()) {
      throw new IllegalStateException(
              "DATABASE_PASSWORD environment variable is required but not set");
    }

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(username);
    config.setPassword(password);
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setConnectionTimeout(30000);
    config.setIdleTimeout(600000);
    config.setMaxLifetime(1800000);

    return new HikariDataSource(config);
  }

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   * @throws Exception if server fails to start
   */
  public static void main(String[] args) throws Exception {
    logger.info("Starting YAPPC API Server...");

    // Run database migrations before starting the server
    try {
      logger.info("Running database migrations...");
      DataSource dataSource = createDataSource();
      FlywayConfiguration.runMigrations(dataSource);
      logger.info("Database migrations completed successfully");
    } catch (Exception e) {
      logger.error("Database migration failed - server will not start", e);
      throw new RuntimeException("Failed to run database migrations", e);
    }

    ApiApplication launcher = new ApiApplication();
    launcher.launch(args);
  }
}
