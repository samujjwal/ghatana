package com.ghatana.digitalmarketing.domain.funnel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TrialOnboarding domain model.
 *
 * @doc.type test
 * @doc.purpose Validates TrialOnboarding lifecycle and validation (P3-001)
 * @doc.layer product
 */
@DisplayName("TrialOnboarding Tests")
class TrialOnboardingTest {

    @Test
    @DisplayName("Should build valid TrialOnboarding")
    void shouldBuildValidTrialOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .stepProgress(Map.of())
            .createdAt(Instant.now())
            .build();

        assertThat(onboarding.getId()).isEqualTo("onboarding-123");
        assertThat(onboarding.getTenantId()).isEqualTo("tenant-456");
        assertThat(onboarding.getWorkspaceId()).isEqualTo("ws-789");
        assertThat(onboarding.getLeadId()).isEqualTo("lead-101");
        assertThat(onboarding.getDemoWorkspaceId()).isEqualTo("demo-202");
        assertThat(onboarding.getStatus()).isEqualTo(TrialOnboardingStatus.PENDING);
        assertThat(onboarding.getCurrentStep()).isEqualTo(0);
        assertThat(onboarding.getTotalSteps()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should start onboarding from PENDING status")
    void shouldStartOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        TrialOnboarding started = onboarding.start();

        assertThat(started.getStatus()).isEqualTo(TrialOnboardingStatus.IN_PROGRESS);
        assertThat(started.getStartedAt()).isNotNull();
        assertThat(started.getCurrentStep()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw when starting non-PENDING onboarding")
    void shouldThrowWhenStartingNonPending() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(1)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> onboarding.start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start onboarding in status");
    }

    @Test
    @DisplayName("Should advance step successfully")
    void shouldAdvanceStep() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(1)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        Map<String, Object> progress = Map.of("step2", Map.of("completed", true));
        TrialOnboarding advanced = onboarding.advanceStep(2, progress);

        assertThat(advanced.getCurrentStep()).isEqualTo(2);
        assertThat(advanced.getStepProgress()).containsKey("step2");
    }

    @Test
    @DisplayName("Should throw when advancing to invalid step number")
    void shouldThrowWhenAdvancingToInvalidStep() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(1)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> onboarding.advanceStep(0, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid step number");

        assertThatThrownBy(() -> onboarding.advanceStep(6, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid step number");
    }

    @Test
    @DisplayName("Should throw when advancing step in non-IN_PROGRESS status")
    void shouldThrowWhenAdvancingNonInProgress() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> onboarding.advanceStep(1, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot advance step in status");
    }

    @Test
    @DisplayName("Should complete onboarding from IN_PROGRESS status")
    void shouldCompleteOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(4)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        TrialOnboarding completed = onboarding.complete();

        assertThat(completed.getStatus()).isEqualTo(TrialOnboardingStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getCurrentStep()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should throw when completing non-IN_PROGRESS onboarding")
    void shouldThrowWhenCompletingNonInProgress() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> onboarding.complete())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot complete onboarding in status");
    }

    @Test
    @DisplayName("Should cancel onboarding")
    void shouldCancelOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(2)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        TrialOnboarding cancelled = onboarding.cancel("User lost interest");

        assertThat(cancelled.getStatus()).isEqualTo(TrialOnboardingStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("User lost interest");
    }

    @Test
    @DisplayName("Should throw when cancelling completed onboarding")
    void shouldThrowWhenCancellingCompleted() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.COMPLETED)
            .currentStep(5)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> onboarding.cancel("User lost interest"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel onboarding in status");
    }

    @Test
    @DisplayName("Should throw when cancelling already cancelled onboarding")
    void shouldThrowWhenCancellingAlreadyCancelled() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.CANCELLED)
            .currentStep(2)
            .totalSteps(5)
            .createdAt(Instant.now())
            .cancellationReason("User lost interest")
            .build();

        assertThatThrownBy(() -> onboarding.cancel("Another reason"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot cancel onboarding in status");
    }

    @Test
    @DisplayName("Should calculate progress percentage correctly")
    void shouldCalculateProgressPercentage() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(2)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        assertThat(onboarding.getProgressPercentage()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("Should return zero progress when totalSteps is zero")
    void shouldReturnZeroProgressWhenTotalStepsIsZero() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        assertThat(onboarding.getProgressPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return true for complete onboarding")
    void shouldReturnTrueForCompleteOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.COMPLETED)
            .currentStep(5)
            .totalSteps(5)
            .createdAt(Instant.now())
            .completedAt(Instant.now())
            .build();

        assertThat(onboarding.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-complete onboarding")
    void shouldReturnFalseForNonCompleteOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(2)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        assertThat(onboarding.isComplete()).isFalse();
    }

    @Test
    @DisplayName("Should return true for in-progress onboarding")
    void shouldReturnTrueForInProgressOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .currentStep(2)
            .totalSteps(5)
            .createdAt(Instant.now())
            .startedAt(Instant.now())
            .build();

        assertThat(onboarding.isInProgress()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-in-progress onboarding")
    void shouldReturnFalseForNonInProgressOnboarding() {
        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        assertThat(onboarding.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when tenantId is blank")
    void shouldThrowWhenTenantIdIsBlank() {
        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when workspaceId is blank")
    void shouldThrowWhenWorkspaceIdIsBlank() {
        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workspaceId must not be blank");
    }

    @Test
    @DisplayName("Should throw when leadId is blank")
    void shouldThrowWhenLeadIdIsBlank() {
        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("leadId must not be blank");
    }

    @Test
    @DisplayName("Should throw when totalSteps is not positive")
    void shouldThrowWhenTotalStepsIsNotPositive() {
        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(0)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("totalSteps must be positive");

        assertThatThrownBy(() -> TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(-1)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("totalSteps must be positive");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        TrialOnboarding original = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        TrialOnboarding modified = original.toBuilder()
            .status(TrialOnboardingStatus.IN_PROGRESS)
            .startedAt(Instant.now())
            .currentStep(1)
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getStatus()).isEqualTo(TrialOnboardingStatus.IN_PROGRESS);
        assertThat(modified.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        TrialOnboarding onboarding1 = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .demoWorkspaceId("demo-202")
            .status(TrialOnboardingStatus.PENDING)
            .totalSteps(5)
            .createdAt(Instant.now())
            .build();

        TrialOnboarding onboarding2 = TrialOnboarding.builder()
            .id("onboarding-123")
            .tenantId("tenant-999")
            .workspaceId("ws-999")
            .leadId("lead-999")
            .demoWorkspaceId("demo-999")
            .status(TrialOnboardingStatus.COMPLETED)
            .totalSteps(10)
            .createdAt(Instant.now())
            .build();

        assertThat(onboarding1).isEqualTo(onboarding2);
        assertThat(onboarding1.hashCode()).isEqualTo(onboarding2.hashCode());
    }
}
