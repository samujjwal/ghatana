package com.ghatana.platform.observability.audit;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Kernel Audit Facade
 *
 * Provides unified audit event emission for all platform and product code.
 * Healthcare decision reason codes must be emitted through this facade to ensure
 * PHI-safe logging and consistent audit trail structure.
 *
 * <p>Products should:</p>
 * <ul>
 *   <li>Use safe reason codes from {@link PhiSafeAuditEnvelope.ReasonCodes}</li>
 *   <li>Never log PHI directly in audit data</li>
 *   <li>Include correlation ID for traceability</li>
 *   <li>Use entity/user references that are safe (not PHI)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Unified audit event emission facade
 * @doc.layer platform
 * @doc.pattern Facade
 */
public interface KernelAuditFacade {

    /**
     * Record an audit event synchronously.
     *
     * @param event the audit event to record
     * @throws AuditException if recording fails
     */
    void recordAuditEvent(PhiSafeAuditEvent event) throws AuditException;

    /**
     * Record an audit event asynchronously.
     *
     * @param event the audit event to record
     * @return Promise that completes when event is recorded
     */
    Promise<Void> recordAuditEventAsync(PhiSafeAuditEvent event);

    /**
     * Record a healthcare decision audit event with safe reason code.
     *
     * <p>This is the preferred method for healthcare policy decisions.</p>
     *
     * @param eventType the type of audit event
     * @param entityId safe entity reference
     * @param userId safe user reference
     * @param tenantId tenant identifier
     * @param action action performed
     * @param reasonCode safe reason code from {@link PhiSafeAuditEnvelope.ReasonCodes}
     * @param correlationId request correlation ID
     * @throws AuditException if recording fails
     */
    void recordHealthcareDecision(
            String eventType,
            String entityId,
            String userId,
            String tenantId,
            String action,
            String reasonCode,
            String correlationId) throws AuditException;

    /**
     * Record a healthcare decision audit event with additional safe metadata.
     *
     * @param eventType the type of audit event
     * @param entityId safe entity reference
     * @param userId safe user reference
     * @param tenantId tenant identifier
     * @param action action performed
     * @param reasonCode safe reason code from {@link PhiSafeAuditEnvelope.ReasonCodes}
     * @param correlationId request correlation ID
     * @param additionalMetadata additional safe metadata (must not contain PHI)
     * @throws AuditException if recording fails
     */
    void recordHealthcareDecisionWithMetadata(
            String eventType,
            String entityId,
            String userId,
            String tenantId,
            String action,
            String reasonCode,
            String correlationId,
            Map<String, Object> additionalMetadata) throws AuditException;

    /**
     * Check if audit service is available.
     *
     * @return true if audit service is available
     */
    boolean isAvailable();

    /**
     * Exception thrown when audit recording fails.
     */
    class AuditException extends Exception {
        public AuditException(String message) {
            super(message);
        }

        public AuditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
