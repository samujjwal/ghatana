/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Sprint.SprintRetrospective;
import com.ghatana.yappc.api.service.SprintService;
import com.ghatana.yappc.api.service.SprintService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDate;
import java.util.*;

/**
 * HTTP controller for sprint management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for sprint management
 * @doc.layer api
 * @doc.pattern Controller
 */
public class SprintController {

    private static final Logger logger = LoggerFactory.getLogger(SprintController.class);

    private final SprintService service;
    private final ObjectMapper mapper;

    @Inject
    public SprintController(SprintService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/sprints */
    public Promise<HttpResponse> createSprint(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CreateSprintRequest req = mapper.readValue(body.getArray(), CreateSprintRequest.class);
                                CreateSprintInput input = new CreateSprintInput(
                                        req.projectId(),
                                        req.name(),
                                        req.goals(),
                                        req.startDate(),
                                        req.endDate(),
                                        req.teamCapacity(),
                                        ctx.userId()
                                );
                                return service.createSprint(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to create sprint", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/sprints/:id */
    public Promise<HttpResponse> getSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getSprint(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Sprint not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/sprints/current */
    public Promise<HttpResponse> getCurrentSprint(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getCurrentSprint(ctx.tenantId(), UUID.fromString(projectId))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("No active sprint"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/sprints */
    public Promise<HttpResponse> listProjectSprints(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listProjectSprints(ctx.tenantId(), UUID.fromString(projectId))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/sprints/:id/start */
    public Promise<HttpResponse> startSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.startSprint(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/sprints/:id/complete */
    public Promise<HttpResponse> completeSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                SprintRetrospective retro = null;
                                if (body.getArray().length > 0) {
                                    retro = mapper.readValue(body.getArray(), SprintRetrospective.class);
                                }
                                return service.completeSprint(ctx.tenantId(), UUID.fromString(id), retro)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to complete sprint", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/sprints/:id/cancel */
    public Promise<HttpResponse> cancelSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CancelSprintRequest req = mapper.readValue(body.getArray(), CancelSprintRequest.class);
                                return service.cancelSprint(ctx.tenantId(), UUID.fromString(id), req.reason())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to cancel sprint", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PUT /api/sprints/:id/goals */
    public Promise<HttpResponse> updateSprintGoals(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateGoalsRequest req = mapper.readValue(body.getArray(), UpdateGoalsRequest.class);
                                return service.updateSprintGoals(ctx.tenantId(), UUID.fromString(id), req.goals())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update sprint goals", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PUT /api/sprints/:id/dates */
    public Promise<HttpResponse> updateSprintDates(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateDatesRequest req = mapper.readValue(body.getArray(), UpdateDatesRequest.class);
                                return service.updateSprintDates(ctx.tenantId(), UUID.fromString(id), 
                                        req.startDate(), req.endDate())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update sprint dates", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/sprints/:id */
    public Promise<HttpResponse> deleteSprint(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.deleteSprint(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() : ApiResponse.notFound("Sprint not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/projects/:projectId/velocity */
    public Promise<HttpResponse> getAverageVelocity(HttpRequest request, String projectId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    String countParam = request.getQueryParameter("count");
                    int count = countParam != null ? Integer.parseInt(countParam) : 5;
                    return service.getAverageVelocity(ctx.tenantId(), UUID.fromString(projectId), count)
                            .map(velocity -> ApiResponse.ok(Map.of("averageVelocity", velocity)));
                })
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record CreateSprintRequest(
            String projectId,
            String name,
            List<String> goals,
            LocalDate startDate,
            LocalDate endDate,
            Integer teamCapacity
    ) {}

    public record CancelSprintRequest(String reason) {}

    public record UpdateGoalsRequest(List<String> goals) {}

    public record UpdateDatesRequest(LocalDate startDate, LocalDate endDate) {}
}
