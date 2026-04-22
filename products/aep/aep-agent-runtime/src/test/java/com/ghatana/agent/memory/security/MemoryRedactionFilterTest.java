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
@DisplayName("MemoryRedactionFilter – Redaction Tests [GH-90000]")
class MemoryRedactionFilterTest {

    // =========================================================================
    // DefaultRedactionPatternProvider
    // =========================================================================

    @Nested
    @DisplayName("DefaultRedactionPatternProvider [GH-90000]")
    class DefaultRedactionPatternProviderTests {

        @Test
        @DisplayName("instance() returns the same singleton [GH-90000]")
        void instanceReturnsSingleton() { // GH-90000
            DefaultRedactionPatternProvider p1 = DefaultRedactionPatternProvider.instance(); // GH-90000
            DefaultRedactionPatternProvider p2 = DefaultRedactionPatternProvider.instance(); // GH-90000
            assertThat(p1).isSameAs(p2); // GH-90000
        }

        @Test
        @DisplayName("piiPatterns() is non-empty [GH-90000]")
        void piiPatternsNonEmpty() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().piiPatterns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("credentialPatterns() is non-empty [GH-90000]")
        void credentialPatternsNonEmpty() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().credentialPatterns()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("providerName() returns 'built-in-defaults' [GH-90000]")
        void providerNameIsBuiltIn() { // GH-90000
            assertThat(DefaultRedactionPatternProvider.instance().providerName()) // GH-90000
                    .isEqualTo("built-in-defaults [GH-90000]");
        }
    }

    // =========================================================================
    // MemoryRedactionFilter — PII redaction
    // =========================================================================

    @Nested
    @DisplayName("PII redaction [GH-90000]")
    class PiiRedactionTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("redacts email addresses [GH-90000]")
        void redactsEmail() { // GH-90000
            String result = filter.redact("Contact alice@example.com for info [GH-90000]");
            assertThat(result).doesNotContain("alice@example.com [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("redacts US phone numbers (XXX-XXX-XXXX) [GH-90000]")
        void redactsPhoneNumbers() { // GH-90000
            String result = filter.redact("Call us at 555-123-4567 today [GH-90000]");
            assertThat(result).doesNotContain("555-123-4567 [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("redacts SSN (XXX-XX-XXXX) [GH-90000]")
        void redactsSsn() { // GH-90000
            String result = filter.redact("SSN: 123-45-6789 [GH-90000]");
            assertThat(result).doesNotContain("123-45-6789 [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("preserves text that has no PII [GH-90000]")
        void preservesCleanText() { // GH-90000
            String clean = "This is a perfectly safe message.";
            assertThat(filter.redact(clean)).isEqualTo(clean); // GH-90000
        }

        @Test
        @DisplayName("redacts multiple PII occurrences in a single string [GH-90000]")
        void redactsMultiplePii() { // GH-90000
            String input = "Email: alice@example.com, Phone: 555-123-4567";
            String result = filter.redact(input); // GH-90000
            assertThat(result).doesNotContain("alice@example.com [GH-90000]");
            assertThat(result).doesNotContain("555-123-4567 [GH-90000]");
        }
    }

    // =========================================================================
    // MemoryRedactionFilter — Credential redaction
    // =========================================================================

    @Nested
    @DisplayName("Credential redaction [GH-90000]")
    class CredentialRedactionTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("redacts API key patterns [GH-90000]")
        void redactsApiKey() { // GH-90000
            String result = filter.redact("api_key=super-secret-value [GH-90000]");
            assertThat(result).doesNotContain("super-secret-value [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("redacts Bearer token [GH-90000]")
        void redactsBearerToken() { // GH-90000
            String result = filter.redact("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.abc [GH-90000]");
            assertThat(result).doesNotContain("eyJhbGciOiJSUzI1NiJ9 [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("redacts password in key=value format [GH-90000]")
        void redactsPassword() { // GH-90000
            String result = filter.redact("password=MyS3cr3tP@ss [GH-90000]");
            assertThat(result).doesNotContain("MyS3cr3tP@ss [GH-90000]");
            assertThat(result).contains("[REDACTED] [GH-90000]");
        }

        @Test
        @DisplayName("case-insensitive credential key matching [GH-90000]")
        void caseInsensitiveCredsMatch() { // GH-90000
            String result = filter.redact("PASSWORD=topsecret [GH-90000]");
            assertThat(result).doesNotContain("topsecret [GH-90000]");
        }
    }

    // =========================================================================
    // Selective mode — PII only / Credentials only
    // =========================================================================

    @Nested
    @DisplayName("Selective redaction modes [GH-90000]")
    class SelectiveModeTests {

        @Test
        @DisplayName("PII-only mode does NOT redact credentials [GH-90000]")
        void piiOnlyLeavesCredentials() { // GH-90000
            MemoryRedactionFilter piiOnly = new MemoryRedactionFilter(true, false); // GH-90000
            String result = piiOnly.redact("api_key=my-api-key [GH-90000]");
            // credential pattern should NOT be applied
            assertThat(result).contains("api_key=my-api-key [GH-90000]");
        }

        @Test
        @DisplayName("Credential-only mode does NOT redact PII [GH-90000]")
        void credentialOnlyLeavesPii() { // GH-90000
            MemoryRedactionFilter credOnly = new MemoryRedactionFilter(false, true); // GH-90000
            String result = credOnly.redact("alice@example.com [GH-90000]");
            // PII pattern should NOT be applied
            assertThat(result).contains("alice@example.com [GH-90000]");
        }

        @Test
        @DisplayName("both-disabled mode returns text unchanged [GH-90000]")
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
    @DisplayName("containsSensitiveContent() [GH-90000]")
    class ContainsSensitiveContentTests {

        private final MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000

        @Test
        @DisplayName("detects email as sensitive [GH-90000]")
        void detectsEmail() { // GH-90000
            assertThat(filter.containsSensitiveContent("Send to bob@corp.io [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("detects API key as sensitive [GH-90000]")
        void detectsApiKey() { // GH-90000
            assertThat(filter.containsSensitiveContent("apikey=abc123 [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("returns false for clean text [GH-90000]")
        void returnsFalseForCleanText() { // GH-90000
            assertThat(filter.containsSensitiveContent("The weather is sunny today. [GH-90000]")).isFalse();
        }
    }

    // =========================================================================
    // Pattern counts
    // =========================================================================

    @Nested
    @DisplayName("Pattern counts [GH-90000]")
    class PatternCountTests {

        @Test
        @DisplayName("defaultFilter has at least 3 PII patterns (email, phone, SSN) [GH-90000]")
        void defaultFilterHasPiiPatterns() { // GH-90000
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000
            assertThat(filter.piiPatternCount()).isGreaterThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("defaultFilter has at least 3 credential patterns [GH-90000]")
        void defaultFilterHasCredentialPatterns() { // GH-90000
            MemoryRedactionFilter filter = MemoryRedactionFilter.defaultFilter(); // GH-90000
            assertThat(filter.credentialPatternCount()).isGreaterThanOrEqualTo(3); // GH-90000
        }
    }
}
