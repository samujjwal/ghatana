package com.ghatana.digitalmarketing.domain.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BudgetReallocationProposal}.
 *
 * @doc.type test
 * @doc.purpose Validates BudgetReallocationProposal domain model behavior (P3-004)
 * @doc.layer product
 */
@DisplayName("BudgetReallocationProposal Tests")
class BudgetReallocationProposalTest {

    @Test
    @DisplayName("Should build valid budget reallocation proposal")
    void shouldBuildValidBudgetReallocationProposal() {
        Instant now = Instant.now();
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI potential"),
            new BudgetReallocationProposal.BudgetAdjustment("campaign-2", "SOCIAL", 3000.0, 2000.0, -1000.0, "Diminishing returns")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget to high-performing campaigns")
            .description("Shift budget from underperforming social to high-ROI paid search")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Based on recent ROI analysis showing 20% higher ROI for paid search")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(proposal.getId()).isEqualTo("reallocate-1");
        assertThat(proposal.getTenantId()).isEqualTo("tenant-1");
        assertThat(proposal.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(proposal.getBudgetRecommendationId()).isEqualTo("budget-rec-1");
        assertThat(proposal.getAdjustments()).hasSize(2);
        assertThat(proposal.getTotalReallocatedAmount()).isEqualTo(0.0);
        assertThat(proposal.getStatus()).isEqualTo(BudgetReallocationStatus.PENDING);
        assertThat(proposal.isProcessed()).isFalse();
    }

    @Test
    @DisplayName("Should approve budget reallocation proposal")
    void shouldApproveBudgetReallocationProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        BudgetReallocationProposal approved = proposal.approve("user-123");

        assertThat(approved.getStatus()).isEqualTo(BudgetReallocationStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("user-123");
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(approved.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should reject budget reallocation proposal")
    void shouldRejectBudgetReallocationProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        BudgetReallocationProposal rejected = proposal.reject("Strategic alignment concerns");

        assertThat(rejected.getStatus()).isEqualTo(BudgetReallocationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Strategic alignment concerns");
        assertThat(rejected.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should execute budget reallocation proposal")
    void shouldExecuteBudgetReallocationProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.APPROVED)
            .approvedBy("user-123")
            .approvedAt(Instant.now().minusSeconds(3600))
            .createdAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        BudgetReallocationProposal executed = proposal.execute();

        assertThat(executed.getStatus()).isEqualTo(BudgetReallocationStatus.EXECUTED);
        assertThat(executed.getExecutedAt()).isNotNull();
        assertThat(executed.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should expire budget reallocation proposal")
    void shouldExpireBudgetReallocationProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        BudgetReallocationProposal expired = proposal.expire();

        assertThat(expired.getStatus()).isEqualTo(BudgetReallocationStatus.EXPIRED);
        assertThat(expired.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should throw when approving non-pending proposal")
    void shouldThrowWhenApprovingNonPendingProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.APPROVED)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        assertThatThrownBy(() -> proposal.approve("user-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only PENDING proposals can be approved");
    }

    @Test
    @DisplayName("Should throw when executing non-approved proposal")
    void shouldThrowWhenExecutingNonApprovedProposal() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        assertThatThrownBy(() -> proposal.execute())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only APPROVED proposals can be executed");
    }

    @Test
    @DisplayName("Should throw when adjustments list is empty")
    void shouldThrowWhenAdjustmentsListIsEmpty() {
        assertThatThrownBy(() -> BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(List.of())
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("adjustments must not be empty");
    }

    @Test
    @DisplayName("Should throw when total reallocated amount is negative")
    void shouldThrowWhenTotalReallocatedAmountIsNegative() {
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        assertThatThrownBy(() -> BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(-100.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("totalReallocatedAmount must be non-negative");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        Instant now = Instant.now();
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        BudgetReallocationProposal original = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        BudgetReallocationProposal modified = original.toBuilder()
            .title("Modified title")
            .totalReallocatedAmount(1000.0)
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getTitle()).isEqualTo("Modified title");
        assertThat(modified.getTotalReallocatedAmount()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        Instant now = Instant.now();
        List<BudgetReallocationProposal.BudgetAdjustment> adjustments1 = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-1", "PAID_SEARCH", 5000.0, 6000.0, 1000.0, "High ROI")
        );

        List<BudgetReallocationProposal.BudgetAdjustment> adjustments2 = List.of(
            new BudgetReallocationProposal.BudgetAdjustment("campaign-2", "SOCIAL", 3000.0, 2000.0, -1000.0, "Low ROI")
        );

        BudgetReallocationProposal proposal1 = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .budgetRecommendationId("budget-rec-1")
            .title("Reallocate budget")
            .description("Description")
            .adjustments(adjustments1)
            .totalReallocatedAmount(0.0)
            .rationale("Rationale")
            .status(BudgetReallocationStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        BudgetReallocationProposal proposal2 = BudgetReallocationProposal.builder()
            .id("reallocate-1")
            .tenantId("tenant-2")
            .workspaceId("workspace-2")
            .budgetRecommendationId("budget-rec-2")
            .title("Different title")
            .description("Different description")
            .adjustments(adjustments2)
            .totalReallocatedAmount(500.0)
            .rationale("Different rationale")
            .status(BudgetReallocationStatus.APPROVED)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(proposal1).isEqualTo(proposal2);
        assertThat(proposal1.hashCode()).isEqualTo(proposal2.hashCode());
    }
}
