package com.ghatana.digitalmarketing.domain.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmEmailFollowUp domain entity")
class DmEmailFollowUpTest {

    private static final Instant NOW = Instant.parse("2026-05-02T18:00:00Z");

    private DmEmailFollowUp valid() {
        return DmEmailFollowUp.builder()
            .id("em-1").tenantId("t1").workspaceId("ws1").connectorId("conn-1")
            .recipientEmails(List.of("a@example.com")).subject("Hello")
            .bodyHtml("<p>Hi</p>").status(DmEmailFollowUpStatus.PENDING)
            .scheduledAt(NOW.plusSeconds(900)).createdAt(NOW).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmEmailFollowUp e = valid();
        assertThat(e.getId()).isEqualTo("em-1");
        assertThat(e.getStatus()).isEqualTo(DmEmailFollowUpStatus.PENDING);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("").tenantId("t").connectorId("c")
                .recipientEmails(List.of("a@a.com")).subject("s").bodyHtml("b")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(NOW).build());
    }

    @Test @DisplayName("builder rejects blank subject")
    void shouldRejectBlankSubject() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").connectorId("c")
                .recipientEmails(List.of("a@a.com")).subject("").bodyHtml("b")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(NOW).build());
    }

    @Test @DisplayName("markSent transitions from PENDING")
    void shouldMarkSent() {
        DmEmailFollowUp sent = valid().markSent(5, 1);
        assertThat(sent.getStatus()).isEqualTo(DmEmailFollowUpStatus.SENT);
        assertThat(sent.getSentCount()).isEqualTo(5);
        assertThat(sent.getFailedCount()).isEqualTo(1);
        assertThat(sent.getExecutedAt()).isNotNull();
    }

    @Test @DisplayName("markSent rejects non-PENDING state")
    void shouldNotMarkSentTwice() {
        DmEmailFollowUp sent = valid().markSent(1, 0);
        assertThatIllegalStateException().isThrownBy(() -> sent.markSent(1, 0));
    }

    @Test @DisplayName("markFailed transitions from PENDING")
    void shouldMarkFailed() {
        DmEmailFollowUp failed = valid().markFailed("timeout");
        assertThat(failed.getStatus()).isEqualTo(DmEmailFollowUpStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("timeout");
        assertThat(failed.getExecutedAt()).isNotNull();
    }

    @Test @DisplayName("cancel transitions from pending and rejects later states")
    void shouldCancelPendingOnly() {
        DmEmailFollowUp cancelled = valid().cancel();

        assertThat(cancelled.getStatus()).isEqualTo(DmEmailFollowUpStatus.CANCELLED);
        assertThat(cancelled.getExecutedAt()).isNotNull();

        assertThatIllegalStateException().isThrownBy(cancelled::cancel);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmEmailFollowUp left = valid();
        DmEmailFollowUp sameId = valid();
        DmEmailFollowUp differentId = DmEmailFollowUp.builder()
            .id("em-9").tenantId("t1").workspaceId("ws1").connectorId("conn-1")
            .recipientEmails(List.of("a@example.com")).subject("Hello")
            .bodyHtml("<p>Hi</p>").status(DmEmailFollowUpStatus.PENDING)
            .createdAt(NOW).build();

        assertThat(left).isEqualTo(sameId).hasSameHashCodeAs(sameId).isNotEqualTo(differentId);
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId(null).subject("s")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(NOW).build());
    }

    @Test @DisplayName("null status throws")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").subject("s")
                .status(null).createdAt(NOW).build());
    }

    @Test @DisplayName("null createdAt throws")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").subject("s")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(null).build());
    }

    @Test @DisplayName("null recipient emails throws")
    void shouldRejectNullRecipientEmails() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").subject("s")
                .recipientEmails(null).status(DmEmailFollowUpStatus.PENDING).createdAt(NOW).build());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmEmailFollowUp e = valid();
        assertThat(e.getTenantId()).isEqualTo("t1");
        assertThat(e.getWorkspaceId()).isEqualTo("ws1");
        assertThat(e.getConnectorId()).isEqualTo("conn-1");
        assertThat(e.getRecipientEmails()).containsExactly("a@example.com");
        assertThat(e.getSubject()).isEqualTo("Hello");
        assertThat(e.getBodyHtml()).isEqualTo("<p>Hi</p>");
        assertThat(e.getStatus()).isEqualTo(DmEmailFollowUpStatus.PENDING);
        assertThat(e.getSentCount()).isZero();
        assertThat(e.getFailedCount()).isZero();
        assertThat(e.getFailureReason()).isNull();
        assertThat(e.getScheduledAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(e.getExecutedAt()).isNull();
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
        assertThat(e.getRecipientEmails()).isUnmodifiable();
        assertThat(e.toString()).contains("em-1", "PENDING");
    }

    @Test @DisplayName("toBuilder preserves and overrides values")
    void shouldCopyWithToBuilder() {
        DmEmailFollowUp original = valid().markFailed("smtp timeout");

        DmEmailFollowUp copy = original.toBuilder()
            .subject("Retry")
            .failureReason(null)
            .status(DmEmailFollowUpStatus.PENDING)
            .executedAt(null)
            .build();

        assertThat(copy.getId()).isEqualTo("em-1");
        assertThat(copy.getSubject()).isEqualTo("Retry");
        assertThat(copy.getFailureReason()).isNull();
        assertThat(copy.getStatus()).isEqualTo(DmEmailFollowUpStatus.PENDING);
        assertThat(copy.getExecutedAt()).isNull();
    }
}
