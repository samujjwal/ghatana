package com.ghatana.digitalmarketing.domain.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link Campaign} — covers construction, lifecycle state machine, and
 * immutability semantics.
 */
@DisplayName("Campaign")
class CampaignTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-001");

    private Campaign newDraftCampaign() {
        Instant now = Instant.now();
        return Campaign.builder()
            .id("campaign-1")
            .workspaceId(WS)
            .name("Q4 Acquisition")
            .status(CampaignStatus.DRAFT)
            .type(CampaignType.EMAIL)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-42")
            .build();
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("builder() constructs campaign with all required fields")
    void shouldBuildWithAllFields() {
        Campaign c = newDraftCampaign();
        assertThat(c.getId()).isEqualTo("campaign-1");
        assertThat(c.getWorkspaceId()).isEqualTo(WS);
        assertThat(c.getName()).isEqualTo("Q4 Acquisition");
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(c.getType()).isEqualTo(CampaignType.EMAIL);
        assertThat(c.getCreatedBy()).isEqualTo("user-42");
    }

    @Test
    @DisplayName("builder() rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Campaign.builder()
                .id("  ")
                .workspaceId(WS)
                .name("Test")
                .type(CampaignType.EMAIL)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("builder() rejects blank name")
    void shouldRejectBlankName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Campaign.builder()
                .id("c-1")
                .workspaceId(WS)
                .name("")
                .type(CampaignType.EMAIL)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    // -----------------------------------------------------------------------
    // Valid lifecycle transitions
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("launch() transitions DRAFT → LAUNCHED")
    void shouldTransitionDraftToLaunched() {
        Campaign launched = newDraftCampaign().launch();
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("pause() transitions LAUNCHED → PAUSED")
    void shouldTransitionLaunchedToPaused() {
        Campaign paused = newDraftCampaign().launch().pause();
        assertThat(paused.getStatus()).isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    @DisplayName("launch() on PAUSED transitions PAUSED → LAUNCHED (resume)")
    void shouldTransitionPausedToLaunched() {
        Campaign resumed = newDraftCampaign().launch().pause().launch();
        assertThat(resumed.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
    }

    @Test
    @DisplayName("complete() transitions LAUNCHED → COMPLETED")
    void shouldTransitionLaunchedToCompleted() {
        Campaign completed = newDraftCampaign().launch().complete();
        assertThat(completed.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
    }

    @Test
    @DisplayName("complete() transitions PAUSED → COMPLETED")
    void shouldTransitionPausedToCompleted() {
        Campaign completed = newDraftCampaign().launch().pause().complete();
        assertThat(completed.getStatus()).isEqualTo(CampaignStatus.COMPLETED);
    }

    @Test
    @DisplayName("archive() transitions COMPLETED → ARCHIVED")
    void shouldTransitionCompletedToArchived() {
        Campaign archived = newDraftCampaign().launch().complete().archive();
        assertThat(archived.getStatus()).isEqualTo(CampaignStatus.ARCHIVED);
    }

    // -----------------------------------------------------------------------
    // Invalid lifecycle transitions
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("pause() throws when campaign is DRAFT")
    void shouldThrowWhenPausingDraftCampaign() {
        assertThatIllegalStateException()
            .isThrownBy(() -> newDraftCampaign().pause())
            .withMessageContaining("pause");
    }

    @Test
    @DisplayName("complete() throws when campaign is DRAFT")
    void shouldThrowWhenCompletingDraftCampaign() {
        assertThatIllegalStateException()
            .isThrownBy(() -> newDraftCampaign().complete())
            .withMessageContaining("complete");
    }

    @Test
    @DisplayName("archive() throws when campaign is LAUNCHED")
    void shouldThrowWhenArchivingLaunchedCampaign() {
        assertThatIllegalStateException()
            .isThrownBy(() -> newDraftCampaign().launch().archive())
            .withMessageContaining("archive");
    }

    @Test
    @DisplayName("launch() throws when campaign is COMPLETED")
    void shouldThrowWhenLaunchingCompletedCampaign() {
        assertThatIllegalStateException()
            .isThrownBy(() -> newDraftCampaign().launch().complete().launch())
            .withMessageContaining("launch");
    }

    @Test
    @DisplayName("launch() throws when campaign is ARCHIVED")
    void shouldThrowWhenLaunchingArchivedCampaign() {
        assertThatIllegalStateException()
            .isThrownBy(() -> newDraftCampaign().launch().complete().archive().launch())
            .withMessageContaining("launch");
    }

    // -----------------------------------------------------------------------
    // Immutability
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("state transitions return a new Campaign instance — original is unchanged")
    void shouldReturnNewInstanceOnTransition() {
        Campaign draft = newDraftCampaign();
        Campaign launched = draft.launch();

        assertThat(draft.getStatus()).isEqualTo(CampaignStatus.DRAFT);  // original unchanged
        assertThat(launched.getStatus()).isEqualTo(CampaignStatus.LAUNCHED);
        assertThat(launched).isNotSameAs(draft);
    }

    // -----------------------------------------------------------------------
    // isLaunchable
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isLaunchable() is true for DRAFT and PAUSED, false otherwise")
    void shouldReportLaunchableCorrectly() {
        Campaign draft   = newDraftCampaign();
        Campaign launched = draft.launch();
        Campaign paused  = launched.pause();
        Campaign completed = launched.complete();

        assertThat(draft.isLaunchable()).isTrue();
        assertThat(paused.isLaunchable()).isTrue();
        assertThat(launched.isLaunchable()).isFalse();
        assertThat(completed.isLaunchable()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Equality
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("equals() is ID-based")
    void shouldHaveIdBasedEquality() {
        Campaign a = newDraftCampaign();
        Campaign b = newDraftCampaign();
        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and different type: not equal
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("some-string");
        // different id: not equal
        Campaign different = Campaign.builder()
            .id("campaign-different")
            .workspaceId(WS)
            .name("Other")
            .type(CampaignType.SOCIAL)
            .status(CampaignStatus.DRAFT)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .createdBy("user-1")
            .build();
        assertThat(a).isNotEqualTo(different);
        assertThat(a.hashCode()).isNotEqualTo(different.hashCode());
    }
}
