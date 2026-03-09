package nlq

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"fmt"
	"strings"
	"time"

	_ "github.com/ClickHouse/clickhouse-go/v2"
)

// ClickHouseExecutor safely executes validated SQL queries against ClickHouse
type ClickHouseExecutor struct {
	db           *sql.DB
	queryTimeout time.Duration
	maxRows      int
	metadata     *QueryMetadata
}

// ExecutionLimits defines resource limits for query execution
type ExecutionLimits struct {
	MaxRows          int           // Maximum rows returned
	MaxMemoryUsage   int64         // Maximum memory usage in bytes
	MaxExecutionTime time.Duration // Maximum execution time
	MaxConcurrency   int           // Maximum concurrent queries per user
}

// NewClickHouseExecutor creates a new ClickHouse query executor
func NewClickHouseExecutor(dsn string) (*ClickHouseExecutor, error) {
	db, err := sql.Open("clickhouse", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to ClickHouse: %w", err)
	}

	// Test connection
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping ClickHouse: %w", err)
	}

	return &ClickHouseExecutor{
		db:           db,
		queryTimeout: 30 * time.Second,
		maxRows:      1000,
		metadata:     loadMetadata(db),
	}, nil
}

// Execute runs a validated SQL query with safety limits
func (e *ClickHouseExecutor) Execute(ctx context.Context, sql *SQLQuery) (*QueryResult, error) {
	if !sql.Safety.Approved {
		return nil, fmt.Errorf("query not approved for execution")
	}

	// Generate unique query ID
	queryID := generateQueryID()

	// Create timeout context
	queryCtx, cancel := context.WithTimeout(ctx, e.queryTimeout)
	defer cancel()

	// Record start time
	startTime := time.Now()

	// Execute the query
	rows, err := e.db.QueryContext(queryCtx, sql.SQL)
	if err != nil {
		return &QueryResult{
			QueryID:   queryID,
			Success:   false,
			Error:     err.Error(),
			Timestamp: startTime,
		}, err
	}
	defer rows.Close()

	// Get column information
	columns, err := rows.Columns()
	if err != nil {
		return nil, fmt.Errorf("failed to get columns: %w", err)
	}

	// Read all rows
	var data [][]any
	rowCount := 0

	for rows.Next() && rowCount < e.maxRows {
		// Create slice for row values
		values := make([]any, len(columns))
		valuePtrs := make([]any, len(columns))
		for i := range values {
			valuePtrs[i] = &values[i]
		}

		// Scan row values
		if err := rows.Scan(valuePtrs...); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		data = append(data, values)
		rowCount++
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error reading rows: %w", err)
	}

	executionTime := time.Since(startTime)

	// Generate chart configuration
	chartConfig := e.generateChartConfig(columns, data)

	result := &QueryResult{
		QueryID:       queryID,
		GeneratedSQL:  *sql,
		Data:          data,
		Columns:       columns,
		RowCount:      rowCount,
		ExecutionTime: executionTime,
		ChartConfig:   chartConfig,
		Success:       true,
		Timestamp:     startTime,
	}

	return result, nil
}

// Preview runs a limited version of the query for validation
func (e *ClickHouseExecutor) Preview(ctx context.Context, sql *SQLQuery, limit int) (*QueryResult, error) {
	// Create preview SQL with LIMIT
	previewSQL := *sql
	if !strings.Contains(strings.ToUpper(previewSQL.SQL), "LIMIT") {
		previewSQL.SQL += fmt.Sprintf(" LIMIT %d", limit)
	}

	// Temporarily reduce max rows for preview
	originalMaxRows := e.maxRows
	e.maxRows = limit
	defer func() {
		e.maxRows = originalMaxRows
	}()

	return e.Execute(ctx, &previewSQL)
}

// GetMetadata returns available tables, columns, and functions
func (e *ClickHouseExecutor) GetMetadata(ctx context.Context) (*QueryMetadata, error) {
	if e.metadata != nil {
		return e.metadata, nil
	}

	return loadMetadata(e.db), nil
}

// generateChartConfig creates visualization configuration based on query results
func (e *ClickHouseExecutor) generateChartConfig(columns []string, data [][]any) ChartConfig {
	config := ChartConfig{
		Type:        "line", // Default to line chart
		Title:       "Query Results",
		Description: "Natural language query visualization",
	}

	if len(columns) == 0 || len(data) == 0 {
		return config
	}

	// Detect time column
	var timeColumn string
	for _, col := range columns {
		if strings.Contains(strings.ToLower(col), "time") ||
			strings.Contains(strings.ToLower(col), "timestamp") {
			timeColumn = col
			break
		}
	}

	if timeColumn != "" {
		config.XAxis = timeColumn
		config.Type = "line"

		// Add non-time columns as Y-axis
		for _, col := range columns {
			if col != timeColumn {
				config.YAxis = append(config.YAxis, col)
			}
		}
	} else {
		// No time column - use bar chart
		config.Type = "bar"
		if len(columns) >= 2 {
			config.XAxis = columns[0]
			config.YAxis = columns[1:]
		}
	}

	return config
}

