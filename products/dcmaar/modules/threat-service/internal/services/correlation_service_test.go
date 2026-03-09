package services

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap/zaptest"

	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
)

// TestCorrelationService_IntegrationScenario tests end-to-end correlation
// between agent anomaly events and extension latency events
func TestCorrelationService_IntegrationScenario(t *testing.T) {
	t.Skip("Integration test - requires ClickHouse database")

	logger := zaptest.NewLogger(t)

	// This would normally connect to a test ClickHouse instance
	storage, err := storage.New("clickhouse://localhost:9000/test", logger)
	require.NoError(t, err)
	defer storage.Close()

	correlationSvc := NewCorrelationService(logger, storage)

	// Simulate a correlation scenario:
	// 1. Agent detects CPU anomaly at time T
	// 2. Extension reports high latency at time T+2min
	// 3. Correlation service should detect this pattern

	ctx := context.Background()
	config := DefaultCorrelationConfig()
	config.TimeWindowMinutes = 10    // Wider window for test
	config.MinCorrelationScore = 0.5 // Lower threshold for test

	// Run correlation analysis
	correlations, err := correlationSvc.AnalyzeForCorrelations(ctx, config)
	require.NoError(t, err)

	// For integration testing, we'd verify:
	// - Correlations are found when both anomaly and latency events exist
	// - Correlation scores are calculated correctly
	// - Incidents are stored and retrievable

	t.Logf("Found %d correlations", len(correlations))

	for _, correlation := range correlations {
		assert.NotEmpty(t, correlation.IncidentID)
		assert.Greater(t, correlation.CorrelationScore, 0.0)
		assert.Greater(t, correlation.Confidence, 0.0)
		assert.NotEmpty(t, correlation.DeviceID)

		// Store the correlation
		err := correlationSvc.StoreCorrelatedIncident(ctx, correlation)
		assert.NoError(t, err)

		t.Logf("Stored correlation: ID=%s, Score=%.2f, Confidence=%.2f, Type=%s, Severity=%s",
			correlation.IncidentID,
			correlation.CorrelationScore,
			correlation.Confidence,
			correlation.IncidentType,
			correlation.Severity)
	}
}

// TestCorrelationService_TimeWindowLogic tests the time window correlation logic
func TestCorrelationService_TimeWindowLogic(t *testing.T) {
	logger := zaptest.NewLogger(t)

	// Use nil storage for unit test (we're testing logic, not storage)
	correlationSvc := NewCorrelationService(logger, nil)

	baseTime := time.Now()
	windowDuration := 5 * time.Minute

	// Create mock anomaly events
	anomalyEvents := []map[string]interface{}{
		{
			"event_id":  "anomaly-1",
			"timestamp": baseTime,
			"device_id": "device-123",
			"tenant_id": "tenant-1",
			"payload": map[string]interface{}{
				"metric": "cpu_usage",
				"score":  3.5,
			},
		},
		{
			"event_id":  "anomaly-2",
			"timestamp": baseTime.Add(2 * time.Minute),
			"device_id": "device-123",
			"tenant_id": "tenant-1",
			"payload": map[string]interface{}{
				"metric": "mem_usage",
				"score":  2.8,
			},
		},
	}

	// Create mock web events
	webEvents := []map[string]interface{}{
		{
			"event_id":    "web-1",
			"timestamp":   baseTime.Add(1 * time.Minute),
			"device_id":   "device-123",
			"tenant_id":   "tenant-1",
			"latency_ms":  4500.0,
			"domain":      "example.com",
			"status_code": int32(200),
		},
		{
			"event_id":    "web-2",
			"timestamp":   baseTime.Add(3 * time.Minute),
			"device_id":   "device-123",
			"tenant_id":   "tenant-1",
			"latency_ms":  5200.0,
			"domain":      "api.example.com",
			"status_code": int32(200),
		},
	}

	// Test the time window finding logic
	windows := correlationSvc.findTimeWindows(anomalyEvents, webEvents, windowDuration)

	// Should find overlapping windows
	assert.NotEmpty(t, windows, "Should find time windows with overlapping events")

	for _, window := range windows {
		assert.NotEmpty(t, window.anomalies, "Window should contain anomaly events")
		assert.NotEmpty(t, window.webEvents, "Window should contain web events")

		// Verify time boundaries
		duration := window.end.Sub(window.start)
		assert.Equal(t, windowDuration, duration, "Window duration should match expected")

		t.Logf("Found window: %v to %v with %d anomalies and %d web events",
			window.start, window.end, len(window.anomalies), len(window.webEvents))
	}
}

