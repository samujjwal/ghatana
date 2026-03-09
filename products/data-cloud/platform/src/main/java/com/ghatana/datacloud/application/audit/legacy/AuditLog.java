package com.ghatana.datacloud.application.audit.legacy;

import com.ghatana.datacloud.entity.audit.AuditAction;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Legacy domain model for audit trail events used by AuditService.
 *
 * <p>
 * Note: Newer application code should prefer the canonical
 * com.ghatana.datacloud.entity.audit.AuditLog type.
 */
public final class AuditLog {

    private final UUID id;
    private final String tenantId;
    private final AuditAction action;
    private final String resourceType;
    private final String resourceId;
    private final String userId;
    private final Instant timestamp;
    private final AuditStatus status;
    private final Map<String, Object> details;
/**
 * Audit status.
 *
 * @doc.type enum
 * @doc.purpose Audit status
 * @doc.layer platform
 * @doc.pattern Enumeration
 */

    public enum AuditStatus {
        SUCCESS,
        FAILURE,
        PARTIAL,
        PENDING
    }

    private AuditLog(
            UUID id,
            String tenantId,
            AuditAction action,
            String resourceType,
            String resourceId,
            String userId,
            Instant timestamp,
            AuditStatus status,
            Map<String, Object> details) {
        this.id = id;
        this.tenantId = tenantId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.userId = userId;
        this.timestamp = timestamp;
        this.status = status;
        this.details = details != null ? Map.copyOf(details) : Map.of();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public boolean isSuccess() {
        return status == AuditStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == AuditStatus.FAILURE;
    }

    public Object getDetail(String key) {
        return details.get(key);
    }

    public String getDetailAsString(String key) {
        Object value = getDetail(key);
        return value != null ? value.toString() : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String tenantId;
        private AuditAction action;
        private String resourceType;
        private String resourceId;
        private String userId;
        private Instant timestamp;
        private AuditStatus status;
        private Map<String, Object> details;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
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

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(AuditStatus status) {
            this.status = status;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public AuditLog build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (status == null) {
                status = AuditStatus.SUCCESS;
            }

            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(action, "action cannot be null");
            Objects.requireNonNull(resourceType, "resourceType cannot be null");
            Objects.requireNonNull(resourceId, "resourceId cannot be null");
            Objects.requireNonNull(userId, "userId cannot be null");

            return new AuditLog(id, tenantId, action, resourceType, resourceId, userId, timestamp, status, details);
        }
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", action=" + action +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AuditLog))
            return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
