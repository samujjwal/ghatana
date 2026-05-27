/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Manifest-backed route authorization matrix for product roles.
 *
 * @doc.type class
 * @doc.purpose Proves owner/admin/editor/viewer route authorization for protected YAPPC actions
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@DisplayName("Route authorization role matrix")
class RouteAuthorizationRoleMatrixTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String PROJECT_ID = "project-1";

    private final RouteAuthorizationRegistry registry = new RouteAuthorizationRegistry(
        new YappcAuthorizationService(new SyncAuthorizationService(new ProductRolePermissionRegistry()))
    );

    @Test
    @DisplayName("all product roles can read phase packets")
    void allProductRolesCanReadPhasePackets() {
        for (ProductRole role : ProductRole.values()) {
            assertThatCode(() -> registry.authorize(projectRequest(
                HttpMethod.GET,
                "/api/v1/phase/packet",
                role
            ))).as("%s should read phase packet", role).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("viewer cannot execute mutating project actions")
    void viewerCannotExecuteMutatingProjectActions() {
        assertThatThrownBy(() -> registry.authorize(projectRequest(
            HttpMethod.POST,
            "/api/v1/yappc/run/retry",
            ProductRole.VIEWER
        )))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(Permission.PROJECT_UPDATE);

        assertThatThrownBy(() -> registry.authorize(projectRequest(
            HttpMethod.POST,
            "/api/v1/yappc/product-family/assets/asset-1/promotions",
            ProductRole.VIEWER
        )))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(Permission.PROJECT_UPDATE);
    }

    @Test
    @DisplayName("owner admin and editor can execute project write actions")
    void writeCapableRolesCanExecuteProjectActions() {
        for (ProductRole role : List.of(ProductRole.OWNER, ProductRole.ADMIN, ProductRole.EDITOR)) {
            assertThatCode(() -> registry.authorize(projectRequest(
                HttpMethod.POST,
                "/api/v1/yappc/run/retry",
                role
            ))).as("%s should retry run", role).doesNotThrowAnyException();

            assertThatCode(() -> registry.authorize(projectRequest(
                HttpMethod.POST,
                "/api/v1/yappc/product-family/assets/asset-1/promotions",
                role
            ))).as("%s should promote product-family asset", role).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("editor and viewer cannot execute admin APIs")
    void editorAndViewerCannotExecuteAdminApis() {
        for (ProductRole role : List.of(ProductRole.EDITOR, ProductRole.VIEWER)) {
            HttpRequest request = adminRequest(HttpMethod.PUT, "/api/admin/feature-flags/yappc.generate");
            request.attach(Principal.class, principal(role));

            assertThatThrownBy(() -> registry.authorize(request))
                .as("%s should not update admin feature flags", role)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Permission.ADMIN_SYSTEM);
        }
    }

    @Test
    @DisplayName("owner and admin can execute admin APIs")
    void ownerAndAdminCanExecuteAdminApis() {
        for (ProductRole role : List.of(ProductRole.OWNER, ProductRole.ADMIN)) {
            HttpRequest request = adminRequest(HttpMethod.PUT, "/api/admin/feature-flags/yappc.generate");
            request.attach(Principal.class, principal(role));

            assertThatCode(() -> registry.authorize(request))
                .as("%s should update admin feature flags", role)
                .doesNotThrowAnyException();
        }
    }

    private HttpRequest projectRequest(HttpMethod method, String path, ProductRole role) {
        HttpRequest request = request(method, path)
            .withHeader(HttpHeaders.of("X-Workspace-Id"), WORKSPACE_ID)
            .withHeader(HttpHeaders.of("X-Project-Id"), PROJECT_ID)
            .build();
        request.attach(Principal.class, principal(role));
        return request;
    }

    private HttpRequest adminRequest(HttpMethod method, String path) {
        return request(method, path)
            .withHeader(HttpHeaders.of("X-Workspace-Id"), WORKSPACE_ID)
            .build();
    }

    private HttpRequest.Builder request(HttpMethod method, String path) {
        String url = "http://localhost" + path;
        return switch (method) {
            case GET -> HttpRequest.get(url);
            case POST -> HttpRequest.post(url);
            case PUT -> HttpRequest.put(url);
            default -> throw new IllegalArgumentException("Unsupported test method: " + method);
        };
    }

    private Principal principal(ProductRole role) {
        return new Principal(role.name().toLowerCase() + "-user", List.of(role.name()), TENANT_ID);
    }

    private enum ProductRole {
        OWNER,
        ADMIN,
        EDITOR,
        VIEWER
    }

    private static final class ProductRolePermissionRegistry implements RolePermissionRegistry {
        @Override
        public Set<String> getPermissions(String role) {
            return switch (role) {
                case "OWNER" -> Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.WORKSPACE_UPDATE,
                    Permission.WORKSPACE_DELETE,
                    Permission.PROJECT_READ,
                    Permission.PROJECT_UPDATE,
                    Permission.PROJECT_DELETE,
                    Permission.ADMIN_SYSTEM
                );
                case "ADMIN" -> Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.WORKSPACE_UPDATE,
                    Permission.PROJECT_READ,
                    Permission.PROJECT_UPDATE,
                    Permission.PROJECT_DELETE,
                    Permission.ADMIN_SYSTEM
                );
                case "EDITOR" -> Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.PROJECT_READ,
                    Permission.PROJECT_UPDATE
                );
                case "VIEWER" -> Set.of(
                    Permission.WORKSPACE_READ,
                    Permission.PROJECT_READ
                );
                default -> Set.of();
            };
        }

        @Override
        public void registerRole(String role, Set<String> permissions) {
        }
    }
}
