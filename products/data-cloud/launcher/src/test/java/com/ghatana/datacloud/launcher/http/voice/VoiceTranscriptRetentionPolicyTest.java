/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void audioBiggerRetentionIsZero() {
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_AUDIO_BUFFER).isZero();
        }

        @Test
        @DisplayName("transcript text retention is ZERO (never persisted)")
        void transcriptTextRetentionIsZero() {
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_TRANSCRIPT_TEXT).isZero();
        }

        @Test
        @DisplayName("intent audit retention is 7 days")
        void intentAuditRetentionIs7Days() {
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_INTENT_AUDIT)
                .isEqualTo(Duration.ofDays(7));
        }

        @Test
        @DisplayName("diagnostic log retention is 24 hours")
        void diagnosticLogRetentionIs24Hours() {
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_DIAGNOSTIC_LOG)
                .isEqualTo(Duration.ofHours(24));
        }

        @Test
        @DisplayName("feedback record retention is 90 days")
        void feedbackRecordRetentionIs90Days() {
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_FEEDBACK_RECORD)
                .isEqualTo(Duration.ofDays(90));
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
        void audioZero_isCompliant() {
            assertThatCode(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ZERO))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("audio tier: one second retention violates policy")
        void audioOneSecond_violatesPolicy() {
            assertThatThrownBy(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audio");
        }

        @Test
        @DisplayName("intent_audit tier: exactly 7 days is compliant")
        void intentAudit7Days_isCompliant() {
            assertThatCode(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(7)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("intent_audit tier: 8 days violates policy")
        void intentAudit8Days_violatesPolicy() {
            assertThatThrownBy(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intent_audit");
        }

        @Test
        @DisplayName("feedback tier: 90 days is compliant")
        void feedback90Days_isCompliant() {
            assertThatCode(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("feedback", Duration.ofDays(90)))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("unknown tier throws IllegalArgumentException")
        void unknownTier_throws() {
            assertThatThrownBy(() ->
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("archive", Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown voice retention tier");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sanitise() — PII detection + redaction
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sanitise() — PII detection")
    class SanitiseTests {

        @Test
        @DisplayName("null input returns empty string")
        void nullInput_returnsEmpty() {
            assertThat(VoiceTranscriptRetentionPolicy.sanitise(null)).isEmpty();
        }

        @Test
        @DisplayName("blank input returns empty string")
        void blankInput_returnsEmpty() {
            assertThat(VoiceTranscriptRetentionPolicy.sanitise("   ")).isEmpty();
        }

        @Test
        @DisplayName("plain command with no PII is returned unchanged (no redaction markers)")
        void plainCommand_returnedUnchanged() {
            String input = "list pipelines for my team";
            String result = VoiceTranscriptRetentionPolicy.sanitise(input);
            assertThat(result).doesNotContain("[REDACTED:");
        }

        @Test
        @DisplayName("email address in transcript is redacted")
        void emailIsRedacted() {
            String result = VoiceTranscriptRetentionPolicy.sanitise(
                "send results to alice@example.com please");
            assertThat(result).contains("[REDACTED:EMAIL]");
            assertThat(result).doesNotContain("alice@example.com");
        }

        @Test
        @DisplayName("US phone number is redacted")
        void phoneIsRedacted() {
            String result = VoiceTranscriptRetentionPolicy.sanitise(
                "call me at 415-555-1234");
            assertThat(result).contains("[REDACTED:PHONE]");
            assertThat(result).doesNotContain("415-555-1234");
        }

        @Test
        @DisplayName("US SSN pattern is redacted")
        void ssnIsRedacted() {
            String result = VoiceTranscriptRetentionPolicy.sanitise(
                "my SSN is 123-45-6789");
            assertThat(result).contains("[REDACTED:SSN]");
            assertThat(result).doesNotContain("123-45-6789");
        }

        @Test
        @DisplayName("IPv4 address is redacted")
        void ipv4IsRedacted() {
            String result = VoiceTranscriptRetentionPolicy.sanitise(
                "connect to server at 192.168.1.100");
            assertThat(result).contains("[REDACTED:IP]");
            assertThat(result).doesNotContain("192.168.1.100");
        }

        @Test
        @DisplayName("multiple PII types in one transcript: all are redacted")
        void multiplepiitypes_allRedacted() {
            String result = VoiceTranscriptRetentionPolicy.sanitise(
                "my email is bob@test.org and phone 555-123-4567");
            assertThat(result).contains("[REDACTED:EMAIL]");
            assertThat(result).doesNotContain("bob@test.org");
            assertThat(result).doesNotContain("555-123-4567");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isAlwaysRedactedField()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAlwaysRedactedField()")
    class AlwaysRedactedFieldTests {

        @ParameterizedTest
        @ValueSource(strings = {"email", "phone", "ssn", "credit_card", "api_key",
                                "password", "ip_address", "full_name", "bank_account"})
        @DisplayName("well-known PII fields return true")
        void wellKnownFields_returnTrue(String field) {
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(field)).isTrue();
        }

        @Test
        @DisplayName("check is case-insensitive")
        void caseInsensitive() {
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("EMAIL")).isTrue();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("Password")).isTrue();
        }

        @Test
        @DisplayName("non-PII field returns false")
        void nonPiiField_returnsFalse() {
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("collection")).isFalse();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("pipelineId")).isFalse();
        }

        @Test
        @DisplayName("null field name returns false")
        void nullField_returnsFalse() {
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(null)).isFalse();
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
        void hasAtLeast5Patterns() {
            assertThat(VoiceTranscriptRetentionPolicy.PII_PATTERNS).hasSizeGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("all patterns have non-blank labels")
        void allPatternsHaveLabels() {
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p ->
                assertThat(p.label()).isNotBlank());
        }

        @Test
        @DisplayName("all patterns have compiled Pattern objects")
        void allPatternsHaveCompiledPatterns() {
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p ->
                assertThat(p.pattern()).isNotNull());
        }
    }
}
