// Package forecast provides KPI forecasting capabilities
// Implements Capability 4: KPI Forecasting Dashboard from Horizontal Slice AI Plan #4
package forecast

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strconv"
	"time"
)

// KPIType represents different types of KPIs that can be forecasted
type KPIType string

const (
	ErrorRate      KPIType = "error_rate"
	ActionSuccess  KPIType = "action_success"
	IngestLatency  KPIType = "ingest_latency"
	CPUUtilization KPIType = "cpu_utilization"
	MemoryUsage    KPIType = "memory_usage"
	RequestRate    KPIType = "request_rate"
	ResponseTime   KPIType = "response_time"
)

// KPIForecast represents a forecast for a specific KPI
type KPIForecast struct {
	KPI           KPIType         `json:"kpi"`
	Current       float64         `json:"current"`
	Forecasts     []ForecastPoint `json:"forecasts"`
	Confidence    float64         `json:"confidence"`
	Trend         string          `json:"trend"` // "increasing", "decreasing", "stable"
	Alerts        []KPIAlert      `json:"alerts"`
	LastUpdated   time.Time       `json:"last_updated"`
	ModelAccuracy float64         `json:"model_accuracy"` // MAPE
}

// ForecastPoint represents a single forecast data point
type ForecastPoint struct {
	Timestamp  time.Time `json:"timestamp"`
	Value      float64   `json:"value"`
	Lower      float64   `json:"lower_bound"`
	Upper      float64   `json:"upper_bound"`
	Confidence float64   `json:"confidence"`
}

// KPIAlert represents a forecasted threshold violation
type KPIAlert struct {
	Severity      string    `json:"severity"` // "low", "medium", "high", "critical"
	Message       string    `json:"message"`
	Timestamp     time.Time `json:"timestamp"`
	Threshold     float64   `json:"threshold"`
	ForecastValue float64   `json:"forecast_value"`
	Action        string    `json:"recommended_action"`
}

// KPIThreshold represents alerting thresholds for a KPI
type KPIThreshold struct {
	KPI       KPIType `json:"kpi"`
	Warning   float64 `json:"warning"`
	Critical  float64 `json:"critical"`
	Direction string  `json:"direction"` // "above", "below"
}

// KPIForecaster provides forecasting capabilities for various KPIs
type KPIForecaster struct {
	thresholds   map[KPIType]KPIThreshold
	models       map[KPIType]ForecastModel
	dataProvider KPIDataProvider
}

// ForecastModel interface for different forecasting algorithms
type ForecastModel interface {
	Forecast(historical []DataPoint, horizonHours int) ([]ForecastPoint, error)
	GetAccuracy() float64
}

// DataPoint represents a historical data point
type DataPoint struct {
	Timestamp time.Time `json:"timestamp"`
	Value     float64   `json:"value"`
}

// KPIDataProvider interface for fetching KPI data
type KPIDataProvider interface {
	GetHistoricalData(kpi KPIType, hours int) ([]DataPoint, error)
	GetCurrentValue(kpi KPIType) (float64, error)
}

// NewKPIForecaster creates a new KPI forecaster
func NewKPIForecaster(dataProvider KPIDataProvider) *KPIForecaster {
	forecaster := &KPIForecaster{
		thresholds:   make(map[KPIType]KPIThreshold),
		models:       make(map[KPIType]ForecastModel),
		dataProvider: dataProvider,
	}

	// Initialize default thresholds
	forecaster.setDefaultThresholds()

	// Initialize forecasting models
	forecaster.initializeModels()

	return forecaster
}

// setDefaultThresholds sets up default alerting thresholds for each KPI
func (kf *KPIForecaster) setDefaultThresholds() {
	kf.thresholds[ErrorRate] = KPIThreshold{
		KPI: ErrorRate, Warning: 5.0, Critical: 10.0, Direction: "above",
	}
	kf.thresholds[ActionSuccess] = KPIThreshold{
		KPI: ActionSuccess, Warning: 90.0, Critical: 80.0, Direction: "below",
	}
	kf.thresholds[IngestLatency] = KPIThreshold{
		KPI: IngestLatency, Warning: 100.0, Critical: 500.0, Direction: "above",
	}
	kf.thresholds[CPUUtilization] = KPIThreshold{
		KPI: CPUUtilization, Warning: 70.0, Critical: 85.0, Direction: "above",
	}
	kf.thresholds[MemoryUsage] = KPIThreshold{
		KPI: MemoryUsage, Warning: 75.0, Critical: 90.0, Direction: "above",
	}
	kf.thresholds[RequestRate] = KPIThreshold{
		KPI: RequestRate, Warning: 1000.0, Critical: 2000.0, Direction: "above",
	}
	kf.thresholds[ResponseTime] = KPIThreshold{
		KPI: ResponseTime, Warning: 200.0, Critical: 1000.0, Direction: "above",
	}
}

