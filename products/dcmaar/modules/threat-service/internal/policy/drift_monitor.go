package policy

import (
	"context"
	"fmt"
	"math"
	"sort"
	"sync"
	"time"

	"go.uber.org/zap"
)

// DriftMonitor monitors policy performance and detects drift over time
type DriftMonitor struct {
	logger  *zap.Logger
	storage DriftStorage
	config  DriftConfig
	metrics *DriftMetrics
	cache   *DriftCache
	mu      sync.RWMutex
	stopCh  chan struct{}
	running bool
}

// DriftConfig configures drift monitoring behavior
type DriftConfig struct {
	// Monitoring intervals
	CheckInterval    time.Duration `json:"check_interval" yaml:"check_interval"`
	WindowSize       time.Duration `json:"window_size" yaml:"window_size"`
	HistoryRetention time.Duration `json:"history_retention" yaml:"history_retention"`

	// Drift detection thresholds
	AccuracyThreshold     float64 `json:"accuracy_threshold" yaml:"accuracy_threshold"`
	PrecisionThreshold    float64 `json:"precision_threshold" yaml:"precision_threshold"`
	RecallThreshold       float64 `json:"recall_threshold" yaml:"recall_threshold"`
	F1Threshold           float64 `json:"f1_threshold" yaml:"f1_threshold"`
	VolumeChangeThreshold float64 `json:"volume_change_threshold" yaml:"volume_change_threshold"`

	// Pattern detection
	SeasonalityWindow int     `json:"seasonality_window" yaml:"seasonality_window"`
	TrendSensitivity  float64 `json:"trend_sensitivity" yaml:"trend_sensitivity"`
	NoveltyThreshold  float64 `json:"novelty_threshold" yaml:"novelty_threshold"`

	// Alert configuration
	AlertThreshold    float64       `json:"alert_threshold" yaml:"alert_threshold"`
	AlertCooldown     time.Duration `json:"alert_cooldown" yaml:"alert_cooldown"`
	EnableSlackAlerts bool          `json:"enable_slack_alerts" yaml:"enable_slack_alerts"`
	EnableEmailAlerts bool          `json:"enable_email_alerts" yaml:"enable_email_alerts"`

	// Performance settings
	MaxConcurrentChecks      int  `json:"max_concurrent_checks" yaml:"max_concurrent_checks"`
	BatchSize                int  `json:"batch_size" yaml:"batch_size"`
	EnableAdaptiveThresholds bool `json:"enable_adaptive_thresholds" yaml:"enable_adaptive_thresholds"`
}

// DriftMetrics tracks drift monitoring performance
type DriftMetrics struct {
	ChecksPerformed        int64     `json:"checks_performed"`
	DriftDetections        int64     `json:"drift_detections"`
	FalsePositives         int64     `json:"false_positives"`
	AlertsSent             int64     `json:"alerts_sent"`
	AvgCheckDuration       float64   `json:"avg_check_duration_ms"`
	LastCheckTime          time.Time `json:"last_check_time"`
	HealthScore            float64   `json:"health_score"`
	UpgradeRecommendations []string  `json:"upgrade_recommendations"`
}

// DriftCache provides fast access to recent drift data
type DriftCache struct {
	policyMetrics map[string]*PolicyDriftMetrics
	recentAlerts  []DriftAlert
	baselineStats map[string]*BaselineStats
	mu            sync.RWMutex
	lastUpdate    time.Time
	cacheSize     int
}

