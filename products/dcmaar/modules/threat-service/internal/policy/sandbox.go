package policy

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
)

// PolicySandbox provides simulation capabilities for testing policy changes
// on historical data before deployment to production
type PolicySandbox struct {
	logger  *zap.Logger
	storage *storage.Storage
}

// SimulationRequest represents a policy simulation request
type SimulationRequest struct {
	SimulationID     string           `json:"simulation_id"`
	Name             string           `json:"name"`
	Description      string           `json:"description"`
	PolicyBundle     PolicyBundle     `json:"policy_bundle"`
	TimeRange        TimeRange        `json:"time_range"`
	EventFilters     EventFilters     `json:"event_filters"`
	SimulationConfig SimulationConfig `json:"simulation_config"`
	RequestedBy      string           `json:"requested_by"`
	RequestedAt      time.Time        `json:"requested_at"`
}

// PolicyBundle represents a complete policy configuration for testing
type PolicyBundle struct {
	Version         string                 `json:"version"`
	AnomalyPolicies []AnomalyPolicy        `json:"anomaly_policies"`
	FilterRules     []FilterRule           `json:"filter_rules"`
	Thresholds      map[string]float64     `json:"thresholds"`
	AllowLists      map[string][]string    `json:"allow_lists"`
	DenyLists       map[string][]string    `json:"deny_lists"`
	Metadata        map[string]interface{} `json:"metadata"`
}

// AnomalyPolicy defines anomaly detection rules
type AnomalyPolicy struct {
	PolicyID      string            `json:"policy_id"`
	MetricName    string            `json:"metric_name"`
	DetectionType string            `json:"detection_type"` // ewma, zscore, threshold
	Threshold     float64           `json:"threshold"`
	WindowSize    int               `json:"window_size"`
	Sensitivity   float64           `json:"sensitivity"`
	Enabled       bool              `json:"enabled"`
	Conditions    []PolicyCondition `json:"conditions"`
}

// PolicyCondition represents conditional logic for policy application
type PolicyCondition struct {
	Field    string      `json:"field"`
	Operator string      `json:"operator"` // eq, ne, gt, lt, contains, in
	Value    interface{} `json:"value"`
}

// FilterRule defines event filtering and suppression rules
type FilterRule struct {
	RuleID     string            `json:"rule_id"`
	Name       string            `json:"name"`
	EventTypes []string          `json:"event_types"`
	Action     string            `json:"action"` // allow, deny, modify, flag
	Conditions []PolicyCondition `json:"conditions"`
	Priority   int               `json:"priority"`
	Enabled    bool              `json:"enabled"`
}

// TimeRange defines the historical data range for simulation
type TimeRange struct {
	StartTime time.Time `json:"start_time"`
	EndTime   time.Time `json:"end_time"`
}

// EventFilters defines which events to include in simulation
type EventFilters struct {
	SourceTypes []string `json:"source_types"` // agent, extension, desktop
	EventTypes  []string `json:"event_types"`  // anomaly, browser, system
	DeviceIDs   []string `json:"device_ids"`
	TenantIDs   []string `json:"tenant_ids"`
	MinSeverity string   `json:"min_severity"`
}

// SimulationConfig controls simulation execution parameters
type SimulationConfig struct {
	MaxEvents       int           `json:"max_events"`       // Limit processing for performance
	BatchSize       int           `json:"batch_size"`       // Events per batch
	TimeoutDuration time.Duration `json:"timeout_duration"` // Max simulation time
	ParallelWorkers int           `json:"parallel_workers"` // Concurrent processing
	EnableMetrics   bool          `json:"enable_metrics"`   // Collect detailed metrics
	DryRun          bool          `json:"dry_run"`          // Don't save results
}

