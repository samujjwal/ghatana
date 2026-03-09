package com.ghatana.datacloud.entity.audit;

import java.time.Instant;
import java.util.*;

/**
 * Audit log entry representing an action taken in the system.
 *
 * <p><b>Purpose</b><br>
 * Immutable record of all significant operations (create, update, delete) for compliance,
 * debugging, and forensic analysis. Tracks who did what, when, and to which resource.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuditLog log = AuditLog.builder()
 *     .tenantId("tenant-123")
 *     .userId("user-456")
 *     .action(AuditAction.CREATE)
 *     .resourceType("entity")
 *     .resourceId("entity-789")
 *     .changes(Map.of("name", Map.entry("", "Product A")))
 *     .timestamp(Instant.now())
 *     .build();
 * }</pre>
 *
 * <p><b>Multi-Tenancy</b><br>
 * All audit logs scoped to tenant. Cross-tenant queries not permitted.
 * TenantId used as primary partition key for storage efficiency.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe for concurrent reads.
 *
 * @doc.type class
 * @doc.purpose Audit log entry for compliance and forensics
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class AuditLog {

    private final String id;
    private final String tenantId;
    private final String userId;
    private final AuditAction action;
    private final String resourceType;
    private final String resourceId;
    private final String collectionId;
    private final Map<String, Map.Entry<String, String>> changes;
    private final String details;
    private final Instant timestamp;
    private final String ipAddress;
    private final String userAgent;

    private AuditLog(
            String id,
            String tenantId,
            String userId,
            AuditAction action,
            String resourceType,
            String resourceId,
            String collectionId,
            Map<String, Map.Entry<String, String>> changes,
            String details,
            Instant timestamp,
            String ipAddress,
            String userAgent) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId cannot be null");
        this.collectionId = collectionId;
        this.changes = Collections.unmodifiableMap(
            Objects.requireNonNull(changes, "changes cannot be null")
        );
        this.details = details;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    /**
     * Gets audit log ID.
     *
     * @return unique audit log ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets tenant ID.
     *
     * @return tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets user ID who performed action.
     *
     * @return user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets action performed.
     *
     * @return audit action
     */
    public AuditAction getAction() {
        return action;
    }

    /**
     * Gets resource type (entity, collection, schema, etc.).
     *
     * @return resource type
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Gets resource ID that was modified.
     *
     * @return resource ID
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Gets collection ID if applicable.
     *
     * @return collection ID or null if not applicable
     */
    public String getCollectionId() {
        return collectionId;
    }

    /**
     * Gets field changes (old value → new value map).
     *
     * @return immutable map of field changes
     */
    public Map<String, Map.Entry<String, String>> getChanges() {
        return changes;
    }

    /**
     * Gets additional details about the action.
     *
     * @return details or null if not provided
     */
    public String getDetails() {
        return details;
    }

    /**
     * Gets timestamp when action occurred.
     *
     * @return action timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Gets IP address of request (if available).
     *
     * @return IP address or null
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Gets user agent of request (if available).
     *
     * @return user agent or null
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Creates builder for AuditLog.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AuditLog.
     */
    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String tenantId;
        private String userId;
        private AuditAction action;
        private String resourceType;
        private String resourceId;
        private String collectionId;
        private Map<String, Map.Entry<String, String>> changes = Map.of();
        private String details;
        private Instant timestamp = Instant.now();
        private String ipAddress;
        private String userAgent;

        /**
         * Sets audit log ID.
         *
         * @param id audit log ID
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets tenant ID.
         *
         * @param tenantId tenant ID (required)
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets user ID.
         *
         * @param userId user ID (required)
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets action.
         *
         * @param action action performed (required)
         * @return this builder
         */
        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        /**
         * Sets resource type.
         *
         * @param resourceType resource type (required)
         * @return this builder
         */
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * Sets resource ID.
         *
         * @param resourceId resource ID (required)
         * @return this builder
         */
        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        /**
         * Sets collection ID.
         *
         * @param collectionId collection ID (optional)
         * @return this builder
         */
        public Builder collectionId(String collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        /**
         * Sets field changes.
         *
         * @param changes map of field changes (required)
         * @return this builder
         */
        public Builder changes(Map<String, Map.Entry<String, String>> changes) {
            this.changes = changes != null ? changes : Map.of();
            return this;
        }

        /**
         * Sets additional details.
         *
         * @param details action details (optional)
         * @return this builder
         */
        public Builder details(String details) {
            this.details = details;
            return this;
        }

        /**
         * Sets timestamp.
         *
         * @param timestamp action timestamp
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets IP address.
         *
         * @param ipAddress request IP address (optional)
         * @return this builder
         */
        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        /**
         * Sets user agent.
         *
         * @param userAgent request user agent (optional)
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Builds AuditLog.
         *
         * @return new AuditLog instance
         * @throws NullPointerException if required fields not set
         */
        public AuditLog build() {
            return new AuditLog(
                id, tenantId, userId, action, resourceType, resourceId,
                collectionId, changes, details, timestamp, ipAddress, userAgent
            );
        }
    }

    @Override
    public String toString() {
        return "AuditLog{" +
            "id='" + id + '\'' +
            ", tenantId='" + tenantId + '\'' +
            ", userId='" + userId + '\'' +
            ", action=" + action +
            ", resourceType='" + resourceType + '\'' +
            ", resourceId='" + resourceId + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog)) return false;
        AuditLog auditLog = (AuditLog) o;
        return Objects.equals(id, auditLog.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
