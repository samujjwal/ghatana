/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud-backed repository for evaluation runs.
 *
 * <p>Evaluation runs track the execution and results of evaluating an agent skill
 * against an evaluation pack.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for evaluation runs
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudEvaluationRunRepository {

    private final ConcurrentHashMap<String, EvaluationRun> runs = new ConcurrentHashMap<>();

    /**
     * Saves an evaluation run.
     *
     * @param run evaluation run to save
     * @return promise of saved evaluation run
     */
    @NotNull
    public Promise<EvaluationRun> save(@NotNull EvaluationRun run) {
        runs.put(run.runId(), run);
        return Promise.of(run);
    }

    /**
     * Finds an evaluation run by ID.
     *
     * @param runId run identifier
     * @return promise of optional evaluation run
     */
    @NotNull
    public Promise<Optional<EvaluationRun>> findById(@NotNull String runId) {
        return Promise.of(Optional.ofNullable(runs.get(runId)));
    }

    /**
     * Finds evaluation runs by pack ID.
     *
     * @param packId pack identifier
     * @return promise of list of evaluation runs
     */
    @NotNull
    public Promise<List<EvaluationRun>> findByPackId(@NotNull String packId) {
        return Promise.of(runs.values().stream()
                .filter(r -> r.packId().equals(packId))
                .toList());
    }

    /**
     * Finds evaluation runs by skill ID.
     *
     * @param skillId skill identifier
     * @return promise of list of evaluation runs
     */
    @NotNull
    public Promise<List<EvaluationRun>> findBySkillId(@NotNull String skillId) {
        return Promise.of(runs.values().stream()
                .filter(r -> r.skillId().equals(skillId))
                .toList());
    }

    /**
     * Finds evaluation runs by agent ID.
     *
     * @param agentId agent identifier
     * @return promise of list of evaluation runs
     */
    @NotNull
    public Promise<List<EvaluationRun>> findByAgentId(@NotNull String agentId) {
        return Promise.of(runs.values().stream()
                .filter(r -> r.agentId().equals(agentId))
                .toList());
    }

    /**
     * Finds evaluation runs by state.
     *
     * @param state run state
     * @return promise of list of evaluation runs
     */
    @NotNull
    public Promise<List<EvaluationRun>> findByState(@NotNull RunState state) {
        return Promise.of(runs.values().stream()
                .filter(r -> r.state() == state)
                .toList());
    }

    /**
     * Finds evaluation runs created after a timestamp.
     *
     * @param since timestamp
     * @return promise of list of evaluation runs
     */
    @NotNull
    public Promise<List<EvaluationRun>> findAfter(@NotNull Instant since) {
        return Promise.of(runs.values().stream()
                .filter(r -> r.createdAt().isAfter(since))
                .toList());
    }

    /**
     * Updates the state of an evaluation run.
     *
     * @param runId run identifier
     * @param state new state
     * @return promise of updated evaluation run
     */
    @NotNull
    public Promise<EvaluationRun> updateState(@NotNull String runId, @NotNull RunState state) {
        EvaluationRun existing = runs.get(runId);
        if (existing == null) {
            return Promise.of(null);
        }

        EvaluationRun updated = new EvaluationRun(
                existing.runId(),
                existing.packId(),
                existing.skillId(),
                existing.agentId(),
                existing.agentReleaseId(),
                state,
                existing.startedAt(),
                existing.completedAt(),
                existing.results(),
                existing.createdAt(),
                Instant.now(),
                existing.createdBy()
        );

        runs.put(runId, updated);
        return Promise.of(updated);
    }

    /**
     * Evaluation run record.
     *
     * @doc.type record
     * @doc.purpose Evaluation run record
     * @doc.layer data-cloud
     * @doc.pattern Record
     */
    public record EvaluationRun(
            @NotNull String runId,
            @NotNull String packId,
            @NotNull String skillId,
            @NotNull String agentId,
            @NotNull String agentReleaseId,
            @NotNull RunState state,
            @NotNull Instant startedAt,
            @Nullable Instant completedAt,
            @NotNull Map<String, TestCaseResult> results,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String createdBy
    ) {
        public EvaluationRun {
            results = Map.copyOf(results);
        }

        /**
         * Returns true if this run is completed.
         *
         * @return true if completed
         */
        public boolean isCompleted() {
            return state == RunState.COMPLETED || state == RunState.FAILED;
        }

        /**
         * Returns the duration of the run if completed.
         *
         * @return duration in milliseconds, or null if not completed
         */
        @Nullable
        public Long durationMs() {
            if (completedAt == null) {
                return null;
            }
            return completedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    /**
     * Run state enumeration.
     *
     * @doc.type enum
     * @doc.purpose Evaluation run state
     * @doc.layer data-cloud
     * @doc.pattern Enumeration
     */
    public enum RunState {
        /** Run is pending execution. */
        PENDING,

        /** Run is currently executing. */
        RUNNING,

        /** Run completed successfully. */
        COMPLETED,

        /** Run failed. */
        FAILED,

        /** Run was cancelled. */
        CANCELLED
    }

    /**
     * Test case result.
     *
     * @doc.type record
     * @doc.purpose Test case result record
     * @doc.layer data-cloud
     * @doc.pattern Record
     */
    public record TestCaseResult(
            @NotNull String caseId,
            boolean passed,
            double score,
            @NotNull String message,
            @Nullable String errorMessage
    ) {
    }
}
