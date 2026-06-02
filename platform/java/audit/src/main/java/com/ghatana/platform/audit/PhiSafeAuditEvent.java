package com.ghatana.platform.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PHI-Safe Audit Event Record
 *
 * Canonical audit event record for healthcare applications.
 * Ensures sensitive PHI/PII is not logged directly while maintaining
 * audit trail completeness through safe references and reason codes.
 *
 * @doc.type record
 * @doc.purpose PHI-safe audit event for healthcare applications
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
     * Create a copy of this audit event with additional metadata.
     *
     * @param additionalMetadata additional safe metadata
     * @return new audit event with merged metadata
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
