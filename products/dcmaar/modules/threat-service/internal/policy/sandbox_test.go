package policy

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap/zaptest"
)

// TestPolicySandbox_DefaultConfiguration tests default configuration
func TestPolicySandbox_DefaultConfiguration(t *testing.T) {
	config := DefaultSimulationConfig()

	assert.Equal(t, 1000000, config.MaxEvents, "Max events should be 1M per requirements")
	assert.Equal(t, 10000, config.BatchSize, "Batch size should be reasonable")
	assert.Equal(t, 2*time.Minute, config.TimeoutDuration, "Timeout should be 2 minutes per requirements")
	assert.Equal(t, 4, config.ParallelWorkers, "Should have multiple workers")
	assert.True(t, config.EnableMetrics, "Metrics should be enabled by default")
	assert.False(t, config.DryRun, "Dry run should be false by default")
}

// TestPolicyBundle_Validation tests policy bundle structure validation
func TestPolicyBundle_Validation(t *testing.T) {
	t.Run("Valid Policy Bundle", func(t *testing.T) {
		bundle := PolicyBundle{
			Version: "1.0.0",
			AnomalyPolicies: []AnomalyPolicy{
				{
					PolicyID:      "test-policy",
					MetricName:    "cpu_usage",
					DetectionType: "zscore",
					Threshold:     2.0,
					WindowSize:    10,
					Sensitivity:   0.8,
					Enabled:       true,
				},
			},
			FilterRules: []FilterRule{
				{
					RuleID:     "test-rule",
					Name:       "Test Rule",
					EventTypes: []string{"anomaly"},
					Action:     "allow",
					Priority:   1,
					Enabled:    true,
				},
			},
			Thresholds: map[string]float64{
				"cpu_critical": 90.0,
			},
		}

		// Basic structure validation
		assert.NotEmpty(t, bundle.Version)
		assert.NotEmpty(t, bundle.AnomalyPolicies)
		assert.NotEmpty(t, bundle.FilterRules)
		assert.NotEmpty(t, bundle.Thresholds)

		// Policy validation
		policy := bundle.AnomalyPolicies[0]
		assert.NotEmpty(t, policy.PolicyID)
		assert.NotEmpty(t, policy.MetricName)
		assert.Greater(t, policy.Threshold, 0.0)
		assert.True(t, policy.Enabled)

		// Rule validation
		rule := bundle.FilterRules[0]
		assert.NotEmpty(t, rule.RuleID)
		assert.NotEmpty(t, rule.EventTypes)
		assert.Contains(t, []string{"allow", "deny", "modify", "flag"}, rule.Action)
		assert.True(t, rule.Enabled)
	})

	t.Run("Policy Conditions", func(t *testing.T) {
		conditions := []PolicyCondition{
			{Field: "device_type", Operator: "eq", Value: "server"},
			{Field: "severity", Operator: "in", Value: []interface{}{"high", "critical"}},
			{Field: "score", Operator: "gt", Value: 2.0},
		}

		for _, condition := range conditions {
			assert.NotEmpty(t, condition.Field)
			assert.NotEmpty(t, condition.Operator)
			assert.NotNil(t, condition.Value)
			assert.Contains(t, []string{"eq", "ne", "gt", "lt", "contains", "in"}, condition.Operator)
		}
	})
}

