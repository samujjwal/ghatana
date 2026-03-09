package query

import (
	"context"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap/zaptest"
)

func TestAnalyzeQuery(t *testing.T) {
	tests := []struct {
		name        string
		setupMock   func(mock sqlmock.Sqlmock)
		expectError bool
		validate    func(t *testing.T, plan *ExplainPlan)
	}{
		{
			name: "successful query analysis",
			setupMock: func(mock sqlmock.Sqlmock) {
				// Mock the EXPLAIN query
				rows := sqlmock.NewRows([]string{"explain"}).
					AddRow(`{"query": "SELECT * FROM events", "plan": {"type": "SELECT"}}`)
				mock.ExpectQuery(`^EXPLAIN`).WillReturnRows(rows)

				// Mock the stats query
				statsRows := sqlmock.NewRows([]string{
					"read_rows", "read_bytes", "written_rows", "written_bytes",
					"total_rows", "elapsed_seconds", "memory_usage_bytes", "query_cache_hit",
				}).AddRow(100, 1024, 0, 0, 100, 0.1, 1048576, false)
				mock.ExpectQuery(`(?s).*EXPLAIN ESTIMATE.*`).WillReturnRows(statsRows)
			},
			expectError: false,
			validate: func(t *testing.T, plan *ExplainPlan) {
				assert.Equal(t, "SELECT * FROM events", plan.Query)
				assert.Equal(t, int64(100), plan.Statistics.ReadRows)
				assert.Equal(t, int64(1024), plan.Statistics.ReadBytes)
				assert.Equal(t, int64(100), plan.Statistics.TotalRows)
				assert.Equal(t, 0.1, plan.Statistics.ElapsedSec)
				assert.Equal(t, int64(1048576), plan.Statistics.MemoryUsage)
			},
		},
		{
			name: "query with cache hit",
			setupMock: func(mock sqlmock.Sqlmock) {
				rows := sqlmock.NewRows([]string{"explain"}).
					AddRow(`{"query": "SELECT * FROM events", "plan": {"type": "SELECT"}}`)
				mock.ExpectQuery(`^EXPLAIN`).WillReturnRows(rows)

				statsRows := sqlmock.NewRows([]string{
					"read_rows", "read_bytes", "written_rows", "written_bytes",
					"total_rows", "elapsed_seconds", "memory_usage_bytes", "query_cache_hit",
				}).AddRow(0, 0, 0, 0, 100, 0.001, 0, true)
				mock.ExpectQuery(`(?s).*EXPLAIN ESTIMATE.*`).WillReturnRows(statsRows)
			},
			expectError: false,
			validate: func(t *testing.T, plan *ExplainPlan) {
				assert.True(t, plan.Statistics.QueryCacheHit)
				assert.Equal(t, int64(0), plan.Statistics.ReadRows)
				assert.Equal(t, 0.001, plan.Statistics.ElapsedSec)
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			db, mock, err := sqlmock.New()
			require.NoError(t, err)
			defer db.Close()

			// Setup mock expectations
			tt.setupMock(mock)

			// Create server with mock DB
			server := &Server{
				db:     db,
				logger: zaptest.NewLogger(t),
			}

			// Test AnalyzeQuery
			plan, err := server.AnalyzeQuery(context.Background(), "SELECT * FROM events")

			// Assert expectations
			if tt.expectError {
				assert.Error(t, err)
				return
			}

			require.NoError(t, err)
			tt.validate(t, plan)

			// Ensure all expectations were met
			assert.NoError(t, mock.ExpectationsWereMet())
		})
	}
}

