package com.ghatana.requirements.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.requirements.application.project.ProjectService;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * REST controller for project export operations.
 *
 * <p><b>Purpose:</b> Provides HTTP API endpoints for:
 * - Exporting requirements to various formats (Markdown, PDF, JSON, YAML)
 * - Generating requirement documentation
 * - Creating printable reports
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li><b>GET /api/v1/projects/{id}/export/markdown:</b> Export as Markdown
 *   <li><b>GET /api/v1/projects/{id}/export/pdf:</b> Export as PDF
 *   <li><b>GET /api/v1/projects/{id}/export/json:</b> Export as JSON
 *   <li><b>GET /api/v1/projects/{id}/export/yaml:</b> Export as YAML
 * </ul>
 *
 * @doc.type class
 * @doc.purpose REST controller for export management
 * @doc.layer product
 * @doc.pattern REST Controller
 * @since 1.0.0
 */
public final class ExportController {
    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.Executor executor;

    public ExportController(ProjectService projectService, ObjectMapper objectMapper, java.util.concurrent.Executor executor) {
        this.projectService = Objects.requireNonNull(projectService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * Create servlet with all export routes.
     * 
     * @return RoutingServlet with all export routes
     */
    public RoutingServlet createServlet() {
        RoutingServlet servlet = new RoutingServlet();
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/projects/:projectId/export/:format", this::exportProject);
        return servlet;
    }

    /**
     * GET /api/v1/projects/{projectId}/export/{format}
     *
     * <p>Export project requirements in specified format.
     *
     * @param request HTTP request
     * @return Promise resolving to HTTP response with exported content
     */
    public Promise<HttpResponse> exportProject(HttpRequest request) {
        String projectId = request.getPathParameter("projectId");
        String format = request.getPathParameter("format");
        logger.info("Exporting project {} in format: {}", projectId, format);

        return extractUser(request)
            .then(principal -> {
                switch (format.toLowerCase()) {
                    case "markdown":
                        return exportAsMarkdown(projectId, principal);
                    case "pdf":
                        return exportAsPdf(projectId, principal);
                    case "json":
                        return exportAsJson(projectId, principal);
                    case "yaml":
                        return exportAsYaml(projectId, principal);
                    default:
                        return Promise.of(ResponseBuilder.badRequest()
                            .json(Map.of("error", "Unsupported format: " + format))
                            .build());
                }
            })
            .map(result -> result)
            .whenException(e -> logger.error("Failed to export project", e));
    }

    /**
     * Export project as Markdown.
     */
    private Promise<HttpResponse> exportAsMarkdown(String projectId, User principal) {
        logger.debug("Exporting project {} as Markdown", projectId);

        return projectService.getProject(principal, projectId)
            .map(project -> {
                StringBuilder md = new StringBuilder();
                md.append("# ").append(project.getName()).append("\n\n");
                md.append("## Description\n\n");
                md.append(project.getDescription()).append("\n\n");
                md.append("## Status\n\n");
                md.append(project.getStatus().name()).append("\n\n");
                md.append("## Requirements\n\n");
                md.append("*Requirements will be listed here*\n");

                return ResponseBuilder.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + project.getName() + ".md\"")
                    .bytes(md.toString().getBytes(), "text/markdown")
                    .build();
            });
    }

    /**
     * Export project as PDF.
     */
    private Promise<HttpResponse> exportAsPdf(String projectId, User principal) {
        logger.debug("Exporting project {} as PDF", projectId);

        return projectService.getProject(principal, projectId)
            .map(project -> {
                // Placeholder - in production would generate actual PDF
                String pdfPlaceholder = "PDF generation not yet implemented";

                return ResponseBuilder.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + project.getName() + ".pdf\"")
                    .bytes(pdfPlaceholder.getBytes(), "application/pdf")
                    .build();
            });
    }

    /**
     * Export project as JSON.
     */
    private Promise<HttpResponse> exportAsJson(String projectId, User principal) {
        logger.debug("Exporting project {} as JSON", projectId);

        return projectService.getProject(principal, projectId)
            .map(project -> {
                Map<String, Object> export = Map.of(
                    "projectId", project.getProjectId(),
                    "name", project.getName(),
                    "description", project.getDescription(),
                    "status", project.getStatus().name(),
                    "requirements", Map.of()  // Placeholder
                );

                return ResponseBuilder.ok()
                    .header("Content-Type", "application/json")
                    .header("Content-Disposition", "attachment; filename=\"" + project.getName() + ".json\"")
                    .json(export)
                    .build();
            });
    }

    /**
     * Export project as YAML.
     */
    private Promise<HttpResponse> exportAsYaml(String projectId, User principal) {
        logger.debug("Exporting project {} as YAML", projectId);

        return projectService.getProject(principal, projectId)
            .map(project -> {
                StringBuilder yaml = new StringBuilder();
                yaml.append("projectId: ").append(project.getProjectId()).append("\n");
                yaml.append("name: ").append(project.getName()).append("\n");
                yaml.append("description: ").append(project.getDescription()).append("\n");
                yaml.append("status: ").append(project.getStatus().name()).append("\n");
                yaml.append("requirements: []\n");

                return ResponseBuilder.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + project.getName() + ".yaml\"")
                    .bytes(yaml.toString().getBytes(), "application/yaml")
                    .build();
            });
    }

    // ============ Helper Methods ============

    private Promise<User> extractUser(HttpRequest request) {
        Object principal = request.getAttachment("userPrincipal");
        if (principal instanceof User) {
            return Promise.of((User) principal);
        }
        return Promise.ofException(new IllegalStateException("User principal not found in request"));
    }

    private HttpResponse handleError(Throwable e, String context) {
        logger.error("{}: {}", context, e.getMessage(), e);

        if (e instanceof IllegalArgumentException) {
            return ResponseBuilder.badRequest()
                .json(Map.of("error", e.getMessage()))
                .build();
        }

        if (e.getClass().getSimpleName().contains("Unauthorized")) {
            return ResponseBuilder.unauthorized()
                .json(Map.of("error", "Unauthorized"))
                .build();
        }

        return ResponseBuilder.internalServerError()
            .json(Map.of("error", "Internal server error"))
            .build();
    }
}