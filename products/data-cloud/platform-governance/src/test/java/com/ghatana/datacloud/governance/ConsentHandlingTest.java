/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.datacloud.governance.redaction.OptimizedFieldMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.ghatana.datacloud.governance.redaction.OptimizedFieldMasker.MaskingMode.FULL;
import static com.ghatana.datacloud.governance.redaction.OptimizedFieldMasker.MaskingMode.PARTIAL;
import static com.ghatana.datacloud.governance.redaction.OptimizedFieldMasker.MaskingMode.TAIL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consent handling tests for data export privacy controls.
 *
 * @doc.type class
 * @doc.purpose Verify consent-gated export masking behavior is fail-closed
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Consent Handling")
@Tag("governance")
class ConsentHandlingTest {

    private static OptimizedFieldMasker newMasker() {
        OptimizedFieldMasker.MaskingPolicy policy = new OptimizedFieldMasker.MaskingPolicy();
        policy.addRule("email", PARTIAL);
        policy.addRule("ssn", FULL);
        policy.addRule("creditCard", TAIL);
        return new OptimizedFieldMasker(policy, 128, true);
    }

    @Test
    @DisplayName("field with explicit consent is exported using masking policy")
    void fieldWithConsentUsesMaskingPolicy() {
        OptimizedFieldMasker masker = newMasker();

        Map<String, String> masked = masker.maskBatchWithConsent(
                Map.of("email", "alice@example.com", "creditCard", "4111111111111111"),
                Map.of("email", true, "creditCard", true));

        assertThat(masked.get("email")).contains("@example.com").doesNotStartWith("alice");
        assertThat(masked.get("creditCard")).endsWith("1111").doesNotStartWith("4111");
    }

    @Test
    @DisplayName("field without consent is redacted even when no masking rule exists")
    void fieldWithoutConsentIsRedacted() {
        OptimizedFieldMasker masker = newMasker();

        Map<String, String> masked = masker.maskBatchWithConsent(
                Map.of("displayName", "Alice Johnson"),
                Map.of("displayName", false));

        assertThat(masked.get("displayName")).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("missing consent defaults to fail-closed redaction")
    void missingConsentDefaultsToRedaction() {
        OptimizedFieldMasker masker = newMasker();

        Map<String, String> masked = masker.maskBatchWithConsent(
                Map.of("ssn", "123-45-6789"),
                Map.of());

        assertThat(masked.get("ssn")).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("mixed consent batch prevents leakage across denied fields")
    void mixedConsentBatchPreventsLeakage() {
        OptimizedFieldMasker masker = newMasker();

        Map<String, String> masked = masker.maskBatchWithConsent(
                Map.of(
                        "email", "customer@bank.com",
                        "ssn", "987-65-4321",
                        "creditCard", "5555555555554444"),
                Map.of(
                        "email", true,
                        "ssn", false,
                        "creditCard", true));

        assertThat(masked.get("email")).contains("@bank.com").doesNotStartWith("customer");
        assertThat(masked.get("ssn")).isEqualTo("[REDACTED]");
        assertThat(masked.get("creditCard")).endsWith("4444").doesNotStartWith("5555");
    }
}
