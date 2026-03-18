package com.ghatana.softwareorg.qa.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for QA department events.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes HTTP endpoints for quality assurance operations (test suite
 * execution, coverage reporting, bug tracking, performance metrics).
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /api/v1/qa/test-suites - Execute test suite - POST /api/v1/qa/coverage
 * - Report coverage metrics - POST /api/v1/qa/bugs - Log detected bug - GET
 * /api/v1/qa/quality-metrics - Query quality metrics
 *
 * @doc.type class
 * @doc.purpose QA domain REST API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class QaEventController {

    private final EventPublisher eventPublisher;
    private final MetricsCollector metrics;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public QaEventController(EventPublisher eventPublisher, MetricsCollector metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    /**
     * Starts test suite execution.
     *
     * @param tenantId tenant context
     * @param suiteId test suite identifier
     * @param suiteName test suite name
     * @param testCount number of tests in suite
     */
    public void startTestSuite(String tenantId, String suiteId, String suiteName, int testCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("suiteId", suiteId);
        payload.put("suiteName", suiteName);
        payload.put("testCount", testCount);
        payload.put("status", "STARTED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("qa.test_suite.started", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("qa.test_suites.started", "tenant", tenantId);
    }

    /**
     * Completes test suite execution with results.
     *
     * @param tenantId tenant context
     * @param suiteId test suite identifier
     * @param totalTests total number of tests
     * @param passedTests number of passing tests
     * @param failedTests number of failing tests
     * @param durationMs total execution time
     */
    public void completeTestSuite(
            String tenantId, String suiteId, int totalTests, int passedTests, int failedTests,
            long durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("suiteId", suiteId);
        payload.put("totalTests", totalTests);
        payload.put("passedTests", passedTests);
        payload.put("failedTests", failedTests);
        payload.put("durationMs", durationMs);
        payload.put("passRate", (double) passedTests / totalTests);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("qa.test_suite.completed", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter(
                "qa.test_suites.completed",
                "passed",
                String.valueOf(failedTests == 0),
                "tenant",
                tenantId);
    }

    /**
     * Reports code coverage metrics.
     *
     * @param tenantId tenant context
     * @param buildId build identifier being measured
     * @param overallCoverage overall code coverage percentage (0-100)
     * @param lineCoverage line coverage percentage
     * @param branchCoverage branch coverage percentage
     */
    public void reportCoverage(
            String tenantId,
            String buildId,
            double overallCoverage,
            double lineCoverage,
            double branchCoverage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("buildId", buildId);
        payload.put("overallCoverage", overallCoverage);
        payload.put("lineCoverage", lineCoverage);
        payload.put("branchCoverage", branchCoverage);

        String eventType
                = overallCoverage >= 80 ? "qa.coverage.threshold_met" : "qa.coverage.threshold_breach";
        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish(eventType, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("qa.coverage.measured", 0, "threshold", overallCoverage >= 80 ? "met" : "breach");
    }

    /**
     * Logs bug discovery.
     *
     * @param tenantId tenant context
     * @param bugId bug identifier
     * @param severity bug severity (CRITICAL, HIGH, MEDIUM, LOW)
     * @param description bug description
     * @param component affected component
     */
    public void reportBug(
            String tenantId, String bugId, String severity, String description, String component) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bugId", bugId);
        payload.put("severity", severity);
        payload.put("description", description);
        payload.put("component", component);
        payload.put("status", "OPENED");

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("qa.bug.reported", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.incrementCounter("qa.bugs.reported", "severity", severity, "tenant", tenantId);
    }

    /**
     * Records performance test results.
     *
     * @param tenantId tenant context
     * @param testName performance test name
     * @param responseTimeMs average response time in ms
     * @param p95TimeMs 95th percentile response time
     * @param p99TimeMs 99th percentile response time
     * @param throughputRps requests per second
     */
    public void recordPerformanceMetrics(
            String tenantId,
            String testName,
            double responseTimeMs,
            double p95TimeMs,
            double p99TimeMs,
            double throughputRps) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("testName", testName);
        payload.put("responseTimeMs", responseTimeMs);
        payload.put("p95TimeMs", p95TimeMs);
        payload.put("p99TimeMs", p99TimeMs);
        payload.put("throughputRps", throughputRps);

        payload.put("tenantId", tenantId);
        try {
            eventPublisher.publish("qa.performance.measured", objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
        metrics.recordTimer("qa.performance.p99", (long) p99TimeMs, "test", testName);
    }
}