// PolicyDriftMetrics tracks drift for a specific policy
type PolicyDriftMetrics struct {
	PolicyID      string    `json:"policy_id"`
	PolicyVersion string    `json:"policy_version"`
	LastUpdated   time.Time `json:"last_updated"`

	// Performance metrics over time
	AccuracyTrend  []TimeSeriesPoint `json:"accuracy_trend"`
	PrecisionTrend []TimeSeriesPoint `json:"precision_trend"`
	RecallTrend    []TimeSeriesPoint `json:"recall_trend"`
	F1Trend        []TimeSeriesPoint `json:"f1_trend"`
	VolumeTrend    []TimeSeriesPoint `json:"volume_trend"`

	// Current state
	CurrentAccuracy  float64 `json:"current_accuracy"`
	CurrentPrecision float64 `json:"current_precision"`
	CurrentRecall    float64 `json:"current_recall"`
	CurrentF1        float64 `json:"current_f1"`
	CurrentVolume    int64   `json:"current_volume"`

	// Drift indicators
	DriftScore    float64     `json:"drift_score"`
	DriftStatus   DriftStatus `json:"drift_status"`
	DriftReasons  []string    `json:"drift_reasons"`
	LastDriftTime *time.Time  `json:"last_drift_time"`

	// Recommendations
	RecommendedActions []RecommendedAction `json:"recommended_actions"`
	NextReviewTime     time.Time           `json:"next_review_time"`

	// Pattern analysis
	SeasonalPattern []float64      `json:"seasonal_pattern"`
	TrendAnalysis   TrendAnalysis  `json:"trend_analysis"`
	NoveltyEvents   []NoveltyEvent `json:"novelty_events"`
}

// TimeSeriesPoint represents a single data point in time series
type TimeSeriesPoint struct {
	Timestamp time.Time              `json:"timestamp"`
	Value     float64                `json:"value"`
	Metadata  map[string]interface{} `json:"metadata,omitempty"`
}

// DriftStatus indicates the current drift state
type DriftStatus string

const (
	DriftStatusHealthy  DriftStatus = "healthy"
	DriftStatusWarning  DriftStatus = "warning"
	DriftStatusCritical DriftStatus = "critical"
	DriftStatusUnknown  DriftStatus = "unknown"
)

// RecommendedAction suggests policy improvements
type RecommendedAction struct {
	ActionType   string    `json:"action_type"`
	Priority     string    `json:"priority"`
	Description  string    `json:"description"`
	Impact       string    `json:"impact"`
	Effort       string    `json:"effort"`
	DeadlineBy   time.Time `json:"deadline_by"`
	AutoApproved bool      `json:"auto_approved"`
}

// TrendAnalysis provides trend insights
type TrendAnalysis struct {
	Direction   string            `json:"direction"` // "improving", "declining", "stable"
	Slope       float64           `json:"slope"`
	Confidence  float64           `json:"confidence"`
	Seasonality bool              `json:"seasonality"`
	Volatility  float64           `json:"volatility"`
	Forecast    []TimeSeriesPoint `json:"forecast"`
}

// NoveltyEvent represents unusual patterns
type NoveltyEvent struct {
	Timestamp       time.Time `json:"timestamp"`
	EventType       string    `json:"event_type"`
	Severity        string    `json:"severity"`
	Description     string    `json:"description"`
	AffectedMetrics []string  `json:"affected_metrics"`
	Confidence      float64   `json:"confidence"`
}

// BaselineStats stores baseline performance for comparison
type BaselineStats struct {
	PolicyID       string    `json:"policy_id"`
	TrainingPeriod TimeRange `json:"training_period"`

	// Statistical baselines
	MeanAccuracy  float64 `json:"mean_accuracy"`
	StdAccuracy   float64 `json:"std_accuracy"`
	MeanPrecision float64 `json:"mean_precision"`
	StdPrecision  float64 `json:"std_precision"`
	MeanRecall    float64 `json:"mean_recall"`
	StdRecall     float64 `json:"std_recall"`
	MeanF1        float64 `json:"mean_f1"`
	StdF1         float64 `json:"std_f1"`
	MeanVolume    float64 `json:"mean_volume"`
	StdVolume     float64 `json:"std_volume"`

	// Distribution characteristics
	AccuracyDistribution []float64 `json:"accuracy_distribution"`
	VolumeDistribution   []float64 `json:"volume_distribution"`

	// Correlation matrix
	MetricCorrelations map[string]map[string]float64 `json:"metric_correlations"`

	LastUpdated time.Time `json:"last_updated"`
}

