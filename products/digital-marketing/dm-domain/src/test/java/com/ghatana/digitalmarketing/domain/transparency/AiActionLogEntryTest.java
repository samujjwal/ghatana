package com.ghatana.digitalmarketing.domain.transparency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiActionLogEntry")
class AiActionLogEntryTest {

    private static AiActionLogEntry valid() {
        return new AiActionLogEntry(
            "act-1",
            "ws-1",
            "corr-1",
            AiActionType.RECOMMENDATION_GENERATED,
            AiActionStatus.PROPOSED,
            "agent-dmos",
            true,
            0.91,
            List.of("https://evidence.example/1"),
            List.of("policy:brand-safe"),
            "Generated strategy recommendation",
            "Used 30-day strategy inputs and approved claims set",
            "strategy-1",
            Instant.now()
        );
    }

    @Test
    @DisplayName("creates valid entry")
    void shouldCreateValidEntry() {
        AiActionLogEntry entry = valid();
        assertThat(entry.actionId()).isEqualTo("act-1");
        assertThat(entry.confidence()).isEqualTo(0.91);
        assertThat(entry.evidenceLinks()).hasSize(1);
    }

    @Test
    @DisplayName("rejects out-of-range confidence")
    void shouldRejectInvalidConfidence() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "act-1", "ws-1", "corr-1", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", true, 1.5,
            List.of(), List.of(), "sum", "details", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("redacted copy removes sensitive details")
    void shouldRedactSensitiveDetails() {
        AiActionLogEntry redacted = valid().redacted();
        assertThat(redacted.details()).isEqualTo("REDACTED");
        assertThat(redacted.evidenceLinks()).isEmpty();
        assertThat(redacted.policyChecks()).hasSize(1);
    }

    @Test
    @DisplayName("null evidenceLinks defaults to empty list")
    void nullEvidenceLinksDefault() {
        AiActionLogEntry e = new AiActionLogEntry(
            "act-1", "ws-1", "corr-1", AiActionType.RECOMMENDATION_GENERATED,
            AiActionStatus.PROPOSED, "agent", true, null,
            null, null, "sum", "details", null, Instant.now());
        assertThat(e.evidenceLinks()).isEmpty();
        assertThat(e.policyChecks()).isEmpty();
        assertThat(e.confidence()).isNull();
    }

    @Test
    @DisplayName("rejects blank actionId")
    void rejectsBlankActionId() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "", "ws-1", "corr-1", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, null,
            null, null, "sum", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank workspaceId")
    void rejectsBlankWorkspaceId() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "", "corr-1", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, null,
            null, null, "sum", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank correlationId")
    void rejectsBlankCorrelationId() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "ws", "", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, null,
            null, null, "sum", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank actor")
    void rejectsBlankActor() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "ws", "corr", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "", false, null,
            null, null, "sum", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank summary")
    void rejectsBlankSummary() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "ws", "corr", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, null,
            null, null, "", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects blank details")
    void rejectsBlankDetails() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "ws", "corr", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, null,
            null, null, "sum", "", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects negative confidence")
    void rejectsNegativeConfidence() {
        assertThatThrownBy(() -> new AiActionLogEntry(
            "a", "ws", "corr", AiActionType.ACTION_EXECUTED,
            AiActionStatus.EXECUTED, "agent", false, -0.1,
            null, null, "sum", "detail", null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
