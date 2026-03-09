//go:build integration

package testhelper_test

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/testhelper"
)

func BenchmarkDatabaseOperations(b *testing.B) {
	// Set up test context and logger
	ctx := testhelper.NewTestContext(b, 30*time.Second)
	logger := testhelper.NewTestLogger(b)

	// Set up test database
	db := testhelper.NewClickHouseTestDB(b, "")
	defer db.Close()

	// Prepare test data
	setupTestTable(b, ctx, db)

	b.Run("InsertSingle", func(b *testing.B) {
		benchmarkInsertSingle(b, ctx, db, logger)
	})

	b.Run("BatchInsert", func(b *testing.B) {
		benchmarkBatchInsert(b, ctx, db, logger)
	})

	b.Run("QueryByID", func(b *testing.B) {
		benchmarkQueryByID(b, ctx, db, logger)
	})
}

func setupTestTable(tb testing.TB, ctx context.Context, db *sql.DB) {
	tb.Helper()

	_, err := db.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS benchmark_test (
			id UInt64,
			name String,
			value Float64,
			timestamp DateTime DEFAULT now()
		) ENGINE = MergeTree()
		ORDER BY (id, timestamp)
	`)
	require.NoError(tb, err)

	// Clear any existing data
	_, err = db.ExecContext(ctx, "TRUNCATE TABLE benchmark_test")
	require.NoError(tb, err)
}

func benchmarkInsertSingle(b *testing.B, ctx context.Context, db *sql.DB, logger *zap.Logger) {
	b.ResetTimer()
	b.ReportAllocs()

	for i := 0; i < b.N; i++ {
		_, err := db.ExecContext(
			ctx,
			"INSERT INTO benchmark_test (id, name, value) VALUES (?, ?, ?)",
			uint64(i),
			"test",
			float64(i)*1.1,
		)
		if err != nil {
			b.Fatalf("Insert failed: %v", err)
		}
	}
}

func benchmarkBatchInsert(b *testing.B, ctx context.Context, db *sql.DB, logger *zap.Logger) {
	batchSize := 1000
	b.ResetTimer()
	b.ReportAllocs()

	for i := 0; i < b.N; i += batchSize {
		tx, err := db.Begin()
		require.NoError(b, err)

		stmt, err := tx.PrepareContext(ctx,
			"INSERT INTO benchmark_test (id, name, value) VALUES (?, ?, ?)")
		require.NoError(b, err)

		for j := 0; j < batchSize && i+j < b.N; j++ {
			_, err = stmt.ExecContext(
				ctx,
				uint64(i+j),
				"batch",
				float64(i+j)*1.1,
			)
			require.NoError(b, err)
		}

		err = tx.Commit()
		require.NoError(b, err)
	}
}

func benchmarkQueryByID(b *testing.B, ctx context.Context, db *sql.DB, logger *zap.Logger) {
	// Insert test data
	_, err := db.ExecContext(ctx, `
		INSERT INTO benchmark_test (id, name, value)
		SELECT number, 'test', number * 1.1
		FROM numbers(10000)
	`)
	require.NoError(b, err)

	b.ResetTimer()
	b.ReportAllocs()

	for i := 0; i < b.N; i++ {
		id := i % 10000
		var result float64
		err := db.QueryRowContext(
			ctx,
			"SELECT value FROM benchmark_test WHERE id = ? LIMIT 1",
			uint64(id),
		).Scan(&result)

		if err != nil {
			b.Fatalf("Query failed: %v", err)
		}
		_ = result
	}
}
