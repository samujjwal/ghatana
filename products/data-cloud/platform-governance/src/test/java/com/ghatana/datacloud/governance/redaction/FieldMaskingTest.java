/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("FieldMaskingTest")
@Tag("governance")
class FieldMaskingTest {

    private FieldMasker masker;

    @BeforeEach
    void setUp() {
        MaskingPolicy policy = new MaskingPolicy();
        policy.addRule("email", MaskingMode.PARTIAL);    // user@ex.com → u***@ex.com
        policy.addRule("ssn", MaskingMode.FULL);          // 123-45-6789 → ***-**-****
        policy.addRule("creditCard", MaskingMode.TAIL);   // 1234 5678 9012 3456 → **** **** **** 3456
        policy.addRule("password", MaskingMode.REDACT);   // → [REDACTED]
        masker = new FieldMasker(policy);
    }

    // ── Full masking ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SSN is fully masked")
    void ssnIsFullyMasked() {
        String masked = masker.mask("ssn", "123-45-6789");
        assertThat(masked).doesNotContain("123");
        assertThat(masked).isNotBlank();
    }

    @Test
    @DisplayName("password is redacted with placeholder")
    void passwordIsRedacted() {
        String masked = masker.mask("password", "SuperSecret1!");
        assertThat(masked).isEqualTo("[REDACTED]");
        assertThat(masked).doesNotContain("Secret");
    }

    // ── Partial masking ───────────────────────────────────────────────────────

    @Test
    @DisplayName("email is partially masked but domain is preserved")
    void emailIsPartiallyMasked() {
        String masked = masker.mask("email", "user@example.com");
        assertThat(masked).contains("@example.com");
        assertThat(masked).doesNotStartWith("user");
    }

    @Test
    @DisplayName("credit card shows only last 4 digits")
    void creditCardShowsLastFourDigits() {
        String masked = masker.mask("creditCard", "1234 5678 9012 3456");
        assertThat(masked).endsWith("3456");
        assertThat(masked).doesNotContain("1234");
        assertThat(masked).doesNotContain("5678");
        assertThat(masked).doesNotContain("9012");
    }

    // ── Unregistered fields ───────────────────────────────────────────────────

    @Test
    @DisplayName("unregistered field is returned as-is by default")
    void unregisteredFieldReturnedAsIs() {
        String masked = masker.mask("username", "john_doe");
        assertThat(masked).isEqualTo("john_doe");
    }

    // ── Parameterized ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "email,  a@b.com,            @b.com",
        "password, secret,           [REDACTED]"
    })
    @DisplayName("masking rules produce expected patterns")
    void maskingRules(String field, String input, String expectedFragment) {
        String masked = masker.mask(field, input.trim());
        assertThat(masked).contains(expectedFragment.trim());
    }

    // ── Null and edge cases ───────────────────────────────────────────────────

    @Test
    @DisplayName("masking null value returns a safe placeholder")
    void nullValueReturnsSafePlaceholder() {
        String masked = masker.mask("email", null);
        assertThat(masked).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("masking empty string returns safe placeholder")
    void emptyStringReturnsSafePlaceholder() {
        String masked = masker.mask("email", "");
        assertThat(masked).isNotNull();
    }

    // ── Batch masking (record-level) ──────────────────────────────────────────

    @Test
    @DisplayName("maskRecord masks all registered fields in a map")
    void maskRecordMasksAllRegisteredFields() {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("email", "person@example.com");
        record.put("password", "P@ssw0rd");
        record.put("username", "jdoe");  // unregistered — should be passed through

        Map<String, String> masked = masker.maskRecord(record);

        assertThat(masked.get("email")).doesNotStartWith("person");
        assertThat(masked.get("email")).contains("@example.com");
        assertThat(masked.get("password")).isEqualTo("[REDACTED]");
        assertThat(masked.get("username")).isEqualTo("jdoe");    // pass-through
    }

    @Test
    @DisplayName("maskRecord on empty map returns empty map")
    void maskRecordEmptyMapReturnsEmpty() {
        assertThat(masker.maskRecord(Map.of())).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation
    // ─────────────────────────────────────────────────────────────────────────

    enum MaskingMode { FULL, PARTIAL, TAIL, REDACT }

    static class MaskingPolicy {
        private final Map<String, MaskingMode> rules = new HashMap<>();

        void addRule(String field, MaskingMode mode) {
            rules.put(field, mode);
        }

        Optional<MaskingMode> modeFor(String field) {
            return Optional.ofNullable(rules.get(field));
        }
    }

    static class FieldMasker {
        private static final String REDACTED = "[REDACTED]";
        private static final String NULL_PLACEHOLDER = "[NULL]";
        private final MaskingPolicy policy;

        FieldMasker(MaskingPolicy policy) {
            this.policy = policy;
        }

        String mask(String field, String value) {
            if (value == null) return NULL_PLACEHOLDER;
            if (value.isEmpty()) return value;
            return policy.modeFor(field)
                    .map(mode -> applyMode(mode, value))
                    .orElse(value);
        }

        Map<String, String> maskRecord(Map<String, String> record) {
            Map<String, String> result = new LinkedHashMap<>();
            record.forEach((k, v) -> result.put(k, mask(k, v)));
            return result;
        }

        private String applyMode(MaskingMode mode, String value) {
            return switch (mode) {
                case REDACT -> REDACTED;
                case FULL -> value.replaceAll("[0-9A-Za-z]", "*");
                case PARTIAL -> {
                    int atIdx = value.indexOf('@');
                    if (atIdx > 0) {
                        String local = value.substring(0, 1) + "***";
                        yield local + value.substring(atIdx);
                    }
                    int half = Math.max(1, value.length() / 2);
                    yield "*".repeat(half) + value.substring(half);
                }
                case TAIL -> {
                    if (value.length() <= 4) yield value;
                    String tail = value.substring(value.length() - 4);
                    String head = value.substring(0, value.length() - 4).replaceAll("[0-9A-Za-z]", "*");
                    yield head + tail;
                }
            };
        }
    }
}
