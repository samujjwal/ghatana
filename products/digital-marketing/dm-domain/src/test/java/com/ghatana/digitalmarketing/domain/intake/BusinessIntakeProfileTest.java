package com.ghatana.digitalmarketing.domain.intake;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("BusinessIntakeProfile")
class BusinessIntakeProfileTest {

    @Test
    @DisplayName("builds draft intake and detects missing critical inputs")
    void shouldBuildDraftAndDetectMissingInputs() {
        BusinessIntakeProfile profile = BusinessIntakeProfile.builder()
            .intakeId("intake-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .businessName("Acme")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-1")
            .build();

        assertThat(profile.getStatus()).isEqualTo(IntakeStatus.DRAFT);
        assertThat(profile.missingCriticalInputs())
            .contains("growthGoal", "offerSummary", "targetAudience", "primaryGeography", "monthlyBudgetAmount");
    }

    @Test
    @DisplayName("submits intake with summary confidence and unknowns")
    void shouldSubmitIntake() {
        Instant now = Instant.now();
        BusinessIntakeProfile profile = BusinessIntakeProfile.builder()
            .intakeId("intake-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .businessName("Acme")
            .offerSummary("SEO retainers")
            .targetAudience("B2B founders")
            .primaryGeography("US")
            .monthlyBudgetAmount(new BigDecimal("1500"))
            .growthGoal("Generate 30 qualified leads")
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();

        BusinessIntakeProfile submitted = profile.submit(
            "Acme should focus on search intent and conversion pages.",
            0.78,
            List.of("No CAC baseline available"),
            now.plusSeconds(5)
        );

        assertThat(submitted.getStatus()).isEqualTo(IntakeStatus.SUBMITTED);
        assertThat(submitted.getAiConfidenceScore()).isEqualTo(0.78);
        assertThat(submitted.getAiUnknowns()).containsExactly("No CAC baseline available");
        assertThat(submitted.getSubmittedAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    @DisplayName("rejects submission when critical inputs are missing")
    void shouldRejectSubmissionWhenMissingInputs() {
        BusinessIntakeProfile profile = BusinessIntakeProfile.builder()
            .intakeId("intake-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy("user-1")
            .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> profile.submit("Summary", 0.7, List.of("budget unknown"), Instant.now()))
            .withMessageContaining("Missing critical inputs");
    }

    @Test
    @DisplayName("rejects invalid confidence and non-draft submission")
    void shouldRejectInvalidSubmitStates() {
        Instant now = Instant.now();
        BusinessIntakeProfile submitted = BusinessIntakeProfile.builder()
            .intakeId("intake-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .businessName("Acme")
            .offerSummary("SEO retainers")
            .targetAudience("B2B founders")
            .primaryGeography("US")
            .monthlyBudgetAmount(new BigDecimal("1500"))
            .growthGoal("Generate 30 qualified leads")
            .aiSummary("summary")
            .aiConfidenceScore(0.5)
            .status(IntakeStatus.SUBMITTED)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .submittedAt(now)
            .build();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> submitted.submit("Another", 0.8, List.of(), now));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> BusinessIntakeProfile.builder()
                .intakeId("intake-2")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .createdAt(now)
                .updatedAt(now)
                .createdBy("user-1")
                .aiConfidenceScore(1.2)
                .build());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> BusinessIntakeProfile.builder()
                .intakeId("intake-3")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .createdAt(now)
                .updatedAt(now)
                .createdBy("user-1")
                .aiConfidenceScore(-0.1)
                .build());
    }

    @Test
    @DisplayName("treats zero budget as missing and rejects blank summary on submit")
    void shouldCoverBudgetAndSummaryBranches() {
        Instant now = Instant.now();
        BusinessIntakeProfile profile = BusinessIntakeProfile.builder()
            .intakeId("intake-4")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .businessName("Acme")
            .offerSummary("SEO retainers")
            .targetAudience("B2B founders")
            .primaryGeography("US")
            .monthlyBudgetAmount(BigDecimal.ZERO)
            .growthGoal("Generate 30 qualified leads")
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();

        assertThat(profile.missingCriticalInputs()).contains("monthlyBudgetAmount");

        BusinessIntakeProfile validBudget = profile.toBuilder()
            .monthlyBudgetAmount(new BigDecimal("1000"))
            .build();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBudget.submit("  ", 0.6, List.of(), now.plusSeconds(1)))
            .withMessageContaining("aiSummary");
    }
}
