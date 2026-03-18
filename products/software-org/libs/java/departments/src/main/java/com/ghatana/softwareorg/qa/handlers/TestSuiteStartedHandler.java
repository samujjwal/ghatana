package com.ghatana.softwareorg.qa.handlers;

import com.ghatana.platform.observability.MetricsCollector;

/**
 * Handles TestSuiteStarted events from QA department.
 *
 * <p>
 * Responsibilities:<br>
 * - Track test suite execution start - Initialize test metrics - Emit timing
 * events
 *
 * @doc.type class
 * @doc.purpose QA event handler for test execution
 * @doc.layer product
 * @doc.pattern EventHandler
 */
public class TestSuiteStartedHandler {

    private final MetricsCollector metrics;

    public TestSuiteStartedHandler(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Handle test suite started event.
     *
     * @param suiteId Test suite identifier
     * @param testCount Number of tests in suite
     */
    public void handle(String suiteId, int testCount) {
        metrics.incrementCounter("test.suite.started", "count", String.valueOf(testCount));
    }
}