// DriftAlert represents a drift detection alert
type DriftAlert struct {
	AlertID            string              `json:"alert_id"`
	PolicyID           string              `json:"policy_id"`
	AlertType          string              `json:"alert_type"`
	Severity           string              `json:"severity"`
	Timestamp          time.Time           `json:"timestamp"`
	Title              string              `json:"title"`
	Description        string              `json:"description"`
	DriftScore         float64             `json:"drift_score"`
	AffectedMetrics    []string            `json:"affected_metrics"`
	RecommendedActions []RecommendedAction `json:"recommended_actions"`

	// Alert lifecycle
	Status         string     `json:"status"` // "open", "acknowledged", "resolved"
	AcknowledgedBy string     `json:"acknowledged_by,omitempty"`
	AcknowledgedAt *time.Time `json:"acknowledged_at,omitempty"`
	ResolvedBy     string     `json:"resolved_by,omitempty"`
	ResolvedAt     *time.Time `json:"resolved_at,omitempty"`

	// Notification tracking
	SlackSent    bool       `json:"slack_sent"`
	EmailSent    bool       `json:"email_sent"`
	LastNotified *time.Time `json:"last_notified,omitempty"`
}

// DriftStorage interface for persisting drift data
type DriftStorage interface {
	StorePolicyMetrics(ctx context.Context, metrics *PolicyDriftMetrics) error
	GetPolicyMetrics(ctx context.Context, policyID string, timeRange TimeRange) (*PolicyDriftMetrics, error)
	StoreAlert(ctx context.Context, alert *DriftAlert) error
	GetActiveAlerts(ctx context.Context) ([]DriftAlert, error)
	UpdateBaselineStats(ctx context.Context, stats *BaselineStats) error
	GetBaselineStats(ctx context.Context, policyID string) (*BaselineStats, error)

	// Analytics queries
	GetDriftTrends(ctx context.Context, timeRange TimeRange) ([]PolicyDriftMetrics, error)
	GetAlertHistory(ctx context.Context, timeRange TimeRange) ([]DriftAlert, error)
	GetPerformanceStats(ctx context.Context) (*DriftMetrics, error)
}

// NewDriftMonitor creates a new drift monitoring service
func NewDriftMonitor(logger *zap.Logger, storage DriftStorage, config DriftConfig) *DriftMonitor {
	if config.CheckInterval == 0 {
		config.CheckInterval = 15 * time.Minute
	}
	if config.WindowSize == 0 {
		config.WindowSize = 24 * time.Hour
	}
	if config.AccuracyThreshold == 0 {
		config.AccuracyThreshold = 0.85
	}
	if config.MaxConcurrentChecks == 0 {
		config.MaxConcurrentChecks = 4
	}

	return &DriftMonitor{
		logger:  logger,
		storage: storage,
		config:  config,
		metrics: &DriftMetrics{},
		cache: &DriftCache{
			policyMetrics: make(map[string]*PolicyDriftMetrics),
			baselineStats: make(map[string]*BaselineStats),
			cacheSize:     1000,
		},
		stopCh: make(chan struct{}),
	}
}

// Start begins drift monitoring
func (dm *DriftMonitor) Start(ctx context.Context) error {
	dm.mu.Lock()
	defer dm.mu.Unlock()

	if dm.running {
		return fmt.Errorf("drift monitor already running")
	}

	dm.running = true
	dm.logger.Info("Starting drift monitor",
		zap.Duration("check_interval", dm.config.CheckInterval),
		zap.Duration("window_size", dm.config.WindowSize))

	// Start monitoring goroutine
	go dm.monitorLoop(ctx)

	return nil
}

// Stop stops drift monitoring
func (dm *DriftMonitor) Stop() error {
	dm.mu.Lock()
	defer dm.mu.Unlock()

	if !dm.running {
		return nil
	}

	close(dm.stopCh)
	dm.running = false
	dm.logger.Info("Drift monitor stopped")

	return nil
}

// monitorLoop runs the main monitoring loop
func (dm *DriftMonitor) monitorLoop(ctx context.Context) {
	ticker := time.NewTicker(dm.config.CheckInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			dm.logger.Info("Drift monitor context cancelled")
			return
		case <-dm.stopCh:
			dm.logger.Info("Drift monitor stop signal received")
			return
		case <-ticker.C:
			if err := dm.performDriftCheck(ctx); err != nil {
				dm.logger.Error("Drift check failed", zap.Error(err))
			}
		}
	}
}

