package forecast

import (
	"fmt"
	"math"
	"time"
)

// ExponentialSmoothingModel implements exponential smoothing for forecasting
type ExponentialSmoothingModel struct {
	alpha    float64 // Smoothing parameter for level
	beta     float64 // Smoothing parameter for trend
	gamma    float64 // Smoothing parameter for seasonality
	accuracy float64 // Model accuracy (MAPE)
}

// NewExponentialSmoothingModel creates a new exponential smoothing model
func NewExponentialSmoothingModel() *ExponentialSmoothingModel {
	return &ExponentialSmoothingModel{
		alpha:    0.3,  // Level smoothing
		beta:     0.1,  // Trend smoothing
		gamma:    0.1,  // Seasonal smoothing
		accuracy: 0.85, // Default accuracy
	}
}

// Forecast generates forecasts using exponential smoothing
func (esm *ExponentialSmoothingModel) Forecast(historical []DataPoint, horizonHours int) ([]ForecastPoint, error) {
	if len(historical) < 3 {
		return nil, fmt.Errorf("insufficient data: need at least 3 points, got %d", len(historical))
	}

	// Initialize components
	level, trend := esm.initializeComponents(historical)
	seasonality := esm.initializeSeasonality(historical)

	// Apply exponential smoothing to historical data
	for i := 1; i < len(historical); i++ {
		level, trend = esm.updateComponents(historical[i].Value, level, trend, seasonality, i)
	}

	// Generate forecasts
	forecasts := make([]ForecastPoint, horizonHours)
	lastTimestamp := historical[len(historical)-1].Timestamp

	for h := 1; h <= horizonHours; h++ {
		// Calculate forecast
		forecastValue := level + float64(h)*trend

		// Add seasonal component if applicable
		seasonalIndex := (len(historical) + h - 1) % 24 // Daily seasonality
		if seasonalIndex < len(seasonality) {
			forecastValue += seasonality[seasonalIndex]
		}

		// Calculate prediction intervals
		confidence := esm.calculateTimeDecayConfidence(h)
		errorMargin := esm.calculateErrorMargin(historical, h)

		forecasts[h-1] = ForecastPoint{
			Timestamp:  lastTimestamp.Add(time.Duration(h) * time.Hour),
			Value:      math.Max(0, forecastValue), // Ensure non-negative
			Lower:      math.Max(0, forecastValue-errorMargin),
			Upper:      forecastValue + errorMargin,
			Confidence: confidence,
		}
	}

	return forecasts, nil
}

// GetAccuracy returns the model's accuracy
func (esm *ExponentialSmoothingModel) GetAccuracy() float64 {
	return esm.accuracy
}

// initializeComponents initializes level and trend components
func (esm *ExponentialSmoothingModel) initializeComponents(data []DataPoint) (float64, float64) {
	if len(data) < 2 {
		return data[0].Value, 0
	}

	// Simple initialization
	level := data[0].Value
	trend := data[1].Value - data[0].Value

	return level, trend
}

// initializeSeasonality initializes seasonal components
func (esm *ExponentialSmoothingModel) initializeSeasonality(data []DataPoint) []float64 {
	// Simple daily seasonality (24 hours)
	seasonality := make([]float64, 24)

	if len(data) < 24 {
		return seasonality // All zeros
	}

	// Calculate average for each hour of day
	hourSums := make([]float64, 24)
	hourCounts := make([]int, 24)

	for _, point := range data {
		hour := point.Timestamp.Hour()
		hourSums[hour] += point.Value
		hourCounts[hour]++
	}

	// Calculate seasonal indices
	var overallMean float64
	validHours := 0
	for i := 0; i < 24; i++ {
		if hourCounts[i] > 0 {
			seasonality[i] = hourSums[i] / float64(hourCounts[i])
			overallMean += seasonality[i]
			validHours++
		}
	}

	if validHours > 0 {
		overallMean /= float64(validHours)

		// Convert to seasonal indices (deviations from mean)
		for i := 0; i < 24; i++ {
			if hourCounts[i] > 0 {
				seasonality[i] -= overallMean
			}
		}
	}

	return seasonality
}

// updateComponents updates level and trend using exponential smoothing
func (esm *ExponentialSmoothingModel) updateComponents(
	observed, level, trend float64,
	seasonality []float64,
	index int,
) (float64, float64) {
	// Remove seasonal component from observed value
	seasonalIndex := (index - 1) % len(seasonality)
	deseasonalized := observed
	if seasonalIndex >= 0 && seasonalIndex < len(seasonality) {
		deseasonalized = observed - seasonality[seasonalIndex]
	}

	// Update level
	newLevel := esm.alpha*deseasonalized + (1-esm.alpha)*(level+trend)

	// Update trend
	newTrend := esm.beta*(newLevel-level) + (1-esm.beta)*trend

	return newLevel, newTrend
}

// calculateTimeDecayConfidence calculates confidence that decreases over time
func (esm *ExponentialSmoothingModel) calculateTimeDecayConfidence(horizonHours int) float64 {
	// Confidence decreases exponentially with forecast horizon
	baseConfidence := esm.accuracy
	decayRate := 0.02 // 2% decay per hour

	confidence := baseConfidence * math.Exp(-decayRate*float64(horizonHours))
	return math.Max(0.1, math.Min(1.0, confidence))
}

