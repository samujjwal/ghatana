/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.services.dashboard.DashboardActionService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static com.ghatana.yappc.api.HttpResponses.error500;

/**
 * HTTP API controller for dashboard actions.
 *
 * <p>Provides backend-driven dashboard actions for project cards and dashboard.
 * Actions are classified as blocked, review-required, safe-to-continue, or primary.
 *
 * @doc.type class
 * @doc.purpose HTTP API controller for dashboard actions
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class DashboardActionController {

    private static final Logger log = LoggerFactory.getLogger(DashboardActionController.class);

    private final ObjectMapper objectMapper;
    private final DashboardActionService dashboardActionService;

    public DashboardActionController(
            @NotNull ObjectMapper objectMapper,
            @NotNull DashboardActionService dashboardActionService
    ) {
        this.objectMapper = objectMapper;
        this.dashboardActionService = dashboardActionService;
    }

    public Promise<HttpResponse> getDashboardActions(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    DashboardActionRequest req = objectMapper.readValue(body.getArray(), DashboardActionRequest.class);

                    // Validate required fields
                    if (req.workspaceId() == null || req.workspaceId().isBlank()) {
                        return Promise.of(badRequest400("workspaceId is required"));
                    }

                    // Extract principal for authorization context
                    Principal principal = request.getAttachment(Principal.class);
                    if (principal == null) {
                        return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Unauthenticated\"}")
                            .build());
                    }

                    // Validate tenant scope
                    if (req.tenantId() != null && !req.tenantId().equals(principal.getTenantId())) {
                        log.warn("Tenant scope mismatch: principalTenant={}, requestTenant={}",
                            principal.getTenantId(), req.tenantId());
                        return Promise.of(HttpResponse.ofCode(403)
                            .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                            .build());
                    }

                    // Build dashboard actions
                    return dashboardActionService.buildDashboardActions(
                        req.workspaceId(),
                        principal,
                        req.correlationId()
                    )
                    .map(actions -> {
                        try {
                            return ok200Json(objectMapper.writeValueAsString(actions));
                        } catch (Exception e) {
                            log.error("Error serializing dashboard actions", e);
                            return error500("Internal server error");
                        }
                    });

                } catch (Exception e) {
                    log.error("Error processing dashboard actions request", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Dashboard actions request failed", e));
    }

    public Promise<HttpResponse> getProjectDashboardActions(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    ProjectDashboardActionRequest req = objectMapper.readValue(body.getArray(), ProjectDashboardActionRequest.class);

                    // Validate required fields
                    if (req.projectId() == null || req.projectId().isBlank()) {
                        return Promise.of(badRequest400("projectId is required"));
                    }
                    if (req.workspaceId() == null || req.workspaceId().isBlank()) {
                        return Promise.of(badRequest400("workspaceId is required"));
                    }

                    // Extract principal for authorization context
                    Principal principal = request.getAttachment(Principal.class);
                    if (principal == null) {
                        return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Unauthenticated\"}")
                            .build());
                    }

                    // Build project dashboard actions
                    return dashboardActionService.buildProjectDashboardActions(
                        req.projectId(),
                        req.workspaceId(),
                        principal,
                        req.correlationId()
                    )
                    .map(actions -> {
                        try {
                            return ok200Json(objectMapper.writeValueAsString(actions));
                        } catch (Exception e) {
                            log.error("Error serializing project dashboard actions", e);
                            return error500("Internal server error");
                        }
                    });

                } catch (Exception e) {
                    log.error("Error processing project dashboard actions request", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Project dashboard actions request failed", e));
    }

    public record DashboardActionRequest(
            String tenantId,
            String workspaceId,
            String correlationId
    ) {}

    public record ProjectDashboardActionRequest(
            String tenantId,
            String workspaceId,
            String projectId,
            String correlationId
    ) {}
}