// SimulationResult contains the complete results of a policy simulation
type SimulationResult struct {
	SimulationID       string              `json:"simulation_id"`
	Status             string              `json:"status"` // running, completed, failed, timeout
	StartedAt          time.Time           `json:"started_at"`
	CompletedAt        *time.Time          `json:"completed_at,omitempty"`
	Duration           time.Duration       `json:"duration"`
	EventsProcessed    int                 `json:"events_processed"`
	EventsFiltered     int                 `json:"events_filtered"`
	AnomaliesDetected  int                 `json:"anomalies_detected"`
	AnomaliesPrevented int                 `json:"anomalies_prevented"`
	IncidentsCreated   int                 `json:"incidents_created"`
	IncidentsPrevented int                 `json:"incidents_prevented"`
	PolicyEffects      []PolicyEffect      `json:"policy_effects"`
	PerformanceMetrics PerformanceMetrics  `json:"performance_metrics"`
	ComparisonReport   *ComparisonReport   `json:"comparison_report,omitempty"`
	Errors             []SimulationError   `json:"errors"`
	Warnings           []SimulationWarning `json:"warnings"`
}

// PolicyEffect describes the impact of a specific policy rule
type PolicyEffect struct {
	PolicyID       string   `json:"policy_id"`
	PolicyType     string   `json:"policy_type"`
	EventsAffected int      `json:"events_affected"`
	Action         string   `json:"action"`
	ImpactSummary  string   `json:"impact_summary"`
	Examples       []string `json:"examples"` // Sample event IDs
}

// PerformanceMetrics tracks simulation execution performance
type PerformanceMetrics struct {
	EventsPerSecond     float64 `json:"events_per_second"`
	AvgProcessingTimeMs float64 `json:"avg_processing_time_ms"`
	PeakMemoryUsageMB   float64 `json:"peak_memory_usage_mb"`
	DatabaseQueries     int     `json:"database_queries"`
	QueryTimeMs         float64 `json:"query_time_ms"`
	WorkerUtilization   float64 `json:"worker_utilization"`
}

// ComparisonReport compares simulation results against actual historical outcomes
type ComparisonReport struct {
	BaselineIncidents  int             `json:"baseline_incidents"`
	SimulatedIncidents int             `json:"simulated_incidents"`
	IncidentDelta      int             `json:"incident_delta"`
	BaselineAnomalies  int             `json:"baseline_anomalies"`
	SimulatedAnomalies int             `json:"simulated_anomalies"`
	AnomalyDelta       int             `json:"anomaly_delta"`
	AccuracyMetrics    AccuracyMetrics `json:"accuracy_metrics"`
	ImpactAssessment   string          `json:"impact_assessment"`
}

// AccuracyMetrics measures simulation accuracy against known outcomes
type AccuracyMetrics struct {
	TruePositives  int     `json:"true_positives"`
	FalsePositives int     `json:"false_positives"`
	TrueNegatives  int     `json:"true_negatives"`
	FalseNegatives int     `json:"false_negatives"`
	Precision      float64 `json:"precision"`
	Recall         float64 `json:"recall"`
	F1Score        float64 `json:"f1_score"`
}

// SimulationError represents an error during simulation
type SimulationError struct {
	Timestamp time.Time `json:"timestamp"`
	ErrorType string    `json:"error_type"`
	Message   string    `json:"message"`
	Context   string    `json:"context"`
}

// SimulationWarning represents a warning during simulation
type SimulationWarning struct {
	Timestamp time.Time `json:"timestamp"`
	Warning   string    `json:"warning"`
	Context   string    `json:"context"`
}

// NewPolicySandbox creates a new policy simulation sandbox
func NewPolicySandbox(logger *zap.Logger, storage *storage.Storage) *PolicySandbox {
	return &PolicySandbox{
		logger:  logger.Named("policy_sandbox"),
		storage: storage,
	}
}

// DefaultSimulationConfig returns sensible defaults for simulation
func DefaultSimulationConfig() SimulationConfig {
	return SimulationConfig{
		MaxEvents:       1000000,         // 1M events max per requirements
		BatchSize:       10000,           // Process in 10k batches
		TimeoutDuration: 2 * time.Minute, // 2 minute timeout per requirements
		ParallelWorkers: 4,               // 4 concurrent workers
		EnableMetrics:   true,
		DryRun:          false,
	}
}

