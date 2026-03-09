package com.ghatana.datacloud.entity.storage;

import java.time.Instant;
import java.util.*;

/**
 * Immutable domain model representing storage policies for collections and data sensitivity.
 *
 * <p><b>Purpose</b><br>
 * Defines allowable storage backends per tenant/collection and applies sensitivity-based
 * routing rules. Policies ensure data compliance, performance requirements, and cost
 * constraints are met when routing operations to storage connectors.
 *
 * <p><b>Architecture Role</b><br>
 * Domain model in storage layer, used by:
 * - StorageRoutingService - Consults policies when computing RoutingPlan
 * - Collection configuration - Applied per collection or tenant-wide
 * - StorageConnectorRegistry - Filters available connectors based on policy
 * - Audit/governance - Tracks policy decisions for compliance
 *
 * <p><b>Policy Components</b><br>
 * - Allowed backends: Set of StorageBackendType values permitted for this collection
 * - Primary backend: Preferred backend for default routing
 * - Data sensitivity: Enum indicating data classification (public, internal, confidential, restricted)
 * - Compliance rules: Map of compliance standards (GDPR, HIPAA, SOC2)
 * - TTL policy: Time-to-live for data in each backend tier
 * - Replication: Minimum replication count for data durability
 * - Encryption: Encryption requirements (at-rest, in-transit)
 *
 * <p><b>Data Sensitivity Levels</b><br>
 * - PUBLIC: No restrictions, can be stored in any backend
 * - INTERNAL: Company-internal, no external storage allowed
 * - CONFIDENTIAL: Sensitive business data, restricted backends only (on-prem DB)
 * - RESTRICTED: Highest sensitivity (PII, PHI), encryption required, audit logging mandatory
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StoragePolicy policy = StoragePolicy.builder()
 *     .name("customer-data-policy")
 *     .label("Customer Data Policy")
 *     .description("Policy for storing customer PII and contact info")
 *     .tenantId("tenant-123")
 *     .collectionId(collectionId)
 *     .dataSensitivity(DataSensitivity.RESTRICTED)
 *     .allowedBackends(
 *         StorageBackendType.RELATIONAL,  // PostgreSQL only for restricted
 *         StorageBackendType.KEY_VALUE    // Redis cache allowed
 *     )
 *     .primaryBackend(StorageBackendType.RELATIONAL)
 *     .addComplianceRule("gdpr", true)
 *     .addComplianceRule("requires_encryption", true)
 *     .requiresReplication(3)  // 3-way replication
 *     .requiresAuditLogging(true)
 *     .ttlDays(365)  // Keep data for 1 year
 *     .build();
 *
 * // Routing service uses policy
 * if (!policy.supports(StorageBackendType.BLOB_STORAGE)) {
 *     // Cannot route to blob storage, use relational instead
 * }
 * }</pre>
 *
 * <p><b>Immutability</b><br>
 * All fields are final and defensive copies made for collections.
 * Safe to share between threads and cache indefinitely.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. No synchronization needed.
 *
 * @see StorageBackendType
 * @see StorageProfile
 * @see MetaCollection
 * @doc.type class
 * @doc.purpose Storage policy for backend selection and compliance
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class StoragePolicy {

    /**
     * Data sensitivity classification.
     */
    public enum DataSensitivity {
        /** Public data, no restrictions */
        PUBLIC(0, "public"),
        /** Internal company data, minimal restrictions */
        INTERNAL(1, "internal"),
        /** Confidential business data */
        CONFIDENTIAL(2, "confidential"),
        /** Highest sensitivity (PII, PHI), strong restrictions */
        RESTRICTED(3, "restricted");

        private final int level;
        private final String value;

        DataSensitivity(int level, String value) {
            this.level = level;
            this.value = value;
        }

        public int getLevel() {
            return level;
        }

        public String getValue() {
            return value;
        }

        public boolean isHigherThan(DataSensitivity other) {
            return this.level > other.level;
        }

        public static DataSensitivity fromString(String value) {
            for (DataSensitivity sens : DataSensitivity.values()) {
                if (sens.value.equalsIgnoreCase(value)) {
                    return sens;
                }
            }
            throw new IllegalArgumentException("Unknown DataSensitivity: " + value);
        }
    }

    private final String name;
    private final String label;
    private final String description;
    private final String tenantId;
    private final UUID collectionId;
    private final DataSensitivity dataSensitivity;
    private final Set<StorageBackendType> allowedBackends;
    private final StorageBackendType primaryBackend;
    private final Map<String, Boolean> complianceRules;
    private final int minReplicationCount;
    private final boolean requiresAuditLogging;
    private final boolean requiresEncryption;
    private final Integer ttlDays;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Private constructor - use Builder.
     */
    private StoragePolicy(
            String name,
            String label,
            String description,
            String tenantId,
            UUID collectionId,
            DataSensitivity dataSensitivity,
            Set<StorageBackendType> allowedBackends,
            StorageBackendType primaryBackend,
            Map<String, Boolean> complianceRules,
            int minReplicationCount,
            boolean requiresAuditLogging,
            boolean requiresEncryption,
            Integer ttlDays,
            Instant createdAt,
            Instant updatedAt) {
        this.name = name;
        this.label = label;
        this.description = description;
        this.tenantId = tenantId;
        this.collectionId = collectionId;
        this.dataSensitivity = dataSensitivity;
        this.allowedBackends = new HashSet<>(allowedBackends);
        this.primaryBackend = primaryBackend;
        this.complianceRules = new HashMap<>(complianceRules);
        this.minReplicationCount = minReplicationCount;
        this.requiresAuditLogging = requiresAuditLogging;
        this.requiresEncryption = requiresEncryption;
        this.ttlDays = ttlDays;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a builder for StoragePolicy.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for StoragePolicy.
     */
    public static class Builder {
        private String name;
        private String label;
        private String description;
        private String tenantId;
        private UUID collectionId;
        private DataSensitivity dataSensitivity = DataSensitivity.INTERNAL;
        private Set<StorageBackendType> allowedBackends = new HashSet<>();
        private StorageBackendType primaryBackend;
        private Map<String, Boolean> complianceRules = new HashMap<>();
        private int minReplicationCount = 1;
        private boolean requiresAuditLogging = false;
        private boolean requiresEncryption = false;
        private Integer ttlDays;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder collectionId(UUID collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder dataSensitivity(DataSensitivity sensitivity) {
            this.dataSensitivity = sensitivity;
            return this;
        }

        public Builder allowedBackends(StorageBackendType... backends) {
            this.allowedBackends = new HashSet<>(Arrays.asList(backends));
            return this;
        }

        public Builder addAllowedBackend(StorageBackendType backend) {
            this.allowedBackends.add(backend);
            return this;
        }

        public Builder primaryBackend(StorageBackendType backend) {
            this.primaryBackend = backend;
            return this;
        }

        public Builder complianceRules(Map<String, Boolean> rules) {
            this.complianceRules = new HashMap<>(rules);
            return this;
        }

        public Builder addComplianceRule(String ruleName, boolean required) {
            this.complianceRules.put(ruleName, required);
            return this;
        }

        public Builder minReplicationCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("minReplicationCount must be >= 1, got: " + count);
            }
            this.minReplicationCount = count;
            return this;
        }

        public Builder requiresAuditLogging(boolean required) {
            this.requiresAuditLogging = required;
            return this;
        }

        public Builder requiresEncryption(boolean required) {
            this.requiresEncryption = required;
            return this;
        }

        public Builder ttlDays(Integer days) {
            if (days != null && days < 0) {
                throw new IllegalArgumentException("ttlDays cannot be negative, got: " + days);
            }
            this.ttlDays = days;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds StoragePolicy with validation.
         *
         * @return new StoragePolicy instance
         * @throws IllegalArgumentException if required fields missing or invalid
         */
        public StoragePolicy build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (allowedBackends.isEmpty()) {
                throw new IllegalArgumentException("allowedBackends must not be empty");
            }
            if (primaryBackend == null) {
                throw new IllegalArgumentException("primaryBackend is required");
            }
            if (!allowedBackends.contains(primaryBackend)) {
                throw new IllegalArgumentException(
                        "primaryBackend must be in allowedBackends: " + primaryBackend);
            }

            // Restricted data requires encryption and audit logging
            if (dataSensitivity == DataSensitivity.RESTRICTED) {
                if (!requiresEncryption) {
                    throw new IllegalArgumentException(
                            "RESTRICTED data must require encryption");
                }
                if (!requiresAuditLogging) {
                    throw new IllegalArgumentException(
                            "RESTRICTED data must require audit logging");
                }
            }

            Instant now = Instant.now();
            return new StoragePolicy(
                    name,
                    label,
                    description,
                    tenantId,
                    collectionId,
                    dataSensitivity,
                    allowedBackends,
                    primaryBackend,
                    complianceRules,
                    minReplicationCount,
                    requiresAuditLogging,
                    requiresEncryption,
                    ttlDays,
                    createdAt != null ? createdAt : now,
                    updatedAt != null ? updatedAt : now);
        }
    }

    // ===== Getters =====

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public DataSensitivity getDataSensitivity() {
        return dataSensitivity;
    }

    public Set<StorageBackendType> getAllowedBackends() {
        return Collections.unmodifiableSet(allowedBackends);
    }

    public StorageBackendType getPrimaryBackend() {
        return primaryBackend;
    }

    public Map<String, Boolean> getComplianceRules() {
        return Collections.unmodifiableMap(complianceRules);
    }

    public int getMinReplicationCount() {
        return minReplicationCount;
    }

    public boolean isAuditLoggingRequired() {
        return requiresAuditLogging;
    }

    public boolean isEncryptionRequired() {
        return requiresEncryption;
    }

    public Integer getTtlDays() {
        return ttlDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ===== Behavioral Methods =====

    /**
     * Check if backend is allowed by this policy.
     *
     * @param backend storage backend type
     * @return true if backend is allowed
     */
    public boolean supports(StorageBackendType backend) {
        return allowedBackends.contains(backend);
    }

    /**
     * Check if compliance rule is enabled.
     *
     * @param ruleName compliance rule name
     * @return true if rule is enabled, false otherwise
     */
    public boolean isComplianceRuleEnabled(String ruleName) {
        return complianceRules.getOrDefault(ruleName, false);
    }

    /**
     * Get backends that satisfy this policy, preferring primary backend.
     *
     * @return list of allowed backends with primary first
     */
    public List<StorageBackendType> getPreferredBackends() {
        List<StorageBackendType> result = new ArrayList<>();
        result.add(primaryBackend);
        for (StorageBackendType backend : allowedBackends) {
            if (backend != primaryBackend) {
                result.add(backend);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoragePolicy)) return false;
        StoragePolicy that = (StoragePolicy) o;
        return Objects.equals(name, that.name) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tenantId);
    }

    @Override
    public String toString() {
        return "StoragePolicy{" +
                "name='" + name + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", dataSensitivity=" + dataSensitivity +
                ", primaryBackend=" + primaryBackend +
                '}';
    }
}
