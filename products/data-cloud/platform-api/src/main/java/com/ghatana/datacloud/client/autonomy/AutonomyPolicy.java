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

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Configurable policy for autonomy level management.
 *
 * <p>Policies define the rules and thresholds for autonomy transitions,
 * including minimum confidence requirements, success rate thresholds,
 * and restrictions on specific action types.
 *
 * <h2>Policy Components</h2>
 * <ul>
 *   <li><b>Thresholds</b>: Confidence/success requirements for upgrades</li>
 *   <li><b>Cooldowns</b>: Minimum time between transitions</li>
 *   <li><b>Restrictions</b>: Actions that cannot reach certain levels</li>
 *   <li><b>Overrides</b>: Per-action-type level limits</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * AutonomyPolicy policy = AutonomyPolicy.builder()
 *     .maxLevel(AutonomyLevel.NOTIFY)  // Never fully autonomous
 *     .upgradeConfidenceThreshold(0.9)
 *     .upgradeSuccessRateThreshold(0.95)
 *     .minimumActionsForUpgrade(50)
 *     .downgradeOnFailure(true)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Configure autonomy transition rules
 * @doc.layer core
 * @doc.pattern Policy, Configuration
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see AutonomyLevel
 * @see AutonomyController
 */
@Value
@Builder(toBuilder = true)
public class AutonomyPolicy {

    /**
     * Policy identifier.
     */
    @Builder.Default
    String id = "default";

    /**
     * Human-readable name.
     */
    @Builder.Default
    String name = "Default Autonomy Policy";

    /**
     * The starting autonomy level for new action types.
     */
    @Builder.Default
    AutonomyLevel defaultLevel = AutonomyLevel.SUGGEST;

    /**
     * The maximum autonomy level allowed by this policy.
     *
     * <p>No action can exceed this level, regardless of confidence.
     */
    @Builder.Default
    AutonomyLevel maxLevel = AutonomyLevel.AUTONOMOUS;

    /**
     * The minimum autonomy level (floor).
     *
     * <p>Downgrades cannot go below this level.
     */
    @Builder.Default
    AutonomyLevel minLevel = AutonomyLevel.SUGGEST;

    // ═══════════════════════════════════════════════════════════════════════════
    // Upgrade Thresholds
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum confidence score required for upgrade consideration.
     *
     * <p>Confidence is typically derived from recent success rate and
     * feedback quality.
     */
    @Builder.Default
    double upgradeConfidenceThreshold = 0.85;

    /**
     * Minimum success rate required for upgrade (0.0 to 1.0).
     *
     * <p>Success rate is calculated over the evaluation window.
     */
    @Builder.Default
    double upgradeSuccessRateThreshold = 0.90;

    /**
     * Minimum number of actions required before upgrade is considered.
     *
     * <p>Ensures sufficient data for reliable confidence calculation.
     */
    @Builder.Default
    int minimumActionsForUpgrade = 20;

    /**
     * Minimum number of positive feedback signals for upgrade.
     */
    @Builder.Default
    int minimumPositiveFeedback = 10;

    /**
     * Duration of the evaluation window for upgrade decisions.
     */
    @Builder.Default
    Duration upgradeEvaluationWindow = Duration.ofDays(7);

    // ═══════════════════════════════════════════════════════════════════════════
    // Downgrade Thresholds
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to automatically downgrade on action failure.
     */
    @Builder.Default
    boolean downgradeOnFailure = true;

    /**
     * Whether to downgrade on negative explicit feedback.
     */
    @Builder.Default
    boolean downgradeOnNegativeFeedback = true;

    /**
     * Failure rate threshold that triggers downgrade (0.0 to 1.0).
     */
    @Builder.Default
    double downgradeFailureRateThreshold = 0.1;

    /**
     * Number of consecutive failures that trigger immediate downgrade.
     */
    @Builder.Default
    int consecutiveFailuresForDowngrade = 3;

    /**
     * Number of downgrade levels to apply on consecutive failures.
     */
    @Builder.Default
    int downgradeStepsOnConsecutiveFailure = 1;

    /**
     * Duration of the evaluation window for downgrade decisions.
     */
    @Builder.Default
    Duration downgradeEvaluationWindow = Duration.ofHours(24);

    // ═══════════════════════════════════════════════════════════════════════════
    // Cooldowns
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum time between upgrade attempts.
     */
    @Builder.Default
    Duration upgradeCooldown = Duration.ofDays(1);

    /**
     * Minimum time between downgrade events.
     */
    @Builder.Default
    Duration downgradeCooldown = Duration.ofHours(1);

    /**
     * Minimum time at a level before upgrade is considered.
     */
    @Builder.Default
    Duration minimumTimeAtLevel = Duration.ofHours(12);

    /**
     * Time to wait after a downgrade before allowing upgrade.
     */
    @Builder.Default
    Duration upgradeWaitAfterDowngrade = Duration.ofDays(3);

    // ═══════════════════════════════════════════════════════════════════════════
    // Risk and Impact
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maximum impact score for actions at each autonomy level.
     *
     * <p>Actions with higher impact require lower autonomy levels.
     */
    @Builder.Default
    Map<AutonomyLevel, Double> maxImpactByLevel = Map.of(
            AutonomyLevel.AUTONOMOUS, 0.3,
            AutonomyLevel.NOTIFY, 0.5,
            AutonomyLevel.CONFIRM, 0.8,
            AutonomyLevel.SUGGEST, 1.0
    );

