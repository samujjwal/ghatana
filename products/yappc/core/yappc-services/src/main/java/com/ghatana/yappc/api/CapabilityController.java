/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Capability controller that exposes user capabilities as a read model.
 *
 * <p>This controller provides a backend source of truth for frontend capabilities,
 * replacing hardcoded capability registries. Capabilities are computed based on:
 * <ul>
 *   <li>User's role in the workspace/project context</li>
 *   <li>Resource scope (tenant/workspace/project/artifact)</li>
 *   <li>Backend feature flags for optional capabilities</li>
 * </ul>
 *
 * <p>The frontend should load capabilities from this endpoint and use them to gate
 * UI actions, ensuring the backend remains the source of enforcement.
 *
 * @doc.type class
 * @doc.purpose Backend read model for user capabilities
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class CapabilityController {

    private static final Logger log = LoggerFactory.getLogger(CapabilityController.class);

    private final YappcAuthorizationService authorizationService;

    public CapabilityController(@NotNull YappcAuthorizationService authorizationService) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService is required");
    }

    /**
     * GET /api/v1/capabilities
     *
     * Returns the capabilities for the current user in the given context.
     *
     * Query parameters:
     * - workspaceId (optional): Workspace context for workspace-scoped capabilities
     * - projectId (optional): Project context for project-scoped capabilities
     * - artifactId (optional): Artifact context for artifact-scoped capabilities
     *
     * Response:
     * {
     *   "role": "OWNER|ADMIN|DEVELOPER|VIEWER",
     *   "capabilities": {
     *     "workspace": { "read": true, "update": false, ... },
     *     "project": { "read": true, "create": true, ... },
     *     "lifecycle": {
     *       "intent": { "read": true, "create": true, ... },
     *       "shape": { "read": true, "create": false, ... },
     *       ...
     *     }
     *   }
     * }
     *
     * @param request the HTTP request
     * @return HTTP response with capabilities
     */
    public Promise<HttpResponse> getCapabilities(@NotNull HttpRequest request) {
        try {
            Principal principal = request.getAttachment(Principal.class);
            if (principal == null) {
                return Promise.of(HttpResponse.ofCode(401)
                    .withJson("{\"error\":\"Unauthenticated\"}")
                    .build());
            }

            String workspaceId = extractQueryParameter(request, "workspaceId");
            String projectId = extractQueryParameter(request, "projectId");
            String artifactId = extractQueryParameter(request, "artifactId");

            String role = determineRole(principal, workspaceId, projectId);

            CapabilityResponse response = new CapabilityResponse(
                role,
                buildWorkspaceCapabilities(role, workspaceId, principal),
                buildProjectCapabilities(role, projectId, principal),
                buildLifecycleCapabilities(role, projectId, principal)
            );

            return Promise.of(HttpResponse.ok200()
                .withJson(toJson(response))
                .build());

        } catch (AccessDeniedException e) {
            log.warn("Access denied for capabilities request: {}", e.getMessage());
            return Promise.of(HttpResponse.ofCode(403)
                .withJson("{\"error\":\"Forbidden: " + e.getMessage() + "\"}")
                .build());
        } catch (Exception e) {
            log.error("Error retrieving capabilities", e);
            return Promise.of(HttpResponse.ofCode(500)
                .withJson("{\"error\":\"Internal error retrieving capabilities\"}")
                .build());
        }
    }

    /**
     * Determines the user's role in the given context.
     */
    private String determineRole(
            @NotNull Principal principal,
            @Nullable String workspaceId,
            @Nullable String projectId
    ) {
        // In a real implementation, this would query the workspace/project membership tables
        // For now, we derive from the principal's roles
        List<String> roles = principal.getRoles();
        if (roles.contains("OWNER")) return "OWNER";
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("DEVELOPER")) return "DEVELOPER";
        return "VIEWER";
    }

    /**
     * Builds workspace-level capabilities.
     */
    private Map<String, Boolean> buildWorkspaceCapabilities(
            @NotNull String role,
            @Nullable String workspaceId,
            @NotNull Principal principal
    ) {
        Map<String, Boolean> capabilities = new HashMap<>();

        switch (role) {
            case "OWNER" -> {
                capabilities.put("read", true);
                capabilities.put("update", true);
                capabilities.put("delete", true);
                capabilities.put("members:read", true);
                capabilities.put("members:update", true);
                capabilities.put("projects:create", true);
            }
            case "ADMIN" -> {
                capabilities.put("read", true);
                capabilities.put("update", true);
                capabilities.put("delete", false);
                capabilities.put("members:read", true);
                capabilities.put("members:update", true);
                capabilities.put("projects:create", true);
            }
            case "DEVELOPER" -> {
                capabilities.put("read", true);
                capabilities.put("update", false);
                capabilities.put("delete", false);
                capabilities.put("members:read", true);
                capabilities.put("members:update", false);
                capabilities.put("projects:create", true);
            }
            case "VIEWER" -> {
                capabilities.put("read", true);
                capabilities.put("update", false);
                capabilities.put("delete", false);
                capabilities.put("members:read", true);
                capabilities.put("members:update", false);
                capabilities.put("projects:create", false);
            }
        }

        return capabilities;
    }

    /**
     * Builds project-level capabilities.
     */
    private Map<String, Boolean> buildProjectCapabilities(
            @NotNull String role,
            @Nullable String projectId,
            @NotNull Principal principal
    ) {
        Map<String, Boolean> capabilities = new HashMap<>();

        switch (role) {
            case "OWNER" -> {
                capabilities.put("read", true);
                capabilities.put("update", true);
                capabilities.put("delete", true);
                capabilities.put("include", true);
                capabilities.put("export", true);
            }
            case "ADMIN" -> {
                capabilities.put("read", true);
                capabilities.put("update", true);
                capabilities.put("delete", true);
                capabilities.put("include", true);
                capabilities.put("export", true);
            }
            case "DEVELOPER" -> {
                capabilities.put("read", true);
                capabilities.put("update", true);
                capabilities.put("delete", false);
                capabilities.put("include", false);
                capabilities.put("export", true);
            }
            case "VIEWER" -> {
                capabilities.put("read", true);
                capabilities.put("update", false);
                capabilities.put("delete", false);
                capabilities.put("include", false);
                capabilities.put("export", false);
            }
        }

        return capabilities;
    }

    /**
     * Builds lifecycle phase capabilities.
     */
    private Map<String, Map<String, Boolean>> buildLifecycleCapabilities(
            @NotNull String role,
            @Nullable String projectId,
            @NotNull Principal principal
    ) {
        Map<String, Map<String, Boolean>> lifecycle = new HashMap<>();

        // Intent phase
        lifecycle.put("intent", buildPhaseCapabilities(role, "intent"));

        // Shape phase
        lifecycle.put("shape", buildPhaseCapabilities(role, "shape"));

        // Validate phase
        lifecycle.put("validate", buildPhaseCapabilities(role, "validate"));

        // Generate phase
        lifecycle.put("generate", buildPhaseCapabilities(role, "generate"));

        // Run phase
        lifecycle.put("run", buildPhaseCapabilities(role, "run"));

        // Observe phase
        lifecycle.put("observe", buildPhaseCapabilities(role, "observe"));

        // Learn phase
        lifecycle.put("learn", buildPhaseCapabilities(role, "learn"));

        // Evolve phase
        lifecycle.put("evolve", buildPhaseCapabilities(role, "evolve"));

        return lifecycle;
    }

    /**
     * Builds capabilities for a specific lifecycle phase.
     */
    private Map<String, Boolean> buildPhaseCapabilities(@NotNull String role, @NotNull String phase) {
        Map<String, Boolean> capabilities = new HashMap<>();

        boolean canCreate = role.equals("OWNER") || role.equals("ADMIN") || role.equals("DEVELOPER");
        boolean canDelete = role.equals("OWNER") || role.equals("ADMIN");
        boolean canApprove = role.equals("OWNER") || role.equals("ADMIN") || role.equals("DEVELOPER");
        boolean canRollback = role.equals("OWNER") || role.equals("ADMIN");

        capabilities.put("read", true); // All roles can read
        capabilities.put("create", canCreate);
        capabilities.put("update", canCreate);
        capabilities.put("delete", canDelete);
        capabilities.put("approve", canApprove);
        capabilities.put("reject", canApprove);
        capabilities.put("rollback", canRollback);

        return capabilities;
    }

    private String extractQueryParameter(@NotNull HttpRequest request, @NotNull String paramName) {
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2 && parts[0].equals(paramName)) {
                return parts[1];
            }
        }
        return null;
    }

    private String toJson(@NotNull CapabilityResponse response) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"role\":\"").append(response.role()).append("\",");
        json.append("\"workspace\":").append(mapToJson(response.workspaceCapabilities())).append(",");
        json.append("\"project\":").append(mapToJson(response.projectCapabilities())).append(",");
        json.append("\"lifecycle\":").append(lifecycleMapToJson(response.lifecycleCapabilities()));
        json.append("}");
        return json.toString();
    }

    private String mapToJson(@NotNull Map<String, Boolean> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Boolean> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private String lifecycleMapToJson(@NotNull Map<String, Map<String, Boolean>> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Map<String, Boolean>> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(mapToJson(entry.getValue()));
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Response DTO for capabilities.
     */
    private record CapabilityResponse(
            String role,
            Map<String, Boolean> workspaceCapabilities,
            Map<String, Boolean> projectCapabilities,
            Map<String, Map<String, Boolean>> lifecycleCapabilities
    ) {}
}
