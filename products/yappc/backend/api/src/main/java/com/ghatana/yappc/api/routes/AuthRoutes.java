/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.routes;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.auth.AuthenticationController;
import com.ghatana.yappc.api.auth.AuthorizationController;
import io.activej.http.RoutingServlet;

/**
 * Route registrations for the Auth API (/api/auth/*).
 *
 * <p>Covers both RBAC authorization checks and session authentication (login/logout/refresh).
 *
 * @doc.type class
 * @doc.purpose Register permission-check, persona, login, register, and session routes
 * @doc.layer api
 * @doc.pattern Router
 */
public final class AuthRoutes {

  private AuthRoutes() {}

  /**
   * Registers all auth API routes on the given builder.
   *
   * @param builder the routing servlet builder
   * @param authz authorization controller (RBAC)
   * @param authn authentication controller (sessions)
   */
  public static void register(
      RoutingServlet.Builder builder,
      AuthorizationController authz,
      AuthenticationController authn) {

    builder
        // Authorization
        .with(POST, "/api/auth/check-permission", authz::checkPermission)
        .with(GET, "/api/auth/user/permissions", authz::getUserPermissions)
        .with(
            GET,
            "/api/auth/persona/:persona/permissions",
            request -> {
              String persona = request.getPathParameter("persona");
              return authz.getPersonaPermissions(request, persona);
            })
        .with(
            GET,
            "/api/auth/persona/:persona/has-permission/:permission",
            request -> {
              String persona = request.getPathParameter("persona");
              String permission = request.getPathParameter("permission");
              return authz.checkPersonaPermission(request, persona, permission);
            })

        // Authentication
        .with(POST, "/api/auth/login", authn::login)
        .with(POST, "/api/auth/register", authn::register)
        .with(POST, "/api/auth/logout", authn::logout)
        .with(POST, "/api/auth/refresh", authn::refresh)
        .with(GET, "/api/auth/profile", authn::getProfile)
        .with(POST, "/api/auth/reset", authn::requestPasswordReset)
        .with(POST, "/api/auth/reset/confirm", authn::confirmPasswordReset);
  }
}
