package testutil

import (
	"context"
	"testing"
	"time"

	"go.uber.org/zap"
)

// ContextWithTimeout returns a context with a test timeout
func ContextWithTimeout(t testing.TB, timeout time.Duration) (context.Context, context.CancelFunc) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	t.Cleanup(func() {
		cancel()
	})
	return ctx, cancel
}

// ContextWithLogger returns a context with a test logger
func ContextWithLogger(t testing.TB) (context.Context, *zap.Logger) {
	logger := zap.NewNop()
	if testing.Verbose() {
		logger, _ = zap.NewDevelopment()
	}
	return context.WithValue(context.Background(), "logger", logger), logger
}

// ContextWithValues returns a context with test values
func ContextWithValues(t testing.TB) context.Context {
	ctx, _ := ContextWithLogger(t)
	return ctx
}
