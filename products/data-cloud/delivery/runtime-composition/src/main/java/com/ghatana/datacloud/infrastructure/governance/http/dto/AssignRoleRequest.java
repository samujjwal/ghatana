package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * HTTP request DTO for assigning roles to principals.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP request data for role assignment with batch support.
 * Supports single role assignment to multiple principals.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AssignRoleRequest request = AssignRoleRequest.builder()
 *     .roleId("admin-role")
 *     .addPrincipalId("principal-123")
 *     .addPrincipalId("principal-456")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP request DTO for role assignment
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class AssignRoleRequest {
    private final String roleId;
    private final Set<String> principalIds;

    private AssignRoleRequest(String roleId, Set<String> principalIds) {
        this.roleId = Objects.requireNonNull(roleId, "roleId cannot be null");
        this.principalIds = new HashSet<>(principalIds != null ? principalIds : Set.of());

        if (roleId.isBlank()) {
            throw new IllegalArgumentException("roleId cannot be blank");
        }
        if (this.principalIds.isEmpty()) {
            throw new IllegalArgumentException("principalIds cannot be empty");
        }
        if (this.principalIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("principalIds cannot contain blank values");
        }
    }

    /**
     * Gets the role ID to assign.
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Gets set of principal IDs (defensive copy).
     */
    public Set<String> getPrincipalIds() {
        return new HashSet<>(principalIds);
    }

    /**
     * Gets number of principals to assign role to.
     */
    public int getPrincipalCount() {
        return principalIds.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String roleId;
        private Set<String> principalIds = new HashSet<>();

        public Builder roleId(String roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder addPrincipalId(String principalId) {
            this.principalIds.add(Objects.requireNonNull(principalId));
            return this;
        }

        public Builder principalIds(Set<String> principalIds) {
            this.principalIds = new HashSet<>(principalIds);
            return this;
        }

        public AssignRoleRequest build() {
            return new AssignRoleRequest(roleId, principalIds);
        }
    }

    @Override
    public String toString() {
        return "AssignRoleRequest{" +
                "roleId='" + roleId + '\'' +
                ", principalCount=" + principalIds.size() +
                '}';
    }
}
