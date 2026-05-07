package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;

/**
 * HTTP response DTO for role assignment operation result.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP response data for role assignment operations.
 * Tracks success/failure counts and operation details.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleAssignmentResponse response = RoleAssignmentResponse.builder()
 *     .roleId("admin-role")
 *     .successCount(2)
 *     .failureCount(0)
 *     .message("Role assigned to 2 principals")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP response DTO for role assignment
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class RoleAssignmentResponse {
    private final String roleId;
    private final int successCount;
    private final int failureCount;
    private final String message;
    private final long timestamp;

    private RoleAssignmentResponse(
            String roleId,
            int successCount,
            int failureCount,
            String message,
            long timestamp) {
        this.roleId = Objects.requireNonNull(roleId);
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.message = Objects.requireNonNull(message);
        this.timestamp = timestamp;

        if (successCount < 0) {
            throw new IllegalArgumentException("successCount cannot be negative");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount cannot be negative");
        }
    }

    /**
     * Gets the role ID that was assigned.
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Gets number of successful assignments.
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * Gets number of failed assignments.
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * Gets total number of principals processed.
     */
    public int getTotalCount() {
        return successCount + failureCount;
    }

    /**
     * Gets operation result message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets operation timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Checks if all assignments were successful.
     */
    public boolean isFullySuccessful() {
        return failureCount == 0 && successCount > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String roleId;
        private int successCount = 0;
        private int failureCount = 0;
        private String message = "";
        private long timestamp = System.currentTimeMillis();

        public Builder roleId(String roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder successCount(int successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failureCount(int failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public RoleAssignmentResponse build() {
            return new RoleAssignmentResponse(roleId, successCount, failureCount, message, timestamp);
        }
    }

    @Override
    public String toString() {
        return "RoleAssignmentResponse{" +
                "roleId='" + roleId + '\'' +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                '}';
    }
}
