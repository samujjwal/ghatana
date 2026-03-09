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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Engine for reflex detection and execution.
 *
 * <p>The ReflexEngine orchestrates the fast-path processing of triggers
 * through reflex rules. It matches incoming triggers against rules and
 * executes the corresponding actions.
 *
 * <h2>Processing Flow</h2>
 * <pre>
 * Trigger → Match Rules → Evaluate Conditions → Execute Actions → Record Outcomes
 * </pre>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Sub-millisecond trigger matching</li>
 *   <li>Priority-based rule ordering</li>
 *   <li>Rate limiting and cooldown</li>
 *   <li>Action execution with timeout</li>
 *   <li>Outcome tracking</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Reflex detection and execution engine
 * @doc.layer core
 * @doc.pattern Engine, Orchestrator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface ReflexEngine {

    /**
     * Processes a trigger and executes matching reflexes.
     *
     * @param trigger the trigger to process
     * @return Promise containing execution results
     */
    Promise<ExecutionResult> process(ReflexTrigger trigger);

    /**
     * Processes multiple triggers in batch.
     *
     * @param triggers the triggers to process
     * @return Promise containing batch results
     */
    Promise<BatchResult> processBatch(List<ReflexTrigger> triggers);

    /**
     * Finds rules that would match a trigger (without executing).
     *
     * @param trigger the trigger to match
     * @return Promise containing matching rules
     */
    Promise<List<ReflexRule>> findMatchingRules(ReflexTrigger trigger);

    /**
     * Evaluates a specific rule against a trigger.
     *
     * @param trigger the trigger
     * @param rule the rule to evaluate
     * @return Promise containing match result
     */
    Promise<MatchResult> evaluate(ReflexTrigger trigger, ReflexRule rule);

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a new reflex rule.
     *
     * @param rule the rule to register
     * @return Promise containing the registered rule
     */
    Promise<ReflexRule> registerRule(ReflexRule rule);

    /**
     * Updates an existing rule.
     *
     * @param rule the updated rule
     * @return Promise containing the updated rule
     */
    Promise<ReflexRule> updateRule(ReflexRule rule);

    /**
     * Removes a rule.
     *
     * @param ruleId the rule ID
     * @param tenantId the tenant ID
     * @return Promise containing success status
     */
    Promise<Boolean> removeRule(String ruleId, String tenantId);

    /**
     * Gets a rule by ID.
     *
     * @param ruleId the rule ID
     * @param tenantId the tenant ID
     * @return Promise containing the rule if found
     */
    Promise<Optional<ReflexRule>> getRule(String ruleId, String tenantId);

    /**
     * Lists all rules for a tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise containing all rules
     */
    Promise<List<ReflexRule>> listRules(String tenantId);

    /**
     * Enables a rule.
     *
     * @param ruleId the rule ID
     * @param tenantId the tenant ID
     * @return Promise containing the updated rule
     */
    Promise<ReflexRule> enableRule(String ruleId, String tenantId);

    /**
     * Disables a rule.
     *
     * @param ruleId the rule ID
     * @param tenantId the tenant ID
     * @return Promise containing the updated rule
     */
    Promise<ReflexRule> disableRule(String ruleId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers an action handler for an action type.
     *
     * @param actionType the action type
     * @param handler the handler
     */
    void registerActionHandler(ReflexRule.ActionType actionType, ActionHandler handler);

    /**
     * Gets the handler for an action type.
     *
     * @param actionType the action type
     * @return the handler if registered
     */
    Optional<ActionHandler> getActionHandler(ReflexRule.ActionType actionType);

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets engine statistics.
     *
     * @param tenantId the tenant ID
     * @return Promise containing statistics
     */
    Promise<EngineStats> getStats(String tenantId);

    /**
     * Clears all rules (use with caution).
     *
     * @param tenantId the tenant ID
     * @return Promise completing when cleared
     */
    Promise<Void> clearAll(String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of processing a single trigger.
     */
    @Value
    @Builder
    class ExecutionResult {
        /**
         * The processed trigger.
         */
        ReflexTrigger trigger;

        /**
         * Rules that matched.
         */
        List<ReflexRule> matchedRules;

        /**
         * Outcomes of rule executions.
         */
        List<ReflexOutcome> outcomes;

        /**
         * Total processing time in milliseconds.
         */
        long processingTimeMs;

        /**
         * Whether any rule was executed.
         */
        boolean executed;

        /**
         * Checks if all executions succeeded.
         *
         * @return true if all succeeded
         */
        public boolean allSucceeded() {
            return outcomes.stream().allMatch(ReflexOutcome::isSuccess);
        }

        /**
         * Gets the number of successful executions.
         *
         * @return success count
         */
        public long successCount() {
            return outcomes.stream().filter(ReflexOutcome::isSuccess).count();
        }
    }

    /**
     * Result of batch processing.
     */
    @Value
    @Builder
    class BatchResult {
        /**
         * Individual results.
         */
        List<ExecutionResult> results;

        /**
         * Total triggers processed.
         */
        int totalProcessed;

        /**
         * Triggers with matches.
         */
        int triggersWithMatches;

        /**
         * Total actions executed.
         */
        int actionsExecuted;

        /**
         * Successful actions.
         */
        int successfulActions;

        /**
         * Total processing time in milliseconds.
         */
        long totalProcessingTimeMs;
    }

    /**
     * Result of rule matching.
     */
    @Value
    @Builder
    class MatchResult {
        /**
         * Whether the rule matched.
         */
        boolean matched;

        /**
         * Match confidence.
         */
        float confidence;

        /**
         * Which conditions matched.
         */
        List<String> matchedConditions;

        /**
         * Which conditions failed.
         */
        List<String> failedConditions;

        /**
         * Reason for non-match (if applicable).
         */
        String reason;

        /**
         * Creates a match result.
         *
         * @param confidence the match confidence
         * @return match result
         */
        public static MatchResult match(float confidence) {
            return MatchResult.builder()
                    .matched(true)
                    .confidence(confidence)
                    .build();
        }

        /**
         * Creates a no-match result.
         *
         * @param reason the reason
         * @return no-match result
         */
        public static MatchResult noMatch(String reason) {
            return MatchResult.builder()
                    .matched(false)
                    .confidence(0)
                    .reason(reason)
                    .build();
        }
    }

    /**
     * Handler for executing actions.
     */
    @FunctionalInterface
    interface ActionHandler {
        /**
         * Executes an action.
         *
         * @param action the action to execute
         * @param trigger the trigger context
         * @param rule the rule context
         * @return Promise containing the outcome
         */
        Promise<ReflexOutcome> execute(
                ReflexRule.Action action,
                ReflexTrigger trigger,
                ReflexRule rule);
    }

    /**
     * Engine statistics.
     */
    @Value
    @Builder
    class EngineStats {
        /**
         * Total rules registered.
         */
        int totalRules;

        /**
         * Enabled rules.
         */
        int enabledRules;

        /**
         * Total triggers processed.
         */
        long triggersProcessed;

        /**
         * Total rules matched.
         */
        long rulesMatched;

        /**
         * Total actions executed.
         */
        long actionsExecuted;

        /**
         * Successful actions.
         */
        long successfulActions;

        /**
         * Failed actions.
         */
        long failedActions;

        /**
         * Average processing time in milliseconds.
         */
        double avgProcessingTimeMs;

        /**
         * Rules by category.
         */
        Map<String, Integer> rulesByCategory;

        /**
         * Rules by action type.
         */
        Map<ReflexRule.ActionType, Integer> rulesByActionType;
    }
}
