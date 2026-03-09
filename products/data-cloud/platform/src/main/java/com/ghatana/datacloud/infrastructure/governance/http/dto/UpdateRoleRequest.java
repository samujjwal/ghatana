package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;

/**
 * HTTP request DTO for updating an existing role.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP request data for role updates. Allows partial updates
 * (any field can be null to skip update).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * UpdateRoleRequest request = UpdateRoleRequest.builder()
 *     .roleName("Senior Administrator")
 *     .description("Updated description")
 *     .isActive(false)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP request DTO for role updates
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class UpdateRoleRequest {
    private final String roleName;
    private final String description;
    private final Boolean isActive;

    private UpdateRoleRequest(
            String roleName,
            String description,
            Boolean isActive) {
        this.roleName = roleName;
        this.description = description;
        this.isActive = isActive;

        // Validation if present
        if (roleName != null && roleName.isBlank()) {
            throw new IllegalArgumentException("roleName cannot be blank");
        }
        if (roleName != null && roleName.length() > 255) {
            throw new IllegalArgumentException("roleName exceeds max length (255)");
        }
        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException("description exceeds max length (1000)");
        }
    }

    /**
     * Gets role name update (null if not provided).
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * Gets description update (null if not provided).
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets active status update (null if not provided).
     */
    public Boolean getIsActive() {
        return isActive;
    }

    /**
     * Checks if any field is provided for update.
     */
    public boolean hasUpdates() {
        return roleName != null || description != null || isActive != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String roleName;
        private String description;
        private Boolean isActive;

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public UpdateRoleRequest build() {
            return new UpdateRoleRequest(roleName, description, isActive);
        }
    }

    @Override
    public String toString() {
        return "UpdateRoleRequest{" +
                "hasRoleName=" + (roleName != null) +
                ", hasDescription=" + (description != null) +
                ", hasIsActive=" + (isActive != null) +
                '}';
    }
}
