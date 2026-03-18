/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.DELETE;
import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;
import static io.activej.http.HttpMethod.PUT;

import com.ghatana.yappc.api.workspace.WorkspaceController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Workspace API (/api/workspaces/*).
 *
 * @doc.type class
 * @doc.purpose Register workspace CRUD, member management, and settings routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class WorkspaceRoutes {

  private WorkspaceRoutes() {}

  /**
   * Registers all workspace API routes on the given builder.
   *
   * @param builder     the routing servlet builder
   * @param controller  workspace controller
   */
  public static void register(RoutingServlet.Builder builder, WorkspaceController controller) {
    builder
        .with(GET,    "/api/workspaces",                               controller::listWorkspaces)
        .with(POST,   "/api/workspaces",                               controller::createWorkspace)
        .with(GET,    "/api/workspaces/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.getWorkspace(request, id);
            })
        .with(PUT,    "/api/workspaces/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.updateWorkspace(request, id);
            })
        .with(DELETE, "/api/workspaces/:id",
            request -> {
              String id = request.getPathParameter("id");
              return controller.deleteWorkspace(request, id);
            })
        .with(GET,    "/api/workspaces/:id/members",
            request -> {
              String id = request.getPathParameter("id");
              return controller.listMembers(request, id);
            })
        .with(POST,   "/api/workspaces/:id/members",
            request -> {
              String id = request.getPathParameter("id");
              return controller.addMember(request, id);
            })
        .with(PUT,    "/api/workspaces/:workspaceId/members/:userId",
            request -> {
              String workspaceId = request.getPathParameter("workspaceId");
              String userId      = request.getPathParameter("userId");
              return controller.updateMember(request, workspaceId, userId);
            })
        .with(DELETE, "/api/workspaces/:workspaceId/members/:userId",
            request -> {
              String workspaceId = request.getPathParameter("workspaceId");
              String userId      = request.getPathParameter("userId");
              return controller.removeMember(request, workspaceId, userId);
            })
        .with(GET,    "/api/workspaces/:id/settings",
            request -> {
              String id = request.getPathParameter("id");
              return controller.getSettings(request, id);
            })
        .with(PUT,    "/api/workspaces/:id/settings",
            request -> {
              String id = request.getPathParameter("id");
              return controller.updateSettings(request, id);
            });
  }
}
