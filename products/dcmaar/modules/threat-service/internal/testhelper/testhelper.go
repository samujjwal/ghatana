package testhelper

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
	"go.uber.org/zap/zaptest"
)

// TestConfig holds configuration for test environment
type TestConfig struct {
	ClickHouseDSN string
	RedisAddr     string
}

// TestContext is a test context with timeout
type TestContext struct {
	context.Context
	Cancel context.CancelFunc
}

// NewTestContext creates a new test context with timeout
func NewTestContext(tb testing.TB, timeout time.Duration) *TestContext {
	tb.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	tb.Cleanup(func() {
		cancel()
	})

	return &TestContext{
		Context: ctx,
		Cancel:  cancel,
	}
}

// NewTestLogger creates a new test logger
func NewTestLogger(tb testing.TB) *zap.Logger {
	tb.Helper()
	return zaptest.NewLogger(tb, zaptest.Level(zap.DebugLevel))
}

// NewClickHouseTestDB creates a new test ClickHouse connection
func NewClickHouseTestDB(tb testing.TB, dsn string) *sql.DB {
	tb.Helper()

	if dsn == "" {
		dsn = "clickhouse://test:test@localhost:9000/test?debug=true"
	}

	conn, err := sql.Open("clickhouse", dsn)
	require.NoError(tb, err, "failed to connect to ClickHouse")

	// Test the connection
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err = conn.PingContext(ctx)
	require.NoError(tb, err, "failed to ping ClickHouse")

	tb.Cleanup(func() {
		_ = conn.Close()
	})

	return conn
}

// NewRedisTestClient creates a new test Redis client
func NewRedisTestClient(tb testing.TB, addr string) *redis.Client {
	tb.Helper()

	if addr == "" {
		addr = "localhost:6379"
	}

	client := redis.NewClient(&redis.Options{
		Addr:         addr,
		Password:     "", // no password set
		DB:           0,  // use default DB
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
	})

	// Test the connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := client.Ping(ctx).Result()
	require.NoError(tb, err, "failed to ping Redis")

	tb.Cleanup(func() {
		_ = client.Close()
	})

	return client
}

// ResetTestDB truncates all tables in the test database
func ResetTestDB(tb testing.TB, db *sql.DB) {
	tb.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Get all tables in the database
	rows, err := db.QueryContext(ctx, "SHOW TABLES")
	require.NoError(tb, err, "failed to list tables")

	defer func() {
		_ = rows.Close()
	}()

	var tables []string
	for rows.Next() {
		var table string
		err := rows.Scan(&table)
		require.NoError(tb, err, "failed to scan table name")
		tables = append(tables, table)
	}

	require.NoError(tb, rows.Err(), "error iterating tables")

	// Truncate all tables
	for _, table := range tables {
		_, err := db.ExecContext(ctx, fmt.Sprintf("TRUNCATE TABLE %s", table))
		require.NoError(tb, err, "failed to truncate table %s", table)
	}
}

// LoadTestConfig loads test configuration from environment variables
func LoadTestConfig() *TestConfig {
	dsn := os.Getenv("CLICKHOUSE_DSN")
	if dsn == "" {
		dsn = "clickhouse://test:test@localhost:9000/test?debug=true"
	}

	redisAddr := os.Getenv("REDIS_ADDR")
	if redisAddr == "" {
		redisAddr = "localhost:6379"
	}

	return &TestConfig{
		ClickHouseDSN: dsn,
		RedisAddr:     redisAddr,
	}
}

// MustReadFile reads a file and fails the test if an error occurs
func MustReadFile(tb testing.TB, path string) []byte {
	tb.Helper()

	data, err := os.ReadFile(path)
	require.NoError(tb, err, "failed to read file: %s", path)

	return data
}

// MustCreateTempFile creates a temporary file with the given content
func MustCreateTempFile(tb testing.TB, content []byte) string {
	tb.Helper()

	tmpFile, err := os.CreateTemp("", "test-*.tmp")
	require.NoError(tb, err, "failed to create temp file")

	_, err = tmpFile.Write(content)
	require.NoError(tb, err, "failed to write to temp file")

	err = tmpFile.Close()
	require.NoError(tb, err, "failed to close temp file")

	tb.Cleanup(func() {
		_ = os.Remove(tmpFile.Name())
	})

	return tmpFile.Name()
}

// Retry retries a function until it succeeds or the timeout is reached
func Retry(tb testing.TB, timeout time.Duration, fn func() error) error {
	tb.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()

	var lastErr error
	for {
		select {
		case <-ctx.Done():
			if lastErr == nil {
				lastErr = ctx.Err()
			}
			return fmt.Errorf("retry timed out: %w", lastErr)
		case <-ticker.C:
			err := fn()
			if err == nil {
				return nil
			}
			lastErr = err
		}
	}
}
