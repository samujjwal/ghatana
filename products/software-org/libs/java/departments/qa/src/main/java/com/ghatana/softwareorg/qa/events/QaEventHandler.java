package com.ghatana.softwareorg.qa.events;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles QA department events. Processes test suite execution, coverage
 * reports, bug reports, and performance metrics. Coordinates with engineering
 * for quality gates and with DevOps for test environments.
 */
public class QaEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QaEventHandler.class);
    private final MetricsCollector metrics;
    private final QaStateManager stateManager;

    public QaEventHandler(MetricsCollector metrics, QaStateManager stateManager) {
        this.metrics = metrics != null ? metrics : new NoopMetricsCollector();
        this.stateManager = stateManager;
    }

    /**
     * Handle test_suite_started event. Records test execution initiation,
     * allocates resources.
     */
    public void handleTestSuiteStarted(String suiteId, String featureId, int totalTests,
            String environment, String tenantId) {
        LOGGER.info("Test suite started: {} ({} tests in {})", suiteId, totalTests, environment);

        try {
            stateManager.recordTestSuiteStart(tenantId, suiteId, featureId, totalTests);
            metrics.incrementCounter("qa.test_suites.started", "environment", environment);
            LOGGER.info("Test suite {} initiated for feature {}", suiteId, featureId);

        } catch (Exception e) {
            LOGGER.error("Error handling test suite started: {}", suiteId, e);
            metrics.incrementCounter("qa.events.failed", "event_type", "test_suite_started");
            throw new RuntimeException("Failed to handle test suite started", e);
        }
    }

    /**
     * Handle test_suite_completed event. Records test results, calculates pass
     * rate, updates QA KPIs.
     */
    public void handleTestSuiteCompleted(String suiteId, int totalTests, int passedTests,
            int failedTests, long durationMs, String tenantId) {
        LOGGER.info("Test suite completed: {} - {}/{} passed", suiteId, passedTests, totalTests);

        try {
            double passRate = totalTests > 0 ? (double) passedTests / totalTests : 0.0;
            stateManager.recordTestSuiteCompletion(tenantId, suiteId, passRate, durationMs);

            metrics.incrementCounter("qa.tests.passed", "result", "pass");
            metrics.incrementCounter("qa.tests.failed", "result", "fail");
            metrics.recordTimer("qa.test_suite.duration_ms", durationMs);
            metrics.recordTimer("qa.pass_rate_percent", (long) (passRate * 100));

            if (passRate < 0.95) {
                LOGGER.warn("Test suite {} has low pass rate: {}", suiteId, passRate);
                metrics.incrementCounter("qa.test_suites.below_threshold", "threshold", "95%");
            } else {
                LOGGER.info("Test suite {} met quality threshold", suiteId);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling test suite completed: {}", suiteId, e);
            metrics.incrementCounter("qa.events.failed", "event_type", "test_suite_completed");
            throw new RuntimeException("Failed to handle test suite completed", e);
        }
    }

    /**
     * Handle coverage_reported event. Records code coverage metrics, flags
     * coverage gaps.
     */
    public void handleCoverageReported(String suiteId, double lineCoverage, double branchCoverage,
            String uncoveredFiles, String tenantId) {
        LOGGER.info("Coverage reported: {} - line: {}, branch: {}", suiteId, lineCoverage,
                branchCoverage);

        try {
            stateManager.recordCoverage(tenantId, suiteId, lineCoverage, branchCoverage);

            metrics.recordTimer("qa.coverage.line_percent", (long) (lineCoverage * 100));
            metrics.recordTimer("qa.coverage.branch_percent", (long) (branchCoverage * 100));

            if (lineCoverage < 0.80) {
                LOGGER.warn("Coverage below threshold: {}%", lineCoverage * 100);
                metrics.incrementCounter("qa.coverage.below_threshold", "target", "80%");
            }

        } catch (Exception e) {
            LOGGER.error("Error handling coverage reported: {}", suiteId, e);
            metrics.incrementCounter("qa.events.failed", "event_type", "coverage_reported");
            throw new RuntimeException("Failed to handle coverage reported", e);
        }
    }

    /**
     * Handle bug_reported event. Records bug findings, severity classification,
     * and assigns to engineering.
     */
    public void handleBugReported(String bugId, String severity, String description,
            String featureId, String tenantId) {
        LOGGER.info("Bug reported: {} ({}): {}", bugId, severity, description);

        try {
            stateManager.recordBug(tenantId, bugId, severity, description, featureId);

            metrics.incrementCounter("qa.bugs.reported", "severity", severity);

            if ("critical".equalsIgnoreCase(severity) || "blocker".equalsIgnoreCase(severity)) {
                LOGGER.error("Critical bug found: {}", bugId);
                metrics.incrementCounter("qa.bugs.critical");
            }

        } catch (Exception e) {
            LOGGER.error("Error handling bug reported: {}", bugId, e);
            metrics.incrementCounter("qa.events.failed", "event_type", "bug_reported");
            throw new RuntimeException("Failed to handle bug reported", e);
        }
    }

    /**
     * Handle performance_metrics_recorded event. Tracks performance benchmarks,
     * flags regressions.
     */
    public void handlePerformanceMetricsRecorded(String testId, long responseTimeMs,
            double throughputRps, String endpoint, String tenantId) {
        LOGGER.info("Performance metrics: {} - {}ms response, {}RPS", testId, responseTimeMs,
                throughputRps);

        try {
            stateManager.recordPerformanceMetric(tenantId, testId, responseTimeMs, throughputRps);

            metrics.recordTimer("qa.performance.response_time_ms", responseTimeMs,
                    "endpoint", endpoint);
            metrics.recordTimer("qa.performance.throughput_rps", (long) throughputRps);

            if (responseTimeMs > 1000) {
                LOGGER.warn("Slow response detected: {}ms for {}", responseTimeMs, endpoint);
                metrics.incrementCounter("qa.performance.slow_responses", "endpoint", endpoint);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling performance metrics: {}", testId, e);
            metrics.incrementCounter("qa.events.failed", "event_type", "performance_metrics");
            throw new RuntimeException("Failed to handle performance metrics recorded", e);
        }
    }
}
