package nlq

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// SQLGenerator converts query intents to ClickHouse SQL
type SQLGenerator struct {
	safetyRules    *SafetyRules
	metadata       *QueryMetadata
	templateEngine *TemplateEngine
}

// SafetyRules defines security constraints for SQL generation
type SafetyRules struct {
	AllowedTables     []string          // Whitelisted table names
	AllowedColumns    []string          // Whitelisted column names
	AllowedFunctions  []string          // Whitelisted SQL functions
	ForbiddenPatterns []string          // Forbidden SQL patterns
	MaxTimeRange      time.Duration     // Maximum allowed time range
	TableAliases      map[string]string // Table name mappings
}

// TemplateEngine generates SQL from templates
type TemplateEngine struct {
	templates map[string]string
}

// NewSQLGenerator creates a new SQL generator with security rules
func NewSQLGenerator(metadata *QueryMetadata) *SQLGenerator {
	return &SQLGenerator{
		safetyRules:    initializeSafetyRules(),
		metadata:       metadata,
		templateEngine: initializeTemplates(),
	}
}

// GenerateSQL converts query intent to executable ClickHouse SQL
func (g *SQLGenerator) GenerateSQL(ctx context.Context, intent *QueryIntent) (*SQLQuery, error) {
	// Determine appropriate table based on metric
	table, err := g.selectTable(intent)
	if err != nil {
		return nil, fmt.Errorf("failed to select table: %w", err)
	}

	// Build SQL components
	selectClause := g.buildSelectClause(intent)
	fromClause := g.buildFromClause(table)
	whereClause := g.buildWhereClause(intent)
	groupByClause := g.buildGroupByClause(intent)
	orderByClause := g.buildOrderByClause(intent)
	limitClause := "LIMIT 1000" // Default safety limit

	// Combine into complete SQL
	sql := fmt.Sprintf("SELECT %s FROM %s WHERE %s",
		selectClause, fromClause, whereClause)

	if groupByClause != "" {
		sql += " " + groupByClause
	}
	if orderByClause != "" {
		sql += " " + orderByClause
	}
	sql += " " + limitClause

	// Extract metadata about the query
	tables := []string{table}
	columns := g.extractColumns(selectClause, whereClause, groupByClause)
	functions := g.extractFunctions(sql)

	query := &SQLQuery{
		SQL:        sql,
		Parameters: make(map[string]any),
		Tables:     tables,
		Columns:    columns,
		Functions:  functions,
		Rationale:  g.generateRationale(intent, sql),
	}

	return query, nil
}

// ValidateSQL performs security checks on generated SQL
func (g *SQLGenerator) ValidateSQL(ctx context.Context, sql *SQLQuery) error {
	safety := SafetyCheck{
		AllowedTables:    g.safetyRules.AllowedTables,
		AllowedColumns:   g.safetyRules.AllowedColumns,
		AllowedFunctions: g.safetyRules.AllowedFunctions,
		Violations:       []string{},
		RiskLevel:        "LOW",
	}

	// Check tables
	for _, table := range sql.Tables {
		if !g.isAllowedTable(table) {
			safety.Violations = append(safety.Violations,
				fmt.Sprintf("forbidden table: %s", table))
			safety.RiskLevel = "HIGH"
		}
	}

	// Check functions
	for _, function := range sql.Functions {
		if !g.isAllowedFunction(function) {
			safety.Violations = append(safety.Violations,
				fmt.Sprintf("forbidden function: %s", function))
			safety.RiskLevel = "MEDIUM"
		}
	}

	// Check for forbidden patterns
	lowerSQL := strings.ToLower(sql.SQL)
	for _, pattern := range g.safetyRules.ForbiddenPatterns {
		if strings.Contains(lowerSQL, pattern) {
			safety.Violations = append(safety.Violations,
				fmt.Sprintf("forbidden pattern: %s", pattern))
			safety.RiskLevel = "HIGH"
		}
	}

	// Approve if no high-risk violations
	safety.Approved = safety.RiskLevel != "HIGH"
	sql.Safety = safety

	if !safety.Approved {
		return fmt.Errorf("SQL failed security validation: %v", safety.Violations)
	}

	return nil
}

// OptimizeSQL applies query optimization hints
func (g *SQLGenerator) OptimizeSQL(ctx context.Context, sql *SQLQuery) (*SQLQuery, error) {
	optimized := *sql // Copy the query

	// Add PREWHERE optimization for time-based filters
	if strings.Contains(sql.SQL, "timestamp >=") {
		optimized.SQL = strings.Replace(optimized.SQL, "WHERE", "PREWHERE", 1)
		optimized.Rationale += " Applied PREWHERE optimization for time filtering."
	}

	// Add sampling for large time ranges
	if strings.Contains(sql.SQL, "FROM events") && !strings.Contains(sql.SQL, "SAMPLE") {
		optimized.SQL = strings.Replace(optimized.SQL, "FROM events", "FROM events SAMPLE 0.1", 1)
		optimized.Rationale += " Applied 10% sampling for performance."
	}

	return &optimized, nil
}

