package com.ghatana.products.yappc.domain.workflow.http;

import com.ghatana.platform.http.server.servlet.RoutingServlet;
import io.activej.http.HttpMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Route configuration for AI Workflow HTTP endpoints.
 *
 * @doc.type class
 * @doc.purpose Workflow route configuration
 * @doc.layer product
 * @doc.pattern Router
 */
public final class WorkflowRoutes {

    private WorkflowRoutes() {
        // Utility class
    }

    /**
     * Configures workflow routes on the provided RoutingServlet.
     *
     * @param servlet The routing servlet to configure
     * @param controller The workflow controller
     * @return The configured routing servlet
     */
    @NotNull
    public static RoutingServlet configure(
        @NotNull RoutingServlet servlet,
        @NotNull WorkflowController controller
    ) {
        // Workflow CRUD
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows", controller::createWorkflow);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/workflows", controller::listWorkflows);
        servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/workflows/:id", request ->
                controller.getWorkflow(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.DELETE, "/api/v1/workflows/:id", request ->
                controller.deleteWorkflow(request, request.getPathParameter("id")));

        // Workflow state transitions
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/start", request ->
                controller.startWorkflow(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/pause", request ->
                controller.pauseWorkflow(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/resume", request ->
                controller.resumeWorkflow(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/cancel", request ->
                controller.cancelWorkflow(request, request.getPathParameter("id")));

        // Step operations
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/steps/advance", request ->
                controller.advanceStep(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/steps/:stepId/goto", request ->
                controller.goToStep(
                        request,
                        request.getPathParameter("id"),
                        request.getPathParameter("stepId")
                ));

        // AI Plan operations
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/plans/generate", request ->
                controller.generatePlan(request, request.getPathParameter("id")));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:workflowId/plans/:planId/approve", request ->
                controller.approvePlan(
                        request,
                        request.getPathParameter("workflowId"),
                        request.getPathParameter("planId")
                ));
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:workflowId/plans/:planId/reject", request ->
                controller.rejectPlan(
                        request,
                        request.getPathParameter("workflowId"),
                        request.getPathParameter("planId")
                ));
        servlet.addAsyncRoute(HttpMethod.PUT, "/api/v1/workflows/:workflowId/plans/:planId/steps", request ->
                controller.modifyPlanSteps(
                        request,
                        request.getPathParameter("workflowId"),
                        request.getPathParameter("planId")
                ));

        // Routing
        servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/workflows/:id/route", request ->
                controller.routeWorkflow(request, request.getPathParameter("id")));

        return servlet;
    }
}
