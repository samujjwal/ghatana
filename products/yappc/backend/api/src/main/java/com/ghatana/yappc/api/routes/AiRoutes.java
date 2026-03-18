/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.ai.AISuggestionsController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the AI Suggestions API (/api/ai/*).
 *
 * @doc.type class
 * @doc.purpose Register AI suggestion generation, inbox, and approval routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class AiRoutes {

  private AiRoutes() {}

  /**
   * Registers all AI suggestions API routes on the given builder.
   *
   * @param builder     the routing servlet builder
   * @param controller  AI suggestions controller
   */
  public static void register(RoutingServlet.Builder builder, AISuggestionsController controller) {
    builder
        .with(POST, "/api/ai/suggestions/generate",  controller::generateSuggestion)
        .with(GET,  "/api/ai/suggestions",           controller::querySuggestions)
        .with(GET,  "/api/ai/suggestions/inbox",     controller::getInbox)
        .with(GET,  "/api/ai/suggestions/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.getSuggestion(request, id);
            })
        .with(POST, "/api/ai/suggestions/:id/accept",
            request -> {
              String id = request.getPathParameter("id");
              return controller.acceptSuggestion(request, id);
            })
        .with(POST, "/api/ai/suggestions/:id/reject",
            request -> {
              String id = request.getPathParameter("id");
              return controller.rejectSuggestion(request, id);
            });
  }
}