// performDriftCheck executes a single drift detection cycle
func (dm *DriftMonitor) performDriftCheck(ctx context.Context) error {
	startTime := time.Now()
	dm.logger.Debug("Starting drift check")

	// Get current policy metrics
	policies, err := dm.getActivePolicies(ctx)
	if err != nil {
		return fmt.Errorf("failed to get active policies: %w", err)
	}

	// Process policies concurrently
	semaphore := make(chan struct{}, dm.config.MaxConcurrentChecks)
	var wg sync.WaitGroup
	alertCh := make(chan *DriftAlert, len(policies))

	for _, policyID := range policies {
		wg.Add(1)
		go func(pid string) {
			defer wg.Done()
			semaphore <- struct{}{}
			defer func() { <-semaphore }()

			if alert := dm.checkPolicyDrift(ctx, pid); alert != nil {
				alertCh <- alert
			}
		}(policyID)
	}

	wg.Wait()
	close(alertCh)

	// Process alerts
	var alerts []*DriftAlert
	for alert := range alertCh {
		alerts = append(alerts, alert)
	}

	// Send notifications
	if err := dm.processAlerts(ctx, alerts); err != nil {
		dm.logger.Error("Failed to process alerts", zap.Error(err))
	}

	// Update metrics
	dm.updateMonitoringMetrics(startTime, len(policies), len(alerts))

	dm.logger.Debug("Drift check completed",
		zap.Int("policies_checked", len(policies)),
		zap.Int("alerts_generated", len(alerts)),
		zap.Duration("duration", time.Since(startTime)))

	return nil
}

// checkPolicyDrift analyzes drift for a specific policy
func (dm *DriftMonitor) checkPolicyDrift(ctx context.Context, policyID string) *DriftAlert {
	// Get recent performance data
	timeRange := TimeRange{
		StartTime: time.Now().Add(-dm.config.WindowSize),
		EndTime:   time.Now(),
	}

	metrics, err := dm.storage.GetPolicyMetrics(ctx, policyID, timeRange)
	if err != nil {
		dm.logger.Error("Failed to get policy metrics",
			zap.String("policy_id", policyID),
			zap.Error(err))
		return nil
	}

	// Get baseline statistics
	baseline, err := dm.storage.GetBaselineStats(ctx, policyID)
	if err != nil {
		dm.logger.Warn("No baseline stats found, calculating from recent data",
			zap.String("policy_id", policyID))
		baseline = dm.calculateBaseline(metrics)
	}

	// Perform drift analysis
	driftScore := dm.calculateDriftScore(metrics, baseline)
	driftReasons := dm.identifyDriftReasons(metrics, baseline)

	// Update cache
	dm.updatePolicyCache(policyID, metrics, driftScore, driftReasons)

	// Generate alert if drift detected
	if driftScore > dm.config.AlertThreshold {
		return dm.createDriftAlert(policyID, metrics, driftScore, driftReasons)
	}

	return nil
}

// calculateDriftScore computes overall drift score
func (dm *DriftMonitor) calculateDriftScore(metrics *PolicyDriftMetrics, baseline *BaselineStats) float64 {
	var score float64
	var components int

	// Accuracy drift
	if baseline.MeanAccuracy > 0 {
		accuracyDrift := math.Abs(metrics.CurrentAccuracy-baseline.MeanAccuracy) / baseline.StdAccuracy
		score += accuracyDrift * 0.3
		components++
	}

	// Precision drift
	if baseline.MeanPrecision > 0 {
		precisionDrift := math.Abs(metrics.CurrentPrecision-baseline.MeanPrecision) / baseline.StdPrecision
		score += precisionDrift * 0.25
		components++
	}

	// Recall drift
	if baseline.MeanRecall > 0 {
		recallDrift := math.Abs(metrics.CurrentRecall-baseline.MeanRecall) / baseline.StdRecall
		score += recallDrift * 0.25
		components++
	}

	// Volume drift
	if baseline.MeanVolume > 0 {
		volumeDrift := math.Abs(float64(metrics.CurrentVolume)-baseline.MeanVolume) / baseline.StdVolume
		score += volumeDrift * 0.2
		components++
	}

	if components > 0 {
		return score / float64(components)
	}

	return 0.0
}

