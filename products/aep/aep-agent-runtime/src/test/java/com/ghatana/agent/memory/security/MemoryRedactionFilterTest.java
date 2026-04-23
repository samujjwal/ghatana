/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
        void instanceReturnsSingleton() { // GH-90000
            DefaultRedactionPatternProvider p1 = DefaultRedactionPatternProvider.instance(); // GH-90000
            DefaultRedactionPatternProvider p2 = DefaultRedactionPatternProvider.instance(); // GH-90000
            assertThat(p1).isSameAs(p2); // GH-90000
        }

        @Test
        @DisplayName("piiPatterns() is non-empty")
        void piiPatternsNonEmpty() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().piiPatterns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("credentialPatterns() is non-empty")
        void credentialPatternsNonEmpty() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().credentialPatterns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("providerName() returns 'built-in-defaults'")
        void providerNameIsBuiltIn() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().providerName()) // GH-90000
                    .isEqualTo("built-in-defaults");
        }
    }

    // =========================================================================
    // MemoryRedactionFilter — PII redaction
    // =========================================================================

    @Nested
    @DisplayName("PII redaction")
    class PiiRedactionTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("redacts email addresses")
        void redactsEmail() { // GH-90000
            String result = filter.redact("Contact alice@example.com for info");
            assertThat(result).doesNotContain("alice@example.com");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts US phone numbers (XXX-XXX-XXXX)")
        void redactsPhoneNumbers() { // GH-90000
            String result = filter.redact("Call us at 555-123-4567 today");
            assertThat(result).doesNotContain("555-123-4567");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts SSN (XXX-XX-XXXX)")
        void redactsSsn() { // GH-90000
            String result = filter.redact("SSN: 123-45-6789");
            assertThat(result).doesNotContain("123-45-6789");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("preserves text that has no PII")
        void preservesCleanText() { // GH-90000
            String clean = "This is a perfectly safe message.";
            assertThat(filter.redact(clean)).isEqualTo(clean); // GH-90000
        }

        @Test
        @DisplayName("redacts multiple PII occurrences in a single string")
        void redactsMultiplePii() { // GH-90000
            String input = "Email: alice@example.com, Phone: 555-123-4567";
            String result = filter.redact(input); // GH-90000
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

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("redacts API key patterns")
        void redactsApiKey() { // GH-90000
            String result = filter.redact("api_key=super-secret-value");
            assertThat(result).doesNotContain("super-secret-value");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts Bearer token")
        void redactsBearerToken() { // GH-90000
            String result = filter.redact("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.abc");
            assertThat(result).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("redacts password in key=value format")
        void redactsPassword() { // GH-90000
            String result = filter.redact("password=MyS3cr3tP@ss");
            assertThat(result).doesNotContain("MyS3cr3tP@ss");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("case-insensitive credential key matching")
        void caseInsensitiveCredsMatch() { // GH-90000
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
        void piiOnlyLeavesCredentials() { // GH-90000
            MemoryRedactionFilter piiOnly = new MemoryRedactionFilter(true, false); // GH-90000
            String result = piiOnly.redact("api_key=my-api-key");
            // credential pattern should NOT be applied
            assertThat(result).contains("api_key=my-api-key");
        }

        @Test
        @DisplayName("Credential-only mode does NOT redact PII")
        void credentialOnlyLeavesPii() { // GH-90000
            MemoryRedactionFilter credOnly = new MemoryRedactionFilter(false, true); // GH-90000
            String result = credOnly.redact("alice@example.com");
            // PII pattern should NOT be applied
            assertThat(result).contains("alice@example.com");
        }

        @Test
        @DisplayName("both-disabled mode returns text unchanged")
        void bothDisabledNoRedaction() { // GH-90000
            MemoryRedactionFilter none = new MemoryRedactionFilter(false, false); // GH-90000
            String text = "alice@example.com password=secret api_key=12345";
            assertThat(none.redact(text)).isEqualTo(text); // GH-90000
        }
    }

    // =========================================================================
    // containsSensitiveContent() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("containsSensitiveContent()")
    class ContainsSensitiveContentTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("detects email as sensitive")
        void detectsEmail() { // GH-90000
            assertThat(filter.containsSensitiveContent("Send to bob@corp.io")).isTrue();
        }

        @Test
        @DisplayName("detects API key as sensitive")
        void detectsApiKey() { // GH-90000
            assertThat(filter.containsSensitiveContent("apikey=abc123")).isTrue();
        }

        @Test
        @DisplayName("returns false for clean text")
        void returnsFalseForCleanText() { // GH-90000
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
        void defaultFilterHasPiiPatterns() { // GH-90000
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000
            assertThat(filter.piiPatternCount()).isGreaterThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("defaultFilter has at least 3 credential patterns")
        void defaultFilterHasCredentialPatterns() { // GH-90000
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000
            assertThat(filter.credentialPatternCount()).isGreaterThanOrEqualTo(3); // GH-90000
        }
    }
}
