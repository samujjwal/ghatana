package com.ghatana.virtualorg.framework.hitl;

import java.time.Instant;
import java.util.Set;

/**
 * Query object for searching audit entries.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides flexible filtering options for querying the audit trail.
 *
 * @doc.type record
 * @doc.purpose Query parameters for audit search
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AuditQuery(
        String agentId,
        Set<String> eventTypes,
        Instant startTime,
        Instant endTime,
        String correlationId,
        String sessionId,
        int limit,
        int offset,
        SortOrder sortOrder
        ) {

    public enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if an entry matches this query.
     *
     * @param entry Entry to check
     * @return True if the entry matches all criteria
     */
    public boolean matches(AuditEntry entry) {
        if (agentId != null && !agentId.equals(entry.agentId())) {
            return false;
        }
        if (eventTypes != null && !eventTypes.isEmpty() && !eventTypes.contains(entry.eventType())) {
            return false;
        }
        if (startTime != null && entry.timestamp().isBefore(startTime)) {
            return false;
        }
        if (endTime != null && entry.timestamp().isAfter(endTime)) {
            return false;
        }
        if (correlationId != null && !correlationId.equals(entry.correlationId())) {
            return false;
        }
        if (sessionId != null && !sessionId.equals(entry.sessionId())) {
            return false;
        }
        return true;
    }

    public static final class Builder {

        private String agentId;
        private Set<String> eventTypes;
        private Instant startTime;
        private Instant endTime;
        private String correlationId;
        private String sessionId;
        private int limit = 100;
        private int offset = 0;
        private SortOrder sortOrder = SortOrder.DESCENDING;

        private Builder() {
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder eventTypes(Set<String> eventTypes) {
            this.eventTypes = eventTypes;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventTypes = Set.of(eventType);
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

        public Builder timeRange(Instant start, Instant end) {
            this.startTime = start;
            this.endTime = end;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder sortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(
                    agentId,
                    eventTypes,
                    startTime,
                    endTime,
                    correlationId,
                    sessionId,
                    limit,
                    offset,
                    sortOrder
            );
        }
    }
}
