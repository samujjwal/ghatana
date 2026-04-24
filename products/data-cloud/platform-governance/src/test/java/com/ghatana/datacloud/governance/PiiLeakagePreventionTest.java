/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.datacloud.governance.redaction.OptimizedFieldMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PII leakage prevention tests for Data Cloud export and data-sharing flows.
 *
 * <p>Verifies that all sensitive fields in data export payloads are masked before
 * reaching consumers. Tests cover individual PII field patterns (SSN, email, credit
 * card, phone, API key, password, date of birth) and batch-export scenarios with
 * mixed sensitive and non-sensitive data.</p>
 *
 * <p>These tests exercise the governance contract from a consumer perspective:
 * after masking, no raw PII must be retrievable from the output even if an
 * attacker inspects every field value in the exported dataset.</p>
 *
 * @doc.type    class
 * @doc.purpose PII leakage prevention contract tests for Data Cloud export flows
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("PII Leakage Prevention")
@Tag("governance")
class PiiLeakagePreventionTest {

    private OptimizedFieldMasker masker;

    @BeforeEach
    void setUp() { // GH-90000
        OptimizedFieldMasker.MaskingPolicy policy = new OptimizedFieldMasker.MaskingPolicy();
        // --- Regulatory PII fields ---
        policy.addRule("ssn",           OptimizedFieldMasker.MaskingMode.FULL);     // Social Security Number
        policy.addRule("sin",           OptimizedFieldMasker.MaskingMode.FULL);     // Social Insurance Number (CA)
        policy.addRule("taxId",         OptimizedFieldMasker.MaskingMode.FULL);     // Tax Identification Number
        policy.addRule("dob",           OptimizedFieldMasker.MaskingMode.REDACT);   // Date of Birth (exact value sensitive)
        policy.addRule("dateOfBirth",   OptimizedFieldMasker.MaskingMode.REDACT);

        // --- Contact / identity fields ---
        policy.addRule("email",         OptimizedFieldMasker.MaskingMode.PARTIAL);  // Local-part masked, domain kept
        policy.addRule("phone",         OptimizedFieldMasker.MaskingMode.TAIL);     // Last 4 digits kept
        policy.addRule("mobilePhone",   OptimizedFieldMasker.MaskingMode.TAIL);

        // --- Financial fields ---
        policy.addRule("creditCard",    OptimizedFieldMasker.MaskingMode.TAIL);     // Last 4 digits kept (PCI)
        policy.addRule("cardNumber",    OptimizedFieldMasker.MaskingMode.TAIL);
        policy.addRule("bankAccount",   OptimizedFieldMasker.MaskingMode.FULL);
        policy.addRule("routingNumber", OptimizedFieldMasker.MaskingMode.FULL);

        // --- Credential / secret fields ---
        policy.addRule("password",      OptimizedFieldMasker.MaskingMode.REDACT);
        policy.addRule("apiKey",        OptimizedFieldMasker.MaskingMode.REDACT);
        policy.addRule("secretKey",     OptimizedFieldMasker.MaskingMode.REDACT);
        policy.addRule("accessToken",   OptimizedFieldMasker.MaskingMode.REDACT);

        masker = new OptimizedFieldMasker(policy, 256, true); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        masker.clearCache();
    }

    // ── Regulatory PII fields ──────────────────────────────────────────────────

    @Nested
    @DisplayName("regulatory PII fields")
    class RegulatoryPiiFields {

        @Test
        @DisplayName("SSN is fully masked: no digit groups from original appear in output")
        void ssnIsFullyMasked() { // GH-90000
            String masked = masker.mask("ssn", "123-45-6789"); // GH-90000
            assertThat(masked).doesNotContain("123").doesNotContain("6789").isNotBlank();
        }

        @Test
        @DisplayName("Canadian SIN is fully masked")
        void sinIsFullyMasked() { // GH-90000
            String masked = masker.mask("sin", "123 456 789"); // GH-90000
            assertThat(masked).doesNotContain("123").doesNotContain("789");
        }

        @Test
        @DisplayName("Tax ID is fully masked")
        void taxIdIsFullyMasked() { // GH-90000
            String masked = masker.mask("taxId", "98-7654321"); // GH-90000
            assertThat(masked).doesNotContain("987654321").doesNotContain("98-7654321");
        }

