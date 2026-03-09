package com.ghatana.softwareorg.engineering.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for Engineering department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for creating and querying engineering domain events
 * (feature requests, commits, builds, code reviews, quality gates).
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /api/v1/engineering/features - Create feature request - POST
 * /api/v1/engineering/commits - Record commit - POST /api/v1/engineering/builds
 * - Record build event - GET /api/v1/engineering/quality-gates - Query quality
 * gate status
 *
 * <p>
 * <b>Usage</b><br>
 * This controller is registered with core/http-server routing and delegates to
 * SoftwareOrgEventPublisher for EventCloud integration.
 *
 * @doc.type class
 * @doc.purpose Engineering domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class EngineeringEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /**
     * Creates new engineering controller.
     *
     * @param eventPublisher EventCloud publisher for persisting events
     * @param metrics metrics collector for instrumentation
     */
    public EngineeringEventController(
            EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Creates new feature request.
     *
     * @param tenantId tenant context
     * @param title feature title
     * @param description feature description
     * @param priority feature priority
     * @return feature ID
     */
    public String createFeatureRequest(
            String tenantId, String title, String description, String priority) {
        String featureId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("featureId", featureId);
        payload.put("title", title);
        payload.put("description", description);
        payload.put("priority", priority);
        payload.put("status", "REQUESTED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("engineering.feature.requested", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter(
                "engineering.feature.requested", "priority", priority, "tenant", tenantId);

        return featureId;
    }

    /**
     * Records commit event from repository.
     *
     * @param tenantId tenant context
     * @param commitHash commit hash from git
     * @param authorEmail author email
     * @param message commit message
     */
    public void recordCommit(String tenantId, String commitHash, String authorEmail, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commitHash", commitHash);
        payload.put("authorEmail", authorEmail);
        payload.put("message", message);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("engineering.commit.analyzed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("engineering.commits", "tenant", tenantId);
    }

    /**
     * Records build event result.
     *
     * @param tenantId tenant context
     * @param buildId build identifier
     * @param status build status (SUCCESS, FAILED, UNSTABLE)
     * @param durationMs build duration in milliseconds
     */
    public void recordBuildResult(
            String tenantId, String buildId, String status, long durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", buildId);
        payload.put("status", status);
        payload.put("durationMs", durationMs);

        String eventType
                = "SUCCESS".equals(status) ? "engineering.build.succeeded" : "engineering.build.failed";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }

        metrics.incrementCounter("engineering.builds", "status", status, "tenant", tenantId);
    }

    /**
     * Evaluates and records quality gate result.
     *
     * @param tenantId tenant context
     * @param buildId build being evaluated
     * @param passed whether quality gates passed
     * @param coverage code coverage percentage
     * @param testsRun number of tests run
     * @param testsPassed number of tests passed
     */
    public void evaluateQualityGate(
            String tenantId,
            String buildId,
            boolean passed,
            double coverage,
            int testsRun,
            int testsPassed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", buildId);
        payload.put("passed", passed);
        payload.put("coverage", coverage);
        payload.put("testsRun", testsRun);
        payload.put("testsPassed", testsPassed);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("engineering.quality_gate.evaluated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("engineering.quality_gate.evaluation", 0, "status", passed ? "pass" : "fail");
    }

    /**
     * Records code review event.
     *
     * @param tenantId tenant context
     * @param reviewId review identifier
     * @param pullRequestId associated PR ID
     * @param reviewerEmail reviewer email
     * @param approved whether review approved changes
     */
    public void recordCodeReview(
            String tenantId,
            String reviewId,
            String pullRequestId,
            String reviewerEmail,
            boolean approved) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewId", reviewId);
        payload.put("pullRequestId", pullRequestId);
        payload.put("reviewerEmail", reviewerEmail);
        payload.put("approved", approved);

        String eventType = approved ? "engineering.review.approved" : "engineering.review.requested_changes";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("engineering.code_reviews", "approved", String.valueOf(approved));
    }
}
