/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.DELETE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.build.BuildController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Build API (/api/v1/build/*).
 *
 * @doc.type class
 * @doc.purpose Register build execution and job management routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class BuildRoutes {

  private BuildRoutes() {}

  /**
   * Registers all build API routes on the given builder.
   *
   * @param builder     the routing servlet builder
   * @param controller  build controller
   */
  public static void register(RoutingServlet.Builder builder, BuildController controller) {
    builder
        .with(POST,   "/api/v1/build/execute",                     controller::executeBuild)
        .with(GET,    "/api/v1/build/jobs/:jobId",                 controller::getBuildStatus)
        .with(GET,    "/api/v1/build/jobs/:jobId/logs",            controller::getBuildLogs)
        .with(GET,    "/api/v1/build/jobs/:jobId/artifacts",       controller::downloadArtifacts)
        .with(DELETE, "/api/v1/build/jobs/:jobId",                 controller::cancelBuild)
        .with(GET,    "/api/v1/build/history",                     controller::getBuildHistory);
  }
}
