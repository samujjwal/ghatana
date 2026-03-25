package com.ghatana.yappc.core.profiling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance Profiler for YAPPC Week 11 Day 54: Advanced performance profiling and monitoring
 *
 * <p>Provides comprehensive JVM and application performance monitoring: - Memory usage analysis and
 * leak detection - CPU profiling and hotspot identification - Thread monitoring and deadlock
 * detection - GC analysis and optimization recommendations - Method-level performance measurement
 *
 * @doc.type class
 * @doc.purpose Performance Profiler for YAPPC Week 11 Day 54: Advanced performance profiling and monitoring
 * @doc.layer platform
 * @doc.pattern Component
 */
public class PerformanceProfiler {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceProfiler.class);

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ProfileSession> activeSessions;
    private final Map<String, List<PerformanceSnapshot>> sessionData;

    // JVM Management Beans
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final RuntimeMXBean runtimeBean;
    private final OperatingSystemMXBean osBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final List<MemoryPoolMXBean> memoryPoolBeans;

    // Profiling configuration
    private boolean profilingEnabled = true;
    private long samplingIntervalMs = 1000;
    private int maxSamplesPerSession = 10000;

    public PerformanceProfiler() {
        this.objectMapper =
                JsonUtils.getDefaultMapper()
                        .configure(SerializationFeature.INDENT_OUTPUT, true)
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionData = new ConcurrentHashMap<>();

        // Initialize JVM management beans
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();

        // Enable thread CPU measurement if supported
        if (threadBean.isThreadCpuTimeSupported()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }

        logger.info(
                "Performance profiler initialized with sampling interval: {}ms",
                samplingIntervalMs);
    }

    /**
 * Start a new profiling session */
    public String startProfilingSession(String sessionName) {
        if (!profilingEnabled) {
            throw new IllegalStateException("Profiling is disabled");
        }

        String sessionId = generateSessionId(sessionName);

        ProfileSession session = new ProfileSession();
        session.sessionId = sessionId;
        session.sessionName = sessionName;
        session.startTime = Instant.now();
        session.samplingInterval = samplingIntervalMs;

        activeSessions.put(sessionId, session);
        sessionData.put(sessionId, new ArrayList<>());

        // Start sampling
        scheduler.scheduleAtFixedRate(
                () -> captureSnapshot(sessionId), 0, samplingIntervalMs, TimeUnit.MILLISECONDS);

        logger.info("Started profiling session: {} ({})", sessionName, sessionId);
        return sessionId;
    }

    /**
 * Stop profiling session and generate report */
    public ProfileReport stopProfilingSession(String sessionId) {
        ProfileSession session = activeSessions.remove(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        session.endTime = Instant.now();
        List<PerformanceSnapshot> snapshots = sessionData.remove(sessionId);

        logger.info(
                "Stopped profiling session: {} with {} snapshots",
                session.sessionName,
                snapshots.size());

        return generateReport(session, snapshots);
    }

    /**
 * Get real-time performance snapshot */
    public PerformanceSnapshot getCurrentSnapshot() {
        return createSnapshot();
    }

    /**
 * Profile a specific method execution */
    public <T> ProfilingResult<T> profileExecution(
            String operationName, ProfiledOperation<T> operation) {
        String sessionId = startProfilingSession("method-" + operationName);

        long startTime = System.nanoTime();
        long startCpuTime = getCurrentThreadCpuTime();

        T result = null;
        Exception exception = null;

        try {
            result = operation.execute();
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long endTime = System.nanoTime();
            long endCpuTime = getCurrentThreadCpuTime();

            ProfileReport report = stopProfilingSession(sessionId);

            ProfilingResult<T> profilingResult = new ProfilingResult<>();
            profilingResult.result = result;
            profilingResult.exception = exception;
            profilingResult.executionTimeNs = endTime - startTime;
            profilingResult.cpuTimeNs = endCpuTime - startCpuTime;
            profilingResult.operationName = operationName;
            profilingResult.report = report;

            return profilingResult;
        }
    }

    /**
 * Analyze memory usage patterns */
    public MemoryAnalysis analyzeMemoryUsage(List<PerformanceSnapshot> snapshots) {
        MemoryAnalysis analysis = new MemoryAnalysis();

        if (snapshots.isEmpty()) {
            return analysis;
        }

        // Calculate memory statistics
        List<Long> heapUsed =
                snapshots.stream().map(s -> s.memoryUsage.heapUsed).collect(Collectors.toList());

        List<Long> nonHeapUsed =
                snapshots.stream().map(s -> s.memoryUsage.nonHeapUsed).collect(Collectors.toList());

        analysis.heapUsageStats = calculateLongStatistics(heapUsed);
        analysis.nonHeapUsageStats = calculateLongStatistics(nonHeapUsed);

        // Detect memory leaks (increasing trend)
        analysis.potentialMemoryLeak = detectMemoryLeak(heapUsed);

        // Analyze GC performance
        analysis.gcAnalysis = analyzeGarbageCollection(snapshots);

        // Memory pool analysis
        analysis.memoryPoolAnalysis = analyzeMemoryPools(snapshots);

        return analysis;
    }

    /**
 * Analyze CPU usage patterns */
    public CpuAnalysis analyzeCpuUsage(List<PerformanceSnapshot> snapshots) {
        CpuAnalysis analysis = new CpuAnalysis();

        if (snapshots.isEmpty()) {
            return analysis;
        }

        List<Double> cpuUsage =
                snapshots.stream()
                        .map(s -> s.cpuUsage.processCpuLoad)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

        if (!cpuUsage.isEmpty()) {
            analysis.cpuUsageStats = calculateDoubleStatistics(cpuUsage);
        }

        // Analyze thread patterns
        analysis.threadAnalysis = analyzeThreads(snapshots);

        return analysis;
    }

    /**
 * Generate performance recommendations */
    public List<PerformanceRecommendation> generateRecommendations(ProfileReport report) {
        List<PerformanceRecommendation> recommendations = new ArrayList<>();

        // Memory recommendations
        if (report.memoryAnalysis != null) {
            recommendations.addAll(generateMemoryRecommendations(report.memoryAnalysis));
        }

        // CPU recommendations
        if (report.cpuAnalysis != null) {
            recommendations.addAll(generateCpuRecommendations(report.cpuAnalysis));
        }

        // GC recommendations
        if (report.memoryAnalysis != null && report.memoryAnalysis.gcAnalysis != null) {
            recommendations.addAll(generateGcRecommendations(report.memoryAnalysis.gcAnalysis));
        }

        return recommendations;
    }

    /**
 * Export profiling data to file */
    public void exportReport(ProfileReport report, Path outputPath, ExportFormat format)
            throws IOException {
        Files.createDirectories(outputPath.getParent());

        switch (format) {
            case JSON -> {
                String json = objectMapper.writeValueAsString(report);
                Files.writeString(
                        outputPath,
                        json,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
            case HTML -> {
                String html = generateHtmlReport(report);
                Files.writeString(
                        outputPath,
                        html,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
            case CSV -> {
                String csv = generateCsvReport(report);
                Files.writeString(
                        outputPath,
                        csv,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

        logger.info("Exported profiling report to: {}", outputPath);
    }

    // Private methods

    private void captureSnapshot(String sessionId) {
        List<PerformanceSnapshot> snapshots = sessionData.get(sessionId);
        if (snapshots == null || snapshots.size() >= maxSamplesPerSession) {
            return;
        }

        try {
            PerformanceSnapshot snapshot = createSnapshot();
            snapshots.add(snapshot);
        } catch (Exception e) {
            logger.warn(
                    "Failed to capture performance snapshot for session {}: {}",
                    sessionId,
                    e.getMessage());
        }
    }

    private PerformanceSnapshot createSnapshot() {
        PerformanceSnapshot snapshot = new PerformanceSnapshot();
        snapshot.timestamp = Instant.now();

        // Memory usage
        snapshot.memoryUsage = captureMemoryUsage();

        // CPU usage
        snapshot.cpuUsage = captureCpuUsage();

        // Thread information
        snapshot.threadInfo = captureThreadInfo();

        // GC information
        snapshot.gcInfo = captureGcInfo();

        return snapshot;
    }

    private MemoryUsageInfo captureMemoryUsage() {
        MemoryUsageInfo info = new MemoryUsageInfo();

        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        info.heapUsed = heapUsage.getUsed();
        info.heapMax = heapUsage.getMax();
        info.heapCommitted = heapUsage.getCommitted();

        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        info.nonHeapUsed = nonHeapUsage.getUsed();
        info.nonHeapMax = nonHeapUsage.getMax();
        info.nonHeapCommitted = nonHeapUsage.getCommitted();

        // Memory pool details
        info.memoryPools =
                memoryPoolBeans.stream()
                        .collect(
                                Collectors.toMap(
                                        MemoryPoolMXBean::getName,
                                        bean -> {
                                            MemoryUsage usage = bean.getUsage();
                                            MemoryPoolInfo poolInfo = new MemoryPoolInfo();
                                            poolInfo.used = usage.getUsed();
                                            poolInfo.max = usage.getMax();
                                            poolInfo.committed = usage.getCommitted();
                                            poolInfo.type = bean.getType().name();
                                            return poolInfo;
                                        }));

        return info;
    }

    private CpuUsageInfo captureCpuUsage() {
        CpuUsageInfo info = new CpuUsageInfo();

        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            info.processCpuLoad = sunOsBean.getProcessCpuLoad();
            info.systemCpuLoad = sunOsBean.getSystemCpuLoad();
            info.processCpuTime = sunOsBean.getProcessCpuTime();
        }

        info.availableProcessors = osBean.getAvailableProcessors();
        info.systemLoadAverage = osBean.getSystemLoadAverage();

        return info;
    }

    private ThreadInfo captureThreadInfo() {
        ThreadInfo info = new ThreadInfo();
        info.threadCount = threadBean.getThreadCount();
        info.daemonThreadCount = threadBean.getDaemonThreadCount();
        info.peakThreadCount = threadBean.getPeakThreadCount();
        info.totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        // Detect deadlocks
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        info.deadlockedThreadCount = deadlockedThreads != null ? deadlockedThreads.length : 0;

        return info;
    }

    private GcInfo captureGcInfo() {
        GcInfo info = new GcInfo();

        info.collectors =
                gcBeans.stream()
                        .collect(
                                Collectors.toMap(
                                        GarbageCollectorMXBean::getName,
                                        bean -> {
                                            GcCollectorInfo collectorInfo = new GcCollectorInfo();
                                            collectorInfo.collectionCount =
                                                    bean.getCollectionCount();
                                            collectorInfo.collectionTime = bean.getCollectionTime();
                                            collectorInfo.memoryPoolNames =
                                                    new ArrayList<>(
                                                            Arrays.asList(
                                                                    bean.getMemoryPoolNames()));
                                            return collectorInfo;
                                        }));

        return info;
    }

    private ProfileReport generateReport(
            ProfileSession session, List<PerformanceSnapshot> snapshots) {
        ProfileReport report = new ProfileReport();
        report.session = session;
        report.snapshotCount = snapshots.size();
        report.generatedAt = Instant.now();

        if (!snapshots.isEmpty()) {
            report.memoryAnalysis = analyzeMemoryUsage(snapshots);
            report.cpuAnalysis = analyzeCpuUsage(snapshots);
            report.recommendations = generateRecommendations(report);
        }

        return report;
    }

    private boolean detectMemoryLeak(List<Long> heapUsage) {
        if (heapUsage.size() < 10) {
            return false;
        }

        // Simple trend detection - check if memory usage is consistently increasing
        int increasingCount = 0;
        for (int i = 1; i < heapUsage.size(); i++) {
            if (heapUsage.get(i) > heapUsage.get(i - 1)) {
                increasingCount++;
            }
        }

        // If more than 70% of samples show increasing memory, flag as potential leak
        return (double) increasingCount / (heapUsage.size() - 1) > 0.7;
    }

    private GcAnalysis analyzeGarbageCollection(List<PerformanceSnapshot> snapshots) {
        GcAnalysis analysis = new GcAnalysis();

        Map<String, List<Long>> collectionCounts = new HashMap<>();
        Map<String, List<Long>> collectionTimes = new HashMap<>();

        for (PerformanceSnapshot snapshot : snapshots) {
            for (Map.Entry<String, GcCollectorInfo> entry : snapshot.gcInfo.collectors.entrySet()) {
                String collectorName = entry.getKey();
                GcCollectorInfo info = entry.getValue();

                collectionCounts
                        .computeIfAbsent(collectorName, k -> new ArrayList<>())
                        .add(info.collectionCount);
                collectionTimes
                        .computeIfAbsent(collectorName, k -> new ArrayList<>())
                        .add(info.collectionTime);
            }
        }

        analysis.collectorStats = new HashMap<>();
        for (String collectorName : collectionCounts.keySet()) {
            GcCollectorStats stats = new GcCollectorStats();
            stats.totalCollections =
                    collectionCounts.get(collectorName).stream()
                            .mapToLong(Long::longValue)
                            .max()
                            .orElse(0);
            stats.totalTime =
                    collectionTimes.get(collectorName).stream()
                            .mapToLong(Long::longValue)
                            .max()
                            .orElse(0);

            analysis.collectorStats.put(collectorName, stats);
        }

        return analysis;
    }

    private Map<String, MemoryPoolStats> analyzeMemoryPools(List<PerformanceSnapshot> snapshots) {
        Map<String, MemoryPoolStats> poolStats = new HashMap<>();

        for (PerformanceSnapshot snapshot : snapshots) {
            for (Map.Entry<String, MemoryPoolInfo> entry :
                    snapshot.memoryUsage.memoryPools.entrySet()) {
                String poolName = entry.getKey();
                MemoryPoolInfo poolInfo = entry.getValue();

                poolStats
                        .computeIfAbsent(poolName, k -> new MemoryPoolStats())
                        .usageValues
                        .add(poolInfo.used);
            }
        }

        // Calculate statistics for each pool
        for (MemoryPoolStats stats : poolStats.values()) {
            stats.statistics = calculateLongStatistics(stats.usageValues);
        }

        return poolStats;
    }

    private ThreadAnalysis analyzeThreads(List<PerformanceSnapshot> snapshots) {
        ThreadAnalysis analysis = new ThreadAnalysis();

        List<Integer> threadCounts =
                snapshots.stream().map(s -> s.threadInfo.threadCount).collect(Collectors.toList());

        analysis.threadCountStats = calculateIntStatistics(threadCounts);
        analysis.hasDeadlocks =
                snapshots.stream().anyMatch(s -> s.threadInfo.deadlockedThreadCount > 0);

        return analysis;
    }

    private List<PerformanceRecommendation> generateMemoryRecommendations(MemoryAnalysis analysis) {
        List<PerformanceRecommendation> recommendations = new ArrayList<>();

        if (analysis.potentialMemoryLeak) {
            recommendations.add(
                    new PerformanceRecommendation(
                            "CRITICAL",
                            "Memory Leak Detection",
                            "Potential memory leak detected. Consider using memory profilers like"
                                    + " JProfiler or async-profiler to identify the source.",
                            "Monitor object allocation patterns and ensure proper cleanup of"
                                    + " resources."));
        }

        if (analysis.heapUsageStats != null
                && analysis.heapUsageStats.mean > 0.8 * analysis.heapUsageStats.max) {
            recommendations.add(
                    new PerformanceRecommendation(
                            "HIGH",
                            "High Memory Usage",
                            "Average heap usage is above 80%. Consider increasing heap size with"
                                    + " -Xmx or optimizing memory usage.",
                            "Analyze memory allocation patterns and consider object pooling or"
                                    + " caching strategies."));
        }

        return recommendations;
    }

    private List<PerformanceRecommendation> generateCpuRecommendations(CpuAnalysis analysis) {
        List<PerformanceRecommendation> recommendations = new ArrayList<>();

        if (analysis.cpuUsageStats != null && analysis.cpuUsageStats.mean > 0.8) {
            recommendations.add(
                    new PerformanceRecommendation(
                            "HIGH",
                            "High CPU Usage",
                            "Average CPU usage is above 80%. Consider optimizing algorithms or"
                                    + " increasing parallelism.",
                            "Profile CPU-intensive operations and consider using more efficient"
                                    + " algorithms or data structures."));
        }

        if (analysis.threadAnalysis != null && analysis.threadAnalysis.hasDeadlocks) {
            recommendations.add(
                    new PerformanceRecommendation(
                            "CRITICAL",
                            "Thread Deadlocks",
                            "Deadlocks detected in thread execution. Review synchronization logic.",
                            "Use thread dumps to analyze deadlock patterns and redesign locking"
                                    + " strategies."));
        }

        return recommendations;
    }

    private List<PerformanceRecommendation> generateGcRecommendations(GcAnalysis analysis) {
        List<PerformanceRecommendation> recommendations = new ArrayList<>();

        long totalGcTime =
                analysis.collectorStats.values().stream().mapToLong(stats -> stats.totalTime).sum();

        if (totalGcTime > 5000) { // More than 5 seconds of GC time
            recommendations.add(
                    new PerformanceRecommendation(
                            "MEDIUM",
                            "High GC Overhead",
                            "Garbage collection is consuming significant time. Consider GC tuning.",
                            "Experiment with different GC algorithms (G1GC, ZGC) or adjust heap"
                                    + " sizing parameters."));
        }

        return recommendations;
    }

    private String generateHtmlReport(ProfileReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<title>Performance Profiling Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append(
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color:"
                        + " white; padding: 20px; border-radius: 8px; }\n");
        html.append(
                ".section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius:"
                        + " 5px; }\n");
        html.append(
                ".metric { display: inline-block; margin: 10px; padding: 10px; background: #f8f9fa;"
                        + " border-radius: 4px; }\n");
        html.append(".critical { color: #dc3545; font-weight: bold; }\n");
        html.append(".high { color: #fd7e14; font-weight: bold; }\n");
        html.append(".medium { color: #ffc107; font-weight: bold; }\n");
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<div class='header'>\n");
        html.append("<h1>Performance Profiling Report</h1>\n");
        html.append("<p>Session: ").append(report.session.sessionName).append("</p>\n");
        html.append("<p>Duration: ")
                .append(report.session.startTime)
                .append(" - ")
                .append(report.session.endTime)
                .append("</p>\n");
        html.append("</div>\n");

        // Memory Analysis
        if (report.memoryAnalysis != null) {
            html.append("<div class='section'>\n");
            html.append("<h2>Memory Analysis</h2>\n");
            if (report.memoryAnalysis.heapUsageStats != null) {
                html.append("<div class='metric'>Heap Usage - Avg: ")
                        .append(formatBytes(report.memoryAnalysis.heapUsageStats.mean))
                        .append("</div>\n");
                html.append("<div class='metric'>Heap Usage - Max: ")
                        .append(formatBytes(report.memoryAnalysis.heapUsageStats.max))
                        .append("</div>\n");
            }
            if (report.memoryAnalysis.potentialMemoryLeak) {
                html.append("<div class='critical'>⚠️ Potential memory leak detected!</div>\n");
            }
            html.append("</div>\n");
        }

        // Recommendations
        if (!report.recommendations.isEmpty()) {
            html.append("<div class='section'>\n");
            html.append("<h2>Recommendations</h2>\n");
            for (PerformanceRecommendation rec : report.recommendations) {
                String cssClass = rec.priority.toLowerCase();
                html.append("<div class='").append(cssClass).append("'>\n");
                html.append("<h3>")
                        .append(rec.title)
                        .append(" (")
                        .append(rec.priority)
                        .append(")</h3>\n");
                html.append("<p>").append(rec.description).append("</p>\n");
                html.append("<p><em>").append(rec.suggestion).append("</em></p>\n");
                html.append("</div>\n");
            }
            html.append("</div>\n");
        }

        html.append("</body>\n</html>");
        return html.toString();
    }

    private String generateCsvReport(ProfileReport report) {
        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value,Unit\n");

        if (report.memoryAnalysis != null && report.memoryAnalysis.heapUsageStats != null) {
            csv.append("Heap Usage Average,")
                    .append(report.memoryAnalysis.heapUsageStats.mean)
                    .append(",bytes\n");
            csv.append("Heap Usage Maximum,")
                    .append(report.memoryAnalysis.heapUsageStats.max)
                    .append(",bytes\n");
        }

        return csv.toString();
    }

    // Utility methods

    private String generateSessionId(String sessionName) {
        return sessionName + "-" + System.currentTimeMillis();
    }

    private long getCurrentThreadCpuTime() {
        return threadBean.isCurrentThreadCpuTimeSupported()
                ? threadBean.getCurrentThreadCpuTime()
                : 0;
    }

    private Statistics<Long> calculateLongStatistics(List<Long> values) {
        if (values.isEmpty()) {
            return new Statistics<>();
        }

        Statistics<Long> stats = new Statistics<>();
        stats.count = values.size();
        stats.sum = values.stream().mapToLong(Long::longValue).sum();
        stats.mean = stats.sum / (double) stats.count;
        stats.min = values.stream().mapToLong(Long::longValue).min().orElse(0);
        stats.max = values.stream().mapToLong(Long::longValue).max().orElse(0);

        List<Long> sorted = values.stream().sorted().collect(Collectors.toList());
        stats.median = sorted.get(sorted.size() / 2);

        return stats;
    }

    private Statistics<Double> calculateDoubleStatistics(List<Double> values) {
        if (values.isEmpty()) {
            return new Statistics<>();
        }

        Statistics<Double> stats = new Statistics<>();
        stats.count = values.size();
        stats.sum = values.stream().mapToDouble(Double::doubleValue).sum();
        stats.mean = stats.sum / stats.count;
        stats.min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        stats.max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        stats.median = sorted.get(sorted.size() / 2);

        return stats;
    }

    private Statistics<Integer> calculateIntStatistics(List<Integer> values) {
        if (values.isEmpty()) {
            return new Statistics<>();
        }

        Statistics<Integer> stats = new Statistics<>();
        stats.count = values.size();
        stats.sum = values.stream().mapToDouble(Integer::doubleValue).sum();
        stats.mean = stats.sum / stats.count;
        stats.min = values.stream().mapToInt(Integer::intValue).min().orElse(0);
        stats.max = values.stream().mapToInt(Integer::intValue).max().orElse(0);

        List<Integer> sorted = values.stream().sorted().collect(Collectors.toList());
        stats.median = sorted.get(sorted.size() / 2);

        return stats;
    }

    private String formatBytes(double bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Data classes and interfaces

    @FunctionalInterface
    public interface ProfiledOperation<T> {
        T execute() throws Exception;
    }

    public enum ExportFormat {
        JSON,
        HTML,
        CSV
    }

    public static class ProfileSession {
        public String sessionId;
        public String sessionName;
        public Instant startTime;
        public Instant endTime;
        public long samplingInterval;
    }

    public static class PerformanceSnapshot {
        public Instant timestamp;
        public MemoryUsageInfo memoryUsage;
        public CpuUsageInfo cpuUsage;
        public ThreadInfo threadInfo;
        public GcInfo gcInfo;
    }

    public static class MemoryUsageInfo {
        public long heapUsed;
        public long heapMax;
        public long heapCommitted;
        public long nonHeapUsed;
        public long nonHeapMax;
        public long nonHeapCommitted;
        public Map<String, MemoryPoolInfo> memoryPools;
    }

    public static class MemoryPoolInfo {
        public long used;
        public long max;
        public long committed;
        public String type;
    }

    public static class CpuUsageInfo {
        public Double processCpuLoad;
        public Double systemCpuLoad;
        public Long processCpuTime;
        public int availableProcessors;
        public double systemLoadAverage;
    }

    public static class ThreadInfo {
        public int threadCount;
        public int daemonThreadCount;
        public int peakThreadCount;
        public long totalStartedThreadCount;
        public int deadlockedThreadCount;
    }

    public static class GcInfo {
        public Map<String, GcCollectorInfo> collectors;
    }

    public static class GcCollectorInfo {
        public long collectionCount;
        public long collectionTime;
        public List<String> memoryPoolNames;
    }

    public static class ProfileReport {
        public ProfileSession session;
        public int snapshotCount;
        public Instant generatedAt;
        public MemoryAnalysis memoryAnalysis;
        public CpuAnalysis cpuAnalysis;
        public List<PerformanceRecommendation> recommendations;
    }

    public static class MemoryAnalysis {
        public Statistics<Long> heapUsageStats;
        public Statistics<Long> nonHeapUsageStats;
        public boolean potentialMemoryLeak;
        public GcAnalysis gcAnalysis;
        public Map<String, MemoryPoolStats> memoryPoolAnalysis;
    }

    public static class CpuAnalysis {
        public Statistics<Double> cpuUsageStats;
        public ThreadAnalysis threadAnalysis;
    }

    public static class GcAnalysis {
        public Map<String, GcCollectorStats> collectorStats;
    }

    public static class GcCollectorStats {
        public long totalCollections;
        public long totalTime;
    }

    public static class MemoryPoolStats {
        public List<Long> usageValues = new ArrayList<>();
        public Statistics<Long> statistics;
    }

    public static class ThreadAnalysis {
        public Statistics<Integer> threadCountStats;
        public boolean hasDeadlocks;
    }

    public static class Statistics<T extends Number> {
        public int count;
        public double sum;
        public double mean;
        public T min;
        public T max;
        public T median;
    }

    public static class PerformanceRecommendation {
        public String priority;
        public String title;
        public String description;
        public String suggestion;

        public PerformanceRecommendation(
                String priority, String title, String description, String suggestion) {
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.suggestion = suggestion;
        }
    }

    public static class ProfilingResult<T> {
        public T result;
        public Exception exception;
        public long executionTimeNs;
        public long cpuTimeNs;
        public String operationName;
        public ProfileReport report;
    }
}
