package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * HTTP response DTO for permission checking result.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP response data for permission check operations.
 * Provides detailed breakdown of which permissions are granted/denied.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PermissionCheckResponse response = PermissionCheckResponse.builder()
 *     .principalId("principal-123")
 *     .allowed(true)
 *     .addGrantedPermission("users:read")
 *     .addGrantedPermission("users:write")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP response DTO for permission checking
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class PermissionCheckResponse {
    private final String principalId;
    private final boolean allowed;
    private final Set<String> grantedPermissions;
    private final Set<String> deniedPermissions;
    private final String reason;
    private final long timestamp;

    private PermissionCheckResponse(
            String principalId,
            boolean allowed,
            Set<String> grantedPermissions,
            Set<String> deniedPermissions,
            String reason,
            long timestamp) {
        this.principalId = Objects.requireNonNull(principalId);
        this.allowed = allowed;
        this.grantedPermissions = new HashSet<>(grantedPermissions != null ? grantedPermissions : Set.of());
        this.deniedPermissions = new HashSet<>(deniedPermissions != null ? deniedPermissions : Set.of());
        this.reason = reason != null ? reason : "";
        this.timestamp = timestamp;
    }

    /**
     * Gets the principal ID checked.
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Gets whether the permission check passed.
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Gets set of granted permissions (defensive copy).
     */
    public Set<String> getGrantedPermissions() {
        return new HashSet<>(grantedPermissions);
    }

    /**
     * Gets set of denied permissions (defensive copy).
     */
    public Set<String> getDeniedPermissions() {
        return new HashSet<>(deniedPermissions);
    }

    /**
     * Gets explanation of check result.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets check timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets total permissions checked.
     */
    public int getTotalPermissions() {
        return grantedPermissions.size() + deniedPermissions.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String principalId;
        private boolean allowed = false;
        private Set<String> grantedPermissions = new HashSet<>();
        private Set<String> deniedPermissions = new HashSet<>();
        private String reason = "";
        private long timestamp = System.currentTimeMillis();

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder allowed(boolean allowed) {
            this.allowed = allowed;
            return this;
        }

        public Builder addGrantedPermission(String permission) {
            this.grantedPermissions.add(Objects.requireNonNull(permission));
            return this;
        }

        public Builder addDeniedPermission(String permission) {
            this.deniedPermissions.add(Objects.requireNonNull(permission));
            return this;
        }

        public Builder grantedPermissions(Set<String> permissions) {
            this.grantedPermissions = new HashSet<>(permissions);
            return this;
        }

        public Builder deniedPermissions(Set<String> permissions) {
            this.deniedPermissions = new HashSet<>(permissions);
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PermissionCheckResponse build() {
            return new PermissionCheckResponse(
                    principalId,
                    allowed,
                    grantedPermissions,
                    deniedPermissions,
                    reason,
                    timestamp);
        }
    }

    @Override
    public String toString() {
        return "PermissionCheckResponse{" +
                "principalId='" + principalId + '\'' +
                ", allowed=" + allowed +
                ", granted=" + grantedPermissions.size() +
                ", denied=" + deniedPermissions.size() +
                '}';
    }
}
