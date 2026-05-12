package com.ghatana.platform.database.datastore;

import java.util.Set;

/**
 * Data store configuration for Data-Cloud platform.
 *
 * <p>Provides a builder pattern for configuring data stores with:
 * <ul>
 *   <li>Schema and retention policies</li>
 *   <li>Governance and encryption settings</li>
 *   <li>Audit and compliance rules</li>
 *   <li>Storage tier and indexing</li>
 *   <li>PII field designation</li>
 *   <li>WORM storage for immutability</li>
 * </ul></p>
 *
 * <p>This shared configuration eliminates duplication across product data services
 * and ensures consistent data store setup across the platform.</p>
 *
 * @doc.type class
 * @doc.purpose Shared data store configuration builder for Data-Cloud
 * @doc.layer platform
 * @doc.pattern Builder
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class DataStoreConfig {
    private final String schema;
    private final Retention retention;
    private final DataGovernance governance;
    private final EncryptionLevel encryption;
    private final AuditLevel auditLevel;
    private final boolean immutable;
    private final Set<ComplianceRule> complianceRules;
    private final StorageTier storageTier;
    private final Set<String> indexFields;
    private final Set<String> piiFields;
    private final boolean wormStorage;

    private DataStoreConfig(Builder builder) {
        this.schema = builder.schema;
        this.retention = builder.retention;
        this.governance = builder.governance;
        this.encryption = builder.encryption;
        this.auditLevel = builder.auditLevel;
        this.immutable = builder.immutable;
        this.complianceRules = builder.complianceRules;
        this.storageTier = builder.storageTier;
        this.indexFields = builder.indexFields;
        this.piiFields = builder.piiFields;
        this.wormStorage = builder.wormStorage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSchema() {
        return schema;
    }

    public Retention getRetention() {
        return retention;
    }

    public DataGovernance getGovernance() {
        return governance;
    }

    public EncryptionLevel getEncryption() {
        return encryption;
    }

    public AuditLevel getAuditLevel() {
        return auditLevel;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public Set<ComplianceRule> getComplianceRules() {
        return complianceRules;
    }

    public StorageTier getStorageTier() {
        return storageTier;
    }

    public Set<String> getIndexFields() {
        return indexFields;
    }

    public Set<String> getPiiFields() {
        return piiFields;
    }

    public boolean isWormStorage() {
        return wormStorage;
    }

    /**
     * Builder for DataStoreConfig.
     */
    public static class Builder {
        private String schema;
        private Retention retention;
        private DataGovernance governance;
        private EncryptionLevel encryption;
        private AuditLevel auditLevel;
        private boolean immutable;
        private Set<ComplianceRule> complianceRules = Set.of();
        private StorageTier storageTier = StorageTier.STANDARD;
        private Set<String> indexFields = Set.of();
        private Set<String> piiFields = Set.of();
        private boolean wormStorage;

        public Builder withSchema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder withRetention(Retention retention) {
            this.retention = retention;
            return this;
        }

        public Builder withGovernance(DataGovernance governance) {
            this.governance = governance;
            return this;
        }

        public Builder withEncryption(EncryptionLevel encryption) {
            this.encryption = encryption;
            return this;
        }

        public Builder withAuditLevel(AuditLevel auditLevel) {
            this.auditLevel = auditLevel;
            return this;
        }

        public Builder withImmutable(boolean immutable) {
            this.immutable = immutable;
            return this;
        }

        public Builder withComplianceRules(Set<ComplianceRule> complianceRules) {
            this.complianceRules = complianceRules;
            return this;
        }

        public Builder withStorageTier(StorageTier storageTier) {
            this.storageTier = storageTier;
            return this;
        }

        public Builder withIndexFields(Set<String> indexFields) {
            this.indexFields = indexFields;
            return this;
        }

        public Builder withPiiFields(Set<String> piiFields) {
            this.piiFields = piiFields;
            return this;
        }

        public Builder withWormStorage(boolean wormStorage) {
            this.wormStorage = wormStorage;
            return this;
        }

        public DataStoreConfig build() {
            return new DataStoreConfig(this);
        }
    }

    /**
     * Retention period for data.
     */
    public static class Retention {
        private final int years;

        private Retention(int years) {
            this.years = years;
        }

        public static Retention ofYears(int years) {
            return new Retention(years);
        }

        public int getYears() {
            return years;
        }
    }

    /**
     * Data governance level.
     */
    public enum DataGovernance {
        STANDARD,
        HEALTHCARE,
        FINANCIAL,
        MARKETING_CONSENT,
        PERSONAL_JOURNAL
    }

    /**
     * Encryption level.
     */
    public enum EncryptionLevel {
        STANDARD,
        STRONG
    }

    /**
     * Audit level.
     */
    public enum AuditLevel {
        BASIC,
        DETAILED,
        FULL
    }

    /**
     * Compliance rules.
     */
    public enum ComplianceRule {
        SEBON_REGULATIONS,
        MIFID_II,
        MIFID_II_AUDIT,
        DODD_FRANK,
        NEPAL_DIRECTIVE_2081,
        NEPAL_PRIVACY_ACT_2075,
        FHIR_R4,
        HIPAA,
        GDPR
    }

    /**
     * Storage tier.
     */
    public enum StorageTier {
        STANDARD,
        MULTI_TIER
    }
}