// TestPolicySandbox_EventFiltering tests event filtering logic
func TestPolicySandbox_EventFiltering(t *testing.T) {
	logger := zaptest.NewLogger(t)
	sandbox := NewPolicySandbox(logger, nil)

	// Create test events
	events := []map[string]interface{}{
		{
			"event_id":   "event-1",
			"event_type": "anomaly",
			"device_id":  "device-123",
			"severity":   "high",
			"payload":    map[string]interface{}{"metric": "cpu_usage", "value": 85.0},
		},
		{
			"event_id":   "event-2",
			"event_type": "browser",
			"device_id":  "device-456",
			"severity":   "low",
			"payload":    map[string]interface{}{"latency_ms": 1500.0},
		},
		{
			"event_id":   "event-3",
			"event_type": "anomaly",
			"device_id":  "test-device-789",
			"severity":   "critical",
			"payload":    map[string]interface{}{"metric": "memory_usage", "value": 95.0},
		},
	}

	// Create policy bundle with filter rules
	bundle := PolicyBundle{
		FilterRules: []FilterRule{
			{
				RuleID:     "allow-high-severity",
				EventTypes: []string{"anomaly", "browser"},
				Action:     "allow",
				Conditions: []PolicyCondition{
					{Field: "severity", Operator: "in", Value: []interface{}{"high", "critical"}},
				},
				Priority: 1,
				Enabled:  true,
			},
			{
				RuleID:     "deny-test-devices",
				EventTypes: []string{"anomaly"},
				Action:     "deny",
				Conditions: []PolicyCondition{
					{Field: "device_id", Operator: "contains", Value: "test-"},
				},
				Priority: 2,
				Enabled:  true,
			},
		},
	}

	// Apply policy bundle
	filteredEvents, policyEffects, err := sandbox.applyPolicyBundle(context.Background(), events, bundle)
	require.NoError(t, err)

	// Verify filtering results
	assert.Len(t, filteredEvents, 1, "Should have 1 event after filtering")
	assert.Equal(t, "event-1", filteredEvents[0]["event_id"], "Should keep high severity non-test event")

	// Verify policy effects
	assert.Len(t, policyEffects, 2, "Should have effects from both rules")

	// Find effects by policy ID
	var allowEffect, denyEffect *PolicyEffect
	for i := range policyEffects {
		if policyEffects[i].PolicyID == "allow-high-severity" {
			allowEffect = &policyEffects[i]
		} else if policyEffects[i].PolicyID == "deny-test-devices" {
			denyEffect = &policyEffects[i]
		}
	}

	require.NotNil(t, allowEffect, "Should have allow rule effect")
	require.NotNil(t, denyEffect, "Should have deny rule effect")

	assert.Equal(t, "allow", allowEffect.Action)
	assert.Equal(t, "deny", denyEffect.Action)
	assert.Greater(t, allowEffect.EventsAffected, 0)
	assert.Greater(t, denyEffect.EventsAffected, 0)
}

// TestPolicySandbox_ConditionEvaluation tests condition evaluation logic
func TestPolicySandbox_ConditionEvaluation(t *testing.T) {
	logger := zaptest.NewLogger(t)
	sandbox := NewPolicySandbox(logger, nil)

	event := map[string]interface{}{
		"event_id":   "test-event",
		"event_type": "anomaly",
		"device_id":  "device-123",
		"severity":   "high",
		"payload": map[string]interface{}{
			"metric": "cpu_usage",
			"value":  85.5,
			"score":  2.3,
		},
		"labels": map[string]interface{}{
			"environment": "production",
			"service":     "web-server",
		},
	}

	testCases := []struct {
		name      string
		condition PolicyCondition
		expected  bool
	}{
		{
			name:      "Equal condition - match",
			condition: PolicyCondition{Field: "severity", Operator: "eq", Value: "high"},
			expected:  true,
		},
		{
			name:      "Equal condition - no match",
			condition: PolicyCondition{Field: "severity", Operator: "eq", Value: "low"},
			expected:  false,
		},
		{
			name:      "Not equal condition - match",
			condition: PolicyCondition{Field: "severity", Operator: "ne", Value: "low"},
			expected:  true,
		},
		{
			name:      "Greater than condition - match",
			condition: PolicyCondition{Field: "value", Operator: "gt", Value: 80.0},
			expected:  true,
		},
		{
			name:      "Greater than condition - no match",
			condition: PolicyCondition{Field: "value", Operator: "gt", Value: 90.0},
			expected:  false,
		},
		{
			name:      "In condition - match",
			condition: PolicyCondition{Field: "severity", Operator: "in", Value: []interface{}{"high", "critical"}},
			expected:  true,
		},
		{
			name:      "In condition - no match",
			condition: PolicyCondition{Field: "severity", Operator: "in", Value: []interface{}{"low", "medium"}},
			expected:  false,
		},
		{
			name:      "Payload field access",
			condition: PolicyCondition{Field: "metric", Operator: "eq", Value: "cpu_usage"},
			expected:  true,
		},
		{
			name:      "Labels field access",
			condition: PolicyCondition{Field: "environment", Operator: "eq", Value: "production"},
			expected:  true,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			result := sandbox.evaluateCondition(event, tc.condition)
			assert.Equal(t, tc.expected, result, "Condition evaluation should match expected result")
		})
	}
}

