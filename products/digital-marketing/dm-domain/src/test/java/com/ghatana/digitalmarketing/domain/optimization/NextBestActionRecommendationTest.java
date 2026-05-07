package com.ghatana.digitalmarketing.domain.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link NextBestActionRecommendation}.
 *
 * @doc.type test
 * @doc.purpose Validates NextBestActionRecommendation domain model behavior (P3-004)
 * @doc.layer product
 */
@DisplayName("NextBestActionRecommendation Tests")
class NextBestActionRecommendationTest {

    @Test
    @DisplayName("Should build valid recommendation")
    void shouldBuildValidRecommendation() {
        Instant now = Instant.now();
        NextBestActionRecommendation recommendation = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget for underperforming campaign")
            .description("Campaign shows high potential with 20% ROI increase potential")
            .parameters(Map.of("budgetIncrease", 1000.0, "reason", "high CTR"))
            .confidenceScore(0.85)
            .rationale("Based on recent performance trends")
            .status(NextBestActionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(recommendation.getId()).isEqualTo("nba-1");
        assertThat(recommendation.getTenantId()).isEqualTo("tenant-1");
        assertThat(recommendation.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(recommendation.getCampaignId()).isEqualTo("campaign-1");
        assertThat(recommendation.getActionType()).isEqualTo(NextBestActionType.INCREASE_BUDGET);
        assertThat(recommendation.getConfidenceScore()).isEqualTo(0.85);
        assertThat(recommendation.getStatus()).isEqualTo(NextBestActionStatus.PENDING);
        assertThat(recommendation.isProcessed()).isFalse();
    }

    @Test
    @DisplayName("Should approve recommendation")
    void shouldApproveRecommendation() {
        NextBestActionRecommendation recommendation = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        NextBestActionRecommendation approved = recommendation.approve("user-123");

        assertThat(approved.getStatus()).isEqualTo(NextBestActionStatus.APPROVED);
        assertThat(approved.getExecutedBy()).isEqualTo("user-123");
        assertThat(approved.getProcessedAt()).isNotNull();
        assertThat(approved.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should reject recommendation")
    void shouldRejectRecommendation() {
        NextBestActionRecommendation recommendation = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        NextBestActionRecommendation rejected = recommendation.reject("Budget constraints");

        assertThat(rejected.getStatus()).isEqualTo(NextBestActionStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Budget constraints");
        assertThat(rejected.getProcessedAt()).isNotNull();
        assertThat(rejected.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should expire recommendation")
    void shouldExpireRecommendation() {
        NextBestActionRecommendation recommendation = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        NextBestActionRecommendation expired = recommendation.expire();

        assertThat(expired.getStatus()).isEqualTo(NextBestActionStatus.EXPIRED);
        assertThat(expired.getProcessedAt()).isNotNull();
        assertThat(expired.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should throw when approving non-pending recommendation")
    void shouldThrowWhenApprovingNonPendingRecommendation() {
        NextBestActionRecommendation recommendation = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.APPROVED)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        assertThatThrownBy(() -> recommendation.approve("user-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only PENDING recommendations can be approved");
    }

    @Test
    @DisplayName("Should throw when confidence score is out of range")
    void shouldThrowWhenConfidenceScoreIsOutOfRange() {
        assertThatThrownBy(() -> NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(1.5)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confidenceScore must be between 0.0 and 1.0");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        Instant now = Instant.now();
        NextBestActionRecommendation original = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        NextBestActionRecommendation modified = original.toBuilder()
            .title("Modified title")
            .confidenceScore(0.90)
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getTitle()).isEqualTo("Modified title");
        assertThat(modified.getConfidenceScore()).isEqualTo(0.90);
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        Instant now = Instant.now();
        NextBestActionRecommendation rec1 = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .actionType(NextBestActionType.INCREASE_BUDGET)
            .title("Increase budget")
            .description("Description")
            .parameters(Map.of())
            .confidenceScore(0.85)
            .rationale("Rationale")
            .status(NextBestActionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        NextBestActionRecommendation rec2 = NextBestActionRecommendation.builder()
            .id("nba-1")
            .tenantId("tenant-2")
            .workspaceId("workspace-2")
            .campaignId("campaign-2")
            .actionType(NextBestActionType.DECREASE_BUDGET)
            .title("Different title")
            .description("Different description")
            .parameters(Map.of())
            .confidenceScore(0.50)
            .rationale("Different rationale")
            .status(NextBestActionStatus.APPROVED)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(rec1).isEqualTo(rec2);
        assertThat(rec1.hashCode()).isEqualTo(rec2.hashCode());
    }
}
