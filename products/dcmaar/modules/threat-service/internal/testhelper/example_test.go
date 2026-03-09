package testhelper_test

import (
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"your-module-path/internal/testhelper"
)

func TestExample(t *testing.T) {
	// Create a test context with timeout
	ctx := testhelper.NewTestContext(t, 30*time.Second)

	// Create a test logger
	logger := testhelper.NewTestLogger(t)
	logger.Info("Starting test")

	// Load test configuration
	cfg := testhelper.LoadTestConfig()

	// Create a test ClickHouse connection
	db := testhelper.NewClickHouseTestDB(t, cfg.ClickHouseDSN)

	// Create a test Redis client
	redisClient := testhelper.NewRedisTestClient(t, cfg.RedisAddr)

	// Test database operations
	t.Run("database operations", func(t *testing.T) {
		// Create a test table
		_, err := db.ExecContext(ctx, `
			CREATE TABLE IF NOT EXISTS test_table (
				id UInt64,
				name String,
				created_at DateTime DEFAULT now()
			) ENGINE = MergeTree()
			ORDER BY (id)
		`)
		require.NoError(t, err, "failed to create test table")

		// Insert test data
		_, err = db.ExecContext(ctx, "INSERT INTO test_table (id, name) VALUES (?, ?)", 1, "test")
		require.NoError(t, err, "failed to insert test data")

		// Query test data
		var (
			id   uint64
			name string
		)
		err = db.QueryRowContext(ctx, "SELECT id, name FROM test_table WHERE id = ?", 1).Scan(&id, &name)
		require.NoError(t, err, "failed to query test data")

		assert.Equal(t, uint64(1), id, "unexpected id")
		assert.Equal(t, "test", name, "unexpected name")

		// Reset the test database
		testhelper.ResetTestDB(t, db)

		// Verify the table was truncated
		var count int
		err = db.QueryRowContext(ctx, "SELECT count() FROM test_table").Scan(&count)
		assert.NoError(t, err, "failed to count rows")
		assert.Equal(t, 0, count, "expected table to be empty after reset")
	})

	// Test Redis operations
	t.Run("redis operations", func(t *testing.T) {
		// Set a test key
		err := redisClient.Set(ctx, "test_key", "test_value", 5*time.Minute).Err()
		require.NoError(t, err, "failed to set test key")

		// Get the test key
		val, err := redisClient.Get(ctx, "test_key").Result()
		require.NoError(t, err, "failed to get test key")
		assert.Equal(t, "test_value", val, "unexpected value from redis")
	})

	// Test file operations
	t.Run("file operations", func(t *testing.T) {
		// Create a test file
		testContent := []byte("test content")
		tmpFile := testhelper.MustCreateTempFile(t, testContent)
		defer os.Remove(tmpFile)

		// Read the test file
		content, err := os.ReadFile(tmpFile)
		require.NoError(t, err, "failed to read test file")
		assert.Equal(t, testContent, content, "unexpected file content")

		// Test MustReadFile helper
		content = testhelper.MustReadFile(t, tmpFile)
		assert.Equal(t, testContent, content, "unexpected content from MustReadFile")
	})

	// Test retry mechanism
	t.Run("retry mechanism", func(t *testing.T) {
		var attempts int
		err := testhelper.Retry(t, 1*time.Second, func() error {
			attempts++
			if attempts < 3 {
				return assert.AnError
			}
			return nil
		})

		assert.NoError(t, err, "retry should eventually succeed")
		assert.Equal(t, 3, attempts, "unexpected number of attempts")
	})
}

// TestMain can be used to set up and tear down test resources
func TestMain(m *testing.M) {
	// Set up any global test fixtures here
	// ...

	// Run the tests
	code := m.Run()

	// Clean up any global test fixtures here
	// ...

	os.Exit(code)
}
