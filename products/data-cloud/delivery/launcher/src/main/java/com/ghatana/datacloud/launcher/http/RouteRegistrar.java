/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpMethod;
import io.activej.http.RoutingServlet;

import java.util.List;
import java.util.Map;

/**
 * Pass 8: Plane-owned route registrar interface.
 *
 * <p>Allows planes (Data Plane, Action Plane, Event Plane, Governance Plane, etc.)
 * to own their route registration logic and metadata. This enables:
 * <ul>
 *   <li>Clear ownership boundaries between planes</li>
 *   <li>Generated route metadata for security and observability</li>
 *   <li>Incremental migration from monolithic router to plane-owned registrars</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Plane-owned route registration contract
 * @doc.layer product
 * @doc.pattern Registrar, Plugin
 */
public interface RouteRegistrar {

    /**
     * Gets the plane identifier for this registrar.
     *
     * @return plane identifier (e.g., "data-plane", "action-plane", "event-plane")
     */
    String getPlaneId();

    /**
     * Gets the route group identifier.
     *
     * @return route group identifier (e.g., "entities", "pipelines", "events")
     */
    String getRouteGroupId();

    /**
     * Registers routes with the routing servlet builder.
     *
     * @param builder the routing servlet builder
     */
    void registerRoutes(RoutingServlet.Builder builder);

    /**
     * Gets route metadata for all routes registered by this registrar.
     *
     * @return list of route metadata
     */
    List<RouteMetadata> getRouteMetadata();

    /**
     * Route metadata for security and observability.
     */
    record RouteMetadata(
        String routeId,
        String path,
        HttpMethod method,
        String permissionId,
        String description,
        Map<String, String> tags
    ) {
        public RouteMetadata {
            if (routeId == null || routeId.isBlank()) {
                throw new IllegalArgumentException("routeId is required");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path is required");
            }
            if (method == null) {
                throw new IllegalArgumentException("method is required");
            }
            if (tags == null) {
                tags = Map.of();
            }
        }
    }
}
