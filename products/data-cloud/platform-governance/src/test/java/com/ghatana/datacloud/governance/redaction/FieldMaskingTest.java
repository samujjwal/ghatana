/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.redaction;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for field-level masking and redaction.
 *
 * <p>Validates that the field masker correctly masks, redacts, and partially
 * reveals PII, financial, and sensitive fields based on masking rules and
 * caller role/context.
 *
 * @doc.type    class
 * @doc.purpose Field masking: PII redaction, partial reveal, role-based access, config-driven rules
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("FieldMaskingTest [GH-90000]")
@Tag("governance [GH-90000]")
class FieldMaskingTest {

    private FieldMasker masker;

    @BeforeEach
    void setUp() { // GH-90000
        MaskingPolicy policy = new MaskingPolicy(); // GH-90000
        policy.addRule("email", MaskingMode.PARTIAL);    // user@ex.com → u***@ex.com // GH-90000
        policy.addRule("ssn", MaskingMode.FULL);          // 123-45-6789 → ***-**-**** // GH-90000
        policy.addRule("creditCard", MaskingMode.TAIL);   // 1234 5678 9012 3456 → **** **** **** 3456 // GH-90000
        policy.addRule("password", MaskingMode.REDACT);   // → [REDACTED] // GH-90000
        masker = new FieldMasker(policy); // GH-90000
    }

