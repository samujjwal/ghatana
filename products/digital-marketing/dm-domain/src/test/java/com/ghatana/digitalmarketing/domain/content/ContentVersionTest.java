package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the F1-017 content version domain layer.
 * Covers {@link ContentVersion}, {@link ContentItem}, {@link ContentBlock},
 * {@link ClaimReference}, {@link DisclosureReference}, and {@link GeneratorMetadata}.
 */
@DisplayName("Content Version Domain Tests")
class ContentVersionTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-test");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    // ---- helpers ----

    private static ContentBlock block() {
        return new ContentBlock("blk-1", "HERO", "Hero content", 0);
    }

    private static ClaimReference claim() {
        return new ClaimReference("clm-1", "Our product is #1", "INTERNAL_RESEARCH");
    }

    private static DisclosureReference disclosure() {
        return new DisclosureReference("dis-1", "Results may vary.", "LEGAL");
    }

    private static GeneratorMetadata metadata() {
        return new GeneratorMetadata("model-v1", "prompt-v2", "TEMPLATE_BASED", NOW);
    }

    private static ContentVersion.Builder baseVersionBuilder() {
        return ContentVersion.builder()
            .versionId("ver-1")
            .itemId("item-1")
            .workspaceId(WS)
            .versionNumber(1)
            .contentBlocks(List.of(block()))
            .claimReferences(List.of(claim()))
            .disclosureReferences(List.of(disclosure()))
            .generatorMetadata(metadata())
            .status(ContentVersionStatus.DRAFT)
            .createdAt(NOW)
            .createdBy("user-alice");
    }

    private static ContentItem.Builder baseItemBuilder() {
        return ContentItem.builder()
            .itemId("item-1")
            .workspaceId(WS)
            .title("Landing Page Copy")
            .itemType(ContentItemType.LANDING_PAGE)
            .description("A test landing page")
            .createdAt(NOW)
            .createdBy("user-alice");
    }

    // ---- ContentBlock validation ----

    @Test
    @DisplayName("ContentBlock accepts valid fields")
    void shouldBuildContentBlock() {
        ContentBlock b = block();
        assertThat(b.blockId()).isEqualTo("blk-1");
        assertThat(b.blockType()).isEqualTo("HERO");
        assertThat(b.bodyText()).isEqualTo("Hero content");
        assertThat(b.ordering()).isZero();
    }

    @Test
    @DisplayName("ContentBlock rejects blank blockId")
    void shouldRejectBlankBlockId() {
        assertThatThrownBy(() -> new ContentBlock("  ", "HERO", "body", 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContentBlock rejects blank blockType")
    void shouldRejectBlankBlockType() {
        assertThatThrownBy(() -> new ContentBlock("blk-1", "", "body", 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContentBlock rejects negative ordering")
    void shouldRejectNegativeOrdering() {
        assertThatThrownBy(() -> new ContentBlock("blk-1", "HERO", "body", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ClaimReference validation ----

    @Test
    @DisplayName("ClaimReference accepts valid fields")
    void shouldBuildClaimReference() {
        assertThat(claim().claimId()).isEqualTo("clm-1");
        assertThat(claim().claimText()).isEqualTo("Our product is #1");
        assertThat(claim().claimSource()).isEqualTo("INTERNAL_RESEARCH");
    }

    @Test
    @DisplayName("ClaimReference rejects blank claimId")
    void shouldRejectBlankClaimId() {
        assertThatThrownBy(() -> new ClaimReference("", "text", "source"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ClaimReference rejects null claimText")
    void shouldRejectNullClaimText() {
        assertThatThrownBy(() -> new ClaimReference("id", null, "source"))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- DisclosureReference validation ----

    @Test
    @DisplayName("DisclosureReference accepts valid fields")
    void shouldBuildDisclosureReference() {
        assertThat(disclosure().disclosureId()).isEqualTo("dis-1");
        assertThat(disclosure().disclosureText()).isEqualTo("Results may vary.");
        assertThat(disclosure().disclosureType()).isEqualTo("LEGAL");
    }

    @Test
    @DisplayName("DisclosureReference rejects blank disclosureId")
    void shouldRejectBlankDisclosureId() {
        assertThatThrownBy(() -> new DisclosureReference("", "text", "LEGAL"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- GeneratorMetadata validation ----

    @Test
    @DisplayName("GeneratorMetadata accepts valid fields")
    void shouldBuildGeneratorMetadata() {
        GeneratorMetadata m = metadata();
        assertThat(m.modelVersion()).isEqualTo("model-v1");
        assertThat(m.promptVersion()).isEqualTo("prompt-v2");
        assertThat(m.sourceStrategy()).isEqualTo("TEMPLATE_BASED");
        assertThat(m.generatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("GeneratorMetadata rejects blank modelVersion")
    void shouldRejectBlankModelVersion() {
        assertThatThrownBy(() -> new GeneratorMetadata("", "p", "s", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GeneratorMetadata rejects null generatedAt")
    void shouldRejectNullGeneratedAt() {
        assertThatThrownBy(() -> new GeneratorMetadata("m", "p", "s", null))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- ContentItem validation ----

    @Test
    @DisplayName("ContentItem accepts valid fields")
    void shouldBuildContentItem() {
        ContentItem item = baseItemBuilder().build();
        assertThat(item.getItemId()).isEqualTo("item-1");
        assertThat(item.getTitle()).isEqualTo("Landing Page Copy");
        assertThat(item.getItemType()).isEqualTo(ContentItemType.LANDING_PAGE);
        assertThat(item.getWorkspaceId()).isEqualTo(WS);
        assertThat(item.getCreatedBy()).isEqualTo("user-alice");
    }

    @Test
    @DisplayName("ContentItem defaults null description to empty string")
    void shouldDefaultNullDescriptionToEmpty() {
        ContentItem item = baseItemBuilder().description(null).build();
        assertThat(item.getDescription()).isEmpty();
    }

    @Test
    @DisplayName("ContentItem rejects blank itemId")
    void shouldRejectBlankItemId() {
        assertThatThrownBy(() -> baseItemBuilder().itemId("  ").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContentItem rejects null itemType")
    void shouldRejectNullItemType() {
        assertThatThrownBy(() -> baseItemBuilder().itemType(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ContentItem rejects blank title")
    void shouldRejectBlankTitle() {
        assertThatThrownBy(() -> baseItemBuilder().title("").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ContentVersion validation ----

    @Test
    @DisplayName("ContentVersion builds with DRAFT status")
    void shouldBuildVersionInDraftStatus() {
        ContentVersion v = baseVersionBuilder().build();
        assertThat(v.getVersionId()).isEqualTo("ver-1");
        assertThat(v.getStatus()).isEqualTo(ContentVersionStatus.DRAFT);
        assertThat(v.isApproved()).isFalse();
        assertThat(v.getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("ContentVersion rejects zero versionNumber")
    void shouldRejectZeroVersionNumber() {
        assertThatThrownBy(() -> baseVersionBuilder().versionNumber(0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContentVersion rejects empty contentBlocks")
    void shouldRejectEmptyContentBlocks() {
        assertThatThrownBy(() -> baseVersionBuilder().contentBlocks(List.of()).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ContentVersion rejects null createdBy")
    void shouldRejectNullCreatedBy() {
        assertThatThrownBy(() -> baseVersionBuilder().createdBy(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    // ---- ContentVersion.submitForReview ----

    @Test
    @DisplayName("submitForReview transitions DRAFT → PENDING_REVIEW")
    void shouldTransitionToPendingReview() {
        ContentVersion v = baseVersionBuilder().build().submitForReview();
        assertThat(v.getStatus()).isEqualTo(ContentVersionStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("submitForReview throws if not in DRAFT")
    void shouldThrowIfSubmitCalledOnNonDraft() {
        ContentVersion pendingReview = baseVersionBuilder().build().submitForReview();
        assertThatThrownBy(pendingReview::submitForReview)
            .isInstanceOf(IllegalStateException.class);
    }

    // ---- ContentVersion.approve ----

    @Test
    @DisplayName("approve transitions PENDING_REVIEW → APPROVED")
    void shouldTransitionToApproved() {
        ContentVersion v = baseVersionBuilder().build()
            .submitForReview()
            .approve("approver-1", NOW);
        assertThat(v.getStatus()).isEqualTo(ContentVersionStatus.APPROVED);
        assertThat(v.isApproved()).isTrue();
        assertThat(v.getApprovedBy()).isEqualTo("approver-1");
        assertThat(v.getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("approve throws if not in PENDING_REVIEW")
    void shouldThrowIfApprovedCalledOnDraft() {
        ContentVersion draft = baseVersionBuilder().build();
        assertThatThrownBy(() -> draft.approve("approver-1", NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve throws for null approvedBy")
    void shouldThrowIfApproveHasNullApprover() {
        ContentVersion pending = baseVersionBuilder().build().submitForReview();
        assertThatThrownBy(() -> pending.approve(null, NOW))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("approve throws for blank approvedBy")
    void shouldThrowIfApproveHasBlankApprover() {
        ContentVersion pending = baseVersionBuilder().build().submitForReview();
        assertThatThrownBy(() -> pending.approve("  ", NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("approve throws for null approvedAt")
    void shouldThrowIfApproveHasNullTimestamp() {
        ContentVersion pending = baseVersionBuilder().build().submitForReview();
        assertThatThrownBy(() -> pending.approve("approver-1", null))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- ContentVersion.archive ----

    @Test
    @DisplayName("archive transitions APPROVED → ARCHIVED")
    void shouldTransitionToArchived() {
        ContentVersion archived = baseVersionBuilder().build()
            .submitForReview()
            .approve("approver-1", NOW)
            .archive();
        assertThat(archived.getStatus()).isEqualTo(ContentVersionStatus.ARCHIVED);
    }

    @Test
    @DisplayName("archive throws if not in APPROVED")
    void shouldThrowIfArchiveCalledOnDraft() {
        ContentVersion draft = baseVersionBuilder().build();
        assertThatThrownBy(draft::archive)
            .isInstanceOf(IllegalStateException.class);
    }
}
