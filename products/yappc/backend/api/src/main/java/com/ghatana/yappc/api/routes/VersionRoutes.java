/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.version.VersionController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Version API (/api/version/*).
 *
 * @doc.type class
 * @doc.purpose Register version creation, history, diff, and rollback routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class VersionRoutes {

  private VersionRoutes() {}

  /**
   * Registers all version API routes on the given builder.
   *
   * @param builder the routing servlet builder
   * @param controller version controller
   */
  public static void register(RoutingServlet.Builder builder, VersionController controller) {
    builder
        .with(POST, "/api/version/create", controller::createVersion)
        .with(
            GET,
            "/api/version/history/:entityId",
            request -> {
              String entityId = request.getPathParameter("entityId");
              return controller.getVersionHistory(request, entityId);
            })
        .with(
            GET,
            "/api/version/:entityId/versions/:versionNumber",
            request -> {
              String entityId = request.getPathParameter("entityId");
              String versionNumStr = request.getPathParameter("versionNumber");
              return controller.getVersion(request, entityId, Integer.parseInt(versionNumStr));
            })
        .with(
            GET,
            "/api/version/:entityId/diff",
            request -> {
              String entityId = request.getPathParameter("entityId");
              return controller.compareVersions(request, entityId);
            })
        .with(
            POST,
            "/api/version/:entityId/rollback",
            request -> {
              String entityId = request.getPathParameter("entityId");
              return controller.rollback(request, entityId);
            });
  }
}
