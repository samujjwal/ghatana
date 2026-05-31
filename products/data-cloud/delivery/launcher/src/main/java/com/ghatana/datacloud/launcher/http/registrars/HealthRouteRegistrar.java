/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.registrars;

import com.ghatana.datacloud.launcher.http.RouteRegistrar;
import com.ghatana.datacloud.launcher.http.handlers.HealthHandler;
import io.activej.http.HttpMethod;
import io.activej.http.RoutingServlet;

import java.util.List;
import java.util.Map;

/**
 * Pass 8: Example plane-owned route registrar for health endpoints.
 *
 * <p>Demonstrates the pattern for plane-owned route registration.
 * Health routes are platform-level infrastructure, not product-specific.
 *
 * @doc.type class
 * @doc.purpose Register health endpoints
 * @doc.layer product
 * @doc.pattern RouteRegistrar
 */
public class HealthRouteRegistrar implements RouteRegistrar {

    private final HealthHandler healthHandler;

    public HealthRouteRegistrar(HealthHandler healthHandler) {
        this.healthHandler = healthHandler;
    }

    @Override
    public String getPlaneId() {
        return "platform";
    }

    @Override
    public String getRouteGroupId() {
        return "health";
    }

    @Override
    public void registerRoutes(RoutingServlet.Builder builder) {
        builder
            .with(HttpMethod.GET, "/health", healthHandler::handleHealth)
            .with(HttpMethod.GET, "/health/detail", healthHandler::handleHealthDetail)
            .with(HttpMethod.GET, "/health/deep", healthHandler::handleHealthDeep)
            .with(HttpMethod.GET, "/ready", healthHandler::handleReady)
            .with(HttpMethod.GET, "/live", healthHandler::handleLive)
            .with(HttpMethod.GET, "/info", healthHandler::handleInfo)
            .with(HttpMethod.GET, "/metrics", healthHandler::handleMetrics);
    }

    @Override
    public List<RouteMetadata> getRouteMetadata() {
        return List.of(
            new RouteMetadata(
                "health-get",
                "/health",
                HttpMethod.GET,
                "platform:health:read",
                "Get health status",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "health-detail-get",
                "/health/detail",
                HttpMethod.GET,
                "platform:health:read",
                "Get detailed health status",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "health-deep-get",
                "/health/deep",
                HttpMethod.GET,
                "platform:health:read",
                "Get deep health status",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "ready-get",
                "/ready",
                HttpMethod.GET,
                "platform:health:read",
                "Get readiness status",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "live-get",
                "/live",
                HttpMethod.GET,
                "platform:health:read",
                "Get liveness status",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "info-get",
                "/info",
                HttpMethod.GET,
                "platform:health:read",
                "Get build info",
                Map.of("plane", "platform", "group", "health")
            ),
            new RouteMetadata(
                "metrics-get",
                "/metrics",
                HttpMethod.GET,
                "platform:health:read",
                "Get metrics",
                Map.of("plane", "platform", "group", "health")
            )
        );
    }
}
