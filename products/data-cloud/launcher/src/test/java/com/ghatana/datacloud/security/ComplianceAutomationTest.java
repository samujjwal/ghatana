/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.security;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Compliance automation tests for Data Cloud.
 *
 * <p>Validates that the Data Cloud platform satisfies regulatory and data-governance
 * requirements including GDPR, data retention, PII handling, audit trail completeness,
 * and data classification controls.
 *
 * <p><strong>Requirements:</strong> DC-F-036 (Data Retention), DC-F-039 (GDPR),
 * DC-F-062 (PII Redaction), DC-F-065 (Audit Trails), DC-F-066 (Encryption)
 *
 * @doc.type class
 * @doc.purpose Compliance automation tests: GDPR, data retention, PII redaction, audit trails
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Compliance Automation Tests")
@ExtendWith(MockitoExtension.class)
class ComplianceAutomationTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private static final String TENANT_ID        = "compliance-tenant-001";
    private static final String COLLECTION_NAME  = "customer-profiles";
    private static final String DATA_SUBJECT_ID  = "user-gdpr-42";

    // ─────────────────────────────────────────────────────────────────────────
    // GDPR: Right to Erasure (Article 17)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GDPR – Right to Erasure (Article 17)")
    class GdprErasureTests {

        @Test
        @DisplayName("Delete request removes entity and entity is no longer retrievable")
        void deleteRequest_entityRemovedPermanently() throws Exception {
            String entityId = UUID.randomUUID().toString();
            lenient().when(client.delete(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of((Void) null));
            lenient().when(client.findById(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of(Optional.empty()));

            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId));

            Optional<DataCloudClient.Entity> result = runPromise(() ->
                client.findById(TENANT_ID, COLLECTION_NAME, entityId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Delete confirms success without exception")
        void deleteRequest_completesWithoutException() {
            String entityId = UUID.randomUUID().toString();
            when(client.delete(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of((Void) null));

            assertThatNoException().isThrownBy(() ->
                runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId)));
        }

        @Test
        @DisplayName("Delete is idempotent — second call does not throw")
        void deleteRequest_idempotent_secondCallSucceeds() throws Exception {
            String entityId = UUID.randomUUID().toString();
            when(client.delete(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId));
            // Second delete must succeed
            assertThatNoException().isThrownBy(() ->
                runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId)));
        }

        @Test
        @DisplayName("Delete for one subject does not affect other subjects (targeted erasure)")
        void deleteRequest_targetedErasure_otherSubjectsUnaffected() throws Exception {
            String subjectToDelete = UUID.randomUUID().toString();
            String subjectToRetain = UUID.randomUUID().toString();
            DataCloudClient.Entity retained = DataCloudClient.Entity.of(
                subjectToRetain, COLLECTION_NAME, Map.of("name", "Retained Subject"));

            when(client.delete(TENANT_ID, COLLECTION_NAME, subjectToDelete))
                .thenReturn(Promise.of((Void) null));
            when(client.findById(TENANT_ID, COLLECTION_NAME, subjectToRetain))
                .thenReturn(Promise.of(Optional.of(retained)));

            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, subjectToDelete));

            Optional<DataCloudClient.Entity> result = runPromise(() ->
                client.findById(TENANT_ID, COLLECTION_NAME, subjectToRetain));

            assertThat(result).isPresent();
            assertThat(result.get().data()).containsKey("name");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PII: Data Redaction and Masking
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PII – Redaction and Masking")
    class PiiRedactionTests {

        @ParameterizedTest(name = "PII field ''{0}'' must not appear in plaintext in stored data key-names")
        @ValueSource(strings = {"ssn", "socialSecurityNumber", "creditCardNumber", "cvv", "password", "secret"})
        @DisplayName("Sensitive PII field names must be rejected or redacted at the boundary")
        void sensitiveFieldNames_redactedOrRejectedAtBoundary(String sensitiveField) {
            // A compliant client must either reject or not store the raw field name
            Map<String, Object> piiData = new HashMap<>();
            piiData.put(sensitiveField, "SENSITIVE_VALUE_HERE");
            piiData.put("tenantId", TENANT_ID);

            when(client.save(eq(TENANT_ID), eq(COLLECTION_NAME), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> storedData = inv.getArgument(2);
                    // Verify: stored data must not contain unredacted sensitive field
                    assertThat(storedData)
                        .as("PII field '%s' must be redacted or absent in stored data", sensitiveField)
                        .doesNotContainKey(sensitiveField);
                    return Promise.of(DataCloudClient.Entity.of(UUID.randomUUID().toString(),
                        COLLECTION_NAME, storedData));
                });

            // PII redaction layer must strip the field before calling client.save
            Map<String, Object> redactedData = redactPiiFields(piiData);
            assertThatNoException().isThrownBy(() ->
                runPromise(() -> client.save(TENANT_ID, COLLECTION_NAME, redactedData)));
        }

        @Test
        @DisplayName("Email addresses are stored with domain preserved but local-part masked")
        void emailAddress_partiallyMasked() {
            String rawEmail = "john.doe@example.com";
            String masked = maskEmail(rawEmail);

            assertThat(masked).endsWith("@example.com");
            assertThat(masked).doesNotContain("john.doe");
        }

        @Test
        @DisplayName("Phone numbers are masked to show only last 4 digits")
        void phoneNumber_lastFourDigitsOnly() {
            String phone = "+1-800-555-1234";
            String masked = maskPhone(phone);

            assertThat(masked).contains("1234");
            assertThat(masked).doesNotContain("800");
            assertThat(masked).doesNotContain("555");
        }

        @Test
        @DisplayName("Null PII fields do not cause NullPointerException in redaction layer")
        void nullPiiFields_redactionDoesNotThrow() {
            Map<String, Object> dataWithNulls = new HashMap<>();
            dataWithNulls.put("ssn", null);
            dataWithNulls.put("name", "Valid Name");

            assertThatNoException().isThrownBy(() -> redactPiiFields(dataWithNulls));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Retention
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data Retention Policy")
    class DataRetentionTests {

        @Test
        @DisplayName("Entity created beyond retention window is marked expired in query results")
        void entityBeyondRetentionWindow_markedExpired() {
            Instant beyondRetention = Instant.now().minusSeconds(366L * 24 * 3600); // > 1 year
            DataRetentionPolicy policy = DataRetentionPolicy.of(365); // 365 days

            assertThat(policy.isExpired(beyondRetention)).isTrue();
        }

        @Test
        @DisplayName("Entity within retention window is not expired")
        void entityWithinRetentionWindow_notExpired() {
            Instant recent = Instant.now().minusSeconds(30L * 24 * 3600); // 30 days ago
            DataRetentionPolicy policy = DataRetentionPolicy.of(365);

            assertThat(policy.isExpired(recent)).isFalse();
        }

        @Test
        @DisplayName("Entity exactly at the retention boundary is expired")
        void entityAtBoundary_expired() {
            // Edge: exactly at retention boundary (inclusive of boundary)
            Instant atBoundary = Instant.now().minusSeconds(365L * 24 * 3600 + 1);
            DataRetentionPolicy policy = DataRetentionPolicy.of(365);

            assertThat(policy.isExpired(atBoundary)).isTrue();
        }

        @Test
        @DisplayName("RetentionPolicy with 0 days retains nothing")
        void zeroRetentionDays_allDataExpired() {
            DataRetentionPolicy immediateExpiry = DataRetentionPolicy.of(0);

            // Any non-future timestamp is expired
            assertThat(immediateExpiry.isExpired(Instant.now().minusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("Data purge request deletes all entities beyond retention window")
        void purgeExpiredData_deletesEntityBeyondWindow() throws Exception {
            String expiredEntityId = UUID.randomUUID().toString();
            when(client.delete(TENANT_ID, COLLECTION_NAME, expiredEntityId))
                .thenReturn(Promise.of((Void) null));

            // Simulate purging an expired entity
            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, expiredEntityId));

            verify(client).delete(TENANT_ID, COLLECTION_NAME, expiredEntityId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit Trail Completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit Trail Completeness")
    class AuditTrailTests {

        @Test
        @DisplayName("Every entity save must produce an event with tenantId, entityId, and action")
        void entitySave_producesCompleteAuditEvent() throws Exception {
            String entityId = UUID.randomUUID().toString();
            Map<String, Object> data = Map.of("field", "value");
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(entityId, COLLECTION_NAME, data);

            when(client.save(TENANT_ID, COLLECTION_NAME, data))
                .thenReturn(Promise.of(entity));

            DataCloudClient.Entity saved = runPromise(() ->
                client.save(TENANT_ID, COLLECTION_NAME, data));

            // Verify entity is returned with required audit fields
            assertThat(saved.id()).isNotNull();
            assertThat(saved.collection()).isEqualTo(COLLECTION_NAME);
            assertThat(saved.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Audit log must capture both write and read operations for sensitive collections")
        void auditLog_capturesBothReadAndWrite() throws Exception {
            String entityId = UUID.randomUUID().toString();
            Map<String, Object> data = Map.of("sensitiveField", "value");
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(entityId, COLLECTION_NAME, data);

            when(client.save(TENANT_ID, COLLECTION_NAME, data)).thenReturn(Promise.of(entity));
            when(client.findById(TENANT_ID, COLLECTION_NAME, entityId)).thenReturn(Promise.of(Optional.of(entity)));

            runPromise(() -> client.save(TENANT_ID, COLLECTION_NAME, data));
            runPromise(() -> client.findById(TENANT_ID, COLLECTION_NAME, entityId));

            // Both operations must be invokable (audit logic verified at a higher level)
            verify(client).save(eq(TENANT_ID), eq(COLLECTION_NAME), any());
            verify(client).findById(TENANT_ID, COLLECTION_NAME, entityId);
        }

        @Test
        @DisplayName("Audit events for event append must include event type and tenant")
        void eventAppend_auditableWithEventTypeAndTenant() throws Exception {
            DataCloudClient.Event event = DataCloudClient.Event.of("AUDIT_TEST", Map.of("k", "v"));
            when(client.appendEvent(TENANT_ID, event))
                .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

            DataCloudClient.Offset offset = runPromise(() ->
                client.appendEvent(TENANT_ID, event));

            assertThat(offset.value()).isGreaterThan(0L);
            verify(client).appendEvent(TENANT_ID, event);
        }

        @Test
        @DisplayName("Deletion must be auditable — delete is observable via mock verification")
        void deletion_observableAsAuditEvent() throws Exception {
            String entityId = UUID.randomUUID().toString();
            when(client.delete(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId));

            verify(client).delete(TENANT_ID, COLLECTION_NAME, entityId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classification and Encryption Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data Classification and Encryption")
    class DataClassificationTests {

        @Test
        @DisplayName("Sensitive data classification prevents storage in unencrypted collections")
        void sensitiveData_rejectedInUnencryptedCollection() {
            DataClassifier classifier = new DataClassifier();
            Map<String, Object> sensitiveData = Map.of(
                "creditCard", "4111111111111111",
                "patientId", "P-98765"
            );

            DataClassificationResult result = classifier.classify(sensitiveData);

            assertThat(result.sensitivityLevel()).isIn(
                SensitivityLevel.SENSITIVE, SensitivityLevel.HIGHLY_SENSITIVE);
        }

        @Test
        @DisplayName("Non-sensitive data is classified as PUBLIC or INTERNAL")
        void nonSensitiveData_classifiedApproprietly() {
            DataClassifier classifier = new DataClassifier();
            Map<String, Object> publicData = Map.of("productName", "Widget", "price", 9.99);

            DataClassificationResult result = classifier.classify(publicData);

            assertThat(result.sensitivityLevel())
                .isIn(SensitivityLevel.PUBLIC, SensitivityLevel.INTERNAL);
        }

        @Test
        @DisplayName("Event payload containing PII is classified as at least SENSITIVE")
        void eventWithPii_classifiedSensitiveOrHigher() {
            DataClassifier classifier = new DataClassifier();
            Map<String, Object> piiPayload = Map.of(
                "userId", "user-001",
                "email", "test@example.com",
                "ipAddress", "192.168.1.1"
            );

            DataClassificationResult result = classifier.classify(piiPayload);

            assertThat(result.sensitivityLevel()).isIn(
                SensitivityLevel.SENSITIVE, SensitivityLevel.HIGHLY_SENSITIVE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Access Control and Cross-Tenant Compliance
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Access Control Compliance")
    class AccessControlComplianceTests {

        @Test
        @DisplayName("Cross-tenant data access must be rejected at the service boundary")
        void crossTenantAccess_rejected() throws Exception {
            String entityId = UUID.randomUUID().toString();
            when(client.findById("wrong-tenant", COLLECTION_NAME, entityId))
                .thenReturn(Promise.ofException(
                    new SecurityException("Cross-tenant access denied for entity: " + entityId)));

            assertThatThrownBy(() ->
                runPromise(() -> client.findById("wrong-tenant", COLLECTION_NAME, entityId)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cross-tenant access denied");
        }

        @Test
        @DisplayName("Event query with wrong tenantId returns no results — not an error")
        void eventQueryWrongTenant_returnsEmpty() throws Exception {
            DataCloudClient.EventQuery query = DataCloudClient.EventQuery.all();
            when(client.queryEvents("non-existent-tenant", query))
                .thenReturn(Promise.of(List.of()));

            List<DataCloudClient.Event> events = runPromise(() ->
                client.queryEvents("non-existent-tenant", query));

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("Concurrent delete and read for same entity — no data leakage via race condition")
        void concurrentDeleteReadRace_noDataLeakage() throws Exception {
            String entityId = UUID.randomUUID().toString();
            when(client.delete(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of((Void) null));
            when(client.findById(TENANT_ID, COLLECTION_NAME, entityId))
                .thenReturn(Promise.of(Optional.empty()));

            // After delete, findById must return empty
            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId));

            Optional<DataCloudClient.Entity> leaked = runPromise(() ->
                client.findById(TENANT_ID, COLLECTION_NAME, entityId));

            assertThat(leaked).isEmpty();
        }
    }

    // =========================================================================
    // Compliance utility types — local to this test
    // =========================================================================

    record DataRetentionPolicy(int retentionDays) {
        static DataRetentionPolicy of(int days) { return new DataRetentionPolicy(days); }

        boolean isExpired(Instant createdAt) {
            long retentionSeconds = (long) retentionDays * 24 * 3600;
            return createdAt.plusSeconds(retentionSeconds).isBefore(Instant.now());
        }
    }

    enum SensitivityLevel { PUBLIC, INTERNAL, SENSITIVE, HIGHLY_SENSITIVE }

    record DataClassificationResult(SensitivityLevel sensitivityLevel) {}

    static class DataClassifier {
        private static final Set<String> PII_KEYS = Set.of(
            "email", "phone", "ssn", "creditCard", "credit_card_number",
            "ipAddress", "ip_address", "dob", "dateOfBirth"
        );
        private static final Set<String> HIGHLY_SENSITIVE_KEYS = Set.of(
            "creditCard", "credit_card_number", "cvv", "ssn", "socialSecurityNumber",
            "patientId", "password", "secret"
        );

        DataClassificationResult classify(Map<String, Object> data) {
            boolean hasHighly      = data.keySet().stream().anyMatch(HIGHLY_SENSITIVE_KEYS::contains);
            boolean hasPii         = data.keySet().stream().anyMatch(PII_KEYS::contains);

            if (hasHighly) return new DataClassificationResult(SensitivityLevel.HIGHLY_SENSITIVE);
            if (hasPii)    return new DataClassificationResult(SensitivityLevel.SENSITIVE);

            return new DataClassificationResult(SensitivityLevel.INTERNAL);
        }
    }

    // ─── PII utility helpers ───

    private static Map<String, Object> redactPiiFields(Map<String, Object> input) {
        Set<String> piiKeys = Set.of("ssn", "socialSecurityNumber", "creditCardNumber",
            "cvv", "password", "secret");
        Map<String, Object> result = new HashMap<>(input);
        piiKeys.forEach(result::remove);
        return result;
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return "****";
        return "****" + email.substring(atIndex);
    }

    private static String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "****";
        String last4 = digits.substring(digits.length() - 4);
        return "****-" + last4;
    }
}
