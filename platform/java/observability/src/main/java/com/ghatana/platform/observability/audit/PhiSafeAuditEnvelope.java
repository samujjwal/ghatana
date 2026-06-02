package com.ghatana.platform.observability.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kernel PHI-Safe Audit Envelope
 *
 * Provides PHI-safe audit event structure for healthcare applications.
 * Ensures sensitive PHI/PII is not logged directly while maintaining
 * audit trail completeness through safe references and reason codes.
 *
 * <p>PHI-safe audit envelope structure:</p>
 * <pre>
 * {
 *   "eventId": "uuid",
 *   "eventType": "AUDIT_EVENT_TYPE",
 *   "entityId": "safe-entity-reference",
 *   "userId": "safe-user-reference",
 *   "tenantId": "tenant-id",
 *   "action": "ACTION_PERFORMED",
 *   "data": {
 *     "reasonCode": "SAFE_REASON_CODE",
 *     "correlationId": "correlation-id",
 *     // Additional safe metadata only
 *   },
 *   "timestamp": "ISO-8601 timestamp"
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose PHI-safe audit event envelope for healthcare applications
 * @doc.layer platform
 * @doc.pattern Audit, Envelope
 */
public final class PhiSafeAuditEnvelope {

    private PhiSafeAuditEnvelope() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a PHI-safe audit event.
     *
     * @param eventType the type of audit event
     * @param entityId safe entity reference (must not contain PHI)
     * @param userId safe user reference (must not contain PHI)
     * @param tenantId tenant identifier
     * @param action action performed
     * @param reasonCode safe reason code for the action
     * @param correlationId request correlation ID
     * @return audit event
     */
    public static PhiSafeAuditEvent create(
            String eventType,
            String entityId,
            String userId,
            String tenantId,
            String action,
            String reasonCode,
            String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonCode", reasonCode);
        data.put("correlationId", correlationId);

        return new PhiSafeAuditEvent(
            UUID.randomUUID().toString(),
            eventType,
            entityId,
            userId,
            tenantId,
            action,
            data,
            Instant.now().toEpochMilli()
        );
    }

    /**
     * Create a PHI-safe audit event with additional safe metadata.
     *
     * @param eventType the type of audit event
     * @param entityId safe entity reference (must not contain PHI)
     * @param userId safe user reference (must not contain PHI)
     * @param tenantId tenant identifier
     * @param action action performed
     * @param reasonCode safe reason code for the action
     * @param correlationId request correlation ID
     * @param additionalMetadata additional safe metadata (must not contain PHI)
     * @return audit event
     */
    public static PhiSafeAuditEvent createWithMetadata(
            String eventType,
            String entityId,
            String userId,
            String tenantId,
            String action,
            String reasonCode,
            String correlationId,
            Map<String, Object> additionalMetadata) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reasonCode", reasonCode);
        data.put("correlationId", correlationId);
        data.putAll(additionalMetadata);

        return new PhiSafeAuditEvent(
            UUID.randomUUID().toString(),
            eventType,
            entityId,
            userId,
            tenantId,
            action,
            data,
            Instant.now().toEpochMilli()
        );
    }

    /**
     * Validate that audit event data does not contain PHI.
     *
     * @param data the data to validate
     * @throws IllegalArgumentException if PHI is detected
     */
    public static void validatePhiSafe(Map<String, Object> data) {
        // PHI patterns that must not appear in audit logs
        String[] phiPatterns = {
            "ssn",
            "social security",
            "patient name",
            "diagnosis",
            "treatment",
            "medication",
            "condition",
            "allergy",
            "vital",
            "symptom"
        };

        String dataString = data.toString().toLowerCase();
        for (String pattern : phiPatterns) {
            if (dataString.contains(pattern)) {
                throw new IllegalArgumentException(
                    "PHI detected in audit data: " + pattern + ". Audit data must not contain PHI."
                );
            }
        }
    }

    /**
     * Safe reason codes for healthcare audit events.
     * These codes provide context without exposing PHI.
     */
    public static final class ReasonCodes {
        private ReasonCodes() {}

        public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
        public static final String AUTH_SESSION_EXPIRED = "AUTH_SESSION_EXPIRED";
        public static final String AUTH_SESSION_INVALID = "AUTH_SESSION_INVALID";
        public static final String POLICY_DENIED = "POLICY_DENIED";
        public static final String POLICY_PHI_ACCESS_DENIED = "POLICY_PHI_ACCESS_DENIED";
        public static final String POLICY_EMERGENCY_ACCESS_GRANTED = "POLICY_EMERGENCY_ACCESS_GRANTED";
        public static final String POLICY_EMERGENCY_ACCESS_DENIED = "POLICY_EMERGENCY_ACCESS_DENIED";
        public static final String CONSENT_GRANTED = "CONSENT_GRANTED";
        public static final String CONSENT_REVOKED = "CONSENT_REVOKED";
        public static final String CONSENT_EXPIRED = "CONSENT_EXPIRED";
        public static final String CONSENT_ACCESS_DENIED = "CONSENT_ACCESS_DENIED";
        public static final String ENTITY_NOT_FOUND = "ENTITY_NOT_FOUND";
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR";
    }
}
