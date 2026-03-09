package telemetry_test

import (
	"context"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/samujjwal/dcmaar/apps/server/internal/telemetry"
	"github.com/samujjwal/dcmaar/apps/server/internal/testutil"
)

func TestMetrics(t *testing.T) {
	_, logger := testutil.TestContext(t)
	cfg := telemetry.DefaultMetricsConfig()

	metrics, err := telemetry.NewMetrics(cfg, logger)
	assert.NoError(t, err)

	// Test metrics collection
	ctx := context.Background()
	metrics.RecordRequest(ctx, "GET", "/test", "200", 100*time.Millisecond, 512)

	// Test metrics endpoint
	req := httptest.NewRequest("GET", "/metrics", nil)
	w := httptest.NewRecorder()
	metrics.Handler().ServeHTTP(w, req)
	assert.Equal(t, 200, w.Code)
}
