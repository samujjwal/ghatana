package query

import (
	"context"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
	"go.uber.org/zap/zaptest"
)

func BenchmarkAnalyzeQuery(b *testing.B) {
	setupBenchmark := func() (*Server, func()) {
		db, mock, err := sqlmock.New()
		if err != nil {
			b.Fatalf("failed to create mock: %v", err)
		}

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

		server := &Server{
			db:     db,
			logger: zaptest.NewLogger(b),
		}

		return server, func() {
			db.Close()
		}
	}

	b.Run("AnalyzeQuery", func(b *testing.B) {
		server, cleanup := setupBenchmark()
		defer cleanup()

		b.ResetTimer()
		for i := 0; i < b.N; i++ {
			_, err := server.AnalyzeQuery(context.Background(), "SELECT * FROM events")
			if err != nil {
				b.Fatalf("AnalyzeQuery failed: %v", err)
			}
		}
	})

	b.Run("GetQueryStats", func(b *testing.B) {
		server, cleanup := setupBenchmark()
		defer cleanup()

		b.ResetTimer()
		for i := 0; i < b.N; i++ {
			_, err := server.getQueryStats(context.Background(), "SELECT * FROM events")
			if err != nil {
				b.Fatalf("getQueryStats failed: %v", err)
			}
		}
	})

	b.Run("LogQueryPlan", func(b *testing.B) {
		server, cleanup := setupBenchmark()
		defer cleanup()

		b.ResetTimer()
		for i := 0; i < b.N; i++ {
			server.logQueryPlan(context.Background(), "SELECT * FROM events")
		}
	})
}

func BenchmarkShouldProfileQuery(b *testing.B) {
	server := &Server{}
	queries := []string{
		"SELECT 1",
		"SELECT * FROM events LIMIT 10",
		"SELECT * FROM events WHERE timestamp > now() - INTERVAL '1 hour' ORDER BY timestamp DESC LIMIT 100",
		"SELECT now()",
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		for _, q := range queries {
			server.shouldProfileQuery(q)
		}
	}
}

func BenchmarkQueryAnalysisIntegration(b *testing.B) {
	setupBenchmark := func() (*Server, func()) {
		db, mock, err := sqlmock.New()
		if err != nil {
			b.Fatalf("failed to create mock: %v", err)
		}

		// Setup mock for main query
		rows := sqlmock.NewRows([]string{"id", "event_type", "timestamp"}).
			AddRow(1, "test", "2023-01-01T00:00:00Z")
		mock.ExpectQuery(`^SELECT`).WillReturnRows(rows)

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

		server := &Server{
			db:     db,
			logger: zaptest.NewLogger(b),
		}

		return server, func() {
			db.Close()
		}
	}

	server, cleanup := setupBenchmark()
	defer cleanup()

	b.Run("QueryWithAnalysis", func(b *testing.B) {
		b.ResetTimer()
		for i := 0; i < b.N; i++ {
			// Execute query
			rows, err := server.db.Query("SELECT * FROM events LIMIT 1")
			if err != nil {
				b.Fatalf("query failed: %v", err)
			}
			rows.Close()

			// Log query plan
			server.logQueryPlan(context.Background(), "SELECT * FROM events LIMIT 1")
		}
	})
}
