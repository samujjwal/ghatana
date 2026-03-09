// Package explainer provides "What Changed?" analysis for incident RCA
// Implements Capability 6: "What Changed?" Explainer from Horizontal Slice AI Plan #3
package explainer

import (
	"context"
	"fmt"
	"sort"
	"time"
)

// Change represents a configuration, version, or policy change
type Change struct {
	ID          string                 `json:"id"`
	Type        string                 `json:"type"`      // "config", "version", "policy", "deployment"
	Component   string                 `json:"component"` // "agent", "server", "extension", etc.
	Description string                 `json:"description"`
	Timestamp   time.Time              `json:"timestamp"`
	Author      string                 `json:"author"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// ChangeCorrelation represents the correlation between a change and an incident
type ChangeCorrelation struct {
	Change             Change   `json:"change"`
	Rank               int      `json:"rank"`          // 1 = most likely cause
	Score              float64  `json:"score"`         // 0.0 to 1.0
	TimeDistance       int64    `json:"time_distance"` // milliseconds between change and incident
	SupportingEvidence []string `json:"supporting_evidence"`
}

// IncidentChanges represents all changes correlated with an incident
type IncidentChanges struct {
	IncidentID    string              `json:"incident_id"`
	IncidentStart time.Time           `json:"incident_start"`
	Changes       []ChangeCorrelation `json:"changes"`
	WindowStart   time.Time           `json:"window_start"`
	WindowEnd     time.Time           `json:"window_end"`
}

// Explainer analyzes changes around incident timeframes
type Explainer struct {
	changeWindow time.Duration // How far back to look for changes
}

// NewExplainer creates a new change explainer
func NewExplainer() *Explainer {
	return &Explainer{
		changeWindow: 30 * time.Minute, // Look 30 minutes before incident
	}
}

// ExplainIncident analyzes what changed before an incident occurred
func (e *Explainer) ExplainIncident(ctx context.Context, incidentID string, incidentStart time.Time) (*IncidentChanges, error) {
	windowStart := incidentStart.Add(-e.changeWindow)
	windowEnd := incidentStart.Add(5 * time.Minute) // Small buffer after incident start

	// Get all changes in the time window
	changes, err := e.getChangesInWindow(ctx, windowStart, windowEnd)
	if err != nil {
		return nil, fmt.Errorf("failed to get changes: %w", err)
	}

	// Correlate changes with incident
	correlations := e.correlateChanges(changes, incidentStart)

	// Sort by correlation score (highest first)
	sort.Slice(correlations, func(i, j int) bool {
		return correlations[i].Score > correlations[j].Score
	})

	// Assign ranks
	for i := range correlations {
		correlations[i].Rank = i + 1
	}

	return &IncidentChanges{
		IncidentID:    incidentID,
		IncidentStart: incidentStart,
		Changes:       correlations,
		WindowStart:   windowStart,
		WindowEnd:     windowEnd,
	}, nil
}

// getChangesInWindow retrieves all changes within a time window
func (e *Explainer) getChangesInWindow(ctx context.Context, start, end time.Time) ([]Change, error) {
	// In a real implementation, this would query the database
	// For now, return mock data
	changes := []Change{
		{
			ID:          "change-001",
			Type:        "deployment",
			Component:   "server",
			Description: "Deploy server v1.2.3 with new query engine",
			Timestamp:   start.Add(10 * time.Minute),
			Author:      "deploy-bot",
			Metadata: map[string]interface{}{
				"version":     "v1.2.3",
				"commit_hash": "abc123def456",
				"environment": "production",
			},
		},
		{
			ID:          "change-002",
			Type:        "config",
			Component:   "agent",
			Description: "Update sampling rate from 0.1 to 0.8",
			Timestamp:   start.Add(15 * time.Minute),
			Author:      "admin@example.com",
			Metadata: map[string]interface{}{
				"old_value":   0.1,
				"new_value":   0.8,
				"config_path": "sampling.rate",
			},
		},
		{
			ID:          "change-003",
			Type:        "policy",
			Component:   "extension",
			Description: "Add new domain to allowlist: analytics.newservice.com",
			Timestamp:   start.Add(20 * time.Minute),
			Author:      "security-team",
			Metadata: map[string]interface{}{
				"domain": "analytics.newservice.com",
				"action": "add_to_allowlist",
			},
		},
	}

	// Filter changes within the window
	var result []Change
	for _, change := range changes {
		if change.Timestamp.After(start) && change.Timestamp.Before(end) {
			result = append(result, change)
		}
	}

	return result, nil
}

// correlateChanges calculates correlation scores between changes and incident
func (e *Explainer) correlateChanges(changes []Change, incidentStart time.Time) []ChangeCorrelation {
	var correlations []ChangeCorrelation

	for _, change := range changes {
		correlation := e.calculateCorrelation(change, incidentStart)
		correlations = append(correlations, correlation)
	}

	return correlations
}

// calculateCorrelation calculates how likely a change is to have caused an incident
func (e *Explainer) calculateCorrelation(change Change, incidentStart time.Time) ChangeCorrelation {
	timeDistance := incidentStart.Sub(change.Timestamp).Milliseconds()

	// Base score calculation
	score := 0.0
	evidence := []string{}

	// Time proximity scoring (closer = higher score)
	if timeDistance < 5*60*1000 { // Within 5 minutes
		score += 0.8
		evidence = append(evidence, "Very recent change (within 5 minutes)")
	} else if timeDistance < 15*60*1000 { // Within 15 minutes
		score += 0.6
		evidence = append(evidence, "Recent change (within 15 minutes)")
	} else if timeDistance < 30*60*1000 { // Within 30 minutes
		score += 0.3
		evidence = append(evidence, "Change within 30 minutes")
	}

	// Change type impact scoring
	switch change.Type {
	case "deployment":
		score += 0.7
		evidence = append(evidence, "Deployment can introduce breaking changes")
	case "config":
		score += 0.5
		evidence = append(evidence, "Configuration change can affect behavior")
	case "policy":
		score += 0.4
		evidence = append(evidence, "Policy change can alter access patterns")
	case "version":
		score += 0.6
		evidence = append(evidence, "Version upgrade can introduce regressions")
	}

	// Component impact scoring
	switch change.Component {
	case "server":
		score += 0.6
		evidence = append(evidence, "Server changes affect core functionality")
	case "agent":
		score += 0.5
		evidence = append(evidence, "Agent changes affect data collection")
	case "extension":
		score += 0.3
		evidence = append(evidence, "Extension changes affect browser monitoring")
	}

	// Metadata-based evidence
	if metadata, ok := change.Metadata["environment"].(string); ok && metadata == "production" {
		score += 0.2
		evidence = append(evidence, "Production environment change")
	}

	// Specific change patterns that commonly cause issues
	if change.Type == "config" {
		if oldVal, hasOld := change.Metadata["old_value"]; hasOld {
			if newVal, hasNew := change.Metadata["new_value"]; hasNew {
				// Large changes in numeric values
				if oldFloat, ok := oldVal.(float64); ok {
					if newFloat, ok := newVal.(float64); ok {
						ratio := newFloat / oldFloat
						if ratio > 2.0 || ratio < 0.5 {
							score += 0.3
							evidence = append(evidence, fmt.Sprintf("Significant value change: %.2f to %.2f", oldFloat, newFloat))
						}
					}
				}
			}
		}
	}

	// Cap score at 1.0
	if score > 1.0 {
		score = 1.0
	}

	return ChangeCorrelation{
		Change:             change,
		Score:              score,
		TimeDistance:       timeDistance,
		SupportingEvidence: evidence,
	}
}

// GetTopChanges returns the most likely changes that caused an incident
func (ic *IncidentChanges) GetTopChanges(limit int) []ChangeCorrelation {
	if len(ic.Changes) <= limit {
		return ic.Changes
	}
	return ic.Changes[:limit]
}
