package com.ghatana.data.governance.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kernel Consent Event Model
 *
 * Provides reusable consent grant/check/revoke event model for healthcare products.
 * PHR and other healthcare products use this model to emit consent lifecycle events
 * for audit, compliance, and downstream processing.
 *
 * <p>Event types:</p>
 * <ul>
 *   <li>CONSENT_GRANTED - Consent was granted by data subject</li>
 *   <li>CONSENT_REVOKED - Consent was revoked by data subject</li>
 *   <li>CONSENT_CHECKED - Consent was checked (read-only operation)</li>
 *   <li>CONSENT_EXPIRED - Consent expired automatically</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Reusable consent event model for healthcare products
 * @doc.layer platform
 * @doc.pattern Event Model
 */
public final class ConsentEventModel {

    private ConsentEventModel() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a consent granted event.
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor who was granted consent
     * @param scope consent scope (e.g., "view-records", "share-data")
     * @param expiresAt consent expiration time
     * @param correlationId request correlation ID
     * @return consent event
     */
    public static ConsentEvent grant(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            Instant expiresAt,
            String correlationId) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_GRANTED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            expiresAt,
            Instant.now(),
            correlationId,
            Map.of()
        );
    }

    /**
     * Create a consent granted event with additional metadata.
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor who was granted consent
     * @param scope consent scope
     * @param expiresAt consent expiration time
     * @param correlationId request correlation ID
     * @param metadata additional event metadata
     * @return consent event
     */
    public static ConsentEvent grantWithMetadata(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            Instant expiresAt,
            String correlationId,
            Map<String, Object> metadata) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_GRANTED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            expiresAt,
            Instant.now(),
            correlationId,
            metadata
        );
    }

    /**
     * Create a consent revoked event.
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor whose consent was revoked
     * @param scope consent scope that was revoked
     * @param correlationId request correlation ID
     * @return consent event
     */
    public static ConsentEvent revoke(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            String correlationId) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_REVOKED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            null,
            Instant.now(),
            correlationId,
            Map.of()
        );
    }

    /**
     * Create a consent revoked event with reason.
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor whose consent was revoked
     * @param scope consent scope that was revoked
     * @param reason reason for revocation
     * @param correlationId request correlation ID
     * @return consent event
     */
    public static ConsentEvent revokeWithReason(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            String reason,
            String correlationId) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_REVOKED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            null,
            Instant.now(),
            correlationId,
            Map.of("reason", reason)
        );
    }

    /**
     * Create a consent checked event (read-only).
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor checking consent
     * @param scope consent scope being checked
     * @param granted whether consent was granted
     * @param correlationId request correlation ID
     * @return consent event
     */
    public static ConsentEvent check(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            boolean granted,
            String correlationId) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_CHECKED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            null,
            Instant.now(),
            correlationId,
            Map.of("granted", granted)
        );
    }

    /**
     * Create a consent expired event.
     *
     * @param tenantId tenant identifier
     * @param subjectId data subject identifier (patient ID)
     * @param accessorId accessor whose consent expired
     * @param scope consent scope that expired
     * @param expiresAt original expiration time
     * @param correlationId request correlation ID
     * @return consent event
     */
    public static ConsentEvent expire(
            String tenantId,
            String subjectId,
            String accessorId,
            String scope,
            Instant expiresAt,
            String correlationId) {
        return new ConsentEvent(
            UUID.randomUUID().toString(),
            EventType.CONSENT_EXPIRED,
            tenantId,
            subjectId,
            accessorId,
            scope,
            expiresAt,
            Instant.now(),
            correlationId,
            Map.of()
        );
    }

    /**
     * Consent event types.
     */
    public enum EventType {
        CONSENT_GRANTED,
        CONSENT_REVOKED,
        CONSENT_CHECKED,
        CONSENT_EXPIRED
    }

    /**
     * Consent event record.
     */
    public record ConsentEvent(
        String eventId,
        EventType eventType,
        String tenantId,
        String subjectId,
        String accessorId,
        String scope,
        Instant expiresAt,
        Instant timestamp,
        String correlationId,
        Map<String, Object> metadata
    ) {
        /**
         * Check if this consent event represents a granted consent.
         *
         * @return true if consent was granted
         */
        public boolean isGranted() {
            return eventType == EventType.CONSENT_GRANTED;
        }

        /**
         * Check if this consent event represents a revoked consent.
         *
         * @return true if consent was revoked
         */
        public boolean isRevoked() {
            return eventType == EventType.CONSENT_REVOKED;
        }

        /**
         * Check if this consent event represents an expired consent.
         *
         * @return true if consent expired
         */
        public boolean isExpired() {
            return eventType == EventType.CONSENT_EXPIRED;
        }

        /**
         * Check if this consent event is active (granted and not expired).
         *
         * @return true if consent is active
         */
        public boolean isActive() {
            return isGranted() && (expiresAt == null || expiresAt.isAfter(Instant.now()));
        }
    }
}
