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

/**
 * Defines the levels of autonomy for AI-initiated actions.
 *
 * <p>Autonomy levels form a hierarchy from full human control (SUGGEST) to
 * full AI autonomy (AUTONOMOUS). The system can transition between levels
 * based on confidence, performance, and policy configuration.
 *
 * <h2>Level Characteristics</h2>
 * <table>
 *   <tr><th>Level</th><th>Latency Impact</th><th>Human Load</th><th>Risk</th></tr>
 *   <tr><td>SUGGEST</td><td>High (waits)</td><td>High</td><td>Low</td></tr>
 *   <tr><td>CONFIRM</td><td>Medium (quick)</td><td>Medium</td><td>Low</td></tr>
 *   <tr><td>NOTIFY</td><td>Low (async)</td><td>Low</td><td>Medium</td></tr>
 *   <tr><td>AUTONOMOUS</td><td>None</td><td>None</td><td>Higher</td></tr>
 * </table>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * AutonomyLevel level = AutonomyLevel.CONFIRM;
 * if (level.requiresHumanApproval()) {
 *     requestApproval(action);
 * }
 * 
 * // Transition logic
 * AutonomyLevel next = level.upgrade();
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Define autonomy level hierarchy
 * @doc.layer core
 * @doc.pattern State, Enumeration
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public enum AutonomyLevel {

    /**
     * Suggestion level - AI recommends, human decides.
     *
     * <p>The AI provides suggestions and recommendations, but all decisions
     * are made by humans. Actions are not taken until explicitly requested.
     *
     * <ul>
     *   <li>Human reviews all suggestions</li>
     *   <li>Human selects which actions to take</li>
     *   <li>AI learns from selections</li>
     *   <li>Highest latency, lowest risk</li>
     * </ul>
     */
    SUGGEST(0, "Suggestion", true, true, false),

    /**
     * Confirmation level - AI proposes, human approves.
     *
     * <p>The AI proposes specific actions and waits for human approval
     * before executing. Approval can be quick (yes/no) rather than
     * requiring detailed review.
     *
     * <ul>
     *   <li>AI proposes ready-to-execute actions</li>
     *   <li>Human approves or rejects</li>
     *   <li>Timeout can escalate or cancel</li>
     *   <li>Medium latency, low risk</li>
     * </ul>
     */
    CONFIRM(1, "Confirmation", true, false, false),

    /**
     * Notification level - AI acts, human is informed.
     *
     * <p>The AI executes actions autonomously but notifies humans of
     * what was done. Humans can review and reverse if needed.
     *
     * <ul>
     *   <li>AI acts immediately</li>
     *   <li>Human receives notification</li>
     *   <li>Actions can be reversed</li>
     *   <li>Low latency, medium risk</li>
     * </ul>
     */
    NOTIFY(2, "Notification", false, false, true),

    /**
     * Autonomous level - AI acts independently.
     *
     * <p>The AI has full autonomy to act without human involvement.
     * Actions are logged for audit purposes but no notifications are
     * sent unless anomalies are detected.
     *
     * <ul>
     *   <li>AI acts without oversight</li>
     *   <li>Full audit trail maintained</li>
     *   <li>Alerts only on anomalies</li>
     *   <li>No latency, higher risk</li>
     * </ul>
     */
    AUTONOMOUS(3, "Autonomous", false, false, false);

    private final int ordinalLevel;
    private final String displayName;
    private final boolean requiresApproval;
    private final boolean requiresSelection;
    private final boolean requiresNotification;

    AutonomyLevel(
            int ordinalLevel,
            String displayName,
            boolean requiresApproval,
            boolean requiresSelection,
            boolean requiresNotification) {
        this.ordinalLevel = ordinalLevel;
        this.displayName = displayName;
        this.requiresApproval = requiresApproval;
        this.requiresSelection = requiresSelection;
        this.requiresNotification = requiresNotification;
    }

    /**
     * Gets the numeric level (0-3, higher = more autonomous).
     *
     * @return the ordinal level
     */
    public int getOrdinalLevel() {
        return ordinalLevel;
    }

    /**
     * Gets the display name for UI purposes.
     *
     * @return human-readable name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this level requires human approval before action.
     *
     * @return true if approval is needed
     */
    public boolean requiresHumanApproval() {
        return requiresApproval;
    }

    /**
     * Checks if this level requires human selection from options.
     *
     * @return true if selection is needed
     */
    public boolean requiresHumanSelection() {
        return requiresSelection;
    }

    /**
     * Checks if this level requires notification after action.
     *
     * @return true if notification should be sent
     */
    public boolean requiresNotification() {
        return requiresNotification;
    }

    /**
     * Checks if the AI can act immediately at this level.
     *
     * @return true if AI can act without waiting
     */
    public boolean canActImmediately() {
        return !requiresApproval;
    }

    /**
     * Checks if this level is more autonomous than another.
     *
     * @param other the level to compare
     * @return true if this level is more autonomous
     */
    public boolean isMoreAutonomousThan(AutonomyLevel other) {
        return this.ordinalLevel > other.ordinalLevel;
    }

    /**
     * Checks if this level is less autonomous than another.
     *
     * @param other the level to compare
     * @return true if this level is less autonomous
     */
    public boolean isLessAutonomousThan(AutonomyLevel other) {
        return this.ordinalLevel < other.ordinalLevel;
    }

    /**
     * Returns the next higher autonomy level (upgrade).
     *
     * @return the next level, or AUTONOMOUS if already at max
     */
    public AutonomyLevel upgrade() {
        return switch (this) {
            case SUGGEST -> CONFIRM;
            case CONFIRM -> NOTIFY;
            case NOTIFY, AUTONOMOUS -> AUTONOMOUS;
        };
    }

    /**
     * Returns the next lower autonomy level (downgrade).
     *
     * @return the previous level, or SUGGEST if already at min
     */
    public AutonomyLevel downgrade() {
        return switch (this) {
            case AUTONOMOUS -> NOTIFY;
            case NOTIFY -> CONFIRM;
            case CONFIRM, SUGGEST -> SUGGEST;
        };
    }

    /**
     * Returns the minimum level (most restricted).
     *
     * @return SUGGEST
     */
    public static AutonomyLevel minimum() {
        return SUGGEST;
    }

    /**
     * Returns the maximum level (most autonomous).
     *
     * @return AUTONOMOUS
     */
    public static AutonomyLevel maximum() {
        return AUTONOMOUS;
    }

    /**
     * Returns a level from its ordinal value.
     *
     * @param ordinal the ordinal (0-3)
     * @return the corresponding level
     * @throws IllegalArgumentException if ordinal is invalid
     */
    public static AutonomyLevel fromOrdinal(int ordinal) {
        for (AutonomyLevel level : values()) {
            if (level.ordinalLevel == ordinal) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid autonomy level ordinal: " + ordinal);
    }

    /**
     * Returns the more restrictive of two levels.
     *
     * @param a first level
     * @param b second level
     * @return the level with lower ordinal
     */
    public static AutonomyLevel moreRestrictive(AutonomyLevel a, AutonomyLevel b) {
        return a.ordinalLevel <= b.ordinalLevel ? a : b;
    }

    /**
     * Returns the more autonomous of two levels.
     *
     * @param a first level
     * @param b second level
     * @return the level with higher ordinal
     */
    public static AutonomyLevel moreAutonomous(AutonomyLevel a, AutonomyLevel b) {
        return a.ordinalLevel >= b.ordinalLevel ? a : b;
    }

    @Override
    public String toString() {
        return String.format("AutonomyLevel{%s, ordinal=%d, requiresApproval=%s}",
                displayName, ordinalLevel, requiresApproval);
    }
}
