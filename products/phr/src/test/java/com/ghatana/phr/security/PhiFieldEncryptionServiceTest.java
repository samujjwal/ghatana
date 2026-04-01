package com.ghatana.phr.security;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PhiFieldEncryptionService}.
 *
 * @doc.type class
 * @doc.purpose Validates field-level PHI encryption correctness, round-trip, and classification gating
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhiFieldEncryptionService")
class PhiFieldEncryptionServiceTest extends EventloopTestBase {

    private PhiFieldEncryptionService service;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        service = new PhiFieldEncryptionService(key);
    }

    @Nested
    @DisplayName("C3/C4 encryption")
    class SensitiveClassificationTests {

        @Test
        @DisplayName("encrypts PHI fields for C3 classification")
        void encryptsPhiFieldsForC3() {
            Map<String, String> fields = buildPatientFields();

            Map<String, String> encrypted = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C3));

            assertThat(encrypted.get("ssn")).startsWith("ENC:");
            assertThat(encrypted.get("dateOfBirth")).startsWith("ENC:");
            assertThat(encrypted.get("diagnosis")).startsWith("ENC:");
            assertThat(encrypted.get("email")).startsWith("ENC:");
            // Non-PHI field should not be encrypted
            assertThat(encrypted.get("preferredLanguage")).isEqualTo("en");
        }

        @Test
        @DisplayName("encrypts PHI fields for C4 classification")
        void encryptsPhiFieldsForC4() {
            Map<String, String> fields = buildPatientFields();

            Map<String, String> encrypted = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C4));

            assertThat(encrypted.get("ssn")).startsWith("ENC:");
            assertThat(encrypted.get("medication")).startsWith("ENC:");
        }

        @Test
        @DisplayName("round-trip: encrypt then decrypt recovers original values")
        void roundTrip() {
            Map<String, String> original = buildPatientFields();

            Map<String, String> encrypted = runPromise(() ->
                    service.encryptFields(original, PhrDataClassification.C3));
            Map<String, String> decrypted = runPromise(() ->
                    service.decryptFields(encrypted));

            assertThat(decrypted).containsAllEntriesOf(original);
        }

        @Test
        @DisplayName("each encryption produces unique ciphertext (random IV)")
        void uniqueCiphertextPerCall() {
            Map<String, String> fields = Map.of("ssn", "123-45-6789");

            Map<String, String> enc1 = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C3));
            Map<String, String> enc2 = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C3));

            assertThat(enc1.get("ssn")).isNotEqualTo(enc2.get("ssn"));
        }
    }

    @Nested
    @DisplayName("C1/C2 passthrough")
    class NonSensitiveClassificationTests {

        @Test
        @DisplayName("does not encrypt fields for C1 classification")
        void noEncryptionForC1() {
            Map<String, String> fields = buildPatientFields();

            Map<String, String> result = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C1));

            assertThat(result).containsAllEntriesOf(fields);
            assertThat(result.get("ssn")).doesNotStartWith("ENC:");
        }

        @Test
        @DisplayName("does not encrypt fields for C2 classification")
        void noEncryptionForC2() {
            Map<String, String> fields = buildPatientFields();

            Map<String, String> result = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C2));

            assertThat(result).containsAllEntriesOf(fields);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("handles null field values gracefully")
        void nullFieldValues() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("ssn", null);
            fields.put("preferredLanguage", "en");

            Map<String, String> encrypted = runPromise(() ->
                    service.encryptFields(fields, PhrDataClassification.C3));

            assertThat(encrypted.get("ssn")).isNull();
            assertThat(encrypted.get("preferredLanguage")).isEqualTo("en");
        }

        @Test
        @DisplayName("handles empty map without error")
        void emptyMap() {
            Map<String, String> empty = Map.of();

            Map<String, String> result = runPromise(() ->
                    service.encryptFields(empty, PhrDataClassification.C4));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("isPhiField identifies known PHI fields")
        void phiFieldDetection() {
            assertThat(PhiFieldEncryptionService.isPhiField("ssn")).isTrue();
            assertThat(PhiFieldEncryptionService.isPhiField("dateOfBirth")).isTrue();
            assertThat(PhiFieldEncryptionService.isPhiField("diagnosis")).isTrue();
            assertThat(PhiFieldEncryptionService.isPhiField("geneticData")).isTrue();
            assertThat(PhiFieldEncryptionService.isPhiField("preferredLanguage")).isFalse();
            assertThat(PhiFieldEncryptionService.isPhiField("patientId")).isFalse();
        }
    }

    private Map<String, String> buildPatientFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ssn", "123-45-6789");
        fields.put("dateOfBirth", "1990-05-15");
        fields.put("email", "patient@example.com");
        fields.put("phoneNumber", "+977-984-1234567");
        fields.put("diagnosis", "J06.9 - Upper respiratory infection");
        fields.put("medication", "Amoxicillin 500mg");
        fields.put("labResult", "WBC 11.2 K/uL");
        fields.put("preferredLanguage", "en");
        fields.put("patientId", "pat-001");
        return fields;
    }
}