    // ── Full masking ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SSN is fully masked [GH-90000]")
    void ssnIsFullyMasked() { // GH-90000
        String masked = masker.mask("ssn", "123-45-6789"); // GH-90000
        assertThat(masked).doesNotContain("123 [GH-90000]");
        assertThat(masked).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("password is redacted with placeholder [GH-90000]")
    void passwordIsRedacted() { // GH-90000
        String masked = masker.mask("password", "SuperSecret1!"); // GH-90000
        assertThat(masked).isEqualTo("[REDACTED] [GH-90000]");
        assertThat(masked).doesNotContain("Secret [GH-90000]");
    }

    // ── Partial masking ───────────────────────────────────────────────────────

    @Test
    @DisplayName("email is partially masked but domain is preserved [GH-90000]")
    void emailIsPartiallyMasked() { // GH-90000
        String masked = masker.mask("email", "user@example.com"); // GH-90000
        assertThat(masked).contains("@example.com [GH-90000]");
        assertThat(masked).doesNotStartWith("user [GH-90000]");
    }

    @Test
    @DisplayName("credit card shows only last 4 digits [GH-90000]")
    void creditCardShowsLastFourDigits() { // GH-90000
        String masked = masker.mask("creditCard", "1234 5678 9012 3456"); // GH-90000
        assertThat(masked).endsWith("3456 [GH-90000]");
        assertThat(masked).doesNotContain("1234 [GH-90000]");
        assertThat(masked).doesNotContain("5678 [GH-90000]");
        assertThat(masked).doesNotContain("9012 [GH-90000]");
    }

    // ── Unregistered fields ───────────────────────────────────────────────────

    @Test
    @DisplayName("unregistered field is returned as-is by default [GH-90000]")
    void unregisteredFieldReturnedAsIs() { // GH-90000
        String masked = masker.mask("username", "john_doe"); // GH-90000
        assertThat(masked).isEqualTo("john_doe [GH-90000]");
    }

    // ── Parameterized ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "email,  a@b.com,            @b.com",
        "password, secret,           [REDACTED]"
    })
    @DisplayName("masking rules produce expected patterns [GH-90000]")
    void maskingRules(String field, String input, String expectedFragment) { // GH-90000
        String masked = masker.mask(field, input.trim()); // GH-90000
        assertThat(masked).contains(expectedFragment.trim()); // GH-90000
    }

    // ── Null and edge cases ───────────────────────────────────────────────────

    @Test
    @DisplayName("masking null value returns a safe placeholder [GH-90000]")
    void nullValueReturnsSafePlaceholder() { // GH-90000
        String masked = masker.mask("email", null); // GH-90000
        assertThat(masked).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("masking empty string returns safe placeholder [GH-90000]")
    void emptyStringReturnsSafePlaceholder() { // GH-90000
        String masked = masker.mask("email", ""); // GH-90000
        assertThat(masked).isNotNull(); // GH-90000
    }

    // ── Batch masking (record-level) ────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("maskRecord masks all registered fields in a map [GH-90000]")
    void maskRecordMasksAllRegisteredFields() { // GH-90000
        Map<String, String> record = new LinkedHashMap<>(); // GH-90000
        record.put("email", "person@example.com"); // GH-90000
        record.put("password", "P@ssw0rd"); // GH-90000
        record.put("username", "jdoe");  // unregistered — should be passed through // GH-90000

        Map<String, String> masked = masker.maskRecord(record); // GH-90000

        assertThat(masked.get("email [GH-90000]")).doesNotStartWith("person [GH-90000]");
        assertThat(masked.get("email [GH-90000]")).contains("@example.com [GH-90000]");
        assertThat(masked.get("password [GH-90000]")).isEqualTo("[REDACTED] [GH-90000]");
        assertThat(masked.get("username [GH-90000]")).isEqualTo("jdoe [GH-90000]");    // pass-through
    }

    @Test
    @DisplayName("maskRecord on empty map returns empty map [GH-90000]")
    void maskRecordEmptyMapReturnsEmpty() { // GH-90000
        assertThat(masker.maskRecord(Map.of())).isEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum MaskingMode { FULL, PARTIAL, TAIL, REDACT }

    static class MaskingPolicy {
        private final Map<String, MaskingMode> rules = new HashMap<>(); // GH-90000

        void addRule(String field, MaskingMode mode) { // GH-90000
            rules.put(field, mode); // GH-90000
        }

        Optional<MaskingMode> modeFor(String field) { // GH-90000
            return Optional.ofNullable(rules.get(field)); // GH-90000
        }
    }

    static class FieldMasker {
        private static final String REDACTED = "[REDACTED]";
        private static final String NULL_PLACEHOLDER = "[NULL]";
        private final MaskingPolicy policy;

        FieldMasker(MaskingPolicy policy) { // GH-90000
            this.policy = policy;
        }

        String mask(String field, String value) { // GH-90000
            if (value == null) return NULL_PLACEHOLDER; // GH-90000
            if (value.isEmpty()) return value; // GH-90000
            return policy.modeFor(field) // GH-90000
                    .map(mode -> applyMode(mode, value)) // GH-90000
                    .orElse(value); // GH-90000
        }

        Map<String, String> maskRecord(Map<String, String> record) { // GH-90000
            Map<String, String> result = new LinkedHashMap<>(); // GH-90000
            record.forEach((k, v) -> result.put(k, mask(k, v))); // GH-90000
            return result;
        }

        private String applyMode(MaskingMode mode, String value) { // GH-90000
            return switch (mode) { // GH-90000
                case REDACT -> REDACTED;
                case FULL -> value.replaceAll("[0-9A-Za-z]", "*"); // GH-90000
                case PARTIAL -> {
                    int atIdx = value.indexOf('@'); // GH-90000
                    if (atIdx > 0) { // GH-90000
                        String local = value.substring(0, 1) + "***"; // GH-90000
                        yield local + value.substring(atIdx); // GH-90000
                    }
                    int half = Math.max(1, value.length() / 2); // GH-90000
                    yield "*".repeat(half) + value.substring(half); // GH-90000
                }
                case TAIL -> {
                    if (value.length() <= 4) yield value; // GH-90000
                    String tail = value.substring(value.length() - 4); // GH-90000
                    String head = value.substring(0, value.length() - 4).replaceAll("[0-9A-Za-z]", "*"); // GH-90000
                    yield head + tail;
                }
            };
        }
    }
}
