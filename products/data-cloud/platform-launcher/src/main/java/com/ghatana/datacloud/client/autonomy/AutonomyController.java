/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.client.autonomy;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing graduated autonomy levels.
 *
 * <p>The AutonomyController is responsible for tracking autonomy levels per
 * action type and tenant, evaluating transition eligibility, and executing
 * level changes based on configured policies and accumulated evidence.
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li>Track current autonomy levels</li>
 *   <li>Evaluate upgrade/downgrade eligibility</li>
 *   <li>Execute level transitions</li>
 *   <li>Gate actions based on current level</li>
 *   <li>Record action outcomes for learning</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for autonomy level management
 * @doc.layer core
 * @doc.pattern State Machine, Controller
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see AutonomyLevel
 * @see AutonomyPolicy
 */
public interface AutonomyController {

    /**
     * Gets the current autonomy level for an action type.
     *
     * @param actionType the action type identifier
     * @param tenantId the tenant ID
     * @return Promise containing the current level
     */
    Promise<AutonomyLevel> getCurrentLevel(String actionType, String tenantId);

    /**
     * Gets the autonomy state for an action type.
     *
     * @param actionType the action type identifier
     * @param tenantId the tenant ID
     * @return Promise containing the full autonomy state
     */
    Promise<AutonomyState> getState(String actionType, String tenantId);

    /**
     * Evaluates whether an action is allowed at the current autonomy level.
     *
     * @param request the action gate request
     * @return Promise containing the gate decision
     */
    Promise<GateDecision> gate(GateRequest request);

    /**
     * Records the outcome of an action for autonomy evaluation.
     *
     * @param outcome the action outcome
     * @return Promise completing when recorded
     */
    Promise<Void> recordOutcome(ActionOutcome outcome);

    /**
     * Evaluates eligibility for an autonomy upgrade.
     *
     * @param actionType the action type
     * @param tenantId the tenant ID
     * @return Promise containing upgrade evaluation result
     */
    Promise<TransitionEvaluation> evaluateUpgrade(String actionType, String tenantId);

    /**
     * Evaluates whether a downgrade is warranted.
     *
     * @param actionType the action type
     * @param tenantId the tenant ID
     * @return Promise containing downgrade evaluation result
     */
    Promise<TransitionEvaluation> evaluateDowngrade(String actionType, String tenantId);

    /**
     * Gets the policy for an action type.
     *
     * @param actionType the action type
     * @return Promise containing the policy
     */
    Promise<AutonomyPolicy> getPolicy(String actionType);

    /**
     * Sets the policy for an action type.
     *
     * @param actionType the action type
     * @param policy the new policy
     * @return Promise completing when set
     */
    Promise<Void> setPolicy(String actionType, AutonomyPolicy policy);

    /**
     * Lists all action types with their current levels.
     *
     * @param tenantId the tenant ID
     * @return Promise containing map of action type to state
     */
    Promise<Map<String, AutonomyState>> listAllStates(String tenantId);

    /**
     * Gets controller statistics.
     *
     * @return Promise containing statistics
     */
    Promise<ControllerStatistics> getStatistics();

    /**
     * Retrieves autonomy logs based on a filter.
     *
     * @param filter the filter criteria
     * @return Promise containing list of logs
     */
    Promise<List<AutonomyLog>> getLogs(AutonomyLogFilter filter);

    /**
     * Gets the complete action log timeline (for UI display).
     *
     * @return Promise containing list of all autonomy logs
     */
    Promise<List<AutonomyLog>> getActionLog();

    /**
     * Gets the autonomy state for a specific domain.
     *
     * @param domain the domain identifier
     * @return Promise containing the domain state
     */
    Promise<AutonomyState> getStateForDomain(String domain);

    /**
     * Updates the autonomy policy for a domain.
     *
     * @param policy the new policy
     * @return Promise containing the updated policy
     */
    Promise<AutonomyPolicy> updatePolicy(AutonomyPolicy policy);

    /**
     * Requests an upgrade to the next autonomy level.
     *
     * @param actionType the action type
     * @param tenantId the tenant ID
     * @param reason the reason for upgrade
     * @return Promise containing transition result
     */
    Promise<TransitionResult> requestUpgrade(String actionType, String tenantId, String reason);

    /**
     * Forces a downgrade to a specific level.
     *
     * @param actionType the action type
     * @param tenantId the tenant ID
     * @param targetLevel the target level (optional, if null downgrades by one step)
     * @param reason the reason for downgrade
     * @return Promise containing transition result
     */
    Promise<TransitionResult> forceDowngrade(String actionType, String tenantId, AutonomyLevel targetLevel, String reason);

