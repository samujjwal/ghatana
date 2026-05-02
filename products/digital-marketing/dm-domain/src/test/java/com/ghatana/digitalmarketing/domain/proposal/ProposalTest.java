package com.ghatana.digitalmarketing.domain.proposal;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Proposal}, {@link ProposalDeliverable}, {@link PricingOption},
 * and {@link ProposalStatus}.
 */
@DisplayName("Proposal Domain Tests")
class ProposalTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-1");

    private static ProposalDeliverable deliverable() {
        return new ProposalDeliverable("GOOGLE_SEARCH_CAMPAIGN", "Search campaign", 14, "campaign", 1);
    }

    private static PricingOption pricing() {
        return new PricingOption("MONTHLY_RETAINER", 2500.00, "USD", "Full management retainer");
    }

    private static Proposal draft() {
        return Proposal.builder()
            .proposalId("prop-1")
            .workspaceId(WS)
            .strategyId("strat-1")
            .templateId("tmpl-1")
            .templateVersion("v1.0")
            .deliverables(List.of(deliverable()))
            .pricingOptions(List.of(pricing()))
            .assumptions("Standard 30-day period")
            .timeline("30-day onboarding")
            .rationale("Generated from strategy strat-1")
            .disclaimer("Results not guaranteed")
            .exclusions("Creative production excluded")
            .measurementPlan("Monthly KPI reviews")
            .modelVersion("v1.0")
            .status(ProposalStatus.DRAFT)
            .generatedAt(Instant.parse("2026-06-01T10:00:00Z"))
            .generatedBy("system")
            .build();
    }

    @Test
    @DisplayName("builder creates valid draft proposal")
    void builderCreatesDraft() {
        Proposal p = draft();
        assertThat(p.getProposalId()).isEqualTo("prop-1");
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.DRAFT);
        assertThat(p.getDeliverables()).hasSize(1);
        assertThat(p.getPricingOptions()).hasSize(1);
        assertThat(p.getReviewedAt()).isNull();
        assertThat(p.getApprovedBy()).isNull();
    }

    @Test
    @DisplayName("submitForReview transitions DRAFT to PENDING_REVIEW")
    void submitForReview() {
        Proposal p = draft().submitForReview();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("submitForReview from non-DRAFT throws IllegalStateException")
    void submitFromNonDraftThrows() {
        Proposal pending = draft().submitForReview();
        assertThatThrownBy(pending::submitForReview)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING_REVIEW");
    }

    @Test
    @DisplayName("approve transitions PENDING_REVIEW to APPROVED with metadata")
    void approveSetsMetadata() {
        Instant reviewedAt = Instant.parse("2026-06-01T12:00:00Z");
        Proposal approved = draft().submitForReview().approve("owner-1", reviewedAt);
        assertThat(approved.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(approved.getReviewedAt()).isEqualTo(reviewedAt);
    }

    @Test
    @DisplayName("approve from DRAFT throws IllegalStateException")
    void approveFromDraftThrows() {
        assertThatThrownBy(() -> draft().approve("owner-1", Instant.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("approve from APPROVED throws IllegalStateException")
    void approveFromApprovedThrows() {
        Proposal approved = draft().submitForReview().approve("owner-1", Instant.now());
        assertThatThrownBy(() -> approved.approve("owner-2", Instant.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve with blank approvedBy throws IllegalArgumentException")
    void approveWithBlankApproverThrows() {
        Proposal pending = draft().submitForReview();
        assertThatThrownBy(() -> pending.approve("  ", Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approvedBy");
    }

    @Test
    @DisplayName("approve with null approvedBy throws NullPointerException")
    void approveWithNullApproverThrows() {
        Proposal pending = draft().submitForReview();
        assertThatThrownBy(() -> pending.approve(null, Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("approve with null reviewedAt throws NullPointerException")
    void approveWithNullReviewedAtThrows() {
        Proposal pending = draft().submitForReview();
        assertThatThrownBy(() -> pending.approve("owner-1", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder rejects blank proposalId")
    void builderRejectsBlankProposalId() {
        assertThatThrownBy(() -> draft().toBuilder().proposalId("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("proposalId");
    }

    @Test
    @DisplayName("builder rejects null workspaceId")
    void builderRejectsNullWorkspaceId() {
        assertThatThrownBy(() -> draft().toBuilder().workspaceId(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("builder rejects empty deliverables list")
    void builderRejectsEmptyDeliverables() {
        assertThatThrownBy(() -> draft().toBuilder().deliverables(List.of()).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deliverables");
    }

    @Test
    @DisplayName("builder rejects empty pricingOptions list")
    void builderRejectsEmptyPricingOptions() {
        assertThatThrownBy(() -> draft().toBuilder().pricingOptions(List.of()).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pricingOptions");
    }

    @Test
    @DisplayName("toBuilder creates mutable copy with same values")
    void toBuilderPreservesValues() {
        Proposal original = draft();
        Proposal copy = original.toBuilder().build();
        assertThat(copy.getProposalId()).isEqualTo(original.getProposalId());
        assertThat(copy.getStrategyId()).isEqualTo(original.getStrategyId());
        assertThat(copy.getStatus()).isEqualTo(original.getStatus());
    }

    // ---- ProposalDeliverable tests ----

    @Test
    @DisplayName("ProposalDeliverable rejects zero timelineDays")
    void deliverableRejectsZeroTimeline() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", "desc", 0, "unit", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timelineDays");
    }

    @Test
    @DisplayName("ProposalDeliverable rejects zero quantity")
    void deliverableRejectsZeroQuantity() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", "desc", 7, "unit", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("ProposalDeliverable rejects blank deliverableType")
    void deliverableRejectsBlankType() {
        assertThatThrownBy(() -> new ProposalDeliverable("", "desc", 7, "unit", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deliverableType");
    }

    // ---- PricingOption tests ----

    @Test
    @DisplayName("PricingOption rejects negative amount")
    void pricingRejectsNegativeAmount() {
        assertThatThrownBy(() -> new PricingOption("TYPE", -1.0, "USD", "desc"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("PricingOption rejects blank pricingType")
    void pricingRejectsBlankType() {
        assertThatThrownBy(() -> new PricingOption("", 100.0, "USD", "desc"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pricingType");
    }

    @Test
    @DisplayName("PricingOption allows zero amount")
    void pricingAllowsZeroAmount() {
        PricingOption p = new PricingOption("FREE", 0.0, "USD", "free tier");
        assertThat(p.amount()).isEqualTo(0.0);
    }

    // ---- ProposalStatus tests ----

    @Test
    @DisplayName("ProposalStatus has exactly three values in order")
    void proposalStatusValues() {
        ProposalStatus[] values = ProposalStatus.values();
        assertThat(values).containsExactly(
            ProposalStatus.DRAFT,
            ProposalStatus.PENDING_REVIEW,
            ProposalStatus.APPROVED
        );
    }

    // ---- Proposal builder blank validation tests ----

    @Test
    @DisplayName("builder rejects blank strategyId")
    void builderRejectsBlankStrategyId() {
        assertThatThrownBy(() -> draft().toBuilder().strategyId("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("strategyId");
    }

    @Test
    @DisplayName("builder rejects blank templateId")
    void builderRejectsBlankTemplateId() {
        assertThatThrownBy(() -> draft().toBuilder().templateId("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateId");
    }

    @Test
    @DisplayName("builder rejects blank templateVersion")
    void builderRejectsBlankTemplateVersion() {
        assertThatThrownBy(() -> draft().toBuilder().templateVersion("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateVersion");
    }

    @Test
    @DisplayName("builder rejects blank modelVersion")
    void builderRejectsBlankModelVersion() {
        assertThatThrownBy(() -> draft().toBuilder().modelVersion("").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("modelVersion");
    }

    // ---- ProposalDeliverable null/blank tests ----

    @Test
    @DisplayName("ProposalDeliverable rejects null deliverableType")
    void deliverableRejectsNullType() {
        assertThatThrownBy(() -> new ProposalDeliverable(null, "desc", 7, "unit", 1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ProposalDeliverable rejects blank description")
    void deliverableRejectsBlankDescription() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", "", 7, "unit", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("description");
    }

    @Test
    @DisplayName("ProposalDeliverable rejects blank unit")
    void deliverableRejectsBlankUnit() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", "desc", 7, "", 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unit");
    }

    @Test
    @DisplayName("ProposalDeliverable rejects null description")
    void deliverableRejectsNullDescription() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", null, 7, "unit", 1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ProposalDeliverable rejects null unit")
    void deliverableRejectsNullUnit() {
        assertThatThrownBy(() -> new ProposalDeliverable("TYPE", "desc", 7, null, 1))
            .isInstanceOf(NullPointerException.class);
    }

    // ---- PricingOption null/blank tests ----

    @Test
    @DisplayName("PricingOption rejects null pricingType")
    void pricingRejectsNullType() {
        assertThatThrownBy(() -> new PricingOption(null, 100.0, "USD", "desc"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("PricingOption rejects blank currency")
    void pricingRejectsBlankCurrency() {
        assertThatThrownBy(() -> new PricingOption("TYPE", 100.0, "", "desc"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    @DisplayName("PricingOption rejects blank description")
    void pricingRejectsBlankDescription() {
        assertThatThrownBy(() -> new PricingOption("TYPE", 100.0, "USD", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("description");
    }

    @Test
    @DisplayName("PricingOption rejects null currency")
    void pricingRejectsNullCurrency() {
        assertThatThrownBy(() -> new PricingOption("TYPE", 100.0, null, "desc"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("PricingOption rejects null description")
    void pricingRejectsNullDescription() {
        assertThatThrownBy(() -> new PricingOption("TYPE", 100.0, "USD", null))
            .isInstanceOf(NullPointerException.class);
    }
}
