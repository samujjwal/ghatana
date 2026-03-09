package com.ghatana.softwareorg.hr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for HR department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for human resources operations (employee lifecycle,
 * performance management, training, compensation).
 *
 * @doc.type class
 * @doc.purpose HR domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class HrEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public HrEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Records employee onboarding.
     *
     * @param tenantId tenant context
     * @param employeeId employee identifier
     * @param name employee name
     * @param role employee role/title
     * @param department department assignment
     */
    public void recordOnboarding(
            String tenantId, String employeeId, String name, String role, String department) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("employeeId", employeeId);
        payload.put("name", name);
        payload.put("role", role);
        payload.put("department", department);
        payload.put("status", "ONBOARDED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("hr.employee.onboarded", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("hr.employees.onboarded", "department", department, "role", role);
    }

    /**
     * Records performance review.
     *
     * @param tenantId tenant context
     * @param reviewId performance review identifier
     * @param employeeId employee being reviewed
     * @param rating performance rating (1-5)
     * @param feedback feedback summary
     */
    public void recordPerformanceReview(
            String tenantId, String reviewId, String employeeId, int rating, String feedback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewId", reviewId);
        payload.put("employeeId", employeeId);
        payload.put("rating", rating);
        payload.put("feedback", feedback);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("hr.performance.reviewed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer(
                "hr.performance.rating",
                rating,
                "level",
                rating >= 4 ? "high" : "needs_improvement");
    }

    /**
     * Records training completion.
     *
     * @param tenantId tenant context
     * @param trainingId training program identifier
     * @param employeeId employee completing training
     * @param courseTitle training course title
     * @param hoursCompleted hours spent on training
     */
    public void recordTrainingCompletion(
            String tenantId, String trainingId, String employeeId, String courseTitle,
            double hoursCompleted) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("trainingId", trainingId);
        payload.put("employeeId", employeeId);
        payload.put("courseTitle", courseTitle);
        payload.put("hoursCompleted", hoursCompleted);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("hr.training.completed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("hr.training.hours", (long) hoursCompleted, "course", courseTitle);
    }

    /**
     * Records compensation/salary event.
     *
     * @param tenantId tenant context
     * @param compensationId compensation event identifier
     * @param employeeId employee identifier
     * @param newSalary new salary amount
     * @param type compensation type (NEW_HIRE, RAISE, BONUS, PROMOTION)
     */
    public void recordCompensation(
            String tenantId, String compensationId, String employeeId, double newSalary, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("compensationId", compensationId);
        payload.put("employeeId", employeeId);
        payload.put("newSalary", newSalary);
        payload.put("type", type);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("hr.compensation.updated", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("hr.compensation", (long) newSalary, "type", type);
    }

    /**
     * Records employee attrition/exit.
     *
     * @param tenantId tenant context
     * @param exitId exit event identifier
     * @param employeeId departing employee
     * @param reason exit reason (RETIREMENT, NEW_OPPORTUNITY, RELOCATION,
     * OTHER)
     */
    public void recordExit(String tenantId, String exitId, String employeeId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitId", exitId);
        payload.put("employeeId", employeeId);
        payload.put("reason", reason);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("hr.employee.exited", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("hr.attrition", "reason", reason);
    }
}