    /**
     * Action types that are restricted to specific maximum levels.
     */
    @Builder.Default
    Map<String, AutonomyLevel> actionTypeLevelCaps = Map.of();

    /**
     * Action types that are blocked entirely (cannot be automated).
     */
    @Builder.Default
    Set<String> blockedActionTypes = Set.of();

    /**
     * Whether to allow override requests from users.
     */
    @Builder.Default
    boolean allowUserOverride = true;

    /**
     * Maximum level users can override to (if allowed).
     */
    @Builder.Default
    AutonomyLevel maxUserOverrideLevel = AutonomyLevel.AUTONOMOUS;

    // ═══════════════════════════════════════════════════════════════════════════
    // Notifications
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether to notify on autonomy level changes.
     */
    @Builder.Default
    boolean notifyOnLevelChange = true;

    /**
     * Channels to notify on level changes.
     */
    @Builder.Default
    List<String> notificationChannels = List.of("audit", "admin");

    /**
     * Whether human approval is required for upgrades.
     */
    @Builder.Default
    boolean requireApprovalForUpgrade = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation and Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates the policy configuration.
     *
     * @return Optional error message if invalid
     */
    public Optional<String> validate() {
        if (maxLevel.isLessAutonomousThan(minLevel)) {
            return Optional.of("maxLevel cannot be less than minLevel");
        }
        if (defaultLevel.isMoreAutonomousThan(maxLevel)) {
            return Optional.of("defaultLevel cannot exceed maxLevel");
        }
        if (defaultLevel.isLessAutonomousThan(minLevel)) {
            return Optional.of("defaultLevel cannot be below minLevel");
        }
        if (upgradeConfidenceThreshold < 0 || upgradeConfidenceThreshold > 1) {
            return Optional.of("upgradeConfidenceThreshold must be between 0 and 1");
        }
        if (upgradeSuccessRateThreshold < 0 || upgradeSuccessRateThreshold > 1) {
            return Optional.of("upgradeSuccessRateThreshold must be between 0 and 1");
        }
        if (downgradeFailureRateThreshold < 0 || downgradeFailureRateThreshold > 1) {
            return Optional.of("downgradeFailureRateThreshold must be between 0 and 1");
        }
        if (minimumActionsForUpgrade < 1) {
            return Optional.of("minimumActionsForUpgrade must be at least 1");
        }
        return Optional.empty();
    }

    /**
     * Gets the effective maximum level for an action type.
     *
     * @param actionType the action type
     * @return the maximum allowed level
     */
    public AutonomyLevel getEffectiveMaxLevel(String actionType) {
        if (blockedActionTypes.contains(actionType)) {
            return AutonomyLevel.SUGGEST; // Blocked = always needs human
        }
        AutonomyLevel cap = actionTypeLevelCaps.get(actionType);
        if (cap != null) {
            return AutonomyLevel.moreRestrictive(cap, maxLevel);
        }
        return maxLevel;
    }

    /**
     * Gets the maximum autonomy level for a given impact score.
     *
     * @param impactScore the impact score (0.0 to 1.0)
     * @return the maximum allowed level
     */
    public AutonomyLevel getMaxLevelForImpact(double impactScore) {
        for (AutonomyLevel level : AutonomyLevel.values()) {
            Double maxImpact = maxImpactByLevel.get(level);
            if (maxImpact != null && impactScore <= maxImpact) {
                return AutonomyLevel.moreRestrictive(level, maxLevel);
            }
        }
        return minLevel;
    }

    /**
     * Checks if an action type is blocked from automation.
     *
     * @param actionType the action type
     * @return true if blocked
     */
    public boolean isActionBlocked(String actionType) {
        return blockedActionTypes.contains(actionType);
    }

    /**
     * Creates a strict policy (maximum human oversight).
     *
     * @return a strict policy
     */
    public static AutonomyPolicy strict() {
        return AutonomyPolicy.builder()
                .id("strict")
                .name("Strict Autonomy Policy")
                .defaultLevel(AutonomyLevel.SUGGEST)
                .maxLevel(AutonomyLevel.CONFIRM)
                .upgradeConfidenceThreshold(0.98)
                .upgradeSuccessRateThreshold(0.99)
                .minimumActionsForUpgrade(100)
                .requireApprovalForUpgrade(true)
                .build();
    }

    /**
     * Creates a balanced policy (moderate oversight).
     *
     * @return a balanced policy
     */
    public static AutonomyPolicy balanced() {
        return AutonomyPolicy.builder()
                .id("balanced")
                .name("Balanced Autonomy Policy")
                .defaultLevel(AutonomyLevel.SUGGEST)
                .maxLevel(AutonomyLevel.NOTIFY)
                .upgradeConfidenceThreshold(0.9)
                .upgradeSuccessRateThreshold(0.95)
                .minimumActionsForUpgrade(30)
                .build();
    }

    /**
     * Creates a permissive policy (minimal oversight).
     *
     * @return a permissive policy
     */
    public static AutonomyPolicy permissive() {
        return AutonomyPolicy.builder()
                .id("permissive")
                .name("Permissive Autonomy Policy")
                .defaultLevel(AutonomyLevel.CONFIRM)
                .maxLevel(AutonomyLevel.AUTONOMOUS)
                .upgradeConfidenceThreshold(0.8)
                .upgradeSuccessRateThreshold(0.85)
                .minimumActionsForUpgrade(10)
                .minimumTimeAtLevel(Duration.ofHours(4))
                .upgradeCooldown(Duration.ofHours(12))
                .build();
    }
}
