/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionRepository;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reconciliation worker that scans the transition log, detects item state mismatches,
 * and replays or repairs transitions.
 * Phase 6 FIX: Adds reconciliation worker to prevent permanent divergence between transition log and current item state.
 *
 * @doc.type class
 * @doc.purpose Reconciliation worker for mastery transition consistency
 * @doc.layer agent-core
 * @doc.pattern Worker
 */
public final class MasteryReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(MasteryReconciliationWorker.class);

    private final MasteryRegistry masteryRegistry;
    private final MasteryTransitionRepository transitionRepository;

    /**
     * Creates a reconciliation worker.
     *
     * @param masteryRegistry mastery registry for state queries
     * @param transitionRepository transition repository for log scanning
     */
    public MasteryReconciliationWorker(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull MasteryTransitionRepository transitionRepository
    ) {
        this.masteryRegistry = masteryRegistry;
        this.transitionRepository = transitionRepository;
    }

    /**
     * Reconciles transitions for a specific tenant.
     * Scans the transition log, detects item state mismatches, and attempts to repair them.
     *
     * @param tenantId tenant ID to reconcile
     * @param since timestamp to start scanning from
     * @return promise of reconciliation result
     */
    @NotNull
    public Promise<ReconciliationResult> reconcileTenant(@NotNull String tenantId, @NotNull Instant since) {
        log.info("Starting reconciliation for tenant {} since {}", tenantId, since);

        return transitionRepository.findByTimeRange(since, java.time.Instant.now())
                .then(transitions -> {
                    List<ReconciliationIssue> issues = new ArrayList<>();
                    AtomicInteger repairedCount = new AtomicInteger(0);
                    AtomicInteger failedCount = new AtomicInteger(0);

                    // Filter transitions by tenantId
                    List<MasteryTransition> tenantTransitions = transitions.stream()
                            .filter(t -> t.tenantId().equals(tenantId))
                            .toList();

                    // Process each transition sequentially
                    return processTransitionsSequentially(tenantTransitions, tenantId, issues, repairedCount, failedCount)
                            .then(() -> {
                                log.info("Reconciliation complete for tenant {}: {} issues found, {} repaired, {} failed",
                                        tenantId, issues.size(), repairedCount.get(), failedCount.get());

                                return Promise.of(new ReconciliationResult(
                                        tenantId,
                                        since,
                                        tenantTransitions.size(),
                                        issues.size(),
                                        repairedCount.get(),
                                        failedCount.get(),
                                        issues
                                ));
                            });
                });
    }

    /**
     * Processes transitions sequentially to avoid blocking the event loop.
     */
    private Promise<Void> processTransitionsSequentially(
            List<MasteryTransition> transitions,
            String tenantId,
            List<ReconciliationIssue> issues,
            AtomicInteger repairedCount,
            AtomicInteger failedCount
    ) {
        // Simple approach: process transitions sequentially using promise chaining
        Promise<Void> result = Promise.of(null);
        for (MasteryTransition transition : transitions) {
            result = result.then(() -> masteryRegistry.getById(tenantId, transition.masteryId())
                    .then(itemOpt -> {
                        if (itemOpt.isEmpty()) {
                            issues.add(new ReconciliationIssue(
                                    transition.transitionId(),
                                    transition.masteryId(),
                                    "Item not found in registry",
                                    ReconciliationAction.CREATE_ITEM
                            ));
                            return Promise.of(null);
                        }

                        MasteryItem item = itemOpt.get();
                        if (item.state() != transition.toState()) {
                            issues.add(new ReconciliationIssue(
                                    transition.transitionId(),
                                    transition.masteryId(),
                                    String.format("State mismatch: transition log says %s, item says %s",
                                            transition.toState(), item.state()),
                                    ReconciliationAction.REPAIR_STATE
                            ));

                            // Attempt to repair the state
                            return repairItemState(item, transition.toState())
                                    .map(success -> {
                                        if (success) {
                                            repairedCount.incrementAndGet();
                                        } else {
                                            failedCount.incrementAndGet();
                                        }
                                        return null;
                                    });
                        }

                        return Promise.of(null);
                    }));
        }
        return result;
    }

    /**
     * Reconciles a specific mastery item by checking its transition history.
     *
     * @param tenantId tenant ID
     * @param masteryId mastery ID to reconcile
     * @return promise of reconciliation result for the item
     */
    @NotNull
    public Promise<ReconciliationResult> reconcileItem(@NotNull String tenantId, @NotNull String masteryId) {
        log.info("Reconciling mastery item {} for tenant {}", masteryId, tenantId);

        return masteryRegistry.getById(tenantId, masteryId)
                .then(itemOpt -> {
                    if (itemOpt.isEmpty()) {
                        log.warn("Mastery item {} not found for tenant {}", masteryId, tenantId);
                        return Promise.of(new ReconciliationResult(
                                tenantId,
                                masteryId,
                                0,
                                1,
                                0,
                                0,
                                List.of(new ReconciliationIssue(
                                        "unknown",
                                        masteryId,
                                        "Item not found in registry",
                                        ReconciliationAction.CREATE_ITEM
                                ))
                        ));
                    }

                    MasteryItem item = itemOpt.get();
                    return transitionRepository.findByMasteryId(masteryId)
                            .then(transitions -> {
                                List<ReconciliationIssue> issues = new ArrayList<>();
                                AtomicInteger repairedCount = new AtomicInteger(0);

                                // Check the most recent transition
                                if (!transitions.isEmpty()) {
                                    MasteryTransition lastTransition = transitions.get(transitions.size() - 1);
                                    if (item.state() != lastTransition.toState()) {
                                        issues.add(new ReconciliationIssue(
                                                lastTransition.transitionId(),
                                                masteryId,
                                                String.format("State mismatch: last transition says %s, item says %s",
                                                        lastTransition.toState(), item.state()),
                                                ReconciliationAction.REPAIR_STATE
                                        ));

                                        // Attempt to repair the state
                                        return repairItemState(item, lastTransition.toState())
                                                .map(success -> {
                                                    if (success) {
                                                        repairedCount.incrementAndGet();
                                                    }
                                                    return new ReconciliationResult(
                                                            tenantId,
                                                            masteryId,
                                                            transitions.size(),
                                                            issues.size(),
                                                            repairedCount.get(),
                                                            success ? 0 : 1,
                                                            issues
                                                    );
                                                });
                                    }
                                }

                                return Promise.of(new ReconciliationResult(
                                        tenantId,
                                        masteryId,
                                        transitions.size(),
                                        issues.size(),
                                        repairedCount.get(),
                                        0,
                                        issues
                                ));
                            });
                });
    }

    /**
     * Repairs a mastery item's state to match the expected state.
     *
     * @param item mastery item to repair
     * @param expectedState expected state from transition log
     * @return promise of repair success
     */
    @NotNull
    private Promise<Boolean> repairItemState(@NotNull MasteryItem item, @NotNull MasteryState expectedState) {
        log.info("Repairing mastery item {} state from {} to {}", item.masteryId(), item.state(), expectedState);

        // Create a new item with the corrected state
        MasteryItem repairedItem = new MasteryItem(
                item.masteryId(),
                item.tenantId(),
                item.skillId(),
                item.domain(),
                item.agentId(),
                item.agentReleaseId(),
                expectedState,
                item.versionScope(),
                item.applicability(),
                item.score(),
                item.procedureIds(),
                item.semanticFactIds(),
                item.negativeKnowledgeIds(),
                item.evidenceRefs(),
                item.evaluationRefs(),
                item.knownFailureModeIds(),
                item.stateHistory(),
                item.lastVerifiedAt(),
                item.staleAfter(),
                item.labels(),
                item.confidence()
        );

        return masteryRegistry.save(repairedItem)
                .then(saved -> {
                    log.info("Successfully repaired mastery item {} state to {}", saved.masteryId(), expectedState);
                    return Promise.of(true);
                })
                .whenException(e -> {
                    log.error("Failed to repair mastery item {} state to {}", item.masteryId(), expectedState, e);
                })
                .then(() -> Promise.of(false));
    }

    /**
     * Reconciliation result summarizing the outcome of a reconciliation operation.
     */
    public record ReconciliationResult(
            @NotNull String tenantId,
            @NotNull Object scope,
            int transitionsScanned,
            int issuesFound,
            int repairedCount,
            int failedCount,
            @NotNull List<ReconciliationIssue> issues
    ) {}

    /**
     * A reconciliation issue detected during scanning.
     */
    public record ReconciliationIssue(
            @NotNull String transitionId,
            @NotNull String masteryId,
            @NotNull String description,
            @NotNull ReconciliationAction action
    ) {}

    /**
     * Action to take for a reconciliation issue.
     */
    public enum ReconciliationAction {
        /** Create a missing mastery item. */
        CREATE_ITEM,
        /** Repair the mastery item state to match transition log. */
        REPAIR_STATE,
        /** Replay the transition. */
        REPLAY_TRANSITION,
        /** Manual intervention required. */
        MANUAL_INTERVENTION
    }
}
