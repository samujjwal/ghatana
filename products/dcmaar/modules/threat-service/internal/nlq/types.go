// Package nlq provides Natural Language Query capabilities for DCMAAR
//
// This package implements Capability 3: Natural-Language Queries over Metrics/Events
// allowing operators to type questions like "show spike after 2.3 deploy for Team-A"
// and get generated ClickHouse SQL + results + rationale.
package nlq

import (
	"context"
	"time"
)

// QueryIntent represents the parsed intent from a natural language query
type QueryIntent struct {
	// Core query components
	Metric      string            `json:"metric"`      // What to measure (e.g., "error_rate", "response_time")
	Aggregation string            `json:"aggregation"` // How to aggregate (e.g., "avg", "sum", "count")
	TimeRange   TimeRange         `json:"time_range"`  // When to look
	Filters     map[string]string `json:"filters"`     // What to filter by
	GroupBy     []string          `json:"group_by"`    // How to group results

	// Analysis specific
	EventType  string     `json:"event_type"` // Event type to analyze
	Threshold  *float64   `json:"threshold"`  // For spike/anomaly detection
	Comparison *TimeRange `json:"comparison"` // Compare against period

	// Metadata
	Confidence   float64 `json:"confidence"`    // Parser confidence 0-1
	OriginalText string  `json:"original_text"` // Original query text
	ParsedBy     string  `json:"parsed_by"`     // Parser method used
}

// TimeRange represents a time period for queries
type TimeRange struct {
	Start time.Time `json:"start"`
	End   time.Time `json:"end"`
	Label string    `json:"label"` // Human readable (e.g., "last 24h")
}

// SQLQuery represents a generated SQL query with metadata
type SQLQuery struct {
	SQL        string         `json:"sql"`        // Generated ClickHouse SQL
	Parameters map[string]any `json:"parameters"` // Bound parameters
	Tables     []string       `json:"tables"`     // Tables accessed
	Columns    []string       `json:"columns"`    // Columns selected
	Functions  []string       `json:"functions"`  // Functions used
	Rationale  string         `json:"rationale"`  // Explanation of query logic
	Safety     SafetyCheck    `json:"safety"`     // Security validation results
}

// SafetyCheck represents security validation of a generated query
type SafetyCheck struct {
	Approved         bool     `json:"approved"`          // Whether query is safe to execute
	AllowedTables    []string `json:"allowed_tables"`    // Whitelisted tables
	AllowedColumns   []string `json:"allowed_columns"`   // Whitelisted columns
	AllowedFunctions []string `json:"allowed_functions"` // Whitelisted functions
	Violations       []string `json:"violations"`        // Security violations found
	RiskLevel        string   `json:"risk_level"`        // LOW, MEDIUM, HIGH
}

// QueryResult represents the result of executing a natural language query
type QueryResult struct {
	// Query metadata
	QueryID      string      `json:"query_id"`      // Unique identifier
	Intent       QueryIntent `json:"intent"`        // Parsed intent
	GeneratedSQL SQLQuery    `json:"generated_sql"` // Generated SQL query

	// Execution results
	Data          [][]any       `json:"data"`           // Query result rows
	Columns       []string      `json:"columns"`        // Column names
	RowCount      int           `json:"row_count"`      // Number of rows returned
	ExecutionTime time.Duration `json:"execution_time"` // Query execution time

	// Chart data
	ChartConfig ChartConfig `json:"chart_config"` // Visualization configuration

	// Audit trail
	UserID    string    `json:"user_id"`         // User who executed query
	Timestamp time.Time `json:"timestamp"`       // Execution time
	Success   bool      `json:"success"`         // Whether execution succeeded
	Error     string    `json:"error,omitempty"` // Error message if failed
}

// ChartConfig represents visualization configuration for query results
type ChartConfig struct {
	Type        string            `json:"type"`        // line, bar, pie, heatmap
	XAxis       string            `json:"x_axis"`      // X-axis column
	YAxis       []string          `json:"y_axis"`      // Y-axis columns
	Series      []string          `json:"series"`      // Data series
	Annotations []ChartAnnotation `json:"annotations"` // Chart annotations
	Title       string            `json:"title"`       // Chart title
	Description string            `json:"description"` // Chart description
}

