package services

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
)

// CorrelationService handles cross-source event correlation between agent anomalies
// and extension latency events to detect systemic performance issues.
type CorrelationService struct {
	logger  *zap.Logger
	storage *storage.Storage
}

// CorrelatedIncident represents a correlated performance incident
type CorrelatedIncident struct {
	IncidentID            string            `json:"incident_id"`
	CreatedAt             time.Time         `json:"created_at"`
	UpdatedAt             time.Time         `json:"updated_at"`
	TenantID              string            `json:"tenant_id"`
	DeviceID              string            `json:"device_id"`
	HostID                string            `json:"host_id"`
	WindowStart           time.Time         `json:"window_start"`
	WindowEnd             time.Time         `json:"window_end"`
	CorrelationScore      float64           `json:"correlation_score"`
	Confidence            float64           `json:"confidence"`
	IncidentType          string            `json:"incident_type"`
	Severity              string            `json:"severity"`
	AnomalyEventIDs       []string          `json:"anomaly_event_ids"`
	WebEventIDs           []string          `json:"web_event_ids"`
	AnomalyCount          int               `json:"anomaly_count"`
	WebEventCount         int               `json:"web_event_count"`
	AvgCPUAnomalyScore    float64           `json:"avg_cpu_anomaly_score"`
	AvgMemoryAnomalyScore float64           `json:"avg_memory_anomaly_score"`
	AvgLatencyMs          float64           `json:"avg_latency_ms"`
	AffectedDomains       []string          `json:"affected_domains"`
	Status                string            `json:"status"`
	ResolvedAt            *time.Time        `json:"resolved_at,omitempty"`
	Labels                map[string]string `json:"labels"`
}

// CorrelationConfig holds configuration for correlation detection
type CorrelationConfig struct {
	TimeWindowMinutes      int     `json:"time_window_minutes"`
	MinCorrelationScore    float64 `json:"min_correlation_score"`
	MinConfidence          float64 `json:"min_confidence"`
	MaxLatencyThresholdMs  float64 `json:"max_latency_threshold_ms"`
	MinAnomalyScore        float64 `json:"min_anomaly_score"`
	CorrelationLookbackHrs int     `json:"correlation_lookback_hrs"`
}

// DefaultCorrelationConfig returns sensible defaults for correlation detection
func DefaultCorrelationConfig() CorrelationConfig {
	return CorrelationConfig{
		TimeWindowMinutes:      5,    // 5-minute correlation window
		MinCorrelationScore:    0.6,  // Minimum 60% correlation score
		MinConfidence:          0.7,  // Minimum 70% confidence
		MaxLatencyThresholdMs:  3000, // Only consider slow requests (>3s)
		MinAnomalyScore:        2.0,  // Only consider significant anomalies (>2 std dev)
		CorrelationLookbackHrs: 1,    // Look back 1 hour for events
	}
}

// NewCorrelationService creates a new correlation service
func NewCorrelationService(logger *zap.Logger, storage *storage.Storage) *CorrelationService {
	return &CorrelationService{
		logger:  logger.Named("correlation"),
		storage: storage,
	}
}

// AnalyzeForCorrelations scans recent events for correlation patterns
func (cs *CorrelationService) AnalyzeForCorrelations(ctx context.Context, config CorrelationConfig) ([]*CorrelatedIncident, error) {
	cs.logger.Info("Starting correlation analysis",
		zap.Int("window_minutes", config.TimeWindowMinutes),
		zap.Float64("min_score", config.MinCorrelationScore))

	// Define the time range for analysis
	endTime := time.Now()
	startTime := endTime.Add(-time.Duration(config.CorrelationLookbackHrs) * time.Hour)

	// Fetch anomaly events
	anomalyEvents, err := cs.fetchAnomalyEvents(ctx, startTime, endTime, config.MinAnomalyScore)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch anomaly events: %w", err)
	}

	// Fetch web events
	webEvents, err := cs.fetchWebEvents(ctx, startTime, endTime, config.MaxLatencyThresholdMs)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch web events: %w", err)
	}

	cs.logger.Info("Fetched events for correlation",
		zap.Int("anomaly_events", len(anomalyEvents)),
		zap.Int("web_events", len(webEvents)))

	// Perform correlation analysis
	correlations := cs.correlateEvents(anomalyEvents, webEvents, config)

	// Filter by confidence and score thresholds
	var validCorrelations []*CorrelatedIncident
	for _, correlation := range correlations {
		if correlation.CorrelationScore >= config.MinCorrelationScore &&
			correlation.Confidence >= config.MinConfidence {
			validCorrelations = append(validCorrelations, correlation)
		}
	}

	cs.logger.Info("Found valid correlations",
		zap.Int("total_analyzed", len(correlations)),
		zap.Int("valid_correlations", len(validCorrelations)))

	return validCorrelations, nil
}

