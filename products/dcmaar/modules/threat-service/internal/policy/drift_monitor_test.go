package policy

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap/zaptest"
)

// MockDriftStorage implements DriftStorage for testing
type MockDriftStorage struct {
	metrics   map[string]*PolicyDriftMetrics
	alerts    []DriftAlert
	baselines map[string]*BaselineStats
}

func NewMockDriftStorage() *MockDriftStorage {
	return &MockDriftStorage{
		metrics:   make(map[string]*PolicyDriftMetrics),
		alerts:    make([]DriftAlert, 0),
		baselines: make(map[string]*BaselineStats),
	}
}

func (m *MockDriftStorage) StorePolicyMetrics(ctx context.Context, metrics *PolicyDriftMetrics) error {
	m.metrics[metrics.PolicyID] = metrics
	return nil
}

func (m *MockDriftStorage) GetPolicyMetrics(ctx context.Context, policyID string, timeRange TimeRange) (*PolicyDriftMetrics, error) {
	if metrics, exists := m.metrics[policyID]; exists {
		return metrics, nil
	}

	// Return mock metrics for testing
	return &PolicyDriftMetrics{
		PolicyID:         policyID,
		PolicyVersion:    "v1.0.0",
		LastUpdated:      time.Now(),
		CurrentAccuracy:  0.85,
		CurrentPrecision: 0.82,
		CurrentRecall:    0.88,
		CurrentF1:        0.85,
		CurrentVolume:    15000,
		AccuracyTrend: []TimeSeriesPoint{
			{Timestamp: time.Now().Add(-24 * time.Hour), Value: 0.92},
			{Timestamp: time.Now().Add(-12 * time.Hour), Value: 0.88},
			{Timestamp: time.Now().Add(-6 * time.Hour), Value: 0.86},
			{Timestamp: time.Now(), Value: 0.85},
		},
	}, nil
}

func (m *MockDriftStorage) StoreAlert(ctx context.Context, alert *DriftAlert) error {
	m.alerts = append(m.alerts, *alert)
	return nil
}

func (m *MockDriftStorage) GetActiveAlerts(ctx context.Context) ([]DriftAlert, error) {
	var activeAlerts []DriftAlert
	for _, alert := range m.alerts {
		if alert.Status == "open" {
			activeAlerts = append(activeAlerts, alert)
		}
	}
	return activeAlerts, nil
}

func (m *MockDriftStorage) UpdateBaselineStats(ctx context.Context, stats *BaselineStats) error {
	m.baselines[stats.PolicyID] = stats
	return nil
}

func (m *MockDriftStorage) GetBaselineStats(ctx context.Context, policyID string) (*BaselineStats, error) {
	if baseline, exists := m.baselines[policyID]; exists {
		return baseline, nil
	}

	// Return mock baseline for testing
	return &BaselineStats{
		PolicyID:      policyID,
		MeanAccuracy:  0.90,
		StdAccuracy:   0.03,
		MeanPrecision: 0.87,
		StdPrecision:  0.04,
		MeanRecall:    0.92,
		StdRecall:     0.02,
		MeanVolume:    12000.0,
		StdVolume:     1500.0,
		LastUpdated:   time.Now().Add(-7 * 24 * time.Hour),
	}, nil
}

func (m *MockDriftStorage) GetDriftTrends(ctx context.Context, timeRange TimeRange) ([]PolicyDriftMetrics, error) {
	var trends []PolicyDriftMetrics
	for _, metrics := range m.metrics {
		trends = append(trends, *metrics)
	}
	return trends, nil
}

func (m *MockDriftStorage) GetAlertHistory(ctx context.Context, timeRange TimeRange) ([]DriftAlert, error) {
	return m.alerts, nil
}

func (m *MockDriftStorage) GetPerformanceStats(ctx context.Context) (*DriftMetrics, error) {
	return &DriftMetrics{
		ChecksPerformed:  100,
		DriftDetections:  5,
		AlertsSent:       3,
		AvgCheckDuration: 1250.0,
		LastCheckTime:    time.Now(),
		HealthScore:      0.87,
	}, nil
}