// selectTable chooses the appropriate table based on metric and intent
func (g *SQLGenerator) selectTable(intent *QueryIntent) (string, error) {
	metric := intent.Metric

	// Map metrics to tables
	tableMap := map[string]string{
		"error_rate":      "events",
		"response_time":   "metrics",
		"cpu_usage":       "metrics",
		"memory_usage":    "metrics",
		"disk_usage":      "metrics",
		"network_latency": "metrics",
		"alert_count":     "alerts",
		"deployment":      "deployments",
		"incident":        "incidents",
	}

	if table, exists := tableMap[metric]; exists {
		return table, nil
	}

	// Default to events table
	return "events", nil
}

// buildSelectClause constructs the SELECT portion of the query
func (g *SQLGenerator) buildSelectClause(intent *QueryIntent) string {
	var selects []string

	// Time column for grouping
	if len(intent.GroupBy) > 0 || intent.Aggregation != "" {
		selects = append(selects, "toStartOfInterval(timestamp, INTERVAL 5 MINUTE) as time_bucket")
	} else {
		selects = append(selects, "timestamp")
	}

	// Metric with aggregation
	if intent.Aggregation != "" {
		switch intent.Aggregation {
		case "average", "avg":
			selects = append(selects, fmt.Sprintf("avg(%s) as %s", intent.Metric, intent.Metric))
		case "sum", "total":
			selects = append(selects, fmt.Sprintf("sum(%s) as %s", intent.Metric, intent.Metric))
		case "count":
			selects = append(selects, fmt.Sprintf("count(*) as %s", intent.Metric))
		case "max", "maximum":
			selects = append(selects, fmt.Sprintf("max(%s) as %s", intent.Metric, intent.Metric))
		case "min", "minimum":
			selects = append(selects, fmt.Sprintf("min(%s) as %s", intent.Metric, intent.Metric))
		default:
			selects = append(selects, fmt.Sprintf("avg(%s) as %s", intent.Metric, intent.Metric))
		}
	} else {
		selects = append(selects, intent.Metric)
	}

	// Group by columns
	for _, groupCol := range intent.GroupBy {
		if !contains(selects, groupCol) {
			selects = append(selects, groupCol)
		}
	}

	// Filter columns that are selected
	for key := range intent.Filters {
		if !contains(selects, key) {
			selects = append(selects, key)
		}
	}

	return strings.Join(selects, ", ")
}

// buildFromClause constructs the FROM portion of the query
func (g *SQLGenerator) buildFromClause(table string) string {
	// Apply table alias if configured
	if alias, exists := g.safetyRules.TableAliases[table]; exists {
		return alias
	}
	return table
}

// buildWhereClause constructs the WHERE portion of the query
func (g *SQLGenerator) buildWhereClause(intent *QueryIntent) string {
	var conditions []string

	// Time range filter (always required)
	conditions = append(conditions,
		fmt.Sprintf("timestamp >= '%s'", intent.TimeRange.Start.Format("2006-01-02 15:04:05")))
	conditions = append(conditions,
		fmt.Sprintf("timestamp <= '%s'", intent.TimeRange.End.Format("2006-01-02 15:04:05")))

	// Apply filters
	for key, value := range intent.Filters {
		conditions = append(conditions, fmt.Sprintf("%s = '%s'", key, value))
	}

	// Threshold filter
	if intent.Threshold != nil {
		conditions = append(conditions,
			fmt.Sprintf("%s >= %f", intent.Metric, *intent.Threshold))
	}

	return strings.Join(conditions, " AND ")
}

// buildGroupByClause constructs the GROUP BY portion of the query
func (g *SQLGenerator) buildGroupByClause(intent *QueryIntent) string {
	if len(intent.GroupBy) == 0 && intent.Aggregation == "" {
		return ""
	}

	var groups []string

	// Always group by time if we have aggregation
	if intent.Aggregation != "" {
		groups = append(groups, "time_bucket")
	}

	// Add explicit group by columns
	groups = append(groups, intent.GroupBy...)

	if len(groups) == 0 {
		return ""
	}

	return "GROUP BY " + strings.Join(groups, ", ")
}

// buildOrderByClause constructs the ORDER BY portion of the query
func (g *SQLGenerator) buildOrderByClause(intent *QueryIntent) string {
	if intent.Aggregation != "" || len(intent.GroupBy) > 0 {
		return "ORDER BY time_bucket ASC"
	}
	return "ORDER BY timestamp DESC"
}