// StoreCorrelatedIncident persists a correlated incident to storage
func (cs *CorrelationService) StoreCorrelatedIncident(ctx context.Context, incident *CorrelatedIncident) error {
	cs.logger.Info("Storing correlated incident",
		zap.String("incident_id", incident.IncidentID),
		zap.String("device_id", incident.DeviceID),
		zap.Float64("correlation_score", incident.CorrelationScore))

	query := `
		INSERT INTO correlated_incidents (
			incident_id, created_at, updated_at, tenant_id, device_id, host_id,
			window_start, window_end, correlation_score, confidence, incident_type,
			severity, anomaly_event_ids, web_event_ids, anomaly_count, web_event_count,
			avg_cpu_anomaly_score, avg_memory_anomaly_score, avg_latency_ms,
			affected_domains, status, labels
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	labelsJSON, _ := json.Marshal(incident.Labels)

	_, err := cs.storage.ExecContext(ctx, query,
		incident.IncidentID,
		incident.CreatedAt,
		incident.UpdatedAt,
		incident.TenantID,
		incident.DeviceID,
		incident.HostID,
		incident.WindowStart,
		incident.WindowEnd,
		incident.CorrelationScore,
		incident.Confidence,
		incident.IncidentType,
		incident.Severity,
		incident.AnomalyEventIDs,
		incident.WebEventIDs,
		incident.AnomalyCount,
		incident.WebEventCount,
		incident.AvgCPUAnomalyScore,
		incident.AvgMemoryAnomalyScore,
		incident.AvgLatencyMs,
		incident.AffectedDomains,
		incident.Status,
		string(labelsJSON),
	)

	if err != nil {
		return fmt.Errorf("failed to store correlated incident: %w", err)
	}

	return nil
}

// GetCorrelatedIncidentsByWindow retrieves incidents within a time window
func (cs *CorrelationService) GetCorrelatedIncidentsByWindow(ctx context.Context, tenantID, deviceID string, startTime, endTime time.Time) ([]*CorrelatedIncident, error) {
	query := `
		SELECT incident_id, created_at, updated_at, tenant_id, device_id, host_id,
			   window_start, window_end, correlation_score, confidence, incident_type,
			   severity, anomaly_event_ids, web_event_ids, anomaly_count, web_event_count,
			   avg_cpu_anomaly_score, avg_memory_anomaly_score, avg_latency_ms,
			   affected_domains, status, resolved_at, labels
		FROM correlated_incidents
		WHERE tenant_id = ? AND device_id = ? 
			  AND created_at >= ? AND created_at <= ?
		ORDER BY created_at DESC`

	rows, err := cs.storage.QueryContext(ctx, query, tenantID, deviceID, startTime, endTime)
	if err != nil {
		return nil, fmt.Errorf("failed to query correlated incidents: %w", err)
	}
	defer rows.Close()

	var incidents []*CorrelatedIncident
	for rows.Next() {
		incident := &CorrelatedIncident{}
		var labelsJSON string
		var resolvedAt *time.Time

		err := rows.Scan(
			&incident.IncidentID,
			&incident.CreatedAt,
			&incident.UpdatedAt,
			&incident.TenantID,
			&incident.DeviceID,
			&incident.HostID,
			&incident.WindowStart,
			&incident.WindowEnd,
			&incident.CorrelationScore,
			&incident.Confidence,
			&incident.IncidentType,
			&incident.Severity,
			&incident.AnomalyEventIDs,
			&incident.WebEventIDs,
			&incident.AnomalyCount,
			&incident.WebEventCount,
			&incident.AvgCPUAnomalyScore,
			&incident.AvgMemoryAnomalyScore,
			&incident.AvgLatencyMs,
			&incident.AffectedDomains,
			&incident.Status,
			&resolvedAt,
			&labelsJSON,
		)
		if err != nil {
			return nil, fmt.Errorf("failed to scan incident row: %w", err)
		}

		incident.ResolvedAt = resolvedAt

		// Parse labels JSON
		if labelsJSON != "" {
			err = json.Unmarshal([]byte(labelsJSON), &incident.Labels)
			if err != nil {
				cs.logger.Warn("Failed to parse incident labels", zap.Error(err))
				incident.Labels = make(map[string]string)
			}
		}

		incidents = append(incidents, incident)
	}

	return incidents, nil
}

// fetchAnomalyEvents retrieves anomaly events from the unified events table
func (cs *CorrelationService) fetchAnomalyEvents(ctx context.Context, startTime, endTime time.Time, minAnomalyScore float64) ([]map[string]interface{}, error) {
	query := `
		SELECT event_id, timestamp, device_id, tenant_id, payload, labels
		FROM events
		WHERE source_type = 'agent' 
			  AND event_type = 'anomaly'
			  AND timestamp >= ? AND timestamp <= ?
			  AND toFloat64OrNull(JSONExtractString(payload, 'score')) >= ?
		ORDER BY timestamp ASC`

	rows, err := cs.storage.QueryContext(ctx, query, startTime, endTime, minAnomalyScore)
	if err != nil {
		return nil, fmt.Errorf("failed to query anomaly events: %w", err)
	}
	defer rows.Close()

	var events []map[string]interface{}
	for rows.Next() {
		var eventID, deviceID, tenantID, payloadJSON, labelsJSON string
		var timestamp time.Time

		err := rows.Scan(&eventID, &timestamp, &deviceID, &tenantID, &payloadJSON, &labelsJSON)
		if err != nil {
			return nil, fmt.Errorf("failed to scan anomaly event: %w", err)
		}

		var payload, labels map[string]interface{}
		json.Unmarshal([]byte(payloadJSON), &payload)
		json.Unmarshal([]byte(labelsJSON), &labels)

		event := map[string]interface{}{
			"event_id":  eventID,
			"timestamp": timestamp,
			"device_id": deviceID,
			"tenant_id": tenantID,
			"payload":   payload,
			"labels":    labels,
		}
		events = append(events, event)
	}

	return events, nil
}

// fetchWebEvents retrieves web events from the browser_events table
func (cs *CorrelationService) fetchWebEvents(ctx context.Context, startTime, endTime time.Time, minLatencyMs float64) ([]map[string]interface{}, error) {
	query := `
		SELECT event_id, timestamp, device_id, tenant_id, url, domain, latency_ms, status_code
		FROM browser_events
		WHERE timestamp >= ? AND timestamp <= ?
			  AND latency_ms >= ?
		ORDER BY timestamp ASC`

	rows, err := cs.storage.QueryContext(ctx, query, startTime, endTime, minLatencyMs)
	if err != nil {
		return nil, fmt.Errorf("failed to query web events: %w", err)
	}
	defer rows.Close()

	var events []map[string]interface{}
	for rows.Next() {
		var eventID, deviceID, tenantID, url, domain string
		var timestamp time.Time
		var latencyMs float64
		var statusCode int32

		err := rows.Scan(&eventID, &timestamp, &deviceID, &tenantID, &url, &domain, &latencyMs, &statusCode)
		if err != nil {
			return nil, fmt.Errorf("failed to scan web event: %w", err)
		}

		event := map[string]interface{}{
			"event_id":    eventID,
			"timestamp":   timestamp,
			"device_id":   deviceID,
			"tenant_id":   tenantID,
			"url":         url,
			"domain":      domain,
			"latency_ms":  latencyMs,
			"status_code": statusCode,
		}
		events = append(events, event)
	}

	return events, nil
}

// correlateEvents performs the core correlation logic between anomaly and web events
func (cs *CorrelationService) correlateEvents(anomalyEvents, webEvents []map[string]interface{}, config CorrelationConfig) []*CorrelatedIncident {
	var correlations []*CorrelatedIncident
	windowDuration := time.Duration(config.TimeWindowMinutes) * time.Minute

	// Group events by device_id for correlation
	deviceAnomalies := make(map[string][]map[string]interface{})
	deviceWebEvents := make(map[string][]map[string]interface{})

	// Group anomaly events by device
	for _, event := range anomalyEvents {
		deviceID := event["device_id"].(string)
		deviceAnomalies[deviceID] = append(deviceAnomalies[deviceID], event)
	}

	// Group web events by device
	for _, event := range webEvents {
		deviceID := event["device_id"].(string)
		deviceWebEvents[deviceID] = append(deviceWebEvents[deviceID], event)
	}

	// Correlate events for each device
	for deviceID := range deviceAnomalies {
		anomalies := deviceAnomalies[deviceID]
		webEvts := deviceWebEvents[deviceID]

		if len(webEvts) == 0 {
			continue // No web events to correlate with
		}

		// Find time windows where both anomalies and web events occur
		correlatedWindows := cs.findTimeWindows(anomalies, webEvts, windowDuration)

		for _, window := range correlatedWindows {
			correlation := cs.calculateCorrelation(window.anomalies, window.webEvents, deviceID, window.start, window.end)
			if correlation != nil {
				correlations = append(correlations, correlation)
			}
		}
	}

	return correlations
}

// timeWindow represents a correlation time window
type timeWindow struct {
	start     time.Time
	end       time.Time
	anomalies []map[string]interface{}
	webEvents []map[string]interface{}
}

// findTimeWindows identifies overlapping time windows containing both anomalies and web events
func (cs *CorrelationService) findTimeWindows(anomalies, webEvents []map[string]interface{}, windowDuration time.Duration) []timeWindow {
	var windows []timeWindow

	for _, anomaly := range anomalies {
		anomalyTime := anomaly["timestamp"].(time.Time)
		windowStart := anomalyTime.Add(-windowDuration / 2)
		windowEnd := anomalyTime.Add(windowDuration / 2)

		var windowAnomalies []map[string]interface{}
		var windowWebEvents []map[string]interface{}

		// Find all anomalies in this window
		for _, a := range anomalies {
			aTime := a["timestamp"].(time.Time)
			if aTime.After(windowStart) && aTime.Before(windowEnd) {
				windowAnomalies = append(windowAnomalies, a)
			}
		}

		// Find all web events in this window
		for _, w := range webEvents {
			wTime := w["timestamp"].(time.Time)
			if wTime.After(windowStart) && wTime.Before(windowEnd) {
				windowWebEvents = append(windowWebEvents, w)
			}
		}

		// Only create window if both types of events are present
		if len(windowAnomalies) > 0 && len(windowWebEvents) > 0 {
			windows = append(windows, timeWindow{
				start:     windowStart,
				end:       windowEnd,
				anomalies: windowAnomalies,
				webEvents: windowWebEvents,
			})
		}
	}

	return windows
}

// calculateCorrelation computes correlation metrics for a time window
func (cs *CorrelationService) calculateCorrelation(anomalies, webEvents []map[string]interface{}, deviceID string, windowStart, windowEnd time.Time) *CorrelatedIncident {
	if len(anomalies) == 0 || len(webEvents) == 0 {
		return nil
	}

	// Extract anomaly scores and web latencies
	var cpuScores, memoryScores, latencies []float64
	var anomalyEventIDs, webEventIDs []string
	var domains []string
	domainSet := make(map[string]bool)

	for _, anomaly := range anomalies {
		anomalyEventIDs = append(anomalyEventIDs, anomaly["event_id"].(string))

		payload := anomaly["payload"].(map[string]interface{})
		if metric, ok := payload["metric"].(string); ok {
			if score, ok := payload["score"].(float64); ok {
				if metric == "cpu_usage" {
					cpuScores = append(cpuScores, score)
				} else if metric == "mem_usage" {
					memoryScores = append(memoryScores, score)
				}
			}
		}
	}

	for _, webEvent := range webEvents {
		webEventIDs = append(webEventIDs, webEvent["event_id"].(string))
		latencies = append(latencies, webEvent["latency_ms"].(float64))

		if domain, ok := webEvent["domain"].(string); ok && domain != "" {
			if !domainSet[domain] {
				domains = append(domains, domain)
				domainSet[domain] = true
			}
		}
	}

	// Calculate correlation score (simplified Pearson correlation)
	correlationScore := cs.calculatePearsonCorrelation(cpuScores, latencies)
	if math.IsNaN(correlationScore) {
		correlationScore = 0.0
	}

	// Calculate confidence based on sample size and temporal overlap
	confidence := cs.calculateConfidence(len(anomalies), len(webEvents), windowStart, windowEnd)

	// Determine incident type and severity
	incidentType := cs.determineIncidentType(cpuScores, memoryScores, latencies)
	severity := cs.determineSeverity(cpuScores, memoryScores, latencies, correlationScore)

	// Get tenant_id from first event (assuming same tenant for device)
	tenantID := "default"
	if len(anomalies) > 0 {
		if tid, ok := anomalies[0]["tenant_id"].(string); ok {
			tenantID = tid
		}
	}

	incident := &CorrelatedIncident{
		IncidentID:            uuid.New().String(),
		CreatedAt:             time.Now(),
		UpdatedAt:             time.Now(),
		TenantID:              tenantID,
		DeviceID:              deviceID,
		HostID:                deviceID, // Assuming device_id maps to host_id
		WindowStart:           windowStart,
		WindowEnd:             windowEnd,
		CorrelationScore:      math.Abs(correlationScore), // Use absolute value
		Confidence:            confidence,
		IncidentType:          incidentType,
		Severity:              severity,
		AnomalyEventIDs:       anomalyEventIDs,
		WebEventIDs:           webEventIDs,
		AnomalyCount:          len(anomalies),
		WebEventCount:         len(webEvents),
		AvgCPUAnomalyScore:    cs.average(cpuScores),
		AvgMemoryAnomalyScore: cs.average(memoryScores),
		AvgLatencyMs:          cs.average(latencies),
		AffectedDomains:       domains,
		Status:                "active",
		Labels: map[string]string{
			"correlation_type": "agent_web",
			"auto_generated":   "true",
		},
	}

	return incident
}

// calculatePearsonCorrelation computes Pearson correlation coefficient
func (cs *CorrelationService) calculatePearsonCorrelation(x, y []float64) float64 {
	if len(x) != len(y) || len(x) < 2 {
		return 0.0
	}

	meanX := cs.average(x)
	meanY := cs.average(y)

	var numerator, sumXX, sumYY float64
	for i := 0; i < len(x); i++ {
		deltaX := x[i] - meanX
		deltaY := y[i] - meanY
		numerator += deltaX * deltaY
		sumXX += deltaX * deltaX
		sumYY += deltaY * deltaY
	}

	denominator := math.Sqrt(sumXX * sumYY)
	if denominator == 0 {
		return 0.0
	}

	return numerator / denominator
}

// calculateConfidence estimates confidence based on sample size and temporal proximity
func (cs *CorrelationService) calculateConfidence(anomalyCount, webEventCount int, windowStart, windowEnd time.Time) float64 {
	// Base confidence on sample size
	sampleScore := math.Min(float64(anomalyCount+webEventCount)/10.0, 1.0)

	// Bonus for tight temporal clustering
	windowDuration := windowEnd.Sub(windowStart).Minutes()
	temporalScore := math.Max(0.0, 1.0-windowDuration/30.0) // Reduce confidence for windows > 30 min

	// Balance Score
	balanceScore := 1.0 - math.Abs(float64(anomalyCount-webEventCount))/float64(anomalyCount+webEventCount)

	return (sampleScore + temporalScore + balanceScore) / 3.0
}

// determineIncidentType classifies the type of incident based on metrics
func (cs *CorrelationService) determineIncidentType(cpuScores, memoryScores, latencies []float64) string {
	hasCPU := len(cpuScores) > 0 && cs.average(cpuScores) > 2.0
	hasMemory := len(memoryScores) > 0 && cs.average(memoryScores) > 2.0
	hasLatency := len(latencies) > 0 && cs.average(latencies) > 3000

	if hasCPU && hasMemory && hasLatency {
		return "mixed"
	} else if hasCPU || hasMemory {
		return "resource"
	} else if hasLatency {
		return "performance"
	}
	return "performance"
}

// determineSeverity calculates incident severity based on metric values
func (cs *CorrelationService) determineSeverity(cpuScores, memoryScores, latencies []float64, correlationScore float64) string {
	avgCPU := cs.average(cpuScores)
	avgMemory := cs.average(memoryScores)
	avgLatency := cs.average(latencies)

	score := 0.0
	if avgCPU > 3.0 {
		score += 2.0
	} else if avgCPU > 2.0 {
		score += 1.0
	}

	if avgMemory > 3.0 {
		score += 2.0
	} else if avgMemory > 2.0 {
		score += 1.0
	}

	if avgLatency > 10000 {
		score += 2.0
	} else if avgLatency > 5000 {
		score += 1.0
	}

	// Boost severity for high correlation
	if correlationScore > 0.8 {
		score += 1.0
	}

	if score >= 4.0 {
		return "critical"
	} else if score >= 2.5 {
		return "high"
	} else if score >= 1.0 {
		return "medium"
	}
	return "low"
}

// average calculates the mean of a slice of float64 values
func (cs *CorrelationService) average(values []float64) float64 {
	if len(values) == 0 {
		return 0.0
	}

	sum := 0.0
	for _, v := range values {
		sum += v
	}
	return sum / float64(len(values))
}