// TestDriftMonitor_Lifecycle tests drift monitor start/stop
func TestDriftMonitor_Lifecycle(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()
	config.CheckInterval = 100 * time.Millisecond // Fast for testing

	monitor := NewDriftMonitor(logger, storage, config)

	// Test starting
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	err := monitor.Start(ctx)
	require.NoError(t, err, "Should start successfully")

	// Test metrics collection
	metrics := monitor.GetDriftMetrics()
	assert.NotNil(t, metrics, "Should return metrics")
	assert.GreaterOrEqual(t, metrics.HealthScore, 0.0, "Health score should be non-negative")

	// Test stopping
	err = monitor.Stop()
	require.NoError(t, err, "Should stop successfully")
}

// TestDriftMonitor_DriftDetection tests drift detection logic
func TestDriftMonitor_DriftDetection(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()
	config.AlertThreshold = 2.0

	monitor := NewDriftMonitor(logger, storage, config)

	// Test drift score calculation
	metrics := &PolicyDriftMetrics{
		PolicyID:         "test-policy",
		CurrentAccuracy:  0.75, // Significantly lower than baseline
		CurrentPrecision: 0.72,
		CurrentRecall:    0.78,
		CurrentVolume:    25000, // Much higher than baseline
	}

	baseline := &BaselineStats{
		PolicyID:      "test-policy",
		MeanAccuracy:  0.90,
		StdAccuracy:   0.03,
		MeanPrecision: 0.87,
		StdPrecision:  0.04,
		MeanRecall:    0.92,
		StdRecall:     0.02,
		MeanVolume:    12000.0,
		StdVolume:     1500.0,
	}

	driftScore := monitor.calculateDriftScore(metrics, baseline)
	assert.Greater(t, driftScore, 2.0, "Should detect significant drift")

	// Test drift reasons identification
	reasons := monitor.identifyDriftReasons(metrics, baseline)
	assert.NotEmpty(t, reasons, "Should identify drift reasons")
	assert.Contains(t, reasons[0], "accuracy", "Should identify accuracy issues")
}

// TestDriftMonitor_BaselineCalculation tests baseline statistics calculation
func TestDriftMonitor_BaselineCalculation(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	// Create test metrics with trend data
	metrics := &PolicyDriftMetrics{
		PolicyID: "test-policy",
		AccuracyTrend: []TimeSeriesPoint{
			{Value: 0.90}, {Value: 0.91}, {Value: 0.89}, {Value: 0.92}, {Value: 0.88},
		},
		PrecisionTrend: []TimeSeriesPoint{
			{Value: 0.85}, {Value: 0.87}, {Value: 0.86}, {Value: 0.88}, {Value: 0.84},
		},
	}

	baseline := monitor.calculateBaseline(metrics)

	assert.Equal(t, "test-policy", baseline.PolicyID)
	assert.Greater(t, baseline.MeanAccuracy, 0.0, "Mean accuracy should be calculated")
	assert.Greater(t, baseline.StdAccuracy, 0.0, "Standard deviation should be calculated")
	assert.Greater(t, baseline.MeanPrecision, 0.0, "Mean precision should be calculated")
	assert.Greater(t, baseline.StdPrecision, 0.0, "Precision std dev should be calculated")
}

// TestDriftMonitor_TrendAnalysis tests trend slope calculation
func TestDriftMonitor_TrendAnalysis(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	testCases := []struct {
		name          string
		points        []TimeSeriesPoint
		expectedSlope float64
	}{
		{
			name: "Declining trend",
			points: []TimeSeriesPoint{
				{Value: 1.0}, {Value: 0.8}, {Value: 0.6}, {Value: 0.4}, {Value: 0.2},
			},
			expectedSlope: -0.2, // Declining by 0.2 per point
		},
		{
			name: "Improving trend",
			points: []TimeSeriesPoint{
				{Value: 0.2}, {Value: 0.4}, {Value: 0.6}, {Value: 0.8}, {Value: 1.0},
			},
			expectedSlope: 0.2, // Improving by 0.2 per point
		},
		{
			name: "Stable trend",
			points: []TimeSeriesPoint{
				{Value: 0.5}, {Value: 0.5}, {Value: 0.5}, {Value: 0.5}, {Value: 0.5},
			},
			expectedSlope: 0.0, // No change
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			slope := monitor.calculateTrendSlope(tc.points)
			assert.InDelta(t, tc.expectedSlope, slope, 0.01, "Slope should match expected value")
		})
	}
}

