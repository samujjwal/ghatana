package com.ghatana.digitalmarketing.persistence.ai;

import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-014: Redaction behavior tests for AI action log entries.
 */
@DisplayName("P1-014: AI Action Log Entry Redaction Tests")
class AiActionLogRedactionTest {

    private static AiActionLogEntry sampleEntry() {
        return new AiActionLogEntry(
            "act-1",
            "ws-1",
            "corr-1",
            AiActionType.RECOMMENDATION_GENERATED,
            AiActionStatus.PROPOSED,
            "agent-dmos",
            true,
            0.92,
            List.of("https://evidence.example/1", "https://evidence.example/2"),
            List.of("policy:brand-safe"),
            "Generated strategy recommendation",
            "Contains sensitive prompt details",
            "strategy-1",
            Instant.parse("2026-01-10T10:00:00Z"),
            1L
        );
    }

    @Test
    @DisplayName("redacted copy removes details and evidence links")
    void shouldRedactSensitiveFields() {
        AiActionLogEntry redacted = sampleEntry().redacted();

        assertThat(redacted.details()).isEqualTo("REDACTED");
        assertThat(redacted.evidenceLinks()).isEmpty();
    }

    @Test
    @DisplayName("redacted copy preserves non-sensitive identifying fields")
    void shouldPreserveIdentityAndStatus() {
        AiActionLogEntry source = sampleEntry();
        AiActionLogEntry redacted = source.redacted();

        assertThat(redacted.actionId()).isEqualTo(source.actionId());
        assertThat(redacted.workspaceId()).isEqualTo(source.workspaceId());
        assertThat(redacted.correlationId()).isEqualTo(source.correlationId());
        assertThat(redacted.actionType()).isEqualTo(source.actionType());
        assertThat(redacted.status()).isEqualTo(source.status());
        assertThat(redacted.summary()).isEqualTo(source.summary());
    }

    @Test
    @DisplayName("redacted copy retains policy checks for auditability")
    void shouldKeepPolicyChecks() {
        AiActionLogEntry redacted = sampleEntry().redacted();

        assertThat(redacted.policyChecks()).containsExactly("policy:brand-safe");
    }
}
