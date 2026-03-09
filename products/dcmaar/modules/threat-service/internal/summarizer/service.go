package summarizer

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/services"
	"github.com/samujjwal/dcmaar/apps/server/internal/storage"
)

// SummarizerService orchestrates incident summarization and storage
type SummarizerService struct {
	logger         *zap.Logger
	llmSummarizer  *LLMSummarizer
	correlationSvc *services.CorrelationService
	storage        *storage.Storage
}

// NewSummarizerService creates a new incident summarizer service
func NewSummarizerService(logger *zap.Logger, llmSummarizer *LLMSummarizer, correlationSvc *services.CorrelationService, storage *storage.Storage) *SummarizerService {
	return &SummarizerService{
		logger:         logger.Named("summarizer_service"),
		llmSummarizer:  llmSummarizer,
		correlationSvc: correlationSvc,
		storage:        storage,
	}
}

// SummarizeIncident generates and stores a summary for a correlated incident
func (ss *SummarizerService) SummarizeIncident(ctx context.Context, incidentID string, config SummarizerConfig) (*IncidentSummary, error) {
	ss.logger.Info("Starting incident summarization",
		zap.String("incident_id", incidentID))

	// First, retrieve the incident from correlation service
	incident, err := ss.getIncidentByID(ctx, incidentID)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve incident: %w", err)
	}

	// Check if summary already exists
	existingSummary, err := ss.GetStoredSummary(ctx, incidentID)
	if err == nil && existingSummary != nil {
		ss.logger.Info("Summary already exists for incident",
			zap.String("incident_id", incidentID),
			zap.Time("generated_at", existingSummary.GeneratedAt))
		return existingSummary, nil
	}

	// Generate new summary using LLM
	summary, err := ss.llmSummarizer.GenerateSummary(ctx, incident, config)
	if err != nil {
		return nil, fmt.Errorf("failed to generate summary: %w", err)
	}

	// Store the summary
	if err := ss.StoreSummary(ctx, summary); err != nil {
		ss.logger.Error("Failed to store summary, but returning generated summary",
			zap.String("incident_id", incidentID),
			zap.Error(err))
		// Don't fail the request if storage fails - return the generated summary
	}

	return summary, nil
}

