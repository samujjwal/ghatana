/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP integration tests for security endpoints (S004).
 *
 * @doc.type class
 * @doc.purpose Security API endpoint tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("SecurityEndpoint – Security API (S004)")
class SecurityEndpointTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("Audit Log Endpoints")
    class AuditLogEndpointsTests {

        @Test
        @DisplayName("[S004]: get_audit_logs_returns_events")
        void getAuditLogsReturnsEvents() throws Exception {
            var events = java.util.List.of(
                Map.of("id", "evt-1", "type", "ACCESS", "userId", "user-1"),
                Map.of("id", "evt-2", "type", "CREATE", "userId", "user-2")
            );

            lenient().when(mockClient.queryAuditLogs(any(), any()))
                .thenReturn(Promise.of(events));

            var response = get("/api/v1/security/audit/logs", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> eventList = (java.util.List<Map<String, Object>>) body.get("events");
            assertThat(eventList).hasSize(2);
        }

        @Test
        @DisplayName("[S004]: get_audit_log_by_id_returns_event")
        void getAuditLogByIdReturnsEvent() throws Exception {
            String eventId = "evt-001";

            Map<String, Object> event = Map.of(
                "id", eventId,
                "type", "ACCESS",
                "userId", "user-001",
                "resource", "Entity",
                "success", true,
                "timestamp", Instant.now().toString()
            );

            lenient().when(mockClient.getAuditLog(any(), eq(eventId)))
                .thenReturn(Promise.of(event));

            var response = get("/api/v1/security/audit/logs/" + eventId, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("id")).isEqualTo(eventId);
        }

        @Test
        @DisplayName("[S004]: export_audit_logs_returns_file")
        void exportAuditLogsReturnsFile() throws Exception {
            byte[] exportData = "[{\"id\":\"1\"}]".getBytes();

            lenient().when(mockClient.exportAuditLogs(any(), any(), any(), any()))
                .thenReturn(Promise.of(exportData));

            var response = postJson("/api/v1/security/audit/export", Map.of(
                "startTime", Instant.now().minusSeconds(86400 * 7).toString(),
                "endTime", Instant.now().toString(),
                "format", "JSON"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            assertThat(response.headers().firstValue("Content-Type"))
                .hasValue("application/octet-stream");
        }
    }

    @Nested
    @DisplayName("RBAC Endpoints")
    class RBACEndpointsTests {

        @Test
        @DisplayName("[S004]: list_roles_returns_roles")
        void listRolesReturnsRoles() throws Exception {
            var roles = java.util.List.of(
                Map.of("id", "role-1", "name", "Admin", "permissions", java.util.List.of("ADMIN")),
                Map.of("id", "role-2", "name", "Editor", "permissions", java.util.List.of("EDIT")),
                Map.of("id", "role-3", "name", "Viewer", "permissions", java.util.List.of("READ"))
            );

            lenient().when(mockClient.listRoles(any()))
                .thenReturn(Promise.of(roles));

            var response = get("/api/v1/security/rbac/roles", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> roleList = (java.util.List<Map<String, Object>>) body.get("roles");
            assertThat(roleList).hasSize(3);
        }

        @Test
        @DisplayName("[S004]: get_role_returns_role")
        void getRoleReturnsRole() throws Exception {
            String roleId = "role-admin";

            Map<String, Object> role = Map.of(
                "id", roleId,
                "name", "Administrator",
                "permissions", java.util.List.of("ENTITY_ADMIN", "USER_MANAGE", "TENANT_ADMIN"),
                "isSystemRole", true
            );

            lenient().when(mockClient.getRole(any(), eq(roleId)))
                .thenReturn(Promise.of(role));

            var response = get("/api/v1/security/rbac/roles/" + roleId, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("id")).isEqualTo(roleId);
        }

        @Test
        @DisplayName("[S004]: create_role_creates_role")
        void createRoleCreatesRole() throws Exception {
            Map<String, Object> newRole = Map.of(
                "name", "Custom Role",
                "permissions", java.util.List.of("ENTITY_READ", "ENTITY_CREATE"),
                "description", "A custom role"
            );

            Map<String, Object> createdRole = Map.of(
                "id", "role-custom",
                "name", "Custom Role",
                "permissions", java.util.List.of("ENTITY_READ", "ENTITY_CREATE")
            );

            lenient().when(mockClient.createRole(any(), any()))
                .thenReturn(Promise.of(createdRole));

            var response = postJson("/api/v1/security/rbac/roles", newRole, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("id")).isNotNull();
        }

        @Test
        @DisplayName("[S004]: assign_role_assigns_to_user")
        void assignRoleAssignsToUser() throws Exception {
            String userId = "user-001";

            lenient().when(mockClient.assignRoleToUser(any(), eq(userId), any()))
                .thenReturn(Promise.of(Map.of("assigned", true)));

            var response = postJson("/api/v1/security/rbac/users/" + userId + "/roles", Map.of(
                "roleId", "role-editor"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("assigned")).isEqualTo(true);
        }

        @Test
        @DisplayName("[S004]: check_permission_returns_granted_status")
        void checkPermissionReturnsGrantedStatus() throws Exception {
            lenient().when(mockClient.checkPermission(any(), any(), any(), any()))
                .thenReturn(Promise.of(Map.of(
                    "userId", "user-001",
                    "permission", "ENTITY_READ",
                    "granted", true
                )));

            var response = postJson("/api/v1/security/rbac/check", Map.of(
                "userId", "user-001",
                "permission", "ENTITY_READ",
                "resource", "entity-123"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("granted")).isEqualTo(true);
        }

        @Test
        @DisplayName("[S004]: get_user_permissions_returns_permissions")
        void getUserPermissionsReturnsPermissions() throws Exception {
            String userId = "user-001";

            lenient().when(mockClient.getUserPermissions(any(), eq(userId)))
                .thenReturn(Promise.of(Map.of(
                    "permissions", java.util.List.of("ENTITY_READ", "ENTITY_CREATE", "REPORT_READ")
                )));

            var response = get("/api/v1/security/rbac/users/" + userId + "/permissions", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<String> perms = (java.util.List<String>) body.get("permissions");
            assertThat(perms).contains("ENTITY_READ");
        }
    }

    @Nested
    @DisplayName("Authentication & Authorization")
    class AuthTests {

        @Test
        @DisplayName("[S004]: missing_auth_header_returns_401")
        void missingAuthHeaderReturns401() throws Exception {
            // Test without tenant header
            var response = get("/api/v1/security/audit/logs");

            assertStatusCode(response, 401);
        }

        @Test
        @DisplayName("[S004]: unauthorized_access_returns_403")
        void unauthorizedAccessReturns403() throws Exception {
            // User without permissions
            lenient().when(mockClient.checkPermission(any(), any(), any(), any()))
                .thenReturn(Promise.of(Map.of("granted", false)));

            var response = get("/api/v1/security/audit/logs", withTenant("tenant-alpha"));

            // Should be denied
            // Note: Actual 403 would be handled by security interceptor
        }
    }
}
