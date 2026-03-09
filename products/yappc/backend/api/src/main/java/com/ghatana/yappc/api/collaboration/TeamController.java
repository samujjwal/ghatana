/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Team.*;
import com.ghatana.yappc.api.service.TeamService;
import com.ghatana.yappc.api.service.TeamService.*;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for team management endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for team management
 * @doc.layer api
 * @doc.pattern Controller
 */
public class TeamController {

    private static final Logger logger = LoggerFactory.getLogger(TeamController.class);

    private final TeamService service;
    private final ObjectMapper mapper;

    @Inject
    public TeamController(TeamService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** POST /api/teams */
    public Promise<HttpResponse> createTeam(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                CreateTeamRequest req = mapper.readValue(body.getArray(), CreateTeamRequest.class);
                                CreateTeamInput input = new CreateTeamInput(
                                        req.organizationId(),
                                        req.name(),
                                        req.description(),
                                        req.type(),
                                        req.visibility(),
                                        req.timezone(),
                                        ctx.userId()
                                );
                                return service.createTeam(ctx.tenantId(), input)
                                        .map(ApiResponse::created);
                            } catch (Exception e) {
                                logger.error("Failed to create team", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/teams/:id */
    public Promise<HttpResponse> getTeam(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getTeam(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Team not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/organizations/:orgId/teams */
    public Promise<HttpResponse> listOrganizationTeams(HttpRequest request, String orgId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listOrganizationTeams(ctx.tenantId(), orgId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/users/:userId/teams */
    public Promise<HttpResponse> listUserTeams(HttpRequest request, String userId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listUserTeams(ctx.tenantId(), userId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/me/teams */
    public Promise<HttpResponse> listMyTeams(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listUserTeams(ctx.tenantId(), ctx.userId())
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/teams/:id */
    public Promise<HttpResponse> updateTeam(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateTeamRequest req = mapper.readValue(body.getArray(), UpdateTeamRequest.class);
                                UpdateTeamInput input = new UpdateTeamInput(
                                        req.name(),
                                        req.description(),
                                        req.visibility(),
                                        req.timezone()
                                );
                                return service.updateTeam(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update team", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/teams/:id/members */
    public Promise<HttpResponse> addMember(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                AddMemberRequest req = mapper.readValue(body.getArray(), AddMemberRequest.class);
                                AddMemberInput input = new AddMemberInput(
                                        req.userId(),
                                        req.email(),
                                        req.displayName(),
                                        req.role()
                                );
                                return service.addMember(ctx.tenantId(), UUID.fromString(id), input)
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to add member", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** PATCH /api/teams/:id/members/:memberId/role */
    public Promise<HttpResponse> updateMemberRole(HttpRequest request, String id, String memberId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> request.loadBody()
                        .then(body -> {
                            try {
                                UpdateRoleRequest req = mapper.readValue(body.getArray(), UpdateRoleRequest.class);
                                return service.updateMemberRole(ctx.tenantId(), UUID.fromString(id), 
                                        memberId, req.role())
                                        .map(ApiResponse::ok);
                            } catch (Exception e) {
                                logger.error("Failed to update member role", e);
                                return Promise.of(ApiResponse.badRequest(e.getMessage()));
                            }
                        }))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/teams/:id/members/:memberId */
    public Promise<HttpResponse> removeMember(HttpRequest request, String id, String memberId) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.removeMember(ctx.tenantId(), UUID.fromString(id), memberId)
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/teams/:id/statistics */
    public Promise<HttpResponse> getTeamStatistics(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getTeamStatistics(ctx.tenantId(), UUID.fromString(id))
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/teams/:id */
    public Promise<HttpResponse> deleteTeam(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.deleteTeam(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() : ApiResponse.notFound("Team not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Request DTOs ==========

    public record CreateTeamRequest(
            String organizationId,
            String name,
            String description,
            TeamType type,
            TeamVisibility visibility,
            String timezone
    ) {}

    public record UpdateTeamRequest(
            String name,
            String description,
            TeamVisibility visibility,
            String timezone
    ) {}

    public record AddMemberRequest(
            String userId,
            String email,
            String displayName,
            MemberRole role
    ) {}

    public record UpdateRoleRequest(MemberRole role) {}
}
