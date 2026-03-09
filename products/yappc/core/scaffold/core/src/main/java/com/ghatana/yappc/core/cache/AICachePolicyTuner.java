/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.yappc.core.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI-powered cache policy tuner that analyzes cache performance and suggests
 * optimizations.
 *
 * <p>
 * Week 10 Day 46: AI cache policy tuner with task timing analysis and
 * optimization suggestions.
 *
 * @doc.type class
 * @doc.purpose AI-powered cache policy tuner that analyzes cache performance
 * and suggests optimizations.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class AICachePolicyTuner {

    private final Map<String, TaskTimingData> taskTimings = new ConcurrentHashMap<>();
    private final Map<String, CacheAccessPattern> cachePatterns = new ConcurrentHashMap<>();
    private final AtomicLong totalAnalysisRuns = new AtomicLong();

    /**
     * Records timing data for a specific task execution.
     */
    public void recordTaskTiming(TaskExecution execution) {
        String taskKey = execution.taskType() + ":" + execution.projectPath();

        taskTimings.compute(
                taskKey,
                (key, existing) -> {
                    if (existing == null) {
                        return new TaskTimingData(
                                execution.taskType(),
                                execution.projectPath(),
                                List.of(execution.duration()),
                                execution.cacheHit(),
                                execution.artifactSize(),
                                execution.timestamp());
                    } else {
                        List<Duration> newDurations = new ArrayList<>(existing.durations());
                        newDurations.add(execution.duration());
                        return new TaskTimingData(
                                existing.taskType(),
                                existing.projectPath(),
                                newDurations,
                                execution.cacheHit(),
                                execution.artifactSize(),
                                execution.timestamp());
                    }
                });
    }

    /**
     * Records cache access pattern for analysis.
     */
    public void recordCacheAccess(CacheAccessEvent event) {
        String cacheKey = event.cacheKey();

        cachePatterns.compute(
                cacheKey,
                (key, existing) -> {
                    if (existing == null) {
                        return new CacheAccessPattern(
                                cacheKey,
                                1,
                                event.hit() ? 1 : 0,
                                event.accessTime(),
                                event.accessTime(),
                                event.artifactSize(),
                                List.of(event.accessTime()));
                    } else {
                        List<Instant> newAccessTimes = new ArrayList<>(existing.accessTimes());
                        newAccessTimes.add(event.accessTime());

                        return new CacheAccessPattern(
                                cacheKey,
                                existing.accessCount() + 1,
                                existing.hitCount() + (event.hit() ? 1 : 0),
                                existing.firstAccess(),
                                event.accessTime(),
                                event.artifactSize(),
                                newAccessTimes);
                    }
                });
    }

    /**
     * Analyzes collected data and generates AI-powered optimization
     * recommendations.
     */
    public CachePolicyRecommendations analyzeCachePolicy() {
        totalAnalysisRuns.incrementAndGet();

        List<CacheRecommendation> recommendations = new ArrayList<>();
        Map<String, Object> metrics = new HashMap<>();

        // Analyze task timing patterns
        analyzeTaskTimingPatterns(recommendations, metrics);

        // Analyze cache access patterns
        analyzeCacheAccessPatterns(recommendations, metrics);

        // Generate AI-powered insights
        generateAIInsights(recommendations, metrics);

        // Calculate overall cache efficiency
        CacheEfficiencyScore efficiency = calculateCacheEfficiency();

        return new CachePolicyRecommendations(
                recommendations, metrics, efficiency, Instant.now(), totalAnalysisRuns.get());
    }

    private void analyzeTaskTimingPatterns(
            List<CacheRecommendation> recommendations, Map<String, Object> metrics) {
        Map<String, TaskPerformanceStats> taskStats = new HashMap<>();

        for (TaskTimingData timing : taskTimings.values()) {
            String taskType = timing.taskType();

            TaskPerformanceStats stats
                    = taskStats.computeIfAbsent(
                            taskType,
                            k
                            -> new TaskPerformanceStats(
                                    taskType, new ArrayList<>(), new ArrayList<>()));

            stats.cachedExecutions()
                    .addAll(timing.durations().stream().filter(d -> timing.cacheHit()).toList());

            stats.uncachedExecutions()
                    .addAll(timing.durations().stream().filter(d -> !timing.cacheHit()).toList());
        }

        // Analyze each task type
        for (TaskPerformanceStats stats : taskStats.values()) {
            analyzeTaskPerformance(stats, recommendations);
        }

        metrics.put("taskTypes", taskStats.size());
        metrics.put("totalTaskExecutions", taskTimings.size());
    }

    private void analyzeTaskPerformance(
            TaskPerformanceStats stats, List<CacheRecommendation> recommendations) {
        if (stats.cachedExecutions().isEmpty() && !stats.uncachedExecutions().isEmpty()) {
            // No cache hits - recommend cache optimization
            Duration avgUncached = calculateAverageDuration(stats.uncachedExecutions());

            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.ENABLE_CACHING,
                            RecommendationPriority.HIGH,
                            "Task type '" + stats.taskType() + "' shows no cache hits",
                            "Enable aggressive caching for "
                            + stats.taskType()
                            + " tasks. Average execution time: "
                            + formatDuration(avgUncached),
                            Map.of(
                                    "taskType", stats.taskType(),
                                    "avgUncachedDuration", avgUncached.toMillis(),
                                    "potentialSavings",
                                    calculatePotentialSavings(
                                            avgUncached,
                                            stats.uncachedExecutions().size()))));
        } else if (!stats.cachedExecutions().isEmpty() && !stats.uncachedExecutions().isEmpty()) {
            // Mixed cache performance - analyze effectiveness
            Duration avgCached = calculateAverageDuration(stats.cachedExecutions());
            Duration avgUncached = calculateAverageDuration(stats.uncachedExecutions());

            double speedup = (double) avgUncached.toMillis() / avgCached.toMillis();

            if (speedup < 2.0) {
                recommendations.add(
                        new CacheRecommendation(
                                RecommendationType.OPTIMIZE_CACHE_STRATEGY,
                                RecommendationPriority.MEDIUM,
                                "Low cache efficiency for " + stats.taskType(),
                                String.format(
                                        "Cache speedup is only %.1fx. Consider cache key"
                                        + " optimization or different caching strategy",
                                        speedup),
                                Map.of(
                                        "taskType", stats.taskType(),
                                        "speedup", speedup,
                                        "avgCachedMs", avgCached.toMillis(),
                                        "avgUncachedMs", avgUncached.toMillis())));
            }
        }
    }

    private void analyzeCacheAccessPatterns(
            List<CacheRecommendation> recommendations, Map<String, Object> metrics) {
        long totalAccesses = 0;
        long totalHits = 0;

        for (CacheAccessPattern pattern : cachePatterns.values()) {
            totalAccesses += pattern.accessCount();
            totalHits += pattern.hitCount();

            // Analyze individual cache key patterns
            analyzeCacheKeyPattern(pattern, recommendations);
        }

        double hitRate = totalAccesses > 0 ? (double) totalHits / totalAccesses : 0.0;

        metrics.put("totalCacheAccesses", totalAccesses);
        metrics.put("totalCacheHits", totalHits);
        metrics.put("overallHitRate", hitRate);

        if (hitRate < 0.5) {
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.INCREASE_CACHE_SIZE,
                            RecommendationPriority.HIGH,
                            "Low overall cache hit rate: " + String.format("%.1f%%", hitRate * 100),
                            "Consider increasing cache size or adjusting eviction policies",
                            Map.of("currentHitRate", hitRate, "recommendedHitRate", 0.8)));
        }
    }

    private void analyzeCacheKeyPattern(
            CacheAccessPattern pattern, List<CacheRecommendation> recommendations) {
        double hitRate = (double) pattern.hitCount() / pattern.accessCount();

        if (pattern.accessCount() > 10 && hitRate < 0.2) {
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.REMOVE_CACHE_KEY,
                            RecommendationPriority.LOW,
                            "Cache key '" + pattern.cacheKey() + "' has very low hit rate",
                            String.format(
                                    "Hit rate: %.1f%% (%d/%d). Consider excluding this pattern from"
                                    + " cache",
                                    hitRate * 100, pattern.hitCount(), pattern.accessCount()),
                            Map.of(
                                    "cacheKey", pattern.cacheKey(),
                                    "hitRate", hitRate,
                                    "accessCount", pattern.accessCount())));
        }

        // Analyze temporal patterns
        if (pattern.accessTimes().size() > 5) {
            analyzeTemporalPattern(pattern, recommendations);
        }
    }

    private void analyzeTemporalPattern(
            CacheAccessPattern pattern, List<CacheRecommendation> recommendations) {
        List<Instant> accessTimes = pattern.accessTimes();
        List<Duration> intervals = new ArrayList<>();

        for (int i = 1; i < accessTimes.size(); i++) {
            intervals.add(Duration.between(accessTimes.get(i - 1), accessTimes.get(i)));
        }

        // Check for regular access patterns
        Duration avgInterval = calculateAverageInterval(intervals);
        boolean regularPattern
                = intervals.stream()
                        .allMatch(
                                d
                                -> Math.abs(d.toMillis() - avgInterval.toMillis())
                                < avgInterval.toMillis() * 0.3);

        if (regularPattern && avgInterval.toHours() < 1) {
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.ADJUST_TTL,
                            RecommendationPriority.MEDIUM,
                            "Regular access pattern detected for " + pattern.cacheKey(),
                            "Consider extending TTL to "
                            + formatDuration(avgInterval.multipliedBy(2))
                            + " based on access pattern",
                            Map.of(
                                    "cacheKey", pattern.cacheKey(),
                                    "accessInterval", avgInterval.toMinutes(),
                                    "recommendedTTL", avgInterval.multipliedBy(2).toMinutes())));
        }
    }

    private void generateAIInsights(
            List<CacheRecommendation> recommendations, Map<String, Object> metrics) {
        // AI-powered insights based on collected patterns

        // Memory usage optimization
        long totalArtifactSize
                = cachePatterns.values().stream().mapToLong(CacheAccessPattern::artifactSize).sum();

        if (totalArtifactSize > 1024 * 1024 * 1024) { // > 1GB
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.MEMORY_OPTIMIZATION,
                            RecommendationPriority.HIGH,
                            "High memory usage detected",
                            "Total cached artifacts: "
                            + formatBytes(totalArtifactSize)
                            + ". Consider compression or selective caching",
                            Map.of("totalSize", totalArtifactSize)));
        }

        // Build pattern optimization
        analyzeBuildPatterns(recommendations, metrics);

        // Cross-project caching insights
        analyzeCrossProjectPatterns(recommendations, metrics);
    }

    private void analyzeBuildPatterns(
            List<CacheRecommendation> recommendations, Map<String, Object> metrics) {
        Map<String, Long> projectExecutions = new HashMap<>();

        for (TaskTimingData timing : taskTimings.values()) {
            projectExecutions.merge(timing.projectPath(), 1L, Long::sum);
        }

        // Find frequently built projects
        List<Map.Entry<String, Long>> frequentProjects
                = projectExecutions.entrySet().stream()
                        .filter(e -> e.getValue() > 10)
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .toList();

        if (!frequentProjects.isEmpty()) {
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.PROJECT_SPECIFIC_TUNING,
                            RecommendationPriority.MEDIUM,
                            "High-frequency projects identified",
                            "Projects with most builds: "
                            + frequentProjects.stream()
                                    .map(e -> e.getKey() + " (" + e.getValue() + " builds)")
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(""),
                            Map.of("frequentProjects", frequentProjects)));
        }
    }

    private void analyzeCrossProjectPatterns(
            List<CacheRecommendation> recommendations, Map<String, Object> metrics) {
        // Look for common cache misses across projects
        Map<String, Set<String>> cacheKeyToProjects = new HashMap<>();

        for (TaskTimingData timing : taskTimings.values()) {
            String cacheKey = generateCacheKey(timing.taskType(), timing.projectPath());
            cacheKeyToProjects
                    .computeIfAbsent(cacheKey, k -> new HashSet<>())
                    .add(timing.projectPath());
        }

        // Find cache keys used across multiple projects
        List<String> sharedCacheKeys
                = cacheKeyToProjects.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(Map.Entry::getKey)
                        .toList();

        if (!sharedCacheKeys.isEmpty()) {
            recommendations.add(
                    new CacheRecommendation(
                            RecommendationType.SHARED_CACHE_OPTIMIZATION,
                            RecommendationPriority.HIGH,
                            "Cross-project cache opportunities identified",
                            "Found "
                            + sharedCacheKeys.size()
                            + " cache patterns used across multiple projects. "
                            + "Consider global cache warming strategies",
                            Map.of("sharedCacheKeys", sharedCacheKeys.size())));
        }
    }

    private CacheEfficiencyScore calculateCacheEfficiency() {
        if (taskTimings.isEmpty()) {
            return new CacheEfficiencyScore(
                    0.0, "Insufficient data", Map.of("reason", "No task timing data available"));
        }

        long cachedTasks = taskTimings.values().stream().mapToLong(t -> t.cacheHit() ? 1 : 0).sum();

        double cacheHitRate = (double) cachedTasks / taskTimings.size();

        // Calculate time savings
        Duration totalCachedTime
                = taskTimings.values().stream()
                        .filter(t -> t.cacheHit())
                        .flatMap(t -> t.durations().stream())
                        .reduce(Duration.ZERO, Duration::plus);

        Duration totalUncachedTime
                = taskTimings.values().stream()
                        .filter(t -> !t.cacheHit())
                        .flatMap(t -> t.durations().stream())
                        .reduce(Duration.ZERO, Duration::plus);

        double efficiencyScore;
        String interpretation;

        if (cacheHitRate >= 0.8) {
            efficiencyScore = 0.9 + (cacheHitRate - 0.8) * 0.5;
            interpretation = "Excellent cache efficiency";
        } else if (cacheHitRate >= 0.6) {
            efficiencyScore = 0.7 + (cacheHitRate - 0.6) * 1.0;
            interpretation = "Good cache efficiency";
        } else if (cacheHitRate >= 0.4) {
            efficiencyScore = 0.5 + (cacheHitRate - 0.4) * 1.0;
            interpretation = "Moderate cache efficiency";
        } else {
            efficiencyScore = cacheHitRate * 1.25;
            interpretation = "Poor cache efficiency - needs optimization";
        }

        return new CacheEfficiencyScore(
                efficiencyScore,
                interpretation,
                Map.of(
                        "cacheHitRate", cacheHitRate,
                        "totalTasks", taskTimings.size(),
                        "cachedTasks", cachedTasks,
                        "totalCachedTimeMs", totalCachedTime.toMillis(),
                        "totalUncachedTimeMs", totalUncachedTime.toMillis()));
    }

    // Utility methods
    private Duration calculateAverageDuration(List<Duration> durations) {
        return durations.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(durations.size());
    }

    private Duration calculateAverageInterval(List<Duration> intervals) {
        return intervals.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(intervals.size());
    }

    private long calculatePotentialSavings(Duration avgDuration, int executionCount) {
        // Assume 70% cache hit rate and 10x speedup
        return (long) (avgDuration.toMillis() * executionCount * 0.7 * 0.9);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String generateCacheKey(String taskType, String projectPath) {
        return taskType + ":" + projectPath.hashCode();
    }

    // Data classes
    public record TaskExecution(
            String taskType,
            String projectPath,
            Duration duration,
            boolean cacheHit,
            long artifactSize,
            Instant timestamp) {
    }

    public record TaskTimingData(
            String taskType,
            String projectPath,
            List<Duration> durations,
            boolean cacheHit,
            long artifactSize,
            Instant lastExecution) {
    }

    public record CacheAccessEvent(
            String cacheKey, boolean hit, Instant accessTime, long artifactSize) {
    }

    public record CacheAccessPattern(
            String cacheKey,
            int accessCount,
            int hitCount,
            Instant firstAccess,
            Instant lastAccess,
            long artifactSize,
            List<Instant> accessTimes) {
    }

    public record TaskPerformanceStats(
            String taskType, List<Duration> cachedExecutions, List<Duration> uncachedExecutions) {
    }

    public record CacheRecommendation(
            RecommendationType type,
            RecommendationPriority priority,
            String title,
            String description,
            Map<String, Object> metadata) {
    }

    public record CachePolicyRecommendations(
            List<CacheRecommendation> recommendations,
            Map<String, Object> metrics,
            CacheEfficiencyScore efficiency,
            Instant analysisTime,
            long analysisRun) {
    }

    public record CacheEfficiencyScore(
            double score, String interpretation, Map<String, Object> details) {
    }

}
