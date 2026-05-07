package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;

/**
 * Privacy management for data protection and consent.
 *
 * <p>Manages consent, data classification, data residency requirements,
 * PII encryption/redaction, and DSAR compliance for privacy compliance
 * with applicable regulatory frameworks (privacy regulations, etc.).</p>
 *
 * @doc.type interface
 * @doc.purpose Privacy and consent management with PII/DSAR support (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface PrivacyManager {

    /**
     * Checks consent status for a data request.
     *
     * @param request the data request
     * @param tenantId the tenant identifier
     * @return Promise containing consent status
     */
    Promise<ConsentStatus> checkConsent(DataRequest request, String tenantId);

    /**
     * Classifies data according to privacy rules.
     *
     * @param data the data to classify
     * @return data classification
     */
    DataClassification classifyData(Object data);

    /**
     * Enforces data residency requirements.
     *
     * @param location the data location
     * @param tenantId the tenant identifier
     * @return Promise containing true if residency requirements are met
     */
    Promise<Boolean> enforceResidency(DataLocation location, String tenantId);

    /**
     * Records consent for data processing.
     *
     * @param tenantId the tenant identifier
     * @param userId the user identifier
     * @param purpose the processing purpose
     * @param granted whether consent is granted
     * @return Promise that completes when consent is recorded
     */
    Promise<Void> recordConsent(String tenantId, String userId, String purpose, boolean granted);

    /**
     * Gets privacy policy for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing the privacy policy
     */
    Promise<Optional<PrivacyPolicy>> getPrivacyPolicy(String tenantId);

    /**
     * Encrypts PII data using tenant-specific encryption.
     *
     * @param tenantId the tenant identifier
     * @param pii the PII data to encrypt
     * @return Promise containing encrypted PII
     */
    Promise<String> encryptPII(String tenantId, String pii);

    /**
     * Decrypts PII data using tenant-specific encryption.
     *
     * @param tenantId the tenant identifier
     * @param encryptedPii the encrypted PII data
     * @return Promise containing decrypted PII
     */
    Promise<String> decryptPII(String tenantId, String encryptedPii);

    /**
     * Hashes PII identifier for pseudonymization.
     *
     * @param tenantId the tenant identifier
     * @param identifier the PII identifier to hash
     * @return Promise containing hashed identifier
     */
    Promise<String> hashPIIIdentifier(String tenantId, String identifier);

    /**
     * Redacts PII from log output or audit trails.
     *
     * @param data the data containing potential PII
     * @param classification the data classification
     * @return redacted data with PII masked
     */
    String redactPII(String data, DataClassification classification);

    /**
     * Processes a Data Subject Access Request (DSAR).
     *
     * @param request the DSAR request
     * @return Promise containing DSAR result
     */
    Promise<DSarResult> processDSAR(DSARRequest request);

    /**
     * Deletes data for a data subject (Right to be Forgotten).
     *
     * @param tenantId the tenant identifier
     * @param subjectId the data subject identifier
     * @return Promise that completes when deletion is finished
     */
    Promise<Void> deleteSubjectData(String tenantId, String subjectId);

    /**
     * Exports data for a data subject (Right to Data Portability).
     *
     * @param tenantId the tenant identifier
     * @param subjectId the data subject identifier
     * @return Promise containing exported data
     */
    Promise<Map<String, Object>> exportSubjectData(String tenantId, String subjectId);

    /**
     * Represents a data access request.
     */
    class DataRequest {
        private final String requesterId;
        private final String dataType;
        private final String purpose;
        private final Map<String, Object> metadata;

        public DataRequest(String requesterId, String dataType, String purpose, Map<String, Object> metadata) {
            this.requesterId = requesterId;
            this.dataType = dataType;
            this.purpose = purpose;
            this.metadata = metadata;
        }

        public String getRequesterId() { return requesterId; }
        public String getDataType() { return dataType; }
        public String getPurpose() { return purpose; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Consent status enumeration.
     */
    enum ConsentStatus {
        GRANTED,
        DENIED,
        PENDING,
        EXPIRED,
        NOT_REQUIRED
    }

    /**
     * Data classification levels.
     */
    enum DataClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED,
        PHI,  // Protected Health Information
        PII   // Personally Identifiable Information
    }

    /**
     * Data location for residency enforcement.
     */
    class DataLocation {
        private final String region;
        private final String country;
        private final String dataCenter;

        public DataLocation(String region, String country, String dataCenter) {
            this.region = region;
            this.country = country;
            this.dataCenter = dataCenter;
        }

        public String getRegion() { return region; }
        public String getCountry() { return country; }
        public String getDataCenter() { return dataCenter; }
    }

    /**
     * Privacy policy for a tenant.
     */
    record PrivacyPolicy(
        String tenantId,
        String version,
        Map<String, Boolean> consentPurposes,
        DataRetention retention,
        String effectiveDate
    ) {
        public PrivacyPolicy {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }
            consentPurposes = Map.copyOf(consentPurposes);
        }
    }

    /**
     * Data retention configuration.
     */
    record DataRetention(
        int piiRetentionDays,
        int auditRetentionDays,
        int logRetentionDays
    ) {}

    /**
     * DSAR request type.
     */
    enum DSARType {
        ACCESS,
        DELETION,
        PORTABILITY,
        RECTIFICATION
    }

    /**
     * DSAR request.
     */
    record DSARRequest(
        String requestId,
        String tenantId,
        String subjectId,
        DSARType type,
        String requesterId,
        java.time.Instant requestedAt
    ) {
        public DSARRequest {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId must not be blank");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (subjectId == null || subjectId.isBlank()) {
                throw new IllegalArgumentException("subjectId must not be blank");
            }
        }
    }

    /**
     * DSAR result.
     */
    record DSarResult(
        String requestId,
        DSARStatus status,
        Map<String, Object> data,
        String message,
        java.time.Instant completedAt
    ) {
        public DSarResult {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId must not be blank");
            }
            data = Map.copyOf(data);
        }
    }

    /**
     * DSAR processing status.
     */
    enum DSARStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        PARTIALLY_COMPLETED
    }
}