// identifyDriftReasons analyzes why drift occurred
func (dm *DriftMonitor) identifyDriftReasons(metrics *PolicyDriftMetrics, baseline *BaselineStats) []string {
	var reasons []string

	// Check accuracy degradation
	if metrics.CurrentAccuracy < baseline.MeanAccuracy-2*baseline.StdAccuracy {
		reasons = append(reasons, "Significant accuracy degradation detected")
	}

	// Check precision issues
	if metrics.CurrentPrecision < baseline.MeanPrecision-2*baseline.StdPrecision {
		reasons = append(reasons, "Precision has declined, indicating more false positives")
	}

	// Check recall issues
	if metrics.CurrentRecall < baseline.MeanRecall-2*baseline.StdRecall {
		reasons = append(reasons, "Recall has declined, indicating more false negatives")
	}

	// Check volume changes
	volumeChange := math.Abs(float64(metrics.CurrentVolume) - baseline.MeanVolume)
	if volumeChange > baseline.StdVolume*3 {
		if float64(metrics.CurrentVolume) > baseline.MeanVolume {
			reasons = append(reasons, "Significant increase in event volume")
		} else {
			reasons = append(reasons, "Significant decrease in event volume")
		}
	}

	// Analyze trends
	if len(metrics.AccuracyTrend) >= 5 {
		slope := dm.calculateTrendSlope(metrics.AccuracyTrend)
		if slope < -dm.config.TrendSensitivity {
			reasons = append(reasons, "Downward trend in accuracy over time")
		}
	}

	return reasons
}

// calculateTrendSlope computes trend slope using linear regression
func (dm *DriftMonitor) calculateTrendSlope(points []TimeSeriesPoint) float64 {
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

	// Linear regression slope: (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
	numerator := n*sumXY - sumX*sumY
	denominator := n*sumX2 - sumX*sumX

	if denominator == 0 {
		return 0
	}

	return numerator / denominator
}

// calculateBaseline computes baseline statistics from recent data
func (dm *DriftMonitor) calculateBaseline(metrics *PolicyDriftMetrics) *BaselineStats {
	baseline := &BaselineStats{
		PolicyID: metrics.PolicyID,
		TrainingPeriod: TimeRange{
			StartTime: time.Now().Add(-30 * 24 * time.Hour), // 30 days
			EndTime:   time.Now(),
		},
		LastUpdated: time.Now(),
	}

	// Calculate statistics from accuracy trend
	if len(metrics.AccuracyTrend) > 0 {
		values := make([]float64, len(metrics.AccuracyTrend))
		for i, point := range metrics.AccuracyTrend {
			values[i] = point.Value
		}
		baseline.MeanAccuracy = dm.calculateMean(values)
		baseline.StdAccuracy = dm.calculateStdDev(values, baseline.MeanAccuracy)
	}

	// Similar calculations for other metrics...
	if len(metrics.PrecisionTrend) > 0 {
		values := make([]float64, len(metrics.PrecisionTrend))
		for i, point := range metrics.PrecisionTrend {
			values[i] = point.Value
		}
		baseline.MeanPrecision = dm.calculateMean(values)
		baseline.StdPrecision = dm.calculateStdDev(values, baseline.MeanPrecision)
	}

	return baseline
}

// calculateMean computes the mean of a slice of values
func (dm *DriftMonitor) calculateMean(values []float64) float64 {
	if len(values) == 0 {
		return 0
	}

	var sum float64
	for _, v := range values {
		sum += v
	}
	return sum / float64(len(values))
}

// calculateStdDev computes standard deviation
func (dm *DriftMonitor) calculateStdDev(values []float64, mean float64) float64 {
	if len(values) <= 1 {
		return 0
	}

	var sumSquares float64
	for _, v := range values {
		diff := v - mean
		sumSquares += diff * diff
	}

	variance := sumSquares / float64(len(values)-1)
	return math.Sqrt(variance)
}

// createDriftAlert creates a drift detection alert
func (dm *DriftMonitor) createDriftAlert(policyID string, metrics *PolicyDriftMetrics, driftScore float64, reasons []string) *DriftAlert {
	alertID := fmt.Sprintf("drift-%s-%d", policyID, time.Now().Unix())

	severity := "warning"
	if driftScore > dm.config.AlertThreshold*1.5 {
		severity = "critical"
	}

	title := fmt.Sprintf("Policy Drift Detected: %s", policyID)
	description := fmt.Sprintf("Policy %s shows drift with score %.2f. Reasons: %v",
		policyID, driftScore, reasons)

	alert := &DriftAlert{
		AlertID:            alertID,
		PolicyID:           policyID,
		AlertType:          "drift_detection",
		Severity:           severity,
		Timestamp:          time.Now(),
		Title:              title,
		Description:        description,
		DriftScore:         driftScore,
		AffectedMetrics:    []string{"accuracy", "precision", "recall"},
		Status:             "open",
		RecommendedActions: dm.generateRecommendations(metrics, reasons),
	}

	return alert
}

