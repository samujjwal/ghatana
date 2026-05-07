package com.ghatana.datacloud.entity.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAssignmentTest {

    @Test
    void shouldBuildActiveAssignmentWithMetadata() { 
        Instant grantedAt = Instant.parse("2026-04-21T10:00:00Z");

        RoleAssignment assignment = RoleAssignment.builder() 
                .assignmentId("assignment-1")
                .tenantId("tenant-a")
                .principalId("user-1")
                .principalType(RoleAssignment.PrincipalType.GROUP) 
                .roleId("operator")
                .grantedAt(grantedAt) 
                .grantedBy("admin-user")
                .metadata("source", "ui") 
                .build(); 

        assertThat(assignment.getAssignmentId()).isEqualTo("assignment-1");
        assertThat(assignment.getTenantId()).isEqualTo("tenant-a");
        assertThat(assignment.getPrincipalId()).isEqualTo("user-1");
        assertThat(assignment.getPrincipalType()).isEqualTo(RoleAssignment.PrincipalType.GROUP); 
        assertThat(assignment.getPrincipalType().getValue()).isEqualTo("group");
        assertThat(assignment.getRoleId()).isEqualTo("operator");
        assertThat(assignment.getGrantedAt()).isEqualTo(grantedAt); 
        assertThat(assignment.getGrantedBy()).isEqualTo("admin-user");
        assertThat(assignment.isActive()).isTrue(); 
        assertThat(assignment.getMetadata()).containsEntry("source", "ui"); 
    }

    @Test
    void shouldReportRevokedAssignmentsAsInactive() { 
        Instant revokedAt = Instant.parse("2026-04-21T11:00:00Z");

        RoleAssignment assignment = RoleAssignment.builder() 
                .assignmentId("assignment-2")
                .tenantId("tenant-a")
                .principalId("svc-1")
                .principalType(RoleAssignment.PrincipalType.SERVICE_ACCOUNT) 
                .roleId("admin")
                .grantedBy("bootstrap")
                .revokedAt(revokedAt, "security-admin") 
                .build(); 

        assertThat(assignment.isActive()).isFalse(); 
        assertThat(assignment.getRevokedAt()).isEqualTo(revokedAt); 
        assertThat(assignment.getRevokedBy()).isEqualTo("security-admin");
        assertThat(assignment.toString()).contains("active=false", "SERVICE_ACCOUNT"); 
    }

    @Test
    void shouldExposeImmutableMetadata() { 
        RoleAssignment assignment = RoleAssignment.builder() 
                .tenantId("tenant-a")
                .principalId("user-1")
                .roleId("viewer")
                .metadata("reason", "support") 
                .build(); 

        assertThatThrownBy(() -> assignment.getMetadata().put("other", "value")) 
                .isInstanceOf(UnsupportedOperationException.class); 
    }

    @Test
    void shouldCompareAssignmentsUsingIdentityAndActiveState() { 
        RoleAssignment active = RoleAssignment.builder() 
                .assignmentId("assignment-3")
                .tenantId("tenant-a")
                .principalId("user-1")
                .roleId("viewer")
                .build(); 

        RoleAssignment equivalentActive = RoleAssignment.builder() 
                .assignmentId("assignment-3")
                .tenantId("tenant-a")
                .principalId("user-1")
                .roleId("viewer")
                .metadata("source", "seed") 
                .build(); 

        RoleAssignment revoked = RoleAssignment.builder() 
                .assignmentId("assignment-3")
                .tenantId("tenant-a")
                .principalId("user-1")
                .roleId("viewer")
                .revokedAt(Instant.parse("2026-04-21T12:00:00Z"), "admin-user")
                .build(); 

        assertThat(active).isEqualTo(equivalentActive); 
        assertThat(active.hashCode()).isEqualTo(equivalentActive.hashCode()); 
        assertThat(active).isNotEqualTo(revoked); 
    }
}