/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.controller.ConfigController;
import com.ghatana.yappc.api.controller.DashboardController;
import com.ghatana.yappc.api.controller.RailController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for Configuration, Dashboard, and Rail APIs.
 *
 * <ul>
 *   <li>/api/config/* - domains, workflows, lifecycle, agents, tasks, personas
 *   <li>/api/dashboards/* - dashboard retrieval by id/domain
 *   <li>/api/rail/* - unified left rail (components, infra, files, history, favourites)
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Register configuration, dashboard, and navigation rail routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class ConfigRoutes {

  private ConfigRoutes() {}

  /**
   * Registers all config, dashboard, and rail routes on the given builder.
   *
   * @param builder the routing servlet builder
   * @param configCtrl configuration controller
   * @param dashCtrl dashboard controller
   * @param railCtrl rail controller
   */
  public static void register(
      RoutingServlet.Builder builder,
      ConfigController configCtrl,
      DashboardController dashCtrl,
      RailController railCtrl) {

    builder
        // Configuration
        .with(GET, "/api/config/domains", configCtrl::getDomains)
        .with(
            GET,
            "/api/config/domains/:id",
            request -> {
              String id = request.getPathParameter("id");
              return configCtrl.getDomainById(request, id);
            })
        .with(GET, "/api/config/workflows", configCtrl::getWorkflows)
        .with(
            GET,
            "/api/config/workflows/:id",
            request -> {
              String id = request.getPathParameter("id");
              return configCtrl.getWorkflowById(request, id);
            })
        .with(GET, "/api/config/lifecycle", configCtrl::getLifecycleConfig)
        .with(GET, "/api/config/agents", configCtrl::getAgentCapabilities)
        .with(GET, "/api/config/tasks", configCtrl::getAllTasks)
        .with(GET, "/api/config/personas", configCtrl::getPersonas)
        .with(
            GET,
            "/api/config/personas/:id",
            request -> {
              String id = request.getPathParameter("id");
              return configCtrl.getPersonaById(request, id);
            })

        // Dashboards
        .with(GET, "/api/dashboards", dashCtrl::getDashboards)
        .with(GET, "/api/dashboards/:id", request -> dashCtrl.getDashboardById(request))
        .with(
            GET,
            "/api/dashboards/domain/:domainId",
            request -> dashCtrl.getDashboardsByDomain(request))

        // Unified Left Rail
        .with(GET, "/api/rail/components", railCtrl::getComponents)
        .with(GET, "/api/rail/infrastructure", railCtrl::getInfrastructure)
        .with(GET, "/api/rail/files", railCtrl::getFiles)
        .with(GET, "/api/rail/datasources", railCtrl::getDataSources)
        .with(GET, "/api/rail/history", railCtrl::getHistory)
        .with(GET, "/api/rail/favorites", railCtrl::getFavorites)
        .with(POST, "/api/rail/ai/suggestions", railCtrl::getSuggestions);
  }
}
