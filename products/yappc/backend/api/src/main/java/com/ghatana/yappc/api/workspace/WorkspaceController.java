/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.WorkspaceService;
import com.ghatana.yappc.api.workspace.dto.AddMemberRequest;
import com.ghatana.yappc.api.workspace.dto.CreateWorkspaceRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateMemberRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateSettingsRequest;
import com.ghatana.yappc.api.workspace.dto.UpdateWorkspaceRequest;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified REST API Controller for Workspace operations.
 *
 * @doc.type class
 * @doc.purpose Workspace management REST API (simplified)
 * @doc.layer api
 * @doc.pattern Controller
 */
public class WorkspaceController {

  private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

  private final WorkspaceService workspaceService;

  public WorkspaceController(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  /** List workspaces. GET /api/workspaces */
  public Promise<HttpResponse> listWorkspaces(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Listing workspaces for tenant: {}", ctx.tenantId());
              return workspaceService
                  .listWorkspaces(ctx.tenantId(), ctx.userId())
                  .map(
                      workspaces -> {
                        logger.info(
                            "Found {} workspaces for tenant {}", workspaces.size(), ctx.tenantId());
                        return ApiResponse.ok(workspaces);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Create workspace. POST /api/workspaces */
  public Promise<HttpResponse> createWorkspace(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, CreateWorkspaceRequest.class)
                    .then(
                        req ->
                            workspaceService
                                .createWorkspace(ctx.tenantId(), ctx.userId(), req)
                                .map(
                                    workspace -> {
                                      logger.info(
                                          "Created workspace {} for tenant {}",
                                          workspace.id(),
                                          ctx.tenantId());
                                      return ApiResponse.created(workspace);
                                    })))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get workspace by ID. GET /api/workspaces/:id */
  public Promise<HttpResponse> getWorkspace(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting workspace {} for tenant: {}", id, ctx.tenantId());
              return workspaceService
                  .getWorkspace(ctx.tenantId(), id)
                  .map(
                      workspace -> {
                        if (workspace == null) {
                          return ApiResponse.notFound("Workspace not found");
                        }
                        logger.info("Retrieved workspace {} for tenant {}", id, ctx.tenantId());
                        return ApiResponse.ok(workspace);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Update workspace. PUT /api/workspaces/:id */
  public Promise<HttpResponse> updateWorkspace(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, UpdateWorkspaceRequest.class)
                    .then(
                        req ->
                            workspaceService
                                .updateWorkspace(ctx.tenantId(), id, req)
                                .map(
                                    workspace -> {
                                      if (workspace == null) {
                                        return ApiResponse.notFound("Workspace not found");
                                      }
                                      logger.info(
                                          "Updated workspace {} for tenant {}", id, ctx.tenantId());
                                      return ApiResponse.ok(workspace);
                                    })))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Delete workspace. DELETE /api/workspaces/:id */
  public Promise<HttpResponse> deleteWorkspace(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Deleting workspace {} for tenant: {}", id, ctx.tenantId());
              return workspaceService
                  .deleteWorkspace(ctx.tenantId(), id)
                  .map(
                      ignored -> {
                        logger.info(
                            "Successfully deleted workspace {} for tenant {}", id, ctx.tenantId());
                        return ApiResponse.noContent();
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** List workspace members. GET /api/workspaces/:id/members */
  public Promise<HttpResponse> listMembers(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Listing members for workspace {} in tenant: {}", id, ctx.tenantId());
              return workspaceService
                  .listMembers(ctx.tenantId(), id)
                  .map(
                      members -> {
                        logger.info(
                            "Found {} members for workspace {} in tenant {}",
                            members.size(),
                            id,
                            ctx.tenantId());
                        return ApiResponse.ok(members);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Add workspace member. POST /api/workspaces/:id/members */
  public Promise<HttpResponse> addMember(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, AddMemberRequest.class)
                    .then(
                        req ->
                            workspaceService
                                .addMember(ctx.tenantId(), id, req)
                                .map(
                                    member -> {
                                      if (member == null) {
                                        return ApiResponse.notFound("Workspace not found");
                                      }
                                      logger.info(
                                          "Added member {} to workspace {} in tenant {}",
                                          member.userId(),
                                          id,
                                          ctx.tenantId());
                                      return ApiResponse.created(member);
                                    })))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Update workspace member. PUT /api/workspaces/:workspaceId/members/:userId */
  public Promise<HttpResponse> updateMember(
      HttpRequest request, String workspaceId, String userId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, UpdateMemberRequest.class)
                    .then(
                        req ->
                            workspaceService
                                .updateMember(ctx.tenantId(), workspaceId, userId, req)
                                .map(
                                    member -> {
                                      if (member == null) {
                                        return ApiResponse.notFound(
                                            "Workspace or member not found");
                                      }
                                      logger.info(
                                          "Updated member {} in workspace {} for tenant {}",
                                          userId,
                                          workspaceId,
                                          ctx.tenantId());
                                      return ApiResponse.ok(member);
                                    })))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Remove workspace member. DELETE /api/workspaces/:workspaceId/members/:userId */
  public Promise<HttpResponse> removeMember(
      HttpRequest request, String workspaceId, String userId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info(
                  "Removing member {} from workspace {} in tenant: {}",
                  userId,
                  workspaceId,
                  ctx.tenantId());

              return workspaceService
                  .removeMember(ctx.tenantId(), workspaceId, userId)
                  .map(
                      removed -> {
                        if (!removed) {
                          return ApiResponse.notFound("Workspace or member not found");
                        }
                        logger.info(
                            "Successfully removed member {} from workspace {} in tenant {}",
                            userId,
                            workspaceId,
                            ctx.tenantId());
                        return ApiResponse.noContent();
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get workspace settings. GET /api/workspaces/:id/settings */
  public Promise<HttpResponse> getSettings(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting settings for workspace {} in tenant: {}", id, ctx.tenantId());
              return workspaceService
                  .getSettings(ctx.tenantId(), id)
                  .map(
                      settings -> {
                        if (settings == null) {
                          return ApiResponse.notFound("Workspace not found");
                        }
                        logger.info(
                            "Retrieved settings for workspace {} in tenant {}", id, ctx.tenantId());
                        return ApiResponse.ok(settings);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Update workspace settings. PUT /api/workspaces/:id/settings */
  public Promise<HttpResponse> updateSettings(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, UpdateSettingsRequest.class)
                    .then(
                        req ->
                            workspaceService
                                .updateSettings(ctx.tenantId(), id, req)
                                .map(
                                    settings -> {
                                      if (settings == null) {
                                        return ApiResponse.notFound("Workspace not found");
                                      }
                                      logger.info(
                                          "Updated settings for workspace {} in tenant {}",
                                          id,
                                          ctx.tenantId());
                                      return ApiResponse.ok(settings);
                                    })))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }
}
