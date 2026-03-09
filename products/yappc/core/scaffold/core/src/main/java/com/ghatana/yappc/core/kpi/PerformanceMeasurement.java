package com.ghatana.yappc.core.kpi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance Measurement Utility for automated KPI collection Week 10 Day 50: KPI measurement and
 * reporting
 *
 * @doc.type class
 * @doc.purpose Performance Measurement Utility for automated KPI collection Week 10 Day 50: KPI measurement and
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PerformanceMeasurement {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMeasurement.class);

    private final KPICollector collector;
    private final Map<String, Instant> activeTimings = new ConcurrentHashMap<>();

    public PerformanceMeasurement(KPICollector collector) {
        this.collector = collector;
    }

    public PerformanceMeasurement() {
        this(new KPICollector());
    }

    /**
 * Measure the execution time of a runnable */
    public void measureExecution(String operationName, Runnable operation) {
        measureExecution(
                operationName,
                () -> {
                    operation.run();
                    return null;
                });
    }

    /**
 * Measure the execution time of a supplier and return its result */
    public <T> T measureExecution(String operationName, Supplier<T> operation) {
        Instant start = Instant.now();
        try {
            T result = operation.get();
            Duration duration = Duration.between(start, Instant.now());
            collector.recordMeasurement(operationName + ".duration", duration.toMillis());
            collector.recordMeasurement(operationName + ".success", 1.0);
            logger.debug("Measured {}: {} ms", operationName, duration.toMillis());
            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            collector.recordMeasurement(operationName + ".duration", duration.toMillis());
            collector.recordMeasurement(operationName + ".success", 0.0);
            logger.warn("Failed measurement {}: {} ms", operationName, duration.toMillis(), e);
            throw e;
        }
    }

    /**
 * Start timing an operation */
    public void startTiming(String operationName) {
        activeTimings.put(operationName, Instant.now());
        logger.debug("Started timing: {}", operationName);
    }

    /**
 * Stop timing an operation and record the result */
    public Duration stopTiming(String operationName) {
        Instant start = activeTimings.remove(operationName);
        if (start == null) {
            logger.warn("No active timing found for operation: {}", operationName);
            return Duration.ZERO;
        }

        Duration duration = Duration.between(start, Instant.now());
        collector.recordMeasurement(operationName + ".duration", duration.toMillis());
        logger.debug("Stopped timing {}: {} ms", operationName, duration.toMillis());
        return duration;
    }

    /**
 * Measure build process with detailed metrics */
    public void measureBuildProcess(String projectName, Runnable buildProcess) {
        String buildMetric = "build." + projectName;
        Instant buildStart = Instant.now();

        try {
            // Record build start
            collector.recordMeasurement(
                    buildMetric + ".started",
                    1.0,
                    Map.of("project", projectName, "timestamp", buildStart.toString()));

            // Execute build
            buildProcess.run();

            // Record success metrics
            Duration buildDuration = Duration.between(buildStart, Instant.now());
            collector.recordBuildTimeToGreen(projectName, buildDuration, true);
            collector.recordMeasurement(
                    buildMetric + ".completed",
                    1.0,
                    Map.of(
                            "project",
                            projectName,
                            "duration_ms",
                            String.valueOf(buildDuration.toMillis())));

            logger.info(
                    "Build completed successfully for {}: {} ms",
                    projectName,
                    buildDuration.toMillis());

        } catch (Exception e) {
            // Record failure metrics
            Duration buildDuration = Duration.between(buildStart, Instant.now());
            collector.recordBuildTimeToGreen(projectName, buildDuration, false);
            collector.recordMeasurement(
                    buildMetric + ".failed",
                    1.0,
                    Map.of(
                            "project",
                            projectName,
                            "duration_ms",
                            String.valueOf(buildDuration.toMillis()),
                            "error",
                            e.getMessage()));

            logger.error("Build failed for {}: {} ms", projectName, buildDuration.toMillis(), e);
            throw e;
        }
    }

    /**
 * Measure test execution with detailed metrics */
    public TestResult measureTestExecution(String suiteName, Supplier<TestResult> testExecution) {
        String testMetric = "test." + suiteName;
        Instant testStart = Instant.now();

        try {
            // Record test start
            collector.recordMeasurement(
                    testMetric + ".started",
                    1.0,
                    Map.of("suite", suiteName, "timestamp", testStart.toString()));

            // Execute tests
            TestResult result = testExecution.get();

            // Record test metrics
            Duration testDuration = Duration.between(testStart, Instant.now());
            collector.recordTestMetrics(
                    suiteName, result.getTotalTests(), result.getPassedTests(), testDuration);

            collector.recordMeasurement(
                    testMetric + ".completed",
                    1.0,
                    Map.of(
                            "suite", suiteName,
                            "total", String.valueOf(result.getTotalTests()),
                            "passed", String.valueOf(result.getPassedTests()),
                            "duration_ms", String.valueOf(testDuration.toMillis())));

            logger.info(
                    "Test execution completed for {}: {} passed out of {} in {} ms",
                    suiteName,
                    result.getPassedTests(),
                    result.getTotalTests(),
                    testDuration.toMillis());

            return result;

        } catch (Exception e) {
            Duration testDuration = Duration.between(testStart, Instant.now());
            collector.recordMeasurement(
                    testMetric + ".failed",
                    1.0,
                    Map.of(
                            "suite",
                            suiteName,
                            "duration_ms",
                            String.valueOf(testDuration.toMillis()),
                            "error",
                            e.getMessage()));

            logger.error(
                    "Test execution failed for {}: {} ms", suiteName, testDuration.toMillis(), e);
            throw e;
        }
    }

    /**
 * Measure cache operations */
    public <T> T measureCacheOperation(
            String cacheName, String operation, Supplier<T> cacheOperation) {
        String cacheMetric = "cache." + cacheName + "." + operation;

        return measureExecution(
                cacheMetric,
                () -> {
                    T result = cacheOperation.get();

                    // Record cache-specific metrics based on operation type
                    if ("get".equals(operation)) {
                        if (result != null) {
                            collector.recordMeasurement("cache." + cacheName + ".hits", 1.0);
                        } else {
                            collector.recordMeasurement("cache." + cacheName + ".misses", 1.0);
                        }
                    }

                    return result;
                });
    }

    /**
 * Measure deployment operations */
    public void measureDeployment(String environment, Runnable deploymentProcess) {
        String deployMetric = "deployment." + environment;
        Instant deployStart = Instant.now();

        try {
            collector.recordMeasurement(
                    deployMetric + ".started",
                    1.0,
                    Map.of("environment", environment, "timestamp", deployStart.toString()));

            deploymentProcess.run();

            Duration deployDuration = Duration.between(deployStart, Instant.now());
            collector.recordDeploymentMetrics(environment, deployDuration, true);
            collector.recordMeasurement(
                    deployMetric + ".completed",
                    1.0,
                    Map.of(
                            "environment",
                            environment,
                            "duration_ms",
                            String.valueOf(deployDuration.toMillis())));

            logger.info(
                    "Deployment completed successfully to {}: {} ms",
                    environment,
                    deployDuration.toMillis());

        } catch (Exception e) {
            Duration deployDuration = Duration.between(deployStart, Instant.now());
            collector.recordDeploymentMetrics(environment, deployDuration, false);
            collector.recordMeasurement(
                    deployMetric + ".failed",
                    1.0,
                    Map.of(
                            "environment",
                            environment,
                            "duration_ms",
                            String.valueOf(deployDuration.toMillis()),
                            "error",
                            e.getMessage()));

            logger.error(
                    "Deployment failed to {}: {} ms", environment, deployDuration.toMillis(), e);
            throw e;
        }
    }

    /**
 * Get the collector for direct access */
    public KPICollector getCollector() {
        return collector;
    }

    /**
 * Test result holder */
    public static class TestResult {
        private final int totalTests;
        private final int passedTests;
        private final int failedTests;

        public TestResult(int totalTests, int passedTests) {
            this.totalTests = totalTests;
            this.passedTests = passedTests;
            this.failedTests = totalTests - passedTests;
        }

        public int getTotalTests() {
            return totalTests;
        }

        public int getPassedTests() {
            return passedTests;
        }

        public int getFailedTests() {
            return failedTests;
        }

        public double getSuccessRate() {
            return (double) passedTests / totalTests;
        }
    }

    /**
 * Create a singleton instance for global access */
    private static final PerformanceMeasurement INSTANCE = new PerformanceMeasurement();

    public static PerformanceMeasurement getInstance() {
        return INSTANCE;
    }
}