// TestDriftMonitor_AlertGeneration tests alert creation and processing
func TestDriftMonitor_AlertGeneration(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	// Test alert creation
	metrics := &PolicyDriftMetrics{
		PolicyID:         "test-policy",
		CurrentAccuracy:  0.75,
		CurrentPrecision: 0.72,
		CurrentRecall:    0.78,
	}

	driftScore := 3.5
	reasons := []string{"Significant accuracy degradation detected", "Precision has declined"}

	alert := monitor.createDriftAlert("test-policy", metrics, driftScore, reasons)

	assert.NotEmpty(t, alert.AlertID, "Alert should have ID")
	assert.Equal(t, "test-policy", alert.PolicyID, "Alert should reference correct policy")
	assert.Equal(t, "critical", alert.Severity, "High drift score should create critical alert")
	assert.Equal(t, "open", alert.Status, "New alert should be open")
	assert.Contains(t, alert.Description, "3.50", "Description should include drift score")
	assert.NotEmpty(t, alert.RecommendedActions, "Should include recommended actions")

	// Test recommendation generation
	actions := monitor.generateRecommendations(metrics, reasons)
	assert.NotEmpty(t, actions, "Should generate recommendations")

	// Should include retrain recommendation for accuracy issues
	hasRetrainAction := false
	for _, action := range actions {
		if action.ActionType == "retrain_model" {
			hasRetrainAction = true
			assert.Equal(t, "high", action.Priority, "Retrain should be high priority")
			break
		}
	}
	assert.True(t, hasRetrainAction, "Should recommend model retraining for accuracy issues")
}

// TestDriftMonitor_StatisticalFunctions tests mathematical utility functions
func TestDriftMonitor_StatisticalFunctions(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	// Test mean calculation
	values := []float64{1.0, 2.0, 3.0, 4.0, 5.0}
	mean := monitor.calculateMean(values)
	assert.Equal(t, 3.0, mean, "Mean should be 3.0")

	// Test standard deviation
	stdDev := monitor.calculateStdDev(values, mean)
	assert.InDelta(t, 1.581, stdDev, 0.01, "Standard deviation should be approximately 1.581")

	// Test empty slice handling
	emptyMean := monitor.calculateMean([]float64{})
	assert.Equal(t, 0.0, emptyMean, "Empty slice should return 0 mean")

	emptyStdDev := monitor.calculateStdDev([]float64{1.0}, 1.0)
	assert.Equal(t, 0.0, emptyStdDev, "Single value should return 0 std dev")
}

// TestDriftMonitor_ConfigValidation tests configuration validation
func TestDriftMonitor_ConfigValidation(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()

	// Test default configuration
	defaultConfig := DefaultDriftConfig()
	assert.Equal(t, 15*time.Minute, defaultConfig.CheckInterval, "Default check interval should be 15 minutes")
	assert.Equal(t, 24*time.Hour, defaultConfig.WindowSize, "Default window size should be 24 hours")
	assert.Equal(t, 0.85, defaultConfig.AccuracyThreshold, "Default accuracy threshold should be 0.85")
	assert.Equal(t, 2.0, defaultConfig.AlertThreshold, "Default alert threshold should be 2.0")
	assert.True(t, defaultConfig.EnableSlackAlerts, "Slack alerts should be enabled by default")
	assert.True(t, defaultConfig.EnableEmailAlerts, "Email alerts should be enabled by default")

	// Test monitor with custom config
	customConfig := DriftConfig{
		CheckInterval:       5 * time.Minute,
		WindowSize:          12 * time.Hour,
		AlertThreshold:      1.5,
		MaxConcurrentChecks: 2,
	}

	monitor := NewDriftMonitor(logger, storage, customConfig)
	assert.NotNil(t, monitor, "Should create monitor with custom config")

	// Verify config defaults are applied for missing values
	assert.Equal(t, 5*time.Minute, monitor.config.CheckInterval, "Custom check interval should be preserved")
	assert.Equal(t, 12*time.Hour, monitor.config.WindowSize, "Custom window size should be preserved")
	assert.Equal(t, 0.85, monitor.config.AccuracyThreshold, "Missing accuracy threshold should use default")
}