// RunSimulation executes a policy simulation on historical data
func (ps *PolicySandbox) RunSimulation(ctx context.Context, request *SimulationRequest) (*SimulationResult, error) {
	ps.logger.Info("Starting policy simulation",
		zap.String("simulation_id", request.SimulationID),
		zap.String("name", request.Name),
		zap.Time("start_time", request.TimeRange.StartTime),
		zap.Time("end_time", request.TimeRange.EndTime))

	result := &SimulationResult{
		SimulationID:  request.SimulationID,
		Status:        "running",
		StartedAt:     time.Now(),
		PolicyEffects: []PolicyEffect{},
		Errors:        []SimulationError{},
		Warnings:      []SimulationWarning{},
	}

	// Set up timeout context
	simCtx, cancel := context.WithTimeout(ctx, request.SimulationConfig.TimeoutDuration)
	defer cancel()

	// Store simulation start
	if !request.SimulationConfig.DryRun {
		if err := ps.storeSimulationStart(simCtx, request, result); err != nil {
			ps.logger.Error("Failed to store simulation start", zap.Error(err))
		}
	}

	// Execute simulation steps
	if err := ps.executeSimulation(simCtx, request, result); err != nil {
		result.Status = "failed"
		result.Errors = append(result.Errors, SimulationError{
			Timestamp: time.Now(),
			ErrorType: "execution_error",
			Message:   err.Error(),
			Context:   "main_execution",
		})
	} else {
		result.Status = "completed"
	}

	// Finalize results
	completedAt := time.Now()
	result.CompletedAt = &completedAt
	result.Duration = completedAt.Sub(result.StartedAt)

	// Store final results
	if !request.SimulationConfig.DryRun {
		if err := ps.storeSimulationResult(simCtx, result); err != nil {
			ps.logger.Error("Failed to store simulation result", zap.Error(err))
		}
	}

	ps.logger.Info("Policy simulation completed",
		zap.String("simulation_id", request.SimulationID),
		zap.String("status", result.Status),
		zap.Duration("duration", result.Duration),
		zap.Int("events_processed", result.EventsProcessed))

	return result, nil
}

// executeSimulation performs the core simulation logic
func (ps *PolicySandbox) executeSimulation(ctx context.Context, request *SimulationRequest, result *SimulationResult) error {
	// Step 1: Load historical events
	events, err := ps.loadHistoricalEvents(ctx, request)
	if err != nil {
		return fmt.Errorf("failed to load historical events: %w", err)
	}

	ps.logger.Info("Loaded historical events",
		zap.String("simulation_id", request.SimulationID),
		zap.Int("event_count", len(events)))

	// Step 2: Apply policy bundle to events
	processedEvents, policyEffects, err := ps.applyPolicyBundle(ctx, events, request.PolicyBundle)
	if err != nil {
		return fmt.Errorf("failed to apply policy bundle: %w", err)
	}

	// Step 3: Simulate anomaly detection with new policies
	anomalies, err := ps.simulateAnomalyDetection(ctx, processedEvents, request.PolicyBundle.AnomalyPolicies)
	if err != nil {
		return fmt.Errorf("failed to simulate anomaly detection: %w", err)
	}

	// Step 4: Generate correlation incidents with simulated data
	incidents, err := ps.simulateIncidentGeneration(ctx, anomalies, processedEvents)
	if err != nil {
		return fmt.Errorf("failed to simulate incident generation: %w", err)
	}

	// Step 5: Compare with baseline (actual historical results)
	comparison, err := ps.generateComparisonReport(ctx, request, anomalies, incidents)
	if err != nil {
		ps.logger.Warn("Failed to generate comparison report", zap.Error(err))
		// Continue without comparison report
	}

	// Step 6: Calculate performance metrics
	perfMetrics := ps.calculatePerformanceMetrics(result.StartedAt, len(events))

	// Update result
	result.EventsProcessed = len(events)
	result.EventsFiltered = len(events) - len(processedEvents)
	result.AnomaliesDetected = len(anomalies)
	result.IncidentsCreated = len(incidents)
	result.PolicyEffects = policyEffects
	result.PerformanceMetrics = perfMetrics
	result.ComparisonReport = comparison

	return nil
}

