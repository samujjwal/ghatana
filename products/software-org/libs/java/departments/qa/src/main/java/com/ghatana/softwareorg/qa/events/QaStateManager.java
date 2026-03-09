package com.ghatana.softwareorg.qa.events;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages QA department state (test suites, coverage, bugs, performance
 * metrics). Thread-safe storage with tenant isolation.
 */
public class QaStateManager {

    private final Map<String, Map<String, Object>> testSuitesByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> coverageByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> bugsByTenant
            = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> performanceMetricsByTenant
            = new ConcurrentHashMap<>();

    public void recordTestSuiteStart(String tenantId, String suiteId, String featureId,
            int totalTests) {
        Map<String, Object> suiteData = new ConcurrentHashMap<>();
        suiteData.put("suiteId", suiteId);
        suiteData.put("featureId", featureId);
        suiteData.put("totalTests", totalTests);
        suiteData.put("startTime", System.currentTimeMillis());

        testSuitesByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(suiteId, suiteData);
    }

    public void recordTestSuiteCompletion(String tenantId, String suiteId, double passRate,
            long durationMs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> suiteData = (Map<String, Object>) testSuitesByTenant
                .getOrDefault(tenantId, new ConcurrentHashMap<>()).get(suiteId);

        if (suiteData != null) {
            suiteData.put("passRate", passRate);
            suiteData.put("durationMs", durationMs);
            suiteData.put("completedTime", System.currentTimeMillis());
        }
    }

    public void recordCoverage(String tenantId, String suiteId, double lineCoverage,
            double branchCoverage) {
        Map<String, Object> coverageData = new ConcurrentHashMap<>();
        coverageData.put("suiteId", suiteId);
        coverageData.put("lineCoverage", lineCoverage);
        coverageData.put("branchCoverage", branchCoverage);
        coverageData.put("timestamp", System.currentTimeMillis());

        coverageByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put("coverage_" + suiteId, coverageData);
    }

    public void recordBug(String tenantId, String bugId, String severity, String description,
            String featureId) {
        Map<String, Object> bugData = new ConcurrentHashMap<>();
        bugData.put("bugId", bugId);
        bugData.put("severity", severity);
        bugData.put("description", description);
        bugData.put("featureId", featureId);
        bugData.put("reportedTime", System.currentTimeMillis());

        bugsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(bugId, bugData);
    }

    public void recordPerformanceMetric(String tenantId, String testId, long responseTimeMs,
            double throughputRps) {
        Map<String, Object> metricData = new ConcurrentHashMap<>();
        metricData.put("testId", testId);
        metricData.put("responseTimeMs", responseTimeMs);
        metricData.put("throughputRps", throughputRps);
        metricData.put("timestamp", System.currentTimeMillis());

        performanceMetricsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(testId, metricData);
    }
}
