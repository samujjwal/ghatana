package nlq

import (
	"context"
	"fmt"
)

// ServiceImpl implements the complete NLQ service
type ServiceImpl struct {
	parser    Parser
	generator Generator
	executor  Executor
	audit     *AuditLogger
}

// NewService creates a new NLQ service with all components
func NewService(clickhouseDSN string) (*ServiceImpl, error) {
	// Initialize executor (connects to ClickHouse)
	executor, err := NewClickHouseExecutor(clickhouseDSN)
	if err != nil {
		return nil, fmt.Errorf("failed to create executor: %w", err)
	}

	// Get metadata for parser and generator
	metadata, err := executor.GetMetadata(context.Background())
	if err != nil {
		return nil, fmt.Errorf("failed to load metadata: %w", err)
	}

	// Initialize components
	parser := NewGrammarParser(metadata)
	generator := NewSQLGenerator(metadata)
	audit := NewAuditLogger(executor.db)

	return &ServiceImpl{
		parser:    parser,
		generator: generator,
		executor:  executor,
		audit:     audit,
	}, nil
}

// ProcessQuery handles end-to-end natural language query processing
func (s *ServiceImpl) ProcessQuery(ctx context.Context, query string, userID string) (*QueryResult, error) {
	// Step 1: Parse natural language to intent
	intent, err := s.parser.Parse(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("parsing failed: %w", err)
	}

	// Step 2: Validate the parsed intent
	if err := s.parser.Validate(ctx, intent); err != nil {
		return nil, fmt.Errorf("validation failed: %w", err)
	}

	// Step 3: Generate SQL from intent
	sql, err := s.generator.GenerateSQL(ctx, intent)
	if err != nil {
		return nil, fmt.Errorf("SQL generation failed: %w", err)
	}

	// Step 4: Validate SQL security
	if err := s.generator.ValidateSQL(ctx, sql); err != nil {
		return nil, fmt.Errorf("SQL validation failed: %w", err)
	}

	// Step 5: Optimize SQL
	optimizedSQL, err := s.generator.OptimizeSQL(ctx, sql)
	if err != nil {
		// Continue with unoptimized SQL if optimization fails
		optimizedSQL = sql
	}

	// Step 6: Execute the query
	result, err := s.executor.Execute(ctx, optimizedSQL)
	if err != nil {
		// Create error result for auditing
		result = &QueryResult{
			Intent:       *intent,
			GeneratedSQL: *optimizedSQL,
			UserID:       userID,
			Success:      false,
			Error:        err.Error(),
		}
	} else {
		// Add metadata to successful result
		result.Intent = *intent
		result.UserID = userID
	}

	// Step 7: Log for audit
	if auditErr := s.audit.LogQuery(ctx, result); auditErr != nil {
		// Don't fail the query if audit logging fails
		fmt.Printf("Audit logging failed: %v\n", auditErr)
	}

	if err != nil {
		return result, err
	}

	return result, nil
}

// Parse implements Parser interface
func (s *ServiceImpl) Parse(ctx context.Context, query string) (*QueryIntent, error) {
	return s.parser.Parse(ctx, query)
}

// Validate implements Parser interface
func (s *ServiceImpl) Validate(ctx context.Context, intent *QueryIntent) error {
	return s.parser.Validate(ctx, intent)
}

// GetSuggestions implements Parser interface
func (s *ServiceImpl) GetSuggestions(ctx context.Context, partial string) ([]string, error) {
	return s.parser.GetSuggestions(ctx, partial)
}

// GenerateSQL implements Generator interface
func (s *ServiceImpl) GenerateSQL(ctx context.Context, intent *QueryIntent) (*SQLQuery, error) {
	return s.generator.GenerateSQL(ctx, intent)
}

// ValidateSQL implements Generator interface
func (s *ServiceImpl) ValidateSQL(ctx context.Context, sql *SQLQuery) error {
	return s.generator.ValidateSQL(ctx, sql)
}

// OptimizeSQL implements Generator interface
func (s *ServiceImpl) OptimizeSQL(ctx context.Context, sql *SQLQuery) (*SQLQuery, error) {
	return s.generator.OptimizeSQL(ctx, sql)
}

// Execute implements Executor interface
func (s *ServiceImpl) Execute(ctx context.Context, sql *SQLQuery) (*QueryResult, error) {
	return s.executor.Execute(ctx, sql)
}

// Preview implements Executor interface
func (s *ServiceImpl) Preview(ctx context.Context, sql *SQLQuery, limit int) (*QueryResult, error) {
	return s.executor.Preview(ctx, sql, limit)
}

// GetMetadata implements Executor interface
func (s *ServiceImpl) GetMetadata(ctx context.Context) (*QueryMetadata, error) {
	return s.executor.GetMetadata(ctx)
}

// GetQueryHistory returns recent queries for a user
func (s *ServiceImpl) GetQueryHistory(ctx context.Context, userID string, limit int) ([]*QueryResult, error) {
	return s.audit.GetQueryHistory(ctx, userID, limit)
}

// SaveQuery persists query results for audit and reuse
func (s *ServiceImpl) SaveQuery(ctx context.Context, result *QueryResult) error {
	return s.audit.LogQuery(ctx, result)
}

// CannedQueries provides pre-built example queries for testing and demo
var CannedQueries = []string{
	"show error rate for service-api in last 24 hours",
	"average response time grouped by service in last week",
	"cpu usage above 80% for production hosts",
	"memory utilization trending up in last 3 days",
	"count alerts by severity since yesterday",
	"database connections for mysql service",
	"disk usage over 90% grouped by host",
	"network latency between us-east and us-west",
	"deployment events for team-backend today",
	"incident count compared to last month",
	"show spike in error rate after deploy-v2.1",
	"failed requests grouped by status code",
	"average build time for ci/cd pipeline",
	"queue length for message broker",
	"cache hit rate below 70% in last hour",
	"api rate limit violations by client",
	"ssl certificate expiring in next 30 days",
	"database query latency above 1 second",
	"container restart count by service",
	"load balancer 5xx errors in last day",
}
