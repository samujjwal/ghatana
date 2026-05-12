package com.ghatana.kernel.observability;

import java.util.List;
import java.util.Map;

/**
 * Audit trail service for immutable event logging.
 *
 * <p>Provides cryptographically secure audit trails with hash chains
 * and Merkle tree anchoring for tamper-evident logging.</p>
 *
 * @doc.type interface
 * @doc.purpose Immutable audit trail management
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface AuditTrailService {

    /**
     * Records an audit event.
     *
     * @param event the audit event to record
     */
    void recordAuditEvent(AuditTrailEvent event);

    /**
     * Queries audit events.
     *
     * @param query the audit query
     * @return list of matching audit events
     */
    List<AuditTrailEvent> queryAuditEvents(AuditQuery query);

    /**
     * Gets immutable audit trail for an entity.
     *
     * @param entityId the entity identifier
     * @return immutable audit trail
     */
    ImmutableAuditTrail getImmutableTrail(String entityId);

    /**
     * Verifies audit trail integrity.
     *
     * @param entityId the entity identifier
     * @return verification result
     */
    VerificationResult verifyTrailIntegrity(String entityId);

    /**
     * Represents an audit event in the audit trail.
     */
    class AuditTrailEvent {
        private final String eventId;
        private final String eventType;
        private final String entityId;
        private final String userId;
        private final String tenantId;
        private final String action;
        private final Map<String, Object> data;
        private final long timestamp;
        private final String previousHash;

        private AuditTrailEvent(Builder builder) {
            this.eventId = builder.eventId;
            this.eventType = builder.eventType;
            this.entityId = builder.entityId;
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.action = builder.action;
            this.data = builder.data;
            this.timestamp = builder.timestamp;
            this.previousHash = builder.previousHash;
        }

        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public String getEntityId() { return entityId; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getAction() { return action; }
        public Map<String, Object> getData() { return data; }
        public long getTimestamp() { return timestamp; }
        public String getPreviousHash() { return previousHash; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String eventId;
            private String eventType;
            private String entityId;
            private String userId;
            private String tenantId;
            private String action;
            private Map<String, Object> data;
            private long timestamp = System.currentTimeMillis();
            private String previousHash;

            public Builder eventId(String eventId) {
                this.eventId = eventId;
                return this;
            }

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder entityId(String entityId) {
                this.entityId = entityId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder action(String action) {
                this.action = action;
                return this;
            }

            public Builder data(Map<String, Object> data) {
                this.data = data;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder previousHash(String previousHash) {
                this.previousHash = previousHash;
                return this;
            }

            public AuditTrailEvent build() {
                return new AuditTrailEvent(this);
            }
        }
    }

    /**
     * Query for audit events.
     */
    class AuditQuery {
        private final String entityId;
        private final String userId;
        private final String tenantId;
        private final String eventType;
        private final long startTime;
        private final long endTime;
        private final int limit;

        private AuditQuery(Builder builder) {
            this.entityId = builder.entityId;
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.eventType = builder.eventType;
            this.startTime = builder.startTime;
            this.endTime = builder.endTime;
            this.limit = builder.limit;
        }

        public String getEntityId() { return entityId; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getEventType() { return eventType; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public int getLimit() { return limit; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String entityId;
            private String userId;
            private String tenantId;
            private String eventType;
            private long startTime = 0;
            private long endTime = Long.MAX_VALUE;
            private int limit = 100;

            public Builder entityId(String entityId) {
                this.entityId = entityId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder startTime(long startTime) {
                this.startTime = startTime;
                return this;
            }

            public Builder endTime(long endTime) {
                this.endTime = endTime;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public AuditQuery build() {
                return new AuditQuery(this);
            }
        }
    }

    /**
     * Immutable audit trail with hash chain.
     */
    interface ImmutableAuditTrail {
        String getEntityId();
        List<AuditTrailEvent> getEvents();
        String getMerkleRoot();
        boolean isIntact();
    }

    /**
     * Verification result for audit trail.
     */
    class VerificationResult {
        private final boolean valid;
        private final String message;
        private final List<String> violations;

        public VerificationResult(boolean valid, String message, List<String> violations) {
            this.valid = valid;
            this.message = message;
            this.violations = violations;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<String> getViolations() { return violations; }
    }
}
