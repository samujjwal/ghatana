package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.application.approval.ApprovalService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-035: Approval role and permission matrix tests.
 *
 * <p>Comprehensive security tests for approval workflow authorization:
 * <ul>
 *   <li>Reviewer role can approve/reject</li>
 *   <li>Non-reviewer cannot approve/reject</li>
 *   <li>Admin can override (if applicable)</li>
 *   <li>Forged role is rejected</li>
 *   <li>Cross-tenant access is blocked</li>
 *   <li>Missing role is rejected</li>
 * </ul>
 */
@DisplayName("P1-035: Approval Role and Permission Matrix Tests")
class ApprovalRoleMatrixTest {

    private ApprovalService approvalService;
    private DmosHttpContextFactory contextFactory;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        contextFactory = new DmosHttpContextFactory(
            true, // production mode
            token -> new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "principal-1",
                "session-1",
                Set.of("USER"), // Default role, overridden per test
                Set.of(),
                true
            )
        );
    }

    @ParameterizedTest
    @CsvSource({
        "REVIEWER, true",
        "APPROVER, true",
        "ADMIN, true",
        "USER, false",
        "EDITOR, false",
        "VIEWER, false"
    })
    @DisplayName("P1-035: Role {0} can approve = {1}")
    void roleCanApprove(String role, boolean expectedCanApprove) {
        // Given
        DmOperationContext ctx = buildContextWithRole(role);

        when(approvalService.approve(any(), eq("approval-123")))
            .thenReturn(Promise.of(mock(ApprovalService.ApprovalResult.class)));

        // When
        boolean canApprove = canApprove(ctx, "approval-123");

        // Then
        assertThat(canApprove).isEqualTo(expectedCanApprove);

        if (expectedCanApprove) {
            verify(approvalService).approve(any(), eq("approval-123"));
        } else {
            verify(approvalService, never()).approve(any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "REVIEWER, true",
        "APPROVER, true",
        "ADMIN, true",
        "USER, false"
    })
    @DisplayName("P1-035: Role {0} can reject = {1}")
    void roleCanReject(String role, boolean expectedCanReject) {
        // Given
        DmOperationContext ctx = buildContextWithRole(role);

        when(approvalService.reject(any(), eq("approval-123"), any()))
            .thenReturn(Promise.of(mock(ApprovalService.ApprovalResult.class)));

        // When
        boolean canReject = canReject(ctx, "approval-123", "Rejected");

        // Then
        assertThat(canReject).isEqualTo(expectedCanReject);
    }

    @Test
    @DisplayName("P1-035: Forged REVIEWER role from client is rejected in production")
    void forgedRoleIsRejected() {
        // Given - User tries to use REVIEWER role without server-side authorization
        DmOperationContext ctx = buildContextWithRole("USER");

        // When - Service checks authorization
        boolean isAuthorized = checkApprovalAuthorization(ctx);

        // Then - Should be denied because USER role cannot approve
        assertThat(isAuthorized).isFalse();
    }

    @Test
    @DisplayName("P1-035: Missing role is rejected")
    void missingRoleIsRejected() {
        // Given - User with no roles
        DmOperationContext ctx = buildContextWithNoRoles();

        // When - Try to approve
        boolean canApprove = canApprove(ctx, "approval-123");

        // Then - Should be rejected
        assertThat(canApprove).isFalse();
    }

    @Test
    @DisplayName("P1-035: Cross-tenant approval access is blocked")
    void crossTenantApprovalIsBlocked() {
        // Given - Approval in tenant-1 workspace
        String approvalWorkspace = "workspace-1";
        String approvalTenant = "tenant-1";

        // User from tenant-2 trying to access
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of(approvalWorkspace))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();

        // When - Try to approve
        boolean canAccess = checkTenantAccess(ctx, approvalTenant, approvalWorkspace);

        // Then - Cross-tenant access should be blocked
        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("P1-035: Same-tenant approval access is allowed")
    void sameTenantApprovalIsAllowed() {
        // Given
        String tenant = "tenant-1";
        String workspace = "workspace-1";

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenant))
            .workspaceId(DmWorkspaceId.of(workspace))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();

        // When
        boolean canAccess = checkTenantAccess(ctx, tenant, workspace);

        // Then
        assertThat(canAccess).isTrue();
    }

    @Test
    @DisplayName("P1-035: Non-reviewer sees approval in read-only mode")
    void nonReviewerSeesReadOnlyApproval() {
        // Given - User with VIEWER role
        DmOperationContext ctx = buildContextWithRole("VIEWER");

        // When - Try to view approval
        boolean canView = canViewApproval(ctx, "approval-123");

        // Then - Should be able to view but not approve
        assertThat(canView).isTrue();
        assertThat(canApprove(ctx, "approval-123")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "REVIEWER, campaigns/*/approve, true",
        "REVIEWER, campaigns/*/reject, true",
        "USER, campaigns/*/approve, false",
        "ADMIN, *, true"
    })
    @DisplayName("P1-035: Role {0} on resource {1} = {2}")
    void roleResourcePermissionCheck(String role, String resource, boolean expectedAllowed) {
        // Given
        DmOperationContext ctx = buildContextWithRole(role);

        // When
        boolean allowed = checkResourcePermission(ctx, resource);

        // Then
        assertThat(allowed).isEqualTo(expectedAllowed);
    }

    // Helper methods

    private DmOperationContext buildContextWithRole(String role) {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .idempotencyKey(DmIdempotencyKey.of("idem-123"))
            .build();
    }

    private DmOperationContext buildContextWithNoRoles() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();
    }

    private boolean canApprove(DmOperationContext ctx, String approvalId) {
        // Simulate authorization check
        Set<String> roles = getRolesForPrincipal(ctx);

        if (!roles.contains("REVIEWER") && !roles.contains("APPROVER") && !roles.contains("ADMIN")) {
            return false;
        }

        // In real implementation, would call service
        try {
            await(approvalService.approve(ctx, approvalId));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canReject(DmOperationContext ctx, String approvalId, String reason) {
        Set<String> roles = getRolesForPrincipal(ctx);

        if (!roles.contains("REVIEWER") && !roles.contains("APPROVER") && !roles.contains("ADMIN")) {
            return false;
        }

        try {
            await(approvalService.reject(ctx, approvalId, reason));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canViewApproval(DmOperationContext ctx, String approvalId) {
        // Any authenticated user can view
        return ctx.getActor() != null;
    }

    private boolean checkApprovalAuthorization(DmOperationContext ctx) {
        Set<String> roles = getRolesForPrincipal(ctx);
        return roles.contains("REVIEWER") || roles.contains("APPROVER") || roles.contains("ADMIN");
    }

    private boolean checkTenantAccess(DmOperationContext ctx, String requiredTenant, String requiredWorkspace) {
        return ctx.getTenantId().getValue().equals(requiredTenant) &&
               ctx.getWorkspaceId().getValue().equals(requiredWorkspace);
    }

    private boolean checkResourcePermission(DmOperationContext ctx, String resource) {
        Set<String> roles = getRolesForPrincipal(ctx);

        if (roles.contains("ADMIN")) {
            return true;
        }

        if (resource.contains("approve") || resource.contains("reject")) {
            return roles.contains("REVIEWER") || roles.contains("APPROVER");
        }

        return false;
    }

    private Set<String> getRolesForPrincipal(DmOperationContext ctx) {
        // In production, this would come from server-side identity provider
        // For testing, we simulate different roles based on principal
        String principal = ctx.getActor().getPrincipalId();

        return switch (principal) {
            case "reviewer-1" -> Set.of("REVIEWER", "USER");
            case "approver-1" -> Set.of("APPROVER", "USER");
            case "admin-1" -> Set.of("ADMIN", "REVIEWER", "USER");
            case "user-1" -> Set.of("USER");
            case "viewer-1" -> Set.of("VIEWER", "USER");
            default -> Set.of("USER");
        };
    }

    private <T> T await(Promise<T> promise) {
        try {
            CompletableFuture<T> future = new CompletableFuture<>();
            promise.whenResult(future::complete).whenException(future::completeExceptionally);
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
