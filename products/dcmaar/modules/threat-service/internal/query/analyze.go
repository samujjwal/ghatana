package query

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
)

// ExplainPlan represents the execution plan for a query
type ExplainPlan struct {
	Query      string                 `json:"query"`
	Plan       map[string]interface{} `json:"plan"`
	Statistics QueryStats             `json:"statistics"`
}

// QueryStats contains performance statistics for a query
type QueryStats struct {
	ReadRows      int64   `json:"read_rows"`
	ReadBytes     int64   `json:"read_bytes"`
	WrittenRows   int64   `json:"written_rows"`
	WrittenBytes  int64   `json:"written_bytes"`
	TotalRows     int64   `json:"total_rows"`
	ElapsedSec    float64 `json:"elapsed_seconds"`
	MemoryUsage   int64   `json:"memory_usage_bytes"`
	QueryCacheHit bool    `json:"query_cache_hit"`
}

// AnalyzeQuery explains the query execution plan
func (s *Server) AnalyzeQuery(ctx context.Context, query string, args ...interface{}) (*ExplainPlan, error) {
	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "query.analyze"))

	// Add EXPLAIN to get the query plan
	explainQuery := fmt.Sprintf("EXPLAIN json = 1, description = 1, header = 1, actions = 1 %s", query)

	var planJSON string
	err := s.db.QueryRowContext(ctx, explainQuery, args...).Scan(&planJSON)
	if err != nil {
		return nil, fmt.Errorf("failed to get query plan: %w", err)
	}

	var plan map[string]interface{}
	if err := json.Unmarshal([]byte(planJSON), &plan); err != nil {
		return nil, fmt.Errorf("failed to parse query plan: %w", err)
	}

	// Get query statistics
	stats, err := s.getQueryStats(ctx, query, args...)
	if err != nil {
		logger.Warn("failed to get query statistics", zap.Error(err))
	}

	return &ExplainPlan{
		Query:      query,
		Plan:       plan,
		Statistics: *stats,
	}, nil
}

// getQueryStats retrieves performance statistics for a query
func (s *Server) getQueryStats(ctx context.Context, query string, args ...interface{}) (*QueryStats, error) {
	// Use a subquery to get stats without executing the full query
	statsQuery := `
	SELECT
	    read_rows,
	    read_bytes,
	    written_rows,
	    written_bytes,
	    result_rows AS total_rows,
	    elapsed AS elapsed_seconds,
	    memory_usage AS memory_usage_bytes,
	    query_cache_usage = 1 AS query_cache_hit
	FROM (
	    SELECT *
	    FROM (
	        EXPLAIN ESTIMATE
	        SELECT count()
	        FROM (%s) AS subquery
	    )
	    SETTINGS log_queries = 1, log_queries_min_query_duration_ms = 0
	)
	ARRAY JOIN arrayFilter(x -> x.1 IN (
	    'read_rows', 'read_bytes', 'written_rows', 'written_bytes',
	    'result_rows', 'elapsed', 'memory_usage', 'query_cache_usage'
	), mapKeys(logs), mapValues(logs)) AS (metric, value)
	`

	stats := &QueryStats{}
	row := s.db.QueryRowContext(ctx, fmt.Sprintf(statsQuery, query), args...)
	err := row.Scan(
		&stats.ReadRows,
		&stats.ReadBytes,
		&stats.WrittenRows,
		&stats.WrittenBytes,
		&stats.TotalRows,
		&stats.ElapsedSec,
		&stats.MemoryUsage,
		&stats.QueryCacheHit,
	)

	if err != nil {
		return nil, fmt.Errorf("failed to get query stats: %w", err)
	}

	return stats, nil
}

// logQueryPlan logs the query plan and stats to the provided logger and trace span
func (s *Server) logQueryPlan(ctx context.Context, query string, args ...interface{}) {
	// Skip for very fast queries to avoid overhead
	if !s.shouldProfileQuery(query) {
		return
	}

	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "query"))

	plan, err := s.AnalyzeQuery(ctx, query, args...)
	if err != nil {
		logger.Warn("failed to analyze query", zap.Error(err))
		return
	}

	// Log to OpenTelemetry span if available
	span := trace.SpanFromContext(ctx)
	if span.IsRecording() {
		span.SetAttributes(
			attribute.Int64("query.read_rows", plan.Statistics.ReadRows),
			attribute.Int64("query.read_bytes", plan.Statistics.ReadBytes),
			attribute.Float64("query.elapsed_seconds", plan.Statistics.ElapsedSec),
			attribute.Bool("query.cache_hit", plan.Statistics.QueryCacheHit),
		)
	}

	// Log to structured logger
	fields := []zap.Field{
		zap.String("query", plan.Query),
		zap.Int64("read_rows", plan.Statistics.ReadRows),
		zap.Int64("read_bytes", plan.Statistics.ReadBytes),
		zap.Float64("elapsed_seconds", plan.Statistics.ElapsedSec),
		zap.Bool("cache_hit", plan.Statistics.QueryCacheHit),
	}

	// Only log full plan in debug mode
	if logger.Core().Enabled(zap.DebugLevel) {
		if planJSON, err := json.Marshal(plan.Plan); err == nil {
			fields = append(fields, zap.ByteString("plan", planJSON))
		}
	}

	logger.Info("query executed", fields...)
}

// shouldProfileQuery determines if a query should be profiled based on heuristics
func (s *Server) shouldProfileQuery(query string) bool {
	// Skip for very simple queries
	if len(query) < 50 {
		return false
	}

	// Skip for known fast queries
	fastPrefixes := []string{
		"SELECT 1",
		"SELECT now()",
	}

	query = strings.TrimSpace(query)
	for _, prefix := range fastPrefixes {
		if strings.HasPrefix(strings.ToUpper(query), prefix) {
			return false
		}
	}

	return true
}
