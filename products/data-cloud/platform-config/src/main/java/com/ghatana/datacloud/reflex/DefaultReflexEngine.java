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
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of the ReflexEngine.
 *
 * <p>Provides fast-path trigger processing with priority-based rule
 * matching and action execution.
 *
 * @doc.type class
 * @doc.purpose Default reflex engine implementation
 * @doc.layer core
 * @doc.pattern Engine, Orchestrator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class DefaultReflexEngine implements ReflexEngine {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReflexEngine.class);

    // Rule storage: tenantId -> ruleId -> ReflexRule
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ReflexRule>> rules;

    // Action handlers by type
    private final EnumMap<ReflexRule.ActionType, ActionHandler> handlers;

    // The production alert handler (exposed for querying alerts)
    private final AlertActionHandler alertHandler;

    // Statistics
    private volatile long triggersProcessed = 0;
    private volatile long rulesMatched = 0;
    private volatile long actionsExecuted = 0;
    private volatile long successfulActions = 0;
    private volatile long failedActions = 0;
    private volatile double totalProcessingTime = 0;

    /**
     * Creates a new reflex engine with default handlers and
     * a default {@link AlertActionHandler}.
     */
    public DefaultReflexEngine() {
        this(new AlertActionHandler());
    }

    /**
     * Creates a new reflex engine with a custom {@link AlertActionHandler}.
     *
     * @param alertHandler the alert handler to use (non-null)
     */
    public DefaultReflexEngine(AlertActionHandler alertHandler) {
        this.rules = new ConcurrentHashMap<>();
        this.handlers = new EnumMap<>(ReflexRule.ActionType.class);
        this.alertHandler = alertHandler;

        // Register default handlers
        registerDefaultHandlers();
    }

    /**
     * Returns the alert handler for querying stored alerts.
     *
     * @return the alert action handler
     */
    public AlertActionHandler getAlertHandler() {
        return alertHandler;
    }

    private void registerDefaultHandlers() {
        // LOG handler
        registerActionHandler(ReflexRule.ActionType.LOG, (action, trigger, rule) -> {
            Instant start = Instant.now();
            LOG.info("Reflex LOG: rule={}, trigger={}, params={}",
                    rule.getId(), trigger.getTriggerId(), action.getParameters());
            return Promise.of(ReflexOutcome.success(
                    rule.getId(),
                    trigger.getTriggerId(),
                    action.getType(),
                    start,
                    Map.of("logged", true),
                    trigger.getTenantId()));
        });

        // ALERT handler (production — backed by AlertActionHandler)
        registerActionHandler(ReflexRule.ActionType.ALERT, alertHandler);

        // SUPPRESS handler
        registerActionHandler(ReflexRule.ActionType.SUPPRESS, (action, trigger, rule) -> {
            Instant start = Instant.now();
            LOG.debug("Reflex SUPPRESS: rule={}, trigger={}", rule.getId(), trigger.getTriggerId());
            return Promise.of(ReflexOutcome.success(
                    rule.getId(),
                    trigger.getTriggerId(),
                    action.getType(),
                    start,
                    Map.of("suppressed", true),
                    trigger.getTenantId()));
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Trigger Processing
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<ExecutionResult> process(ReflexTrigger trigger) {
        long startTime = System.currentTimeMillis();
        triggersProcessed++;

        return findMatchingRules(trigger)
                .then(matchedRules -> {
                    if (matchedRules.isEmpty()) {
                        return Promise.of(ExecutionResult.builder()
                                .trigger(trigger)
                                .matchedRules(List.of())
                                .outcomes(List.of())
                                .processingTimeMs(System.currentTimeMillis() - startTime)
                                .executed(false)
                                .build());
                    }

                    rulesMatched += matchedRules.size();

                    // Execute actions for matching rules in priority order
                    List<Promise<ReflexOutcome>> outcomePromises = new ArrayList<>();

                    for (ReflexRule rule : matchedRules) {
                        for (ReflexRule.Action action : rule.getAllActions()) {
                            Promise<ReflexOutcome> outcomePromise = executeActionInternal(action, trigger, rule);
                            outcomePromises.add(outcomePromise);
                        }
                    }

                    return Promises.toList(outcomePromises)
                            .map(outcomes -> {
                                long processingTime = System.currentTimeMillis() - startTime;
                                totalProcessingTime += processingTime;

                                return ExecutionResult.builder()
                                        .trigger(trigger)
                                        .matchedRules(matchedRules)
                                        .outcomes(outcomes)
                                        .processingTimeMs(processingTime)
                                        .executed(true)
                                        .build();
                            });
                });
    }

    @Override
    public Promise<BatchResult> processBatch(List<ReflexTrigger> triggers) {
        long startTime = System.currentTimeMillis();

        List<Promise<ExecutionResult>> resultPromises = triggers.stream()
                .map(this::process)
                .collect(Collectors.toList());

        return Promises.toList(resultPromises)
                .map(results -> {
                    int triggersWithMatches = 0;
                    int actionsExec = 0;
                    int successful = 0;

                    for (ExecutionResult result : results) {
                        if (result.isExecuted()) triggersWithMatches++;
                        actionsExec += result.getOutcomes().size();
                        successful += result.successCount();
                    }

                    return BatchResult.builder()
                            .results(results)
                            .totalProcessed(triggers.size())
                            .triggersWithMatches(triggersWithMatches)
                            .actionsExecuted(actionsExec)
                            .successfulActions(successful)
                            .totalProcessingTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                });
    }

    @Override
    public Promise<List<ReflexRule>> findMatchingRules(ReflexTrigger trigger) {
        String tenantId = trigger.getTenantId() != null ? trigger.getTenantId() : "default";

        return Promise.of(
                getTenantRules(tenantId).values().stream()
                        .filter(ReflexRule::isEnabled)
                        .filter(ReflexRule::isReady)
                        .filter(rule -> matchesTriggerType(rule, trigger))
                        .filter(rule -> evaluateCondition(rule.getCondition(), trigger))
                        .sorted(Comparator.comparingInt(ReflexRule::getPriority))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<MatchResult> evaluate(ReflexTrigger trigger, ReflexRule rule) {
        if (!rule.isEnabled()) {
            return Promise.of(MatchResult.noMatch("Rule is disabled"));
        }

        if (!rule.isReady()) {
            return Promise.of(MatchResult.noMatch("Rule is in cooldown"));
        }

        if (!matchesTriggerType(rule, trigger)) {
            return Promise.of(MatchResult.noMatch("Trigger type does not match"));
        }

        if (!evaluateCondition(rule.getCondition(), trigger)) {
            return Promise.of(MatchResult.noMatch("Condition not satisfied"));
        }

        return Promise.of(MatchResult.match(trigger.getConfidence() * rule.getConfidence()));
    }

    private boolean matchesTriggerType(ReflexRule rule, ReflexTrigger trigger) {
        if (rule.getTriggerTypes().isEmpty()) return true;
        return rule.getTriggerTypes().contains(trigger.getType());
    }

    private boolean evaluateCondition(ReflexRule.Condition condition, ReflexTrigger trigger) {
        if (condition == null) return true;

        // Check pattern ID
        if (condition.getPatternId() != null) {
            if (!condition.getPatternId().equals(trigger.getPatternId())) {
                return false;
            }
        }

        // Check confidence threshold
        if (trigger.getConfidence() < condition.getMinConfidence()) {
            return false;
        }

        // Check required features
        if (!condition.getRequiredFeatures().isEmpty()) {
            for (Map.Entry<String, Object> entry : condition.getRequiredFeatures().entrySet()) {
                Object actual = trigger.getFeatures().get(entry.getKey());
                if (!entry.getValue().equals(actual)) {
                    if (condition.isMatchAll()) {
                        return false;
                    }
                } else if (!condition.isMatchAll()) {
                    return true; // Any match is enough
                }
            }
            if (!condition.isMatchAll()) {
                return false; // No matches found for OR logic
            }
        }

        // Evaluate sub-conditions
        if (!condition.getSubConditions().isEmpty()) {
            boolean anyMatch = false;
            for (ReflexRule.Condition sub : condition.getSubConditions()) {
                boolean subResult = evaluateCondition(sub, trigger);
                if (condition.isMatchAll() && !subResult) {
                    return false;
                }
                if (subResult) anyMatch = true;
            }
            if (!condition.isMatchAll() && !anyMatch) {
                return false;
            }
        }

        return true;
    }

    private Promise<ReflexOutcome> executeActionInternal(
            ReflexRule.Action action,
            ReflexTrigger trigger,
            ReflexRule rule) {

        actionsExecuted++;

        ActionHandler handler = handlers.get(action.getType());
        if (handler == null) {
            LOG.warn("No handler for action type: {}", action.getType());
            failedActions++;
            return Promise.of(ReflexOutcome.failure(
                    rule.getId(),
                    trigger.getTriggerId(),
                    action.getType(),
                    Instant.now(),
                    new IllegalStateException("No handler for action type: " + action.getType()),
                    trigger.getTenantId()));
        }

        return handler.execute(action, trigger, rule)
                .map(outcome -> {
                    if (outcome.isSuccess()) {
                        successfulActions++;
                    } else {
                        failedActions++;
                    }

                    // Update rule statistics
                    String tenantId = trigger.getTenantId() != null ? trigger.getTenantId() : "default";
                    ConcurrentHashMap<String, ReflexRule> tenantRules = getTenantRules(tenantId);
                    ReflexRule updated = outcome.isSuccess()
                            ? rule.recordSuccess(outcome.getExecutionTimeMs())
                            : rule.recordFailure();
                    tenantRules.put(rule.getId(), updated);

                    return outcome;
                })
                .whenException(e -> {
                    failedActions++;
                    LOG.error("Action execution failed: rule={}, action={}", rule.getId(), action.getType(), e);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rule Management
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<ReflexRule> registerRule(ReflexRule rule) {
        String tenantId = rule.getTenantId() != null ? rule.getTenantId() : "default";
        String ruleId = rule.getId() != null ? rule.getId() : "rfx-" + System.nanoTime();

        ReflexRule toStore = rule.toBuilder()
                .id(ruleId)
                .tenantId(tenantId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        getTenantRules(tenantId).put(ruleId, toStore);
        LOG.info("Registered reflex rule: {} for tenant {}", ruleId, tenantId);

        return Promise.of(toStore);
    }

    @Override
    public Promise<ReflexRule> updateRule(ReflexRule rule) {
        String tenantId = rule.getTenantId() != null ? rule.getTenantId() : "default";

        ReflexRule existing = getTenantRules(tenantId).get(rule.getId());
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Rule not found: " + rule.getId()));
        }

        ReflexRule updated = rule.toBuilder()
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now())
                .triggerCount(existing.getTriggerCount())
                .successCount(existing.getSuccessCount())
                .failureCount(existing.getFailureCount())
                .build();

        getTenantRules(tenantId).put(rule.getId(), updated);
        LOG.info("Updated reflex rule: {}", rule.getId());

        return Promise.of(updated);
    }

    @Override
    public Promise<Boolean> removeRule(String ruleId, String tenantId) {
        String tenant = tenantId != null ? tenantId : "default";
        ReflexRule removed = getTenantRules(tenant).remove(ruleId);

        if (removed != null) {
            LOG.info("Removed reflex rule: {} from tenant {}", ruleId, tenant);
        }

        return Promise.of(removed != null);
    }

    @Override
    public Promise<Optional<ReflexRule>> getRule(String ruleId, String tenantId) {
        String tenant = tenantId != null ? tenantId : "default";
        return Promise.of(Optional.ofNullable(getTenantRules(tenant).get(ruleId)));
    }

    @Override
    public Promise<List<ReflexRule>> listRules(String tenantId) {
        String tenant = tenantId != null ? tenantId : "default";
        return Promise.of(new ArrayList<>(getTenantRules(tenant).values()));
    }

    @Override
    public Promise<ReflexRule> enableRule(String ruleId, String tenantId) {
        return getRule(ruleId, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Rule not found: " + ruleId));
                    }
                    return updateRule(opt.get().toBuilder().enabled(true).build());
                });
    }

    @Override
    public Promise<ReflexRule> disableRule(String ruleId, String tenantId) {
        return getRule(ruleId, tenantId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Rule not found: " + ruleId));
                    }
                    return updateRule(opt.get().toBuilder().enabled(false).build());
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void registerActionHandler(ReflexRule.ActionType actionType, ActionHandler handler) {
        handlers.put(actionType, handler);
        LOG.debug("Registered action handler for: {}", actionType);
    }

    @Override
    public Optional<ActionHandler> getActionHandler(ReflexRule.ActionType actionType) {
        return Optional.ofNullable(handlers.get(actionType));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<EngineStats> getStats(String tenantId) {
        String tenant = tenantId != null ? tenantId : "default";
        ConcurrentHashMap<String, ReflexRule> tenantRules = getTenantRules(tenant);

        Map<String, Integer> byCategory = new HashMap<>();
        Map<ReflexRule.ActionType, Integer> byActionType = new EnumMap<>(ReflexRule.ActionType.class);
        int enabled = 0;

        for (ReflexRule rule : tenantRules.values()) {
            if (rule.isEnabled()) enabled++;
            byCategory.merge(rule.getCategory(), 1, Integer::sum);

            for (ReflexRule.Action action : rule.getAllActions()) {
                byActionType.merge(action.getType(), 1, Integer::sum);
            }
        }

        return Promise.of(EngineStats.builder()
                .totalRules(tenantRules.size())
                .enabledRules(enabled)
                .triggersProcessed(triggersProcessed)
                .rulesMatched(rulesMatched)
                .actionsExecuted(actionsExecuted)
                .successfulActions(successfulActions)
                .failedActions(failedActions)
                .avgProcessingTimeMs(triggersProcessed > 0 ? totalProcessingTime / triggersProcessed : 0)
                .rulesByCategory(byCategory)
                .rulesByActionType(byActionType)
                .build());
    }

    @Override
    public Promise<Void> clearAll(String tenantId) {
        String tenant = tenantId != null ? tenantId : "default";
        getTenantRules(tenant).clear();
        LOG.info("Cleared all rules for tenant: {}", tenant);
        return Promise.complete();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private ConcurrentHashMap<String, ReflexRule> getTenantRules(String tenantId) {
        String key = tenantId != null ? tenantId : "default";
        return rules.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }
}
