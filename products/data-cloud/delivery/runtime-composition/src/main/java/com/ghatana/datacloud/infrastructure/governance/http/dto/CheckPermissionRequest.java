package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * HTTP request DTO for checking permissions.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP request data for permission checking operations.
 * Validates whether a principal has required permissions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CheckPermissionRequest request = CheckPermissionRequest.builder()
 *     .principalId("principal-123")
 *     .addPermission("users:read")
 *     .addPermission("users:write")
 *     .requireAllPermissions(true)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP request DTO for permission checking
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class CheckPermissionRequest {
    private final String principalId;
    private final Set<String> permissions;
    private final boolean requireAllPermissions;

    private CheckPermissionRequest(
            String principalId,
            Set<String> permissions,
            boolean requireAllPermissions) {
        this.principalId = Objects.requireNonNull(principalId, "principalId cannot be null");
        this.permissions = new HashSet<>(permissions != null ? permissions : Set.of());
        this.requireAllPermissions = requireAllPermissions;

        if (principalId.isBlank()) {
            throw new IllegalArgumentException("principalId cannot be blank");
        }
        if (this.permissions.isEmpty()) {
            throw new IllegalArgumentException("permissions cannot be empty");
        }
        if (this.permissions.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("permissions cannot contain blank values");
        }
    }

    /**
     * Gets the principal ID.
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Gets set of permissions to check (defensive copy).
     */
    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }

    /**
     * Gets whether ALL permissions are required (vs. ANY).
     */
    public boolean isRequireAllPermissions() {
        return requireAllPermissions;
    }

    /**
     * Gets the logical operator for permission checking.
     */
    public String getLogicalOperator() {
        return requireAllPermissions ? "ALL" : "ANY";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String principalId;
        private Set<String> permissions = new HashSet<>();
        private boolean requireAllPermissions = false;

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder addPermission(String permission) {
            this.permissions.add(Objects.requireNonNull(permission));
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = new HashSet<>(permissions);
            return this;
        }

        public Builder requireAllPermissions(boolean requireAllPermissions) {
            this.requireAllPermissions = requireAllPermissions;
            return this;
        }

        public CheckPermissionRequest build() {
            return new CheckPermissionRequest(principalId, permissions, requireAllPermissions);
        }
    }

    @Override
    public String toString() {
        return "CheckPermissionRequest{" +
                "principalId='" + principalId + '\'' +
                ", permissionCount=" + permissions.size() +
                ", operator=" + getLogicalOperator() +
                '}';
    }
}
