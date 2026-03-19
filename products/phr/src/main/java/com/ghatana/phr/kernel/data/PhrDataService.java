package com.ghatana.phr.kernel.data;

import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.stream.Stream;

/**
 * Initializes PHR data stores in Data-Cloud with healthcare governance.
 *
 * <p>All Data-Cloud calls return CompletableFuture; we wrap them with
 * {@code Promise.ofFuture(cf)} at the adapter boundary.</p>
 *
 * <p>Healthcare retention requirements:
 * <ul>
 *   <li>Patient records: 25 years (healthcare requirement)</li>
 *   <li>Consent records: 10 years</li>
 *   <li>Document records: 25 years</li>
 *   <li>Audit logs: 7 years (Nepal Directive 2081)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR data store initialization with healthcare retention/governance policies
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public class PhrDataService {

    private final DataCloudPlatform dataCloud;

    public PhrDataService(DataCloudPlatform dataCloud) {
        this.dataCloud = dataCloud;
    }

    /**
     * Initializes all PHR data stores.
     *
     * @return Promise completing when all stores are initialized
     */
    public Promise<Void> initializeStores() {
        return Promises.all(
            Stream.of(
                initializePatientRecordsStore(),
                initializeConsentRecordsStore(),
                initializeDocumentStore(),
                initializeAppointmentStore(),
                initializeAuditStore()
            )
        ).toVoid();
    }

    /**
     * Initializes patient records store with healthcare governance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializePatientRecordsStore() {
        return Promise.ofFuture(
            dataCloud.createStore("patient.records", DataStoreConfig.builder()
                .withSchema("patient-schema-v1")
                .withRetention(Retention.ofYears(25)) // Healthcare requirement
                .withGovernance(DataGovernance.HEALTHCARE)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withImmutableFields(Set.of("patient_id", "medical_record_number"))
                .withPiiFields(Set.of("name", "dob", "address", "phone", "email"))
                .build())
        );
    }

    /**
     * Initializes consent records store with healthcare governance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeConsentRecordsStore() {
        return Promise.ofFuture(
            dataCloud.createStore("patient.consents", DataStoreConfig.builder()
                .withSchema("consent-schema-v1")
                .withRetention(Retention.ofYears(10))
                .withGovernance(DataGovernance.HEALTHCARE)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withImmutable(true) // Consent records are immutable once granted
                .withComplianceRules(Set.of(
                    ComplianceRule.NEPAL_DIRECTIVE_2081,
                    ComplianceRule.PRIVACY_ACT_2075,
                    ComplianceRule.FHIR_CONSENT_R4
                ))
                .build())
        );
    }

    /**
     * Initializes document store for medical records and imaging.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeDocumentStore() {
        return Promise.ofFuture(
            dataCloud.createStore("patient.documents", DataStoreConfig.builder()
                .withSchema("document-schema-v1")
                .withRetention(Retention.ofYears(25))
                .withGovernance(DataGovernance.HEALTHCARE)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.DETAILED)
                .withStorageTier(StorageTier.MULTI_TIER) // Hot for recent, cold for archive
                .withIndexFields(Set.of("patient_id", "document_type", "upload_date", "provider_id"))
                .build())
        );
    }

    /**
     * Initializes appointment scheduling store.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeAppointmentStore() {
        return Promise.ofFuture(
            dataCloud.createStore("patient.appointments", DataStoreConfig.builder()
                .withSchema("appointment-schema-v1")
                .withRetention(Retention.ofYears(7))
                .withGovernance(DataGovernance.HEALTHCARE)
                .withEncryption(EncryptionLevel.STANDARD)
                .withAuditLevel(AuditLevel.BASIC)
                .withIndexFields(Set.of("patient_id", "provider_id", "appointment_date", "status"))
                .build())
        );
    }

    /**
     * Initializes audit store for compliance.
     *
     * @return Promise completing when store is initialized
     */
    private Promise<Void> initializeAuditStore() {
        return Promise.ofFuture(
            dataCloud.createStore("phr.audit", DataStoreConfig.builder()
                .withSchema("audit-schema-v1")
                .withRetention(Retention.ofYears(7)) // Nepal Directive 2081
                .withGovernance(DataGovernance.HEALTHCARE)
                .withEncryption(EncryptionLevel.STRONG)
                .withAuditLevel(AuditLevel.FULL)
                .withImmutable(true)
                .withWormStorage(true) // Write Once Read Many
                .build())
        );
    }

    // ==================== Inner Types ====================

    /**
     * Data-Cloud platform interface.
     */
    public interface DataCloudPlatform {
        java.util.concurrent.CompletableFuture<Void> createStore(String name, DataStoreConfig config);
    }

    /**
     * Data store configuration.
     */
    public static class DataStoreConfig {
        private final String schema;
        private final Retention retention;
        private final DataGovernance governance;
        private final EncryptionLevel encryption;
        private final AuditLevel auditLevel;
        private final boolean immutable;
        private final Set<String> immutableFields;
        private final Set<String> piiFields;
        private final Set<ComplianceRule> complianceRules;
        private final StorageTier storageTier;
        private final Set<String> indexFields;
        private final boolean wormStorage;

        private DataStoreConfig(Builder builder) {
            this.schema = builder.schema;
            this.retention = builder.retention;
            this.governance = builder.governance;
            this.encryption = builder.encryption;
            this.auditLevel = builder.auditLevel;
            this.immutable = builder.immutable;
            this.immutableFields = builder.immutableFields;
            this.piiFields = builder.piiFields;
            this.complianceRules = builder.complianceRules;
            this.storageTier = builder.storageTier;
            this.indexFields = builder.indexFields;
            this.wormStorage = builder.wormStorage;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String schema;
            private Retention retention;
            private DataGovernance governance;
            private EncryptionLevel encryption;
            private AuditLevel auditLevel;
            private boolean immutable;
            private Set<String> immutableFields = Set.of();
            private Set<String> piiFields = Set.of();
            private Set<ComplianceRule> complianceRules = Set.of();
            private StorageTier storageTier = StorageTier.STANDARD;
            private Set<String> indexFields = Set.of();
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

            public Builder withImmutableFields(Set<String> immutableFields) {
                this.immutableFields = immutableFields;
                return this;
            }

            public Builder withPiiFields(Set<String> piiFields) {
                this.piiFields = piiFields;
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

            public Builder withWormStorage(boolean wormStorage) {
                this.wormStorage = wormStorage;
                return this;
            }

            public DataStoreConfig build() {
                return new DataStoreConfig(this);
            }
        }
    }

    /**
     * Retention period.
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
        FINANCIAL
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
        NEPAL_DIRECTIVE_2081,
        PRIVACY_ACT_2075,
        FHIR_CONSENT_R4
    }

    /**
     * Storage tier.
     */
    public enum StorageTier {
        STANDARD,
        MULTI_TIER
    }
}
