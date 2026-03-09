package incidents

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3"
)

// RootCauseTemplateService provides root cause analysis using learned templates
// Implements Capability 2: Root-Cause Ranking Templates from Horizontal Slice AI Plan #5
type RootCauseTemplateService struct {
	db        *sql.DB
	templates map[string]*CausalTemplate
}

// CausalTemplate represents a learned causal pattern
type CausalTemplate struct {
	ID                   string                 `json:"id"`
	Name                 string                 `json:"name"`
	Description          string                 `json:"description"`
	Pattern              []string               `json:"pattern"`
	Conditions           map[string]interface{} `json:"conditions"`
	Confidence           float64                `json:"confidence"`
	Support              int                    `json:"support"`
	Precision            float64                `json:"precision"`
	Recall               float64                `json:"recall"`
	Components           []string               `json:"components"`
	TimeWindowMinutes    int                    `json:"time_window_minutes"`
	SeverityDistribution map[int]float64        `json:"severity_distribution"`
	Tags                 []string               `json:"tags"`
	CreatedAt            time.Time              `json:"created_at"`
	LastUpdated          time.Time              `json:"last_updated"`
	Version              string                 `json:"version"`
}

// RootCausePrediction represents a predicted root cause
type RootCausePrediction struct {
	TemplateID       string   `json:"template_id"`
	RootCauseType    string   `json:"root_cause_type"`
	Description      string   `json:"description"`
	Confidence       float64  `json:"confidence"`
	Evidence         []string `json:"evidence"`
	SuggestedActions []string `json:"suggested_actions"`
	SimilarIncidents []string `json:"similar_incidents"`
	EstimatedTTL     int      `json:"estimated_ttl"`
	Rank             int      `json:"rank"`
}

// IncidentAnalysisRequest represents an incident for root cause analysis
type IncidentAnalysisRequest struct {
	IncidentID       string                 `json:"incident_id"`
	Components       []string               `json:"components"`
	EventTypes       []string               `json:"event_types"`
	EventSequence    []EventSequenceItem    `json:"event_sequence"`
	Severity         int                    `json:"severity"`
	DurationMinutes  int                    `json:"duration_minutes"`
	Tags             []string               `json:"tags"`
	ErrorMessages    []string               `json:"error_messages"`
	AffectedServices []string               `json:"affected_services"`
	TimeOfDay        int                    `json:"time_of_day"`
	DayOfWeek        int                    `json:"day_of_week"`
	Metadata         map[string]interface{} `json:"metadata"`
}

// EventSequenceItem represents an event in a temporal sequence
type EventSequenceItem struct {
	EventType        string `json:"event_type"`
	MinutesFromStart int    `json:"minutes_from_start"`
}

// TemplateMatchResult represents how well an incident matches a template
type TemplateMatchResult struct {
	Template        *CausalTemplate `json:"template"`
	MatchScore      float64         `json:"match_score"`
	MatchedElements []string        `json:"matched_elements"`
	MissingElements []string        `json:"missing_elements"`
}