    /**
     * Sets the autonomy level directly (override).
     *
     * @param actionType the action type
     * @param tenantId the tenant ID
     * @param level the new level
     * @param reason the reason for override
     * @return Promise containing transition result
     */
    Promise<TransitionResult> setLevel(String actionType, String tenantId, AutonomyLevel level, String reason);

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Complete state of autonomy for an action type.
     */
    @Value
    @Builder
    class AutonomyState {
        String actionType;
        String tenantId;
        AutonomyLevel currentLevel;
        AutonomyLevel effectiveMaxLevel;

        // Statistics
        int totalActions;
        int successfulActions;
        int failedActions;
        double successRate;
        double confidence;

        // History
        Instant levelEnteredAt;
        Instant lastActionAt;
        Instant lastUpgradeAt;
        Instant lastDowngradeAt;
        int upgradeCount;
        int downgradeCount;
        int consecutiveSuccesses;
        int consecutiveFailures;

        // Feedback
        int positiveFeedbackCount;
        int negativeFeedbackCount;

        /**
         * Gets the time spent at the current level.
         *
         * @return duration at current level
         */
        public java.time.Duration getTimeAtLevel() {
            return java.time.Duration.between(
                    levelEnteredAt != null ? levelEnteredAt : Instant.now(),
                    Instant.now()
            );
        }

        /**
         * Calculates an overall health score (0.0 to 1.0).
         *
         * @return health score
         */
        public double getHealthScore() {
            double successComponent = successRate * 0.5;
            double confidenceComponent = confidence * 0.3;
            double feedbackComponent = totalActions > 0
                    ? ((double) positiveFeedbackCount / (positiveFeedbackCount + negativeFeedbackCount + 1)) * 0.2
                    : 0.1;
            return successComponent + confidenceComponent + feedbackComponent;
        }
    }

    /**
     * Request to gate an action.
     */
    @Value
    @Builder
    class GateRequest {
        String actionType;
        String tenantId;
        String actionId;
        double impactScore;
        Map<String, Object> context;
        boolean allowWait;
        java.time.Duration maxWaitTime;
    }

    /**
     * Decision from the action gate.
     */
    @Value
    @Builder
    class GateDecision {
        String actionId;
        boolean allowed;
        AutonomyLevel currentLevel;
        GateAction requiredAction;
        String message;
        java.time.Duration waitTime;
        String approvalId;

        public enum GateAction {
            /** Action can proceed immediately */
            PROCEED,
            /** Action requires human approval */
            AWAIT_APPROVAL,
            /** Action should be presented as suggestion */
            PRESENT_SUGGESTION,
            /** Action is blocked by policy */
            BLOCKED,
            /** Action was rejected */
            REJECTED
        }

        public boolean canProceed() {
            return requiredAction == GateAction.PROCEED;
        }
    }

    /**
     * Outcome of an action for learning.
     */
    @Value
    @Builder
    class ActionOutcome {
        String actionType;
        String tenantId;
        String actionId;
        boolean success;
        String errorMessage;
        double impactScore;
        java.time.Duration executionTime;
        FeedbackType feedbackType;
        double feedbackScore;
        Map<String, Object> metadata;

        public enum FeedbackType {
            NONE,
            POSITIVE_EXPLICIT,
            NEGATIVE_EXPLICIT,
            POSITIVE_IMPLICIT,
            NEGATIVE_IMPLICIT,
            SYSTEM_OBSERVED
        }
    }

    /**
     * Evaluation of transition eligibility.
     */
    @Value
    @Builder
    class TransitionEvaluation {
        String actionType;
        String tenantId;
        AutonomyLevel currentLevel;
        AutonomyLevel targetLevel;
        boolean eligible;
        double confidence;
        List<String> reasons;
        List<String> blockers;
        Map<String, Double> criteriaScores;

        public boolean isBlocked() {
            return blockers != null && !blockers.isEmpty();
        }
    }

    /**
     * Result of a level transition.
     */
    @Value
    @Builder
    class TransitionResult {
        String actionType;
        String tenantId;
        AutonomyLevel previousLevel;
        AutonomyLevel newLevel;
        boolean success;
        TransitionType type;
        String reason;
        String message;
        Instant transitionTime;

        public enum TransitionType {
            UPGRADE,
            DOWNGRADE,
            OVERRIDE,
            RESET,
            NONE
        }

        public boolean levelChanged() {
            return previousLevel != newLevel;
        }
    }

    /**
     * Controller statistics.
     */
    @Value
    @Builder
    class ControllerStatistics {
        int totalActionTypes;
        Map<AutonomyLevel, Integer> actionTypesByLevel;
        long totalActionsGated;
        long actionsAllowed;
        long actionsBlocked;
        long totalUpgrades;
        long totalDowngrades;
        double averageConfidence;
        double overallSuccessRate;
        Instant lastTransitionTime;
    }

    /**
     * Filter for autonomy logs.
     */
    @Value
    @Builder
    class AutonomyLogFilter {
        String actionType;
        String tenantId;
        Instant startTime;
        Instant endTime;
        Integer limit;
    }
}
