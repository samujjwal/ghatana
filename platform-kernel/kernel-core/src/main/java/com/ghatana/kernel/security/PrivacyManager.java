package com.ghatana.kernel.security;

import java.util.Map;

/**
 * Privacy management for data protection and consent.
 *
 * <p>Manages consent, data classification, and data residency requirements
 * for privacy compliance with applicable regulatory frameworks.</p>
 *
 * @doc.type interface
 * @doc.purpose Privacy and consent management
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
     * @return consent status
     */
    ConsentStatus checkConsent(DataRequest request, String tenantId);

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
     * @return true if residency requirements are met
     */
    boolean enforceResidency(DataLocation location, String tenantId);

    /**
     * Records consent for data processing.
     *
     * @param tenantId the tenant identifier
     * @param userId the user identifier
     * @param purpose the processing purpose
     * @param granted whether consent is granted
     */
    void recordConsent(String tenantId, String userId, String purpose, boolean granted);

    /**
     * Gets privacy policy for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return the privacy policy
     */
    Policy getPrivacyPolicy(String tenantId);

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
}