        @Test
        @DisplayName("date of birth is redacted with placeholder sentinel")
        void dateOfBirthIsRedacted() { // GH-90000
            String masked = masker.mask("dob", "1985-07-04"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
            assertThat(masked).doesNotContain("1985").doesNotContain("07-04");
        }

        @Test
        @DisplayName("full dateOfBirth field name is also redacted")
        void dateOfBirthFieldAlsoRedacted() { // GH-90000
            String masked = masker.mask("dateOfBirth", "04/07/1985"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
        }
    }

    // ── Contact and Identity Fields ────────────────────────────────────────────

    @Nested
    @DisplayName("contact and identity fields")
    class ContactAndIdentityFields {

        @Test
        @DisplayName("email is partially masked: local-part hidden, domain preserved for support routing")
        void emailIsPartiallyMasked() { // GH-90000
            String masked = masker.mask("email", "alice.smith@example.com"); // GH-90000
            // Domain must be kept for routing; local-part must NOT be present
            assertThat(masked).contains("@example.com");
            assertThat(masked).doesNotStartWith("alice");
            assertThat(masked).doesNotContain("smith");
        }

        @Test
        @DisplayName("phone number shows only last four digits")
        void phoneNumberShowsLastFourDigits() { // GH-90000
            String masked = masker.mask("phone", "+1 (800) 555-1234"); // GH-90000
            assertThat(masked).endsWith("1234");
            assertThat(masked).doesNotContain("800").doesNotContain("555");
        }

        @Test
        @DisplayName("mobile phone is masked like landline phone")
        void mobilePhoneShowsLastFourDigits() { // GH-90000
            String masked = masker.mask("mobilePhone", "6045559876"); // GH-90000
            assertThat(masked).endsWith("9876");
            assertThat(masked).doesNotContain("604").doesNotContain("5559");
        }
    }

    // ── Financial Fields ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("financial fields")
    class FinancialFields {

        @Test
        @DisplayName("credit card number shows only last four digits (PCI DSS requirement)")
        void creditCardShowsLastFourDigits() { // GH-90000
            String masked = masker.mask("creditCard", "4111 1111 1111 4321"); // GH-90000
            assertThat(masked).endsWith("4321");
            assertThat(masked).doesNotContain("4111");
        }

        @Test
        @DisplayName("cardNumber field is treated identically to creditCard")
        void cardNumberFieldMaskedLikeCreditCard() { // GH-90000
            String masked = masker.mask("cardNumber", "5500000000000004"); // GH-90000
            assertThat(masked).endsWith("0004");
            assertThat(masked).doesNotContain("5500");
        }

        @Test
        @DisplayName("bank account number is fully masked")
        void bankAccountFullyMasked() { // GH-90000
            String masked = masker.mask("bankAccount", "00123456789"); // GH-90000
            assertThat(masked).doesNotContain("0012").doesNotContain("6789");
        }

        @Test
        @DisplayName("routing number is fully masked")
        void routingNumberFullyMasked() { // GH-90000
            String masked = masker.mask("routingNumber", "021000021"); // GH-90000
            assertThat(masked).doesNotContain("021000021");
        }
    }

    // ── Credential and Secret Fields ───────────────────────────────────────────

    @Nested
    @DisplayName("credential and secret fields")
    class CredentialAndSecretFields {

        @Test
        @DisplayName("password is replaced with [REDACTED] sentinel")
        void passwordIsRedacted() { // GH-90000
            String masked = masker.mask("password", "Sup3rS3cr3t!"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
            assertThat(masked).doesNotContain("Sup3rS3cr3t");
        }

        @Test
        @DisplayName("API key is replaced with [REDACTED] sentinel")
        void apiKeyIsRedacted() { // GH-90000
            String masked = masker.mask("apiKey", "sk-prod-abc123xyzSecret"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
            assertThat(masked).doesNotContain("abc123");
        }

        @Test
        @DisplayName("secret key is replaced with [REDACTED] sentinel")
        void secretKeyIsRedacted() { // GH-90000
            String masked = masker.mask("secretKey", "AKIAIOSFODNN7EXAMPLE"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
        }

        @Test
        @DisplayName("access token is replaced with [REDACTED] sentinel")
        void accessTokenIsRedacted() { // GH-90000
            String masked = masker.mask("accessToken", "Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig"); // GH-90000
            assertThat(masked).isEqualTo("[REDACTED]");
            assertThat(masked).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
        }
    }

    // ── Batch Export PII Leakage Prevention ───────────────────────────────────

    @Nested
    @DisplayName("batch export PII leakage prevention")
    class BatchExportPiiLeakagePrevention {

        @Test
        @DisplayName("all PII fields in a record are masked in a single batch pass")
        void allPiiFieldsInRecordAreMaskedInBatch() { // GH-90000
            Map<String, String> exportRecord = new HashMap<>();
            exportRecord.put("ssn",         "999-00-1234");
            exportRecord.put("email",        "bob.jones@corp.internal");
            exportRecord.put("creditCard",   "3782 822463 10005");
            exportRecord.put("phone",        "1-800-555-9988");
            exportRecord.put("password",     "hunter2");
            exportRecord.put("apiKey",       "ghp_RealSecretToken12345");
            exportRecord.put("bankAccount",  "000123456789");

            // Non-PII fields must pass through without modification
            exportRecord.put("accountId",    "ACC-42");
            exportRecord.put("planType",     "enterprise");
            exportRecord.put("createdAt",    "2026-01-15T08:30:00Z");

            Map<String, String> masked = masker.maskBatch(exportRecord); // GH-90000

            // PII fields must not contain any raw sensitive values
            assertThat(masked.get("ssn")).doesNotContain("999").doesNotContain("1234");
            assertThat(masked.get("email")).contains("@corp.internal").doesNotContain("bob");
            assertThat(masked.get("creditCard")).endsWith("0005").doesNotContain("3782");
            assertThat(masked.get("phone")).endsWith("9988").doesNotContain("800");
            assertThat(masked.get("password")).isEqualTo("[REDACTED]");
            assertThat(masked.get("apiKey")).isEqualTo("[REDACTED]");
            assertThat(masked.get("bankAccount")).doesNotContain("000123456789");

            // Non-PII fields must be untouched
            assertThat(masked.get("accountId")).isEqualTo("ACC-42");
            assertThat(masked.get("planType")).isEqualTo("enterprise");
            assertThat(masked.get("createdAt")).isEqualTo("2026-01-15T08:30:00Z");
        }

        @Test
        @DisplayName("null and empty PII field values do not cause leakage or exceptions")
        void nullAndEmptyPiiValuesHandledSafely() { // GH-90000
            Map<String, String> exportRecord = new HashMap<>();
            exportRecord.put("ssn",      null);
            exportRecord.put("email",    "");
            exportRecord.put("password", "secret");

            Map<String, String> masked = masker.maskBatch(exportRecord); // GH-90000

            // Null and empty must not surface unmasked sensitive data
            // (exact sentinel values depend on implementation; key assertion is no raw data)
            String maskedSsn = masked.get("ssn");
            assertThat(maskedSsn).satisfiesAnyOf(
                    v -> assertThat(v).isNull(),
                    v -> assertThat(v).isIn("[NULL]", "[EMPTY]", "[REDACTED]", "")
            );

            String maskedEmail = masked.get("email");
            assertThat(maskedEmail).satisfiesAnyOf(
                    v -> assertThat(v).isEmpty(),
                    v -> assertThat(v).isIn("[EMPTY]", "[REDACTED]")
            );

            // Non-null PII is still masked
            assertThat(masked.get("password")).isEqualTo("[REDACTED]");
        }

        @Test
        @DisplayName("large batch of records has no PII leakage across any entry")
        void largeBatchHasNoPiiLeakage() { // GH-90000
            // Simulates an export job with many customer records. Iterate over all
            // and assert no record exposes raw SSN or credit card data.
            for (int i = 0; i < 200; i++) { // GH-90000
                Map<String, String> record = new HashMap<>();
                record.put("ssn",        String.format("%03d-%02d-%04d", i % 1000, i % 100, i));
                record.put("creditCard", String.format("4111111111%06d", i));
                record.put("name",       "Customer " + i); // non-PII: must pass through

                Map<String, String> masked = masker.maskBatch(record); // GH-90000

                // SSN must be fully obscured
                String rawSsn = record.get("ssn");
                assertThat(masked.get("ssn"))
                        .as("Record %d SSN must not be raw", i)
                        .isNotEqualTo(rawSsn);

                // Credit card must show only last 4 digits
                String rawCard = record.get("creditCard");
                String maskedCard = masked.get("creditCard");
                assertThat(maskedCard)
                        .as("Record %d credit card must not be raw", i)
                        .isNotEqualTo(rawCard)
                        .doesNotContain("4111111111");

                // Non-PII customer name must be unchanged
                assertThat(masked.get("name"))
                        .as("Record %d name must pass through unmodified", i)
                        .isEqualTo("Customer " + i);
            }
        }
    }
}
