/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.learning;

import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.learning.LearningContract;
import com.ghatana.agent.learning.LearningLevel;
import com.ghatana.agent.learning.LearningTarget;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Learning engine that implements the REFLECT phase of the GAA lifecycle.
 *
 * <p>The {@code LearningEngine} batch-processes recent episodes from an agent's
 * memory store, extracts patterns, synthesises versioned procedural policies,
 * and produces confidence-scored learning outcomes. Low-confidence policies
 * (below the configured threshold) are flagged for human review.
 *
 * <p>Learning is governed by a {@link LearningContract} which declares:
 * <ul>
 *   <li>The {@link LearningLevel} (L0-L5) controlling which targets are allowed</li>
 *   <li>The set of {@link LearningTarget}s the agent may propose changes for</li>
 *   <li>Whether provenance tracking is required</li>
 *   <li>Whether promotion (evaluation before activation) is required</li>
 * </ul>
 *
 * <h2>Learning Levels (L0-L5)</h2>
 * <ul>
 *   <li><b>L0</b>: No learning — deterministic, static configuration only</li>
 *   <li><b>L1</b>: Episodic memory only — store raw episodes</li>
 *   <li><b>L2</b>: Semantic facts and retrieval policies — bandit/online learning</li>
 *   <li><b>L3</b>: Procedural skills and negative knowledge — pattern synthesis</li>
 *   <li><b>L4</b>: Prompt templates and planner policies — structural learning</li>
 *   <li><b>L5</b>: Model adapters — offline-only, full parameter updates</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * LearningContract contract = new LearningContract(
 *     LearningLevel.L3,
 *     Set.of(LearningTarget.PROCEDURAL_SKILL, LearningTarget.SEMANTIC_FACT),
 *     true,  // provenanceRequired
 *     true   // promotionRequired
 * );
 *
 * LearningEngine engine = LearningEngine.create(executor)
 *     .withLearningContract(contract)
 *     .withHumanReviewThreshold(0.7);
 *
 * // Run during the REFLECT phase (fire-and-forget — never block user response)
 * LearningOutcome outcome = engine.reflect(agentId, memoryStore).await();
 * }</pre>
 *
 * <p><b>Implementation note</b>: All blocking I/O is wrapped in
 * {@code Promise.ofBlocking(executor, ...)} per the ActiveJ concurrency rules.
 *
 * @doc.type class
 * @doc.purpose Batch learning engine for GAA reflect phase — policy synthesis and confidence scoring
 * @doc.layer framework
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 * @doc.gaa.memory procedural|episodic
 */
public final class LearningEngine {

    /** Minimum confidence for a synthesised policy to be auto-approved. */
    private static final double DEFAULT_REVIEW_THRESHOLD = 0.7;
    /** Default batch size for episode processing. */
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final Executor executor;
    private LearningContract learningContract;
    private double humanReviewThreshold;
    private int batchSize;

    private LearningEngine(@NotNull Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.learningContract = new LearningContract(
                com.ghatana.agent.learning.LearningLevel.L0,
                Set.of(),
                false,
                false
        );
        this.humanReviewThreshold = DEFAULT_REVIEW_THRESHOLD;
        this.batchSize = DEFAULT_BATCH_SIZE;
    }

    /**
     * Creates a new {@code LearningEngine}.
     *
     * @param executor blocking executor for I/O operations
     * @return new engine instance
     */
    @NotNull
    public static LearningEngine create(@NotNull Executor executor) {
        return new LearningEngine(executor);
    }

    /**
     * Sets the learning contract, governing what the agent may learn and propose.
     *
     * @param contract the learning contract
     * @return this engine (fluent)
     */
    @NotNull
    public LearningEngine withLearningContract(@NotNull LearningContract contract) {
        this.learningContract = Objects.requireNonNull(contract, "contract must not be null");
        return this;
    }

