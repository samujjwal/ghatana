package com.ghatana.kernel.security;

import com.ghatana.core.eventloop.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PrivacyManager contract.
 *
 * @doc.type class
 * @doc.purpose PrivacyManager contract tests (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("PrivacyManager Tests")
class PrivacyManagerTest extends EventloopTestBase {

    private final InMemoryPrivacyManager manager = new InMemoryPrivacyManager(Executors.newVirtualThreadPerTaskExecutor());

    @Test
    @DisplayName("Should check consent status")
    void shouldCheckConsentStatus() {
        String tenantId = "tenant-1";
        String userId = "user-1";
        String purpose = "marketing";

        ConsentStatus before = runPromise(() -> manager.checkConsent(
            new PrivacyManager.DataRequest(userId, "email", purpose, Map.of()),
            tenantId
        ));
        assertThat(before).isEqualTo(PrivacyManager.ConsentStatus.NOT_REQUIRED);

        runPromise(() -> manager.recordConsent(tenantId, userId, purpose, true));

        ConsentStatus after = runPromise(() -> manager.checkConsent(
            new PrivacyManager.DataRequest(userId, "email", purpose, Map.of()),
            tenantId
        ));
        assertThat(after).isEqualTo(PrivacyManager.ConsentStatus.GRANTED);
    }

    @Test
    @DisplayName("Should classify data as PII")
    void shouldClassifyDataAsPII() {
        PrivacyManager.DataClassification email = manager.classifyData("user@example.com");
        assertThat(email).isEqualTo(PrivacyManager.DataClassification.PII);

        PrivacyManager.DataClassification phone = manager.classifyData("+1234567890");
        assertThat(phone).isEqualTo(PrivacyManager.DataClassification.PII);

        PrivacyManager.DataClassification ssn = manager.classifyData("123-45-6789");
        assertThat(ssn).isEqualTo(PrivacyManager.DataClassification.PII);
    }

    @Test
    @DisplayName("Should classify data as PHI")
    void shouldClassifyDataAsPHI() {
        PrivacyManager.DataClassification medical = manager.classifyData("Patient medical record");
        assertThat(medical).isEqualTo(PrivacyManager.DataClassification.PHI);
    }

    @Test
    @DisplayName("Should classify data as RESTRICTED")
    void shouldClassifyDataAsRESTRICTED() {
        PrivacyManager.DataClassification card = manager.classifyData("Credit card account number");
        assertThat(card).isEqualTo(PrivacyManager.DataClassification.RESTRICTED);
    }

    @Test
    @DisplayName("Should enforce residency requirements")
    void shouldEnforceResidencyRequirements() {
        String tenantId = "tenant-1";
        PrivacyManager.DataLocation usLocation = new PrivacyManager.DataLocation("us-east", "US", "dc1");
        PrivacyManager.DataLocation euLocation = new PrivacyManager.DataLocation("eu-west", "EU", "dc2");
        PrivacyManager.DataLocation otherLocation = new PrivacyManager.DataLocation("asia-east", "CN", "dc3");

        boolean usAllowed = runPromise(() -> manager.enforceResidency(usLocation, tenantId));
        assertThat(usAllowed).isTrue();

        boolean euAllowed = runPromise(() -> manager.enforceResidency(euLocation, tenantId));
        assertThat(euAllowed).isTrue();

        boolean otherAllowed = runPromise(() -> manager.enforceResidency(otherLocation, tenantId));
        assertThat(otherAllowed).isFalse();
    }

    @Test
    @DisplayName("Should encrypt and decrypt PII")
    void shouldEncryptAndDecryptPII() {
        String tenantId = "tenant-1";
        String pii = "john.doe@example.com";

        String encrypted = runPromise(() -> manager.encryptPII(tenantId, pii));
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(pii);

        String decrypted = runPromise(() -> manager.decryptPII(tenantId, encrypted));
        assertThat(decrypted).isEqualTo(pii);
    }

    @Test
    @DisplayName("Should hash PII identifier")
    void shouldHashPIIIdentifier() {
        String tenantId = "tenant-1";
        String identifier = "user@example.com";

        String hash1 = runPromise(() -> manager.hashPIIIdentifier(tenantId, identifier));
        String hash2 = runPromise(() -> manager.hashPIIIdentifier(tenantId, identifier));

        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(16);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should redact PII from data")
    void shouldRedactPIIFromData() {
        String data = "Contact: user@example.com, Phone: +1234567890, SSN: 123-45-6789";

        String redacted = manager.redactPII(data, PrivacyManager.DataClassification.PII);

        assertThat(redacted).doesNotContain("user@example.com");
        assertThat(redacted).doesNotContain("+1234567890");
        assertThat(redacted).doesNotContain("123-45-6789");
        assertThat(redacted).contains("[REDACTED-EMAIL]");
        assertThat(redacted).contains("[REDACTED-PHONE]");
        assertThat(redacted).contains("[REDACTED-SSN]");
    }

    @Test
    @DisplayName("Should not redact public data")
    void shouldNotRedactPublicData() {
        String data = "This is public information";

        String redacted = manager.redactPII(data, PrivacyManager.DataClassification.PUBLIC);

        assertThat(redacted).isEqualTo(data);
    }

    @Test
    @DisplayName("Should process DSAR request")
    void shouldProcessDSARRequest() {
        PrivacyManager.DSARRequest request = new PrivacyManager.DSARRequest(
            "req-1",
            "tenant-1",
            "subject-1",
            PrivacyManager.DSARType.ACCESS,
            "requester-1",
            java.time.Instant.now()
        );

        PrivacyManager.DSarResult result = runPromise(() -> manager.processDSAR(request));

        assertThat(result.requestId()).isEqualTo("req-1");
        assertThat(result.status()).isEqualTo(PrivacyManager.DSARStatus.COMPLETED);
        assertThat(result.data()).isNotNull();
        assertThat(result.message()).isEqualTo("DSAR processed successfully");
    }

    @Test
    @DisplayName("Should delete subject data")
    void shouldDeleteSubjectData() {
        String tenantId = "tenant-1";
        String subjectId = "subject-1";

        runPromise(() -> manager.recordConsent(tenantId, subjectId, "marketing", true));

        runPromise(() -> manager.deleteSubjectData(tenantId, subjectId));

        PrivacyManager.ConsentStatus consent = runPromise(() -> manager.checkConsent(
            new PrivacyManager.DataRequest(subjectId, "email", "marketing", Map.of()),
            tenantId
        ));
        assertThat(consent).isEqualTo(PrivacyManager.ConsentStatus.NOT_REQUIRED);
    }

    @Test
    @DisplayName("Should export subject data")
    void shouldExportSubjectData() {
        String tenantId = "tenant-1";
        String subjectId = "subject-1";

        Map<String, Object> data = runPromise(() -> manager.exportSubjectData(tenantId, subjectId));

        assertThat(data).isNotNull();
        assertThat(data.get("subjectId")).isEqualTo(subjectId);
        assertThat(data.get("tenantId")).isEqualTo(tenantId);
        assertThat(data).containsKey("exportedAt");
    }

    @Test
    @DisplayName("Should get privacy policy")
    void shouldGetPrivacyPolicy() {
        String tenantId = "tenant-1";

        Optional<PrivacyManager.Policy> before = runPromise(() -> manager.getPrivacyPolicy(tenantId));
        assertThat(before).isEmpty();

        PrivacyManager.DataRetention retention = new PrivacyManager.DataRetention(365, 2555, 90);
        PrivacyManager.Policy policy = new PrivacyManager.Policy(
            tenantId,
            "1.0.0",
            Map.of("marketing", true, "analytics", false),
            retention,
            "2026-01-01"
        );

        PrivacyManager.Policy storedPolicy = runPromise(() -> manager.getPrivacyPolicy(tenantId).orElseThrow());
    }
}
