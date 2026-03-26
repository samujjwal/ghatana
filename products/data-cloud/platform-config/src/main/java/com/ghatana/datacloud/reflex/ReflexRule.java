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

package com.ghatana.datacloud.reflex;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a reflex rule: a condition-action pair for automatic response.
 *
 * <p>Reflex rules are learned from deliberative processing and encode
 * fast-path responses to recognized situations. They trade flexibility
 * for speed.
 *
 * <h2>Rule Components</h2>
 * <ul>
 *   <li><b>Condition</b>: When should this rule fire?</li>
 *   <li><b>Action</b>: What should happen when it fires?</li>
 *   <li><b>Constraints</b>: Safety bounds and execution limits</li>
 *   <li><b>Learning</b>: How was this rule acquired?</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ReflexRule rule = ReflexRule.builder()
 *     .id("rfx-circuit-breaker")
 *     .name("Error Rate Circuit Breaker")
 *     .condition(Condition.builder()
 *         .expression("error_rate > 0.5")
 *         .patternId("high-error-pattern")
 *         .build())
 *     .action(Action.builder()
 *         .type(ActionType.ALERT)
 *         .parameters(Map.of("severity", "critical"))
 *         .build())
 *     .enabled(true)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Reflex condition-action rule
 * @doc.layer core
 * @doc.pattern Rule, Strategy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class ReflexRule {

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unique identifier for this rule.
     */
    String id;

    /**
     * Human-readable name.
     */
    String name;

    /**
     * Description of what this rule does.
     */
    String description;

    /**
     * Category for grouping rules.
     */
    @Builder.Default
    String category = "general";

    /**
     * The tenant this rule belongs to.
     */
    String tenantId;

    // ═══════════════════════════════════════════════════════════════════════════
    // Condition
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The condition that triggers this rule.
     */
    Condition condition;

    /**
     * Trigger types this rule responds to.
     */
    @Builder.Default
    List<ReflexTrigger.TriggerType> triggerTypes = List.of(ReflexTrigger.TriggerType.RECORD);

    // ═══════════════════════════════════════════════════════════════════════════
    // Action
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The action to execute when triggered.
     */
    Action action;

    /**
     * Actions to execute in sequence (if multiple).
     */
    @Builder.Default
    List<Action> actions = List.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Constraints
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether this rule is enabled.
     */
    @Builder.Default
    boolean enabled = true;

    /**
     * Priority for rule ordering (lower = higher priority).
     */
    @Builder.Default
    int priority = 5;

    /**
     * Minimum interval between activations.
     */
    @Builder.Default
    Duration cooldown = Duration.ZERO;

    /**
     * Maximum executions per time window.
     */
    @Builder.Default
    int maxExecutionsPerWindow = 100;

    /**
     * Time window for execution limit.
     */
    @Builder.Default
    Duration executionWindow = Duration.ofMinutes(1);

    /**
     * Maximum execution time before timeout.
     */
    @Builder.Default
    Duration timeout = Duration.ofSeconds(5);

    /**
     * Whether action is reversible.
     */
    @Builder.Default
    boolean reversible = true;

    /**
     * Risk level of this rule.
     */
    @Builder.Default
    RiskLevel riskLevel = RiskLevel.LOW;

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the rule was created.
     */
    @Builder.Default
    Instant createdAt = Instant.now();

    /**
     * When the rule was last updated.
     */
    @Builder.Default
    Instant updatedAt = Instant.now();

    /**
     * When the rule was last triggered.
     */
    Instant lastTriggeredAt;

    /**
     * How this rule was learned.
     */
    @Builder.Default
    LearningSource learningSource = LearningSource.MANUAL;

    /**
     * Related pattern ID (if learned from pattern).
     */
    String sourcePatternId;

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Total times this rule has been triggered.
     */
    @Builder.Default
    long triggerCount = 0;

    /**
     * Successful executions.
     */
    @Builder.Default
    long successCount = 0;

    /**
     * Failed executions.
     */
    @Builder.Default
    long failureCount = 0;

    /**
     * Average execution time in milliseconds.
     */
    @Builder.Default
    double avgExecutionTimeMs = 0;

    /**
     * Confidence in this rule (0.0 to 1.0).
     */
    @Builder.Default
    float confidence = 1.0f;

    // ═══════════════════════════════════════════════════════════════════════════
    // Computed Properties
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the success rate of this rule.
     *
     * @return success rate (0.0 to 1.0)
     */
    public float getSuccessRate() {
        long total = successCount + failureCount;
        return total > 0 ? (float) successCount / total : 0.0f;
    }

    /**
     * Checks if the rule is ready to fire (not in cooldown).
     *
     * @return true if ready
     */
    public boolean isReady() {
        if (!enabled) return false;
        if (lastTriggeredAt == null) return true;
        return Instant.now().isAfter(lastTriggeredAt.plus(cooldown));
    }

    /**
     * Records a successful execution.
     *
     * @param executionTimeMs execution time
     * @return updated rule
     */
    public ReflexRule recordSuccess(long executionTimeMs) {
        long newTotal = successCount + failureCount + 1;
        double newAvg = (avgExecutionTimeMs * (newTotal - 1) + executionTimeMs) / newTotal;

        return this.toBuilder()
                .triggerCount(triggerCount + 1)
                .successCount(successCount + 1)
                .avgExecutionTimeMs(newAvg)
                .lastTriggeredAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Records a failed execution.
     *
     * @return updated rule
     */
    public ReflexRule recordFailure() {
        return this.toBuilder()
                .triggerCount(triggerCount + 1)
                .failureCount(failureCount + 1)
                .lastTriggeredAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Gets all actions (single action or action list).
     *
     * @return list of actions
     */
    public List<Action> getAllActions() {
        if (action != null) {
            return List.of(action);
        }
        return actions;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Condition for rule triggering.
     */
    @Value
    @Builder
    public static class Condition {
        /**
         * Expression to evaluate (e.g., "error_rate > 0.5").
         */
        String expression;

        /**
         * Pattern ID to match.
         */
        String patternId;

        /**
         * Required features with expected values.
         */
        @Builder.Default
        Map<String, Object> requiredFeatures = Map.of();

        /**
         * Minimum confidence for pattern match.
         */
        @Builder.Default
        float minConfidence = 0.0f;

        /**
         * Whether all conditions must match (AND) or any (OR).
         */
        @Builder.Default
        boolean matchAll = true;

        /**
         * Sub-conditions for complex rules.
         */
        @Builder.Default
        List<Condition> subConditions = List.of();
    }

    /**
     * Action to execute when rule fires.
     */
    @Value
    @Builder
    public static class Action {
        /**
         * Type of action.
         */
        ActionType type;

        /**
         * Action target (endpoint, topic, etc.).
         */
        String target;

        /**
         * Action parameters.
         */
        @Builder.Default
        Map<String, Object> parameters = Map.of();

        /**
         * Template for action payload.
         */
        String payloadTemplate;

        /**
         * Whether action should be async.
         */
        @Builder.Default
        boolean async = false;

        /**
         * Retry count on failure.
         */
        @Builder.Default
        int retries = 0;

        /**
         * Creates an alert action.
         *
         * @param severity the alert severity
         * @param message the alert message
         * @return alert action
         */
        public static Action alert(String severity, String message) {
            return Action.builder()
                    .type(ActionType.ALERT)
                    .parameters(Map.of("severity", severity, "message", message))
                    .build();
        }

        /**
         * Creates a webhook action.
         *
         * @param url the webhook URL
         * @param payload the payload
         * @return webhook action
         */
        public static Action webhook(String url, Map<String, Object> payload) {
            return Action.builder()
                    .type(ActionType.WEBHOOK)
                    .target(url)
                    .parameters(payload)
                    .build();
        }
    }

    /**
     * Types of actions.
     */
    public enum ActionType {
        /**
         * Send an alert/notification.
         */
        ALERT,

        /**
         * Call a webhook.
         */
        WEBHOOK,

        /**
         * Publish to a topic.
         */
        PUBLISH,

        /**
         * Execute a function/handler.
         */
        EXECUTE,

        /**
         * Store data.
         */
        STORE,

        /**
         * Transform data.
         */
        TRANSFORM,

        /**
         * Route to different processing.
         */
        ROUTE,

        /**
         * Log the event.
         */
        LOG,

        /**
         * Suppress/drop the event.
         */
        SUPPRESS,

        /**
         * Escalate to human operator.
         */
        ESCALATE
    }

    /**
     * Risk levels for rules.
     */
    public enum RiskLevel {
        /**
         * No-risk, safe to execute automatically.
         */
        LOW,

        /**
         * Medium risk, some caution needed.
         */
        MEDIUM,

        /**
         * High risk, requires approval.
         */
        HIGH,

        /**
         * Critical risk, human approval required.
         */
        CRITICAL
    }

    /**
     * How the rule was learned.
     */
    public enum LearningSource {
        /**
         * Created manually by user.
         */
        MANUAL,

        /**
         * Learned from pattern.
         */
        PATTERN,

        /**
         * Learned from reinforcement.
         */
        REINFORCEMENT,

        /**
         * Learned from imitation.
         */
        IMITATION,

        /**
         * Learned from feedback.
         */
        FEEDBACK,

        /**
         * Imported from template.
         */
        TEMPLATE
    }
}