// loadHistoricalEvents retrieves events from ClickHouse based on filters
func (ps *PolicySandbox) loadHistoricalEvents(ctx context.Context, request *SimulationRequest) ([]map[string]interface{}, error) {
	query := `
		SELECT event_id, timestamp, source_type, event_type, device_id, 
			   tenant_id, payload, labels
		FROM events 
		WHERE timestamp >= ? AND timestamp <= ?`

	args := []interface{}{request.TimeRange.StartTime, request.TimeRange.EndTime}

	// Add filters
	if len(request.EventFilters.SourceTypes) > 0 {
		query += " AND source_type IN (" + ps.buildPlaceholders(len(request.EventFilters.SourceTypes)) + ")"
		for _, st := range request.EventFilters.SourceTypes {
			args = append(args, st)
		}
	}

	if len(request.EventFilters.EventTypes) > 0 {
		query += " AND event_type IN (" + ps.buildPlaceholders(len(request.EventFilters.EventTypes)) + ")"
		for _, et := range request.EventFilters.EventTypes {
			args = append(args, et)
		}
	}

	if len(request.EventFilters.DeviceIDs) > 0 {
		query += " AND device_id IN (" + ps.buildPlaceholders(len(request.EventFilters.DeviceIDs)) + ")"
		for _, did := range request.EventFilters.DeviceIDs {
			args = append(args, did)
		}
	}

	// Add limit
	query += fmt.Sprintf(" ORDER BY timestamp LIMIT %d", request.SimulationConfig.MaxEvents)

	rows, err := ps.storage.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to query events: %w", err)
	}
	defer rows.Close()

	var events []map[string]interface{}
	for rows.Next() {
		var eventID, sourceType, eventType, deviceID, tenantID, payloadJSON, labelsJSON string
		var timestamp time.Time

		err := rows.Scan(&eventID, &timestamp, &sourceType, &eventType, &deviceID, &tenantID, &payloadJSON, &labelsJSON)
		if err != nil {
			return nil, fmt.Errorf("failed to scan event: %w", err)
		}

		var payload, labels map[string]interface{}
		json.Unmarshal([]byte(payloadJSON), &payload)
		json.Unmarshal([]byte(labelsJSON), &labels)

		event := map[string]interface{}{
			"event_id":    eventID,
			"timestamp":   timestamp,
			"source_type": sourceType,
			"event_type":  eventType,
			"device_id":   deviceID,
			"tenant_id":   tenantID,
			"payload":     payload,
			"labels":      labels,
		}
		events = append(events, event)
	}

	return events, nil
}

// applyPolicyBundle applies filter rules to events
func (ps *PolicySandbox) applyPolicyBundle(ctx context.Context, events []map[string]interface{}, bundle PolicyBundle) ([]map[string]interface{}, []PolicyEffect, error) {
	var filteredEvents []map[string]interface{}
	var policyEffects []PolicyEffect

	// Track policy effects
	effectsMap := make(map[string]*PolicyEffect)

	for _, event := range events {
		allowed := true
		appliedRules := []string{}

		// Apply filter rules in priority order
		for _, rule := range bundle.FilterRules {
			if !rule.Enabled {
				continue
			}

			if ps.matchesRule(event, rule) {
				appliedRules = append(appliedRules, rule.RuleID)

				// Track effect
				if effect, exists := effectsMap[rule.RuleID]; exists {
					effect.EventsAffected++
				} else {
					effectsMap[rule.RuleID] = &PolicyEffect{
						PolicyID:       rule.RuleID,
						PolicyType:     "filter_rule",
						EventsAffected: 1,
						Action:         rule.Action,
						ImpactSummary:  fmt.Sprintf("Rule '%s' applied", rule.Name),
						Examples:       []string{event["event_id"].(string)},
					}
				}

				// Apply action
				switch rule.Action {
				case "deny":
					allowed = false
				case "allow":
					allowed = true
				case "flag":
					// Add flag to event metadata
					if labels, ok := event["labels"].(map[string]interface{}); ok {
						labels["flagged_by"] = rule.RuleID
					}
				}
			}
		}

		if allowed {
			filteredEvents = append(filteredEvents, event)
		}
	}

	// Convert effects map to slice
	for _, effect := range effectsMap {
		policyEffects = append(policyEffects, *effect)
	}

	return filteredEvents, policyEffects, nil
}