// initializeModels sets up forecasting models for each KPI
func (kf *KPIForecaster) initializeModels() {
	// Use exponential smoothing for most KPIs
	for _, kpiType := range []KPIType{ErrorRate, ActionSuccess, IngestLatency, CPUUtilization, MemoryUsage, RequestRate, ResponseTime} {
		kf.models[kpiType] = NewExponentialSmoothingModel()
	}
}

// ForecastKPI generates a forecast for a specific KPI
func (kf *KPIForecaster) ForecastKPI(ctx context.Context, kpi KPIType, horizonHours int) (*KPIForecast, error) {
	// Get historical data (last 7 days for context)
	historical, err := kf.dataProvider.GetHistoricalData(kpi, 24*7)
	if err != nil {
		return nil, fmt.Errorf("failed to get historical data: %w", err)
	}

	if len(historical) == 0 {
		return nil, fmt.Errorf("no historical data available for KPI %s", kpi)
	}

	// Get current value
	current, err := kf.dataProvider.GetCurrentValue(kpi)
	if err != nil {
		return nil, fmt.Errorf("failed to get current value: %w", err)
	}

	// Generate forecast
	model := kf.models[kpi]
	forecasts, err := model.Forecast(historical, horizonHours)
	if err != nil {
		return nil, fmt.Errorf("failed to generate forecast: %w", err)
	}

	// Analyze trend
	trend := kf.analyzeTrend(historical, forecasts)

	// Generate alerts
	alerts := kf.generateAlerts(kpi, forecasts)

	// Calculate overall confidence
	confidence := kf.calculateConfidence(kpi, historical, forecasts)

	return &KPIForecast{
		KPI:           kpi,
		Current:       current,
		Forecasts:     forecasts,
		Confidence:    confidence,
		Trend:         trend,
		Alerts:        alerts,
		LastUpdated:   time.Now(),
		ModelAccuracy: model.GetAccuracy(),
	}, nil
}

// analyzeTrend determines if the KPI is trending up, down, or stable
func (kf *KPIForecaster) analyzeTrend(historical []DataPoint, forecasts []ForecastPoint) string {
	if len(historical) < 2 || len(forecasts) < 2 {
		return "stable"
	}

	// Compare recent historical trend with forecast trend
	recentHistorical := historical[len(historical)-5:] // Last 5 points
	if len(recentHistorical) < 2 {
		recentHistorical = historical
	}

	// Calculate historical slope
	historicalSlope := calculateSlope(recentHistorical)

	// Calculate forecast slope
	forecastSlope := 0.0
	if len(forecasts) >= 2 {
		first := forecasts[0]
		last := forecasts[len(forecasts)-1]
		timeDiff := last.Timestamp.Sub(first.Timestamp).Hours()
		if timeDiff > 0 {
			forecastSlope = (last.Value - first.Value) / timeDiff
		}
	}

	// Combine historical and forecast trends
	avgSlope := (historicalSlope + forecastSlope) / 2

	if avgSlope > 0.1 {
		return "increasing"
	} else if avgSlope < -0.1 {
		return "decreasing"
	}
	return "stable"
}

// generateAlerts creates alerts for forecasted threshold violations
func (kf *KPIForecaster) generateAlerts(kpi KPIType, forecasts []ForecastPoint) []KPIAlert {
	var alerts []KPIAlert

	threshold, exists := kf.thresholds[kpi]
	if !exists {
		return alerts
	}

	for _, forecast := range forecasts {
		var severity string
		var violated bool
		var thresholdValue float64

		if threshold.Direction == "above" {
			if forecast.Value >= threshold.Critical {
				severity = "critical"
				violated = true
				thresholdValue = threshold.Critical
			} else if forecast.Value >= threshold.Warning {
				severity = "warning"
				violated = true
				thresholdValue = threshold.Warning
			}
		} else { // below
			if forecast.Value <= threshold.Critical {
				severity = "critical"
				violated = true
				thresholdValue = threshold.Critical
			} else if forecast.Value <= threshold.Warning {
				severity = "warning"
				violated = true
				thresholdValue = threshold.Warning
			}
		}

		if violated {
			alert := KPIAlert{
				Severity:      severity,
				Message:       generateAlertMessage(kpi, severity, forecast.Value, thresholdValue),
				Timestamp:     forecast.Timestamp,
				Threshold:     thresholdValue,
				ForecastValue: forecast.Value,
				Action:        generateRecommendedAction(kpi, severity),
			}
			alerts = append(alerts, alert)
		}
	}

	return alerts
}

