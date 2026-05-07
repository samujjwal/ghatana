package com.ghatana.digitalmarketing.domain.optimization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ExperimentSuggestion}.
 *
 * @doc.type test
 * @doc.purpose Validates ExperimentSuggestion domain model behavior (P3-004)
 * @doc.layer product
 */
@DisplayName("ExperimentSuggestion Tests")
class ExperimentSuggestionTest {

    @Test
    @DisplayName("Should build valid experiment suggestion")
    void shouldBuildValidExperimentSuggestion() {
        Instant now = Instant.now();
        ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creative variations")
            .description("A/B test different ad creatives to improve CTR")
            .controlVariant(Map.of("creativeId", "creative-a", "headline", "Original"))
            .treatmentVariant(Map.of("creativeId", "creative-b", "headline", "New"))
            .hypothesis("New creative will increase CTR by 15%")
            .successMetric("click_through_rate")
            .minimumDetectableEffect(0.15)
            .rationale("Based on historical creative performance analysis")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(suggestion.getId()).isEqualTo("exp-1");
        assertThat(suggestion.getTenantId()).isEqualTo("tenant-1");
        assertThat(suggestion.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(suggestion.getCampaignId()).isEqualTo("campaign-1");
        assertThat(suggestion.getExperimentType()).isEqualTo(ExperimentType.CREATIVE_TEST);
        assertThat(suggestion.getHypothesis()).isEqualTo("New creative will increase CTR by 15%");
        assertThat(suggestion.getMinimumDetectableEffect()).isEqualTo(0.15);
        assertThat(suggestion.getStatus()).isEqualTo(ExperimentSuggestionStatus.PENDING);
        assertThat(suggestion.isProcessed()).isFalse();
    }

    @Test
    @DisplayName("Should approve experiment suggestion")
    void shouldApproveExperimentSuggestion() {
        ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        ExperimentSuggestion approved = suggestion.approve("experiment-123", "user-456");

        assertThat(approved.getStatus()).isEqualTo(ExperimentSuggestionStatus.APPROVED);
        assertThat(approved.getExperimentId()).isEqualTo("experiment-123");
        assertThat(approved.getApprovedBy()).isEqualTo("user-456");
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(approved.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should reject experiment suggestion")
    void shouldRejectExperimentSuggestion() {
        ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        ExperimentSuggestion rejected = suggestion.reject("Insufficient traffic for statistical significance");

        assertThat(rejected.getStatus()).isEqualTo(ExperimentSuggestionStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Insufficient traffic for statistical significance");
        assertThat(rejected.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should expire experiment suggestion")
    void shouldExpireExperimentSuggestion() {
        ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        ExperimentSuggestion expired = suggestion.expire();

        assertThat(expired.getStatus()).isEqualTo(ExperimentSuggestionStatus.EXPIRED);
        assertThat(expired.isProcessed()).isTrue();
    }

    @Test
    @DisplayName("Should throw when approving non-pending suggestion")
    void shouldThrowWhenApprovingNonPendingSuggestion() {
        ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.APPROVED)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build();

        assertThatThrownBy(() -> suggestion.approve("experiment-123", "user-456"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only PENDING suggestions can be approved");
    }

    @Test
    @DisplayName("Should throw when minimum detectable effect is not positive")
    void shouldThrowWhenMinimumDetectableEffectIsNotPositive() {
        assertThatThrownBy(() -> ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.0)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(86400))
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minimumDetectableEffect must be positive");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        Instant now = Instant.now();
        ExperimentSuggestion original = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        ExperimentSuggestion modified = original.toBuilder()
            .title("Modified title")
            .minimumDetectableEffect(0.20)
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getTitle()).isEqualTo("Modified title");
        assertThat(modified.getMinimumDetectableEffect()).isEqualTo(0.20);
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCodeBasedOnId() {
        Instant now = Instant.now();
        ExperimentSuggestion suggestion1 = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .campaignId("campaign-1")
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test creatives")
            .description("Description")
            .controlVariant(Map.of("creativeId", "creative-a"))
            .treatmentVariant(Map.of("creativeId", "creative-b"))
            .hypothesis("Hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .rationale("Rationale")
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        ExperimentSuggestion suggestion2 = ExperimentSuggestion.builder()
            .id("exp-1")
            .tenantId("tenant-2")
            .workspaceId("workspace-2")
            .campaignId("campaign-2")
            .experimentType(ExperimentType.AUDIENCE_TEST)
            .title("Different title")
            .description("Different description")
            .controlVariant(Map.of("audienceId", "audience-a"))
            .treatmentVariant(Map.of("audienceId", "audience-b"))
            .hypothesis("Different hypothesis")
            .successMetric("conversion_rate")
            .minimumDetectableEffect(0.25)
            .rationale("Different rationale")
            .status(ExperimentSuggestionStatus.APPROVED)
            .createdAt(now)
            .expiresAt(now.plusSeconds(86400))
            .build();

        assertThat(suggestion1).isEqualTo(suggestion2);
        assertThat(suggestion1.hashCode()).isEqualTo(suggestion2.hashCode());
    }
}