// generateRationale explains the generated SQL query
func (g *SQLGenerator) generateRationale(intent *QueryIntent, sql string) string {
	var parts []string

	parts = append(parts, fmt.Sprintf("Query analyzes %s metric", intent.Metric))

	if intent.Aggregation != "" {
		parts = append(parts, fmt.Sprintf("using %s aggregation", intent.Aggregation))
	}

	parts = append(parts, fmt.Sprintf("over time range %s", intent.TimeRange.Label))

	if len(intent.Filters) > 0 {
		var filters []string
		for key, value := range intent.Filters {
			filters = append(filters, fmt.Sprintf("%s=%s", key, value))
		}
		parts = append(parts, fmt.Sprintf("filtered by %s", strings.Join(filters, ", ")))
	}

	if len(intent.GroupBy) > 0 {
		parts = append(parts, fmt.Sprintf("grouped by %s", strings.Join(intent.GroupBy, ", ")))
	}

	if intent.Threshold != nil {
		parts = append(parts, fmt.Sprintf("with threshold %f", *intent.Threshold))
	}

	return strings.Join(parts, " ") + "."
}

// extractColumns extracts column names from SQL clauses
func (g *SQLGenerator) extractColumns(clauses ...string) []string {
	var columns []string
	fullText := strings.Join(clauses, " ")

	// Simple column extraction - could be enhanced with proper SQL parsing
	words := strings.Fields(fullText)
	for _, word := range words {
		word = strings.Trim(word, "(),")
		if g.isColumnName(word) {
			columns = append(columns, word)
		}
	}

	return removeDuplicates(columns)
}

// extractFunctions extracts function names from SQL
func (g *SQLGenerator) extractFunctions(sql string) []string {
	var functions []string

	// Common SQL functions
	functionPatterns := []string{"avg(", "sum(", "count(", "max(", "min(", "toStartOfInterval("}

	lowerSQL := strings.ToLower(sql)
	for _, pattern := range functionPatterns {
		if strings.Contains(lowerSQL, pattern) {
			funcName := strings.TrimSuffix(pattern, "(")
			functions = append(functions, funcName)
		}
	}

	return removeDuplicates(functions)
}

// isAllowedTable checks if table is in the whitelist
func (g *SQLGenerator) isAllowedTable(table string) bool {
	return contains(g.safetyRules.AllowedTables, table)
}

// isAllowedFunction checks if function is in the whitelist
func (g *SQLGenerator) isAllowedFunction(function string) bool {
	return contains(g.safetyRules.AllowedFunctions, function)
}

// isColumnName checks if a word looks like a column name
func (g *SQLGenerator) isColumnName(word string) bool {
	// Basic heuristic - could be enhanced
	return len(word) > 0 && !strings.ContainsAny(word, "0123456789'\"") &&
		word != "and" && word != "or" && word != "where" && word != "select"
}

// Helper functions
func contains(slice []string, item string) bool {
	for _, s := range slice {
		if s == item {
			return true
		}
	}
	return false
}

func removeDuplicates(slice []string) []string {
	keys := make(map[string]bool)
	var result []string

	for _, item := range slice {
		if !keys[item] {
			keys[item] = true
			result = append(result, item)
		}
	}

	return result
}

// initializeSafetyRules sets up security constraints
func initializeSafetyRules() *SafetyRules {
	return &SafetyRules{
		AllowedTables: []string{
			"events", "metrics", "alerts", "deployments", "incidents",
			"services", "hosts", "teams", "users",
		},
		AllowedColumns: []string{
			"timestamp", "service", "host", "team", "region", "environment",
			"error_rate", "response_time", "cpu_usage", "memory_usage", "disk_usage",
			"network_latency", "alert_count", "severity", "status", "version",
		},
		AllowedFunctions: []string{
			"avg", "sum", "count", "max", "min", "median", "quantile",
			"toStartOfInterval", "formatDateTime", "toString", "toFloat64",
		},
		ForbiddenPatterns: []string{
			"drop", "delete", "truncate", "alter", "create", "insert", "update",
			"union", "exec", "system", "file", "url", "remote",
		},
		MaxTimeRange: 30 * 24 * time.Hour, // 30 days
		TableAliases: map[string]string{
			"events":  "dcmaar.events",
			"metrics": "dcmaar.metrics",
			"alerts":  "dcmaar.alerts",
		},
	}
}

// initializeTemplates sets up SQL templates
func initializeTemplates() *TemplateEngine {
	return &TemplateEngine{
		templates: map[string]string{
			"basic_metric":      "SELECT timestamp, {{.metric}} FROM {{.table}} WHERE timestamp BETWEEN {{.start}} AND {{.end}}",
			"aggregated_metric": "SELECT toStartOfInterval(timestamp, INTERVAL 5 MINUTE) as time, {{.aggregation}}({{.metric}}) as value FROM {{.table}} WHERE timestamp BETWEEN {{.start}} AND {{.end}} GROUP BY time ORDER BY time",
			"comparison_query":  "SELECT * FROM ({{.current}}) UNION ALL SELECT * FROM ({{.comparison}})",
		},
	}
}
