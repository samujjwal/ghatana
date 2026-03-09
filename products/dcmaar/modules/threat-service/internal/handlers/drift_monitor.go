package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/gorilla/mux"
	"go.uber.org/zap"
)

// DriftMonitor interface for drift monitoring operations
type DriftMonitor interface {
	Start() error
	Stop() error
	GetDriftMetrics() *DriftMetrics
	GetPolicyDriftStatus(policyID string) (*PolicyDriftMetrics, error)
	GetRecentAlerts(limit int) []DriftAlert
}

// DriftMetrics represents drift monitoring metrics
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

// PolicyDriftMetrics represents drift metrics for a specific policy
type PolicyDriftMetrics struct {
	PolicyID         string    `json:"policy_id"`
	PolicyVersion    string    `json:"policy_version"`
	LastUpdated      time.Time `json:"last_updated"`
	CurrentAccuracy  float64   `json:"current_accuracy"`
	CurrentPrecision float64   `json:"current_precision"`
	CurrentRecall    float64   `json:"current_recall"`
	CurrentF1        float64   `json:"current_f1"`
	CurrentVolume    int64     `json:"current_volume"`
	DriftScore       float64   `json:"drift_score"`
	DriftStatus      string    `json:"drift_status"`
	DriftReasons     []string  `json:"drift_reasons"`
}

// DriftAlert represents a drift detection alert
type DriftAlert struct {
	AlertID         string     `json:"alert_id"`
	PolicyID        string     `json:"policy_id"`
	AlertType       string     `json:"alert_type"`
	Severity        string     `json:"severity"`
	Timestamp       time.Time  `json:"timestamp"`
	Title           string     `json:"title"`
	Description     string     `json:"description"`
	DriftScore      float64    `json:"drift_score"`
	AffectedMetrics []string   `json:"affected_metrics"`
	Status          string     `json:"status"`
	AcknowledgedBy  string     `json:"acknowledged_by,omitempty"`
	AcknowledgedAt  *time.Time `json:"acknowledged_at,omitempty"`
	ResolvedBy      string     `json:"resolved_by,omitempty"`
	ResolvedAt      *time.Time `json:"resolved_at,omitempty"`
}

// DriftMonitorHandler handles drift monitoring REST API endpoints
type DriftMonitorHandler struct {
	logger       *zap.Logger
	driftMonitor DriftMonitor
}

// NewDriftMonitorHandler creates a new drift monitoring handler
func NewDriftMonitorHandler(logger *zap.Logger, driftMonitor DriftMonitor) *DriftMonitorHandler {
	return &DriftMonitorHandler{
		logger:       logger,
		driftMonitor: driftMonitor,
	}
}

// SetupRoutes sets up HTTP routes for drift monitoring
func (h *DriftMonitorHandler) SetupRoutes(mux *http.ServeMux) {
	// Drift monitoring service management
	mux.HandleFunc("/drift/status", h.GetDriftMonitorStatus)
	mux.HandleFunc("/drift/start", h.StartDriftMonitoring)
	mux.HandleFunc("/drift/stop", h.StopDriftMonitoring)

	// Policy drift status
	mux.HandleFunc("/drift/policies", h.GetPolicyDriftOverview)

	// Alerts and notifications
	mux.HandleFunc("/drift/alerts", h.GetDriftAlerts)

	// Recommendations
	mux.HandleFunc("/drift/recommendations", h.GetDriftRecommendations)

	// Analytics and reporting
	mux.HandleFunc("/drift/analytics/dashboard", h.GetDriftDashboard)
	mux.HandleFunc("/drift/analytics/trends", h.GetDriftTrends)
	mux.HandleFunc("/drift/analytics/performance", h.GetPerformanceAnalytics)
}

