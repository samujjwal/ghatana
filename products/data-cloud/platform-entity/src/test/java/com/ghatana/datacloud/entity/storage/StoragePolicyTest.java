package com.ghatana.datacloud.entity.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for StoragePolicy domain model.
 *
 * @doc.type class
 * @doc.purpose Verifies StoragePolicy builder validation and behavioral methods
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("StoragePolicy Tests")
class StoragePolicyTest {

    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final String TENANT_ID = "tenant-123";

    @Test
    @DisplayName("builder creates valid policy with required fields")
    void builderCreatesValidPolicyWithRequiredFields() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        assertThat(policy.getName()).isEqualTo("test-policy");
        assertThat(policy.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(policy.getCollectionId()).isEqualTo(COLLECTION_ID);
        assertThat(policy.getAllowedBackends()).containsExactly(StorageBackendType.RELATIONAL);
        assertThat(policy.getPrimaryBackend()).isEqualTo(StorageBackendType.RELATIONAL);
    }

    @Test
    @DisplayName("builder fails when name is blank")
    void builderFailsWhenNameIsBlank() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("  ")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("builder fails when tenantId is blank")
    void builderFailsWhenTenantIdIsBlank() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId("  ")
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId is required");
    }

    @Test
    @DisplayName("builder fails when allowedBackends is empty")
    void builderFailsWhenAllowedBackendsIsEmpty() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("allowedBackends must not be empty");
    }

    @Test
    @DisplayName("builder fails when primaryBackend not in allowedBackends")
    void builderFailsWhenPrimaryBackendNotInAllowedBackends() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.KEY_VALUE)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("primaryBackend must be in allowedBackends");
    }

    @Test
    @DisplayName("builder fails when restricted data lacks encryption")
    void builderFailsWhenRestrictedDataLacksEncryption() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .dataSensitivity(StoragePolicy.DataSensitivity.RESTRICTED)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .requiresAuditLogging(true)
            .requiresEncryption(false)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RESTRICTED data must require encryption");
    }

    @Test
    @DisplayName("builder fails when restricted data lacks audit logging")
    void builderFailsWhenRestrictedDataLacksAuditLogging() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .dataSensitivity(StoragePolicy.DataSensitivity.RESTRICTED)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .requiresAuditLogging(false)
            .requiresEncryption(true)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RESTRICTED data must require audit logging");
    }

    @Test
    @DisplayName("builder accepts restricted data with encryption and audit logging")
    void builderAcceptsRestrictedDataWithEncryptionAndAuditLogging() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .dataSensitivity(StoragePolicy.DataSensitivity.RESTRICTED)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .requiresAuditLogging(true)
            .requiresEncryption(true)
            .build();

        assertThat(policy.getDataSensitivity()).isEqualTo(StoragePolicy.DataSensitivity.RESTRICTED);
        assertThat(policy.isEncryptionRequired()).isTrue();
        assertThat(policy.isAuditLoggingRequired()).isTrue();
    }

    @Test
    @DisplayName("builder fails when minReplicationCount is less than 1")
    void builderFailsWhenMinReplicationCountIsLessThan1() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .minReplicationCount(0)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minReplicationCount must be >= 1");
    }

    @Test
    @DisplayName("builder fails when ttlDays is negative")
    void builderFailsWhenTtlDaysIsNegative() {
        assertThatThrownBy(() -> StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .ttlDays(-1)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttlDays cannot be negative");
    }

    @Test
    @DisplayName("supports returns true for allowed backend")
    void supportsReturnsTrueForAllowedBackend() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL, StorageBackendType.KEY_VALUE)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        assertThat(policy.supports(StorageBackendType.RELATIONAL)).isTrue();
        assertThat(policy.supports(StorageBackendType.KEY_VALUE)).isTrue();
    }

    @Test
    @DisplayName("supports returns false for disallowed backend")
    void supportsReturnsFalseForDisallowedBackend() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        assertThat(policy.supports(StorageBackendType.KEY_VALUE)).isFalse();
    }

    @Test
    @DisplayName("isComplianceRuleEnabled returns true for enabled rule")
    void isComplianceRuleEnabledReturnsTrueForEnabledRule() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .addComplianceRule("gdpr", true)
            .build();

        assertThat(policy.isComplianceRuleEnabled("gdpr")).isTrue();
    }

    @Test
    @DisplayName("isComplianceRuleEnabled returns false for disabled rule")
    void isComplianceRuleEnabledReturnsFalseForDisabledRule() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .addComplianceRule("gdpr", false)
            .build();

        assertThat(policy.isComplianceRuleEnabled("gdpr")).isFalse();
    }

    @Test
    @DisplayName("isComplianceRuleEnabled returns false for unknown rule")
    void isComplianceRuleEnabledReturnsFalseForUnknownRule() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        assertThat(policy.isComplianceRuleEnabled("unknown")).isFalse();
    }

    @Test
    @DisplayName("getPreferredBackends returns primary first then others")
    void getPreferredBackendsReturnsPrimaryFirstThenOthers() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.KEY_VALUE, StorageBackendType.RELATIONAL, StorageBackendType.BLOB_STORAGE)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        assertThat(policy.getPreferredBackends())
            .containsExactly(StorageBackendType.RELATIONAL, StorageBackendType.KEY_VALUE, StorageBackendType.BLOB_STORAGE);
    }

    @Test
    @DisplayName("DataSensitivity isHigherThan works correctly")
    void dataSensitivityIsHigherThanWorksCorrectly() {
        assertThat(StoragePolicy.DataSensitivity.RESTRICTED.isHigherThan(StoragePolicy.DataSensitivity.CONFIDENTIAL)).isTrue();
        assertThat(StoragePolicy.DataSensitivity.CONFIDENTIAL.isHigherThan(StoragePolicy.DataSensitivity.INTERNAL)).isTrue();
        assertThat(StoragePolicy.DataSensitivity.INTERNAL.isHigherThan(StoragePolicy.DataSensitivity.PUBLIC)).isTrue();
        assertThat(StoragePolicy.DataSensitivity.PUBLIC.isHigherThan(StoragePolicy.DataSensitivity.PUBLIC)).isFalse();
    }

    @Test
    @DisplayName("DataSensitivity fromString parses correctly")
    void dataSensitivityFromStringParsesCorrectly() {
        assertThat(StoragePolicy.DataSensitivity.fromString("public")).isEqualTo(StoragePolicy.DataSensitivity.PUBLIC);
        assertThat(StoragePolicy.DataSensitivity.fromString("internal")).isEqualTo(StoragePolicy.DataSensitivity.INTERNAL);
        assertThat(StoragePolicy.DataSensitivity.fromString("confidential")).isEqualTo(StoragePolicy.DataSensitivity.CONFIDENTIAL);
        assertThat(StoragePolicy.DataSensitivity.fromString("restricted")).isEqualTo(StoragePolicy.DataSensitivity.RESTRICTED);
    }

    @Test
    @DisplayName("DataSensitivity fromString is case-insensitive")
    void dataSensitivityFromStringIsCaseInsensitive() {
        assertThat(StoragePolicy.DataSensitivity.fromString("PUBLIC")).isEqualTo(StoragePolicy.DataSensitivity.PUBLIC);
        assertThat(StoragePolicy.DataSensitivity.fromString("Restricted")).isEqualTo(StoragePolicy.DataSensitivity.RESTRICTED);
    }

    @Test
    @DisplayName("DataSensitivity fromString throws for unknown value")
    void dataSensitivityFromStringThrowsForUnknownValue() {
        assertThatThrownBy(() -> StoragePolicy.DataSensitivity.fromString("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown DataSensitivity");
    }

    @Test
    @DisplayName("policy is immutable - collections are defensive copies")
    void policyIsImmutableCollectionsAreDefensiveCopies() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .complianceRules(Map.of("gdpr", true))
            .build();

        // Modify returned collections
        policy.getAllowedBackends().add(StorageBackendType.KEY_VALUE);
        policy.getComplianceRules().put("hipaa", true);

        // Original policy unchanged
        assertThat(policy.getAllowedBackends()).hasSize(1);
        assertThat(policy.getComplianceRules()).hasSize(1);
    }

    @Test
    @DisplayName("equals and hashCode based on name and tenantId")
    void equalsAndHashCodeBasedOnNameAndTenantId() {
        StoragePolicy policy1 = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        StoragePolicy policy2 = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(UUID.randomUUID())
            .allowedBackends(StorageBackendType.KEY_VALUE)
            .primaryBackend(StorageBackendType.KEY_VALUE)
            .build();

        assertThat(policy1).isEqualTo(policy2);
        assertThat(policy1.hashCode()).isEqualTo(policy2.hashCode());
    }

    @Test
    @DisplayName("toString includes key fields")
    void toStringIncludesKeyFields() {
        StoragePolicy policy = StoragePolicy.builder()
            .name("test-policy")
            .tenantId(TENANT_ID)
            .collectionId(COLLECTION_ID)
            .dataSensitivity(StoragePolicy.DataSensitivity.RESTRICTED)
            .allowedBackends(StorageBackendType.RELATIONAL)
            .primaryBackend(StorageBackendType.RELATIONAL)
            .build();

        String str = policy.toString();
        assertThat(str).contains("test-policy");
        assertThat(str).contains(TENANT_ID);
        assertThat(str).contains("RESTRICTED");
        assertThat(str).contains("RELATIONAL");
    }
}
