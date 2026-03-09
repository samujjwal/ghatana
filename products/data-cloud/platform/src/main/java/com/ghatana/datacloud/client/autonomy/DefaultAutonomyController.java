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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of the Autonomy Controller.
 *
 * <p>Provides graduated autonomy management with configurable policies,
 * tracking of action outcomes, and automatic level transitions based on
 * accumulated evidence.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Per-action-type autonomy tracking</li>
 *   <li>Configurable upgrade/downgrade policies</li>
 *   <li>Action gating with approval workflow</li>
 *   <li>Outcome-based confidence building</li>
 *   <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * AutonomyController controller = new DefaultAutonomyController();
 * 
 * // Gate an action
 * GateRequest request = GateRequest.builder()
 *     .actionType("alert.auto-resolve")
 *     .tenantId("tenant-123")
 *     .impactScore(0.3)
 *     .build();
 * 
 * controller.gate(request)
 *     .whenResult(decision -> {
 *         if (decision.canProceed()) {
 *             executeAction();
 *         }
 *     });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Default autonomy controller implementation
 * @doc.layer core
 * @doc.pattern State Machine, Controller
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class DefaultAutonomyController implements AutonomyController {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAutonomyController.class);

    // State storage: key = actionType:tenantId
    private final ConcurrentHashMap<String, MutableState> states = new ConcurrentHashMap<>();

    // Logs storage
    private final java.util.concurrent.CopyOnWriteArrayList<AutonomyLog> logs = new java.util.concurrent.CopyOnWriteArrayList<>();

    // Policy storage
    private final ConcurrentHashMap<String, AutonomyPolicy> policies = new ConcurrentHashMap<>();
    private AutonomyPolicy defaultPolicy = AutonomyPolicy.balanced();

    // Statistics
    private final AtomicLong totalActionsGated = new AtomicLong();
    private final AtomicLong actionsAllowed = new AtomicLong();
    private final AtomicLong actionsBlocked = new AtomicLong();
    private final AtomicLong totalUpgrades = new AtomicLong();
    private final AtomicLong totalDowngrades = new AtomicLong();

    // Pending approvals: approvalId -> callback
    private final ConcurrentHashMap<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    /**
     * Mutable internal state for tracking.
     */
    private static class MutableState {
        final String actionType;
        final String tenantId;
        volatile AutonomyLevel currentLevel;
        volatile Instant levelEnteredAt;
        volatile Instant lastActionAt;
        volatile Instant lastUpgradeAt;
        volatile Instant lastDowngradeAt;
        final AtomicLong totalActions = new AtomicLong();
        final AtomicLong successfulActions = new AtomicLong();
        final AtomicLong failedActions = new AtomicLong();
        final AtomicLong positiveFeedback = new AtomicLong();
        final AtomicLong negativeFeedback = new AtomicLong();
        volatile int upgradeCount = 0;
        volatile int downgradeCount = 0;
        volatile int consecutiveSuccesses = 0;
        volatile int consecutiveFailures = 0;

        MutableState(String actionType, String tenantId, AutonomyLevel initialLevel) {
            this.actionType = actionType;
            this.tenantId = tenantId;
            this.currentLevel = initialLevel;
            this.levelEnteredAt = Instant.now();
        }

        synchronized AutonomyState toImmutable(AutonomyLevel effectiveMax) {
            long total = totalActions.get();
            long successes = successfulActions.get();
            double successRate = total > 0 ? (double) successes / total : 0.0;

            return AutonomyState.builder()
                    .actionType(actionType)
                    .tenantId(tenantId)
                    .currentLevel(currentLevel)
                    .effectiveMaxLevel(effectiveMax)
                    .totalActions((int) total)
                    .successfulActions((int) successes)
                    .failedActions((int) failedActions.get())
                    .successRate(successRate)
                    .confidence(calculateConfidence())
                    .levelEnteredAt(levelEnteredAt)
                    .lastActionAt(lastActionAt)
                    .lastUpgradeAt(lastUpgradeAt)
                    .lastDowngradeAt(lastDowngradeAt)
                    .upgradeCount(upgradeCount)
                    .downgradeCount(downgradeCount)
                    .consecutiveSuccesses(consecutiveSuccesses)
                    .consecutiveFailures(consecutiveFailures)
                    .positiveFeedbackCount((int) positiveFeedback.get())
                    .negativeFeedbackCount((int) negativeFeedback.get())
                    .build();
        }

        double calculateConfidence() {
            long total = totalActions.get();
            if (total == 0) return 0.5;

            double successRate = (double) successfulActions.get() / total;
            double feedbackRatio = positiveFeedback.get() + negativeFeedback.get() > 0
                    ? (double) positiveFeedback.get() / (positiveFeedback.get() + negativeFeedback.get())
                    : 0.5;

            // More data = more confidence in our confidence
            double dataConfidence = Math.min(1.0, total / 100.0);

            return (successRate * 0.6 + feedbackRatio * 0.4) * dataConfidence
                    + 0.5 * (1 - dataConfidence);
        }
    }

    private record PendingApproval(
            String actionId,
            GateRequest request,
            Instant createdAt,
            Duration timeout
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // Level Access
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<AutonomyLevel> getCurrentLevel(String actionType, String tenantId) {
        MutableState state = getOrCreateState(actionType, tenantId);
        return Promise.of(state.currentLevel);
    }

    @Override
    public Promise<AutonomyState> getState(String actionType, String tenantId) {
        MutableState state = getOrCreateState(actionType, tenantId);
        AutonomyPolicy policy = getPolicy(actionType, tenantId);
        return Promise.of(state.toImmutable(policy.getEffectiveMaxLevel(actionType)));
    }

    @Override
    public Promise<List<AutonomyLog>> getLogs(AutonomyLogFilter filter) {
        return Promise.of(logs.stream()
                .filter(log -> filterMatches(log, filter))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(filter.getLimit() != null ? filter.getLimit() : 100)
                .toList());
    }

    private boolean filterMatches(AutonomyLog log, AutonomyLogFilter filter) {
        if (filter.getActionType() != null && !filter.getActionType().equals(log.getActionType())) return false;
        if (filter.getTenantId() != null && !filter.getTenantId().equals(log.getTenantId())) return false;
        if (filter.getStartTime() != null && log.getTimestamp().isBefore(filter.getStartTime())) return false;
        if (filter.getEndTime() != null && log.getTimestamp().isAfter(filter.getEndTime())) return false;
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Action Gating
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<GateDecision> gate(GateRequest request) {
        return Promise.of(request)
                .map(this::gateInternal);
    }

    private GateDecision gateInternal(GateRequest request) {
        totalActionsGated.incrementAndGet();

        String actionType = request.getActionType();
        String tenantId = request.getTenantId();
        AutonomyPolicy policy = getPolicy(actionType, tenantId);

        // Check if action is blocked
        if (policy.isActionBlocked(actionType)) {
            actionsBlocked.incrementAndGet();
            return GateDecision.builder()
                    .actionId(request.getActionId())
                    .allowed(false)
                    .requiredAction(GateDecision.GateAction.BLOCKED)
                    .message("Action type is blocked by policy")
                    .build();
        }

        MutableState state = getOrCreateState(actionType, tenantId);
        AutonomyLevel currentLevel = state.currentLevel;

        // Check impact-based restrictions
        AutonomyLevel maxForImpact = policy.getMaxLevelForImpact(request.getImpactScore());
        AutonomyLevel effectiveLevel = AutonomyLevel.moreRestrictive(currentLevel, maxForImpact);

        // Determine required action based on level
        GateDecision.GateAction requiredAction = switch (effectiveLevel) {
            case AUTONOMOUS -> GateDecision.GateAction.PROCEED;
            case NOTIFY -> GateDecision.GateAction.PROCEED; // Will notify after
            case CONFIRM -> GateDecision.GateAction.AWAIT_APPROVAL;
            case SUGGEST -> GateDecision.GateAction.PRESENT_SUGGESTION;
        };

        boolean allowed = requiredAction == GateDecision.GateAction.PROCEED;
        if (allowed) {
            actionsAllowed.incrementAndGet();
        }

        // Create approval ID if needed
        String approvalId = null;
        if (requiredAction == GateDecision.GateAction.AWAIT_APPROVAL) {
            approvalId = java.util.UUID.randomUUID().toString();
            pendingApprovals.put(approvalId, new PendingApproval(
                    request.getActionId(),
                    request,
                    Instant.now(),
                    request.getMaxWaitTime()
            ));
        }

        LOG.debug("Gate decision for {}/{}: level={}, action={}",
                actionType, tenantId, effectiveLevel, requiredAction);

        logs.add(AutonomyLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .actionType(actionType)
                .tenantId(tenantId)
                .level(effectiveLevel)
                .decision(mapGateActionToDecision(requiredAction))
                .confidence(state.calculateConfidence())
                .context(request.getContext())
                .timestamp(Instant.now())
                .build());

        return GateDecision.builder()
                .actionId(request.getActionId())
                .allowed(allowed)
                .currentLevel(effectiveLevel)
                .requiredAction(requiredAction)
                .approvalId(approvalId)
                .message(buildGateMessage(effectiveLevel, requiredAction))
                .build();
    }

    private String mapGateActionToDecision(GateDecision.GateAction action) {
        return switch (action) {
            case PROCEED -> "ALLOWED";
            case AWAIT_APPROVAL, PRESENT_SUGGESTION -> "ADVISORY";
            case BLOCKED -> "BLOCKED";
            case REJECTED -> "REJECTED";
        };
    }

    private String buildGateMessage(AutonomyLevel level, GateDecision.GateAction action) {
        return switch (action) {
            case PROCEED -> "Action approved at " + level + " level";
            case AWAIT_APPROVAL -> "Action requires approval at " + level + " level";
            case PRESENT_SUGGESTION -> "Action presented as suggestion at " + level + " level";
            case BLOCKED -> "Action blocked by policy";
            case REJECTED -> "Action rejected";
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Outcome Recording
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Void> recordOutcome(ActionOutcome outcome) {
        return Promise.of(outcome)
                .map(o -> {
                    recordOutcomeInternal(o);
                    return null;
                });
    }

    private void recordOutcomeInternal(ActionOutcome outcome) {
        MutableState state = getOrCreateState(outcome.getActionType(), outcome.getTenantId());
        AutonomyPolicy policy = getPolicy(outcome.getActionType(), outcome.getTenantId());

        logs.add(AutonomyLog.builder()
                .id(java.util.UUID.randomUUID().toString())
                .actionType(outcome.getActionType())
                .tenantId(outcome.getTenantId())
                .level(state.currentLevel)
                .decision(outcome.isSuccess() ? "OUTCOME_SUCCESS" : "OUTCOME_FAILURE")
                .confidence(state.calculateConfidence())
                .context(outcome.getMetadata())
                .timestamp(Instant.now())
                .build());

        synchronized (state) {
            state.totalActions.incrementAndGet();
            state.lastActionAt = Instant.now();

            if (outcome.isSuccess()) {
                state.successfulActions.incrementAndGet();
                state.consecutiveSuccesses++;
                state.consecutiveFailures = 0;
            } else {
                state.failedActions.incrementAndGet();
                state.consecutiveFailures++;
                state.consecutiveSuccesses = 0;

                // Check for immediate downgrade
                if (policy.isDowngradeOnFailure()
                        && state.consecutiveFailures >= policy.getConsecutiveFailuresForDowngrade()) {
                    forceDowngradeInternal(state, policy,
                            "Consecutive failures: " + state.consecutiveFailures);
                }
            }

            // Record feedback
            switch (outcome.getFeedbackType()) {
                case POSITIVE_EXPLICIT, POSITIVE_IMPLICIT -> state.positiveFeedback.incrementAndGet();
                case NEGATIVE_EXPLICIT, NEGATIVE_IMPLICIT -> {
                    state.negativeFeedback.incrementAndGet();
                    if (policy.isDowngradeOnNegativeFeedback()
                            && outcome.getFeedbackType() == ActionOutcome.FeedbackType.NEGATIVE_EXPLICIT) {
                        state.consecutiveFailures++;
                    }
                }
                default -> { }
            }
        }

        LOG.debug("Recorded outcome for {}/{}: success={}",
                outcome.getActionType(), outcome.getTenantId(), outcome.isSuccess());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transition Evaluation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<TransitionEvaluation> evaluateUpgrade(String actionType, String tenantId) {
        return Promise.of(actionType)
                .map(at -> evaluateUpgradeInternal(at, tenantId));
    }

    private TransitionEvaluation evaluateUpgradeInternal(String actionType, String tenantId) {
        MutableState state = getOrCreateState(actionType, tenantId);
        AutonomyPolicy policy = getPolicy(actionType, tenantId);

        List<String> reasons = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();

        AutonomyLevel current = state.currentLevel;
        AutonomyLevel target = current.upgrade();
        AutonomyLevel effectiveMax = policy.getEffectiveMaxLevel(actionType);

        // Check max level
        if (current == AutonomyLevel.AUTONOMOUS || current == effectiveMax) {
            blockers.add("Already at maximum level");
        }

        // Check time at level
        Duration timeAtLevel = Duration.between(state.levelEnteredAt, Instant.now());
        if (timeAtLevel.compareTo(policy.getMinimumTimeAtLevel()) < 0) {
            blockers.add("Minimum time at level not met: " + timeAtLevel + " < "
                    + policy.getMinimumTimeAtLevel());
        } else {
            reasons.add("Time at level sufficient");
        }

        // Check upgrade cooldown
        if (state.lastUpgradeAt != null) {
            Duration sinceLast = Duration.between(state.lastUpgradeAt, Instant.now());
            if (sinceLast.compareTo(policy.getUpgradeCooldown()) < 0) {
                blockers.add("Upgrade cooldown not met");
            }
        }

        // Check downgrade wait
        if (state.lastDowngradeAt != null) {
            Duration sinceDowngrade = Duration.between(state.lastDowngradeAt, Instant.now());
            if (sinceDowngrade.compareTo(policy.getUpgradeWaitAfterDowngrade()) < 0) {
                blockers.add("Wait period after downgrade not met");
            }
        }

        // Check action count
        long total = state.totalActions.get();
        if (total < policy.getMinimumActionsForUpgrade()) {
            blockers.add("Insufficient actions: " + total + " < " + policy.getMinimumActionsForUpgrade());
        } else {
            reasons.add("Sufficient actions: " + total);
            scores.put("actionCount", Math.min(1.0, (double) total / policy.getMinimumActionsForUpgrade()));
        }

        // Check success rate
        double successRate = total > 0 ? (double) state.successfulActions.get() / total : 0;
        scores.put("successRate", successRate);
        if (successRate < policy.getUpgradeSuccessRateThreshold()) {
            blockers.add("Success rate too low: " + String.format("%.2f", successRate));
        } else {
            reasons.add("Success rate: " + String.format("%.2f", successRate));
        }

        // Check confidence
        double confidence = state.calculateConfidence();
        scores.put("confidence", confidence);
        if (confidence < policy.getUpgradeConfidenceThreshold()) {
            blockers.add("Confidence too low: " + String.format("%.2f", confidence));
        } else {
            reasons.add("Confidence: " + String.format("%.2f", confidence));
        }

        // Check positive feedback
        long positive = state.positiveFeedback.get();
        if (positive < policy.getMinimumPositiveFeedback()) {
            blockers.add("Insufficient positive feedback: " + positive);
        } else {
            reasons.add("Positive feedback: " + positive);
        }

        boolean eligible = blockers.isEmpty();

        return TransitionEvaluation.builder()
                .actionType(actionType)
                .tenantId(tenantId)
                .currentLevel(current)
                .targetLevel(eligible ? target : current)
                .eligible(eligible)
                .confidence(confidence)
                .reasons(reasons)
                .blockers(blockers)
                .criteriaScores(scores)
                .build();
    }

    @Override
    public Promise<TransitionEvaluation> evaluateDowngrade(String actionType, String tenantId) {
        return Promise.of(actionType)
                .map(at -> evaluateDowngradeInternal(at, tenantId));
    }

    private TransitionEvaluation evaluateDowngradeInternal(String actionType, String tenantId) {
        MutableState state = getOrCreateState(actionType, tenantId);
        AutonomyPolicy policy = getPolicy(actionType, tenantId);

        List<String> reasons = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();

        AutonomyLevel current = state.currentLevel;
        AutonomyLevel target = current.downgrade();

        if (current == AutonomyLevel.SUGGEST || current == policy.getMinLevel()) {
            blockers.add("Already at minimum level");
        }

        // Check failure rate
        long total = state.totalActions.get();
        double failureRate = total > 0 ? (double) state.failedActions.get() / total : 0;
        scores.put("failureRate", failureRate);

        if (failureRate >= policy.getDowngradeFailureRateThreshold()) {
            reasons.add("Failure rate exceeds threshold: " + String.format("%.2f", failureRate));
        }

        // Check consecutive failures
        if (state.consecutiveFailures >= policy.getConsecutiveFailuresForDowngrade()) {
            reasons.add("Consecutive failures: " + state.consecutiveFailures);
        }

        boolean eligible = !blockers.isEmpty() || !reasons.isEmpty();
        double confidence = state.calculateConfidence();

        return TransitionEvaluation.builder()
                .actionType(actionType)
                .tenantId(tenantId)
                .currentLevel(current)
                .targetLevel(eligible && blockers.isEmpty() ? target : current)
                .eligible(eligible && blockers.isEmpty())
                .confidence(1 - confidence) // Inverse for downgrade
                .reasons(reasons)
                .blockers(blockers)
                .criteriaScores(scores)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<TransitionResult> requestUpgrade(String actionType, String tenantId, String reason) {
        return evaluateUpgrade(actionType, tenantId)
                .then(eval -> {
                    if (!eval.isEligible()) {
                        return Promise.of(TransitionResult.builder()
                                .actionType(actionType)
                                .tenantId(tenantId)
                                .previousLevel(eval.getCurrentLevel())
                                .newLevel(eval.getCurrentLevel())
                                .success(false)
                                .type(TransitionResult.TransitionType.NONE)
                                .reason(reason)
                                .message("Not eligible: " + String.join(", ", eval.getBlockers()))
                                .build());
                    }

                    return executeUpgrade(actionType, tenantId, reason);
                });
    }

    private Promise<TransitionResult> executeUpgrade(String actionType, String tenantId, String reason) {
        return Promise.of(actionType)
                .map(at -> executeUpgradeInternal(at, tenantId, reason));
    }

    private TransitionResult executeUpgradeInternal(String actionType, String tenantId, String reason) {
        MutableState state = getOrCreateState(actionType, tenantId);
        AutonomyLevel previous;
        AutonomyLevel next;

        synchronized (state) {
            previous = state.currentLevel;
            next = previous.upgrade();

            state.currentLevel = next;
            state.levelEnteredAt = Instant.now();
            state.lastUpgradeAt = Instant.now();
            state.upgradeCount++;
            state.consecutiveSuccesses = 0;
            state.consecutiveFailures = 0;
        }

        totalUpgrades.incrementAndGet();
        LOG.info("Upgraded {}/{} from {} to {}: {}",
                actionType, tenantId, previous, next, reason);

        return TransitionResult.builder()
                .actionType(actionType)
                .tenantId(tenantId)
                .previousLevel(previous)
                .newLevel(next)
                .success(true)
                .type(TransitionResult.TransitionType.UPGRADE)
                .reason(reason)
                .message("Upgraded successfully")
                .transitionTime(Instant.now())
                .build();
    }

    @Override
    public Promise<TransitionResult> forceDowngrade(
            String actionType,
            String tenantId,
            AutonomyLevel targetLevel,
            String reason) {
        return Promise.of(actionType)
                .map(at -> {
                    MutableState state = getOrCreateState(at, tenantId);
                    AutonomyPolicy policy = getPolicy(at, tenantId);
                    return forceDowngradeInternal(state, policy, reason);
                });
    }

    private TransitionResult forceDowngradeInternal(
            MutableState state,
            AutonomyPolicy policy,
            String reason) {

        AutonomyLevel previous;
        AutonomyLevel next;

        synchronized (state) {
            previous = state.currentLevel;

            // Apply downgrade steps
            next = previous;
            for (int i = 0; i < policy.getDowngradeStepsOnConsecutiveFailure(); i++) {
                if (next != policy.getMinLevel()) {
                    next = next.downgrade();
                }
            }

            state.currentLevel = next;
            state.levelEnteredAt = Instant.now();
            state.lastDowngradeAt = Instant.now();
            state.downgradeCount++;
            state.consecutiveSuccesses = 0;
            state.consecutiveFailures = 0;
        }

        totalDowngrades.incrementAndGet();
        LOG.warn("Downgraded {}/{} from {} to {}: {}",
                state.actionType, state.tenantId, previous, next, reason);

        return TransitionResult.builder()
                .actionType(state.actionType)
                .tenantId(state.tenantId)
                .previousLevel(previous)
                .newLevel(next)
                .success(true)
                .type(TransitionResult.TransitionType.DOWNGRADE)
                .reason(reason)
                .message("Downgraded due to: " + reason)
                .transitionTime(Instant.now())
                .build();
    }

    @Override
    public Promise<TransitionResult> setLevel(
            String actionType,
            String tenantId,
            AutonomyLevel level,
            String reason) {
        return Promise.of(actionType)
                .map(at -> {
                    MutableState state = getOrCreateState(at, tenantId);
                    AutonomyLevel previous;

                    synchronized (state) {
                        previous = state.currentLevel;
                        state.currentLevel = level;
                        state.levelEnteredAt = Instant.now();
                    }

                    LOG.info("Override {}/{} from {} to {}: {}",
                            at, tenantId, previous, level, reason);

                    return TransitionResult.builder()
                            .actionType(at)
                            .tenantId(tenantId)
                            .previousLevel(previous)
                            .newLevel(level)
                            .success(true)
                            .type(TransitionResult.TransitionType.OVERRIDE)
                            .reason(reason)
                            .transitionTime(Instant.now())
                            .build();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Policy Management
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<AutonomyPolicy> getPolicy(String actionType) {
        return Promise.of(policies.getOrDefault(actionType, defaultPolicy));
    }

    @Override
    public Promise<Void> setPolicy(String actionType, AutonomyPolicy policy) {
        if (actionType == null) {
            defaultPolicy = policy;
        } else {
            policies.put(actionType, policy);
        }
        return Promise.complete();
    }

    @Override
    public Promise<Map<String, AutonomyState>> listAllStates(String tenantId) {
        Map<String, AutonomyState> result = new HashMap<>();
        states.forEach((key, state) -> {
            if (key.endsWith(":" + tenantId)) {
                AutonomyPolicy policy = policies.getOrDefault(state.actionType, defaultPolicy);
                result.put(state.actionType, state.toImmutable(policy.getEffectiveMaxLevel(state.actionType)));
            }
        });
        return Promise.of(result);
    }

    @Override
    public Promise<ControllerStatistics> getStatistics() {
        Map<AutonomyLevel, Integer> byLevel = new EnumMap<>(AutonomyLevel.class);
        double totalConfidence = 0;
        double totalSuccessRate = 0;
        int count = 0;

        for (MutableState state : states.values()) {
            byLevel.merge(state.currentLevel, 1, Integer::sum);
            totalConfidence += state.calculateConfidence();

            long total = state.totalActions.get();
            double successRate = total > 0 ? (double) state.successfulActions.get() / total : 0.0;
            totalSuccessRate += successRate;

            count++;
        }

        return Promise.of(ControllerStatistics.builder()
                .totalActionTypes(states.size())
                .actionTypesByLevel(byLevel)
                .totalActionsGated(totalActionsGated.get())
                .actionsAllowed(actionsAllowed.get())
                .actionsBlocked(actionsBlocked.get())
                .totalUpgrades(totalUpgrades.get())
                .totalDowngrades(totalDowngrades.get())
                .averageConfidence(count > 0 ? totalConfidence / count : 0)
                .overallSuccessRate(count > 0 ? totalSuccessRate / count : 0)
                .lastTransitionTime(Instant.now()) // Approximate
                .build());
    }

    @Override
    public Promise<List<AutonomyLog>> getActionLog() {
        // Return all logs (could add pagination in the future)
        return Promise.of(new ArrayList<>(logs));
    }

    @Override
    public Promise<AutonomyState> getStateForDomain(String domain) {
        // Domain maps to actionType for now (could be enhanced for domain hierarchies)
        // Find the first state matching this domain/actionType
        for (Map.Entry<String, MutableState> entry : states.entrySet()) {
            MutableState state = entry.getValue();
            if (state.actionType.equals(domain) || state.actionType.startsWith(domain + ".")) {
                AutonomyPolicy policy = policies.getOrDefault(state.actionType, defaultPolicy);
                return Promise.of(state.toImmutable(policy.getEffectiveMaxLevel(state.actionType)));
            }
        }

        // If not found, return a default state for the domain
        AutonomyPolicy policy = policies.getOrDefault(domain, defaultPolicy);
        return Promise.of(AutonomyState.builder()
                .actionType(domain)
                .tenantId("default")
                .currentLevel(policy.getDefaultLevel())
                .effectiveMaxLevel(policy.getMaxLevel())
                .totalActions(0)
                .successfulActions(0)
                .failedActions(0)
                .successRate(0.0)
                .confidence(0.0)
                .lastActionAt(Instant.now())
                .levelEnteredAt(Instant.now())
                .upgradeCount(0)
                .downgradeCount(0)
                .consecutiveSuccesses(0)
                .consecutiveFailures(0)
                .positiveFeedbackCount(0)
                .negativeFeedbackCount(0)
                .build());
    }

    @Override
    public Promise<AutonomyPolicy> updatePolicy(AutonomyPolicy policy) {
        // Update default policy (policies are not per-action-type in current implementation)
        defaultPolicy = policy;

        LOG.info("Updated autonomy policy: {}", policy.getName());

        // Log the policy change
        AutonomyLog log = AutonomyLog.builder()
                .timestamp(Instant.now())
                .actionType("policy_update")
                .tenantId("system")
                .level(policy.getDefaultLevel())
                .decision("UPDATED")
                .confidence(1.0)
                .context(Map.of(
                    "policyId", (Object) policy.getId(),
                    "policyName", policy.getName(),
                    "defaultLevel", policy.getDefaultLevel().toString(),
                    "maxLevel", policy.getMaxLevel().toString()
                ))
                .build();
        logs.add(log);

        return Promise.of(policy);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildKey(String actionType, String tenantId) {
        return actionType + ":" + (tenantId != null ? tenantId : "default");
    }

    private AutonomyPolicy getPolicy(String actionType, String tenantId) {
        // For now, policies are global per action type.
        // Tenant overrides could be added here.
        return policies.getOrDefault(actionType, defaultPolicy);
    }

    private MutableState getOrCreateState(String actionType, String tenantId) {
        String key = buildKey(actionType, tenantId);
        return states.computeIfAbsent(key, k -> {
            AutonomyPolicy policy = getPolicy(actionType, tenantId);
            return new MutableState(actionType, tenantId, policy.getDefaultLevel());
        });
    }
}