// loadMetadata loads database schema information
func loadMetadata(db *sql.DB) *QueryMetadata {
	metadata := &QueryMetadata{
		Tables:    []TableInfo{},
		Functions: []FunctionInfo{},
		Examples:  []QueryExample{},
	}

	// Load table information
	tables := []TableInfo{
		{
			Name:        "events",
			Description: "Application events and logs",
			Columns: []ColumnInfo{
				{Name: "timestamp", Type: "DateTime", Description: "Event timestamp"},
				{Name: "service", Type: "String", Description: "Service name"},
				{Name: "host", Type: "String", Description: "Host name"},
				{Name: "level", Type: "String", Description: "Log level"},
				{Name: "message", Type: "String", Description: "Event message"},
				{Name: "error_rate", Type: "Float64", Description: "Error rate percentage"},
			},
			Tags: []string{"logs", "events", "errors"},
		},
		{
			Name:        "metrics",
			Description: "System and application metrics",
			Columns: []ColumnInfo{
				{Name: "timestamp", Type: "DateTime", Description: "Metric timestamp"},
				{Name: "service", Type: "String", Description: "Service name"},
				{Name: "host", Type: "String", Description: "Host name"},
				{Name: "cpu_usage", Type: "Float64", Description: "CPU utilization percentage"},
				{Name: "memory_usage", Type: "Float64", Description: "Memory utilization percentage"},
				{Name: "response_time", Type: "Float64", Description: "Response time in milliseconds"},
			},
			Tags: []string{"metrics", "performance", "infrastructure"},
		},
		{
			Name:        "deployments",
			Description: "Deployment events and releases",
			Columns: []ColumnInfo{
				{Name: "timestamp", Type: "DateTime", Description: "Deployment timestamp"},
				{Name: "service", Type: "String", Description: "Service deployed"},
				{Name: "version", Type: "String", Description: "Version deployed"},
				{Name: "team", Type: "String", Description: "Team responsible"},
				{Name: "environment", Type: "String", Description: "Deployment environment"},
			},
			Tags: []string{"deployments", "releases", "ci/cd"},
		},
	}
	metadata.Tables = tables

	// Load function information
	functions := []FunctionInfo{
		{Name: "avg", Category: "aggregation", Description: "Calculate average value", Safe: true},
		{Name: "sum", Category: "aggregation", Description: "Calculate sum of values", Safe: true},
		{Name: "count", Category: "aggregation", Description: "Count number of rows", Safe: true},
		{Name: "max", Category: "aggregation", Description: "Find maximum value", Safe: true},
		{Name: "min", Category: "aggregation", Description: "Find minimum value", Safe: true},
		{Name: "toStartOfInterval", Category: "date", Description: "Round timestamp to interval", Safe: true},
		{Name: "formatDateTime", Category: "date", Description: "Format timestamp", Safe: true},
	}
	metadata.Functions = functions

	// Load example queries
	examples := []QueryExample{
		{
			NaturalLanguage: "show error rate for service-api in last 24 hours",
			Intent: QueryIntent{
				Metric:    "error_rate",
				Filters:   map[string]string{"service": "service-api"},
				TimeRange: TimeRange{Label: "last 24h"},
			},
			SQL:         "SELECT timestamp, error_rate FROM events WHERE service = 'service-api' AND timestamp >= now() - INTERVAL 24 HOUR",
			Description: "Monitor error rates for a specific service",
			Category:    "error_monitoring",
		},
		{
			NaturalLanguage: "average response time grouped by service in last week",
			Intent: QueryIntent{
				Metric:      "response_time",
				Aggregation: "average",
				GroupBy:     []string{"service"},
				TimeRange:   TimeRange{Label: "last week"},
			},
			SQL:         "SELECT service, avg(response_time) FROM metrics WHERE timestamp >= now() - INTERVAL 7 DAY GROUP BY service",
			Description: "Compare response times across services",
			Category:    "performance_analysis",
		},
	}
	metadata.Examples = examples

	return metadata
}

// generateQueryID creates a unique identifier for query tracking
func generateQueryID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

// AuditLogger handles query audit logging
type AuditLogger struct {
	db *sql.DB
}

// NewAuditLogger creates a new audit logger
func NewAuditLogger(db *sql.DB) *AuditLogger {
	return &AuditLogger{db: db}
}

// LogQuery records a query execution for audit purposes
func (a *AuditLogger) LogQuery(ctx context.Context, result *QueryResult) error {
	query := `
		INSERT INTO nlq_audit (
			query_id, user_id, original_text, generated_sql, 
			execution_time_ms, row_count, success, error_message, timestamp
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	`

	var errorMsg *string
	if result.Error != "" {
		errorMsg = &result.Error
	}

	_, err := a.db.ExecContext(ctx, query,
		result.QueryID,
		result.UserID,
		result.Intent.OriginalText,
		result.GeneratedSQL.SQL,
		result.ExecutionTime.Milliseconds(),
		result.RowCount,
		result.Success,
		errorMsg,
		result.Timestamp,
	)

	return err
}

// GetQueryHistory retrieves audit history for a user
func (a *AuditLogger) GetQueryHistory(ctx context.Context, userID string, limit int) ([]*QueryResult, error) {
	query := `
		SELECT query_id, original_text, generated_sql, execution_time_ms, 
			   row_count, success, error_message, timestamp
		FROM nlq_audit 
		WHERE user_id = ? 
		ORDER BY timestamp DESC 
		LIMIT ?
	`

	rows, err := a.db.QueryContext(ctx, query, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []*QueryResult
	for rows.Next() {
		var result QueryResult
		var errorMsg sql.NullString
		var executionTimeMs int64

		err := rows.Scan(
			&result.QueryID,
			&result.Intent.OriginalText,
			&result.GeneratedSQL.SQL,
			&executionTimeMs,
			&result.RowCount,
			&result.Success,
			&errorMsg,
			&result.Timestamp,
		)
		if err != nil {
			continue
		}

		result.ExecutionTime = time.Duration(executionTimeMs) * time.Millisecond
		if errorMsg.Valid {
			result.Error = errorMsg.String
		}

		results = append(results, &result)
	}

	return results, nil
}