// StoreSummary persists an incident summary to storage
func (ss *SummarizerService) StoreSummary(ctx context.Context, summary *IncidentSummary) error {
	ss.logger.Debug("Storing incident summary",
		zap.String("incident_id", summary.IncidentID))

	// Serialize timeline and recommendations as JSON
	timelineJSON, err := json.Marshal(summary.Timeline)
	if err != nil {
		return fmt.Errorf("failed to marshal timeline: %w", err)
	}

	recommendationsJSON, err := json.Marshal(summary.Recommendations)
	if err != nil {
		return fmt.Errorf("failed to marshal recommendations: %w", err)
	}

	labelsJSON, err := json.Marshal(summary.Labels)
	if err != nil {
		return fmt.Errorf("failed to marshal labels: %w", err)
	}

	query := `
		INSERT INTO incident_summaries (
			incident_id, summary, timeline, root_cause, impact, 
			recommendations, confidence, generated_at, model, 
			tokens_used, labels
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	_, err = ss.storage.ExecContext(ctx, query,
		summary.IncidentID,
		summary.Summary,
		string(timelineJSON),
		summary.RootCause,
		summary.Impact,
		string(recommendationsJSON),
		summary.Confidence,
		summary.GeneratedAt,
		summary.Model,
		summary.TokensUsed,
		string(labelsJSON),
	)

	if err != nil {
		return fmt.Errorf("failed to store incident summary: %w", err)
	}

	ss.logger.Info("Successfully stored incident summary",
		zap.String("incident_id", summary.IncidentID))

	return nil
}

// GetStoredSummary retrieves a stored incident summary
func (ss *SummarizerService) GetStoredSummary(ctx context.Context, incidentID string) (*IncidentSummary, error) {
	query := `
		SELECT incident_id, summary, timeline, root_cause, impact,
			   recommendations, confidence, generated_at, model,
			   tokens_used, labels
		FROM incident_summaries
		WHERE incident_id = ?
		ORDER BY generated_at DESC
		LIMIT 1`

	row := ss.storage.QueryRowContext(ctx, query, incidentID)

	var summary IncidentSummary
	var timelineJSON, recommendationsJSON, labelsJSON string

	err := row.Scan(
		&summary.IncidentID,
		&summary.Summary,
		&timelineJSON,
		&summary.RootCause,
		&summary.Impact,
		&recommendationsJSON,
		&summary.Confidence,
		&summary.GeneratedAt,
		&summary.Model,
		&summary.TokensUsed,
		&labelsJSON,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil // No summary found
		}
		return nil, fmt.Errorf("failed to scan summary: %w", err)
	}

	// Deserialize JSON fields
	if err := json.Unmarshal([]byte(timelineJSON), &summary.Timeline); err != nil {
		ss.logger.Warn("Failed to unmarshal timeline", zap.Error(err))
		summary.Timeline = []TimelineEvent{}
	}

	if err := json.Unmarshal([]byte(recommendationsJSON), &summary.Recommendations); err != nil {
		ss.logger.Warn("Failed to unmarshal recommendations", zap.Error(err))
		summary.Recommendations = []string{}
	}

	if err := json.Unmarshal([]byte(labelsJSON), &summary.Labels); err != nil {
		ss.logger.Warn("Failed to unmarshal labels", zap.Error(err))
		summary.Labels = make(map[string]string)
	}

	return &summary, nil
}

// GetSummariesByTimeRange retrieves summaries within a time range
func (ss *SummarizerService) GetSummariesByTimeRange(ctx context.Context, startTime, endTime time.Time, limit int) ([]*IncidentSummary, error) {
	query := `
		SELECT incident_id, summary, timeline, root_cause, impact,
			   recommendations, confidence, generated_at, model,
			   tokens_used, labels
		FROM incident_summaries
		WHERE generated_at >= ? AND generated_at <= ?
		ORDER BY generated_at DESC
		LIMIT ?`

	rows, err := ss.storage.QueryContext(ctx, query, startTime, endTime, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query summaries: %w", err)
	}
	defer rows.Close()

	var summaries []*IncidentSummary
	for rows.Next() {
		var summary IncidentSummary
		var timelineJSON, recommendationsJSON, labelsJSON string

		err := rows.Scan(
			&summary.IncidentID,
			&summary.Summary,
			&timelineJSON,
			&summary.RootCause,
			&summary.Impact,
			&recommendationsJSON,
			&summary.Confidence,
			&summary.GeneratedAt,
			&summary.Model,
			&summary.TokensUsed,
			&labelsJSON,
		)
		if err != nil {
			ss.logger.Error("Failed to scan summary row", zap.Error(err))
			continue
		}

		// Deserialize JSON fields
		json.Unmarshal([]byte(timelineJSON), &summary.Timeline)
		json.Unmarshal([]byte(recommendationsJSON), &summary.Recommendations)
		json.Unmarshal([]byte(labelsJSON), &summary.Labels)

		summaries = append(summaries, &summary)
	}

	return summaries, nil
}

// AutoSummarizeNewIncidents automatically generates summaries for new incidents
func (ss *SummarizerService) AutoSummarizeNewIncidents(ctx context.Context, config SummarizerConfig) error {
	ss.logger.Info("Starting auto-summarization of new incidents")

	// Get recent incidents without summaries (last 1 hour)
	endTime := time.Now()
	startTime := endTime.Add(-1 * time.Hour)

	// This would need to be implemented in correlation service to find incidents without summaries
	incidents, err := ss.getIncidentsWithoutSummaries(ctx, startTime, endTime)
	if err != nil {
		return fmt.Errorf("failed to get incidents without summaries: %w", err)
	}

	ss.logger.Info("Found incidents needing summarization",
		zap.Int("count", len(incidents)))

	successCount := 0
	for _, incident := range incidents {
		summary, err := ss.llmSummarizer.GenerateSummary(ctx, incident, config)
		if err != nil {
			ss.logger.Error("Failed to generate summary for incident",
				zap.String("incident_id", incident.IncidentID),
				zap.Error(err))
			continue
		}

		if err := ss.StoreSummary(ctx, summary); err != nil {
			ss.logger.Error("Failed to store summary for incident",
				zap.String("incident_id", incident.IncidentID),
				zap.Error(err))
			continue
		}

		successCount++
	}

	ss.logger.Info("Auto-summarization completed",
		zap.Int("total_incidents", len(incidents)),
		zap.Int("successful_summaries", successCount))

	return nil
}

// getIncidentByID retrieves a specific incident (placeholder - would integrate with correlation service)
func (ss *SummarizerService) getIncidentByID(ctx context.Context, incidentID string) (*services.CorrelatedIncident, error) {
	// This would be implemented to query the correlation service or storage directly
	// For now, return an error indicating this needs correlation service integration
	return nil, fmt.Errorf("getIncidentByID not yet implemented - needs correlation service integration")
}

// getIncidentsWithoutSummaries finds incidents that need summarization
func (ss *SummarizerService) getIncidentsWithoutSummaries(ctx context.Context, startTime, endTime time.Time) ([]*services.CorrelatedIncident, error) {
	// This would query for incidents that don't have corresponding summaries
	// For now, return empty slice
	return []*services.CorrelatedIncident{}, nil
}