// NewRootCauseTemplateService creates a new root cause template service
func NewRootCauseTemplateService(dbPath string) (*RootCauseTemplateService, error) {
	db, err := sql.Open("sqlite3", dbPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	service := &RootCauseTemplateService{
		db:        db,
		templates: make(map[string]*CausalTemplate),
	}

	// Initialize database schema
	if err := service.initDB(); err != nil {
		return nil, fmt.Errorf("failed to initialize database: %w", err)
	}

	// Load templates into memory
	if err := service.loadTemplates(); err != nil {
		return nil, fmt.Errorf("failed to load templates: %w", err)
	}

	return service, nil
}

// initDB creates the necessary database tables
func (s *RootCauseTemplateService) initDB() error {
	schema := `
	CREATE TABLE IF NOT EXISTS causal_templates (
		id TEXT PRIMARY KEY,
		name TEXT NOT NULL,
		description TEXT,
		pattern TEXT,
		conditions TEXT,
		confidence REAL,
		support INTEGER,
		precision_score REAL,
		recall_score REAL,
		components TEXT,
		time_window_minutes INTEGER,
		severity_distribution TEXT,
		tags TEXT,
		created_at TEXT,
		last_updated TEXT,
		version TEXT
	);

	CREATE TABLE IF NOT EXISTS incident_predictions (
		id TEXT PRIMARY KEY,
		incident_id TEXT NOT NULL,
		template_id TEXT,
		root_cause_type TEXT,
		confidence REAL,
		evidence TEXT,
		suggested_actions TEXT,
		predicted_at TEXT,
		actual_root_cause TEXT,
		feedback_score INTEGER,
		FOREIGN KEY (template_id) REFERENCES causal_templates(id)
	);

	CREATE INDEX IF NOT EXISTS idx_predictions_incident ON incident_predictions(incident_id);
	CREATE INDEX IF NOT EXISTS idx_predictions_template ON incident_predictions(template_id);
	`

	_, err := s.db.Exec(schema)
	return err
}

// loadTemplates loads all templates from database into memory
func (s *RootCauseTemplateService) loadTemplates() error {
	rows, err := s.db.Query(`
		SELECT id, name, description, pattern, conditions, confidence, support,
		       precision_score, recall_score, components, time_window_minutes,
		       severity_distribution, tags, created_at, last_updated, version
		FROM causal_templates
	`)
	if err != nil {
		return err
	}
	defer rows.Close()

	for rows.Next() {
		var template CausalTemplate
		var patternJSON, conditionsJSON, componentsJSON, severityJSON, tagsJSON string
		var createdAtStr, lastUpdatedStr string

		err := rows.Scan(
			&template.ID, &template.Name, &template.Description,
			&patternJSON, &conditionsJSON, &template.Confidence,
			&template.Support, &template.Precision, &template.Recall,
			&componentsJSON, &template.TimeWindowMinutes,
			&severityJSON, &tagsJSON, &createdAtStr, &lastUpdatedStr,
			&template.Version,
		)
		if err != nil {
			continue // Skip malformed templates
		}

		// Parse JSON fields
		json.Unmarshal([]byte(patternJSON), &template.Pattern)
		json.Unmarshal([]byte(conditionsJSON), &template.Conditions)
		json.Unmarshal([]byte(componentsJSON), &template.Components)
		json.Unmarshal([]byte(severityJSON), &template.SeverityDistribution)
		json.Unmarshal([]byte(tagsJSON), &template.Tags)

		// Parse timestamps
		if createdAt, err := time.Parse(time.RFC3339, createdAtStr); err == nil {
			template.CreatedAt = createdAt
		}
		if lastUpdated, err := time.Parse(time.RFC3339, lastUpdatedStr); err == nil {
			template.LastUpdated = lastUpdated
		}

		s.templates[template.ID] = &template
	}

	return rows.Err()
}

// PredictRootCause analyzes an incident and returns ranked root cause predictions
func (s *RootCauseTemplateService) PredictRootCause(ctx context.Context, request IncidentAnalysisRequest) ([]RootCausePrediction, error) {
	var predictions []RootCausePrediction

	// Match against all templates
	for _, template := range s.templates {
		matchResult := s.matchTemplate(request, template)

		if matchResult.MatchScore >= 0.3 { // Minimum threshold
			prediction := RootCausePrediction{
				TemplateID:       template.ID,
				RootCauseType:    s.inferRootCauseFromTemplate(template),
				Description:      template.Description,
				Confidence:       matchResult.MatchScore,
				Evidence:         s.generateEvidence(matchResult),
				SuggestedActions: s.generateSuggestedActions(template, request),
				SimilarIncidents: s.findSimilarIncidents(template.ID),
				EstimatedTTL:     template.TimeWindowMinutes,
			}
			predictions = append(predictions, prediction)
		}
	}

	// Sort by confidence (highest first)
	sort.Slice(predictions, func(i, j int) bool {
		return predictions[i].Confidence > predictions[j].Confidence
	})

	// Assign ranks
	for i := range predictions {
		predictions[i].Rank = i + 1
	}

	// Store predictions for learning
	s.storePredictions(request.IncidentID, predictions)

	return predictions, nil
}

// matchTemplate calculates how well an incident matches a template
func (s *RootCauseTemplateService) matchTemplate(request IncidentAnalysisRequest, template *CausalTemplate) TemplateMatchResult {
	score := 0.0
	factors := 0
	matchedElements := []string{}
	missingElements := []string{}

	// Pattern matching (event types)
	if len(template.Pattern) > 0 {
		eventTypeSet := make(map[string]bool)
		for _, eventType := range request.EventTypes {
			eventTypeSet[eventType] = true
		}

		patternMatches := 0
		for _, patternEvent := range template.Pattern {
			if eventTypeSet[patternEvent] {
				patternMatches++
				matchedElements = append(matchedElements, fmt.Sprintf("pattern:%s", patternEvent))
			} else {
				missingElements = append(missingElements, fmt.Sprintf("pattern:%s", patternEvent))
			}
		}

		patternScore := float64(patternMatches) / float64(len(template.Pattern))
		score += patternScore * 0.4
		factors++
	}

	// Component matching
	if len(template.Components) > 0 {
		componentSet := make(map[string]bool)
		for _, component := range request.Components {
			componentSet[component] = true
		}

		componentMatches := 0
		for _, templateComp := range template.Components {
			if componentSet[templateComp] {
				componentMatches++
				matchedElements = append(matchedElements, fmt.Sprintf("component:%s", templateComp))
			} else {
				missingElements = append(missingElements, fmt.Sprintf("component:%s", templateComp))
			}
		}

		componentScore := float64(componentMatches) / float64(len(template.Components))
		score += componentScore * 0.3
		factors++
	}

	// Severity matching
	if template.SeverityDistribution != nil {
		if probability, exists := template.SeverityDistribution[request.Severity]; exists {
			score += probability * 0.2
			matchedElements = append(matchedElements, fmt.Sprintf("severity:%d", request.Severity))
		} else {
			missingElements = append(missingElements, fmt.Sprintf("severity:%d", request.Severity))
		}
		factors++
	}

	// Tag similarity
	if len(template.Tags) > 0 && len(request.Tags) > 0 {
		requestTagSet := make(map[string]bool)
		for _, tag := range request.Tags {
			requestTagSet[tag] = true
		}

		tagMatches := 0
		for _, templateTag := range template.Tags {
			if requestTagSet[templateTag] {
				tagMatches++
				matchedElements = append(matchedElements, fmt.Sprintf("tag:%s", templateTag))
			}
		}

		tagScore := float64(tagMatches) / float64(len(template.Tags))
		score += tagScore * 0.1
		factors++
	}

	// Apply template base confidence
	if factors > 0 {
		score /= float64(factors)
		score *= template.Confidence // Weight by template confidence
	}

	return TemplateMatchResult{
		Template:        template,
		MatchScore:      score,
		MatchedElements: matchedElements,
		MissingElements: missingElements,
	}
}

// inferRootCauseFromTemplate extracts root cause type from template
func (s *RootCauseTemplateService) inferRootCauseFromTemplate(template *CausalTemplate) string {
	// Extract from template name or tags
	name := strings.ToLower(template.Name)

	// Common root cause patterns
	patterns := map[string]string{
		"memory":     "memory_leak",
		"cpu":        "cpu_overload",
		"network":    "network_partition",
		"database":   "database_issue",
		"driver":     "driver_incompatibility",
		"config":     "configuration_error",
		"deployment": "deployment_failure",
		"disk":       "disk_space",
		"timeout":    "timeout_issue",
		"connection": "connection_failure",
	}

	for pattern, rootCause := range patterns {
		if strings.Contains(name, pattern) {
			return rootCause
		}
	}

	// Check tags
	for _, tag := range template.Tags {
		if rootCause, exists := patterns[strings.ToLower(tag)]; exists {
			return rootCause
		}
	}

	return "unknown"
}

// generateEvidence creates evidence list from match result
func (s *RootCauseTemplateService) generateEvidence(matchResult TemplateMatchResult) []string {
	evidence := []string{}

	if len(matchResult.MatchedElements) > 0 {
		evidence = append(evidence, fmt.Sprintf("Matched %d template elements", len(matchResult.MatchedElements)))

		// Group by type
		elementsByType := make(map[string][]string)
		for _, element := range matchResult.MatchedElements {
			parts := strings.SplitN(element, ":", 2)
			if len(parts) == 2 {
				elementsByType[parts[0]] = append(elementsByType[parts[0]], parts[1])
			}
		}

		for elementType, elements := range elementsByType {
			if len(elements) > 0 {
				evidence = append(evidence, fmt.Sprintf("Matched %s: %s", elementType, strings.Join(elements, ", ")))
			}
		}
	}

	evidence = append(evidence, fmt.Sprintf("Template confidence: %.2f", matchResult.Template.Confidence))
	evidence = append(evidence, fmt.Sprintf("Historical support: %d incidents", matchResult.Template.Support))

	return evidence
}

// generateSuggestedActions creates action suggestions based on template and incident
func (s *RootCauseTemplateService) generateSuggestedActions(template *CausalTemplate, request IncidentAnalysisRequest) []string {
	actions := []string{}

	// Generic actions based on root cause type
	rootCause := s.inferRootCauseFromTemplate(template)

	actionMap := map[string][]string{
		"memory_leak": {
			"Check memory usage trends and identify leaking processes",
			"Review recent code changes for memory management issues",
			"Consider restarting affected services to free memory",
		},
		"cpu_overload": {
			"Identify processes consuming high CPU",
			"Check for infinite loops or inefficient algorithms",
			"Scale horizontally or optimize performance",
		},
		"network_partition": {
			"Verify network connectivity between components",
			"Check routing tables and firewall rules",
			"Contact network team if infrastructure issue",
		},
		"database_issue": {
			"Check database connectivity and performance",
			"Review recent schema changes or queries",
			"Verify database server resources",
		},
		"configuration_error": {
			"Review recent configuration changes",
			"Validate configuration syntax and values",
			"Compare with known good configuration",
		},
	}

	if specificActions, exists := actionMap[rootCause]; exists {
		actions = append(actions, specificActions...)
	}

	// Component-specific actions
	for _, component := range request.Components {
		switch component {
		case "agent":
			actions = append(actions, "Check agent logs for error details")
		case "server":
			actions = append(actions, "Review server metrics and health status")
		case "database":
			actions = append(actions, "Verify database connection and query performance")
		}
	}

	// Severity-based actions
	if request.Severity <= 2 { // Critical/High
		actions = append(actions, "Escalate to on-call engineer immediately")
		actions = append(actions, "Prepare rollback plan if recent deployment")
	}

	return actions
}

// findSimilarIncidents finds incidents that used the same template
func (s *RootCauseTemplateService) findSimilarIncidents(templateID string) []string {
	rows, err := s.db.Query(`
		SELECT DISTINCT incident_id 
		FROM incident_predictions 
		WHERE template_id = ? 
		ORDER BY predicted_at DESC 
		LIMIT 5
	`, templateID)

	if err != nil {
		return []string{}
	}
	defer rows.Close()

	var incidents []string
	for rows.Next() {
		var incidentID string
		if rows.Scan(&incidentID) == nil {
			incidents = append(incidents, incidentID)
		}
	}

	return incidents
}

// storePredictions stores predictions for learning and feedback
func (s *RootCauseTemplateService) storePredictions(incidentID string, predictions []RootCausePrediction) {
	for _, prediction := range predictions {
		evidenceJSON, _ := json.Marshal(prediction.Evidence)
		actionsJSON, _ := json.Marshal(prediction.SuggestedActions)

		s.db.Exec(`
			INSERT OR REPLACE INTO incident_predictions 
			(id, incident_id, template_id, root_cause_type, confidence, evidence, suggested_actions, predicted_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		`,
			fmt.Sprintf("%s-%s", incidentID, prediction.TemplateID),
			incidentID,
			prediction.TemplateID,
			prediction.RootCauseType,
			prediction.Confidence,
			string(evidenceJSON),
			string(actionsJSON),
			time.Now().Format(time.RFC3339),
		)
	}
}

// GetTemplate retrieves a specific template by ID
func (s *RootCauseTemplateService) GetTemplate(templateID string) (*CausalTemplate, error) {
	template, exists := s.templates[templateID]
	if !exists {
		return nil, fmt.Errorf("template not found: %s", templateID)
	}
	return template, nil
}

// ListTemplates returns all available templates
func (s *RootCauseTemplateService) ListTemplates() []*CausalTemplate {
	templates := make([]*CausalTemplate, 0, len(s.templates))
	for _, template := range s.templates {
		templates = append(templates, template)
	}

	// Sort by confidence and support
	sort.Slice(templates, func(i, j int) bool {
		if templates[i].Confidence != templates[j].Confidence {
			return templates[i].Confidence > templates[j].Confidence
		}
		return templates[i].Support > templates[j].Support
	})

	return templates
}

// HTTP Handlers

// HandlePredictRootCause handles POST /incidents/:id/templates
func (s *RootCauseTemplateService) HandlePredictRootCause(w http.ResponseWriter, r *http.Request) {
	var request IncidentAnalysisRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	predictions, err := s.PredictRootCause(r.Context(), request)
	if err != nil {
		http.Error(w, fmt.Sprintf("Prediction failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"incident_id":  request.IncidentID,
		"predictions":  predictions,
		"total":        len(predictions),
		"predicted_at": time.Now().UTC(),
	})
}

// HandleGetTemplates handles GET /templates
func (s *RootCauseTemplateService) HandleGetTemplates(w http.ResponseWriter, r *http.Request) {
	templates := s.ListTemplates()

	// Apply filters
	if rootCause := r.URL.Query().Get("root_cause"); rootCause != "" {
		filtered := []*CausalTemplate{}
		for _, template := range templates {
			if s.inferRootCauseFromTemplate(template) == rootCause {
				filtered = append(filtered, template)
			}
		}
		templates = filtered
	}

	if minConfidence := r.URL.Query().Get("min_confidence"); minConfidence != "" {
		if threshold, err := strconv.ParseFloat(minConfidence, 64); err == nil {
			filtered := []*CausalTemplate{}
			for _, template := range templates {
				if template.Confidence >= threshold {
					filtered = append(filtered, template)
				}
			}
			templates = filtered
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"templates": templates,
		"total":     len(templates),
	})
}

// HandleGetTemplate handles GET /templates/:id
func (s *RootCauseTemplateService) HandleGetTemplate(w http.ResponseWriter, r *http.Request) {
	templateID := r.URL.Query().Get("id")
	if templateID == "" {
		http.Error(w, "template ID required", http.StatusBadRequest)
		return
	}

	template, err := s.GetTemplate(templateID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(template)
}

// HandleSubmitFeedback handles POST /predictions/:id/feedback
func (s *RootCauseTemplateService) HandleSubmitFeedback(w http.ResponseWriter, r *http.Request) {
	var feedback struct {
		PredictionID    string `json:"prediction_id"`
		ActualRootCause string `json:"actual_root_cause"`
		FeedbackScore   int    `json:"feedback_score"` // 1-5 rating
		Comments        string `json:"comments"`
	}

	if err := json.NewDecoder(r.Body).Decode(&feedback); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Update prediction with feedback
	_, err := s.db.Exec(`
		UPDATE incident_predictions 
		SET actual_root_cause = ?, feedback_score = ?
		WHERE id = ?
	`, feedback.ActualRootCause, feedback.FeedbackScore, feedback.PredictionID)

	if err != nil {
		http.Error(w, "Failed to update feedback", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

// Close closes the database connection
func (s *RootCauseTemplateService) Close() error {
	return s.db.Close()
}
