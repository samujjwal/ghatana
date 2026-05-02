package com.ghatana.digitalmarketing.domain.strategy;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MarketingStrategy domain entity")
class MarketingStrategyTest {

    private static MarketingStrategy.Builder validBuilder() {
        return MarketingStrategy.builder()
            .strategyId("strat-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .status(StrategyStatus.DRAFT)
            .goals(List.of(new StrategyGoal("lead-gen", "Generate 20 leads", "leads", "CRM count")))
            .channelPlans(List.of(new CampaignPlan(
                StrategyChannel.GOOGLE_SEARCH, "Drive search traffic", 1000, List.of("Great offer"), List.of("keyword"))))
            .budgetCap(2000)
            .rationale("Proven channel for local services")
            .assumptions("Client provides landing page content")
            .measurementPlan("Track via UTM + GA4")
            .contentPlan("2 ad variants per week")
            .modelVersion("v1.0")
            .generatedAt(Instant.now())
            .generatedBy("system");
    }

    @Test
    @DisplayName("builds valid strategy with required fields")
    void shouldBuildValidStrategy() {
        MarketingStrategy s = validBuilder().build();

        assertThat(s.getStrategyId()).isEqualTo("strat-1");
        assertThat(s.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(s.getStatus()).isEqualTo(StrategyStatus.DRAFT);
        assertThat(s.getGoals()).hasSize(1);
        assertThat(s.getChannelPlans()).hasSize(1);
        assertThat(s.getBudgetCap()).isEqualTo(2000);
        assertThat(s.getApprovedAt()).isNull();
        assertThat(s.getApprovedBy()).isNull();
    }

    @Test
    @DisplayName("rejects blank strategyId")
    void shouldRejectBlankStrategyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().strategyId("  ").build());
    }

    @Test
    @DisplayName("rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatThrownBy(() -> validBuilder().workspaceId(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null status")
    void shouldRejectNullStatus() {
        assertThatThrownBy(() -> validBuilder().status(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects negative budgetCap")
    void shouldRejectNegativeBudgetCap() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().budgetCap(-1).build());
    }

    @Test
    @DisplayName("rejects blank rationale")
    void shouldRejectBlankRationale() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().rationale("  ").build());
    }

    @Test
    @DisplayName("rejects blank assumptions")
    void shouldRejectBlankAssumptions() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().assumptions("  ").build());
    }

    @Test
    @DisplayName("rejects blank measurementPlan")
    void shouldRejectBlankMeasurementPlan() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().measurementPlan("  ").build());
    }

    @Test
    @DisplayName("rejects blank contentPlan")
    void shouldRejectBlankContentPlan() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().contentPlan("  ").build());
    }

    @Test
    @DisplayName("rejects blank modelVersion")
    void shouldRejectBlankModelVersion() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().modelVersion("  ").build());
    }

    @Test
    @DisplayName("rejects blank generatedBy")
    void shouldRejectBlankGeneratedBy() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().generatedBy("  ").build());
    }

    @Test
    @DisplayName("rejects null generatedAt")
    void shouldRejectNullGeneratedAt() {
        assertThatThrownBy(() -> validBuilder().generatedAt(null).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("submitForApproval transitions DRAFT to PENDING_APPROVAL")
    void shouldSubmitDraftForApproval() {
        MarketingStrategy draft = validBuilder().build();

        MarketingStrategy pending = draft.submitForApproval();

        assertThat(pending.getStatus()).isEqualTo(StrategyStatus.PENDING_APPROVAL);
        assertThat(pending.getStrategyId()).isEqualTo("strat-1");
    }

    @Test
    @DisplayName("submitForApproval throws when strategy is not DRAFT")
    void shouldThrowWhenSubmittingNonDraft() {
        MarketingStrategy pending = validBuilder().status(StrategyStatus.PENDING_APPROVAL).build();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(pending::submitForApproval);
    }

    @Test
    @DisplayName("approve transitions PENDING_APPROVAL to APPROVED")
    void shouldApprovePendingStrategy() {
        Instant approvedAt = Instant.now();
        MarketingStrategy pending = validBuilder().status(StrategyStatus.PENDING_APPROVAL).build();

        MarketingStrategy approved = pending.approve("owner-1", approvedAt);

        assertThat(approved.getStatus()).isEqualTo(StrategyStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo("owner-1");
        assertThat(approved.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("approve throws when strategy is not PENDING_APPROVAL")
    void shouldThrowWhenApprovingNonPending() {
        MarketingStrategy draft = validBuilder().build();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> draft.approve("owner-1", Instant.now()));
    }

    @Test
    @DisplayName("approve throws for blank approvedBy")
    void shouldThrowWhenApprovedByIsBlank() {
        MarketingStrategy pending = validBuilder().status(StrategyStatus.PENDING_APPROVAL).build();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> pending.approve("  ", Instant.now()));
    }

    @Test
    @DisplayName("StrategyGoal rejects blank fields")
    void shouldValidateStrategyGoal() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGoal(" ", "desc", "metric", "method"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGoal("type", " ", "metric", "method"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGoal("type", "desc", " ", "method"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StrategyGoal("type", "desc", "metric", " "));
    }

    @Test
    @DisplayName("CampaignPlan rejects blank objective and negative budget")
    void shouldValidateCampaignPlan() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CampaignPlan(StrategyChannel.GOOGLE_SEARCH, " ", 100, List.of(), List.of()));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CampaignPlan(StrategyChannel.GOOGLE_SEARCH, "obj", -1, List.of(), List.of()));
        assertThatThrownBy(() -> new CampaignPlan(null, "obj", 100, List.of(), List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("goals and channelPlans lists are defensively copied")
    void shouldDefensivelyCopyLists() {
        MarketingStrategy s = validBuilder().build();
        assertThatThrownBy(() -> s.getGoals().add(new StrategyGoal("x", "d", "m", "mm")))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> s.getChannelPlans().add(
            new CampaignPlan(StrategyChannel.LANDING_PAGE, "obj", 0, List.of(), List.of())))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
