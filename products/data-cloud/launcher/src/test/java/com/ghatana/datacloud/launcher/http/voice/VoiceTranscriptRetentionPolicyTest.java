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
@DisplayName("VoiceTranscriptRetentionPolicy [GH-90000]")
class VoiceTranscriptRetentionPolicyTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Retention tier windows
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Retention windows [GH-90000]")
    class RetentionWindowTests {

        @Test
        @DisplayName("audio buffer retention is ZERO (delete immediately) [GH-90000]")
        void audioBiggerRetentionIsZero() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_AUDIO_BUFFER).isZero(); // GH-90000
        }

        @Test
        @DisplayName("transcript text retention is ZERO (never persisted) [GH-90000]")
        void transcriptTextRetentionIsZero() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_TRANSCRIPT_TEXT).isZero(); // GH-90000
        }

        @Test
        @DisplayName("intent audit retention is 7 days [GH-90000]")
        void intentAuditRetentionIs7Days() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_INTENT_AUDIT) // GH-90000
                .isEqualTo(Duration.ofDays(7)); // GH-90000
        }

        @Test
        @DisplayName("diagnostic log retention is 24 hours [GH-90000]")
        void diagnosticLogRetentionIs24Hours() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_DIAGNOSTIC_LOG) // GH-90000
                .isEqualTo(Duration.ofHours(24)); // GH-90000
        }

        @Test
        @DisplayName("feedback record retention is 90 days [GH-90000]")
        void feedbackRecordRetentionIs90Days() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.RETAIN_FEEDBACK_RECORD) // GH-90000
                .isEqualTo(Duration.ofDays(90)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // assertRetentionCompliant
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertRetentionCompliant() [GH-90000]")
    class RetentionComplianceTests {

        @Test
        @DisplayName("audio tier: zero duration is compliant [GH-90000]")
        void audioZero_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ZERO)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("audio tier: one second retention violates policy [GH-90000]")
        void audioOneSecond_violatesPolicy() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("audio", Duration.ofSeconds(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("audio [GH-90000]");
        }

        @Test
        @DisplayName("intent_audit tier: exactly 7 days is compliant [GH-90000]")
        void intentAudit7Days_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(7))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("intent_audit tier: 8 days violates policy [GH-90000]")
        void intentAudit8Days_violatesPolicy() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("intent_audit", Duration.ofDays(8))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("intent_audit [GH-90000]");
        }

        @Test
        @DisplayName("feedback tier: 90 days is compliant [GH-90000]")
        void feedback90Days_isCompliant() { // GH-90000
            assertThatCode(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("feedback", Duration.ofDays(90))) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("unknown tier throws IllegalArgumentException [GH-90000]")
        void unknownTier_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                VoiceTranscriptRetentionPolicy.assertRetentionCompliant("archive", Duration.ofDays(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unknown voice retention tier [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sanitise() — PII detection + redaction // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sanitise() — PII detection [GH-90000]")
    class SanitiseTests {

        @Test
        @DisplayName("null input returns empty string [GH-90000]")
        void nullInput_returnsEmpty() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.sanitise(null)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("blank input returns empty string [GH-90000]")
        void blankInput_returnsEmpty() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.sanitise("    [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("plain command with no PII is returned unchanged (no redaction markers) [GH-90000]")
        void plainCommand_returnedUnchanged() { // GH-90000
            String input = "list pipelines for my team";
            String result = VoiceTranscriptRetentionPolicy.sanitise(input); // GH-90000
            assertThat(result).doesNotContain("[REDACTED: [GH-90000]");
        }

        @Test
        @DisplayName("email address in transcript is redacted [GH-90000]")
        void emailIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "send results to alice@example.com please");
            assertThat(result).contains("[REDACTED:EMAIL] [GH-90000]");
            assertThat(result).doesNotContain("alice@example.com [GH-90000]");
        }

        @Test
        @DisplayName("US phone number is redacted [GH-90000]")
        void phoneIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "call me at 415-555-1234");
            assertThat(result).contains("[REDACTED:PHONE] [GH-90000]");
            assertThat(result).doesNotContain("415-555-1234 [GH-90000]");
        }

        @Test
        @DisplayName("US SSN pattern is redacted [GH-90000]")
        void ssnIsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "my SSN is 123-45-6789");
            assertThat(result).contains("[REDACTED:SSN] [GH-90000]");
            assertThat(result).doesNotContain("123-45-6789 [GH-90000]");
        }

        @Test
        @DisplayName("IPv4 address is redacted [GH-90000]")
        void ipv4IsRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "connect to server at 192.168.1.100");
            assertThat(result).contains("[REDACTED:IP] [GH-90000]");
            assertThat(result).doesNotContain("192.168.1.100 [GH-90000]");
        }

        @Test
        @DisplayName("multiple PII types in one transcript: all are redacted [GH-90000]")
        void multiplepiitypes_allRedacted() { // GH-90000
            String result = VoiceTranscriptRetentionPolicy.sanitise( // GH-90000
                "my email is bob@test.org and phone 555-123-4567");
            assertThat(result).contains("[REDACTED:EMAIL] [GH-90000]");
            assertThat(result).doesNotContain("bob@test.org [GH-90000]");
            assertThat(result).doesNotContain("555-123-4567 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isAlwaysRedactedField() // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAlwaysRedactedField() [GH-90000]")
    class AlwaysRedactedFieldTests {

        @ParameterizedTest
        @ValueSource(strings = {"email", "phone", "ssn", "credit_card", "api_key", // GH-90000
                                "password", "ip_address", "full_name", "bank_account"})
        @DisplayName("well-known PII fields return true [GH-90000]")
        void wellKnownFields_returnTrue(String field) { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(field)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("check is case-insensitive [GH-90000]")
        void caseInsensitive() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("EMAIL [GH-90000]")).isTrue();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("Password [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("non-PII field returns false [GH-90000]")
        void nonPiiField_returnsFalse() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("collection [GH-90000]")).isFalse();
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField("pipelineId [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("null field name returns false [GH-90000]")
        void nullField_returnsFalse() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.isAlwaysRedactedField(null)).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PII_PATTERNS list completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PII_PATTERNS list [GH-90000]")
    class PiiPatternListTests {

        @Test
        @DisplayName("contains at least 5 pattern entries [GH-90000]")
        void hasAtLeast5Patterns() { // GH-90000
            assertThat(VoiceTranscriptRetentionPolicy.PII_PATTERNS).hasSizeGreaterThanOrEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("all patterns have non-blank labels [GH-90000]")
        void allPatternsHaveLabels() { // GH-90000
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p -> // GH-90000
                assertThat(p.label()).isNotBlank()); // GH-90000
        }

        @Test
        @DisplayName("all patterns have compiled Pattern objects [GH-90000]")
        void allPatternsHaveCompiledPatterns() { // GH-90000
            VoiceTranscriptRetentionPolicy.PII_PATTERNS.forEach(p -> // GH-90000
                assertThat(p.pattern()).isNotNull()); // GH-90000
        }
    }
}