    /**
     * Sets the learning level (convenience method that creates a default contract).
     *
     * @param level the desired learning level
     * @return this engine (fluent)
     * @deprecated Use {@link #withLearningContract(LearningContract)} for explicit control
     */
    @NotNull
    @Deprecated
    public LearningEngine withLearningLevel(@NotNull com.ghatana.agent.learning.LearningLevel level) {
        Objects.requireNonNull(level, "level must not be null");
        this.learningContract = new LearningContract(level, Set.of(), level.ordinal() >= 2, level.ordinal() >= 3);
        return this;
    }

    /**
     * Sets the confidence threshold below which synthesised policies are flagged
     * for human review before being applied.
     *
     * @param threshold confidence threshold in [0.0, 1.0]
     * @return this engine (fluent)
     */
    @NotNull
    public LearningEngine withHumanReviewThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0.0, 1.0], got: " + threshold);
        }
        this.humanReviewThreshold = threshold;
        return this;
    }

    /**
     * Sets how many episodes are processed per reflection batch.
     *
     * @param size batch size (must be positive)
     * @return this engine (fluent)
     */
    @NotNull
    public LearningEngine withBatchSize(int size) {
        if (size <= 0) throw new IllegalArgumentException("batchSize must be positive, got: " + size);
        this.batchSize = size;
        return this;
    }

    /**
     * Runs a full reflect pass for the given agent.
     *
     * <p>The reflect pass:
     * <ol>
     *   <li>Queries recent episodes from the memory store</li>
     *   <li>Synthesises candidate policies via pattern extraction</li>
     *   <li>Scores each policy with a confidence value</li>
     *   <li>Persists high-confidence policies; flags low-confidence ones</li>
     * </ol>
     *
     * <p><b>Must be called fire-and-forget</b> — never await this on the user response path.
     *
     * @param agentId     agent to reflect for
     * @param memoryStore the agent's memory store
     * @return promise of {@link LearningOutcome}
     */
    @NotNull
    public Promise<LearningOutcome> reflect(
            @NotNull String agentId,
            @NotNull MemoryStore memoryStore) {
        if (learningContract.level() == com.ghatana.agent.learning.LearningLevel.L0) {
            return Promise.of(LearningOutcome.noOp(agentId, learningContract.level()));
        }
        Instant start = Instant.now();
        MemoryFilter filter = MemoryFilter.builder()
                .startTime(Instant.now().minus(Duration.ofHours(24)))
                .build();

        return memoryStore.queryEpisodes(filter, batchSize)
                .then(episodes -> {
                    if (episodes.isEmpty()) {
                        return Promise.of(LearningOutcome.noOp(agentId, learningContract.level()));
                    }
                    // CPU-bound pattern synthesis on the blocking executor
                    return Promise.<List<CandidatePolicy>>ofBlocking(executor,
                                    () -> synthesisePolicies(agentId, episodes, learningContract))
                            .then(candidates -> persistPolicies(agentId, episodes, candidates, memoryStore, start));
                });
    }

    /**
     * Synthesises candidate policies from a batch of episodes.
     * In production this would call an LLM or rule extractor.
     * This default implementation uses a simple N-gram pattern heuristic.
     *
     * @param agentId  agent identifier
     * @param episodes episodes to process
     * @param contract learning contract governing what can be learned
     * @return list of candidate policies with confidence scores
     */
    @NotNull
    private List<CandidatePolicy> synthesisePolicies(
            @NotNull String agentId,
            @NotNull List<Episode> episodes,
            @NotNull LearningContract contract) {
        // Pattern: find repeated (action, outcome) pairs above frequency threshold
        java.util.Map<String, long[]> patternCounts = new java.util.HashMap<>();
        for (Episode ep : episodes) {
            if (ep.getAction() == null || ep.getReward() == null) continue;
            String key = ep.getAction();
            patternCounts.computeIfAbsent(key, k -> new long[]{0, 0});
            patternCounts.get(key)[0]++;
            if (ep.getReward() != null && ep.getReward() > 0) patternCounts.get(key)[1]++;
        }

        List<CandidatePolicy> out = new java.util.ArrayList<>();
        for (var entry : patternCounts.entrySet()) {
            long total = entry.getValue()[0];
            long positive = entry.getValue()[1];
            if (total < 3) continue; // Minimum sample size
            double confidence = (double) positive / total;
            if (confidence >= 0.4) { // Only synthesise plausible policies
                out.add(new CandidatePolicy(
                        "when: recent-context contains pattern '" + entry.getKey() + "'",
                        "do: " + entry.getKey(),
                        confidence));
            }
        }
        return out;
    }

    /**
     * Persists approved candidate policies to the memory store and returns the final outcome.
     * Low-confidence candidates are flagged for human review and not persisted.
     *
     * @param agentId     agent identifier
     * @param episodes    source episodes used for synthesis
     * @param candidates  all synthesised candidate policies
     * @param memoryStore target memory store
     * @param start       reflect-pass start time
     * @return promise of the {@link LearningOutcome}
     */
    @NotNull
    private Promise<LearningOutcome> persistPolicies(
            @NotNull String agentId,
            @NotNull List<Episode> episodes,
            @NotNull List<CandidatePolicy> candidates,
            @NotNull MemoryStore memoryStore,
            @NotNull Instant start) {
        String episodeIds = episodes.stream()
                .map(e -> e.getId() != null ? e.getId() : "")
                .filter(s -> !s.isEmpty())
                .toList()
                .toString();
        int flagged = (int) candidates.stream()
                .filter(c -> c.confidence() < humanReviewThreshold)
                .count();
        List<CandidatePolicy> approved = candidates.stream()
                .filter(c -> c.confidence() >= humanReviewThreshold)
                .toList();

        if (approved.isEmpty()) {
            Duration duration = Duration.between(start, Instant.now());
            return Promise.of(new LearningOutcome(
                    agentId, start, duration, learningContract.level(), episodes.size(), 0, 0, flagged));
        }

        // Chain all store operations sequentially to avoid concurrent writes
        Promise<Integer> storeChain = Promise.of(0);
        for (CandidatePolicy candidate : approved) {
            Policy policy = Policy.builder()
                    .agentId(agentId)
                    .situation(candidate.situation())
                    .action(candidate.action())
                    .confidence(candidate.confidence())
                    .learnedFromEpisodes(episodeIds)
                    .build();
            storeChain = storeChain.then(count ->
                    memoryStore.storePolicy(policy).map(p -> count + 1));
        }

        int episodeCount = episodes.size();
        int totalFlagged = flagged;
        return storeChain.map(created -> {
            Duration duration = Duration.between(start, Instant.now());
            return new LearningOutcome(
                    agentId, start, duration, learningContract.level(), episodeCount, created, 0, totalFlagged);
        });
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Outcome of a single reflect pass.
     *
     * @param agentId              agent identifier
     * @param startedAt            when the pass started
     * @param duration             time taken
     * @param levelApplied         learning level used
     * @param episodesProcessed    number of episodes consumed
     * @param policiesCreated      new policies stored (high confidence)
     * @param policiesUpdated      existing policies whose confidence was updated
     * @param policiesFlaggedForReview policies below threshold needing human review
     */
    public record LearningOutcome(
            @NotNull String agentId,
            @NotNull Instant startedAt,
            @NotNull Duration duration,
            @NotNull com.ghatana.agent.learning.LearningLevel levelApplied,
            int episodesProcessed,
            int policiesCreated,
            int policiesUpdated,
            int policiesFlaggedForReview) {

        /** Returns a no-op outcome for agents with L0 learning or no episodes. */
        @NotNull
        static LearningOutcome noOp(@NotNull String agentId, @NotNull com.ghatana.agent.learning.LearningLevel level) {
            return new LearningOutcome(agentId, Instant.now(), Duration.ZERO, level, 0, 0, 0, 0);
        }
    }

    /** Candidate policy before confidence filtering and persistence. */
    private record CandidatePolicy(
            @NotNull String situation,
            @NotNull String action,
            double confidence) {}
}
