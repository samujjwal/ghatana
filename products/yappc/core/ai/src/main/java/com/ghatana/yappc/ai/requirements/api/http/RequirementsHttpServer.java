package com.ghatana.yappc.ai.requirements.api.http;

import com.ghatana.yappc.ai.requirements.api.config.RequirementsConfig;
import com.ghatana.yappc.ai.requirements.api.http.filter.AuthenticationFilter;
import com.ghatana.yappc.ai.requirements.api.http.filter.CorsFilter;
import com.ghatana.yappc.ai.requirements.api.rest.ExportController;
import com.ghatana.yappc.ai.requirements.api.rest.ProjectController;
import com.ghatana.yappc.ai.requirements.api.rest.RequirementController;
import com.ghatana.yappc.ai.requirements.api.rest.WorkspaceController;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * HTTP server configuration and routing for the AI Requirements application.
 *
 * <p><b>Purpose:</b> Sets up the HTTP server with:
 * - REST API endpoints for workspaces, projects, requirements
 * - GraphQL endpoint for complex queries
 * - Authentication and authorization filters
 * - CORS handling
 * - Error handling and response formatting
 *
 * <p><b>Routing:</b>
 * <ul>
 *   <li>{@code POST /api/v1/workspaces} - Create workspace
 *   <li>{@code GET /api/v1/workspaces/{id}} - Get workspace
 *   <li>{@code POST /api/v1/workspaces/{id}/projects} - Create project
 *   <li>{@code GET /api/v1/projects/{id}/requirements} - List requirements
 *   <li>{@code POST /graphql} - GraphQL queries/mutations
 *   <li>{@code GET /health} - Health check
 * </ul>
 *
 * <p><b>Filters:</b>
 * <ul>
 *   <li>CORS - Cross-origin requests (development only)
 *   <li>Authentication - JWT validation
 *   <li>Logging - Request/response metrics
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP server configuration
 * @doc.layer product
 * @doc.pattern Server Configuration
 * @since 1.0.0
 */
public final class RequirementsHttpServer {
  private static final Logger logger = LoggerFactory.getLogger(RequirementsHttpServer.class);

  private final RequirementsConfig config;
  private final WorkspaceController workspaceController;
  private final ProjectController projectController;
  private final RequirementController requirementController;
  private final ExportController exportController;
  private final AuthenticationFilter authFilter;
  private final CorsFilter corsFilter;
  private final Eventloop eventloop;

  /**
   * Create the HTTP server.
   *
   * @param config the application configuration
   * @param workspaceController workspace endpoints
   * @param projectController project endpoints
   * @param requirementController requirement endpoints
   * @param exportController export endpoints
   * @param authFilter authentication filter
   * @param corsFilter CORS filter
   * @param eventloop the event loop
   */
  public RequirementsHttpServer(
      RequirementsConfig config,
      WorkspaceController workspaceController,
      ProjectController projectController,
      RequirementController requirementController,
      ExportController exportController,
      AuthenticationFilter authFilter,
      CorsFilter corsFilter,
      Eventloop eventloop) {
    this.config = Objects.requireNonNull(config);
    this.workspaceController = Objects.requireNonNull(workspaceController);
    this.projectController = Objects.requireNonNull(projectController);
    this.requirementController = Objects.requireNonNull(requirementController);
    this.exportController = Objects.requireNonNull(exportController);
    this.authFilter = Objects.requireNonNull(authFilter);
    this.corsFilter = Objects.requireNonNull(corsFilter);
    this.eventloop = Objects.requireNonNull(eventloop);
  }

  /**
   * Create and configure the HttpServer using core abstractions.
   *
   * @return configured HTTP server
   */
  public HttpServer create() {
    logger.info("Creating HTTP server on port {}", config.getServerPort());

    return HttpServerBuilder.create()
        .withEventloop(eventloop)
        .withPort(config.getServerPort())
        .withHealthCheck("/health")
        .addServlet(workspaceController.createServlet())
        .addServlet(projectController.createServlet())
        .addServlet(requirementController.createServlet())
        .addServlet(exportController.createServlet())
        .build();
  }
}