// generateRecommendations creates recommended actions based on drift analysis
func (dm *DriftMonitor) generateRecommendations(metrics *PolicyDriftMetrics, reasons []string) []RecommendedAction {
	var actions []RecommendedAction

	for _, reason := range reasons {
		switch {
		case reason == "Significant accuracy degradation detected":
			actions = append(actions, RecommendedAction{
				ActionType:  "retrain_model",
				Priority:    "high",
				Description: "Retrain the policy model with recent data to improve accuracy",
				Impact:      "Should restore accuracy to baseline levels",
				Effort:      "medium",
				DeadlineBy:  time.Now().Add(7 * 24 * time.Hour),
			})

		case reason == "Precision has declined, indicating more false positives":
			actions = append(actions, RecommendedAction{
				ActionType:  "adjust_thresholds",
				Priority:    "medium",
				Description: "Increase detection thresholds to reduce false positives",
				Impact:      "Reduce false positive rate, may slightly decrease recall",
				Effort:      "low",
				DeadlineBy:  time.Now().Add(3 * 24 * time.Hour),
			})

		case reason == "Significant increase in event volume":
			actions = append(actions, RecommendedAction{
				ActionType:  "scale_resources",
				Priority:    "high",
				Description: "Scale up processing resources to handle increased load",
				Impact:      "Maintain processing performance under higher load",
				Effort:      "low",
				DeadlineBy:  time.Now().Add(1 * 24 * time.Hour),
			})
		}
	}

	// Always add a general review recommendation
	actions = append(actions, RecommendedAction{
		ActionType:  "policy_review",
		Priority:    "medium",
		Description: "Schedule a comprehensive policy review with the security team",
		Impact:      "Identify underlying causes and long-term improvements",
		Effort:      "high",
		DeadlineBy:  time.Now().Add(14 * 24 * time.Hour),
	})

	return actions
}

// updatePolicyCache updates the in-memory cache
func (dm *DriftMonitor) updatePolicyCache(policyID string, metrics *PolicyDriftMetrics, driftScore float64, reasons []string) {
	dm.cache.mu.Lock()
	defer dm.cache.mu.Unlock()

	metrics.DriftScore = driftScore
	metrics.DriftReasons = reasons

	// Determine drift status
	if driftScore < dm.config.AlertThreshold*0.5 {
		metrics.DriftStatus = DriftStatusHealthy
	} else if driftScore < dm.config.AlertThreshold {
		metrics.DriftStatus = DriftStatusWarning
	} else {
		metrics.DriftStatus = DriftStatusCritical
	}

	dm.cache.policyMetrics[policyID] = metrics
	dm.cache.lastUpdate = time.Now()
}

// processAlerts handles alert notifications and storage
func (dm *DriftMonitor) processAlerts(ctx context.Context, alerts []*DriftAlert) error {
	for _, alert := range alerts {
		// Store alert
		if err := dm.storage.StoreAlert(ctx, alert); err != nil {
			dm.logger.Error("Failed to store alert", zap.Error(err))
			continue
		}

		// Send notifications
		if err := dm.sendNotifications(ctx, alert); err != nil {
			dm.logger.Error("Failed to send notifications", zap.Error(err))
		}

		// Add to cache
		dm.cache.mu.Lock()
		dm.cache.recentAlerts = append(dm.cache.recentAlerts, *alert)
		// Keep only recent alerts in cache
		if len(dm.cache.recentAlerts) > 100 {
			dm.cache.recentAlerts = dm.cache.recentAlerts[1:]
		}
		dm.cache.mu.Unlock()
	}

	return nil
}

