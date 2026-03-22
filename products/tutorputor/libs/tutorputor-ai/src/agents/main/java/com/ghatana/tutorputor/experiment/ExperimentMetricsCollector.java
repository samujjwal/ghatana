package com.ghatana.tutorputor.experiment;

import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Collects and aggregates metrics for A/B experiments.
 * 
 * <p>Tracks various metrics including:
 * <ul>
 *   <li>Learning outcomes (mastery gains, quiz scores)</li>
 *   <li>Engagement metrics (time on task, completion rates)</li>
 *   <li>Content quality metrics (readability, accuracy)</li>
 *   <li>User satisfaction (ratings, feedback)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Experiment metrics collection
 * @doc.layer product
 * @doc.pattern Collector
 */
public class ExperimentMetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ExperimentMetricsCollector.class);

    private final ExperimentManager experimentManager;
    private final ConcurrentMap<String, UserMetrics> userMetricsMap;

    /**
     * Creates a new metrics collector.
     *
     * @param experimentManager the experiment manager
     */
    public ExperimentMetricsCollector(@NotNull ExperimentManager experimentManager) {
        this.experimentManager = experimentManager;
        this.userMetricsMap = new ConcurrentHashMap<>();
        LOG.info("ExperimentMetricsCollector initialized");
    }

    /**
     * Records mastery gain for a learner.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param topicId the topic ID
     * @param previousMastery mastery level before intervention
     * @param newMastery mastery level after intervention
     */
    public void recordMasteryGain(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String topicId,
            double previousMastery,
            double newMastery) {
        
        double gain = newMastery - previousMastery;
        
        experimentManager.recordConversion(experimentId, userId, "mastery_gain", gain);
        experimentManager.recordConversion(experimentId, userId, "final_mastery", newMastery);
        
        getUserMetrics(userId).recordMasteryGain(topicId, gain);
        
        LOG.debug("Recorded mastery gain: user={}, topic={}, gain={}", 
            userId, topicId, gain);
    }

    /**
     * Records quiz score.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param quizId the quiz ID
     * @param score the score (0-100)
     * @param timeSpent time spent on quiz
     */
    public void recordQuizScore(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String quizId,
            double score,
            @NotNull Duration timeSpent) {
        
        experimentManager.recordConversion(experimentId, userId, "quiz_score", score);
        experimentManager.recordConversion(experimentId, userId, "quiz_time_seconds", 
            timeSpent.toSeconds());
        
        getUserMetrics(userId).recordQuizScore(quizId, score, timeSpent);
        
        LOG.debug("Recorded quiz score: user={}, quiz={}, score={}", 
            userId, quizId, score);
    }

    /**
     * Records content engagement.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param contentId the content ID
     * @param timeSpent time spent on content
     * @param completed whether content was completed
     */
    public void recordEngagement(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String contentId,
            @NotNull Duration timeSpent,
            boolean completed) {
        
        experimentManager.recordConversion(experimentId, userId, "engagement_seconds", 
            timeSpent.toSeconds());
        experimentManager.recordConversion(experimentId, userId, "completion_rate", 
            completed ? 1.0 : 0.0);
        
        getUserMetrics(userId).recordEngagement(contentId, timeSpent, completed);
        
        LOG.debug("Recorded engagement: user={}, content={}, completed={}", 
            userId, contentId, completed);
    }

    /**
     * Records content rating.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param contentId the content ID
     * @param rating the rating (1-5)
     * @param feedback optional feedback text
     */
    public void recordRating(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String contentId,
            int rating,
            String feedback) {
        
        experimentManager.recordConversion(experimentId, userId, "content_rating", rating);
        
        getUserMetrics(userId).recordRating(contentId, rating, feedback);
        
        LOG.debug("Recorded rating: user={}, content={}, rating={}", 
            userId, contentId, rating);
    }

    /**
     * Records hint usage.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param problemId the problem ID
     * @param hintsUsed number of hints used
     * @param hintHelpful whether hints were helpful
     */
    public void recordHintUsage(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String problemId,
            int hintsUsed,
            boolean hintHelpful) {
        
        experimentManager.recordConversion(experimentId, userId, "hints_used", hintsUsed);
        experimentManager.recordConversion(experimentId, userId, "hint_helpful", 
            hintHelpful ? 1.0 : 0.0);
        
        getUserMetrics(userId).recordHintUsage(problemId, hintsUsed, hintHelpful);
        
        LOG.debug("Recorded hint usage: user={}, problem={}, hints={}", 
            userId, problemId, hintsUsed);
    }

    /**
     * Records content quality score.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param contentId the content ID
     * @param readabilityScore readability score (0-100)
     * @param accuracyScore accuracy score (0-100)
     * @param relevanceScore relevance score (0-100)
     */
    public void recordContentQuality(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String contentId,
            double readabilityScore,
            double accuracyScore,
            double relevanceScore) {
        
        experimentManager.recordConversion(experimentId, userId, "readability", readabilityScore);
        experimentManager.recordConversion(experimentId, userId, "accuracy", accuracyScore);
        experimentManager.recordConversion(experimentId, userId, "relevance", relevanceScore);
        
        double overallQuality = (readabilityScore + accuracyScore + relevanceScore) / 3.0;
        experimentManager.recordConversion(experimentId, userId, "overall_quality", overallQuality);
        
        LOG.debug("Recorded content quality: content={}, overall={}", 
            contentId, overallQuality);
    }

    /**
     * Records session metrics.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param sessionDuration total session duration
     * @param problemsAttempted number of problems attempted
     * @param problemsSolved number of problems solved correctly
     */
    public void recordSession(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull Duration sessionDuration,
            int problemsAttempted,
            int problemsSolved) {
        
        experimentManager.recordConversion(experimentId, userId, "session_duration_minutes", 
            sessionDuration.toMinutes());
        experimentManager.recordConversion(experimentId, userId, "problems_attempted", 
            problemsAttempted);
        experimentManager.recordConversion(experimentId, userId, "problems_solved", 
            problemsSolved);
        
        if (problemsAttempted > 0) {
            double successRate = (double) problemsSolved / problemsAttempted * 100;
            experimentManager.recordConversion(experimentId, userId, "success_rate", successRate);
        }
        
        LOG.debug("Recorded session: user={}, duration={}min, solved={}/{}", 
            userId, sessionDuration.toMinutes(), problemsSolved, problemsAttempted);
    }

    /**
     * Gets aggregated metrics for a user.
     *
     * @param userId the user ID
     * @return user metrics
     */
    public UserMetrics getUserMetrics(@NotNull String userId) {
        return userMetricsMap.computeIfAbsent(userId, k -> new UserMetrics(userId));
    }

    /**
     * Gets summary statistics for an experiment.
     *
     * @param experimentId the experiment ID
     * @return experiment summary
     */
    public ExperimentSummary getExperimentSummary(@NotNull String experimentId) {
        ExperimentManager.ExperimentResults results = 
            experimentManager.getResults(experimentId);
        
        if (results == null) {
            return null;
        }
        
        Map<String, VariantSummary> variantSummaries = new HashMap<>();
        
        for (var entry : results.variantResults().entrySet()) {
            String variantId = entry.getKey();
            ExperimentManager.VariantResults vr = entry.getValue();
            
            variantSummaries.put(variantId, new VariantSummary(
                variantId,
                vr.variant().name(),
                vr.exposures(),
                getMetricValue(vr, "mastery_gain"),
                getMetricValue(vr, "quiz_score"),
                getMetricValue(vr, "completion_rate"),
                getMetricValue(vr, "content_rating"),
                getMetricValue(vr, "overall_quality")
            ));
        }
        
        return new ExperimentSummary(
            experimentId,
            results.experiment().name(),
            variantSummaries
        );
    }

    private double getMetricValue(ExperimentManager.VariantResults vr, String metric) {
        ExperimentManager.MetricStats stats = vr.metrics().get(metric);
        return stats != null ? stats.mean() : 0.0;
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * User metrics aggregation.
     */
    public static class UserMetrics {
        private final String userId;
        private final List<MasteryRecord> masteryRecords;
        private final List<QuizRecord> quizRecords;
        private final List<EngagementRecord> engagementRecords;
        private final List<RatingRecord> ratingRecords;
        private final List<HintRecord> hintRecords;

        UserMetrics(String userId) {
            this.userId = userId;
            this.masteryRecords = Collections.synchronizedList(new ArrayList<>());
            this.quizRecords = Collections.synchronizedList(new ArrayList<>());
            this.engagementRecords = Collections.synchronizedList(new ArrayList<>());
            this.ratingRecords = Collections.synchronizedList(new ArrayList<>());
            this.hintRecords = Collections.synchronizedList(new ArrayList<>());
        }

        public String userId() { return userId; }

        void recordMasteryGain(String topicId, double gain) {
            masteryRecords.add(new MasteryRecord(topicId, gain, Instant.now()));
        }

        void recordQuizScore(String quizId, double score, Duration timeSpent) {
            quizRecords.add(new QuizRecord(quizId, score, timeSpent, Instant.now()));
        }

        void recordEngagement(String contentId, Duration timeSpent, boolean completed) {
            engagementRecords.add(new EngagementRecord(contentId, timeSpent, completed, Instant.now()));
        }

        void recordRating(String contentId, int rating, String feedback) {
            ratingRecords.add(new RatingRecord(contentId, rating, feedback, Instant.now()));
        }

        void recordHintUsage(String problemId, int hintsUsed, boolean helpful) {
            hintRecords.add(new HintRecord(problemId, hintsUsed, helpful, Instant.now()));
        }

        public double averageMasteryGain() {
            return masteryRecords.stream()
                .mapToDouble(MasteryRecord::gain)
                .average()
                .orElse(0.0);
        }

        public double averageQuizScore() {
            return quizRecords.stream()
                .mapToDouble(QuizRecord::score)
                .average()
                .orElse(0.0);
        }

        public double completionRate() {
            if (engagementRecords.isEmpty()) return 0.0;
            long completed = engagementRecords.stream()
                .filter(EngagementRecord::completed)
                .count();
            return (double) completed / engagementRecords.size();
        }

        public double averageRating() {
            return ratingRecords.stream()
                .mapToInt(RatingRecord::rating)
                .average()
                .orElse(0.0);
        }

        public Duration totalEngagementTime() {
            return engagementRecords.stream()
                .map(EngagementRecord::timeSpent)
                .reduce(Duration.ZERO, Duration::plus);
        }
    }

    public record MasteryRecord(String topicId, double gain, Instant timestamp) {}
    public record QuizRecord(String quizId, double score, Duration timeSpent, Instant timestamp) {}
    public record EngagementRecord(String contentId, Duration timeSpent, boolean completed, Instant timestamp) {}
    public record RatingRecord(String contentId, int rating, String feedback, Instant timestamp) {}
    public record HintRecord(String problemId, int hintsUsed, boolean helpful, Instant timestamp) {}

    /**
     * Summary for a variant.
     */
    public record VariantSummary(
        String variantId,
        String variantName,
        long exposures,
        double avgMasteryGain,
        double avgQuizScore,
        double completionRate,
        double avgRating,
        double avgQuality
    ) {}

    /**
     * Summary for an experiment.
     */
    public record ExperimentSummary(
        String experimentId,
        String experimentName,
        Map<String, VariantSummary> variants
    ) {
        /**
         * Gets the winning variant based on mastery gain.
         *
         * @return the winning variant ID, or null if no data
         */
        public String getWinningVariant() {
            return variants.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().avgMasteryGain()))
                .map(Map.Entry::getKey)
                .orElse(null);
        }

        /**
         * Calculates lift of treatment over control.
         *
         * @param controlId the control variant ID
         * @param treatmentId the treatment variant ID
         * @param metric which metric to compare
         * @return lift percentage
         */
        public double calculateLift(String controlId, String treatmentId, String metric) {
            VariantSummary control = variants.get(controlId);
            VariantSummary treatment = variants.get(treatmentId);
            
            if (control == null || treatment == null) return 0.0;
            
            double controlValue = getMetricValue(control, metric);
            double treatmentValue = getMetricValue(treatment, metric);
            
            if (controlValue == 0) return 0.0;
            
            return ((treatmentValue - controlValue) / controlValue) * 100;
        }

        private double getMetricValue(VariantSummary vs, String metric) {
            return switch (metric) {
                case "mastery_gain" -> vs.avgMasteryGain();
                case "quiz_score" -> vs.avgQuizScore();
                case "completion_rate" -> vs.completionRate();
                case "rating" -> vs.avgRating();
                case "quality" -> vs.avgQuality();
                default -> 0.0;
            };
        }
    }
}
