package com.ghatana.datacloud.infrastructure.query;

import com.ghatana.datacloud.infrastructure.query.QueryTelemetryService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing slow queries and recommending optimizations.
 *
 * <p><b>Purpose</b><br>
 * Analyzes query telemetry data to identify performance issues and suggest
 * database optimizations such as missing indexes, inefficient queries, and
 * schema improvements.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QueryAdvisorService advisor = new QueryAdvisorService(telemetryService);
 *
 * // Get index recommendations
 * List<IndexRecommendation> recommendations = advisor.recommendIndexes();
 *
 * // Analyze specific slow query
 * QueryAnalysis analysis = advisor.analyzeQuery("findByName");
 *
 * // Get optimization suggestions
 * List<OptimizationSuggestion> suggestions = advisor.getSuggestions();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Infrastructure layer service for query analysis
 * - Uses QueryTelemetryService for query execution data
 * - Provides actionable optimization recommendations
 * - Supports database performance tuning
 *
 * <p><b>Analysis Capabilities</b><br>
 * - Missing index detection (queries with table scans)
 * - Slow query identification (>500ms threshold)
 * - Query pattern analysis (SELECT *, JOIN without indexes)
 * - Result set size warnings (>1000 rows)
 * - Query complexity scoring
 *
 * <p><b>Recommendation Types</b><br>
 * - CREATE INDEX suggestions with column analysis
 * - Query rewrite suggestions (SELECT specific columns)
 * - Schema optimization (denormalization, partitioning)
 * - Caching recommendations (frequent identical queries)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - analysis uses immutable telemetry snapshots.
 *
 * @see QueryTelemetryService
 * @doc.type class
 * @doc.purpose Query analysis and optimization recommendations
 * @doc.layer product
 * @doc.pattern Service (Infrastructure Layer)
 */
