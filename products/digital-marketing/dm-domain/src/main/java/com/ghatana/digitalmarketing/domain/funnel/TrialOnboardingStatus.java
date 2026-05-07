package com.ghatana.digitalmarketing.domain.funnel;

/**
 * Lifecycle status for trial onboarding workflows.
 *
 * @doc.type enum
 * @doc.purpose Trial onboarding workflow lifecycle states (P3-001)
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public enum TrialOnboardingStatus {
    /**
     * Onboarding is created but not yet started.
     */
    PENDING,

    /**
     * Onboarding is in progress.
     */
    IN_PROGRESS,

    /**
     * Onboarding has been completed successfully.
     */
    COMPLETED,

    /**
     * Onboarding has been cancelled by user or admin.
     */
    CANCELLED
}
