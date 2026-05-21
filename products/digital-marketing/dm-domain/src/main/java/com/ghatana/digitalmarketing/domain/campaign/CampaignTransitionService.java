/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.domain.campaign;

import java.time.Instant;
import java.util.Objects;

/**
 * Campaign transition service - manages campaign state transitions with business rule validation.
 *
 * <p>This service enforces business rules for campaign state transitions beyond the basic
 * state machine. It validates preconditions for each transition and records transition events.</p>
 *
 * @doc.type class
 * @doc.purpose Campaign transition service with business rule validation
 * @doc.layer product
 * @doc.pattern Service
 */
public class CampaignTransitionService {

    private final CampaignStateMachine stateMachine;

    public CampaignTransitionService() {
        this.stateMachine = new CampaignStateMachine();
    }

    /**
     * Transition a campaign to a new state with business rule validation.
     *
     * @param campaign the campaign to transition
     * @param toStatus the target status
     * @param context transition context with actor and reason
     * @return transition result with success/failure and any errors
     */
    public TransitionResult transition(Campaign campaign, CampaignStatus toStatus, TransitionContext context) {
        Objects.requireNonNull(campaign, "Campaign cannot be null");
        Objects.requireNonNull(toStatus, "Target status cannot be null");
        Objects.requireNonNull(context, "Transition context cannot be null");

        CampaignStatus fromStatus = campaign.getStatus();

        // Validate basic state machine transition
        if (!stateMachine.canTransition(fromStatus, toStatus)) {
            return TransitionResult.failure(
                String.format("Invalid transition: %s → %s", fromStatus, toStatus)
            );
        }

        // Validate business rules for specific transitions
        String businessRuleError = validateBusinessRules(campaign, toStatus, context);
        if (businessRuleError != null) {
            return TransitionResult.failure(businessRuleError);
        }

        // Execute transition - return new Campaign instance (immutable)
        Campaign updatedCampaign = Campaign.builder()
            .id(campaign.getId())
            .workspaceId(campaign.getWorkspaceId())
            .name(campaign.getName())
            .status(toStatus)
            .type(campaign.getType())
            .objective(campaign.getObjective())
            .budgetCents(campaign.getBudgetCents())
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .audience(campaign.getAudience())
            .landingPageUrl(campaign.getLandingPageUrl())
            .createdAt(campaign.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(campaign.getCreatedBy())
            .build();

        return TransitionResult.success(updatedCampaign);
    }

    /**
     * Validate business rules for a specific transition.
     *
     * @param campaign the campaign to transition
     * @param toStatus the target status
     * @param context transition context
     * @return error message if validation fails, null if valid
     */
    private String validateBusinessRules(Campaign campaign, CampaignStatus toStatus, TransitionContext context) {
        switch (toStatus) {
            case PENDING_APPROVAL:
                return validatePendingApproval(campaign);
            case APPROVED:
                return validateApproved(campaign, context);
            case PENDING_LAUNCH:
                return validatePendingLaunch(campaign);
            case LAUNCH_RUNNING:
                return validateLaunchRunning(campaign);
            case LAUNCHED:
                return validateLaunched(campaign);
            case PAUSED:
                return validatePaused(campaign);
            case COMPLETED:
                return validateCompleted(campaign);
            case ARCHIVED:
                return validateArchived(campaign);
            case ROLLED_BACK:
                return validateRolledBack(campaign, context);
            default:
                return null;
        }
    }

    private String validatePendingApproval(Campaign campaign) {
        if (campaign.getName() == null || campaign.getName().isBlank()) {
            return "Campaign must have a name before requesting approval";
        }
        if (campaign.getBudgetCents() == null || campaign.getBudgetCents() <= 0) {
            return "Campaign must have a valid budget before requesting approval";
        }
        return null;
    }

    private String validateApproved(Campaign campaign, TransitionContext context) {
        if (!context.hasApproval()) {
            return "Campaign approval requires explicit approval from authorized actor";
        }
        if (context.getActor() == null || context.getActor().isBlank()) {
            return "Campaign approval requires actor information";
        }
        return null;
    }

    private String validatePendingLaunch(Campaign campaign) {
        if (campaign.getStartDate() == null) {
            return "Campaign must have a start date before launch";
        }
        if (campaign.getEndDate() != null && campaign.getStartDate().compareTo(campaign.getEndDate()) > 0) {
            return "Campaign start date must be before end date";
        }
        return null;
    }

    private String validateLaunchRunning(Campaign campaign) {
        // Additional preflight checks can be added here
        return null;
    }

    private String validateLaunched(Campaign campaign) {
        if (campaign.getStartDate() == null) {
            return "Campaign must have a start date when launched";
        }
        return null;
    }

    private String validatePaused(Campaign campaign) {
        // Campaign can be paused only if it's currently running
        return null;
    }

    private String validateCompleted(Campaign campaign) {
        if (campaign.getEndDate() == null) {
            return "Campaign must have an end date when completed";
        }
        return null;
    }

    private String validateArchived(Campaign campaign) {
        if (!stateMachine.isTerminal(campaign.getStatus())) {
            return "Campaign must be in a terminal state before archiving";
        }
        return null;
    }

    private String validateRolledBack(Campaign campaign, TransitionContext context) {
        if (context.getReason() == null || context.getReason().isBlank()) {
            return "Campaign rollback requires a reason";
        }
        return null;
    }

    /**
     * Transition result.
     */
    public static class TransitionResult {
        private final boolean success;
        private final String error;
        private final Campaign updatedCampaign;

        private TransitionResult(boolean success, String error, Campaign updatedCampaign) {
            this.success = success;
            this.error = error;
            this.updatedCampaign = updatedCampaign;
        }

        public static TransitionResult success(Campaign updatedCampaign) {
            return new TransitionResult(true, null, updatedCampaign);
        }

        public static TransitionResult failure(String error) {
            return new TransitionResult(false, error, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public Campaign getUpdatedCampaign() {
            return updatedCampaign;
        }
    }

    /**
     * Transition context.
     */
    public static class TransitionContext {
        private final String actor;
        private final String reason;
        private final boolean hasApproval;

        public TransitionContext(String actor, String reason, boolean hasApproval) {
            this.actor = actor;
            this.reason = reason;
            this.hasApproval = hasApproval;
        }

        public String getActor() {
            return actor;
        }

        public String getReason() {
            return reason;
        }

        public boolean hasApproval() {
            return hasApproval;
        }
    }
}
