/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 */
package com.ghatana.datacloud.launcher.http.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link VoiceTranscriptRetentionPolicy}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for voice transcript PII redaction and retention windows
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VoiceTranscriptRetentionPolicy")
class VoiceTranscriptRetentionPolicyTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Retention tier windows
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Retention windows")
    class RetentionWindowTests {

        @Test
        @DisplayName("audio buffer retention is ZERO (delete immediately)")
        void audioBiggerRetentionIsZero() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_AUDIO_BUFFER).isZero(); // GH-90000
        }

        @Test
        @DisplayName("transcript text retention is ZERO (never persisted)")
        void transcriptTextRetentionIsZero() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_TRANSCRIPT_TEXT).isZero(); // GH-90000
        }

        @Test
        @DisplayName("intent audit retention is 7 days")
        void intentAuditRetentionIs7Days() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_INTENT_AUDIT) // GH-90000
                .isEqualTo(Duration.ofDays(7)); // GH-90000
        }

        @Test
        @DisplayName("diagnostic log retention is 24 hours")
        void diagnosticLogRetentionIs24Hours() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_DIAGNOSTIC_LOG) // GH-90000
                .isEqualTo(Duration.ofHours(24)); // GH-90000
        }

        @Test
        @DisplayName("feedback record retention is 90 days")
        void feedbackRecordRetentionIs90Days() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_FEEDBACK_RECORD) // GH-90000
                .isEqualTo(Duration.ofDays(90)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // assertRetentionCompliant
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertRetentionCompliant()")
    class RetentionComplianceTests {

        @Test
        @DisplayName("audio tier: zero duration is compliant")
        void audioZero_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ZERO)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("audio tier: one second retention violates policy")
        void audioOneSecond_violatesPolicy() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ofSeconds(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("audio");
        }

        @Test
        @DisplayName("intent_audit tier: exactly 7 days is compliant")
        void intentAudit7Days_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(7))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("intent_audit tier: 8 days violates policy")
        void intentAudit8Days_violatesPolicy() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(8))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("intent_audit");
        }

        @Test
        @DisplayName("feedback tier: 90 days is compliant")
        void feedback90Days_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("feedback", Duration.ofDays(90))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("unknown tier throws IllegalArgumentException")
        void unknownTier_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("archive", Duration.ofDays(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unknown voice retention tier");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sanitise() — PII detection + redaction // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sanitise() — PII detection")
    class SanitiseTests {

        @Test
        @DisplayName("null input returns empty string")
        void nullInput_returnsEmpty() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.sanitise(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("blank input returns empty string")
        void blankInput_returnsEmpty() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.sanitise("   ")).isEmpty();
        }

        @Test
        @DisplayName("plain command with no PII is returned unchanged (no redaction markers)")
        void plainCommand_returnedUnchanged() { // GH-90000
            String input = "list pipelines for my team";
            String result = VoiceTranscriptRetentionPolicy.sanitise(input); // GH-90000
            assertThat(result).doesNotContain("[REDACTED:");
        }

        @Test
        @DisplayName("email address in transcript is redacted")
        void emailIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "send results to alice@example.com please");
            assertThat(result).contains("[REDACTED:EMAIL]");
            assertThat(result).doesNotContain("alice@example.com");
        }

        @Test
        @DisplayName("US phone number is redacted")
        void phoneIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "call me at 415-555-1234");
            assertThat(result).contains("[REDACTED:PHONE]");
            assertThat(result).doesNotContain("415-555-1234");
        }

        @Test
        @DisplayName("US SSN pattern is redacted")
        void ssnIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "my SSN is 123-45-6789");
            assertThat(result).contains("[REDACTED:SSN]");
            assertThat(result).doesNotContain("123-45-6789");
        }

        @Test
        @DisplayName("IPv4 address is redacted")
        void ipv4IsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "connect to server at 192.168.1.100");
            assertThat(result).contains("[REDACTED:IP]");
            assertThat(result).doesNotContain("192.168.1.100");
        }

        @Test
        @DisplayName("multiple PII types in one transcript: all are redacted")
        void multiplepiitypes_allRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "my email is bob@test.org and phone 555-123-4567");
            assertThat(result).contains("[REDACTED:EMAIL]");
            assertThat(result).doesNotContain("bob@test.org");
            assertThat(result).doesNotContain("555-123-4567");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isAlwaysRedactedField() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAlwaysRedactedField()")
    class AlwaysRedactedFieldTests {

        @ParameterizedTest
        @ValueSource(strings = {"email", "phone", "ssn", "credit_card", "api_key", // GH-90000
                                "password", "ip_address", "full_name", "bank_account"})
        @DisplayName("well-known PII fields return true")
        void wellKnownFields_returnTrue(String field) { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(field)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("check is case-insensitive")
        void caseInsensitive() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("EMAIL")).isTrue();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("Password")).isTrue();
        }

        @Test
        @DisplayName("non-PII field returns false")
        void nonPiiField_returnsFalse() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("collection")).isFalse();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("pipelineId")).isFalse();
        }

        @Test
        @DisplayName("null field name returns false")
        void nullField_returnsFalse() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(null)).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PII_PATTERNS list completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PII_PATTERNS list")
    class PiiPatternListTests {

        @Test
        @DisplayName("contains at least 5 pattern entries")
        void hasAtLeast5Patterns() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.PII_PATTERNS).hasSizeGreaterThanOrEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("all patterns have non-blank labels")
        void allPatternsHaveLabels() { // GH-90000
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p -> // GH-90000
                assertThat(p.label()).isNotBlank()); // GH-90000
        }

        @Test
        @DisplayName("all patterns have compiled Pattern objects")
        void allPatternsHaveCompiledPatterns() { // GH-90000
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p -> // GH-90000
                assertThat(p.pattern()).isNotNull()); // GH-90000
        }
    }
}
