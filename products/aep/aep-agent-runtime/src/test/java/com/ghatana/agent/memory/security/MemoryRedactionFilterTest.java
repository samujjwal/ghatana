/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MemoryRedactionFilter} and {@link DefaultRedactionPatternProvider}.
 *
 * @doc.type class
 * @doc.purpose Tests for memory redaction — PII and credential masking
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("MemoryRedactionFilter – Redaction Tests")
class MemoryRedactionFilterTest {

    // =========================================================================
    // DefaultRedactionPatternProvider
    // =========================================================================

    @Nested
    @DisplayName("DefaultRedactionPatternProvider")
    class DefaultRedactionPatternProviderTests {

        @Test
        @DisplayName("instance() returns the same singleton")
        void instanceReturnsSingleton() {
            DefaultRedactionPatternProvider p1 = DefaultRedactionPatternProvider.instance();
            DefaultRedactionPatternProvider p2 = DefaultRedactionPatternProvider.instance();
            assertThat(p1).isSameAs(p2);
        }

        @Test
        @DisplayName("piiPatterns() is non-empty")
        void piiPatternsNonEmpty() {
            assertThat(DefaultRedactionPatternProvider.instance().piiPatterns()).isNotEmpty();
        }

        @Test
        @DisplayName("credentialPatterns() is non-empty")
        void credentialPatternsNonEmpty() {
            assertThat(DefaultRedactionPatternProvider.instance().credentialPatterns()).isNotEmpty();
        }

        @Test
        @DisplayName("providerName() returns 'built-in-defaults'")
        void providerNameIsBuiltIn() {
            assertThat(DefaultRedactionPatternProvider.instance().providerName())
                    .isEqualTo("built-in-defaults");
        }
    }

    // =========================================================================
    // MemoryRedactionFilter — PII redaction
    // =========================================================================

    @Nested
    @DisplayName("PII redaction")
    class PiiRedactionTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();

        @Test
        @DisplayName("redacts email addresses")
        void redactsEmail() {
            String result = filter.redact("Contact alice@example.com for info");
            assertThat(result).doesNotContain("alice@example.com");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts US phone numbers (XXX-XXX-XXXX)")
        void redactsPhoneNumbers() {
            String result = filter.redact("Call us at 555-123-4567 today");
            assertThat(result).doesNotContain("555-123-4567");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts SSN (XXX-XX-XXXX)")
        void redactsSsn() {
            String result = filter.redact("SSN: 123-45-6789");
            assertThat(result).doesNotContain("123-45-6789");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("preserves text that has no PII")
        void preservesCleanText() {
            String clean = "This is a perfectly safe message.";
            assertThat(filter.redact(clean)).isEqualTo(clean);
        }

        @Test
        @DisplayName("redacts multiple PII occurrences in a single string")
        void redactsMultiplePii() {
            String input = "Email: alice@example.com, Phone: 555-123-4567";
            String result = filter.redact(input);
            assertThat(result).doesNotContain("alice@example.com");
            assertThat(result).doesNotContain("555-123-4567");
        }
    }

    // =========================================================================
    // MemoryRedactionFilter — Credential redaction
    // =========================================================================

    @Nested
    @DisplayName("Credential redaction")
    class CredentialRedactionTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();

        @Test
        @DisplayName("redacts API key patterns")
        void redactsApiKey() {
            String result = filter.redact("api_key=super-secret-value");
            assertThat(result).doesNotContain("super-secret-value");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts Bearer token")
        void redactsBearerToken() {
            String result = filter.redact("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.abc");
            assertThat(result).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts password in key=value format")
        void redactsPassword() {
            String result = filter.redact("password=MyS3cr3tP@ss");
            assertThat(result).doesNotContain("MyS3cr3tP@ss");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("case-insensitive credential key matching")
        void caseInsensitiveCredsMatch() {
            String result = filter.redact("PASSWORD=topsecret");
            assertThat(result).doesNotContain("topsecret");
        }
    }

    // =========================================================================
    // Selective mode — PII only / Credentials only
    // =========================================================================

    @Nested
    @DisplayName("Selective redaction modes")
    class SelectiveModeTests {

        @Test
        @DisplayName("PII-only mode does NOT redact credentials")
        void piiOnlyLeavesCredentials() {
            MemoryRedactionFilter piiOnly = new MemoryRedactionFilter(true, false);
            String result = piiOnly.redact("api_key=my-api-key");
            // credential pattern should NOT be applied
            assertThat(result).contains("api_key=my-api-key");
        }

        @Test
        @DisplayName("Credential-only mode does NOT redact PII")
        void credentialOnlyLeavesPii() {
            MemoryRedactionFilter credOnly = new MemoryRedactionFilter(false, true);
            String result = credOnly.redact("alice@example.com");
            // PII pattern should NOT be applied
            assertThat(result).contains("alice@example.com");
        }

        @Test
        @DisplayName("both-disabled mode returns text unchanged")
        void bothDisabledNoRedaction() {
            MemoryRedactionFilter none = new MemoryRedactionFilter(false, false);
            String text = "alice@example.com password=secret api_key=12345";
            assertThat(none.redact(text)).isEqualTo(text);
        }
    }

    // =========================================================================
    // containsSensitiveContent()
    // =========================================================================

    @Nested
    @DisplayName("containsSensitiveContent()")
    class ContainsSensitiveContentTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();

        @Test
        @DisplayName("detects email as sensitive")
        void detectsEmail() {
            assertThat(filter.containsSensitiveContent("Send to bob@corp.io")).isTrue();
        }

        @Test
        @DisplayName("detects API key as sensitive")
        void detectsApiKey() {
            assertThat(filter.containsSensitiveContent("apikey=abc123")).isTrue();
        }

        @Test
        @DisplayName("returns false for clean text")
        void returnsFalseForCleanText() {
            assertThat(filter.containsSensitiveContent("The weather is sunny today.")).isFalse();
        }
    }

    // =========================================================================
    // Pattern counts
    // =========================================================================

    @Nested
    @DisplayName("Pattern counts")
    class PatternCountTests {

        @Test
        @DisplayName("defaultFilter has at least 3 PII patterns (email, phone, SSN)")
        void defaultFilterHasPiiPatterns() {
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();
            assertThat(filter.piiPatternCount()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("defaultFilter has at least 3 credential patterns")
        void defaultFilterHasCredentialPatterns() {
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter();
            assertThat(filter.credentialPatternCount()).isGreaterThanOrEqualTo(3);
        }
    }
}