// calculateErrorMargin calculates prediction interval margins
func (esm *ExponentialSmoothingModel) calculateErrorMargin(historical []DataPoint, horizonHours int) float64 {
	if len(historical) < 2 {
		return 0
	}

	// Calculate historical error variance
	var errorSum float64
	for i := 1; i < len(historical); i++ {
		// Simple one-step-ahead error approximation
		predicted := historical[i-1].Value
		actual := historical[i].Value
		error := math.Abs(actual - predicted)
		errorSum += error * error
	}

	variance := errorSum / float64(len(historical)-1)
	stdError := math.Sqrt(variance)

	// Error margin increases with forecast horizon
	horizonMultiplier := 1.0 + 0.1*float64(horizonHours)

	// 95% confidence interval (approximately 2 standard deviations)
	return 2.0 * stdError * horizonMultiplier
}

// MockKPIDataProvider provides mock data for testing
type MockKPIDataProvider struct {
	data map[KPIType][]DataPoint
}

// NewMockKPIDataProvider creates a new mock data provider
func NewMockKPIDataProvider() *MockKPIDataProvider {
	provider := &MockKPIDataProvider{
		data: make(map[KPIType][]DataPoint),
	}

	// Generate mock historical data
	provider.generateMockData()

	return provider
}

// GetHistoricalData returns mock historical data
func (m *MockKPIDataProvider) GetHistoricalData(kpi KPIType, hours int) ([]DataPoint, error) {
	data, exists := m.data[kpi]
	if !exists {
		return nil, fmt.Errorf("no data available for KPI %s", kpi)
	}

	// Return last N hours of data
	if hours >= len(data) {
		return data, nil
	}

	return data[len(data)-hours:], nil
}

// GetCurrentValue returns mock current value
func (m *MockKPIDataProvider) GetCurrentValue(kpi KPIType) (float64, error) {
	data, exists := m.data[kpi]
	if !exists || len(data) == 0 {
		return 0, fmt.Errorf("no current data available for KPI %s", kpi)
	}

	return data[len(data)-1].Value, nil
}

// generateMockData generates realistic mock data for each KPI
func (m *MockKPIDataProvider) generateMockData() {
	now := time.Now()

	// Generate 7 days of hourly data for each KPI
	for _, kpi := range []KPIType{ErrorRate, ActionSuccess, IngestLatency, CPUUtilization, MemoryUsage, RequestRate, ResponseTime} {
		var points []DataPoint

		for h := -168; h <= 0; h++ { // 7 days = 168 hours
			timestamp := now.Add(time.Duration(h) * time.Hour)
			value := m.generateKPIValue(kpi, h, timestamp)

			points = append(points, DataPoint{
				Timestamp: timestamp,
				Value:     value,
			})
		}

		m.data[kpi] = points
	}
}

// generateKPIValue generates a realistic value for a specific KPI
func (m *MockKPIDataProvider) generateKPIValue(kpi KPIType, hourOffset int, timestamp time.Time) float64 {
	// Add daily seasonality based on hour of day
	hour := timestamp.Hour()
	dailyPattern := math.Sin(2*math.Pi*float64(hour)/24.0) * 0.3

	// Add weekly seasonality
	weekday := timestamp.Weekday()
	weeklyPattern := 0.0
	if weekday == time.Saturday || weekday == time.Sunday {
		weeklyPattern = -0.2 // Lower activity on weekends
	}

	// Add some trend and noise
	trend := float64(hourOffset) * 0.001
	noise := (math.Sin(float64(hourOffset)*0.1) + math.Cos(float64(hourOffset)*0.07)) * 0.1

	var base float64
	switch kpi {
	case ErrorRate:
		base = 2.5 + dailyPattern*1.0 + weeklyPattern*0.5 + trend + noise
		return math.Max(0, base)
	case ActionSuccess:
		base = 95.0 + dailyPattern*3.0 + weeklyPattern*2.0 - trend + noise
		return math.Min(100, math.Max(0, base))
	case IngestLatency:
		base = 45.0 + dailyPattern*15.0 + weeklyPattern*10.0 + trend*10 + noise*5
		return math.Max(0, base)
	case CPUUtilization:
		base = 35.0 + dailyPattern*20.0 + weeklyPattern*10.0 + trend*5 + noise*3
		return math.Min(100, math.Max(0, base))
	case MemoryUsage:
		base = 60.0 + dailyPattern*15.0 + weeklyPattern*8.0 + trend*3 + noise*2
		return math.Min(100, math.Max(0, base))
	case RequestRate:
		base = 500.0 + dailyPattern*200.0 + weeklyPattern*100.0 + trend*20 + noise*25
		return math.Max(0, base)
	case ResponseTime:
		base = 150.0 + dailyPattern*50.0 + weeklyPattern*30.0 + trend*8 + noise*10
		return math.Max(0, base)
	default:
		return 50.0 + dailyPattern*10.0 + noise*5
	}
}
