package com.ghatana.softwareorg.engineering.events;

import com.ghatana.softwareorg.engineering.domain.FeatureRequest;
import com.ghatana.softwareorg.engineering.domain.CodeReview;
import com.ghatana.softwareorg.engineering.domain.BuildResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles engineering department events. Processes feature requests, commits,
 * builds, code reviews, and quality gates. Emits metrics and side effects for
 * downstream departments.
 */
public class EngineeringEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(EngineeringEventHandler.class);
    private final MetricsCollector metrics;
    private final EngineeringStateManager stateManager;

    public EngineeringEventHandler(MetricsCollector metrics, EngineeringStateManager stateManager) {
        this.metrics = metrics != null ? metrics : new NoopMetricsCollector();
        this.stateManager = stateManager;
    }

    /**
     * Handle feature_shipped event. Records feature creation, updates KPIs,
     * triggers QA pipeline.
     */
    public void handleFeatureShipped(String featureId, String title, String priority,
            String description, String tenantId) {
        logger.info("Processing feature shipped: {} ({})", featureId, title);

        try {
            // Record in state
            FeatureRequest feature = new FeatureRequest(featureId, title, description, "SHIPPED", priority);
            stateManager.recordFeature(tenantId, feature);

            // Emit metrics
            metrics.incrementCounter("engineering.features.shipped", "priority", priority);
            metrics.recordTimer("engineering.feature.creation_time", 0, "priority", priority);

            // Trigger downstream: QA should pick up
            logger.info("Feature {} ready for QA", featureId);

        } catch (Exception e) {
            logger.error("Error processing feature shipped: {}", featureId, e);
            metrics.incrementCounter("engineering.events.failed", "event_type", "feature_shipped");
            throw new RuntimeException("Failed to process feature shipped", e);
        }
    }

    /**
     * Handle commit_analyzed event. Records code commit, extracts metrics
     * (lines changed, files affected).
     */
    public void handleCommitAnalyzed(String commitId, String branch, int filesChanged,
            int linesAdded, int linesDeleted, String author, String tenantId) {
        logger.info("Processing commit analyzed: {} by {}", commitId, author);

        try {
            stateManager.recordCommit(tenantId, commitId, filesChanged, linesAdded, linesDeleted);

            metrics.incrementCounter("engineering.commits.total", "branch", branch);
            metrics.recordTimer("engineering.commit.size", filesChanged + linesAdded + linesDeleted,
                    "branch", branch);

            logger.info("Commit {} analyzed: {} files, {} lines added", commitId, filesChanged,
                    linesAdded);

        } catch (Exception e) {
            logger.error("Error processing commit analyzed: {}", commitId, e);
            metrics.incrementCounter("engineering.events.failed", "event_type", "commit_analyzed");
            throw new RuntimeException("Failed to process commit analyzed", e);
        }
    }

    /**
     * Handle build_result event. Records build success/failure, updates cycle
     * time metrics, triggers next stage.
     */
    public void handleBuildResult(String buildId, String featureId, boolean success,
            long durationMs, String buildServer, String tenantId) {
        logger.info("Processing build result: {} - {}", buildId, success ? "SUCCESS" : "FAILURE");

        try {
            BuildResult result = new BuildResult(buildId, featureId, success, durationMs);
            stateManager.recordBuild(tenantId, result);

            metrics.incrementCounter("engineering.builds.total", "status", success ? "success" : "failure");
            metrics.recordTimer("engineering.build.duration_ms", durationMs,
                    "status", success ? "success" : "failure");

            if (success) {
                logger.info("Build {} succeeded in {}ms", buildId, durationMs);
                // Trigger QA: tests should run
            } else {
                logger.warn("Build {} failed after {}ms", buildId, durationMs);
                metrics.incrementCounter("engineering.builds.failed", "build_server", buildServer);
            }

        } catch (Exception e) {
            logger.error("Error processing build result: {}", buildId, e);
            metrics.incrementCounter("engineering.events.failed", "event_type", "build_result");
            throw new RuntimeException("Failed to process build result", e);
        }
    }

    /**
     * Handle quality_gate_evaluation event. Records gate results, may block
     * deployment if thresholds not met.
     */
    public void handleQualityGateEvaluation(String featureId, boolean gatePassed,
            double coverage, double testPassRate, String reason, String tenantId) {
        logger.info("Processing quality gate: {} - {}", featureId, gatePassed ? "PASSED" : "FAILED");

        try {
            stateManager.recordQualityGate(tenantId, featureId, gatePassed, coverage, testPassRate);

            String status = gatePassed ? "passed" : "failed";
            metrics.incrementCounter("engineering.quality_gates", "status", status);
            metrics.recordTimer("engineering.coverage_percent", (long) (coverage * 100));

            if (!gatePassed) {
                logger.warn("Quality gate failed for {}: {}", featureId, reason);
                metrics.incrementCounter("engineering.quality_gates.blocked", "reason", reason);
                // Block deployment: notify DevOps
            } else {
                logger.info("Quality gate passed for {}", featureId);
                // Proceed to deployment: notify DevOps
            }

        } catch (Exception e) {
            logger.error("Error processing quality gate: {}", featureId, e);
            metrics.incrementCounter("engineering.events.failed", "event_type", "quality_gate");
            throw new RuntimeException("Failed to process quality gate evaluation", e);
        }
    }

    /**
     * Handle code_review_completed event. Records review feedback, approvals,
     * and rejection reasons.
     */
    public void handleCodeReviewCompleted(String prId, String reviewer, boolean approved,
            String feedback, String tenantId) {
        logger.info("Processing code review: {} - {}", prId, approved ? "APPROVED" : "REJECTED");

        try {
            CodeReview review = new CodeReview(prId, reviewer, approved, feedback);
            stateManager.recordCodeReview(tenantId, review);

            metrics.incrementCounter("engineering.code_reviews.total",
                    "status", approved ? "approved" : "rejected");

            if (approved) {
                logger.info("Code review approved for PR {}", prId);
                // Trigger merge: next step in pipeline
            } else {
                logger.info("Code review rejected for PR {}: {}", prId, feedback);
                metrics.incrementCounter("engineering.code_reviews.blocked", "reason", "feedback");
            }

        } catch (Exception e) {
            logger.error("Error processing code review: {}", prId, e);
            metrics.incrementCounter("engineering.events.failed", "event_type", "code_review");
            throw new RuntimeException("Failed to process code review completed", e);
        }
    }
}