// sendNotifications sends alert notifications via configured channels
func (dm *DriftMonitor) sendNotifications(ctx context.Context, alert *DriftAlert) error {
	// Implementation would integrate with notification services
	dm.logger.Info("Drift alert generated",
		zap.String("alert_id", alert.AlertID),
		zap.String("policy_id", alert.PolicyID),
		zap.String("severity", alert.Severity),
		zap.Float64("drift_score", alert.DriftScore))

	return nil
}

// getActivePolicies retrieves list of policies to monitor
func (dm *DriftMonitor) getActivePolicies(ctx context.Context) ([]string, error) {
	// This would typically query the policy storage to get active policies
	// For now, return a mock list
	return []string{"cpu-anomaly-policy", "memory-threshold-policy", "network-latency-policy"}, nil
}

// updateMonitoringMetrics updates drift monitor performance metrics
func (dm *DriftMonitor) updateMonitoringMetrics(startTime time.Time, policiesChecked, alertsGenerated int) {
	duration := time.Since(startTime)

	dm.metrics.ChecksPerformed++
	dm.metrics.DriftDetections += int64(alertsGenerated)
	dm.metrics.AlertsSent += int64(alertsGenerated)
	dm.metrics.LastCheckTime = time.Now()

	// Update average check duration using exponential moving average
	alpha := 0.1
	newDuration := float64(duration.Nanoseconds()) / 1000000 // Convert to milliseconds
	if dm.metrics.AvgCheckDuration == 0 {
		dm.metrics.AvgCheckDuration = newDuration
	} else {
		dm.metrics.AvgCheckDuration = alpha*newDuration + (1-alpha)*dm.metrics.AvgCheckDuration
	}

	// Calculate health score (0-1, higher is better)
	if dm.metrics.ChecksPerformed > 0 {
		falsePositiveRate := float64(dm.metrics.FalsePositives) / float64(dm.metrics.DriftDetections)
		dm.metrics.HealthScore = 1.0 - falsePositiveRate
	}
}

// GetDriftMetrics returns current drift monitoring metrics
func (dm *DriftMonitor) GetDriftMetrics() *DriftMetrics {
	dm.mu.RLock()
	defer dm.mu.RUnlock()

	// Return a copy to avoid race conditions
	metrics := *dm.metrics
	return &metrics
}

// GetPolicyDriftStatus returns drift status for a specific policy
func (dm *DriftMonitor) GetPolicyDriftStatus(policyID string) (*PolicyDriftMetrics, error) {
	dm.cache.mu.RLock()
	defer dm.cache.mu.RUnlock()

	metrics, exists := dm.cache.policyMetrics[policyID]
	if !exists {
		return nil, fmt.Errorf("no drift metrics found for policy %s", policyID)
	}

	// Return a copy
	result := *metrics
	return &result, nil
}

// GetRecentAlerts returns recent drift alerts
func (dm *DriftMonitor) GetRecentAlerts(limit int) []DriftAlert {
	dm.cache.mu.RLock()
	defer dm.cache.mu.RUnlock()

	alerts := make([]DriftAlert, len(dm.cache.recentAlerts))
	copy(alerts, dm.cache.recentAlerts)

	// Sort by timestamp descending
	sort.Slice(alerts, func(i, j int) bool {
		return alerts[i].Timestamp.After(alerts[j].Timestamp)
	})

	if limit > 0 && len(alerts) > limit {
		alerts = alerts[:limit]
	}

	return alerts
}

// DefaultDriftConfig returns default drift monitoring configuration
func DefaultDriftConfig() DriftConfig {
	return DriftConfig{
		CheckInterval:            15 * time.Minute,
		WindowSize:               24 * time.Hour,
		HistoryRetention:         30 * 24 * time.Hour,
		AccuracyThreshold:        0.85,
		PrecisionThreshold:       0.80,
		RecallThreshold:          0.80,
		F1Threshold:              0.80,
		VolumeChangeThreshold:    0.20,
		SeasonalityWindow:        168, // 1 week in hours
		TrendSensitivity:         0.05,
		NoveltyThreshold:         2.0,
		AlertThreshold:           2.0,
		AlertCooldown:            1 * time.Hour,
		EnableSlackAlerts:        true,
		EnableEmailAlerts:        true,
		MaxConcurrentChecks:      4,
		BatchSize:                1000,
		EnableAdaptiveThresholds: true,
	}
}
