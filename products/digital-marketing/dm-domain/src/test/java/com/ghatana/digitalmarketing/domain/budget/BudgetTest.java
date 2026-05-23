package com.ghatana.digitalmarketing.domain.budget;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @doc.type class
 * @doc.purpose Validates campaign budget enforcement with spend gates
 * @doc.layer product
 * @doc.pattern BudgetTest
 */
@DisplayName("dm-008: Campaign Budget Enforcement Tests")
class BudgetTest {

    private Budget validDraft() {
        return Budget.builder()
            .id("bud-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .campaignId("camp-1")
            .allocatedAmount(10000.0)
            .spentAmount(0.0)
            .currency("USD")
            .status(BudgetStatus.DRAFT)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("rejects null required fields")
    void shouldRejectNulls() {
        assertThatNullPointerException()
            .isThrownBy(() -> Budget.builder()
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .allocatedAmount(100)
                .currency("USD")
                .status(BudgetStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Budget.builder()
                .id("")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .allocatedAmount(100)
                .currency("USD")
                .status(BudgetStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("rejects negative allocated amount")
    void shouldRejectNegativeAllocated() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Budget.builder()
                .id("bud-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .campaignId("camp-1")
                .allocatedAmount(-1)
                .currency("USD")
                .status(BudgetStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("getRemainingAmount computes correctly")
    void shouldComputeRemaining() {
        Budget budget = validDraft().toBuilder()
            .allocatedAmount(1000.0)
            .spentAmount(300.0)
            .build();
        assertThat(budget.getRemainingAmount()).isEqualTo(700.0);
    }

    @Test
    @DisplayName("approve transitions DRAFT to APPROVED")
    void shouldApproveDraft() {
        Budget approved = validDraft().approve();
        assertThat(approved.getStatus()).isEqualTo(BudgetStatus.APPROVED);
    }

    @Test
    @DisplayName("approve throws when not in DRAFT status")
    void shouldRejectApprovalFromNonDraft() {
        Budget approved = validDraft().approve();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(approved::approve);
    }

    @Test
    @DisplayName("isApprovedAndSolvent returns true when APPROVED with remaining")
    void shouldBeSolventWhenApprovedWithRemaining() {
        Budget approved = validDraft().approve();
        assertThat(approved.isApprovedAndSolvent()).isTrue();
    }

    @Test
    @DisplayName("isApprovedAndSolvent returns false when DRAFT")
    void shouldNotBeSolventWhenDraft() {
        assertThat(validDraft().isApprovedAndSolvent()).isFalse();
    }

    @Test
    @DisplayName("recordSpend deducts amount and keeps APPROVED when partial")
    void shouldRecordPartialSpend() {
        Budget approved = validDraft().approve();
        Budget afterSpend = approved.recordSpend(1500.0);

        assertThat(afterSpend.getSpentAmount()).isEqualTo(1500.0);
        assertThat(afterSpend.getStatus()).isEqualTo(BudgetStatus.APPROVED);
    }

    @Test
    @DisplayName("recordSpend transitions to EXHAUSTED when fully spent")
    void shouldTransitionToExhaustedWhenFull() {
        Budget approved = validDraft().approve();
        Budget exhausted = approved.recordSpend(10000.0);

        assertThat(exhausted.getStatus()).isEqualTo(BudgetStatus.EXHAUSTED);
        assertThat(exhausted.getRemainingAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("recordSpend throws when amount exceeds remaining budget")
    void shouldRejectOverspend() {
        Budget approved = validDraft().approve();
        assertThatIllegalArgumentException()
            .isThrownBy(() -> approved.recordSpend(10001.0));
    }

    @Test
    @DisplayName("recordSpend throws when not APPROVED")
    void shouldRejectSpendOnNonApproved() {
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> validDraft().recordSpend(100.0));
    }

    @Test
    @DisplayName("cancel transitions budget to CANCELLED")
    void shouldCancelBudget() {
        Budget cancelled = validDraft().cancel();
        assertThat(cancelled.getStatus()).isEqualTo(BudgetStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel throws when EXHAUSTED")
    void shouldRejectCancelWhenExhausted() {
        Budget approved = validDraft().approve();
        Budget exhausted = approved.recordSpend(10000.0);
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(exhausted::cancel);
    }

    @Test
    @DisplayName("cancel throws when already CANCELLED")
    void shouldRejectCancelWhenAlreadyCancelled() {
        Budget cancelled = validDraft().cancel();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(cancelled::cancel);
    }

    @Test
    @DisplayName("equals and hashCode based on id and workspaceId")
    void shouldUseIdAndWorkspaceForEquality() {
        Budget a = validDraft();
        Budget b = validDraft();
        // same id: equal
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        // self-reference
        assertThat(a).isEqualTo(a);
        // null and wrong type
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
        // different id: not equal
        Budget different = validDraft().toBuilder().id("bud-other").build();
        assertThat(a).isNotEqualTo(different);
        assertThat(a.hashCode()).isNotEqualTo(different.hashCode());
    }

    @Test
    @DisplayName("toBuilder produces equivalent budget")
    void shouldRoundTripViaBuilder() {
        Budget original = validDraft();
        Budget copy = original.toBuilder().build();
        assertThat(copy).isEqualTo(original);
        assertThat(copy.getAllocatedAmount()).isEqualTo(original.getAllocatedAmount());
    }

    @Test @DisplayName("blank currency throws")
    void shouldRejectBlankCurrency() {
        assertThatIllegalArgumentException().isThrownBy(() -> validDraft().toBuilder().currency("").build());
    }

    @Test @DisplayName("negative spentAmount throws")
    void shouldRejectNegativeSpentAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> validDraft().toBuilder().spentAmount(-1.0).build());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        Budget b = validDraft();
        assertThat(b.getCampaignId()).isNotNull();
        assertThat(b.getCreatedBy()).isNotNull();
        assertThat(b.toString()).isNotNull();
    }
}