// ChartAnnotation represents annotations on charts (e.g., deployment markers)
type ChartAnnotation struct {
	Type      string    `json:"type"`      // line, point, region
	Timestamp time.Time `json:"timestamp"` // When annotation occurs
	Label     string    `json:"label"`     // Annotation text
	Color     string    `json:"color"`     // Display color
	Source    string    `json:"source"`    // Source of annotation (deploy, incident)
}

// Parser interface for converting natural language to structured queries
type Parser interface {
	// Parse converts natural language query to structured intent
	Parse(ctx context.Context, query string) (*QueryIntent, error)

	// Validate checks if a query intent is valid and safe
	Validate(ctx context.Context, intent *QueryIntent) error

	// GetSuggestions provides query suggestions based on partial input
	GetSuggestions(ctx context.Context, partial string) ([]string, error)
}

// Generator interface for converting structured queries to SQL
type Generator interface {
	// GenerateSQL converts query intent to executable SQL
	GenerateSQL(ctx context.Context, intent *QueryIntent) (*SQLQuery, error)

	// ValidateSQL performs security checks on generated SQL
	ValidateSQL(ctx context.Context, sql *SQLQuery) error

	// OptimizeSQL applies query optimization hints
	OptimizeSQL(ctx context.Context, sql *SQLQuery) (*SQLQuery, error)
}

// Executor interface for running SQL queries safely
type Executor interface {
	// Execute runs a validated SQL query with limits
	Execute(ctx context.Context, sql *SQLQuery) (*QueryResult, error)

	// Preview runs a limited version of the query for validation
	Preview(ctx context.Context, sql *SQLQuery, limit int) (*QueryResult, error)

	// GetMetadata returns available tables, columns, and functions
	GetMetadata(ctx context.Context) (*QueryMetadata, error)
}

// QueryMetadata represents available database schema information
type QueryMetadata struct {
	Tables    []TableInfo    `json:"tables"`    // Available tables
	Functions []FunctionInfo `json:"functions"` // Available functions
	Examples  []QueryExample `json:"examples"`  // Example queries
}

// TableInfo represents metadata about a database table
type TableInfo struct {
	Name        string       `json:"name"`        // Table name
	Description string       `json:"description"` // Human-readable description
	Columns     []ColumnInfo `json:"columns"`     // Table columns
	SampleRate  float64      `json:"sample_rate"` // Sampling rate if applicable
	TTL         string       `json:"ttl"`         // Data retention period
	Tags        []string     `json:"tags"`        // Semantic tags
}

// ColumnInfo represents metadata about a database column
type ColumnInfo struct {
	Name        string   `json:"name"`        // Column name
	Type        string   `json:"type"`        // Data type
	Description string   `json:"description"` // Human-readable description
	Nullable    bool     `json:"nullable"`    // Whether column can be null
	Examples    []string `json:"examples"`    // Example values
	Tags        []string `json:"tags"`        // Semantic tags
}

// FunctionInfo represents metadata about available SQL functions
type FunctionInfo struct {
	Name        string   `json:"name"`        // Function name
	Category    string   `json:"category"`    // Function category (aggregation, date, string)
	Description string   `json:"description"` // What the function does
	Signature   string   `json:"signature"`   // Function signature
	Examples    []string `json:"examples"`    // Usage examples
	Safe        bool     `json:"safe"`        // Whether function is safe for NLQ
}

// QueryExample represents example queries for learning
type QueryExample struct {
	NaturalLanguage string      `json:"natural_language"` // Natural language query
	Intent          QueryIntent `json:"intent"`           // Parsed intent
	SQL             string      `json:"sql"`              // Generated SQL
	Description     string      `json:"description"`      // What this query does
	Category        string      `json:"category"`         // Query category
}

// Service combines all NLQ functionality
type Service interface {
	Parser
	Generator
	Executor

	// ProcessQuery handles end-to-end natural language query processing
	ProcessQuery(ctx context.Context, query string, userID string) (*QueryResult, error)

	// GetQueryHistory returns recent queries for a user
	GetQueryHistory(ctx context.Context, userID string, limit int) ([]*QueryResult, error)

	// SaveQuery persists query results for audit and reuse
	SaveQuery(ctx context.Context, result *QueryResult) error
}