// TestCorrelationService_StatisticalMethods tests correlation calculations
func TestCorrelationService_StatisticalMethods(t *testing.T) {
	logger := zaptest.NewLogger(t)
	correlationSvc := NewCorrelationService(logger, nil)

	// Test Pearson correlation calculation
	t.Run("Pearson Correlation", func(t *testing.T) {
		// Perfect positive correlation
		x1 := []float64{1, 2, 3, 4, 5}
		y1 := []float64{2, 4, 6, 8, 10}
		corr1 := correlationSvc.calculatePearsonCorrelation(x1, y1)
		assert.InDelta(t, 1.0, corr1, 0.01, "Should have perfect positive correlation")

		// Perfect negative correlation
		x2 := []float64{1, 2, 3, 4, 5}
		y2 := []float64{5, 4, 3, 2, 1}
		corr2 := correlationSvc.calculatePearsonCorrelation(x2, y2)
		assert.InDelta(t, -1.0, corr2, 0.01, "Should have perfect negative correlation")

		// No correlation
		x3 := []float64{1, 2, 3, 4, 5}
		y3 := []float64{1, 1, 1, 1, 1}
		corr3 := correlationSvc.calculatePearsonCorrelation(x3, y3)
		assert.InDelta(t, 0.0, corr3, 0.01, "Should have no correlation")
	})

	// Test confidence calculation
	t.Run("Confidence Calculation", func(t *testing.T) {
		baseTime := time.Now()

		// High confidence: many events in short window
		conf1 := correlationSvc.calculateConfidence(10, 8, baseTime, baseTime.Add(5*time.Minute))
		assert.Greater(t, conf1, 0.7, "Should have high confidence for many events in short window")

		// Lower confidence: few events in long window
		conf2 := correlationSvc.calculateConfidence(2, 1, baseTime, baseTime.Add(45*time.Minute))
		assert.Less(t, conf2, 0.5, "Should have lower confidence for few events in long window")
	})

	// Test incident classification
	t.Run("Incident Classification", func(t *testing.T) {
		// CPU + Memory + Latency = Mixed
		incidentType1 := correlationSvc.determineIncidentType([]float64{3.0}, []float64{3.0}, []float64{5000})
		assert.Equal(t, "mixed", incidentType1)

		// Only CPU = Resource
		incidentType2 := correlationSvc.determineIncidentType([]float64{3.0}, []float64{}, []float64{5000})
		assert.Equal(t, "resource", incidentType2)

		// Only Latency = Performance
		incidentType3 := correlationSvc.determineIncidentType([]float64{}, []float64{}, []float64{5000})
		assert.Equal(t, "performance", incidentType3)
	})

	// Test severity determination
	t.Run("Severity Determination", func(t *testing.T) {
		// High CPU + Memory + Latency + High correlation = Critical
		severity1 := correlationSvc.determineSeverity([]float64{4.0}, []float64{4.0}, []float64{12000}, 0.9)
		assert.Equal(t, "critical", severity1)

		// Moderate values = Medium/High
		severity2 := correlationSvc.determineSeverity([]float64{2.5}, []float64{2.0}, []float64{6000}, 0.7)
		assert.Contains(t, []string{"medium", "high"}, severity2)

		// Low values = Low
		severity3 := correlationSvc.determineSeverity([]float64{1.5}, []float64{1.0}, []float64{3500}, 0.4)
		assert.Contains(t, []string{"low", "medium"}, severity3)
	})
}
