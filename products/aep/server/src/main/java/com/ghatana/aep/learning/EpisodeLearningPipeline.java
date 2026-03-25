/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.learning;

import com.ghatana.agent.learning.UpdateCandidate;
import com.ghatana.agent.learning.evaluation.CompositeEvaluationGate;
import com.ghatana.agent.learning.evaluation.EvaluationContext;
import com.ghatana.agent.learning.evaluation.EvaluationGate.GateResult;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewItemType;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the AEP learning pipeline: episode collection → outcome evaluation
 * → policy proposal → human review submission.
 *
 * <h2>Pipeline stages</h2>
 * <ol>
 *   <li><b>Collect</b> — query recent episodic memory from DataCloud</li>
 *   <li><b>Group</b> — partition episodes by {@code skillId}</li>
 *   <li><b>Evaluate</b> — run {@link CompositeEvaluationGate} for each skill
 *       that has reached the minimum episode threshold</li>
 *   <li><b>Propose</b> — for skills that pass the evaluation, create a
 *       {@link PolicyProvenanceRecord} with full lineage metadata</li>
 *   <li><b>Review</b> — submit passing candidates to {@link HumanReviewQueue}
 *       when confidence is in the review band; auto-skip or tag for auto-promote
 *       at higher confidence thresholds</li>
 * </ol>
 *
 * <h2>Confidence bands</h2>
 * <pre>
 *   &lt; {@code skipBelowThreshold}   — too noisy; skip silently
 *   &gt;= {@code reviewThreshold}     — queue for human review
 *   &gt;= {@code autoPromoteThreshold} — tag ACTIVE in the review item context
 *                                      so reviewers can apply 1-click promotion
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Episode-to-policy learning pipeline — collect, evaluate, propose, review
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 */
public class EpisodeLearningPipeline {

    private static final Logger log = LoggerFactory.getLogger(EpisodeLearningPipeline.class);

    /** DataCloud collection where episodic memories are stored. */
    private static final String EPISODE_COLLECTION = "dc_memory";

    /** Maximum number of recent episodes to include per reflection run. */
    private static final int MAX_EPISODES_PER_RUN = 500;

    private final DataCloudClient agentDataCloud;
    private final CompositeEvaluationGate evaluationGate;
    private final HumanReviewQueue reviewQueue;

    /** Minimum episodes required for a skill before proposing a policy update. */
    private final int minEpisodeCount;

    /** Confidence at or above which we queue an item for human review. */
    private final double reviewThreshold;

    /** Confidence at or above which the review item is tagged as auto-promotable. */
    private final double autoPromoteThreshold;

    /**
     * Creates a pipeline with default thresholds:
     * min episodes = 10, review threshold = 0.70, auto-promote threshold = 0.90.
     *
     * @param agentDataCloud  DataCloud client for episode queries (required)
     * @param evaluationGate  composite evaluation gate (required)
     * @param reviewQueue     human review queue (required)
     */
    public EpisodeLearningPipeline(
            @NotNull DataCloudClient agentDataCloud,
            @NotNull CompositeEvaluationGate evaluationGate,
            @NotNull HumanReviewQueue reviewQueue) {
        this(agentDataCloud, evaluationGate, reviewQueue, 10, 0.70, 0.90);
    }

