/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.yappc.api.auth.PersonaMapping.PersonaType;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API Controller for Authorization operations.
 *
 * <p><b>Purpose</b><br>
 * Exposes AuthorizationService and PersonaMapping to frontend for: - Checking user permissions -
 * Getting permission sets for personas - Validating access to resources
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * POST /api/auth/check-permission - Check if user has permission
 * GET  /api/auth/user/permissions - Get all permissions for user
 * GET  /api/auth/persona/:persona/permissions - Get permissions for persona
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - API layer (HTTP → Service) - Bridges YAPPC personas with platform RBAC - Uses PersonaMapping
 * for persona → permission resolution - Enforces multi-tenant isolation
 *
 * @doc.type class
 * @doc.purpose REST API for authorization
 * @doc.layer api
 * @doc.pattern Controller
 */
public class AuthorizationController {

  private static final Logger logger = LoggerFactory.getLogger(AuthorizationController.class);

  private final SyncAuthorizationService authorizationService;

  /**
   * Creates AuthorizationController.
   *
   * @param authorizationService the authorization service
   */
  public AuthorizationController(SyncAuthorizationService authorizationService) {
    this.authorizationService =
        Objects.requireNonNull(authorizationService, "authorizationService is required");
  }

  /**
   * Check if user has a specific permission.
   *
   * <p>POST /api/auth/check-permission Body: { userId, tenantId, permission, resourceId? }
   *
   * @param request HTTP request with permission check data
   * @return Promise of HTTP response with result
   */
  public Promise<HttpResponse> checkPermission(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, CheckPermissionRequest.class)
                    .then(
                        payload -> {
                          if (payload.permission() == null || payload.permission().isBlank()) {
                            return Promise.of(ApiResponse.badRequest("permission is required"));
                          }

                          String effectiveTenantId =
                              payload.tenantId() != null ? payload.tenantId() : ctx.tenantId();
                          if (!ctx.tenantId().equals(effectiveTenantId)) {
                            return Promise.of(
                                ApiResponse.forbidden("User does not have access to tenant"));
                          }

                          Set<String> roles =
                              resolveRoles(payload.roles(), payload.persona(), ctx.role(), ctx.persona());
                          String effectiveUserId =
                              payload.userId() != null && !payload.userId().isBlank()
                                  ? payload.userId()
                                  : ctx.userId();

                          User user = new User(effectiveUserId, effectiveUserId, roles);
                          boolean hasPermission =
                              authorizationService.hasPermission(user, payload.permission());

                          Map<String, Object> permissionCheckResult = new HashMap<>();
                          permissionCheckResult.put("hasPermission", hasPermission);
                          permissionCheckResult.put("permission", payload.permission());
                          permissionCheckResult.put("userId", effectiveUserId);
                          permissionCheckResult.put("tenantId", effectiveTenantId);
                          permissionCheckResult.put("resourceId", payload.resourceId());
                          permissionCheckResult.put("checkedAt", Instant.now());
                          permissionCheckResult.put("grantedRoles", new ArrayList<>(roles));

                          logger.info(
                              "Permission check completed: userId={}, tenantId={}, permission={}, allowed={}",
                              effectiveUserId,
                              effectiveTenantId,
                              payload.permission(),
                              hasPermission);
                          return Promise.of(ApiResponse.ok(permissionCheckResult));
                        }))
        .then(
            response -> Promise.of(response),
            e -> {
              logger.error("Permission check failed", e);
              return Promise.of(ApiResponse.fromException(e));
            });
  }

  /**
   * Get all permissions for current user.
   *
   * <p>GET /api/auth/user/permissions?tenantId=...&resourceId=...
   *
   * @param request HTTP request with query params
   * @return Promise of HTTP response with permission list
   */
  public Promise<HttpResponse> getUserPermissions(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              String requestedTenantId = request.getQueryParameter("tenantId");
              String effectiveTenantId =
                  requestedTenantId != null ? requestedTenantId : ctx.tenantId();
              if (!ctx.tenantId().equals(effectiveTenantId)) {
                return Promise.of(ApiResponse.forbidden("User does not have access to tenant"));
              }

              String resourceId = request.getQueryParameter("resourceId");
              String personaParam = request.getQueryParameter("persona");
              String rolesParam = request.getQueryParameter("roles");
              List<String> requestedRoles = parseRolesQuery(rolesParam);
              Set<String> roles =
                  resolveRoles(requestedRoles, personaParam, ctx.role(), ctx.persona());

              User user = new User(ctx.userId(), ctx.userId(), roles);
              List<String> permissions = new ArrayList<>(authorizationService.getAllPermissions(user));
              permissions.sort(String::compareTo);

              Map<String, Object> userPermissions = new HashMap<>();
              userPermissions.put("userId", ctx.userId());
              userPermissions.put("tenantId", effectiveTenantId);
              userPermissions.put("resourceId", resourceId);
              userPermissions.put("roles", new ArrayList<>(roles));
              userPermissions.put("permissions", permissions);
              userPermissions.put("permissionCount", permissions.size());
              userPermissions.put("retrievedAt", Instant.now());

              logger.info(
                  "Retrieved {} permissions for user {} in tenant {}",
                  permissions.size(),
                  ctx.userId(),
                  effectiveTenantId);
              return Promise.of(ApiResponse.ok(userPermissions));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Get permissions for a specific persona type.
   *
   * <p>GET /api/auth/persona/:persona/permissions
   *
   * @param request HTTP request
   * @param personaName persona name (e.g., "DEVELOPER", "PRODUCT_MANAGER")
   * @return Promise of HTTP response with permission list
   */
  public Promise<HttpResponse> getPersonaPermissions(HttpRequest request, String personaName) {
    try {
      PersonaType persona = PersonaType.valueOf(personaName.toUpperCase());
      Set<String> permissions = PersonaMapping.getPersonaPermissions(persona);

      logger.info("Fetching permissions for persona: {}", personaName);

      // Convert permissions to list of strings
      java.util.List<String> permissionNames = new java.util.ArrayList<>(permissions);

      Map<String, Object> response =
          Map.of(
              "persona",
              personaName,
              "defaultRole",
              PersonaMapping.getDefaultRole(persona).getName(),
              "permissions",
              permissionNames,
              "permissionCount",
              permissions.size());

      return Promise.of(ApiResponse.ok(response));

    } catch (IllegalArgumentException e) {
      logger.warn("Invalid persona name: {}", personaName);
      return Promise.of(ApiResponse.badRequest("Persona '" + personaName + "' does not exist"));
    }
  }

  /**
   * Check if persona has specific permission.
   *
   * <p>GET /api/auth/persona/:persona/has-permission/:permission
   *
   * @param request HTTP request
   * @param personaName persona name
   * @param permissionName permission name
   * @return Promise of HTTP response with boolean result
   */
  public Promise<HttpResponse> checkPersonaPermission(
      HttpRequest request, String personaName, String permissionName) {
    try {
      PersonaType persona = PersonaType.valueOf(personaName.toUpperCase());
      String permission = permissionName;

      boolean hasPermission = PersonaMapping.hasPermission(persona, permission);

      logger.info(
          "Checking if persona {} has permission {}: {}",
          personaName,
          permissionName,
          hasPermission);

      Map<String, Object> response =
          Map.of(
              "persona", personaName,
              "permission", permissionName,
              "hasPermission", hasPermission);

      return Promise.of(ApiResponse.ok(response));

    } catch (IllegalArgumentException e) {
      return Promise.of(ApiResponse.badRequest("Invalid persona or permission"));
    }
  }

  private Set<String> resolveRoles(
      List<String> requestRoles, String requestPersona, String contextRole, String contextPersona) {
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    if (requestRoles != null) {
      requestRoles.stream()
          .filter(role -> role != null && !role.isBlank())
          .map(role -> role.trim().toUpperCase(Locale.ROOT))
          .forEach(roles::add);
    }

    if (roles.isEmpty()) {
      String persona = requestPersona != null ? requestPersona : contextPersona;
      if (persona != null && !persona.isBlank()) {
        try {
          PersonaType personaType = PersonaType.valueOf(persona.trim().toUpperCase(Locale.ROOT));
          String mappedRole = PersonaMapping.getDefaultRole(personaType).getName();
          if (mappedRole != null && !mappedRole.isBlank()) {
            roles.add(mappedRole.trim().toUpperCase(Locale.ROOT));
          }
        } catch (IllegalArgumentException ignored) {
          logger.debug("Unknown persona '{}'; skipping persona-based role mapping", persona);
        }
      }
    }

    if (roles.isEmpty() && contextRole != null && !contextRole.isBlank()) {
      roles.add(contextRole.trim().toUpperCase(Locale.ROOT));
    }

    if (roles.isEmpty()) {
      roles.add("MEMBER");
    }
    return roles;
  }

  private List<String> parseRolesQuery(String rolesParam) {
    if (rolesParam == null || rolesParam.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(rolesParam.split(","))
        .map(String::trim)
        .filter(role -> !role.isBlank())
        .toList();
  }

  private record CheckPermissionRequest(
      String userId, String tenantId, String permission, String resourceId, String persona, List<String> roles) {}
}
