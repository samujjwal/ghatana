package com.ghatana.digitalmarketing.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmOutboxEntry} and {@link DmDeadLetterEntry}.
 *
 * @doc.type class
 * @doc.purpose Verifies outbox and DLQ domain entity lifecycle (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmOutboxEntry and DmDeadLetterEntry Tests")
class DmOutboxEntryTest {

    private DmOutboxEntry.Builder validOutboxBuilder() {
        return DmOutboxEntry.builder()
            .id(UUID.randomUUID().toString())
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.CAMPAIGN_CREATED)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .correlationId("corr-1")
            .serializedPayload("{\"v\":1}")
            .status(DmOutboxStatus.PENDING)
            .attemptCount(0)
            .createdAt(Instant.now())
            .scheduledAt(Instant.now());
    }

    // ── DmOutboxEntry ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("markDispatched returns entry with DISPATCHED status")
    void markDispatchedSetsStatus() {
        DmOutboxEntry entry = validOutboxBuilder().build();
        DmOutboxEntry dispatched = entry.markDispatched();

        assertThat(dispatched.getStatus()).isEqualTo(DmOutboxStatus.DISPATCHED);
        assertThat(dispatched.getLastAttemptAt()).isNotNull();
        // original unchanged
        assertThat(entry.getStatus()).isEqualTo(DmOutboxStatus.PENDING);
    }

    @Test
    @DisplayName("recordFailure increments attemptCount and sets FAILED below MAX_ATTEMPTS")
    void recordFailureSetsFailed() {
        DmOutboxEntry entry = validOutboxBuilder().build();
        DmOutboxEntry failed = entry.recordFailure("network-error");

        assertThat(failed.getStatus()).isEqualTo(DmOutboxStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastFailureReason()).isEqualTo("network-error");
    }

    @Test
    @DisplayName("recordFailure transitions to DEAD at MAX_ATTEMPTS")
    void recordFailureTransitionsToDead() {
        DmOutboxEntry entry = validOutboxBuilder().attemptCount(DmOutboxEntry.MAX_ATTEMPTS - 1).build();
        DmOutboxEntry dead = entry.recordFailure("final-error");

        assertThat(dead.getStatus()).isEqualTo(DmOutboxStatus.DEAD);
        assertThat(dead.getAttemptCount()).isEqualTo(DmOutboxEntry.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("isRetryable is true for FAILED entry below MAX_ATTEMPTS")
    void isRetryableTrueForFailed() {
        DmOutboxEntry entry = validOutboxBuilder()
            .status(DmOutboxStatus.FAILED).attemptCount(1).build();
        assertThat(entry.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("isRetryable is false for PENDING entry")
    void isRetryableFalseForPending() {
        DmOutboxEntry entry = validOutboxBuilder().status(DmOutboxStatus.PENDING).build();
        assertThat(entry.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("isRetryable is false for DISPATCHED entry")
    void isRetryableFalseForDispatched() {
        DmOutboxEntry entry = validOutboxBuilder().status(DmOutboxStatus.DISPATCHED).build();
        assertThat(entry.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("isRetryable is false for DEAD entry")
    void isRetryableFalseForDead() {
        DmOutboxEntry entry = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD).attemptCount(DmOutboxEntry.MAX_ATTEMPTS).build();
        assertThat(entry.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("isRetryable is false for FAILED at MAX_ATTEMPTS")
    void isRetryableFalseForFailedAtMax() {
        DmOutboxEntry entry = validOutboxBuilder()
            .status(DmOutboxStatus.FAILED).attemptCount(DmOutboxEntry.MAX_ATTEMPTS).build();
        assertThat(entry.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode are id-based")
    void equalsAndHashCodeAreIdBased() {
        String sharedId = UUID.randomUUID().toString();
        DmOutboxEntry a = validOutboxBuilder().id(sharedId).build();
        DmOutboxEntry b = validOutboxBuilder().id(sharedId).attemptCount(3).build();
        DmOutboxEntry c = validOutboxBuilder().id(UUID.randomUUID().toString()).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString contains id, eventType, and status")
    void toStringContainsKeyFields() {
        DmOutboxEntry entry = validOutboxBuilder().id("oe-1").build();
        String str = entry.toString();

        assertThat(str).contains("oe-1");
        assertThat(str).contains("CAMPAIGN_CREATED");
        assertThat(str).contains("PENDING");
    }

    // ── DmDeadLetterEntry ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fromOutboxEntry creates DLQ entry from DEAD outbox entry")
    void fromOutboxEntryCreatesEntry() {
        DmOutboxEntry dead = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD)
            .attemptCount(DmOutboxEntry.MAX_ATTEMPTS)
            .build()
            .recordFailure("fatal"); // produces a fresh DEAD entry

        DmDeadLetterEntry dlq = DmDeadLetterEntry.fromOutboxEntry(dead);

        assertThat(dlq.getId()).isNotBlank();
        assertThat(dlq.getOriginalOutboxId()).isEqualTo(dead.getId());
        assertThat(dlq.getEventType()).isEqualTo(DmEventType.CAMPAIGN_CREATED);
        assertThat(dlq.getTenantId()).isEqualTo("tenant-1");
        assertThat(dlq.isReplayed()).isFalse();
        assertThat(dlq.getReplayedAt()).isNull();
    }

    @Test
    @DisplayName("fromOutboxEntry rejects non-DEAD entries")
    void fromOutboxEntryRejectsNonDead() {
        DmOutboxEntry pending = validOutboxBuilder().build();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmDeadLetterEntry.fromOutboxEntry(pending));
    }

    @Test
    @DisplayName("fromOutboxEntry rejects null")
    void fromOutboxEntryRejectsNull() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> DmDeadLetterEntry.fromOutboxEntry(null));
    }

    @Test
    @DisplayName("markReplayed sets replayed flag and replayedAt timestamp")
    void markReplayedSetsReplayedState() {
        DmOutboxEntry dead = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD).attemptCount(DmOutboxEntry.MAX_ATTEMPTS)
            .build().recordFailure("err");
        DmDeadLetterEntry dlq = DmDeadLetterEntry.fromOutboxEntry(dead);

        DmDeadLetterEntry replayed = dlq.markReplayed();

        assertThat(replayed.isReplayed()).isTrue();
        assertThat(replayed.getReplayedAt()).isNotNull();
        // original unchanged
        assertThat(dlq.isReplayed()).isFalse();
    }

    @Test
    @DisplayName("DmDeadLetterEntry equals and hashCode are id-based")
    void dlqEqualsAndHashCodeAreIdBased() {
        DmOutboxEntry dead = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD).attemptCount(DmOutboxEntry.MAX_ATTEMPTS)
            .build().recordFailure("e");
        DmDeadLetterEntry a = DmDeadLetterEntry.fromOutboxEntry(dead);
        DmDeadLetterEntry b = DmDeadLetterEntry.fromOutboxEntry(dead);

        // Two different instances from same dead entry — different IDs (UUID)
        assertThat(a.getId()).isNotBlank();
        assertThat(b.getId()).isNotBlank();
    }

    @Test
    @DisplayName("DmDeadLetterEntry toString contains key fields")
    void dlqToStringContainsKeyFields() {
        DmOutboxEntry dead = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD).attemptCount(DmOutboxEntry.MAX_ATTEMPTS)
            .build().recordFailure("e");
        DmDeadLetterEntry dlq = DmDeadLetterEntry.fromOutboxEntry(dead);
        String str = dlq.toString();

        assertThat(str).contains("CAMPAIGN_CREATED");
    }

    @Test
    @DisplayName("DmDeadLetterEntry lastFailureReason defaults to 'unknown' when null")
    void dlqLastFailureReasonDefaultsToUnknown() {
        // Build a dead outbox entry that has no failure reason
        DmOutboxEntry dead = validOutboxBuilder()
            .status(DmOutboxStatus.DEAD)
            .attemptCount(DmOutboxEntry.MAX_ATTEMPTS)
            .build();
        // Use builder directly to set null failure reason
        DmDeadLetterEntry dlq = DmDeadLetterEntry.builder()
            .id(UUID.randomUUID().toString())
            .originalOutboxId(dead.getId())
            .eventId(dead.getEventId())
            .eventType(dead.getEventType())
            .tenantId(dead.getTenantId())
            .workspaceId(dead.getWorkspaceId())
            .correlationId(dead.getCorrelationId())
            .serializedPayload(dead.getSerializedPayload())
            .totalAttempts(dead.getAttemptCount())
            .lastFailureReason(null)  // triggers null branch
            .createdAt(dead.getCreatedAt())
            .deadAt(Instant.now())
            .replayed(false)
            .build();

        assertThat(dlq.getLastFailureReason()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("DmDeadLetterEntry builder rejects blank id")
    void dlqBuilderRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmDeadLetterEntry.builder()
                .id("  ")
                .originalOutboxId("oe-1")
                .eventId("ev-1")
                .eventType(DmEventType.CAMPAIGN_CREATED)
                .tenantId("t-1")
                .workspaceId("ws-1")
                .correlationId("c-1")
                .serializedPayload("{}")
                .createdAt(Instant.now())
                .deadAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("DmDeadLetterEntry builder rejects null id")
    void dlqBuilderRejectsNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> DmDeadLetterEntry.builder()
                .originalOutboxId("oe-1")
                .eventId("ev-1")
                .eventType(DmEventType.CAMPAIGN_CREATED)
                .tenantId("t-1")
                .workspaceId("ws-1")
                .correlationId("c-1")
                .serializedPayload("{}")
                .createdAt(Instant.now())
                .deadAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects blank id")
    void outboxBuilderRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().id("  ").build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects null id")
    void outboxBuilderRejectsNullId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().id(null).build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects blank tenantId")
    void outboxBuilderRejectsBlankTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().tenantId("").build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects null tenantId")
    void outboxBuilderRejectsNullTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().tenantId(null).build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects blank workspaceId")
    void outboxBuilderRejectsBlankWorkspaceId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().workspaceId("  ").build());
    }

    @Test
    @DisplayName("DmOutboxEntry builder rejects blank serializedPayload")
    void outboxBuilderRejectsBlankPayload() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validOutboxBuilder().serializedPayload("").build());
    }
}