    /**
     * Creates a pipeline with explicit thresholds.
     *
     * @param agentDataCloud        DataCloud client for episode queries
     * @param evaluationGate        composite evaluation gate
     * @param reviewQueue           human review queue
     * @param minEpisodeCount       minimum episodes before proposing a policy
     * @param reviewThreshold       confidence threshold for queuing for human review
     * @param autoPromoteThreshold  confidence threshold for auto-promote tagging
     */
    public EpisodeLearningPipeline(
            @NotNull DataCloudClient agentDataCloud,
            @NotNull CompositeEvaluationGate evaluationGate,
            @NotNull HumanReviewQueue reviewQueue,
            int minEpisodeCount,
            double reviewThreshold,
            double autoPromoteThreshold) {
        this.agentDataCloud = agentDataCloud;
        this.evaluationGate = evaluationGate;
        this.reviewQueue = reviewQueue;
        this.minEpisodeCount = minEpisodeCount;
        this.reviewThreshold = reviewThreshold;
        this.autoPromoteThreshold = autoPromoteThreshold;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Runs the full learning pipeline for a tenant.
     *
     * <p>Async, non-blocking. All heavy DataCloud queries are already
     * {@code Promise}-based and execute on the ActiveJ event loop.
     *
     * @param tenantId the tenant to run learning for
     * @return pipeline result with counts per stage
     */
    @NotNull
    public Promise<LearningPipelineResult> run(@NotNull String tenantId) {
        log.info("[learning-pipeline] starting run for tenant={}", tenantId);

        List<DataCloudClient.Filter> filters = List.of(
                DataCloudClient.Filter.eq("type", "EPISODIC"));
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(filters)
                .limit(MAX_EPISODES_PER_RUN)
                .build();

        return agentDataCloud.query(tenantId, EPISODE_COLLECTION, query)
                .then(episodes -> processEpisodes(tenantId, episodes))
                .then(Promise::of, e -> {
                    log.error("[learning-pipeline] pipeline failed for tenant={}: {}", tenantId, e.getMessage(), e);
                    return Promise.of(LearningPipelineResult.failed(tenantId, e.getMessage()));
                });
    }

    // ─── Stage implementations ────────────────────────────────────────────────

    private Promise<LearningPipelineResult> processEpisodes(
            @NotNull String tenantId,
            @NotNull List<DataCloudClient.Entity> rawEpisodes) {

        if (rawEpisodes.isEmpty()) {
            log.info("[learning-pipeline] no episodes found for tenant={}", tenantId);
            return Promise.of(LearningPipelineResult.empty(tenantId));
        }

        // Group episodes by skillId
        Map<String, List<DataCloudClient.Entity>> bySkill = rawEpisodes.stream()
                .filter(e -> e.data().containsKey("agentId"))
                .collect(Collectors.groupingBy(
                        e -> (String) e.data().getOrDefault("agentId", "unknown")));

        log.info("[learning-pipeline] tenant={} — {} episodes across {} skills",
                tenantId, rawEpisodes.size(), bySkill.size());

        // Evaluate each skill asynchronously, then collect all results
        List<Promise<SkillResult>> skillPromises = bySkill.entrySet().stream()
                .map(entry -> evaluateSkill(tenantId, entry.getKey(), entry.getValue()))
                .toList();

        return Promises.toList(skillPromises)
                .map(skillResults -> aggregateResults(tenantId, rawEpisodes.size(), skillResults));
    }

    private Promise<SkillResult> evaluateSkill(
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull List<DataCloudClient.Entity> episodes) {

        if (episodes.size() < minEpisodeCount) {
            log.debug("[learning-pipeline] skill={} has {} episodes (< {}), skipping",
                    skillId, episodes.size(), minEpisodeCount);
            return Promise.of(new SkillResult(skillId, SkillOutcome.INSUFFICIENT_EPISODES, 0.0));
        }

        // Compute outcome metrics from episodes
        Map<String, Double> metrics = computeMetrics(episodes);
        double successRate = metrics.getOrDefault("successRate", 0.0);

        if (successRate < reviewThreshold * 0.5) {
            // Not enough signal to propose a policy — skip
            log.debug("[learning-pipeline] skill={} success-rate={:.2f} too low, skipping",
                    skillId, successRate);
            return Promise.of(new SkillResult(skillId, SkillOutcome.BELOW_THRESHOLD, successRate));
        }

        // Build UpdateCandidate for the evaluation gate
        String proposedVersion = UUID.randomUUID().toString();
        String changeDescription = buildChangeDescription(metrics, episodes.size());

        UpdateCandidate candidate = UpdateCandidate.builder()
                .skillId(skillId)
                .proposedVersion(proposedVersion)
                .currentVersion(null)
                .changeDescription(changeDescription)
                .agentId(skillId)
                .source("episode-learning-pipeline")
                .metadata(Map.of(
                        "episodeCount", episodes.size(),
                        "tenantId", tenantId,
                        "metrics", metrics))
                .build();

        EvaluationContext context = EvaluationContext.builder()
                .agentId(skillId)
                .recentTraceIds(episodes.stream().map(DataCloudClient.Entity::id).limit(20).toList())
                .historicalSuccessRate(successRate)
                .currentVersionExecutionCount(episodes.size())
                .environmentMetadata(Map.of("tenantId", tenantId))
                .build();

        return evaluationGate.evaluate(candidate, context)
                .then(gateResult -> {
                    double confidence = gateResult.score();
                    if (!gateResult.passed() || confidence < reviewThreshold) {
                        log.debug("[learning-pipeline] skill={} gate did not pass (score={:.2f})", skillId, confidence);
                        return Promise.of(new SkillResult(skillId, SkillOutcome.GATE_FAILED, confidence));
                    }

                    List<String> episodeIds = episodes.stream().map(DataCloudClient.Entity::id).toList();
                    PolicyProvenanceRecord provenance = PolicyProvenanceRecord.pending(
                            proposedVersion, tenantId, skillId,
                            1, episodeIds, metrics, confidence);

                    boolean autoPromotable = confidence >= autoPromoteThreshold;
                    return submitForReview(tenantId, skillId, proposedVersion, confidence,
                            changeDescription, gateResult, provenance, autoPromotable)
                            .map(item -> new SkillResult(skillId, SkillOutcome.QUEUED_FOR_REVIEW, confidence));
                });
    }

    private Promise<ReviewItem> submitForReview(
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull String proposedVersion,
            double confidence,
            @NotNull String changeDescription,
            @NotNull GateResult gateResult,
            @NotNull PolicyProvenanceRecord provenance,
            boolean autoPromotable) {

        Map<String, Object> context = new HashMap<>();
        context.put("provenance", Map.of(
                "policyId", provenance.policyId(),
                "skillId", skillId,
                "version", provenance.version(),
                "sourceEpisodeIds", provenance.sourceEpisodeIds(),
                "evaluationMetrics", provenance.evaluationMetrics(),
                "activationMode", provenance.activationMode().name()));
        context.put("autoPromotable", autoPromotable);
        context.put("gateResult", Map.of(
                "gateName", gateResult.gateName(),
                "passed", gateResult.passed(),
                "score", gateResult.score(),
                "threshold", gateResult.threshold(),
                "reason", gateResult.reason()));

        ReviewItem item = ReviewItem.builder()
                .tenantId(tenantId)
                .skillId(skillId)
                .proposedVersion(proposedVersion)
                .itemType(ReviewItemType.POLICY)
                .confidenceScore(confidence)
                .evaluationSummary(changeDescription
                        + (autoPromotable ? " [auto-promotable: confidence ≥ " + autoPromoteThreshold + "]" : ""))
                .context(Map.copyOf(context))
                .build();

        log.info("[learning-pipeline] queuing review for skill={} confidence={:.2f} auto-promotable={}",
                skillId, confidence, autoPromotable);

        return reviewQueue.enqueue(item);
    }

    // ─── Metric computation ───────────────────────────────────────────────────

    /**
     * Derives outcome metrics from a list of episodes. Episodes are expected to carry
     * {@code outcome} (SUCCESS/FAILURE/TIMEOUT/CANCELLED), and optionally {@code latencyMs}.
     */
    static Map<String, Double> computeMetrics(@NotNull List<DataCloudClient.Entity> episodes) {
        if (episodes.isEmpty()) {
            return Map.of("successRate", 0.0, "errorRate", 0.0, "avgLatencyMs", 0.0, "count", 0.0);
        }

        long successCount = episodes.stream()
                .filter(e -> "SUCCESS".equals(e.data().get("outcome")))
                .count();
        long failureCount = episodes.stream()
                .filter(e -> {
                    String outcome = (String) e.data().get("outcome");
                    return "FAILURE".equals(outcome) || "TIMEOUT".equals(outcome);
                })
                .count();

        double successRate = (double) successCount / episodes.size();
        double errorRate = (double) failureCount / episodes.size();

        double avgLatencyMs = episodes.stream()
                .mapToDouble(e -> {
                    Object latency = e.data().get("latencyMs");
                    if (latency instanceof Number n) return n.doubleValue();
                    return 0.0;
                })
                .average()
                .orElse(0.0);

        return Map.of(
                "successRate", successRate,
                "errorRate", errorRate,
                "avgLatencyMs", avgLatencyMs,
                "count", (double) episodes.size());
    }

    private static String buildChangeDescription(Map<String, Double> metrics, int episodeCount) {
        return String.format(
                "Candidate policy derived from %d episodes: success=%.1f%%, error=%.1f%%, avgLatency=%.0fms",
                episodeCount,
                metrics.getOrDefault("successRate", 0.0) * 100,
                metrics.getOrDefault("errorRate", 0.0) * 100,
                metrics.getOrDefault("avgLatencyMs", 0.0));
    }

    // ─── Result aggregation ───────────────────────────────────────────────────

    private LearningPipelineResult aggregateResults(
            String tenantId, int totalEpisodes, List<SkillResult> skillResults) {

        int queued = (int) skillResults.stream()
                .filter(r -> r.outcome() == SkillOutcome.QUEUED_FOR_REVIEW)
                .count();
        int skipped = (int) skillResults.stream()
                .filter(r -> r.outcome() == SkillOutcome.INSUFFICIENT_EPISODES
                        || r.outcome() == SkillOutcome.BELOW_THRESHOLD)
                .count();
        int gateFailed = (int) skillResults.stream()
                .filter(r -> r.outcome() == SkillOutcome.GATE_FAILED)
                .count();

        log.info("[learning-pipeline] completed for tenant={}: episodes={}, skills={}, queued={}, skipped={}, gateFailed={}",
                tenantId, totalEpisodes, skillResults.size(), queued, skipped, gateFailed);

        return new LearningPipelineResult(
                tenantId, true, null,
                totalEpisodes, skillResults.size(),
                queued, skipped, gateFailed);
    }

    // ─── Internal types ───────────────────────────────────────────────────────

    enum SkillOutcome { INSUFFICIENT_EPISODES, BELOW_THRESHOLD, GATE_FAILED, QUEUED_FOR_REVIEW }

    record SkillResult(String skillId, SkillOutcome outcome, double confidence) {}

    /**
     * Summary result of a single pipeline run.
     *
     * @param tenantId       tenant this run was for
     * @param success        false if the pipeline failed at the collection stage
     * @param errorMessage   error message if {@code success} is false
     * @param episodesRead   total episodes read from DataCloud
     * @param skillsEvaluated  number of unique skills evaluated
     * @param policiesQueued  number of new review items submitted to the HITL queue
     * @param skillsSkipped  skills with too few episodes or too-weak signal
     * @param gateFailures   skills that did not pass the evaluation gate
     *
     * @doc.type record
     * @doc.purpose Learning pipeline run summary
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record LearningPipelineResult(
            @NotNull String tenantId,
            boolean success,
            @org.jetbrains.annotations.Nullable String errorMessage,
            int episodesRead,
            int skillsEvaluated,
            int policiesQueued,
            int skillsSkipped,
            int gateFailures) {

        static LearningPipelineResult empty(String tenantId) {
            return new LearningPipelineResult(tenantId, true, null, 0, 0, 0, 0, 0);
        }

        static LearningPipelineResult failed(String tenantId, String errorMessage) {
            return new LearningPipelineResult(tenantId, false, errorMessage, 0, 0, 0, 0, 0);
        }
    }
}
