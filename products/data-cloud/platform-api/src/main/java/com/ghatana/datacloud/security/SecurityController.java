/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.security;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST controller for security endpoints.
 *
 * @doc.type class
 * @doc.purpose Security API endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
public class SecurityController implements AsyncServlet {

    private final AuditLogService auditLogService;
    private final RBACService rbacService;

    public SecurityController(AuditLogService auditLogService, RBACService rbacService) {
        this.auditLogService = auditLogService;
        this.rbacService = rbacService;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String path = request.getRelativePath();
        String method = request.getMethod().toString();

        if ("GET".equals(method) && path.endsWith("/audit/logs")) {
            return queryAuditLogs(request);
        } else if ("GET".equals(method) && path.matches(".*/audit/logs/[^/]+")) {
            return getAuditLog(request, extractId(path));
        } else if ("POST".equals(method) && path.endsWith("/audit/export")) {
            return exportAuditLogs(request);
        } else if ("GET".equals(method) && path.endsWith("/rbac/roles")) {
            return listRoles(request);
        } else if ("GET".equals(method) && path.matches(".*/rbac/roles/[^/]+")) {
            return getRole(request, extractId(path));
        } else if ("POST".equals(method) && path.endsWith("/rbac/roles")) {
            return createRole(request);
        } else if ("POST".equals(method) && path.matches(".*/rbac/users/[^/]+/roles")) {
            return assignRole(request, extractUserId(path));
        } else if ("GET".equals(method) && path.matches(".*/rbac/users/[^/]+/permissions")) {
            return getUserPermissions(request, extractUserId(path));
        } else if ("POST".equals(method) && path.endsWith("/rbac/check")) {
            return checkPermission(request);
        }

        return notFound();
    }

    private Promise<HttpResponse> queryAuditLogs(HttpRequest request) {
        String tenantId = extractTenantId(request);

        // Parse query parameters
        AuditLogService.AuditQuery query = AuditLogService.AuditQuery.builder()
            .limit(100)
            .build();

        return auditLogService.query(tenantId, query)
            .then(events -> okJson(Map.of("events", events, "total", events.size())));
    }

    private Promise<HttpResponse> getAuditLog(HttpRequest request, String eventId) {
        return auditLogService.getEvent(eventId)
            .then(eventOpt -> {
                if (eventOpt.isPresent()) {
                    return okJson(eventOpt.get());
                } else {
                    return notFound("Event not found");
                }
            });
    }

    private Promise<HttpResponse> exportAuditLogs(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String tenantId = extractTenantId(request);
                    String format = (String) bodyMap.getOrDefault("format", "JSON");

                    return auditLogService.export(
                        tenantId,
                        java.time.Instant.parse((String) bodyMap.get("startTime")),
                        java.time.Instant.parse((String) bodyMap.get("endTime")),
                        AuditLogService.ExportFormat.valueOf(format)
                    ).then(data -> Promise.of(HttpResponse.ok200()
                        .withHeader(HttpHeaders.of("Content-Type"), "application/octet-stream")
                        .withHeader(HttpHeaders.of("Content-Disposition"), "attachment; filename=\"audit-export." + format.toLowerCase() + "\"")
                        .withBody(data)
                        .build()));
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> listRoles(HttpRequest request) {
        String tenantId = extractTenantId(request);
        return rbacService.listRoles(tenantId)
            .then(roles -> okJson(Map.of("roles", roles, "total", roles.size())));
    }

    private Promise<HttpResponse> getRole(HttpRequest request, String roleId) {
        return rbacService.getRole(roleId)
            .then(roleOpt -> {
                if (roleOpt.isPresent()) {
                    return okJson(roleOpt.get());
                } else {
                    return notFound("Role not found");
                }
            });
    }

    private Promise<HttpResponse> createRole(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    // Parse role definition
                    RBACService.Role role = parseRole(body.asString(StandardCharsets.UTF_8));
                    return rbacService.saveRole(role)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> assignRole(HttpRequest request, String userId) {
        String tenantId = extractTenantId(request);
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String roleId = (String) bodyMap.get("roleId");

                    return rbacService.assignRole(userId, tenantId, roleId)
                        .then(v -> okJson(Map.of("assigned", true)));
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> getUserPermissions(HttpRequest request, String userId) {
        String tenantId = extractTenantId(request);
        return rbacService.getUserPermissions(userId, tenantId)
            .then(permissions -> okJson(Map.of("permissions", permissions)));
    }

    private Promise<HttpResponse> checkPermission(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String userId = (String) bodyMap.get("userId");
                    String tenantId = extractTenantId(request);
                    RBACService.Permission permission = RBACService.Permission.valueOf((String) bodyMap.get("permission"));
                    String resource = (String) bodyMap.get("resource");

                    return rbacService.hasPermission(userId, tenantId, permission, resource)
                        .then(hasPermission -> okJson(Map.of(
                                "userId", userId,
                                "permission", permission.toString(),
                                "granted", hasPermission
                            )));
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private String extractId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (("logs".equals(parts[i]) || "roles".equals(parts[i])) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractUserId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("users".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-ID"));
        return tenantId != null ? tenantId : "default-tenant";
    }

    private RBACService.Role parseRole(String json) {
        // Simplified parsing - in production use Jackson
        return new RBACService.Role(
            "new-role", "New Role", "", "tenant-alpha",
            java.util.Set.of(), java.util.List.of(), false, 0
        );
    }

    private Map<String, Object> parseJson(String json) {
        // Simplified - in production use Jackson
        return Map.of();
    }

    private String toJson(Object obj) {
        // Simplified - in production use Jackson
        return "{}";
    }

    private Promise<HttpResponse> okJson(Object payload) {
        return Promise.of(HttpResponse.ok200().withJson(toJson(payload)).build());
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound(String message) {
        return Promise.of(HttpResponse.ofCode(404).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound() {
        return notFound("Not Found");
    }
}
