package com.ghatana.datacloud.entity.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAssignmentTest {

    @Test
    void shouldBuildActiveAssignmentWithMetadata() { // GH-90000
        Instant grantedAt = Instant.parse("2026-04-21T10:00:00Z [GH-90000]");

        RoleAssignment assignment = RoleAssignment.builder() // GH-90000
                .assignmentId("assignment-1 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .principalId("user-1 [GH-90000]")
                .principalType(RoleAssignment.PrincipalType.GROUP) // GH-90000
                .roleId("operator [GH-90000]")
                .grantedAt(grantedAt) // GH-90000
                .grantedBy("admin-user [GH-90000]")
                .metadata("source", "ui") // GH-90000
                .build(); // GH-90000

        assertThat(assignment.getAssignmentId()).isEqualTo("assignment-1 [GH-90000]");
        assertThat(assignment.getTenantId()).isEqualTo("tenant-a [GH-90000]");
        assertThat(assignment.getPrincipalId()).isEqualTo("user-1 [GH-90000]");
        assertThat(assignment.getPrincipalType()).isEqualTo(RoleAssignment.PrincipalType.GROUP); // GH-90000
        assertThat(assignment.getPrincipalType().getValue()).isEqualTo("group [GH-90000]");
        assertThat(assignment.getRoleId()).isEqualTo("operator [GH-90000]");
        assertThat(assignment.getGrantedAt()).isEqualTo(grantedAt); // GH-90000
        assertThat(assignment.getGrantedBy()).isEqualTo("admin-user [GH-90000]");
        assertThat(assignment.isActive()).isTrue(); // GH-90000
        assertThat(assignment.getMetadata()).containsEntry("source", "ui"); // GH-90000
    }

    @Test
    void shouldReportRevokedAssignmentsAsInactive() { // GH-90000
        Instant revokedAt = Instant.parse("2026-04-21T11:00:00Z [GH-90000]");

        RoleAssignment assignment = RoleAssignment.builder() // GH-90000
                .assignmentId("assignment-2 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .principalId("svc-1 [GH-90000]")
                .principalType(RoleAssignment.PrincipalType.SERVICE_ACCOUNT) // GH-90000
                .roleId("admin [GH-90000]")
                .grantedBy("bootstrap [GH-90000]")
                .revokedAt(revokedAt, "security-admin") // GH-90000
                .build(); // GH-90000

        assertThat(assignment.isActive()).isFalse(); // GH-90000
        assertThat(assignment.getRevokedAt()).isEqualTo(revokedAt); // GH-90000
        assertThat(assignment.getRevokedBy()).isEqualTo("security-admin [GH-90000]");
        assertThat(assignment.toString()).contains("active=false", "SERVICE_ACCOUNT"); // GH-90000
    }

    @Test
    void shouldExposeImmutableMetadata() { // GH-90000
        RoleAssignment assignment = RoleAssignment.builder() // GH-90000
                .tenantId("tenant-a [GH-90000]")
                .principalId("user-1 [GH-90000]")
                .roleId("viewer [GH-90000]")
                .metadata("reason", "support") // GH-90000
                .build(); // GH-90000

        assertThatThrownBy(() -> assignment.getMetadata().put("other", "value")) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    @Test
    void shouldCompareAssignmentsUsingIdentityAndActiveState() { // GH-90000
        RoleAssignment active = RoleAssignment.builder() // GH-90000
                .assignmentId("assignment-3 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .principalId("user-1 [GH-90000]")
                .roleId("viewer [GH-90000]")
                .build(); // GH-90000

        RoleAssignment equivalentActive = RoleAssignment.builder() // GH-90000
                .assignmentId("assignment-3 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .principalId("user-1 [GH-90000]")
                .roleId("viewer [GH-90000]")
                .metadata("source", "seed") // GH-90000
                .build(); // GH-90000

        RoleAssignment revoked = RoleAssignment.builder() // GH-90000
                .assignmentId("assignment-3 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .principalId("user-1 [GH-90000]")
                .roleId("viewer [GH-90000]")
                .revokedAt(Instant.parse("2026-04-21T12:00:00Z [GH-90000]"), "admin-user")
                .build(); // GH-90000

        assertThat(active).isEqualTo(equivalentActive); // GH-90000
        assertThat(active.hashCode()).isEqualTo(equivalentActive.hashCode()); // GH-90000
        assertThat(active).isNotEqualTo(revoked); // GH-90000
    }
}