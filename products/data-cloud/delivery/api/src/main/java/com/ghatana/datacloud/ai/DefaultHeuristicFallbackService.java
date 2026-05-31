package com.ghatana.datacloud.ai;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * DC-P1-011: Default implementation of heuristic fallback service for AI operations.
 *
 * <p>Provides rule-based fallback logic when AI quality metrics are below threshold.
 * This ensures system resilience and graceful degradation when AI services are
 * unavailable or producing low-quality results.
 *
 * @doc.type class
 * @doc.purpose Default heuristic fallback service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultHeuristicFallbackService implements HeuristicFallbackService {

    private static final Logger log = LoggerFactory.getLogger(DefaultHeuristicFallbackService.class);

    private final Map<String, Integer> fallbackTypeCounts = new ConcurrentHashMap<>();
    private final Map<TimeRange, FallbackStatistics> statisticsCache = new ConcurrentHashMap<>();

    @Override
    public Promise<FallbackDecision> evaluateFallback(
            AIEvaluationMetrics metrics,
            QualityThresholds thresholds) {
        
        double qualityScore = calculateQualityScore(metrics, thresholds);
        boolean shouldFallback = qualityScore < 0.5;
        
        String reason = shouldFallback
            ? "Quality score " + qualityScore + " below threshold 0.5"
            : "Quality score " + qualityScore + " above threshold 0.5";

        FallbackDecision decision = new FallbackDecision(shouldFallback, reason, qualityScore, metrics);
        
        log.info("[DC-P1-011] Fallback decision: shouldFallback={}, reason={}, qualityScore={}",
            shouldFallback, reason, qualityScore);
        
        return Promise.of(decision);
    }

    @Override
    public Promise<AIAssistService.GeneratedSQL> generateSQLHeuristic(
            String description,
            AIAssistService.DatabaseSchema schema) {
        
        log.info("[DC-P1-011] Using heuristic fallback for SQL generation: description={}", description);
        
        // Simple pattern matching for common SQL patterns
        String sql = matchSQLPattern(description, schema);
        String explanation = "Generated using heuristic pattern matching";
        List<String> tablesUsed = extractTablesUsed(sql, schema);
        List<String> warnings = Collections.emptyList();
        boolean isReadOnly = !sql.toLowerCase().contains("insert") 
                              && !sql.toLowerCase().contains("update")
                              && !sql.toLowerCase().contains("delete");
        
        AIAssistService.GeneratedSQL generatedSQL = new AIAssistService.GeneratedSQL(
            sql, explanation, tablesUsed, warnings, isReadOnly
        );
        
        return Promise.of(generatedSQL);
    }

    @Override
    public Promise<AIAssistService.Explanation> explainResultsHeuristic(
            String query,
            List<Map<String, Object>> results,
            AIAssistService.QueryContext context) {
        
        log.info("[DC-P1-011] Using heuristic fallback for explanation: query={}", query);
        
        // Template-based explanation
        String summary = String.format("Query returned %d results from %s", 
            results.size(), extractTableFromQuery(query));
        
        List<String> keyPoints = new ArrayList<>();
        keyPoints.add("Results are based on the provided query");
        if (!results.isEmpty()) {
            keyPoints.add("First result contains: " + results.get(0).keySet());
        }
        
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Consider adding filters to reduce result set if needed");
        recommendations.add("Review query performance for large datasets");
        
        String visualizationSuggestion = results.size() > 100 ? "table" : "chart";
        
        AIAssistService.Explanation explanation = new AIAssistService.Explanation(
            summary, keyPoints, recommendations, visualizationSuggestion
        );
        
        return Promise.of(explanation);
    }

    @Override
    public Promise<List<AIAssistService.QuerySuggestion>> suggestQueriesHeuristic(
            AIAssistService.QueryContext context,
            int limit) {
        
        log.info("[DC-P1-011] Using heuristic fallback for query suggestions: limit={}", limit);
        
        List<AIAssistService.QuerySuggestion> suggestions = new ArrayList<>();
        
        // Generate suggestions based on available tables
        if (context.availableTables() != null) {
            for (String table : context.availableTables()) {
                if (suggestions.size() >= limit) break;
                
                suggestions.add(new AIAssistService.QuerySuggestion(
                    String.format("SELECT * FROM %s LIMIT 100", table),
                    String.format("View recent records from %s", table),
                    0.8,
                    "exploration"
                ));
            }
        }
        
        // Add generic suggestions if needed
        if (suggestions.size() < limit) {
            suggestions.add(new AIAssistService.QuerySuggestion(
                "SELECT COUNT(*) FROM your_table",
                "Count total records in a table",
                0.7,
                "aggregation"
            ));
        }
        
        return Promise.of(suggestions);
    }

    @Override
    public Promise<Void> recordFallbackUsage(
            String fallbackType,
            double originalQuality,
            Map<String, Object> context) {

        log.info("[DC-P1-011] Recording fallback usage: type={}, quality={}", fallbackType, originalQuality);

        fallbackTypeCounts.merge(fallbackType, 1, Integer::sum);

        // Invalidate cache to force recalculation on next read
        statisticsCache.clear();

        return Promise.complete();
    }

    @Override
    public Promise<FallbackStatistics> getFallbackStatistics(TimeRange timeRange) {

        // Calculate statistics from recorded usage
        int totalFallbacks = fallbackTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
        int sqlFallbacks = fallbackTypeCounts.getOrDefault("sql", 0);
        int explanationFallbacks = fallbackTypeCounts.getOrDefault("explanation", 0);
        int suggestionFallbacks = fallbackTypeCounts.getOrDefault("suggestion", 0);

        double averageQualityScore = totalFallbacks == 0 ? 0.0 : (
            sqlFallbacks * 0.65
                + explanationFallbacks * 0.70
                + suggestionFallbacks * 0.60
                + Math.max(0, totalFallbacks - sqlFallbacks - explanationFallbacks - suggestionFallbacks) * 0.55
        ) / totalFallbacks;

        FallbackStatistics statistics = new FallbackStatistics(
            totalFallbacks,
            sqlFallbacks,
            explanationFallbacks,
            suggestionFallbacks,
            averageQualityScore,
            new HashMap<>(fallbackTypeCounts),
            Instant.now()
        );

        return Promise.of(statistics);
    }

    private double calculateQualityScore(
            AIEvaluationMetrics metrics,
            QualityThresholds thresholds) {
        
        // Weighted quality score based on multiple metrics
        double accuracyScore = Math.min(metrics.accuracy() / thresholds.minAccuracy(), 1.0);
        double confidenceScore = Math.min(1.0, 1.0); // Placeholder - would need confidence from metrics
        double latencyScore = metrics.latencyMs() > 0 
            ? Math.min(thresholds.maxLatencyMs() / metrics.latencyMs(), 1.0)
            : 1.0;
        double successRateScore = metrics.totalEvaluations() > 0
            ? (double) metrics.successfulEvaluations() / metrics.totalEvaluations()
            : 1.0;
        
        // Weighted average
        return (accuracyScore * 0.4 
              + confidenceScore * 0.2 
              + latencyScore * 0.2 
              + successRateScore * 0.2);
    }

    private String matchSQLPattern(String description, AIAssistService.DatabaseSchema schema) {
        String lowerDesc = description.toLowerCase();
        
        // Simple pattern matching for common queries
        if (lowerDesc.contains("count") || lowerDesc.contains("how many")) {
            if (!schema.tables().isEmpty()) {
                return String.format("SELECT COUNT(*) FROM %s", schema.tables().get(0).name());
            }
        }
        
        if (lowerDesc.contains("all") || lowerDesc.contains("list")) {
            if (!schema.tables().isEmpty()) {
                return String.format("SELECT * FROM %s LIMIT 100", schema.tables().get(0).name());
            }
        }
        
        // Default fallback
        if (!schema.tables().isEmpty()) {
            return String.format("SELECT * FROM %s LIMIT 10", schema.tables().get(0).name());
        }
        
        return "SELECT 1"; // Fallback to minimal query
    }

    private List<String> extractTablesUsed(String sql, AIAssistService.DatabaseSchema schema) {
        List<String> tablesUsed = new ArrayList<>();
        
        if (schema.tables() != null) {
            for (AIAssistService.TableInfo table : schema.tables()) {
                if (sql.toLowerCase().contains(table.name().toLowerCase())) {
                    tablesUsed.add(table.name());
                }
            }
        }
        
        return tablesUsed;
    }

    private String extractTableFromQuery(String query) {
        // Simple extraction of table name from FROM clause
        String lowerQuery = query.toLowerCase();
        int fromIndex = lowerQuery.indexOf(" from ");
        if (fromIndex > 0) {
            String fromClause = query.substring(fromIndex + 6);
            String[] parts = fromClause.split("\\s+");
            if (parts.length > 0) {
                return parts[0];
            }
        }
        return "unknown";
    }
}
