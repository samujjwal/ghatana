/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.controller.GraphQLController;
import com.ghatana.yappc.api.controller.WebSocketController;
import io.activej.http.RoutingServlet;
import io.activej.reactor.Reactor;

/**
 * Route registrations for WebSocket and GraphQL endpoints.
 *
 * @doc.type class
 * @doc.purpose Register platform-level HTTP routes (WebSocket, GraphQL)
 * @doc.layer api
 * @doc.pattern Router
 */
public final class PlatformRoutes {

  private PlatformRoutes() {}

  /**
   * Registers WebSocket and GraphQL routes on the given builder.
   *
   * @param builder the routing servlet builder to register routes on
   * @param reactor ActiveJ reactor for WebSocket servlet creation
   * @param wsCtrl WebSocket controller
   * @param gqlCtrl GraphQL controller
   */
  public static void register(
      RoutingServlet.Builder builder,
      Reactor reactor,
      WebSocketController wsCtrl,
      GraphQLController gqlCtrl) {

    builder
        .with(GET, "/ws", wsCtrl.createServlet(reactor))
        .with(POST, "/graphql", gqlCtrl::handleRequest);
  }
}