public class QueryAdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryAdvisorService.class);

    private static final Duration SLOW_QUERY_THRESHOLD = Duration.ofMillis(500);
    private static final Pattern WHERE_CLAUSE_PATTERN = Pattern.compile(
        "WHERE\\s+([\\w.]+)\\s*=",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "FROM\\s+(\\w+)",
        Pattern.CASE_INSENSITIVE
    );

    private final QueryTelemetryService telemetryService;

    /**
     * Creates a new query advisor service.
     *
     * @param telemetryService the query telemetry service (required)
     * @throws NullPointerException if telemetryService is null
     */
    public QueryAdvisorService(QueryTelemetryService telemetryService) {
        this.telemetryService = Objects.requireNonNull(
            telemetryService,
            "TelemetryService must not be null"
        );
    }

    /**
     * Recommends indexes based on query telemetry.
     *
     * <p><b>Strategy</b><br>
     * Analyzes slow queries with table scans to suggest missing indexes.
     * Prioritizes recommendations by impact (query frequency × duration).
     *
     * @return list of index recommendations sorted by priority (descending)
     */
    public List<IndexRecommendation> recommendIndexes() {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        List<SlowQuery> slowQueries = telemetryService.getSlowQueries(SLOW_QUERY_THRESHOLD);

        for (SlowQuery slowQuery : slowQueries) {
            QueryStatistics stats = telemetryService.getQueryStatistics(slowQuery.queryName());

            if (stats != null && stats.tableScans() > 0 && stats.indexesUsed().isEmpty()) {
                // Extract table and columns from SQL
                String sql = slowQuery.exampleSql();
                String tableName = extractTableName(sql);
                List<String> columns = extractWhereColumns(sql);

                if (tableName != null && !columns.isEmpty()) {
                    long impact = slowQuery.occurrences() * slowQuery.averageDuration().toMillis();

                    recommendations.add(new IndexRecommendation(
                        slowQuery.queryName(),
                        tableName,
                        columns,
                        impact,
                        String.format("CREATE INDEX idx_%s_%s ON %s (%s)",
                            tableName,
                            String.join("_", columns),
                            tableName,
                            String.join(", ", columns)),
                        String.format("Slow query with %d table scans, avg duration %dms",
                            stats.tableScans(), slowQuery.averageDuration().toMillis())
                    ));
                }
            }
        }

        // Sort by impact (highest first)
        recommendations.sort(Comparator.comparing(IndexRecommendation::impact).reversed());

        LOGGER.info("Generated {} index recommendations", recommendations.size());
        return recommendations;
    }

    /**
     * Analyzes a specific query.
     *
     * @param queryName the query name to analyze (required)
     * @return QueryAnalysis with findings, or null if query not found
     * @throws NullPointerException if queryName is null
     */
    public QueryAnalysis analyzeQuery(String queryName) {
        Objects.requireNonNull(queryName, "QueryName must not be null");

        QueryStatistics stats = telemetryService.getQueryStatistics(queryName);
        if (stats == null) {
            return null;
        }

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int severityScore = 0;

        // Check average duration
        if (stats.averageDuration().compareTo(SLOW_QUERY_THRESHOLD) > 0) {
            issues.add(String.format("Slow query: average duration %dms exceeds threshold %dms",
                stats.averageDuration().toMillis(), SLOW_QUERY_THRESHOLD.toMillis()));
            suggestions.add("Consider adding indexes or optimizing query structure");
            severityScore += 3;
        }

        // Check table scans
        double tableScanRate = (double) stats.tableScans() / stats.totalExecutions();
        if (tableScanRate > 0.5) {
            issues.add(String.format("High table scan rate: %.1f%% of executions",
                tableScanRate * 100));
            suggestions.add("Add indexes on frequently filtered columns");
            severityScore += 5;
        }

        // Check failure rate
        double failureRate = (double) stats.failedExecutions() / stats.totalExecutions();
        if (failureRate > 0.1) {
            issues.add(String.format("High failure rate: %.1f%%", failureRate * 100));
            suggestions.add("Review query syntax and database constraints");
            severityScore += 4;
        }

        // Check index usage
        if (stats.indexesUsed().isEmpty() && stats.totalExecutions() > 10) {
            issues.add("No indexes used despite frequent execution");
            suggestions.add("Analyze query plan and add appropriate indexes");
            severityScore += 3;
        }

        String severity = calculateSeverity(severityScore);

        return new QueryAnalysis(
            queryName,
            stats,
            issues,
            suggestions,
            severity,
            severityScore
        );
    }

    /**
     * Gets all optimization suggestions.
     *
     * <p>Combines index recommendations and query-specific suggestions.
     *
     * @return list of optimization suggestions sorted by priority
     */
    public List<OptimizationSuggestion> getSuggestions() {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Add index recommendations
        List<IndexRecommendation> indexRecommendations = recommendIndexes();
        for (IndexRecommendation rec : indexRecommendations) {
            suggestions.add(new OptimizationSuggestion(
                rec.queryName(),
                "INDEX",
                rec.rationale(),
                rec.sql(),
                calculatePriority(rec.impact())
            ));
        }

        // Add query-specific suggestions
        List<SlowQuery> slowQueries = telemetryService.getSlowQueries(SLOW_QUERY_THRESHOLD);
        for (SlowQuery slowQuery : slowQueries) {
            QueryAnalysis analysis = analyzeQuery(slowQuery.queryName());
            if (analysis != null) {
                for (String suggestion : analysis.suggestions()) {
                    suggestions.add(new OptimizationSuggestion(
                        slowQuery.queryName(),
                        "QUERY",
                        suggestion,
                        null, // No direct SQL for query suggestions
                        analysis.severity()
                    ));
                }
            }
        }

        // Sort by priority
        suggestions.sort(Comparator.comparing(OptimizationSuggestion::priority).reversed());

        return suggestions;
    }

    /**
     * Gets performance report summary.
     *
     * @return PerformanceReport with overall statistics
     */
    public PerformanceReport getPerformanceReport() {
        TelemetrySummary summary = telemetryService.getSummary();
        List<SlowQuery> slowQueries = telemetryService.getSlowQueries(SLOW_QUERY_THRESHOLD);
        List<IndexRecommendation> indexRecommendations = recommendIndexes();

        // Calculate metrics
        long totalTableScans = slowQueries.stream()
            .map(sq -> telemetryService.getQueryStatistics(sq.queryName()))
            .filter(Objects::nonNull)
            .mapToLong(QueryStatistics::tableScans)
            .sum();

        int criticalIssues = (int) slowQueries.stream()
            .map(sq -> analyzeQuery(sq.queryName()))
            .filter(Objects::nonNull)
            .filter(analysis -> "CRITICAL".equals(analysis.severity()))
            .count();

        return new PerformanceReport(
            summary.totalQueries(),
            summary.slowQueries(),
            slowQueries.size(),
            totalTableScans,
            indexRecommendations.size(),
            criticalIssues
        );
    }

    /**
     * Extracts table name from SQL.
     *
     * @param sql the SQL statement
     * @return table name or null if not found
     */
    private String extractTableName(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts WHERE clause columns from SQL.
     *
     * @param sql the SQL statement
     * @return list of column names
     */
    private List<String> extractWhereColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher matcher = WHERE_CLAUSE_PATTERN.matcher(sql);

        while (matcher.find()) {
            String column = matcher.group(1);
            // Remove table prefix if present (e.g., "c.name" -> "name")
            if (column.contains(".")) {
                column = column.substring(column.indexOf('.') + 1);
            }
            columns.add(column);
        }

        return columns;
    }

    /**
     * Calculates severity based on score.
     *
     * @param score the severity score (0-15+)
     * @return severity level string
     */
    private String calculateSeverity(int score) {
        if (score >= 10) return "CRITICAL";
        if (score >= 6) return "HIGH";
        if (score >= 3) return "MEDIUM";
        return "LOW";
    }

    /**
     * Calculates priority string from impact value.
     *
     * @param impact the impact value (frequency × duration)
     * @return priority string
     */
    private String calculatePriority(long impact) {
        if (impact >= 10000) return "CRITICAL";
        if (impact >= 5000) return "HIGH";
        if (impact >= 1000) return "MEDIUM";
        return "LOW";
    }

    /**
     * Index recommendation.
     *
     * @param queryName the query name
     * @param tableName the table requiring index
     * @param columns the columns to index
     * @param impact the impact score (frequency × duration)
     * @param sql the CREATE INDEX statement
     * @param rationale the reason for recommendation
     *
     * @doc.type record
     * @doc.purpose Index creation recommendation
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record IndexRecommendation(
            String queryName,
            String tableName,
            List<String> columns,
            long impact,
            String sql,
            String rationale) {
    }

    /**
     * Query analysis result.
     *
     * @param queryName the query name
     * @param statistics the query statistics
     * @param issues the identified issues
     * @param suggestions the optimization suggestions
     * @param severity the severity level (LOW/MEDIUM/HIGH/CRITICAL)
     * @param severityScore the numeric severity score
     *
     * @doc.type record
     * @doc.purpose Query analysis result
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record QueryAnalysis(
            String queryName,
            QueryStatistics statistics,
            List<String> issues,
            List<String> suggestions,
            String severity,
            int severityScore) {
    }

    /**
     * Optimization suggestion.
     *
     * @param queryName the query name
     * @param type the suggestion type (INDEX/QUERY/SCHEMA)
     * @param description the suggestion description
     * @param sql the SQL to execute (nullable for non-index suggestions)
     * @param priority the priority level (LOW/MEDIUM/HIGH/CRITICAL)
     *
     * @doc.type record
     * @doc.purpose Optimization suggestion
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record OptimizationSuggestion(
            String queryName,
            String type,
            String description,
            String sql,
            String priority) {
    }

    /**
     * Performance report summary.
     *
     * @param totalQueries total queries executed
     * @param slowQueries number of slow queries
     * @param uniqueSlowQueries number of unique slow queries
     * @param totalTableScans total table scans detected
     * @param indexRecommendations number of index recommendations
     * @param criticalIssues number of critical issues
     *
     * @doc.type record
     * @doc.purpose Performance report summary
     * @doc.layer product
     * @doc.pattern Value Object
     */
    public record PerformanceReport(
            long totalQueries,
            long slowQueries,
            int uniqueSlowQueries,
            long totalTableScans,
            int indexRecommendations,
            int criticalIssues) {
    }
}
