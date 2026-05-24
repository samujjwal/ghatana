/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.redaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Phase 3: Contract tests for encryption and redaction capabilities.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Field masking for sensitive data</li>
 *   <li>Encryption of sensitive fields at rest</li>
 *   <li>Redaction of PII in audit logs</li>
 *   <li>Key management validation</li>
 * </ul>
 */
@DisplayName("Encryption and Redaction Tests (Phase 3)")
class EncryptionRedactionTest {

    private final FieldMasker fieldMasker = new FieldMasker();

    // =========================================================================
    //  Field Masking
    // =========================================================================

    @Nested
    @DisplayName("Field Masking")
    class FieldMaskingTests {

        @Test
        @DisplayName("masks email addresses")
        void masksEmailAddresses() {
            String email = "user@example.com";
            String masked = fieldMasker.maskEmail(email);

            assertThat(masked).doesNotContain("user");
            assertThat(masked).contains("***");
            assertThat(masked).endsWith("@example.com");
        }

        @Test
        @DisplayName("masks phone numbers")
        void masksPhoneNumbers() {
            String phone = "+1-555-123-4567";
            String masked = fieldMasker.maskPhone(phone);

            assertThat(masked).doesNotContain("123");
            assertThat(masked).contains("xxx");
        }

        @Test
        @DisplayName("masks credit card numbers")
        void masksCreditCardNumbers() {
            String cc = "4111111111111111";
            String masked = fieldMasker.maskCreditCard(cc);

            assertThat(masked).doesNotContain("1111");
            assertThat(masked).startsWith("xxxx-xxxx-xxxx-");
        }

        @Test
        @DisplayName("masks social security numbers")
        void masksSocialSecurityNumbers() {
            String ssn = "123-45-6789";
            String masked = fieldMasker.maskSSN(ssn);

            assertThat(masked).doesNotContain("6789");
            assertThat(masked).contains("xxx-xx-");
        }

        @Test
        @DisplayName("masks arbitrary sensitive fields")
        void masksArbitrarySensitiveFields() {
            Map<String, Object> data = Map.of(
                "apiKey", "secret-key-123",
                "password", "my-password",
                "name", "John Doe"
            );

            Map<String, Object> masked = fieldMasker.maskFields(data, "apiKey", "password");

            assertThat(masked.get("apiKey")).isNotEqualTo("secret-key-123");
            assertThat(masked.get("password")).isNotEqualTo("my-password");
            assertThat(masked.get("name")).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("handles null input gracefully")
        void handlesNullInputGracefully() {
            String masked = fieldMasker.maskEmail(null);
            assertThat(masked).isNull();
        }

        @Test
        @DisplayName("requires non-null field list for masking")
        void requiresNonNullFieldListForMasking() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> fieldMasker.maskFields(Map.of(), (String[]) null))
                .withMessageContaining("fields must not be null");
        }
    }

    // =========================================================================
    //  Encryption at Rest
    // =========================================================================

    @Nested
    @DisplayName("Encryption at Rest")
    class EncryptionTests {

        @Test
        @DisplayName("encrypts sensitive data")
        void encryptsSensitiveData() {
            String plaintext = "sensitive-data-123";
            String encrypted = fieldMasker.encrypt(plaintext);

            assertThat(encrypted).isNotEqualTo(plaintext);
            assertThat(encrypted).isNotEmpty();
        }

        @Test
        @DisplayName("decrypts encrypted data")
        void decryptsEncryptedData() {
            String plaintext = "sensitive-data-123";
            String encrypted = fieldMasker.encrypt(plaintext);
            String decrypted = fieldMasker.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("encryption is deterministic with same key")
        void encryptionIsDeterministicWithSameKey() {
            String plaintext = "sensitive-data-123";
            String encrypted1 = fieldMasker.encrypt(plaintext);
            String encrypted2 = fieldMasker.encrypt(plaintext);

            // With same key, should produce same result (for testing)
            // In production, use randomized IV for security
            assertThat(encrypted1).isEqualTo(encrypted2);
        }

        @Test
        @DisplayName("handles null encryption input")
        void handlesNullEncryptionInput() {
            String encrypted = fieldMasker.encrypt(null);
            assertThat(encrypted).isNull();
        }

        @Test
        @DisplayName("handles null decryption input")
        void handlesNullDecryptionInput() {
            String decrypted = fieldMasker.decrypt(null);
            assertThat(decrypted).isNull();
        }
    }

    // =========================================================================
    //  Audit Log Redaction
    // =========================================================================

    @Nested
    @DisplayName("Audit Log Redaction")
    class AuditRedactionTests {

        @Test
        @DisplayName("redacts PII from audit details")
        void redactsPiiFromAuditDetails() {
            Map<String, Object> auditData = Map.of(
                "userId", "user-123",
                "email", "user@example.com",
                "action", "CREATE",
                "ssn", "123-45-6789"
            );

            Map<String, Object> redacted = fieldMasker.redactAuditData(auditData);

            assertThat(redacted.get("userId")).isEqualTo("user-123");
            assertThat(redacted.get("email")).isNotEqualTo("user@example.com");
            assertThat(redacted.get("action")).isEqualTo("CREATE");
            assertThat(redacted.get("ssn")).isNotEqualTo("123-45-6789");
        }

        @Test
        @DisplayName("preserves non-sensitive fields")
        void preservesNonSensitiveFields() {
            Map<String, Object> auditData = Map.of(
                "action", "CREATE",
                "resourceId", "entity-456",
                "timestamp", "2026-01-01T00:00:00Z"
            );

            Map<String, Object> redacted = fieldMasker.redactAuditData(auditData);

            assertThat(redacted).isEqualTo(auditData);
        }

        @Test
        @DisplayName("handles empty audit data")
        void handlesEmptyAuditData() {
            Map<String, Object> redacted = fieldMasker.redactAuditData(Map.of());
            assertThat(redacted).isEmpty();
        }
    }

    // =========================================================================
    //  Key Management
    // =========================================================================

    @Nested
    @DisplayName("Key Management")
    class KeyManagementTests {

        @Test
        @DisplayName("validates encryption key format")
        void validatesEncryptionKeyFormat() {
            boolean valid = fieldMasker.validateKeyFormat("AES-256");
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("rejects invalid key format")
        void rejectsInvalidKeyFormat() {
            boolean valid = fieldMasker.validateKeyFormat("WEAK-ALGORITHM");
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("rotates encryption keys")
        void rotatesEncryptionKeys() {
            String plaintext = "sensitive-data-123";
            String encryptedWithOldKey = fieldMasker.encrypt(plaintext);

            fieldMasker.rotateKey();
            String encryptedWithNewKey = fieldMasker.encrypt(plaintext);

            // New key should produce different ciphertext
            assertThat(encryptedWithNewKey).isNotEqualTo(encryptedWithOldKey);

            // Should still decrypt with new key
            String decrypted = fieldMasker.decrypt(encryptedWithNewKey);
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }
}
