package com.ghatana.core.integration;

import com.ghatana.platform.domain.learning.PatternRecommender;
import com.ghatana.core.workflow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recommends optimal approval chains using learning and collaborative filtering.
 *
 * <p><b>Purpose</b><br>
 * Learns from historical approval decisions to recommend optimal approval chains.
 * Improves efficiency by routing to approvers who typically approve faster with higher
 * acceptance rates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ApprovalRecommender recommender = new ApprovalRecommender();
 *
 * // Train from historical data
 * recommender.learnFromApprovals(approvalHistory);
 *
 * // Get recommendations
 * List<String> recommendedApprovers = recommender
 *     .recommendApprovers(task, 3);  // Top 3 approvers
 *
 * // Track outcome for learning
 * recommender.recordApprovalOutcome(task, approvers, 0.95);  // 95% satisfaction
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Historical approval analysis
 * - Approver efficiency ranking
 * - Context-based recommendations
 * - Feedback learning
 * - Approval time prediction
 * - Success rate tracking
 *
 * @doc.type class
 * @doc.purpose Approval chain recommendation engine
 * @doc.layer core
 * @doc.pattern Strategy, Observer
 */
public class ApprovalRecommender {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalRecommender.class);

    private final PatternRecommender learningEngine;
    private final Map<String, ApproverProfile> approverProfiles;
    private final Map<String, ApprovalOutcome> outcomeHistory;
    private final List<RecommendationListener> listeners;

    /**
     * Create approval recommender.
     */
    public ApprovalRecommender() {
        this.learningEngine = new PatternRecommender();
        this.approverProfiles = new ConcurrentHashMap<>();
        this.outcomeHistory = new ConcurrentHashMap<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());

        bootstrapDefaultPatterns();
        logger.debug("Created ApprovalRecommender");
    }

    private void bootstrapDefaultPatterns() {
        learningEngine.registerPattern("default-approver",
            Set.of("approval", "priority-medium"),
            0.5);
        logger.debug("Bootstrapped default recommendation patterns");
    }

    /**
     * Record approval outcome for learning.
     *
     * @param task Approved task
     * @param approverId Approver identifier
     * @param approved Whether approved (true) or rejected (false)
     * @param satisfactionScore Satisfaction score (0-1)
     * @param approvalTimeMillis Time taken for approval
     */
    public void recordApprovalOutcome(WorkflowTask task, String approverId,
                                     boolean approved, double satisfactionScore,
                                     long approvalTimeMillis) {
        String outcomeId = task.getTaskId() + "-" + approverId;

        ApprovalOutcome outcome = new ApprovalOutcome(
            outcomeId,
            task.getTaskId(),
            approverId,
            approved,
            satisfactionScore,
            approvalTimeMillis,
            System.currentTimeMillis()
        );

        outcomeHistory.put(outcomeId, outcome);

        // Update approver profile
        ApproverProfile profile = approverProfiles.computeIfAbsent(
            approverId,
            k -> new ApproverProfile(approverId)
        );

        profile.recordOutcome(outcome);

        // Learn in engine
        learningEngine.recordFeedback(approverId, approved, satisfactionScore);

        logger.debug("Recorded approval outcome: {} approved by {}",
                   task.getTaskId(), approverId);
    }

    /**
     * Recommend approvers for task.
     *
     * @param task Task needing approval
     * @param maxRecommendations Maximum recommendations to return
     * @return Recommended approvers ranked by effectiveness
     */
    public List<ApproverRecommendation> recommendApprovers(WorkflowTask task, int maxRecommendations) {
        List<ApproverRecommendation> recommendations = new ArrayList<>();

        // Get event types from context
        Set<String> eventTypes = extractEventTypes(task);

        // Get recommendations from learning engine
        List<PatternRecommender.Recommendation> learningRecs = Collections.emptyList();
        try {
            learningRecs = learningEngine.getRecommendations(eventTypes, maxRecommendations).getResult();
        } catch (Exception e) {
            logger.warn("No learning data available for approver recommendations, falling back to profiles", e);
        }

        // Convert to approval recommendations with profiles
        for (PatternRecommender.Recommendation rec : learningRecs) {
            ApproverProfile profile = approverProfiles.get(rec.getPatternId());

            if (profile != null) {
                ApproverRecommendation appRec = new ApproverRecommendation(
                    rec.getPatternId(),
                    rec.getScore(),
                    profile.getApprovalRate(),
                    profile.getAverageApprovalTime(),
                    profile.getSatisfactionScore()
                );
                recommendations.add(appRec);
            }
        }

        if (recommendations.isEmpty()) {
            for (ApproverProfile profile : approverProfiles.values()) {
                recommendations.add(new ApproverRecommendation(
                    profile.getApproverId(),
                    profile.getApprovalRate(),
                    profile.getApprovalRate(),
                    profile.getAverageApprovalTime(),
                    profile.getSatisfactionScore()
                ));
            }
        }

        // Sort by composite score
        recommendations.sort((a, b) -> Double.compare(b.getCompositeScore(), a.getCompositeScore()));

        logger.info("Recommended {} approvers for task {}", recommendations.size(), task.getTaskId());
        notifyListeners(task, recommendations);

        return recommendations.stream()
            .limit(maxRecommendations)
            .toList();
    }

    /**
     * Extract event types from task context.
     *
     * @param task Task to analyze
     * @return Set of event types
     */
    private Set<String> extractEventTypes(WorkflowTask task) {
        Set<String> types = new HashSet<>();
        types.add(task.getType().name().toLowerCase());
        types.add("priority-" + task.getPriority().name().toLowerCase());

        Object dept = task.getContext().get("department");
        if (dept != null) {
            types.add("dept-" + dept.toString().toLowerCase());
        }

        return types;
    }

    /**
     * Get approver profile.
     *
     * @param approverId Approver identifier
     * @return Optional approver profile
     */
    public Optional<ApproverProfile> getApproverProfile(String approverId) {
        return Optional.ofNullable(approverProfiles.get(approverId));
    }

    /**
     * Predict approval time for approver.
     *
     * @param approverId Approver identifier
     * @return Predicted time in milliseconds
     */
    public long predictApprovalTime(String approverId) {
        ApproverProfile profile = approverProfiles.get(approverId);
        if (profile != null) {
            return profile.getAverageApprovalTime();
        }
        return 3600000;  // Default 1 hour
    }

    /**
     * Register recommendation listener.
     *
     * @param listener Listener to register
     */
    public void registerListener(RecommendationListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify listeners of recommendations.
     *
     * @param task Task
     * @param recommendations Recommendations
     */
    private void notifyListeners(WorkflowTask task, List<ApproverRecommendation> recommendations) {
        for (RecommendationListener listener : listeners) {
            try {
                listener.onRecommendations(task, recommendations);
            } catch (Exception e) {
                logger.error("Error notifying recommendation listener", e);
            }
        }
    }

    /**
     * Approver profile tracking performance metrics.
     */
    public static class ApproverProfile {
        private final String approverId;
        private int totalApprovals = 0;
        private int approvedCount = 0;
        private long totalApprovalTime = 0;
        private double totalSatisfaction = 0;

        public ApproverProfile(String approverId) {
            this.approverId = approverId;
        }

        public void recordOutcome(ApprovalOutcome outcome) {
            totalApprovals++;
            if (outcome.isApproved()) {
                approvedCount++;
            }
            totalApprovalTime += outcome.getApprovalTimeMillis();
            totalSatisfaction += outcome.getSatisfactionScore();
        }

        public String getApproverId() {
            return approverId;
        }

        public double getApprovalRate() {
            return totalApprovals == 0 ? 0 : (double) approvedCount / totalApprovals;
        }

        public long getAverageApprovalTime() {
            return totalApprovals == 0 ? 0 : totalApprovalTime / totalApprovals;
        }

        public double getSatisfactionScore() {
            return totalApprovals == 0 ? 0 : totalSatisfaction / totalApprovals;
        }

        public int getTotalApprovals() {
            return totalApprovals;
        }
    }

    /**
     * Approval outcome record.
     */
    public static class ApprovalOutcome {
        private final String outcomeId;
        private final String taskId;
        private final String approverId;
        private final boolean approved;
        private final double satisfactionScore;
        private final long approvalTimeMillis;
        private final long timestamp;

        public ApprovalOutcome(String outcomeId, String taskId, String approverId,
                             boolean approved, double satisfactionScore,
                             long approvalTimeMillis, long timestamp) {
            this.outcomeId = outcomeId;
            this.taskId = taskId;
            this.approverId = approverId;
            this.approved = approved;
            this.satisfactionScore = satisfactionScore;
            this.approvalTimeMillis = approvalTimeMillis;
            this.timestamp = timestamp;
        }

        public boolean isApproved() {
            return approved;
        }

        public double getSatisfactionScore() {
            return satisfactionScore;
        }

        public long getApprovalTimeMillis() {
            return approvalTimeMillis;
        }
    }

    /**
     * Approval recommendation.
     */
    public static class ApproverRecommendation {
        private final String approverId;
        private final double relevanceScore;
        private final double approvalRate;
        private final long averageApprovalTime;
        private final double satisfactionScore;

        public ApproverRecommendation(String approverId, double relevanceScore,
                                     double approvalRate, long averageApprovalTime,
                                     double satisfactionScore) {
            this.approverId = approverId;
            this.relevanceScore = relevanceScore;
            this.approvalRate = approvalRate;
            this.averageApprovalTime = averageApprovalTime;
            this.satisfactionScore = satisfactionScore;
        }

        public String getApproverId() {
            return approverId;
        }

        public double getCompositeScore() {
            // Weight: relevance (50%), approval rate (30%), satisfaction (20%)
            return (relevanceScore * 0.5) + (approvalRate * 0.3) + (satisfactionScore * 0.2);
        }

        public double getApprovalRate() {
            return approvalRate;
        }

        public long getAverageApprovalTime() {
            return averageApprovalTime;
        }

        public double getSatisfactionScore() {
            return satisfactionScore;
        }

        @Override
        public String toString() {
            return String.format("ApproverRec{id=%s, score=%.2f, rate=%.0f%%, time=%dms, sat=%.2f}",
                    approverId, getCompositeScore(), approvalRate*100, averageApprovalTime, satisfactionScore);
        }
    }

    /**
     * Recommendation listener interface.
     */
    @FunctionalInterface
    public interface RecommendationListener {
        void onRecommendations(WorkflowTask task, List<ApproverRecommendation> recommendations);
    }
}