func TestShouldProfileQuery(t *testing.T) {
	tests := []struct {
		name     string
		query    string
		expected bool
	}{
		{
			name:     "simple query",
			query:    "SELECT 1",
			expected: false,
		},
		{
			name:     "short query",
			query:    "SELECT * FROM events LIMIT 10",
			expected: false,
		},
		{
			name:     "complex query",
			query:    "SELECT * FROM events WHERE timestamp > now() - INTERVAL '1 hour' ORDER BY timestamp DESC LIMIT 100",
			expected: true,
		},
		{
			name:     "known fast query",
			query:    "SELECT now()",
			expected: false,
		},
	}

	server := &Server{}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := server.shouldProfileQuery(tt.query)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestLogQueryPlan(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	// Setup mock for EXPLAIN query
	explainRows := sqlmock.NewRows([]string{"explain"}).
		AddRow(`{"query": "SELECT * FROM events", "plan": {"type": "SELECT"}}`)
	mock.ExpectQuery(`^EXPLAIN`).WillReturnRows(explainRows)

	// Setup mock for stats query
	statsRows := sqlmock.NewRows([]string{
		"read_rows", "read_bytes", "written_rows", "written_bytes",
		"total_rows", "elapsed_seconds", "memory_usage_bytes", "query_cache_hit",
	}).AddRow(100, 1024, 0, 0, 100, 0.1, 1048576, false)
	mock.ExpectQuery(`(?s).*EXPLAIN ESTIMATE.*`).WillReturnRows(statsRows)

	// Create server with mock DB and test logger
	logger := zaptest.NewLogger(t)
	server := &Server{
		db:     db,
		logger: logger,
	}

	// Test logQueryPlan
	server.logQueryPlan(context.Background(), "SELECT * FROM events")

	// Verify all expectations were met
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestGetQueryStats(t *testing.T) {
	db, mock, err := sqlmock.New()
	require.NoError(t, err)
	defer db.Close()

	tests := []struct {
		name        string
		setupMock   func()
		expectError bool
		validate    func(t *testing.T, stats *QueryStats)
	}{
		{
			name: "successful stats retrieval",
			setupMock: func() {
				rows := sqlmock.NewRows([]string{
					"read_rows", "read_bytes", "written_rows", "written_bytes",
					"total_rows", "elapsed_seconds", "memory_usage_bytes", "query_cache_hit",
				}).AddRow(100, 1024, 10, 512, 100, 0.1, 1048576, false)
				mock.ExpectQuery(`(?s).*EXPLAIN ESTIMATE.*`).WillReturnRows(rows)
			},
			expectError: false,
			validate: func(t *testing.T, stats *QueryStats) {
				assert.Equal(t, int64(100), stats.ReadRows)
				assert.Equal(t, int64(1024), stats.ReadBytes)
				assert.Equal(t, int64(10), stats.WrittenRows)
				assert.Equal(t, int64(512), stats.WrittenBytes)
				assert.Equal(t, int64(100), stats.TotalRows)
				assert.Equal(t, 0.1, stats.ElapsedSec)
				assert.Equal(t, int64(1048576), stats.MemoryUsage)
				assert.False(t, stats.QueryCacheHit)
			},
		},
		{
			name: "cache hit",
			setupMock: func() {
				rows := sqlmock.NewRows([]string{
					"read_rows", "read_bytes", "written_rows", "written_bytes",
					"total_rows", "elapsed_seconds", "memory_usage_bytes", "query_cache_hit",
				}).AddRow(0, 0, 0, 0, 100, 0.001, 0, true)
				mock.ExpectQuery(`(?s).*EXPLAIN ESTIMATE.*`).WillReturnRows(rows)
			},
			expectError: false,
			validate: func(t *testing.T, stats *QueryStats) {
				assert.True(t, stats.QueryCacheHit)
				assert.Equal(t, int64(0), stats.ReadRows)
				assert.Equal(t, 0.001, stats.ElapsedSec)
			},
		},
	}

	server := &Server{
		db:     db,
		logger: zaptest.NewLogger(t),
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Reset mock expectations
			mock.Expectations()

			// Setup mock
			tt.setupMock()

			// Test getQueryStats
			stats, err := server.getQueryStats(context.Background(), "SELECT * FROM events")

			// Assert expectations
			if tt.expectError {
				assert.Error(t, err)
				return
			}

			require.NoError(t, err)
			tt.validate(t, stats)

			// Ensure all expectations were met
			assert.NoError(t, mock.ExpectationsWereMet())
		})
	}
}
