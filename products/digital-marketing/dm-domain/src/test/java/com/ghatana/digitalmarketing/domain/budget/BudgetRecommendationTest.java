package com.ghatana.digitalmarketing.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BudgetRecommendation} domain aggregate.
 */
@DisplayName("BudgetRecommendation Domain Tests")
class BudgetRecommendationTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-1");

    private static BudgetRecommendation draft() {
        return BudgetRecommendation.builder()
            .recommendationId("rec-1")
            .workspaceId(WS)
            .strategyId("strat-1")
            .totalMonthlyCap(3000.0)
            .changeThresholdPct(10.0)
            .channelAllocations(List.of(
                new BudgetChannelAllocation("GOOGLE_SEARCH", 2100.0, 70.0, "Primary lead channel"),
                new BudgetChannelAllocation("LANDING_PAGE", 600.0, 20.0, "Conversion page hosting"),
                new BudgetChannelAllocation("EMAIL_FOLLOW_UP", 300.0, 10.0, "Follow-up sequence")))
            .rationale("Based on $3k/month budget signal from intake")
            .assumptions("30-day period, local service market")
            .modelVersion("v1.0")
            .status(BudgetRecommendationStatus.DRAFT)
            .generatedAt(Instant.parse("2026-05-02T10:00:00Z"))
            .generatedBy("system")
            .build();
    }

    @Test
    @DisplayName("builder creates valid draft recommendation")
    void builderCreatesDraft() {
        BudgetRecommendation rec = draft();
        assertThat(rec.getRecommendationId()).isEqualTo("rec-1");
        assertThat(rec.getStatus()).isEqualTo(BudgetRecommendationStatus.DRAFT);
        assertThat(rec.getTotalMonthlyCap()).isEqualTo(3000.0);
        assertThat(rec.getChannelAllocations()).hasSize(3);
        assertThat(rec.getApprovedAt()).isNull();
        assertThat(rec.getApprovedBy()).isNull();
    }

    @Test
    @DisplayName("submitForApproval transitions DRAFT to PENDING_APPROVAL")
    void submitForApproval() {
        BudgetRecommendation pending = draft().submitForApproval();
        assertThat(pending.getStatus()).isEqualTo(BudgetRecommendationStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("submitForApproval from non-DRAFT throws")
    void submitFromNonDraftThrows() {
        BudgetRecommendation pending = draft().submitForApproval();
        assertThatThrownBy(pending::submitForApproval)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve transitions PENDING_APPROVAL to APPROVED with metadata")
    void approveSetsMetadata() {
        Instant approvedAt = Instant.parse("2026-05-02T11:00:00Z");
        BudgetRecommendation approved = draft().submitForApproval().approve("owner-1", approvedAt);
        assertThat(approved.getStatus()).isEqualTo(BudgetRecommendationStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(approved.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("approve from DRAFT throws")
    void approveFromDraftThrows() {
        assertThatThrownBy(() -> draft().approve("owner-1", Instant.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reject transitions PENDING_APPROVAL to REJECTED")
    void rejectFromPendingApproval() {
        BudgetRecommendation rejected = draft().submitForApproval().reject();
        assertThat(rejected.getStatus()).isEqualTo(BudgetRecommendationStatus.REJECTED);
    }

    @Test
    @DisplayName("reject from DRAFT throws")
    void rejectFromDraftThrows() {
        assertThatThrownBy(() -> draft().reject())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("builder rejects blank recommendationId")
    void rejectsBlankId() {
        assertThatThrownBy(() -> draft().toBuilder().recommendationId("").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("builder rejects negative totalMonthlyCap")
    void rejectsNegativeCap() {
        assertThatThrownBy(() -> draft().toBuilder().totalMonthlyCap(-1.0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("builder rejects empty channelAllocations")
    void rejectsEmptyAllocations() {
        assertThatThrownBy(() -> draft().toBuilder().channelAllocations(List.of()).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("channel allocation rejects negative dailyCap")
    void allocationRejectsNegativeDailyCap() {
        assertThatThrownBy(() -> new BudgetChannelAllocation("GOOGLE_SEARCH", 100.0, -1.0, "ok"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changeThresholdPct outside 0-100 throws")
    void changeThresholdOutOfRangeThrows() {
        assertThatThrownBy(() -> draft().toBuilder().changeThresholdPct(101.0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
