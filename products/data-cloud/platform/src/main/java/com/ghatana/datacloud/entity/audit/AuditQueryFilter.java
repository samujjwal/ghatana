package com.ghatana.datacloud.entity.audit;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Filter criteria for querying audit logs.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates query parameters for flexible audit log searches.
 * Supports filtering by tenant, user, action, resource, and time range.
 *
 * @doc.type record
 * @doc.purpose Query filter for audit log searches
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public record AuditQueryFilter(
    String tenantId,
    Optional<String> userId,
    Optional<Set<AuditAction>> actions,
    Optional<String> resourceType,
    Optional<String> resourceId,
    Optional<Instant> startTime,
    Optional<Instant> endTime,
    int offset,
    int limit
) {
    /**
     * Creates a new builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AuditQueryFilter.
     */
    public static class Builder {
        private String tenantId;
        private String userId;
        private Set<AuditAction> actions;
        private String resourceType;
        private String resourceId;
        private Instant startTime;
        private Instant endTime;
        private int offset = 0;
        private int limit = 100;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder actions(Set<AuditAction> actions) {
            this.actions = actions;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public AuditQueryFilter build() {
            return new AuditQueryFilter(
                tenantId,
                Optional.ofNullable(userId),
                Optional.ofNullable(actions),
                Optional.ofNullable(resourceType),
                Optional.ofNullable(resourceId),
                Optional.ofNullable(startTime),
                Optional.ofNullable(endTime),
                offset,
                limit
            );
        }
    }
}