// TestPolicySandbox_AnomalyDetection tests anomaly detection simulation
func TestPolicySandbox_AnomalyDetection(t *testing.T) {
	logger := zaptest.NewLogger(t)
	sandbox := NewPolicySandbox(logger, nil)

	// Create test metric events
	events := []map[string]interface{}{
		{
			"event_id":  "metric-1",
			"device_id": "device-123",
			"tenant_id": "tenant-1",
			"timestamp": time.Now(),
			"payload": map[string]interface{}{
				"metric": "cpu_usage",
				"value":  95.0,
				"score":  2.8,
			},
		},
		{
			"event_id":  "metric-2",
			"device_id": "device-123",
			"tenant_id": "tenant-1",
			"timestamp": time.Now(),
			"payload": map[string]interface{}{
				"metric": "memory_usage",
				"value":  88.0,
				"score":  1.5,
			},
		},
		{
			"event_id":  "metric-3",
			"device_id": "device-456",
			"tenant_id": "tenant-1",
			"timestamp": time.Now(),
			"payload": map[string]interface{}{
				"metric": "cpu_usage",
				"value":  45.0,
				"score":  0.8,
			},
		},
	}

	// Create anomaly policies
	policies := []AnomalyPolicy{
		{
			PolicyID:      "cpu-policy",
			MetricName:    "cpu_usage",
			DetectionType: "zscore",
			Threshold:     2.0,
			Enabled:       true,
		},
		{
			PolicyID:      "memory-policy",
			MetricName:    "memory_usage",
			DetectionType: "threshold",
			Threshold:     85.0,
			Enabled:       true,
		},
	}

	// Run anomaly detection simulation
	anomalies, err := sandbox.simulateAnomalyDetection(context.Background(), events, policies)
	require.NoError(t, err)

	// Verify results
	assert.Len(t, anomalies, 2, "Should detect 2 anomalies")

	// Check that anomalies have correct structure
	for _, anomaly := range anomalies {
		assert.Contains(t, anomaly, "event_id")
		assert.Contains(t, anomaly, "device_id")
		assert.Contains(t, anomaly, "timestamp")
		assert.Equal(t, "anomaly", anomaly["event_type"])
		assert.Equal(t, "simulation", anomaly["source_type"])

		payload, ok := anomaly["payload"].(map[string]interface{})
		require.True(t, ok, "Payload should be a map")
		assert.Contains(t, payload, "metric")
		assert.Contains(t, payload, "policy_id")
		assert.Equal(t, true, payload["simulated"])

		labels, ok := anomaly["labels"].(map[string]interface{})
		require.True(t, ok, "Labels should be a map")
		assert.Equal(t, "true", labels["simulation"])
	}
}

// TestPolicySandbox_PerformanceMetrics tests performance metric calculations
func TestPolicySandbox_PerformanceMetrics(t *testing.T) {
	logger := zaptest.NewLogger(t)
	sandbox := NewPolicySandbox(logger, nil)

	startTime := time.Now().Add(-1 * time.Minute)
	eventCount := 50000

	metrics := sandbox.calculatePerformanceMetrics(startTime, eventCount)

	assert.Greater(t, metrics.EventsPerSecond, 0.0, "Events per second should be positive")
	assert.Greater(t, metrics.AvgProcessingTimeMs, 0.0, "Average processing time should be positive")
	assert.Greater(t, metrics.PeakMemoryUsageMB, 0.0, "Memory usage should be positive")
	assert.GreaterOrEqual(t, metrics.DatabaseQueries, 0, "Database queries should be non-negative")
	assert.GreaterOrEqual(t, metrics.QueryTimeMs, 0.0, "Query time should be non-negative")
	assert.GreaterOrEqual(t, metrics.WorkerUtilization, 0.0, "Worker utilization should be non-negative")
	assert.LessOrEqual(t, metrics.WorkerUtilization, 1.0, "Worker utilization should not exceed 100%")

	// Test that metrics are reasonable for the given input
	expectedEPS := float64(eventCount) / 60.0 // 60 seconds
	assert.InDelta(t, expectedEPS, metrics.EventsPerSecond, expectedEPS*0.1, "Events per second should be close to expected")
}

