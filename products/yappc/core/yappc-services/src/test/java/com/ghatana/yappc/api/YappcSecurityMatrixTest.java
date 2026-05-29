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
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.phase.PhaseActionAuthorizationService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security matrix covering tenant boundary, scopes, roles, feature flags, and policy denial.
 *
 * @doc.type test
 * @doc.purpose Verifies production YAPPC security gates fail closed across core authorization axes
 * @doc.layer api
 * @doc.pattern SecurityMatrixTest
 */
@DisplayName("YAPPC security matrix")
class YappcSecurityMatrixTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String PROJECT_ID = "project-1";

    private final RouteAuthorizationRegistry registry = new RouteAuthorizationRegistry(authorizationService());

    @Test
    @DisplayName("tenant boundary mismatch denies project access before permission grants")
    void tenantBoundaryMismatchDeniesProjectAccess() {
        YappcAuthorizationService service = authorizationService();
        Principal crossTenantOwner = principal(ProductRole.OWNER, "tenant-2");

        assertThatThrownBy(() -> service.authorizeProjectAccess(
                crossTenantOwner,
                TENANT_ID,
                WORKSPACE_ID,
                PROJECT_ID,
                Permission.PROJECT_READ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("request body scopes cannot escalate beyond authorized scopes")
    void requestBodyScopesCannotEscalateBeyondAuthorizedScopes() {
        assertThatCode(() -> RouteAuthorizationRegistry.validateBodyScopeAgainstAuthorized(
                "project:write",
                Set.of("project:*")))
                .doesNotThrowAnyException();
        assertThatCode(() -> RouteAuthorizationRegistry.validateBodyScopesAgainstAuthorized(
                Set.of("project:read", "workspace:read"),
                Set.of("admin")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> RouteAuthorizationRegistry.validateBodyScopeAgainstAuthorized(
                "tenant:admin",
                Set.of("project:read")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("role matrix denies viewer mutations and editor admin APIs")
    void roleMatrixDeniesViewerMutationsAndEditorAdminApis() {
        assertThatThrownBy(() -> registry.authorize(projectRequest(
                HttpMethod.POST,
                "/api/v1/yappc/run/retry",
                ProductRole.VIEWER)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Permission.PROJECT_UPDATE);

        assertThatThrownBy(() -> registry.authorize(adminRequest(
                HttpMethod.PUT,
                "/api/admin/feature-flags/yappc.generate",
                ProductRole.EDITOR)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(Permission.ADMIN_SYSTEM);

        assertThatCode(() -> registry.authorize(adminRequest(
                HttpMethod.PUT,
                "/api/admin/feature-flags/yappc.generate",
                ProductRole.ADMIN)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("feature flags and policy denial disable phase advance")
    void featureFlagsAndPolicyDenialDisablePhaseAdvance() {
        PhaseActionAuthorizationService service = new PhaseActionAuthorizationService();
        CapabilityEvaluationService.CapabilityModel capabilities = CapabilityEvaluationService.CapabilityModel.allGranted();
        PhasePacket.PhaseReadiness readiness = new PhasePacket.PhaseReadiness(
                true,
                "GENERATE",
                List.of(),
                1.0,
                false);

        PhasePacket.PhaseAction flagBlockedAdvance = advanceAction(service.determineAvailableActions(
                "GENERATE",
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of(),
                readiness,
                List.of(),
                List.of(),
                true,
                com.ghatana.yappc.services.phase.RunActionContext.degraded(List.of(), List.of())));
        assertThat(flagBlockedAdvance.enabled()).isFalse();
        assertThat(flagBlockedAdvance.disabledReason())
                .isEqualTo("phaseAction.disabled.phaseAdvanceEntitlementMissing");

        PhasePacket.PhaseAction policyBlockedAdvance = advanceAction(service.determineAvailableActions(
                "GENERATE",
                capabilities,
                PhasePacket.TenantTier.PRO,
                Set.of("phase.advance"),
                readiness,
                List.of(),
                List.of(new PhasePacket.GovernanceRecord(
                        "policy-denial-1",
                        "POLICY_DENIAL",
                        "Denied",
                        "system",
                        Instant.parse("2026-05-26T10:15:30Z"),
                        Map.of(),
                        "decision-1")),
                true,
                com.ghatana.yappc.services.phase.RunActionContext.degraded(List.of(), List.of())));
        assertThat(policyBlockedAdvance.enabled()).isFalse();
        assertThat(policyBlockedAdvance.disabledReason())
                .isEqualTo("phaseAction.disabled.policyDeniedTransition");
    }

    private static PhasePacket.PhaseAction advanceAction(List<PhasePacket.PhaseAction> actions) {
        return actions.stream()
                .filter(action -> "advance-phase".equals(action.actionId()))
                .findFirst()
                .orElseThrow();
    }

    private HttpRequest projectRequest(HttpMethod method, String path, ProductRole role) {
        HttpRequest request = request(method, path)
                .withHeader(HttpHeaders.of("X-Workspace-Id"), WORKSPACE_ID)
                .withHeader(HttpHeaders.of("X-Project-Id"), PROJECT_ID)
                .build();
        request.attach(Principal.class, principal(role, TENANT_ID));
        return request;
    }

    private HttpRequest adminRequest(HttpMethod method, String path, ProductRole role) {
        HttpRequest request = request(method, path)
                .withHeader(HttpHeaders.of("X-Workspace-Id"), WORKSPACE_ID)
                .build();
        request.attach(Principal.class, principal(role, TENANT_ID));
        return request;
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

    private static Principal principal(ProductRole role, String tenantId) {
        return new Principal(role.name().toLowerCase() + "-user", List.of(role.name()), tenantId);
    }

    private static YappcAuthorizationService authorizationService() {
        return new YappcAuthorizationService(new SyncAuthorizationService(new ProductRolePermissionRegistry()));
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
                        Permission.ADMIN_SYSTEM);
                case "ADMIN" -> Set.of(
                        Permission.WORKSPACE_READ,
                        Permission.WORKSPACE_UPDATE,
                        Permission.PROJECT_READ,
                        Permission.PROJECT_UPDATE,
                        Permission.PROJECT_DELETE,
                        Permission.ADMIN_SYSTEM);
                case "EDITOR" -> Set.of(
                        Permission.WORKSPACE_READ,
                        Permission.PROJECT_READ,
                        Permission.PROJECT_UPDATE);
                case "VIEWER" -> Set.of(
                        Permission.WORKSPACE_READ,
                        Permission.PROJECT_READ);
                default -> Set.of();
            };
        }

        @Override
        public void registerRole(String role, Set<String> permissions) {
        }
    }
}