// matchesRule checks if an event matches a filter rule's conditions
func (ps *PolicySandbox) matchesRule(event map[string]interface{}, rule FilterRule) bool {
	// Check event type match
	if len(rule.EventTypes) > 0 {
		eventType, ok := event["event_type"].(string)
		if !ok {
			return false
		}

		found := false
		for _, et := range rule.EventTypes {
			if et == eventType {
				found = true
				break
			}
		}
		if !found {
			return false
		}
	}

	// Check all conditions
	for _, condition := range rule.Conditions {
		if !ps.evaluateCondition(event, condition) {
			return false
		}
	}

	return true
}

// evaluateCondition evaluates a single policy condition against an event
func (ps *PolicySandbox) evaluateCondition(event map[string]interface{}, condition PolicyCondition) bool {
	// Extract field value from event (supports nested fields)
	fieldValue := ps.extractFieldValue(event, condition.Field)
	if fieldValue == nil {
		return false
	}

	// Evaluate condition based on operator
	switch condition.Operator {
	case "eq":
		return fmt.Sprintf("%v", fieldValue) == fmt.Sprintf("%v", condition.Value)
	case "ne":
		return fmt.Sprintf("%v", fieldValue) != fmt.Sprintf("%v", condition.Value)
	case "gt":
		if fv, ok := fieldValue.(float64); ok {
			if cv, ok := condition.Value.(float64); ok {
				return fv > cv
			}
		}
	case "lt":
		if fv, ok := fieldValue.(float64); ok {
			if cv, ok := condition.Value.(float64); ok {
				return fv < cv
			}
		}
	case "contains":
		return fmt.Sprintf("%v", fieldValue) == fmt.Sprintf("%v", condition.Value)
	case "in":
		if values, ok := condition.Value.([]interface{}); ok {
			for _, v := range values {
				if fmt.Sprintf("%v", fieldValue) == fmt.Sprintf("%v", v) {
					return true
				}
			}
		}
	}

	return false
}

// extractFieldValue extracts a field value from an event, supporting nested paths
func (ps *PolicySandbox) extractFieldValue(event map[string]interface{}, fieldPath string) interface{} {
	// Simple field extraction - could be enhanced to support dot notation
	if value, exists := event[fieldPath]; exists {
		return value
	}

	// Check in payload
	if payload, ok := event["payload"].(map[string]interface{}); ok {
		if value, exists := payload[fieldPath]; exists {
			return value
		}
	}

	// Check in labels
	if labels, ok := event["labels"].(map[string]interface{}); ok {
		if value, exists := labels[fieldPath]; exists {
			return value
		}
	}

	return nil
}

// simulateAnomalyDetection applies anomaly detection policies to events
func (ps *PolicySandbox) simulateAnomalyDetection(ctx context.Context, events []map[string]interface{}, policies []AnomalyPolicy) ([]map[string]interface{}, error) {
	var anomalies []map[string]interface{}

	// Group events by device and metric for anomaly detection
	deviceMetrics := make(map[string]map[string][]map[string]interface{})

	for _, event := range events {
		deviceID, ok := event["device_id"].(string)
		if !ok {
			continue
		}

		payload, ok := event["payload"].(map[string]interface{})
		if !ok {
			continue
		}

		metric, ok := payload["metric"].(string)
		if !ok {
			continue
		}

		if deviceMetrics[deviceID] == nil {
			deviceMetrics[deviceID] = make(map[string][]map[string]interface{})
		}

		deviceMetrics[deviceID][metric] = append(deviceMetrics[deviceID][metric], event)
	}

	// Apply anomaly policies
	for _, policy := range policies {
		if !policy.Enabled {
			continue
		}

		for deviceID, metrics := range deviceMetrics {
			if metricEvents, exists := metrics[policy.MetricName]; exists {
				detectedAnomalies := ps.applyAnomalyPolicy(policy, deviceID, metricEvents)
				anomalies = append(anomalies, detectedAnomalies...)
			}
		}
	}

	return anomalies, nil
}

