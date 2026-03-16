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
      OutboxRelayService outboxRelayService) {

    // Build routing servlet
    AsyncServlet routingServlet =
        RoutingServlet.builder(reactor)
            // CORS preflight handled by middleware - no explicit OPTIONS needed here
            
            // ========== WebSocket ==========
            .with(GET, "/ws", webSocketController.createServlet(reactor))

            // ========== GraphQL API ==========
            .with(POST, "/graphql", graphQLController::handleRequest)

            // ========== Build API ==========
            .with(POST, "/api/v1/build/execute", buildController::executeBuild)
            .with(GET, "/api/v1/build/jobs/:jobId", buildController::getBuildStatus)
            .with(GET, "/api/v1/build/jobs/:jobId/logs", buildController::getBuildLogs)
            .with(GET, "/api/v1/build/jobs/:jobId/artifacts", buildController::downloadArtifacts)
            .with(DELETE, "/api/v1/build/jobs/:jobId", buildController::cancelBuild)
            .with(GET, "/api/v1/build/history", buildController::getBuildHistory)

            // ========== Audit API ==========
            .with(POST, "/api/audit/record", auditController::recordEvent)
            .with(GET, "/api/audit/events", auditController::queryEvents)
            .with(
                GET,
                "/api/audit/events/:eventId",
                request -> {
                  String eventId = request.getPathParameter("eventId");
                  return auditController.getEvent(request, eventId);
                })
            // v1 lifecycle-oriented audit query (Observability 6.2)
            .with(GET, "/api/v1/audit/events", auditController::queryAuditEventsV1)

            // ========== Version API ==========
            .with(POST, "/api/version/create", versionController::createVersion)
            .with(
                GET,
                "/api/version/history/:entityId",
                request -> {
                  String entityId = request.getPathParameter("entityId");
                  return versionController.getVersionHistory(request, entityId);
                })
            .with(
                GET,
                "/api/version/:entityId/versions/:versionNumber",
                request -> {
                  String entityId = request.getPathParameter("entityId");
                  String versionNumberStr = request.getPathParameter("versionNumber");
                  Integer versionNumber = Integer.parseInt(versionNumberStr);
                  return versionController.getVersion(request, entityId, versionNumber);
                })
            .with(
                GET,
                "/api/version/:entityId/diff",
                request -> {
                  String entityId = request.getPathParameter("entityId");
                  return versionController.compareVersions(request, entityId);
                })
            .with(
                POST,
                "/api/version/:entityId/rollback",
                request -> {
                  String entityId = request.getPathParameter("entityId");
                  return versionController.rollback(request, entityId);
                })

            // ========== Authorization API ==========
            .with(POST, "/api/auth/check-permission", authController::checkPermission)
            .with(GET, "/api/auth/user/permissions", authController::getUserPermissions)
            .with(
                GET,
                "/api/auth/persona/:persona/permissions",
                request -> {
                  String persona = request.getPathParameter("persona");
                  return authController.getPersonaPermissions(request, persona);
                })
            .with(
                GET,
                "/api/auth/persona/:persona/has-permission/:permission",
                request -> {
                  String persona = request.getPathParameter("persona");
                  String permission = request.getPathParameter("permission");
                  return authController.checkPersonaPermission(request, persona, permission);
                })

            // ========== Authentication API ==========
            .with(POST, "/api/auth/login", authenticationController::login)
            .with(POST, "/api/auth/register", authenticationController::register)
            .with(POST, "/api/auth/logout", authenticationController::logout)
            .with(POST, "/api/auth/refresh", authenticationController::refresh)
            .with(GET, "/api/auth/profile", authenticationController::getProfile)
            .with(POST, "/api/auth/reset", authenticationController::requestPasswordReset)
            .with(POST, "/api/auth/reset/confirm", authenticationController::confirmPasswordReset)

            // ========== Requirements API ==========
            .with(POST, "/api/requirements", requirementsController::createRequirement)
            .with(GET, "/api/requirements", requirementsController::queryRequirements)
            .with(
                GET,
                "/api/requirements/domains",
                requirementsController::getAvailableDomains) // Config integration
            .with(GET, "/api/requirements/funnel", requirementsController::getFunnelAnalytics)
            .with(
                GET,
                "/api/requirements/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return requirementsController.getRequirement(request, id);
                })
            .with(
                PUT,
                "/api/requirements/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return requirementsController.updateRequirement(request, id);
                })
            .with(
                DELETE,
                "/api/requirements/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return requirementsController.deleteRequirement(request, id);
                })
            .with(
                POST,
                "/api/requirements/:id/approve",
                request -> {
                  String id = request.getPathParameter("id");
                  return requirementsController.approveRequirement(request, id);
                })
            .with(
                POST,
                "/api/requirements/:id/quality",
                request -> {
                  String id = request.getPathParameter("id");
                  return requirementsController.calculateQualityScore(request, id);
                })

            // ========== AI Suggestions API ==========
            .with(POST, "/api/ai/suggestions/generate", aiSuggestionsController::generateSuggestion)
            .with(GET, "/api/ai/suggestions", aiSuggestionsController::querySuggestions)
            .with(GET, "/api/ai/suggestions/inbox", aiSuggestionsController::getInbox)
            .with(
                GET,
                "/api/ai/suggestions/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return aiSuggestionsController.getSuggestion(request, id);
                })
            .with(
                POST,
                "/api/ai/suggestions/:id/accept",
                request -> {
                  String id = request.getPathParameter("id");
                  return aiSuggestionsController.acceptSuggestion(request, id);
                })
            .with(
                POST,
                "/api/ai/suggestions/:id/reject",
                request -> {
                  String id = request.getPathParameter("id");
                  return aiSuggestionsController.rejectSuggestion(request, id);
                })

            // ========== Workspace API ==========
            .with(GET, "/api/workspaces", workspaceController::listWorkspaces)
            .with(POST, "/api/workspaces", workspaceController::createWorkspace)
            .with(
                GET,
                "/api/workspaces/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.getWorkspace(request, id);
                })
            .with(
                PUT,
                "/api/workspaces/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.updateWorkspace(request, id);
                })
            .with(
                DELETE,
                "/api/workspaces/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.deleteWorkspace(request, id);
                })
            .with(
                GET,
                "/api/workspaces/:id/members",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.listMembers(request, id);
                })
            .with(
                POST,
                "/api/workspaces/:id/members",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.addMember(request, id);
                })
            .with(
                PUT,
                "/api/workspaces/:workspaceId/members/:userId",
                request -> {
                  String workspaceId = request.getPathParameter("workspaceId");
                  String userId = request.getPathParameter("userId");
                  return workspaceController.updateMember(request, workspaceId, userId);
                })
            .with(
                DELETE,
                "/api/workspaces/:workspaceId/members/:userId",
                request -> {
                  String workspaceId = request.getPathParameter("workspaceId");
                  String userId = request.getPathParameter("userId");
                  return workspaceController.removeMember(request, workspaceId, userId);
                })
            .with(
                GET,
                "/api/workspaces/:id/settings",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.getSettings(request, id);
                })
            .with(
                PUT,
                "/api/workspaces/:id/settings",
                request -> {
                  String id = request.getPathParameter("id");
                  return workspaceController.updateSettings(request, id);
                })

            // ========== Architecture API ==========
            .with(POST, "/api/architecture/impact", architectureController::analyzeImpact)
            .with(GET, "/api/architecture/dependencies", architectureController::getDependencies)
            .with(GET, "/api/architecture/tech-debt", architectureController::getTechDebt)
            .with(GET, "/api/architecture/patterns", architectureController::getPatternWarnings)
            .with(POST, "/api/architecture/simulate", architectureController::simulateChange)

            // ========== Approval API ==========
            .with(POST, "/api/approvals", approvalController::createWorkflow)
            .with(GET, "/api/approvals/pending", approvalController::getPendingApprovals)
            .with(
                GET,
                "/api/approvals/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return approvalController.getWorkflow(request, id);
                })
            .with(
                POST,
                "/api/approvals/:id/decision",
                request -> {
                  String id = request.getPathParameter("id");
                  return approvalController.submitDecision(request, id);
                })
            .with(
                DELETE,
                "/api/approvals/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return approvalController.cancelWorkflow(request, id);
                })
            .with(
                GET,
                "/api/approvals/:id/history",
                request -> {
                  String id = request.getPathParameter("id");
                  return approvalController.getHistory(request, id);
                })

            // ========== Configuration API ==========
            .with(GET, "/api/config/domains", configController::getDomains)
            .with(
                GET,
                "/api/config/domains/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return configController.getDomainById(request, id);
                })
            .with(GET, "/api/config/workflows", configController::getWorkflows)
            .with(
                GET,
                "/api/config/workflows/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return configController.getWorkflowById(request, id);
                })
            .with(GET, "/api/config/lifecycle", configController::getLifecycleConfig)
            .with(GET, "/api/config/agents", configController::getAgentCapabilities)
            .with(GET, "/api/config/tasks", configController::getAllTasks)
            .with(GET, "/api/config/personas", configController::getPersonas)
            .with(
                GET,
                "/api/config/personas/:id",
                request -> {
                  String id = request.getPathParameter("id");
                  return configController.getPersonaById(request, id);
                })

            // ========== Dashboard API ==========
            .with(GET, "/api/dashboards", dashboardController::getDashboards)
            .with(
                GET,
                "/api/dashboards/:id",
                request -> dashboardController.getDashboardById(request))
            .with(
                GET,
                "/api/dashboards/domain/:domainId",
                request -> dashboardController.getDashboardsByDomain(request))

            // ========== Unified Left Rail (Platform) ==========
            .with(GET, "/api/rail/components", railController::getComponents)
            .with(GET, "/api/rail/infrastructure", railController::getInfrastructure)
            .with(GET, "/api/rail/files", railController::getFiles)
            .with(GET, "/api/rail/datasources", railController::getDataSources)
            .with(GET, "/api/rail/history", railController::getHistory)
            .with(GET, "/api/rail/favorites", railController::getFavorites)
            .with(POST, "/api/rail/ai/suggestions", railController::getSuggestions)

            // ========== Workflow Agent API ==========
            .with(POST, "/api/agents/execute", workflowAgentController::executeAgent)
            .with(POST, "/api/agents/execute/batch", workflowAgentController::executeBatch)
            .with(
                DELETE,
                "/api/agents/execute/:id",
                request -> workflowAgentController.cancelExecution(request))
            .with(
                GET,
                "/api/agents/execute/:id",
                request -> workflowAgentController.getExecutionStatus(request))
            .with(GET, "/api/agents", workflowAgentController::listAgents)
            .with(
                GET,
                "/api/agents/role/:role",
                request -> workflowAgentController.getAgentsByRole(request))
            .with(
                GET,
                "/api/agents/:id/health",
                request -> workflowAgentController.getAgentHealth(request))

            // ========== Code Generation API ==========
            .with(POST, "/api/v1/codegen/from-openapi", codeGenerationController::generateFromOpenAPI)
            .with(POST, "/api/v1/codegen/from-graphql", codeGenerationController::generateFromGraphQL)
            .with(POST, "/api/v1/codegen/from-schema", codeGenerationController::generateFromSchema)
            .with(POST, "/api/v1/codegen/preview", codeGenerationController::previewCode)

            // ========== Test Generation API ==========
            .with(POST, "/api/v1/testing/generate", testGenerationController::generateTests)
            .with(POST, "/api/v1/testing/coverage", testGenerationController::analyzeCoverage)
            .with(GET, "/api/v1/testing/templates", testGenerationController::listTemplates)

            // ========== Observability: Prometheus scrape endpoint ==========
            .with(
                GET,
                "/metrics",
                request -> io.activej.promise.Promise.of(
                    io.activej.http.HttpResponse.ok200()
                        .withHeader(
                            io.activej.http.HttpHeaders.CONTENT_TYPE,
                            "text/plain; version=0.0.4; charset=utf-8")
                        .withBody(micrometerMetricsCollector.scrape()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .build()))

            // ========== Health Aggregation (Observability 6.6) ==========
            .with(GET, "/health/detailed", healthAggregationController::getDetailedHealth)

            // ========== Workflow Execution (Orchestration 7.3) ==========
            .with(
                POST,
                "/api/v1/workflows/:templateId/start",
                request -> {
                  String templateId = request.getPathParameter("templateId");
                  return workflowExecutionController.startWorkflow(request, templateId);
                })
            .with(
                GET,
                "/api/v1/workflows/runs/:runId/status",
                request -> {
                  String runId = request.getPathParameter("runId");
                  return workflowExecutionController.getRunStatus(request, runId);
                })

            // ========== DLQ Management (Orchestration 7.4) ==========
            .with(GET,  "/api/v1/dlq", dlqController::listEntries)
            .with(
                POST,
                "/api/v1/dlq/:id/retry",
                request -> {
                  String entryId = request.getPathParameter("id");
                  return dlqController.retryEntry(request, entryId);
                })

            // ========== Agent History & Rationale (Observability 6.4) ==========
            .with(
                GET,
                "/api/v1/agents/:agentId/history",
                request -> {
                  String aid = request.getPathParameter("agentId");
                  return agentHistoryController.getHistory(request, aid);
                })
            .with(
                GET,
                "/api/v1/agents/:agentId/rationale/:turnId",
                request -> {
                  String aid    = request.getPathParameter("agentId");
                  String turnId = request.getPathParameter("turnId");
                  return agentHistoryController.getRationale(request, aid, turnId);
                })

            // ========== Learned Policies (Plan 9.5.4) ==========
            .with(
                GET,
                "/api/v1/agents/:agentId/policies",
                request -> {
                  String aid = request.getPathParameter("agentId");
                  return learnedPolicyController.getPolicies(request, aid);
                })

            .build();

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
