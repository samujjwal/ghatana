/*
 * @doc.purpose HTTP endpoints for dashboard access
 * @doc.layer platform
 * @doc.pattern Controller
 */
package com.ghatana.yappc.api.controller;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.service.DashboardService;
import io.activej.http.*;
import io.activej.promise.Promise;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for dashboard endpoints.
 * 
 * Provides dashboard configuration data for the frontend,
 * including widgets and actions for each domain.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for dashboard access
 * @doc.layer platform
 * @doc.pattern Controller
 */
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/dashboards - Get all dashboards
     */
    public Promise<HttpResponse> getDashboards(HttpRequest request) {
        logger.debug("GET /api/dashboards");
        return dashboardService.getDashboards()
                .map(dashboards -> ApiResponse.ok(Map.of("dashboards", dashboards)));
    }

    /**
     * GET /api/dashboards/:id - Get dashboard by ID
     */
    public Promise<HttpResponse> getDashboardById(HttpRequest request) {
        String id = request.getPathParameter("id");
        logger.debug("GET /api/dashboards/{}", id);

        return dashboardService.getDashboardById(id)
                .map(dashboard -> ApiResponse.ok(Map.of("dashboard", dashboard)))
                .then(Promise::of, e -> {
                    logger.warn("Dashboard not found: {}", id);
                    return Promise.of(ApiResponse.notFound(e.getMessage()));
                });
    }

    /**
     * GET /api/dashboards/domain/:domainId - Get dashboards by domain
     */
    public Promise<HttpResponse> getDashboardsByDomain(HttpRequest request) {
        String domainId = request.getPathParameter("domainId");
        logger.debug("GET /api/dashboards/domain/{}", domainId);

        return dashboardService.getDashboardsByDomain(domainId)
                .map(dashboards -> ApiResponse.ok(Map.of("dashboards", dashboards)));
    }
}