// applyAnomalyPolicy applies a single anomaly policy to metric events
func (ps *PolicySandbox) applyAnomalyPolicy(policy AnomalyPolicy, deviceID string, events []map[string]interface{}) []map[string]interface{} {
	var anomalies []map[string]interface{}

	// Simple threshold-based detection for simulation
	for _, event := range events {
		payload, ok := event["payload"].(map[string]interface{})
		if !ok {
			continue
		}

		value, ok := payload["value"].(float64)
		if !ok {
			continue
		}

		score, ok := payload["score"].(float64)
		if !ok {
			continue
		}

		// Apply detection logic based on policy
		isAnomaly := false
		switch policy.DetectionType {
		case "threshold":
			isAnomaly = value > policy.Threshold
		case "zscore":
			isAnomaly = score > policy.Threshold
		case "ewma":
			// Simplified - would normally use EWMA calculation
			isAnomaly = score > policy.Threshold
		}

		if isAnomaly {
			anomaly := map[string]interface{}{
				"event_id":    event["event_id"],
				"timestamp":   event["timestamp"],
				"device_id":   deviceID,
				"tenant_id":   event["tenant_id"],
				"source_type": "simulation",
				"event_type":  "anomaly",
				"payload": map[string]interface{}{
					"metric":    policy.MetricName,
					"value":     value,
					"score":     score,
					"policy_id": policy.PolicyID,
					"simulated": true,
				},
				"labels": map[string]interface{}{
					"simulation": "true",
					"policy_id":  policy.PolicyID,
				},
			}
			anomalies = append(anomalies, anomaly)
		}
	}

	return anomalies
}

// simulateIncidentGeneration creates mock incidents from simulated anomalies
func (ps *PolicySandbox) simulateIncidentGeneration(ctx context.Context, anomalies, webEvents []map[string]interface{}) ([]map[string]interface{}, error) {
	var incidents []map[string]interface{}

	// Simple correlation simulation - would use actual correlation service logic
	deviceAnomalies := make(map[string][]map[string]interface{})
	deviceWebEvents := make(map[string][]map[string]interface{})

	// Group by device
	for _, anomaly := range anomalies {
		if deviceID, ok := anomaly["device_id"].(string); ok {
			deviceAnomalies[deviceID] = append(deviceAnomalies[deviceID], anomaly)
		}
	}

	for _, webEvent := range webEvents {
		if deviceID, ok := webEvent["device_id"].(string); ok {
			deviceWebEvents[deviceID] = append(deviceWebEvents[deviceID], webEvent)
		}
	}

	// Create incidents where both anomalies and web events exist
	for deviceID := range deviceAnomalies {
		if webEvts, exists := deviceWebEvents[deviceID]; exists && len(webEvts) > 0 {
			incident := map[string]interface{}{
				"incident_id":          fmt.Sprintf("sim-%s-%d", deviceID, time.Now().Unix()),
				"device_id":            deviceID,
				"incident_type":        "performance",
				"severity":             "medium",
				"correlation_score":    0.75, // Mock score
				"anomaly_count":        len(deviceAnomalies[deviceID]),
				"web_event_count":      len(webEvts),
				"simulated":            true,
				"simulation_timestamp": time.Now(),
			}
			incidents = append(incidents, incident)
		}
	}

	return incidents, nil
}

// generateComparisonReport compares simulation results with historical baselines
func (ps *PolicySandbox) generateComparisonReport(ctx context.Context, request *SimulationRequest, anomalies, incidents []map[string]interface{}) (*ComparisonReport, error) {
	// Query historical baselines for the same time period
	baselineAnomalies, err := ps.getBaselineAnomalies(ctx, request.TimeRange)
	if err != nil {
		return nil, fmt.Errorf("failed to get baseline anomalies: %w", err)
	}

	baselineIncidents, err := ps.getBaselineIncidents(ctx, request.TimeRange)
	if err != nil {
		return nil, fmt.Errorf("failed to get baseline incidents: %w", err)
	}

	// Calculate comparison metrics
	report := &ComparisonReport{
		BaselineIncidents:  len(baselineIncidents),
		SimulatedIncidents: len(incidents),
		IncidentDelta:      len(incidents) - len(baselineIncidents),
		BaselineAnomalies:  len(baselineAnomalies),
		SimulatedAnomalies: len(anomalies),
		AnomalyDelta:       len(anomalies) - len(baselineAnomalies),
	}

	// Generate impact assessment
	if report.IncidentDelta > 0 {
		report.ImpactAssessment = fmt.Sprintf("Policy changes would result in %d additional incidents detected", report.IncidentDelta)
	} else if report.IncidentDelta < 0 {
		report.ImpactAssessment = fmt.Sprintf("Policy changes would prevent %d incidents", -report.IncidentDelta)
	} else {
		report.ImpactAssessment = "No significant change in incident detection"
	}

	// Calculate accuracy metrics (simplified)
	report.AccuracyMetrics = ps.calculateAccuracyMetrics(baselineAnomalies, anomalies)

	return report, nil
}

