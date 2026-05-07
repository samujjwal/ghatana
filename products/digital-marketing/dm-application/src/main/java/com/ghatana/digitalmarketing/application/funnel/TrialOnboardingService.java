package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;

/**
 * Application service for trial onboarding workflow management in self-marketing acquisition funnel.
 *
 * @doc.type interface
 * @doc.purpose Provides trial onboarding workflow orchestration (P3-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface TrialOnboardingService {

    /**
     * Creates a new trial onboarding workflow.
     *
     * @param ctx the operation context
     * @param command the creation command
     * @return the created trial onboarding
     */
    Promise<TrialOnboarding> create(DmOperationContext ctx, CreateTrialOnboardingCommand command);

    /**
     * Starts a trial onboarding workflow.
     *
     * @param ctx the operation context
     * @param onboardingId the onboarding ID
     * @return the started trial onboarding
     */
    Promise<TrialOnboarding> start(DmOperationContext ctx, String onboardingId);

    /**
     * Advances a trial onboarding to the next step.
     *
     * @param ctx the operation context
     * @param onboardingId the onboarding ID
     * @param stepNumber the step number to advance to
     * @param progress the step progress data
     * @return the advanced trial onboarding
     */
    Promise<TrialOnboarding> advanceStep(DmOperationContext ctx, String onboardingId, int stepNumber, Map<String, Object> progress);

    /**
     * Completes a trial onboarding workflow.
     *
     * @param ctx the operation context
     * @param onboardingId the onboarding ID
     * @return the completed trial onboarding
     */
    Promise<TrialOnboarding> complete(DmOperationContext ctx, String onboardingId);

    /**
     * Cancels a trial onboarding workflow.
     *
     * @param ctx the operation context
     * @param onboardingId the onboarding ID
     * @param reason the cancellation reason
     * @return the cancelled trial onboarding
     */
    Promise<TrialOnboarding> cancel(DmOperationContext ctx, String onboardingId, String reason);

    /**
     * Finds a trial onboarding by ID.
     *
     * @param ctx the operation context
     * @param onboardingId the onboarding ID
     * @return the trial onboarding if found
     */
    Promise<Optional<TrialOnboarding>> findById(DmOperationContext ctx, String onboardingId);

    /**
     * Finds trial onboarding by lead ID.
     *
     * @param ctx the operation context
     * @param leadId the lead ID
     * @return the trial onboarding if found
     */
    Promise<Optional<TrialOnboarding>> findByLeadId(DmOperationContext ctx, String leadId);

    /**
     * Lists trial onboardings for a tenant.
     *
     * @param ctx the operation context
     * @return list of trial onboardings
     */
    Promise<java.util.List<TrialOnboarding>> list(DmOperationContext ctx);

    /**
     * Command to create a trial onboarding.
     */
    record CreateTrialOnboardingCommand(
        String leadId,
        String demoWorkspaceId,
        int totalSteps
    ) {
        public CreateTrialOnboardingCommand {
            if (leadId == null || leadId.isBlank()) {
                throw new IllegalArgumentException("leadId must not be blank");
            }
            if (demoWorkspaceId == null || demoWorkspaceId.isBlank()) {
                throw new IllegalArgumentException("demoWorkspaceId must not be blank");
            }
            if (totalSteps <= 0) {
                throw new IllegalArgumentException("totalSteps must be positive");
            }
        }
    }
}
