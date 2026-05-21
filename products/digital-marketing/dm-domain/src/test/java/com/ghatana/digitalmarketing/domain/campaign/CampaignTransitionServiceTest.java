/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.domain.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CampaignTransitionService Tests")
class CampaignTransitionServiceTest {

    private final CampaignTransitionService transitionService = new CampaignTransitionService();

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        @Test
        @DisplayName("DRAFT to PENDING_APPROVAL succeeds with valid budget and name")
        void draftToPendingApproval() {
            Campaign campaign = createCampaign(CampaignStatus.DRAFT, "Test Campaign", 1_000L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Request approval", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.PENDING_APPROVAL, context);

            assertTrue(result.isSuccess());
            assertEquals(CampaignStatus.PENDING_APPROVAL, result.getUpdatedCampaign().getStatus());
        }

        @Test
        @DisplayName("PENDING_APPROVAL to APPROVED succeeds when approval flag is true")
        void pendingApprovalToApproved() {
            Campaign campaign = createCampaign(CampaignStatus.PENDING_APPROVAL, "Approved", 1_000L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("approver@example.com", "Approved", true);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.APPROVED, context);

            assertTrue(result.isSuccess());
            assertEquals(CampaignStatus.APPROVED, result.getUpdatedCampaign().getStatus());
        }

        @Test
        @DisplayName("APPROVED to PENDING_LAUNCH succeeds when start date is present")
        void approvedToPendingLaunch() {
            Campaign campaign = createCampaign(
                    CampaignStatus.APPROVED,
                    "Launchable",
                    1_000L,
                    Instant.now().plusSeconds(3600).toString(),
                    null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Launch", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.PENDING_LAUNCH, context);

            assertTrue(result.isSuccess());
            assertEquals(CampaignStatus.PENDING_LAUNCH, result.getUpdatedCampaign().getStatus());
        }

        @Test
        @DisplayName("LAUNCHED to PAUSED succeeds")
        void launchedToPaused() {
            Campaign campaign = createCampaign(
                    CampaignStatus.LAUNCHED,
                    "Live campaign",
                    1_000L,
                    Instant.now().minusSeconds(3600).toString(),
                    Instant.now().toString());
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Pause", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.PAUSED, context);

            assertTrue(result.isSuccess());
            assertEquals(CampaignStatus.PAUSED, result.getUpdatedCampaign().getStatus());
        }
    }

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("DRAFT to APPROVED fails by state machine")
        void invalidStateTransition() {
            Campaign campaign = createCampaign(CampaignStatus.DRAFT, "Draft", 1_000L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Invalid", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.APPROVED, context);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("Invalid transition"));
        }

        @Test
        @DisplayName("DRAFT to PENDING_APPROVAL fails when budget is zero")
        void draftToPendingApprovalWithZeroBudget() {
            Campaign campaign = createCampaign(CampaignStatus.DRAFT, "Draft", 0L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Request", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.PENDING_APPROVAL, context);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("budget"));
        }

        @Test
        @DisplayName("PENDING_APPROVAL to APPROVED fails when actor missing")
        void pendingApprovalToApprovedWithoutActor() {
            Campaign campaign = createCampaign(CampaignStatus.PENDING_APPROVAL, "Pending", 1_000L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext(null, "Approve", true);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.APPROVED, context);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("actor"));
        }

        @Test
        @DisplayName("APPROVED to PENDING_LAUNCH fails when date range is invalid")
        void approvedToPendingLaunchWithInvalidDateRange() {
            Campaign campaign = createCampaign(
                    CampaignStatus.APPROVED,
                    "Invalid dates",
                    1_000L,
                    Instant.now().plusSeconds(3600).toString(),
                    Instant.now().toString());
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Launch", false);

            CampaignTransitionService.TransitionResult result =
                    transitionService.transition(campaign, CampaignStatus.PENDING_LAUNCH, context);

            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("start date"));
        }

        @Test
        @DisplayName("Null campaign throws NullPointerException")
        void nullCampaign() {
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Test", false);
            assertThrows(
                    NullPointerException.class,
                    () -> transitionService.transition(null, CampaignStatus.PENDING_APPROVAL, context));
        }

        @Test
        @DisplayName("Null target status throws NullPointerException")
        void nullTargetStatus() {
            Campaign campaign = createCampaign(CampaignStatus.DRAFT, "Draft", 1_000L, null, null);
            CampaignTransitionService.TransitionContext context =
                    new CampaignTransitionService.TransitionContext("user@example.com", "Test", false);
            assertThrows(NullPointerException.class, () -> transitionService.transition(campaign, null, context));
        }

        @Test
        @DisplayName("Null context throws NullPointerException")
        void nullContext() {
            Campaign campaign = createCampaign(CampaignStatus.DRAFT, "Draft", 1_000L, null, null);
            assertThrows(
                    NullPointerException.class,
                    () -> transitionService.transition(campaign, CampaignStatus.PENDING_APPROVAL, null));
        }
    }

    private Campaign createCampaign(
            CampaignStatus status,
            String name,
            Long budgetCents,
            String startDate,
            String endDate) {
        Instant now = Instant.now();
        return Campaign.builder()
                .id("campaign-123")
                .workspaceId(DmWorkspaceId.of("workspace-123"))
                .name(name)
                .status(status)
                .type(CampaignType.EMAIL)
                .budgetCents(budgetCents)
                .startDate(startDate)
                .endDate(endDate)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("user@example.com")
                .build();
    }
}