// Helper methods

func (ps *PolicySandbox) buildPlaceholders(count int) string {
	if count == 0 {
		return ""
	}

	placeholders := "?"
	for i := 1; i < count; i++ {
		placeholders += ",?"
	}
	return placeholders
}

func (ps *PolicySandbox) calculatePerformanceMetrics(startTime time.Time, eventCount int) PerformanceMetrics {
	duration := time.Since(startTime)
	eventsPerSecond := float64(eventCount) / duration.Seconds()

	return PerformanceMetrics{
		EventsPerSecond:     eventsPerSecond,
		AvgProcessingTimeMs: duration.Seconds() * 1000 / float64(eventCount),
		PeakMemoryUsageMB:   50.0,                            // Mock value
		DatabaseQueries:     3,                               // Mock value
		QueryTimeMs:         duration.Seconds() * 1000 * 0.1, // 10% of total time
		WorkerUtilization:   0.85,                            // Mock value
	}
}

func (ps *PolicySandbox) calculateAccuracyMetrics(baseline, simulated []map[string]interface{}) AccuracyMetrics {
	// Simplified accuracy calculation
	tp := min(len(baseline), len(simulated))
	fp := max(0, len(simulated)-len(baseline))
	fn := max(0, len(baseline)-len(simulated))

	precision := float64(tp) / float64(tp+fp)
	recall := float64(tp) / float64(tp+fn)
	f1 := 2 * (precision * recall) / (precision + recall)

	if tp+fp == 0 {
		precision = 0
	}
	if tp+fn == 0 {
		recall = 0
	}
	if precision+recall == 0 {
		f1 = 0
	}

	return AccuracyMetrics{
		TruePositives:  tp,
		FalsePositives: fp,
		TrueNegatives:  0, // Not calculated in this simplified version
		FalseNegatives: fn,
		Precision:      precision,
		Recall:         recall,
		F1Score:        f1,
	}
}

func (ps *PolicySandbox) getBaselineAnomalies(ctx context.Context, timeRange TimeRange) ([]map[string]interface{}, error) {
	// Query actual anomalies from the time range
	query := `
		SELECT COUNT() as anomaly_count
		FROM events 
		WHERE event_type = 'anomaly' 
		  AND timestamp >= ? AND timestamp <= ?`

	row := ps.storage.QueryRowContext(ctx, query, timeRange.StartTime, timeRange.EndTime)

	var count int
	if err := row.Scan(&count); err != nil {
		return nil, err
	}

	// Return mock data for now
	return make([]map[string]interface{}, count), nil
}

func (ps *PolicySandbox) getBaselineIncidents(ctx context.Context, timeRange TimeRange) ([]map[string]interface{}, error) {
	// Query actual incidents from the time range
	query := `
		SELECT COUNT() as incident_count
		FROM correlated_incidents 
		WHERE created_at >= ? AND created_at <= ?`

	row := ps.storage.QueryRowContext(ctx, query, timeRange.StartTime, timeRange.EndTime)

	var count int
	if err := row.Scan(&count); err != nil {
		return nil, err
	}

	// Return mock data for now
	return make([]map[string]interface{}, count), nil
}

// Storage methods

func (ps *PolicySandbox) storeSimulationStart(ctx context.Context, request *SimulationRequest, result *SimulationResult) error {
	// Store simulation metadata - would be implemented with actual storage schema
	ps.logger.Debug("Storing simulation start", zap.String("simulation_id", request.SimulationID))
	return nil
}

func (ps *PolicySandbox) storeSimulationResult(ctx context.Context, result *SimulationResult) error {
	// Store complete simulation results - would be implemented with actual storage schema
	ps.logger.Debug("Storing simulation result", zap.String("simulation_id", result.SimulationID))
	return nil
}

// Helper functions
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}
