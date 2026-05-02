package com.ghatana.digitalmarketing.domain.sow;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SowClause}, {@link SowRiskFlag}, and {@link SowDraft}.
 */
@DisplayName("SOW Domain Tests")
class SowDraftTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-1");

    private static SowClause approvedClause() {
        return new SowClause(
                "clause-1", "SCOPE", "v1.0",
                "The service provider agrees to deliver the described services.",
                "Legal Team", "Senior Counsel",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED);
    }

    private static SowClause draftClause() {
        return new SowClause(
                "clause-2", "PAYMENT", "v1.0",
                "Payment is due within 30 days of invoice.",
                "Finance Team", "CFO",
                LocalDate.of(2026, 1, 1), SowClauseStatus.DRAFT);
    }

    private static SowDraft baseDraft() {
        return SowDraft.builder()
                .sowId("sow-1")
                .workspaceId(WS)
                .proposalId("prop-1")
                .templateVersion("v1.0")
                .selectedClauses(List.of(approvedClause()))
                .riskFlags(List.of())
                .assumptions("Standard 30-day campaign")
                .exclusions("Paid media excluded")
                .disclaimer(SowDraft.LEGAL_DISCLAIMER)
                .modelVersion("v1.0")
                .status(SowStatus.DRAFT)
                .createdAt(Instant.parse("2026-06-01T10:00:00Z"))
                .build();
    }

    // ---- SowClause validation ----

    @Test
    @DisplayName("SowClause accepts valid fields")
    void shouldBuildClauseWithValidFields() {
        SowClause clause = approvedClause();
        assertThat(clause.clauseId()).isEqualTo("clause-1");
        assertThat(clause.isApproved()).isTrue();
    }

    @Test
    @DisplayName("SowClause rejects null clauseId")
    void shouldRejectNullClauseId() {
        assertThatThrownBy(() -> new SowClause(
                null, "SCOPE", "v1.0", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank clauseId")
    void shouldRejectBlankClauseId() {
        assertThatThrownBy(() -> new SowClause(
                "  ", "SCOPE", "v1.0", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clauseId");
    }

    @Test
    @DisplayName("SowClause rejects null clauseType")
    void shouldRejectNullClauseType() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", null, "v1.0", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank clauseType")
    void shouldRejectBlankClauseType() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "", "v1.0", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clauseType");
    }

    @Test
    @DisplayName("SowClause rejects null version")
    void shouldRejectNullVersion() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", null, "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank version")
    void shouldRejectBlankVersion() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "  ", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("SowClause rejects null content")
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", null, "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank content")
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    @DisplayName("SowClause rejects null owner")
    void shouldRejectNullOwner() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", null, "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank owner")
    void shouldRejectBlankOwner() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", "  ", "reviewer",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    @DisplayName("SowClause rejects null reviewer")
    void shouldRejectNullReviewer() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", "owner", null,
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects blank reviewer")
    void shouldRejectBlankReviewer() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", "owner", "  ",
                LocalDate.of(2026, 1, 1), SowClauseStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewer");
    }

    @Test
    @DisplayName("SowClause rejects null effectiveDate")
    void shouldRejectNullEffectiveDate() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", "owner", "reviewer",
                null, SowClauseStatus.APPROVED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowClause rejects null status")
    void shouldRejectNullStatus() {
        assertThatThrownBy(() -> new SowClause(
                "c-1", "SCOPE", "v1.0", "content", "owner", "reviewer",
                LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("DRAFT clause returns isApproved=false")
    void draftClauseShouldNotBeApproved() {
        assertThat(draftClause().isApproved()).isFalse();
    }

    // ---- SowRiskFlag validation ----

    @Test
    @DisplayName("SowRiskFlag accepts WARNING severity")
    void shouldBuildRiskFlagWarning() {
        SowRiskFlag flag = new SowRiskFlag(SowRiskType.UNSUPPORTED_GUARANTEE, "risk desc", "WARNING");
        assertThat(flag.flagType()).isEqualTo(SowRiskType.UNSUPPORTED_GUARANTEE);
        assertThat(flag.severity()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("SowRiskFlag accepts BLOCKER severity")
    void shouldBuildRiskFlagBlocker() {
        SowRiskFlag flag = new SowRiskFlag(SowRiskType.MISSING_APPROVAL, "missing approval", "BLOCKER");
        assertThat(flag.severity()).isEqualTo("BLOCKER");
    }

    @Test
    @DisplayName("SowRiskFlag rejects null flagType")
    void shouldRejectNullFlagType() {
        assertThatThrownBy(() -> new SowRiskFlag(null, "desc", "WARNING"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowRiskFlag rejects null description")
    void shouldRejectNullDescription() {
        assertThatThrownBy(() -> new SowRiskFlag(SowRiskType.PRIVACY_ISSUE, null, "WARNING"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowRiskFlag rejects blank description")
    void shouldRejectBlankDescription() {
        assertThatThrownBy(() -> new SowRiskFlag(SowRiskType.PRIVACY_ISSUE, "  ", "WARNING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    @DisplayName("SowRiskFlag rejects null severity")
    void shouldRejectNullSeverity() {
        assertThatThrownBy(() -> new SowRiskFlag(SowRiskType.PRIVACY_ISSUE, "desc", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("SowRiskFlag rejects blank severity")
    void shouldRejectBlankSeverity() {
        assertThatThrownBy(() -> new SowRiskFlag(SowRiskType.PRIVACY_ISSUE, "desc", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity");
    }

    @Test
    @DisplayName("SowRiskFlag rejects invalid severity value")
    void shouldRejectInvalidSeverity() {
        assertThatThrownBy(() -> new SowRiskFlag(SowRiskType.PRIVACY_ISSUE, "desc", "CRITICAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity");
    }

    // ---- SowDraft builder validation ----

    @Test
    @DisplayName("SowDraft builds successfully with valid fields")
    void shouldBuildDraftSuccessfully() {
        SowDraft draft = baseDraft();
        assertThat(draft.getSowId()).isEqualTo("sow-1");
        assertThat(draft.getStatus()).isEqualTo(SowStatus.DRAFT);
        assertThat(draft.getDisclaimer()).isEqualTo(SowDraft.LEGAL_DISCLAIMER);
        assertThat(draft.getSelectedClauses()).hasSize(1);
        assertThat(draft.getRiskFlags()).isEmpty();
        assertThat(draft.getApprovedBy()).isNull();
        assertThat(draft.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("SowDraft rejects blank sowId")
    void shouldRejectBlankSowId() {
        assertThatThrownBy(() -> baseDraft().toBuilder().sowId("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sowId");
    }

    @Test
    @DisplayName("SowDraft rejects blank proposalId")
    void shouldRejectBlankProposalId() {
        assertThatThrownBy(() -> baseDraft().toBuilder().proposalId("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proposalId");
    }

    @Test
    @DisplayName("SowDraft rejects blank templateVersion")
    void shouldRejectBlankTemplateVersion() {
        assertThatThrownBy(() -> baseDraft().toBuilder().templateVersion("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateVersion");
    }

    @Test
    @DisplayName("SowDraft rejects blank modelVersion")
    void shouldRejectBlankModelVersion() {
        assertThatThrownBy(() -> baseDraft().toBuilder().modelVersion("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelVersion");
    }

    @Test
    @DisplayName("SowDraft rejects empty selectedClauses")
    void shouldRejectEmptySelectedClauses() {
        assertThatThrownBy(() -> baseDraft().toBuilder().selectedClauses(List.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selectedClauses");
    }

    @Test
    @DisplayName("SowDraft rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatThrownBy(() -> baseDraft().toBuilder().workspaceId(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    // ---- SowDraft state transitions ----

    @Test
    @DisplayName("submitForReview transitions DRAFT to PENDING_REVIEW")
    void shouldSubmitForReview() {
        SowDraft submitted = baseDraft().submitForReview();
        assertThat(submitted.getStatus()).isEqualTo(SowStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("submitForReview throws IllegalStateException when not in DRAFT")
    void shouldThrowWhenSubmittingNonDraft() {
        SowDraft pending = baseDraft().toBuilder().status(SowStatus.PENDING_REVIEW).build();
        assertThatThrownBy(pending::submitForReview)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_REVIEW");
    }

    @Test
    @DisplayName("approve transitions PENDING_REVIEW to APPROVED")
    void shouldApproveFromPendingReview() {
        SowDraft pending = baseDraft().toBuilder().status(SowStatus.PENDING_REVIEW).build();
        SowDraft approved = pending.approve("reviewer-1", Instant.parse("2026-06-02T12:00:00Z"));
        assertThat(approved.getStatus()).isEqualTo(SowStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("reviewer-1");
        assertThat(approved.getApprovedAt()).isEqualTo(Instant.parse("2026-06-02T12:00:00Z"));
    }

    @Test
    @DisplayName("approve throws IllegalStateException when not in PENDING_REVIEW")
    void shouldThrowWhenApprovingDraft() {
        assertThatThrownBy(() -> baseDraft().approve("reviewer-1", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("approve throws NullPointerException for null approvedBy")
    void shouldThrowWhenApprovedByIsNull() {
        SowDraft pending = baseDraft().toBuilder().status(SowStatus.PENDING_REVIEW).build();
        assertThatThrownBy(() -> pending.approve(null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("approve throws NullPointerException for null approvedAt")
    void shouldThrowWhenApprovedAtIsNull() {
        SowDraft pending = baseDraft().toBuilder().status(SowStatus.PENDING_REVIEW).build();
        assertThatThrownBy(() -> pending.approve("reviewer-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("export transitions APPROVED to EXPORTED")
    void shouldExportApprovedDraft() {
        SowDraft approved = baseDraft().toBuilder()
                .status(SowStatus.APPROVED)
                .approvedBy("reviewer-1")
                .approvedAt(Instant.now())
                .build();
        SowDraft exported = approved.export();
        assertThat(exported.getStatus()).isEqualTo(SowStatus.EXPORTED);
    }

    @Test
    @DisplayName("export throws IllegalStateException when not APPROVED")
    void shouldThrowWhenExportingNonApproved() {
        assertThatThrownBy(() -> baseDraft().export())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("toBuilder preserves all fields")
    void shouldPreserveFieldsViaToBuilder() {
        SowDraft draft = baseDraft();
        SowDraft rebuilt = draft.toBuilder().build();
        assertThat(rebuilt.getSowId()).isEqualTo(draft.getSowId());
        assertThat(rebuilt.getProposalId()).isEqualTo(draft.getProposalId());
        assertThat(rebuilt.getStatus()).isEqualTo(draft.getStatus());
        assertThat(rebuilt.getSelectedClauses()).isEqualTo(draft.getSelectedClauses());
        assertThat(rebuilt.getRiskFlags()).isEqualTo(draft.getRiskFlags());
    }
}