// TestPolicySandbox_AccuracyMetrics tests accuracy calculation
func TestPolicySandbox_AccuracyMetrics(t *testing.T) {
	logger := zaptest.NewLogger(t)
	sandbox := NewPolicySandbox(logger, nil)

	testCases := []struct {
		name              string
		baselineCount     int
		simulatedCount    int
		expectedPrecision float64
		expectedRecall    float64
		expectedF1        float64
	}{
		{
			name:              "Perfect match",
			baselineCount:     10,
			simulatedCount:    10,
			expectedPrecision: 1.0,
			expectedRecall:    1.0,
			expectedF1:        1.0,
		},
		{
			name:              "Over-detection",
			baselineCount:     10,
			simulatedCount:    15,
			expectedPrecision: 0.667, // 10/15
			expectedRecall:    1.0,   // 10/10
			expectedF1:        0.8,   // 2 * (0.667 * 1.0) / (0.667 + 1.0)
		},
		{
			name:              "Under-detection",
			baselineCount:     10,
			simulatedCount:    7,
			expectedPrecision: 1.0,   // 7/7
			expectedRecall:    0.7,   // 7/10
			expectedF1:        0.824, // 2 * (1.0 * 0.7) / (1.0 + 0.7)
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			// Create mock data
			baseline := make([]map[string]interface{}, tc.baselineCount)
			simulated := make([]map[string]interface{}, tc.simulatedCount)

			metrics := sandbox.calculateAccuracyMetrics(baseline, simulated)

			assert.InDelta(t, tc.expectedPrecision, metrics.Precision, 0.01, "Precision should match expected")
			assert.InDelta(t, tc.expectedRecall, metrics.Recall, 0.01, "Recall should match expected")
			assert.InDelta(t, tc.expectedF1, metrics.F1Score, 0.01, "F1 score should match expected")

			// Verify that TP + FP + FN equals total predicted and actual
			assert.Equal(t, tc.simulatedCount, metrics.TruePositives+metrics.FalsePositives, "TP + FP should equal simulated count")
			assert.Equal(t, tc.baselineCount, metrics.TruePositives+metrics.FalseNegatives, "TP + FN should equal baseline count")
		})
	}
}

// TestSimulationRequest_Validation tests simulation request validation
func TestSimulationRequest_Validation(t *testing.T) {
	baseTime := time.Now()

	t.Run("Valid Request", func(t *testing.T) {
		request := &SimulationRequest{
			SimulationID: "test-sim-123",
			Name:         "Test Simulation",
			Description:  "Testing policy changes",
			TimeRange: TimeRange{
				StartTime: baseTime.Add(-1 * time.Hour),
				EndTime:   baseTime,
			},
			SimulationConfig: DefaultSimulationConfig(),
			RequestedBy:      "test-user",
			RequestedAt:      baseTime,
		}

		// Basic validation
		assert.NotEmpty(t, request.SimulationID)
		assert.NotEmpty(t, request.Name)
		assert.False(t, request.TimeRange.StartTime.IsZero())
		assert.False(t, request.TimeRange.EndTime.IsZero())
		assert.True(t, request.TimeRange.EndTime.After(request.TimeRange.StartTime))
		assert.NotEmpty(t, request.RequestedBy)
	})

	t.Run("Invalid Time Range", func(t *testing.T) {
		request := &SimulationRequest{
			TimeRange: TimeRange{
				StartTime: baseTime,
				EndTime:   baseTime.Add(-1 * time.Hour), // End before start
			},
		}

		assert.True(t, request.TimeRange.StartTime.After(request.TimeRange.EndTime), "End time should be before start time")
	})
}

// TestEventFilters tests event filtering configuration
func TestEventFilters(t *testing.T) {
	filters := EventFilters{
		SourceTypes: []string{"agent", "extension"},
		EventTypes:  []string{"anomaly", "browser"},
		DeviceIDs:   []string{"device-1", "device-2"},
		TenantIDs:   []string{"tenant-1"},
		MinSeverity: "medium",
	}

	assert.NotEmpty(t, filters.SourceTypes)
	assert.NotEmpty(t, filters.EventTypes)
	assert.Contains(t, filters.SourceTypes, "agent")
	assert.Contains(t, filters.EventTypes, "anomaly")
	assert.NotEmpty(t, filters.MinSeverity)
}

// BenchmarkPolicyEvaluation benchmarks policy condition evaluation
func BenchmarkPolicyEvaluation(b *testing.B) {
	logger := zaptest.NewLogger(b)
	sandbox := NewPolicySandbox(logger, nil)

	event := map[string]interface{}{
		"event_id":  "bench-event",
		"severity":  "high",
		"device_id": "device-123",
		"payload": map[string]interface{}{
			"metric": "cpu_usage",
			"value":  85.0,
		},
	}

	condition := PolicyCondition{
		Field:    "severity",
		Operator: "eq",
		Value:    "high",
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		sandbox.evaluateCondition(event, condition)
	}
}
