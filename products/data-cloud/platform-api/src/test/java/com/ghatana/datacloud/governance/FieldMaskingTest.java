/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for data field masking (PII redaction) by sensitivity level and regulation. // GH-90000
 *
 * @doc.type    class
 * @doc.purpose Tests for field-level masking and PII redaction logic
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Field Masking and PII Redaction Tests")
class FieldMaskingTest extends EventloopTestBase {

    // ── Masking model ─────────────────────────────────────────────────────────

    enum MaskLevel { NONE, PARTIAL, FULL, REDACT }

    record FieldPolicy(String fieldName, MaskLevel maskLevel, boolean isPersonalData) {} // GH-90000

    record DataRecord(String id, Map<String, String> fields) {} // GH-90000

    private FieldMasker masker;

    @BeforeEach
    void setUp() { // GH-90000
        masker = new FieldMasker(List.of( // GH-90000
                new FieldPolicy("email", MaskLevel.PARTIAL, true), // GH-90000
                new FieldPolicy("phoneNumber", MaskLevel.PARTIAL, true), // GH-90000
                new FieldPolicy("creditCard", MaskLevel.FULL, true), // GH-90000
                new FieldPolicy("ssn", MaskLevel.REDACT, true), // GH-90000
                new FieldPolicy("username", MaskLevel.NONE, false), // GH-90000
                new FieldPolicy("createdAt", MaskLevel.NONE, false) // GH-90000
        ));
    }

    // ── Individual field masking ───────────────────────────────────────────────

    @Test
    @DisplayName("email is partially masked — local-part is hidden")
    void emailIsPartiallyMasked() { // GH-90000
        String masked = masker.mask("email", "alice@example.com"); // GH-90000
        assertThat(masked).endsWith("@example.com");
        assertThat(masked).doesNotContain("alice");
    }

    @Test
    @DisplayName("phoneNumber is partially masked — only last 4 digits visible")
    void phoneNumberIsPartiallyMasked() { // GH-90000
        String masked = masker.mask("phoneNumber", "+12025551234"); // GH-90000
        assertThat(masked).endsWith("1234");
        assertThat(masked).doesNotContain("+12025551");
    }

    @Test
    @DisplayName("credit card is fully masked — all digits replaced")
    void creditCardIsFullyMasked() { // GH-90000
        String masked = masker.mask("creditCard", "4111111111111111"); // GH-90000
        assertThat(masked).doesNotContain("4111");
        assertThat(masked).matches("[*]+");
    }

    @Test
    @DisplayName("SSN is redacted — returns a fixed redaction token")
    void ssnIsRedacted() { // GH-90000
        String masked = masker.mask("ssn", "123-45-6789"); // GH-90000
        assertThat(masked).isEqualTo("[REDACTED]");
        assertThat(masked).doesNotContain("123");
    }

    @Test
    @DisplayName("non-PII fields pass through unmodified")
    void nonPiiFieldsPassThrough() { // GH-90000
        String username = masker.mask("username", "alice_wonder"); // GH-90000
        String createdAt = masker.mask("createdAt", "2026-01-01T00:00:00Z"); // GH-90000

        assertThat(username).isEqualTo("alice_wonder");
        assertThat(createdAt).isEqualTo("2026-01-01T00:00:00Z");
    }

    // ── Record-level masking ──────────────────────────────────────────────────

    @Test
    @DisplayName("maskRecord masks all PII fields in a data record")
    void maskRecordMasksAllPiiFields() { // GH-90000
        DataRecord record = new DataRecord("record-001", new HashMap<>(Map.of( // GH-90000
                "email", "bob@example.com",
                "phoneNumber", "+441234567890",
                "creditCard", "5500005555555559",
                "ssn", "987-65-4321",
                "username", "bob_builder",
                "createdAt", "2026-03-01T10:00:00Z"
        )));

        DataRecord masked = masker.maskRecord(record); // GH-90000

        assertThat(masked.fields().get("email")).doesNotContain("bob");
        assertThat(masked.fields().get("phoneNumber")).endsWith("7890");
        assertThat(masked.fields().get("creditCard")).matches("[*]+");
        assertThat(masked.fields().get("ssn")).isEqualTo("[REDACTED]");
        assertThat(masked.fields().get("username")).isEqualTo("bob_builder");
        assertThat(masked.fields().get("createdAt")).isEqualTo("2026-03-01T10:00:00Z");
    }

    @Test
    @DisplayName("maskRecord does not mutate the original record")
    void maskRecordDoesNotMutateOriginal() { // GH-90000
        DataRecord original = new DataRecord("r-002", new HashMap<>(Map.of( // GH-90000
                "email", "carol@example.com",
                "ssn", "111-22-3333"
        )));

        masker.maskRecord(original); // GH-90000

        assertThat(original.fields().get("email")).isEqualTo("carol@example.com");
        assertThat(original.fields().get("ssn")).isEqualTo("111-22-3333");
    }

    @Test
    @DisplayName("unknown fields pass through unmodified")
    void unknownFieldsPassThrough() { // GH-90000
        String masked = masker.mask("unknownField", "some-value"); // GH-90000
        assertThat(masked).isEqualTo("some-value");
    }

    // ── Field masker implementation (inner, for test) ───────────────────────── // GH-90000

    static class FieldMasker {
        private final Map<String, FieldPolicy> policies = new HashMap<>(); // GH-90000

        FieldMasker(List<FieldPolicy> policies) { // GH-90000
            for (FieldPolicy p : policies) { // GH-90000
                this.policies.put(p.fieldName(), p); // GH-90000
            }
        }

        String mask(String fieldName, String value) { // GH-90000
            FieldPolicy policy = policies.get(fieldName); // GH-90000
            if (policy == null) return value; // GH-90000
            return switch (policy.maskLevel()) { // GH-90000
                case NONE -> value;
                case PARTIAL -> {
                    if (value.contains("@")) {
                        // Email: hide local-part
                        int atIdx = value.indexOf('@'); // GH-90000
                        yield "***" + value.substring(atIdx); // GH-90000
                    } else if (value.matches("[+0-9 ()-]+")) {
                        // Phone: show last 4
                        String digits = value.replaceAll("[^0-9]", ""); // GH-90000
                        String last4 = digits.length() >= 4 // GH-90000
                                ? digits.substring(digits.length() - 4) : digits; // GH-90000
                        yield "****" + last4;
                    } else {
                        yield "***";
                    }
                }
                case FULL -> "*".repeat(Math.max(value.length(), 8)); // GH-90000
                case REDACT -> "[REDACTED]";
            };
        }

        DataRecord maskRecord(DataRecord record) { // GH-90000
            Map<String, String> maskedFields = new HashMap<>(); // GH-90000
            for (Map.Entry<String, String> entry : record.fields().entrySet()) { // GH-90000
                maskedFields.put(entry.getKey(), mask(entry.getKey(), entry.getValue())); // GH-90000
            }
            return new DataRecord(record.id(), maskedFields); // GH-90000
        }
    }
}
