/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.audit.AuditController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Audit API (/api/audit/*, /api/v1/audit/*).
 *
 * @doc.type class
 * @doc.purpose Register audit logging and event query routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class AuditRoutes {

  private AuditRoutes() {}

  /**
   * Registers all audit API routes on the given builder.
   *
   * @param builder the routing servlet builder
   * @param controller audit controller
   */
  public static void register(RoutingServlet.Builder builder, AuditController controller) {
    builder
        .with(POST, "/api/audit/record", controller::recordEvent)
        .with(GET, "/api/audit/events", controller::queryEvents)
        .with(
            GET,
            "/api/audit/events/:eventId",
            request -> {
              String eventId = request.getPathParameter("eventId");
              return controller.getEvent(request, eventId);
            })
        // v1 lifecycle-oriented audit query (Observability 6.2)
        .with(GET, "/api/v1/audit/events", controller::queryAuditEventsV1);
  }
}
