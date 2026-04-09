/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * Tests for data field masking (PII redaction) by sensitivity level and regulation.
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

    record FieldPolicy(String fieldName, MaskLevel maskLevel, boolean isPersonalData) {}

    record DataRecord(String id, Map<String, String> fields) {}

    private FieldMasker masker;

    @BeforeEach
    void setUp() {
        masker = new FieldMasker(List.of(
                new FieldPolicy("email", MaskLevel.PARTIAL, true),
                new FieldPolicy("phoneNumber", MaskLevel.PARTIAL, true),
                new FieldPolicy("creditCard", MaskLevel.FULL, true),
                new FieldPolicy("ssn", MaskLevel.REDACT, true),
                new FieldPolicy("username", MaskLevel.NONE, false),
                new FieldPolicy("createdAt", MaskLevel.NONE, false)
        ));
    }

    // ── Individual field masking ───────────────────────────────────────────────

    @Test
    @DisplayName("email is partially masked — local-part is hidden")
    void emailIsPartiallyMasked() {
        String masked = masker.mask("email", "alice@example.com");
        assertThat(masked).endsWith("@example.com");
        assertThat(masked).doesNotContain("alice");
    }

    @Test
    @DisplayName("phoneNumber is partially masked — only last 4 digits visible")
    void phoneNumberIsPartiallyMasked() {
        String masked = masker.mask("phoneNumber", "+12025551234");
        assertThat(masked).endsWith("1234");
        assertThat(masked).doesNotContain("+12025551");
    }

    @Test
    @DisplayName("credit card is fully masked — all digits replaced")
    void creditCardIsFullyMasked() {
        String masked = masker.mask("creditCard", "4111111111111111");
        assertThat(masked).doesNotContain("4111");
        assertThat(masked).matches("[*]+");
    }

    @Test
    @DisplayName("SSN is redacted — returns a fixed redaction token")
    void ssnIsRedacted() {
        String masked = masker.mask("ssn", "123-45-6789");
        assertThat(masked).isEqualTo("[REDACTED]");
        assertThat(masked).doesNotContain("123");
    }

    @Test
    @DisplayName("non-PII fields pass through unmodified")
    void nonPiiFieldsPassThrough() {
        String username = masker.mask("username", "alice_wonder");
        String createdAt = masker.mask("createdAt", "2026-01-01T00:00:00Z");

        assertThat(username).isEqualTo("alice_wonder");
        assertThat(createdAt).isEqualTo("2026-01-01T00:00:00Z");
    }

    // ── Record-level masking ──────────────────────────────────────────────────

    @Test
    @DisplayName("maskRecord masks all PII fields in a data record")
    void maskRecordMasksAllPiiFields() {
        DataRecord record = new DataRecord("record-001", new HashMap<>(Map.of(
                "email", "bob@example.com",
                "phoneNumber", "+441234567890",
                "creditCard", "5500005555555559",
                "ssn", "987-65-4321",
                "username", "bob_builder",
                "createdAt", "2026-03-01T10:00:00Z"
        )));

        DataRecord masked = masker.maskRecord(record);

        assertThat(masked.fields().get("email")).doesNotContain("bob");
        assertThat(masked.fields().get("phoneNumber")).endsWith("7890");
        assertThat(masked.fields().get("creditCard")).matches("[*]+");
        assertThat(masked.fields().get("ssn")).isEqualTo("[REDACTED]");
        assertThat(masked.fields().get("username")).isEqualTo("bob_builder");
        assertThat(masked.fields().get("createdAt")).isEqualTo("2026-03-01T10:00:00Z");
    }

    @Test
    @DisplayName("maskRecord does not mutate the original record")
    void maskRecordDoesNotMutateOriginal() {
        DataRecord original = new DataRecord("r-002", new HashMap<>(Map.of(
                "email", "carol@example.com",
                "ssn", "111-22-3333"
        )));

        masker.maskRecord(original);

        assertThat(original.fields().get("email")).isEqualTo("carol@example.com");
        assertThat(original.fields().get("ssn")).isEqualTo("111-22-3333");
    }

    @Test
    @DisplayName("unknown fields pass through unmodified")
    void unknownFieldsPassThrough() {
        String masked = masker.mask("unknownField", "some-value");
        assertThat(masked).isEqualTo("some-value");
    }

    // ── Field masker implementation (inner, for test) ─────────────────────────

    static class FieldMasker {
        private final Map<String, FieldPolicy> policies = new HashMap<>();

        FieldMasker(List<FieldPolicy> policies) {
            for (FieldPolicy p : policies) {
                this.policies.put(p.fieldName(), p);
            }
        }

        String mask(String fieldName, String value) {
            FieldPolicy policy = policies.get(fieldName);
            if (policy == null) return value;
            return switch (policy.maskLevel()) {
                case NONE -> value;
                case PARTIAL -> {
                    if (value.contains("@")) {
                        // Email: hide local-part
                        int atIdx = value.indexOf('@');
                        yield "***" + value.substring(atIdx);
                    } else if (value.matches("[+0-9 ()-]+")) {
                        // Phone: show last 4
                        String digits = value.replaceAll("[^0-9]", "");
                        String last4 = digits.length() >= 4
                                ? digits.substring(digits.length() - 4) : digits;
                        yield "****" + last4;
                    } else {
                        yield "***";
                    }
                }
                case FULL -> "*".repeat(Math.max(value.length(), 8));
                case REDACT -> "[REDACTED]";
            };
        }

        DataRecord maskRecord(DataRecord record) {
            Map<String, String> maskedFields = new HashMap<>();
            for (Map.Entry<String, String> entry : record.fields().entrySet()) {
                maskedFields.put(entry.getKey(), mask(entry.getKey(), entry.getValue()));
            }
            return new DataRecord(record.id(), maskedFields);
        }
    }
}
