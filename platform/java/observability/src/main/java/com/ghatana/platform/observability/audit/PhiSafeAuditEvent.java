package com.ghatana.platform.observability.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PHI-safe audit event representation used by observability audit helpers.
 *
 * @doc.type record
 * @doc.purpose PHI-safe audit event value object for observability helpers
 * @doc.layer platform
 * @doc.pattern Audit, ValueObject
 */
public record PhiSafeAuditEvent(
    String eventId,
    String eventType,
    String entityId,
    String userId,
    String tenantId,
    String action,
    Map<String, Object> data,
    long timestamp
) {
    /**
     * Create a copy of this event with additional safe metadata.
     *
     * @param additionalMetadata additional safe metadata to merge
     * @return a new event with merged metadata
     */
    public PhiSafeAuditEvent withMetadata(Map<String, Object> additionalMetadata) {
        Map<String, Object> mergedData = new LinkedHashMap<>(this.data);
        mergedData.putAll(additionalMetadata);
        return new PhiSafeAuditEvent(
            this.eventId,
            this.eventType,
            this.entityId,
            this.userId,
            this.tenantId,
            this.action,
            mergedData,
            this.timestamp
        );
    }
}
