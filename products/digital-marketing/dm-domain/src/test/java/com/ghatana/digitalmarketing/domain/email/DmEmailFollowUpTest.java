package com.ghatana.digitalmarketing.domain.email;

import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUp;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmEmailFollowUp domain entity")
class DmEmailFollowUpTest {

    private DmEmailFollowUp valid() {
        return DmEmailFollowUp.builder()
            .id("em-1").tenantId("t1").workspaceId("ws1").connectorId("conn-1")
            .recipientEmails(List.of("a@example.com")).subject("Hello")
            .bodyHtml("<p>Hi</p>").status(DmEmailFollowUpStatus.PENDING)
            .createdAt(Instant.now()).build();
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
                .status(DmEmailFollowUpStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank subject")
    void shouldRejectBlankSubject() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").connectorId("c")
                .recipientEmails(List.of("a@a.com")).subject("").bodyHtml("b")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("markSent transitions from PENDING")
    void shouldMarkSent() {
        DmEmailFollowUp sent = valid().markSent(5, 0);
        assertThat(sent.getStatus()).isEqualTo(DmEmailFollowUpStatus.SENT);
        assertThat(sent.getSentCount()).isEqualTo(5);
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
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId(null).subject("s")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null status throws")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").subject("s")
                .status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null createdAt throws")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEmailFollowUp.builder().id("x").tenantId("t").subject("s")
                .status(DmEmailFollowUpStatus.PENDING).createdAt(null).build());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmEmailFollowUp e = valid();
        assertThat(e.getTenantId()).isNotNull();
        assertThat(e.getSubject()).isNotNull();
        assertThat(e.getStatus()).isNotNull();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.toString()).isNotNull();
    }
}