// calculateConfidence calculates overall forecast confidence
func (kf *KPIForecaster) calculateConfidence(kpi KPIType, historical []DataPoint, forecasts []ForecastPoint) float64 {
	if len(historical) < 5 {
		return 0.5 // Low confidence with little data
	}

	// Base confidence on data quality and model accuracy
	model := kf.models[kpi] // Use the KPI type as the key
	modelAccuracy := 0.8    // Default if model accuracy not available
	if model != nil {
		modelAccuracy = model.GetAccuracy()
	}

	// Adjust confidence based on data variability
	variance := calculateVariance(historical)
	variabilityFactor := 1.0 / (1.0 + variance/100.0) // Higher variance = lower confidence

	// Adjust confidence based on forecast horizon
	horizonFactor := 1.0
	if len(forecasts) > 0 {
		hours := time.Until(forecasts[len(forecasts)-1].Timestamp).Hours()
		horizonFactor = math.Max(0.3, 1.0-hours/168.0) // Confidence decreases over week
	}

	return modelAccuracy * variabilityFactor * horizonFactor
}

// HTTPHandler handles HTTP requests for KPI forecasts
func (kf *KPIForecaster) HTTPHandler(w http.ResponseWriter, r *http.Request) {
	// Extract KPI name from URL
	kpiName := r.URL.Query().Get("kpi")
	if kpiName == "" {
		http.Error(w, "Missing KPI parameter", http.StatusBadRequest)
		return
	}

	kpi := KPIType(kpiName)
	if !kf.isValidKPI(kpi) {
		http.Error(w, "Invalid KPI type", http.StatusBadRequest)
		return
	}

	// Extract horizon (default to 24 hours)
	horizonStr := r.URL.Query().Get("horizon")
	horizon := 24
	if horizonStr != "" {
		if h, err := strconv.Atoi(horizonStr); err == nil && h > 0 && h <= 168 {
			horizon = h
		}
	}

	// Generate forecast
	forecast, err := kf.ForecastKPI(r.Context(), kpi, horizon)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to generate forecast: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(forecast); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
		return
	}
}

// isValidKPI checks if the given KPI type is supported
func (kf *KPIForecaster) isValidKPI(kpi KPIType) bool {
	validKPIs := []KPIType{ErrorRate, ActionSuccess, IngestLatency, CPUUtilization, MemoryUsage, RequestRate, ResponseTime}
	for _, valid := range validKPIs {
		if kpi == valid {
			return true
		}
	}
	return false
}

// Helper functions

// calculateSlope calculates the slope of a time series
func calculateSlope(points []DataPoint) float64 {
	if len(points) < 2 {
		return 0
	}

	n := float64(len(points))
	var sumX, sumY, sumXY, sumX2 float64

	for i, point := range points {
		x := float64(i)
		y := point.Value
		sumX += x
		sumY += y
		sumXY += x * y
		sumX2 += x * x
	}

	denominator := n*sumX2 - sumX*sumX
	if denominator == 0 {
		return 0
	}

	return (n*sumXY - sumX*sumY) / denominator
}

// calculateVariance calculates the variance of historical data
func calculateVariance(points []DataPoint) float64 {
	if len(points) < 2 {
		return 0
	}

	// Calculate mean
	var sum float64
	for _, point := range points {
		sum += point.Value
	}
	mean := sum / float64(len(points))

	// Calculate variance
	var variance float64
	for _, point := range points {
		diff := point.Value - mean
		variance += diff * diff
	}

	return variance / float64(len(points)-1)
}

// generateAlertMessage creates a human-readable alert message
func generateAlertMessage(kpi KPIType, severity string, forecastValue, threshold float64) string {
	return fmt.Sprintf("%s %s forecast to %s %.2f (threshold: %.2f)",
		string(kpi), severity,
		map[string]string{"above": "reach", "below": "drop to"}["above"],
		forecastValue, threshold)
}

// generateRecommendedAction suggests actions for alerts
func generateRecommendedAction(kpi KPIType, severity string) string {
	actions := map[KPIType]map[string]string{
		ErrorRate: {
			"warning":  "Review recent deployments and error logs",
			"critical": "Prepare rollback plan and investigate error sources",
		},
		ActionSuccess: {
			"warning":  "Check automation pipeline health",
			"critical": "Switch to manual processes and debug automation",
		},
		IngestLatency: {
			"warning":  "Monitor ingest pipeline performance",
			"critical": "Scale ingest capacity or implement backpressure",
		},
		CPUUtilization: {
			"warning":  "Prepare to scale compute resources",
			"critical": "Scale immediately or redistribute load",
		},
		MemoryUsage: {
			"warning":  "Monitor for memory leaks and prepare to scale",
			"critical": "Scale memory or restart high-usage services",
		},
	}

	if kpiActions, exists := actions[kpi]; exists {
		if action, exists := kpiActions[severity]; exists {
			return action
		}
	}

	return "Monitor closely and prepare scaling actions"
}
