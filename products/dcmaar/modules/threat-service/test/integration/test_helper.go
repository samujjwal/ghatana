package integration

import (
	"context"
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
)

// TestEnvironment manages the test environment including services and failure injection
type TestEnvironment struct {
	ClickHouseDSN string
	RedisAddr     string
	Logger        *zap.Logger
	
	// Failure injection controls
	mu              sync.Mutex
	failuresEnabled map[string]bool
}

func SetupTestEnvironment(t *testing.T) *TestEnvironment {
	t.Helper()

	// Initialize logger
	logger, err := zap.NewDevelopment()
	require.NoError(t, err, "Failed to create logger")
	t.Cleanup(func() {
		_ = logger.Sync()
	})

	// Get environment variables or use defaults
	dsn := os.Getenv("TEST_CLICKHOUSE_DSN")
	if dsn == "" {
		dsn = "tcp://localhost:9000?database=testdb&username=test&password=test"
	}

	redisAddr := os.Getenv("TEST_REDIS_ADDR")
	if redisAddr == "" {
		redisAddr = "localhost:6379"
	}

	return &TestEnvironment{
		ClickHouseDSN:   dsn,
		RedisAddr:       redisAddr,
		Logger:          logger,
		failuresEnabled: make(map[string]bool),
	}
}

func (e *TestEnvironment) WaitForServices(ctx context.Context, timeout time.Duration) error {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	// TODO: Implement service health checks
	// For now, just wait a bit to ensure services are ready
	select {
	case <-ticker.C:
		e.Logger.Info("Test services ready")
		return nil
	case <-ctx.Done():
		return fmt.Errorf("timeout waiting for services: %w", ctx.Err())
	}
}

func (e *TestEnvironment) EnableFailure(name string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.failuresEnabled[name] = true
	e.Logger.Info("Enabled failure mode", zap.String("failure", name))
}

func (e *TestEnvironment) DisableFailure(name string) {
	e.mu.Lock()
	defer e.mu.Unlock()
	delete(e.failuresEnabled, name)
	e.Logger.Info("Disabled failure mode", zap.String("failure", name))
}

func (e *TestEnvironment) IsFailureEnabled(name string) bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.failuresEnabled[name]
}

func (e *TestEnvironment) Cleanup() {
	e.mu.Lock()
	defer e.mu.Unlock()
	
	// Reset all failure modes
	e.failuresEnabled = make(map[string]bool)
	e.Logger.Info("Test environment cleaned up")
}
