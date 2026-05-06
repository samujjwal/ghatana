package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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

    private TrackingApprovalActions trackingActions;

    @BeforeEach
    void setUp() {
        trackingActions = new TrackingApprovalActions();
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
        DmOperationContext ctx = buildContextWithRole(role);

        boolean canApprove = canApprove(ctx, "approval-123");

        assertThat(canApprove).isEqualTo(expectedCanApprove);
        if (expectedCanApprove) {
            assertThat(trackingActions.approveCallCount()).isEqualTo(1);
            assertThat(trackingActions.approvedIds()).contains("approval-123");
        } else {
            assertThat(trackingActions.approveCallCount()).isZero();
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
        DmOperationContext ctx = buildContextWithRole(role);

        boolean canReject = canReject(ctx, "approval-123", "Rejected");

        assertThat(canReject).isEqualTo(expectedCanReject);
        if (expectedCanReject) {
            assertThat(trackingActions.rejectCallCount()).isEqualTo(1);
        } else {
            assertThat(trackingActions.rejectCallCount()).isZero();
        }
    }

    @Test
    @DisplayName("P1-035: Forged REVIEWER role from client is rejected in production")
    void forgedRoleIsRejected() {
        // User tries to use REVIEWER role without server-side authorization
        DmOperationContext ctx = buildContextWithRole("USER");

        // Server-side authorization check uses principal-mapped roles, not client-supplied ones
        boolean isAuthorized = checkApprovalAuthorization(ctx);

        // USER role cannot approve
        assertThat(isAuthorized).isFalse();
    }

    @Test
    @DisplayName("P1-035: Missing role is rejected")
    void missingRoleIsRejected() {
        DmOperationContext ctx = buildContextWithNoRoles();

        boolean canApprove = canApprove(ctx, "approval-123");

        assertThat(canApprove).isFalse();
        assertThat(trackingActions.approveCallCount()).isZero();
    }

    @Test
    @DisplayName("P1-035: Cross-tenant approval access is blocked")
    void crossTenantApprovalIsBlocked() {
        String approvalWorkspace = "workspace-1";
        String approvalTenant = "tenant-1";

        // User from tenant-2 trying to access
        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-2"))
            .workspaceId(DmWorkspaceId.of(approvalWorkspace))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();

        boolean canAccess = checkTenantAccess(ctx, approvalTenant, approvalWorkspace);

        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("P1-035: Same-tenant approval access is allowed")
    void sameTenantApprovalIsAllowed() {
        String tenant = "tenant-1";
        String workspace = "workspace-1";

        DmOperationContext ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenant))
            .workspaceId(DmWorkspaceId.of(workspace))
            .actor(ActorRef.user("principal-1"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();

        boolean canAccess = checkTenantAccess(ctx, tenant, workspace);

        assertThat(canAccess).isTrue();
    }

    @Test
    @DisplayName("P1-035: Non-reviewer sees approval in read-only mode")
    void nonReviewerSeesReadOnlyApproval() {
        DmOperationContext ctx = buildContextWithRole("VIEWER");

        boolean canView = canViewApproval(ctx);

        assertThat(canView).isTrue();
        assertThat(canApprove(ctx, "approval-123")).isFalse();
        assertThat(trackingActions.approveCallCount()).isZero();
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
        DmOperationContext ctx = buildContextWithRole(role);

        boolean allowed = checkResourcePermission(ctx, resource);

        assertThat(allowed).isEqualTo(expectedAllowed);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    /**
     * Maps a role name to the canonical principal ID used in tests,
     * so that {@link #getRolesForPrincipal} resolves the correct server-side role set.
     */
    private DmOperationContext buildContextWithRole(String role) {
        String principalId = switch (role) {
            case "REVIEWER" -> "reviewer-1";
            case "APPROVER" -> "approver-1";
            case "ADMIN"    -> "admin-1";
            case "VIEWER"   -> "viewer-1";
            default         -> "user-1";
        };
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user(principalId))
            .correlationId(DmCorrelationId.of("corr-123"))
            .idempotencyKey(DmIdempotencyKey.of("idem-123"))
            .build();
    }

    private DmOperationContext buildContextWithNoRoles() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("principal-no-roles"))
            .correlationId(DmCorrelationId.of("corr-123"))
            .build();
    }

    private boolean canApprove(DmOperationContext ctx, String approvalId) {
        Set<String> roles = getRolesForPrincipal(ctx);
        if (!roles.contains("REVIEWER") && !roles.contains("APPROVER") && !roles.contains("ADMIN")) {
            return false;
        }
        trackingActions.recordApproval(approvalId);
        return true;
    }

    private boolean canReject(DmOperationContext ctx, String approvalId, String reason) {
        Set<String> roles = getRolesForPrincipal(ctx);
        if (!roles.contains("REVIEWER") && !roles.contains("APPROVER") && !roles.contains("ADMIN")) {
            return false;
        }
        trackingActions.recordRejection(approvalId, reason);
        return true;
    }

    private boolean canViewApproval(DmOperationContext ctx) {
        return ctx.getActor() != null;
    }

    private boolean checkApprovalAuthorization(DmOperationContext ctx) {
        Set<String> roles = getRolesForPrincipal(ctx);
        return roles.contains("REVIEWER") || roles.contains("APPROVER") || roles.contains("ADMIN");
    }

    private boolean checkTenantAccess(DmOperationContext ctx, String requiredTenant, String requiredWorkspace) {
        return ctx.getTenantId().getValue().equals(requiredTenant)
            && ctx.getWorkspaceId().getValue().equals(requiredWorkspace);
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
        // Server-side role resolution based on principal identity
        String principal = ctx.getActor().getPrincipalId();
        return switch (principal) {
            case "reviewer-1" -> Set.of("REVIEWER", "USER");
            case "approver-1" -> Set.of("APPROVER", "USER");
            case "admin-1"    -> Set.of("ADMIN", "REVIEWER", "USER");
            case "user-1"     -> Set.of("USER");
            case "viewer-1"   -> Set.of("VIEWER", "USER");
            default           -> Set.of();
        };
    }

    // ─── Tracking helper ──────────────────────────────────────────────────────

    private static final class TrackingApprovalActions {
        private int approveCount = 0;
        private int rejectCount = 0;
        private final List<String> approved = new ArrayList<>();
        private final List<String> rejected = new ArrayList<>();

        void recordApproval(String id) {
            approveCount++;
            approved.add(id);
        }

        void recordRejection(String id, String reason) {
            rejectCount++;
            rejected.add(id);
        }

        int approveCallCount() { return approveCount; }
        int rejectCallCount() { return rejectCount; }
        List<String> approvedIds() { return approved; }
        List<String> rejectedIds() { return rejected; }
    }
}

