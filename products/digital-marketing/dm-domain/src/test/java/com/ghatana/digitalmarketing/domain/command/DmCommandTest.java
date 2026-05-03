package com.ghatana.digitalmarketing.domain.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmCommand}.
 *
 * @doc.type class
 * @doc.purpose Verifies DmCommand entity lifecycle and builder validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmCommand Tests")
class DmCommandTest {

    private DmCommand.Builder validBuilder() {
        return DmCommand.builder()
            .id("cmd-1")
            .commandType(DmCommandType.CAMPAIGN_CREATE)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .correlationId("corr-1")
            .issuedBy("user-1")
            .serializedPayload("{}")
            .status(DmCommandStatus.PENDING)
            .attemptCount(0)
            .createdAt(Instant.now())
            .scheduledAt(Instant.now());
    }

    @Test
    @DisplayName("builder creates a valid command")
    void builderCreatesValidCommand() {
        DmCommand cmd = validBuilder().build();

        assertThat(cmd.getId()).isEqualTo("cmd-1");
        assertThat(cmd.getCommandType()).isEqualTo(DmCommandType.CAMPAIGN_CREATE);
        assertThat(cmd.getTenantId()).isEqualTo("tenant-1");
        assertThat(cmd.getStatus()).isEqualTo(DmCommandStatus.PENDING);
        assertThat(cmd.isRetryable()).isTrue();
        assertThat(cmd.getExecutedAt()).isNull();
        assertThat(cmd.getCompletedAt()).isNull();
        assertThat(cmd.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markExecuting returns copy with EXECUTING status and incremented attempt count")
    void markExecutingTransitionsState() {
        DmCommand cmd = validBuilder().build();
        DmCommand executing = cmd.markExecuting();

        assertThat(executing.getStatus()).isEqualTo(DmCommandStatus.EXECUTING);
        assertThat(executing.getAttemptCount()).isEqualTo(1);
        assertThat(executing.getExecutedAt()).isNotNull();
        // original unchanged
        assertThat(cmd.getStatus()).isEqualTo(DmCommandStatus.PENDING);
    }

    @Test
    @DisplayName("markSucceeded returns copy with SUCCEEDED status")
    void markSucceededTransitionsState() {
        DmCommand cmd = validBuilder().build().markExecuting();
        DmCommand succeeded = cmd.markSucceeded();

        assertThat(succeeded.getStatus()).isEqualTo(DmCommandStatus.SUCCEEDED);
        assertThat(succeeded.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed returns copy with FAILED status and reason")
    void markFailedTransitionsState() {
        DmCommand cmd = validBuilder().build().markExecuting();
        DmCommand failed = cmd.markFailed("quota-exceeded");

        assertThat(failed.getStatus()).isEqualTo(DmCommandStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("quota-exceeded");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed with null reason defaults to 'unknown'")
    void markFailedDefaultsReason() {
        DmCommand cmd = validBuilder().build().markExecuting().markFailed(null);

        assertThat(cmd.getFailureReason()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("markRolledBack returns copy with ROLLED_BACK status")
    void markRolledBackTransitionsState() {
        DmCommand cmd = validBuilder().build().markExecuting().markFailed("error");
        DmCommand rb = cmd.markRolledBack();

        assertThat(rb.getStatus()).isEqualTo(DmCommandStatus.ROLLED_BACK);
    }

    @Test
    @DisplayName("isRetryable is true for PENDING within attempt limit")
    void isRetryableTrueForPending() {
        DmCommand cmd = validBuilder().attemptCount(0).status(DmCommandStatus.PENDING).build();
        assertThat(cmd.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("isRetryable is true for FAILED within attempt limit")
    void isRetryableTrueForFailed() {
        DmCommand cmd = validBuilder().attemptCount(1).status(DmCommandStatus.FAILED).build();
        assertThat(cmd.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("isRetryable is false at MAX_ATTEMPTS")
    void isRetryableFalseAtMaxAttempts() {
        DmCommand cmd = validBuilder().attemptCount(DmCommand.MAX_ATTEMPTS).status(DmCommandStatus.FAILED).build();
        assertThat(cmd.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("isRetryable is false for SUCCEEDED")
    void isRetryableFalseForSucceeded() {
        DmCommand cmd = validBuilder().status(DmCommandStatus.SUCCEEDED).build();
        assertThat(cmd.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("isRetryable is false for ROLLED_BACK")
    void isRetryableFalseForRolledBack() {
        DmCommand cmd = validBuilder().status(DmCommandStatus.ROLLED_BACK).build();
        assertThat(cmd.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode are id-based")
    void equalsAndHashCodeAreIdBased() {
        DmCommand a = validBuilder().id("cmd-x").build();
        DmCommand b = validBuilder().id("cmd-x").commandType(DmCommandType.CAMPAIGN_DELETE).build();
        DmCommand c = validBuilder().id("cmd-y").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString contains id, type, and status")
    void toStringContainsKeyFields() {
        DmCommand cmd = validBuilder().build();
        String str = cmd.toString();

        assertThat(str).contains("cmd-1");
        assertThat(str).contains("CAMPAIGN_CREATE");
        assertThat(str).contains("PENDING");
    }

    // ── builder validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("builder rejects blank id")
    void builderRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().id("  ").build());
    }

    @Test
    @DisplayName("builder rejects null commandType")
    void builderRejectsNullCommandType() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().commandType(null).build());
    }

    @Test
    @DisplayName("builder rejects blank tenantId")
    void builderRejectsBlankTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().tenantId("").build());
    }

    @Test
    @DisplayName("builder rejects blank workspaceId")
    void builderRejectsBlankWorkspaceId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().workspaceId("").build());
    }

    @Test
    @DisplayName("builder rejects blank correlationId")
    void builderRejectsBlankCorrelationId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().correlationId("").build());
    }

    @Test
    @DisplayName("builder rejects blank issuedBy")
    void builderRejectsBlankIssuedBy() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().issuedBy("").build());
    }

    @Test
    @DisplayName("builder rejects blank serializedPayload")
    void builderRejectsBlankPayload() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().serializedPayload("  ").build());
    }

    @Test
    @DisplayName("builder rejects negative attemptCount")
    void builderRejectsNegativeAttemptCount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().attemptCount(-1).build());
    }

    @Test
    @DisplayName("builder rejects null createdAt")
    void builderRejectsNullCreatedAt() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().createdAt(null).build());
    }

    @Test
    @DisplayName("builder rejects null scheduledAt")
    void builderRejectsNullScheduledAt() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().scheduledAt(null).build());
    }
}