// TestDriftMonitor_CacheOperations tests cache functionality
func TestDriftMonitor_CacheOperations(t *testing.T) {
	logger := zaptest.NewLogger(t)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	// Test cache updates
	policyID := "test-policy"
	metrics := &PolicyDriftMetrics{
		PolicyID:         policyID,
		CurrentAccuracy:  0.88,
		CurrentPrecision: 0.85,
		CurrentRecall:    0.90,
	}

	driftScore := 1.2
	reasons := []string{"Minor accuracy decline"}

	monitor.updatePolicyCache(policyID, metrics, driftScore, reasons)

	// Verify cache update
	cachedMetrics, err := monitor.GetPolicyDriftStatus(policyID)
	require.NoError(t, err, "Should retrieve cached metrics")
	assert.Equal(t, policyID, cachedMetrics.PolicyID, "Cached policy ID should match")
	assert.Equal(t, driftScore, cachedMetrics.DriftScore, "Cached drift score should match")
	assert.Equal(t, DriftStatusWarning, cachedMetrics.DriftStatus, "Should determine correct drift status")
	assert.Equal(t, reasons, cachedMetrics.DriftReasons, "Cached reasons should match")

	// Test recent alerts cache
	alert := &DriftAlert{
		AlertID:   "test-alert-001",
		PolicyID:  policyID,
		Severity:  "medium",
		Timestamp: time.Now(),
		Status:    "open",
	}

	err = monitor.processAlerts(context.Background(), []*DriftAlert{alert})
	require.NoError(t, err, "Should process alerts successfully")

	recentAlerts := monitor.GetRecentAlerts(10)
	assert.Len(t, recentAlerts, 1, "Should have one recent alert")
	assert.Equal(t, alert.AlertID, recentAlerts[0].AlertID, "Alert ID should match")
}

// BenchmarkDriftMonitor_DriftCalculation benchmarks drift score calculation
func BenchmarkDriftMonitor_DriftCalculation(b *testing.B) {
	logger := zaptest.NewLogger(b)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	metrics := &PolicyDriftMetrics{
		PolicyID:         "bench-policy",
		CurrentAccuracy:  0.85,
		CurrentPrecision: 0.82,
		CurrentRecall:    0.88,
		CurrentVolume:    15000,
	}

	baseline := &BaselineStats{
		PolicyID:      "bench-policy",
		MeanAccuracy:  0.90,
		StdAccuracy:   0.03,
		MeanPrecision: 0.87,
		StdPrecision:  0.04,
		MeanRecall:    0.92,
		StdRecall:     0.02,
		MeanVolume:    12000.0,
		StdVolume:     1500.0,
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		monitor.calculateDriftScore(metrics, baseline)
	}
}

// BenchmarkDriftMonitor_TrendAnalysis benchmarks trend slope calculation
func BenchmarkDriftMonitor_TrendAnalysis(b *testing.B) {
	logger := zaptest.NewLogger(b)
	storage := NewMockDriftStorage()
	config := DefaultDriftConfig()

	monitor := NewDriftMonitor(logger, storage, config)

	// Create trend data with 100 points
	points := make([]TimeSeriesPoint, 100)
	for i := 0; i < 100; i++ {
		points[i] = TimeSeriesPoint{
			Timestamp: time.Now().Add(time.Duration(i) * time.Hour),
			Value:     0.9 - float64(i)*0.001, // Declining trend
		}
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		monitor.calculateTrendSlope(points)
	}
}