// GetDriftMonitorStatus returns current drift monitor status
func (h *DriftMonitorHandler) GetDriftMonitorStatus(w http.ResponseWriter, r *http.Request) {
	metrics := h.driftMonitor.GetDriftMetrics()

	response := map[string]interface{}{
		"status":       "running",
		"metrics":      metrics,
		"last_check":   metrics.LastCheckTime,
		"health_score": metrics.HealthScore,
		"uptime":       time.Since(metrics.LastCheckTime),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// StartDriftMonitoring starts the drift monitoring service
func (h *DriftMonitorHandler) StartDriftMonitoring(w http.ResponseWriter, r *http.Request) {
	if err := h.driftMonitor.Start(); err != nil {
		h.logger.Error("Failed to start drift monitoring", zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, "Failed to start drift monitoring", err)
		return
	}

	response := map[string]interface{}{
		"message":    "Drift monitoring started successfully",
		"started_at": time.Now(),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// StopDriftMonitoring stops the drift monitoring service
func (h *DriftMonitorHandler) StopDriftMonitoring(w http.ResponseWriter, r *http.Request) {
	if err := h.driftMonitor.Stop(); err != nil {
		h.logger.Error("Failed to stop drift monitoring", zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, "Failed to stop drift monitoring", err)
		return
	}

	response := map[string]interface{}{
		"message":    "Drift monitoring stopped successfully",
		"stopped_at": time.Now(),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetPolicyDriftOverview returns drift status for all policies
func (h *DriftMonitorHandler) GetPolicyDriftOverview(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	limit := h.getIntQueryParam(r, "limit", 50)
	status := r.URL.Query().Get("status")
	severity := r.URL.Query().Get("severity")

	// Mock implementation - in real scenario would query storage
	policies := []map[string]interface{}{
		{
			"policy_id":        "cpu-anomaly-policy",
			"drift_status":     "warning",
			"drift_score":      1.8,
			"current_accuracy": 0.87,
			"last_updated":     time.Now().Add(-1 * time.Hour),
			"alert_count":      2,
		},
		{
			"policy_id":        "memory-threshold-policy",
			"drift_status":     "healthy",
			"drift_score":      0.3,
			"current_accuracy": 0.94,
			"last_updated":     time.Now().Add(-30 * time.Minute),
			"alert_count":      0,
		},
		{
			"policy_id":        "network-latency-policy",
			"drift_status":     "critical",
			"drift_score":      3.2,
			"current_accuracy": 0.76,
			"last_updated":     time.Now().Add(-15 * time.Minute),
			"alert_count":      5,
		},
	}

	// Apply filters
	filteredPolicies := h.filterPolicies(policies, status, severity)
	if len(filteredPolicies) > limit {
		filteredPolicies = filteredPolicies[:limit]
	}

	response := map[string]interface{}{
		"policies":       filteredPolicies,
		"total_count":    len(policies),
		"filtered_count": len(filteredPolicies),
		"summary": map[string]interface{}{
			"healthy":  h.countPoliciesByStatus(policies, "healthy"),
			"warning":  h.countPoliciesByStatus(policies, "warning"),
			"critical": h.countPoliciesByStatus(policies, "critical"),
		},
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetPolicyDriftStatus returns detailed drift status for a specific policy
func (h *DriftMonitorHandler) GetPolicyDriftStatus(w http.ResponseWriter, r *http.Request) {
	policyID := mux.Vars(r)["policyId"]

	metrics, err := h.driftMonitor.GetPolicyDriftStatus(policyID)
	if err != nil {
		h.logger.Error("Failed to get policy drift status",
			zap.String("policy_id", policyID),
			zap.Error(err))
		h.writeErrorResponse(w, http.StatusNotFound, "Policy drift metrics not found", err)
		return
	}

	h.writeJSONResponse(w, http.StatusOK, metrics)
}

// GetPolicyDriftHistory returns historical drift data for a policy
func (h *DriftMonitorHandler) GetPolicyDriftHistory(w http.ResponseWriter, r *http.Request) {
	policyID := mux.Vars(r)["policyId"]

	// Parse time range parameters
	startTime, endTime := h.getTimeRangeParams(r)

	// Mock historical data
	history := []map[string]interface{}{
		{
			"timestamp":   time.Now().Add(-24 * time.Hour),
			"accuracy":    0.92,
			"precision":   0.89,
			"recall":      0.94,
			"drift_score": 0.5,
			"volume":      15423,
		},
		{
			"timestamp":   time.Now().Add(-12 * time.Hour),
			"accuracy":    0.88,
			"precision":   0.85,
			"recall":      0.91,
			"drift_score": 1.2,
			"volume":      18234,
		},
		{
			"timestamp":   time.Now().Add(-6 * time.Hour),
			"accuracy":    0.84,
			"precision":   0.82,
			"recall":      0.87,
			"drift_score": 2.1,
			"volume":      21567,
		},
	}

	response := map[string]interface{}{
		"policy_id": policyID,
		"time_range": map[string]interface{}{
			"start_time": startTime,
			"end_time":   endTime,
		},
		"history":     history,
		"data_points": len(history),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetPolicyBaseline returns baseline statistics for a policy
func (h *DriftMonitorHandler) GetPolicyBaseline(w http.ResponseWriter, r *http.Request) {
	policyID := mux.Vars(r)["policyId"]

	// Mock baseline data
	baseline := map[string]interface{}{
		"policy_id":        policyID,
		"baseline_version": "v1.2.0",
		"training_period": map[string]interface{}{
			"start_time": time.Now().Add(-30 * 24 * time.Hour),
			"end_time":   time.Now().Add(-7 * 24 * time.Hour),
		},
		"statistics": map[string]interface{}{
			"mean_accuracy":  0.91,
			"std_accuracy":   0.04,
			"mean_precision": 0.88,
			"std_precision":  0.05,
			"mean_recall":    0.93,
			"std_recall":     0.03,
			"mean_volume":    16500.0,
			"std_volume":     2400.0,
		},
		"confidence_level": 0.95,
		"sample_size":      2156,
		"last_updated":     time.Now().Add(-7 * 24 * time.Hour),
	}

	h.writeJSONResponse(w, http.StatusOK, baseline)
}

// UpdatePolicyBaseline updates baseline statistics for a policy
func (h *DriftMonitorHandler) UpdatePolicyBaseline(w http.ResponseWriter, r *http.Request) {
	policyID := mux.Vars(r)["policyId"]

	var request struct {
		TrainingPeriod struct {
			StartTime time.Time `json:"start_time"`
			EndTime   time.Time `json:"end_time"`
		} `json:"training_period"`
		ForceRecalculation bool `json:"force_recalculation"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Mock baseline update
	h.logger.Info("Updating policy baseline",
		zap.String("policy_id", policyID),
		zap.Time("start_time", request.TrainingPeriod.StartTime),
		zap.Time("end_time", request.TrainingPeriod.EndTime))

	response := map[string]interface{}{
		"message":                 "Baseline updated successfully",
		"policy_id":               policyID,
		"updated_at":              time.Now(),
		"recalculation_triggered": request.ForceRecalculation,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetDriftAlerts returns drift alerts with filtering
func (h *DriftMonitorHandler) GetDriftAlerts(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	limit := h.getIntQueryParam(r, "limit", 20)
	status := r.URL.Query().Get("status")
	severity := r.URL.Query().Get("severity")
	policyID := r.URL.Query().Get("policy_id")

	// Get recent alerts from monitor
	alerts := h.driftMonitor.GetRecentAlerts(limit * 2) // Get more to allow for filtering

	// Convert to map format and apply filters
	var filteredAlerts []map[string]interface{}
	for _, alert := range alerts {
		if status != "" && alert.Status != status {
			continue
		}
		if severity != "" && alert.Severity != severity {
			continue
		}
		if policyID != "" && alert.PolicyID != policyID {
			continue
		}

		alertMap := map[string]interface{}{
			"alert_id":         alert.AlertID,
			"policy_id":        alert.PolicyID,
			"alert_type":       alert.AlertType,
			"severity":         alert.Severity,
			"title":            alert.Title,
			"description":      alert.Description,
			"drift_score":      alert.DriftScore,
			"status":           alert.Status,
			"created_at":       alert.Timestamp,
			"acknowledged_by":  alert.AcknowledgedBy,
			"acknowledged_at":  alert.AcknowledgedAt,
			"resolved_by":      alert.ResolvedBy,
			"resolved_at":      alert.ResolvedAt,
			"affected_metrics": alert.AffectedMetrics,
		}

		filteredAlerts = append(filteredAlerts, alertMap)

		if len(filteredAlerts) >= limit {
			break
		}
	}

	response := map[string]interface{}{
		"alerts":         filteredAlerts,
		"total_count":    len(alerts),
		"filtered_count": len(filteredAlerts),
		"has_more":       len(alerts) > limit,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetDriftAlert returns a specific drift alert
func (h *DriftMonitorHandler) GetDriftAlert(w http.ResponseWriter, r *http.Request) {
	alertID := mux.Vars(r)["alertId"]

	// Find alert in recent alerts
	alerts := h.driftMonitor.GetRecentAlerts(100)
	for _, alert := range alerts {
		if alert.AlertID == alertID {
			h.writeJSONResponse(w, http.StatusOK, alert)
			return
		}
	}

	h.writeErrorResponse(w, http.StatusNotFound, "Alert not found", nil)
}

// AcknowledgeDriftAlert acknowledges a drift alert
func (h *DriftMonitorHandler) AcknowledgeDriftAlert(w http.ResponseWriter, r *http.Request) {
	alertID := mux.Vars(r)["alertId"]

	var request struct {
		AcknowledgedBy string `json:"acknowledged_by"`
		Notes          string `json:"notes"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	h.logger.Info("Acknowledging drift alert",
		zap.String("alert_id", alertID),
		zap.String("acknowledged_by", request.AcknowledgedBy))

	response := map[string]interface{}{
		"message":         "Alert acknowledged successfully",
		"alert_id":        alertID,
		"acknowledged_by": request.AcknowledgedBy,
		"acknowledged_at": time.Now(),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// ResolveDriftAlert resolves a drift alert
func (h *DriftMonitorHandler) ResolveDriftAlert(w http.ResponseWriter, r *http.Request) {
	alertID := mux.Vars(r)["alertId"]

	var request struct {
		ResolvedBy      string   `json:"resolved_by"`
		ResolutionNotes string   `json:"resolution_notes"`
		ActionsTaken    []string `json:"actions_taken"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	h.logger.Info("Resolving drift alert",
		zap.String("alert_id", alertID),
		zap.String("resolved_by", request.ResolvedBy))

	response := map[string]interface{}{
		"message":       "Alert resolved successfully",
		"alert_id":      alertID,
		"resolved_by":   request.ResolvedBy,
		"resolved_at":   time.Now(),
		"actions_taken": request.ActionsTaken,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// SuppressDriftAlert suppresses a drift alert
func (h *DriftMonitorHandler) SuppressDriftAlert(w http.ResponseWriter, r *http.Request) {
	alertID := mux.Vars(r)["alertId"]

	var request struct {
		SuppressedBy     string        `json:"suppressed_by"`
		Reason           string        `json:"reason"`
		SuppressDuration time.Duration `json:"suppress_duration"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	h.logger.Info("Suppressing drift alert",
		zap.String("alert_id", alertID),
		zap.String("suppressed_by", request.SuppressedBy),
		zap.Duration("duration", request.SuppressDuration))

	response := map[string]interface{}{
		"message":          "Alert suppressed successfully",
		"alert_id":         alertID,
		"suppressed_by":    request.SuppressedBy,
		"suppressed_until": time.Now().Add(request.SuppressDuration),
		"reason":           request.Reason,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetDriftRecommendations returns recommended actions for drift issues
func (h *DriftMonitorHandler) GetDriftRecommendations(w http.ResponseWriter, r *http.Request) {
	policyID := r.URL.Query().Get("policy_id")
	status := r.URL.Query().Get("status")
	priority := r.URL.Query().Get("priority")

	// Mock recommendations
	recommendations := []map[string]interface{}{
		{
			"recommendation_id": "rec-001",
			"policy_id":         "cpu-anomaly-policy",
			"action_type":       "retrain_model",
			"priority":          "high",
			"title":             "Retrain CPU Anomaly Detection Model",
			"description":       "Model accuracy has dropped below acceptable threshold. Retrain with recent data.",
			"impact":            "Should restore accuracy to baseline levels",
			"effort":            "medium",
			"deadline":          time.Now().Add(7 * 24 * time.Hour),
			"status":            "pending",
			"auto_approved":     false,
		},
		{
			"recommendation_id": "rec-002",
			"policy_id":         "memory-threshold-policy",
			"action_type":       "adjust_thresholds",
			"priority":          "medium",
			"title":             "Adjust Memory Usage Thresholds",
			"description":       "Increase thresholds to reduce false positive rate",
			"impact":            "Reduce false positive rate by ~15%",
			"effort":            "low",
			"deadline":          time.Now().Add(3 * 24 * time.Hour),
			"status":            "approved",
			"auto_approved":     true,
		},
	}

	// Apply filters
	filteredRecs := h.filterRecommendations(recommendations, policyID, status, priority)

	response := map[string]interface{}{
		"recommendations": filteredRecs,
		"total_count":     len(recommendations),
		"filtered_count":  len(filteredRecs),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// ApproveRecommendation approves a recommendation for execution
func (h *DriftMonitorHandler) ApproveRecommendation(w http.ResponseWriter, r *http.Request) {
	recommendationID := mux.Vars(r)["recommendationId"]

	var request struct {
		ApprovedBy string `json:"approved_by"`
		Notes      string `json:"notes"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	h.logger.Info("Approving recommendation",
		zap.String("recommendation_id", recommendationID),
		zap.String("approved_by", request.ApprovedBy))

	response := map[string]interface{}{
		"message":           "Recommendation approved successfully",
		"recommendation_id": recommendationID,
		"approved_by":       request.ApprovedBy,
		"approved_at":       time.Now(),
		"status":            "approved",
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// ExecuteRecommendation executes an approved recommendation
func (h *DriftMonitorHandler) ExecuteRecommendation(w http.ResponseWriter, r *http.Request) {
	recommendationID := mux.Vars(r)["recommendationId"]

	var request struct {
		ExecutedBy string                 `json:"executed_by"`
		Parameters map[string]interface{} `json:"parameters"`
		DryRun     bool                   `json:"dry_run"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	h.logger.Info("Executing recommendation",
		zap.String("recommendation_id", recommendationID),
		zap.String("executed_by", request.ExecutedBy),
		zap.Bool("dry_run", request.DryRun))

	// Mock execution
	executionResult := map[string]interface{}{
		"success":          true,
		"duration_minutes": 12,
		"impact_measured":  0.15,
		"changes_applied": []string{
			"Updated model training data",
			"Recomputed feature weights",
			"Applied new model version",
		},
		"rollback_available": true,
	}

	response := map[string]interface{}{
		"message":           "Recommendation executed successfully",
		"recommendation_id": recommendationID,
		"executed_by":       request.ExecutedBy,
		"executed_at":       time.Now(),
		"dry_run":           request.DryRun,
		"result":            executionResult,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// GetDriftDashboard returns dashboard data for drift monitoring
func (h *DriftMonitorHandler) GetDriftDashboard(w http.ResponseWriter, r *http.Request) {
	dashboard := map[string]interface{}{
		"overview": map[string]interface{}{
			"total_policies":    12,
			"healthy_policies":  8,
			"warning_policies":  3,
			"critical_policies": 1,
			"monitoring_active": true,
			"last_update":       time.Now().Add(-5 * time.Minute),
		},
		"alerts": map[string]interface{}{
			"open_alerts":               4,
			"critical_alerts":           1,
			"alerts_today":              7,
			"avg_resolution_time_hours": 3.2,
		},
		"performance": map[string]interface{}{
			"avg_accuracy":         0.89,
			"avg_precision":        0.86,
			"avg_recall":           0.91,
			"overall_health_score": 0.87,
		},
		"trends": map[string]interface{}{
			"accuracy_trend": "declining",
			"volume_trend":   "increasing",
			"alert_trend":    "stable",
		},
		"top_issues": []map[string]interface{}{
			{
				"policy_id":   "cpu-anomaly-policy",
				"issue":       "accuracy_degradation",
				"severity":    "high",
				"drift_score": 2.8,
			},
			{
				"policy_id":   "network-latency-policy",
				"issue":       "volume_spike",
				"severity":    "medium",
				"drift_score": 1.9,
			},
		},
	}

	h.writeJSONResponse(w, http.StatusOK, dashboard)
}

// GetDriftTrends returns trend analysis data
func (h *DriftMonitorHandler) GetDriftTrends(w http.ResponseWriter, r *http.Request) {
	// Parse parameters
	timeRange := r.URL.Query().Get("time_range") // "24h", "7d", "30d"
	if timeRange == "" {
		timeRange = "24h"
	}

	// Mock trend data
	trends := map[string]interface{}{
		"time_range": timeRange,
		"accuracy_trend": []map[string]interface{}{
			{"time": time.Now().Add(-24 * time.Hour), "value": 0.92},
			{"time": time.Now().Add(-12 * time.Hour), "value": 0.88},
			{"time": time.Now().Add(-6 * time.Hour), "value": 0.85},
			{"time": time.Now(), "value": 0.84},
		},
		"drift_score_trend": []map[string]interface{}{
			{"time": time.Now().Add(-24 * time.Hour), "value": 0.5},
			{"time": time.Now().Add(-12 * time.Hour), "value": 1.2},
			{"time": time.Now().Add(-6 * time.Hour), "value": 2.1},
			{"time": time.Now(), "value": 2.8},
		},
		"volume_trend": []map[string]interface{}{
			{"time": time.Now().Add(-24 * time.Hour), "value": 15400},
			{"time": time.Now().Add(-12 * time.Hour), "value": 18200},
			{"time": time.Now().Add(-6 * time.Hour), "value": 21500},
			{"time": time.Now(), "value": 24800},
		},
		"forecasts": map[string]interface{}{
			"accuracy_forecast": []map[string]interface{}{
				{"time": time.Now().Add(6 * time.Hour), "value": 0.82, "confidence": 0.85},
				{"time": time.Now().Add(12 * time.Hour), "value": 0.80, "confidence": 0.78},
				{"time": time.Now().Add(24 * time.Hour), "value": 0.79, "confidence": 0.72},
			},
		},
	}

	h.writeJSONResponse(w, http.StatusOK, trends)
}

// GetPerformanceAnalytics returns performance analytics data
func (h *DriftMonitorHandler) GetPerformanceAnalytics(w http.ResponseWriter, r *http.Request) {
	analytics := map[string]interface{}{
		"monitor_performance": map[string]interface{}{
			"checks_per_minute":     4.2,
			"avg_check_duration_ms": 1250.0,
			"success_rate":          0.987,
			"error_rate":            0.013,
			"memory_usage_mb":       245.6,
			"cpu_usage":             0.18,
		},
		"detection_performance": map[string]interface{}{
			"true_positive_rate":  0.89,
			"false_positive_rate": 0.12,
			"precision":           0.88,
			"recall":              0.89,
			"f1_score":            0.885,
		},
		"policy_statistics": map[string]interface{}{
			"most_stable_policy":     "disk-usage-policy",
			"most_volatile_policy":   "network-latency-policy",
			"best_performing_policy": "memory-threshold-policy",
			"avg_drift_score":        1.45,
		},
		"resource_utilization": map[string]interface{}{
			"database_connections": 8,
			"concurrent_checks":    3,
			"queue_depth":          0,
			"cache_hit_rate":       0.92,
		},
	}

	h.writeJSONResponse(w, http.StatusOK, analytics)
}

// Helper methods

func (h *DriftMonitorHandler) writeJSONResponse(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(data)
}

func (h *DriftMonitorHandler) writeErrorResponse(w http.ResponseWriter, statusCode int, message string, err error) {
	response := map[string]interface{}{
		"error":     message,
		"timestamp": time.Now(),
	}

	if err != nil {
		response["details"] = err.Error()
	}

	h.writeJSONResponse(w, statusCode, response)
}

func (h *DriftMonitorHandler) getIntQueryParam(r *http.Request, key string, defaultValue int) int {
	if value := r.URL.Query().Get(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}

func (h *DriftMonitorHandler) getTimeRangeParams(r *http.Request) (time.Time, time.Time) {
	endTime := time.Now()
	startTime := endTime.Add(-24 * time.Hour) // Default to 24 hours

	if start := r.URL.Query().Get("start_time"); start != "" {
		if t, err := time.Parse(time.RFC3339, start); err == nil {
			startTime = t
		}
	}

	if end := r.URL.Query().Get("end_time"); end != "" {
		if t, err := time.Parse(time.RFC3339, end); err == nil {
			endTime = t
		}
	}

	return startTime, endTime
}

func (h *DriftMonitorHandler) filterPolicies(policies []map[string]interface{}, status, severity string) []map[string]interface{} {
	var filtered []map[string]interface{}

	for _, policy := range policies {
		if status != "" && policy["drift_status"] != status {
			continue
		}
		// Add more filtering logic as needed
		filtered = append(filtered, policy)
	}

	return filtered
}

func (h *DriftMonitorHandler) filterRecommendations(recommendations []map[string]interface{}, policyID, status, priority string) []map[string]interface{} {
	var filtered []map[string]interface{}

	for _, rec := range recommendations {
		if policyID != "" && rec["policy_id"] != policyID {
			continue
		}
		if status != "" && rec["status"] != status {
			continue
		}
		if priority != "" && rec["priority"] != priority {
			continue
		}
		filtered = append(filtered, rec)
	}

	return filtered
}

func (h *DriftMonitorHandler) countPoliciesByStatus(policies []map[string]interface{}, status string) int {
	count := 0
	for _, policy := range policies {
		if policy["drift_status"] == status {
			count++
		}
	}
	return count
}
