/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.DELETE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;
import static io.activej.http.HttpMethod.PUT;

import com.ghatana.yappc.api.requirements.RequirementsController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Requirements API (/api/requirements/*).
 *
 * @doc.type class
 * @doc.purpose Register requirements CRUD, funnel analytics, and quality scoring routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class RequirementsRoutes {

  private RequirementsRoutes() {}

  /**
   * Registers all requirements API routes on the given builder.
   *
   * @param builder     the routing servlet builder
   * @param controller  requirements controller
   */
  public static void register(RoutingServlet.Builder builder, RequirementsController controller) {
    builder
        .with(POST, "/api/requirements",              controller::createRequirement)
        .with(GET,  "/api/requirements",              controller::queryRequirements)
        .with(GET,  "/api/requirements/domains",      controller::getAvailableDomains)
        .with(GET,  "/api/requirements/funnel",       controller::getFunnelAnalytics)
        .with(GET,  "/api/requirements/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.getRequirement(request, id);
            })
        .with(PUT,    "/api/requirements/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.updateRequirement(request, id);
            })
        .with(DELETE, "/api/requirements/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.deleteRequirement(request, id);
            })
        .with(POST, "/api/requirements/:id/approve",
            request -> {
              String id = request.getPathParameter("id");
              return controller.approveRequirement(request, id);
            })
        .with(POST, "/api/requirements/:id/quality",
            request -> {
              String id = request.getPathParameter("id");
              return controller.calculateQualityScore(request, id);
            });
  }
}
