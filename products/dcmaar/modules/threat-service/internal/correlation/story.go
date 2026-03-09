package correlation

import (
	"context"
	"crypto/md5"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"
)

// CrossSourceStoryCorrelator aggregates anomalies, extension errors, and infra logs into unified stories
// Implements Capability 1: Cross-Source Incident Story Correlator from Horizontal Slice AI Plan #5
type CrossSourceStoryCorrelator struct {
	stories        map[string]*IncidentStory
	mutex          sync.RWMutex
	correlationCfg CorrelationConfig
	analytics      *CorrelationAnalytics
}

// CorrelationConfig defines how events are correlated into stories
type CorrelationConfig struct {
	TimeWindowMinutes   int     `json:"time_window_minutes"`  // Events within this window can be correlated
	TenantIsolation     bool    `json:"tenant_isolation"`     // Isolate stories by tenant
	SimilarityThreshold float64 `json:"similarity_threshold"` // 0.0-1.0, threshold for event similarity
	MaxEventsPerStory   int     `json:"max_events_per_story"` // Prevent stories from growing too large
	MinEventsForStory   int     `json:"min_events_for_story"` // Minimum events to consider a valid story
	ConfidenceThreshold float64 `json:"confidence_threshold"` // Minimum confidence to publish story
	AutoArchiveHours    int     `json:"auto_archive_hours"`   // Auto-archive inactive stories
}

// IncidentStory represents a unified cross-source incident narrative
type IncidentStory struct {
	ID          string                 `json:"id"`
	TenantID    string                 `json:"tenant_id"`
	Title       string                 `json:"title"`
	Description string                 `json:"description"`
	Status      string                 `json:"status"`   // "active", "investigating", "resolved", "archived"
	Severity    int                    `json:"severity"` // 1=Critical, 2=High, 3=Medium, 4=Low
	StartTime   time.Time              `json:"start_time"`
	EndTime     *time.Time             `json:"end_time,omitempty"`
	LastUpdated time.Time              `json:"last_updated"`
	Events      []CorrelatedEvent      `json:"events"`
	Timeline    []TimelineEvent        `json:"timeline"`
	RootCauses  []RootCause            `json:"root_causes"`
	Impact      ImpactAssessment       `json:"impact"`
	Tags        []string               `json:"tags"`
	Confidence  float64                `json:"confidence"` // 0.0-1.0, confidence in correlation
	Evidence    []Evidence             `json:"evidence"`
	CreatedBy   string                 `json:"created_by"`
	AssignedTo  string                 `json:"assigned_to,omitempty"`
	Resolution  string                 `json:"resolution,omitempty"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// CorrelatedEvent represents an event that's part of an incident story
type CorrelatedEvent struct {
	ID            string                 `json:"id"`
	Type          string                 `json:"type"`   // "anomaly", "extension_error", "infra_log", "metric_spike"
	Source        string                 `json:"source"` // Source system/component
	Timestamp     time.Time              `json:"timestamp"`
	Message       string                 `json:"message"`
	Severity      int                    `json:"severity"`
	Component     string                 `json:"component"`
	Service       string                 `json:"service"`
	Host          string                 `json:"host,omitempty"`
	Tags          []string               `json:"tags"`
	Fields        map[string]interface{} `json:"fields"`
	CorrelationID string                 `json:"correlation_id"` // Links related events
	Confidence    float64                `json:"confidence"`     // Confidence this event belongs to story
	CausalWeight  float64                `json:"causal_weight"`  // How likely this event caused others
	ImpactScore   float64                `json:"impact_score"`   // Estimated impact of this event
}

// TimelineEvent represents a significant moment in the incident timeline
type TimelineEvent struct {
	Timestamp   time.Time `json:"timestamp"`
	Type        string    `json:"type"` // "started", "escalated", "investigated", "resolved"
	Description string    `json:"description"`
	Actor       string    `json:"actor"`     // Who/what performed this action
	EventIDs    []string  `json:"event_ids"` // Related events
}

// RootCause represents a potential root cause of the incident
type RootCause struct {
	ID          string    `json:"id"`
	Type        string    `json:"type"` // "configuration", "deployment", "infrastructure", "code"
	Description string    `json:"description"`
	Confidence  float64   `json:"confidence"` // 0.0-1.0
	Evidence    []string  `json:"evidence"`   // Event IDs supporting this root cause
	FirstSeen   time.Time `json:"first_seen"`
	Component   string    `json:"component"`
}

// ImpactAssessment quantifies the impact of an incident
type ImpactAssessment struct {
	UsersAffected       int      `json:"users_affected"`
	ServicesAffected    []string `json:"services_affected"`
	DowntimeMinutes     int      `json:"downtime_minutes"`
	RevenueImpact       float64  `json:"revenue_impact"`
	BusinessCriticality int      `json:"business_criticality"` // 1-5 scale
	CustomerComplaints  int      `json:"customer_complaints"`
}

// Evidence represents supporting evidence for correlations and root causes
type Evidence struct {
	Type        string    `json:"type"` // "metric", "log", "trace", "alert"
	Source      string    `json:"source"`
	Timestamp   time.Time `json:"timestamp"`
	Description string    `json:"description"`
	Value       string    `json:"value"`
	Confidence  float64   `json:"confidence"`
	EventID     string    `json:"event_id"`
}

// CorrelationAnalytics tracks correlation performance and quality
type CorrelationAnalytics struct {
	TotalEvents       int64         `json:"total_events"`
	TotalStories      int64         `json:"total_stories"`
	AvgEventsPerStory float64       `json:"avg_events_per_story"`
	CorrelationRate   float64       `json:"correlation_rate"`    // % events successfully correlated
	FalsePositiveRate float64       `json:"false_positive_rate"` // % incorrect correlations
	RecallRate        float64       `json:"recall_rate"`         // % true incidents captured
	AvgConfidence     float64       `json:"avg_confidence"`
	ProcessingLatency time.Duration `json:"processing_latency"`
}

// NewCrossSourceStoryCorrelator creates a new correlator with default configuration
func NewCrossSourceStoryCorrelator() *CrossSourceStoryCorrelator {
	return &CrossSourceStoryCorrelator{
		stories: make(map[string]*IncidentStory),
		correlationCfg: CorrelationConfig{
			TimeWindowMinutes:   60, // 1 hour window
			TenantIsolation:     true,
			SimilarityThreshold: 0.7,
			MaxEventsPerStory:   1000,
			MinEventsForStory:   2,
			ConfidenceThreshold: 0.6,
			AutoArchiveHours:    168, // 1 week
		},
		analytics: &CorrelationAnalytics{},
	}
}

// CorrelateEvent adds an event and attempts to correlate it with existing stories
func (c *CrossSourceStoryCorrelator) CorrelateEvent(ctx context.Context, event CorrelatedEvent) (*IncidentStory, error) {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	// Update analytics
	c.analytics.TotalEvents++

	// Find candidate stories for correlation
	candidateStories := c.findCandidateStories(event)

	var bestStory *IncidentStory
	bestScore := 0.0

	// Evaluate correlation with each candidate
	for _, story := range candidateStories {
		score := c.calculateCorrelationScore(event, story)
		if score > bestScore && score >= c.correlationCfg.SimilarityThreshold {
			bestScore = score
			bestStory = story
		}
	}

	// If no good correlation found, create new story
	if bestStory == nil {
		return c.createNewStory(event), nil
	}

	// Add event to existing story
	return c.addEventToStory(event, bestStory, bestScore), nil
}

// findCandidateStories finds stories that could potentially correlate with the event
func (c *CrossSourceStoryCorrelator) findCandidateStories(event CorrelatedEvent) []*IncidentStory {
	candidates := []*IncidentStory{}
	timeWindow := time.Duration(c.correlationCfg.TimeWindowMinutes) * time.Minute

	for _, story := range c.stories {
		// Skip archived stories
		if story.Status == "archived" {
			continue
		}

		// Check tenant isolation
		if c.correlationCfg.TenantIsolation && story.TenantID != event.Fields["tenant_id"] {
			continue
		}

		// Check time window
		if event.Timestamp.Sub(story.StartTime) > timeWindow {
			continue
		}

		// Check if story has capacity
		if len(story.Events) >= c.correlationCfg.MaxEventsPerStory {
			continue
		}

		candidates = append(candidates, story)
	}

	return candidates
}

// calculateCorrelationScore computes how well an event correlates with a story
func (c *CrossSourceStoryCorrelator) calculateCorrelationScore(event CorrelatedEvent, story *IncidentStory) float64 {
	score := 0.0
	factors := 0

	// Time proximity factor
	if len(story.Events) > 0 {
		latestEvent := story.Events[len(story.Events)-1]
		timeDiff := event.Timestamp.Sub(latestEvent.Timestamp)
		if timeDiff < 5*time.Minute {
			score += 0.3
		} else if timeDiff < 15*time.Minute {
			score += 0.2
		} else if timeDiff < 60*time.Minute {
			score += 0.1
		}
		factors++
	}

	// Component/service similarity
	for _, storyEvent := range story.Events {
		if event.Component == storyEvent.Component {
			score += 0.2
			break
		}
		if event.Service == storyEvent.Service {
			score += 0.15
			break
		}
	}
	factors++

	// Tag similarity
	commonTags := c.countCommonTags(event.Tags, story.Tags)
	if len(story.Tags) > 0 {
		tagSimilarity := float64(commonTags) / float64(len(story.Tags))
		score += tagSimilarity * 0.2
	}
	factors++

	// Message similarity (simple keyword matching)
	messageSimilarity := c.calculateMessageSimilarity(event.Message, story.Description)
	score += messageSimilarity * 0.15
	factors++

	// Severity alignment
	severityDiff := abs(event.Severity - story.Severity)
	if severityDiff == 0 {
		score += 0.1
	} else if severityDiff == 1 {
		score += 0.05
	}
	factors++

	// Correlation ID matching (if available)
	if event.CorrelationID != "" {
		for _, storyEvent := range story.Events {
			if storyEvent.CorrelationID == event.CorrelationID {
				score += 0.3
				break
			}
		}
	}
	factors++

	return score / float64(factors)
}

// createNewStory creates a new incident story from an event
func (c *CrossSourceStoryCorrelator) createNewStory(event CorrelatedEvent) *IncidentStory {
	storyID := c.generateStoryID(event)

	story := &IncidentStory{
		ID:          storyID,
		TenantID:    getStringFromFields(event.Fields, "tenant_id"),
		Title:       c.generateStoryTitle(event),
		Description: c.generateStoryDescription(event),
		Status:      "active",
		Severity:    event.Severity,
		StartTime:   event.Timestamp,
		LastUpdated: time.Now(),
		Events:      []CorrelatedEvent{event},
		Timeline: []TimelineEvent{
			{
				Timestamp:   event.Timestamp,
				Type:        "started",
				Description: "Incident story created from initial event",
				Actor:       "correlator",
				EventIDs:    []string{event.ID},
			},
		},
		RootCauses: []RootCause{},
		Impact: ImpactAssessment{
			BusinessCriticality: event.Severity,
		},
		Tags:       event.Tags,
		Confidence: 1.0, // New story starts with high confidence
		Evidence:   []Evidence{},
		CreatedBy:  "cross-source-correlator",
		Metadata:   make(map[string]interface{}),
	}

	c.stories[storyID] = story
	c.analytics.TotalStories++

	return story
}

// addEventToStory adds an event to an existing story
func (c *CrossSourceStoryCorrelator) addEventToStory(event CorrelatedEvent, story *IncidentStory, correlationScore float64) *IncidentStory {
	event.Confidence = correlationScore
	story.Events = append(story.Events, event)
	story.LastUpdated = time.Now()

	// Update story metadata
	c.updateStoryFromEvent(story, event)

	// Add timeline event
	story.Timeline = append(story.Timeline, TimelineEvent{
		Timestamp:   event.Timestamp,
		Type:        "event_added",
		Description: fmt.Sprintf("Added %s event: %s", event.Type, event.Message),
		Actor:       "correlator",
		EventIDs:    []string{event.ID},
	})

	// Recalculate story confidence
	c.recalculateStoryConfidence(story)

	return story
}

// updateStoryFromEvent updates story properties based on new event
func (c *CrossSourceStoryCorrelator) updateStoryFromEvent(story *IncidentStory, event CorrelatedEvent) {
	// Update severity (take the highest)
	if event.Severity < story.Severity {
		story.Severity = event.Severity
	}

	// Merge tags
	for _, tag := range event.Tags {
		if !contains(story.Tags, tag) {
			story.Tags = append(story.Tags, tag)
		}
	}

	// Update impact assessment
	if event.ImpactScore > 0 {
		// Simple impact aggregation - in production this would be more sophisticated
		story.Impact.BusinessCriticality = max(story.Impact.BusinessCriticality, int(event.ImpactScore))
	}
}

// recalculateStoryConfidence recalculates the overall confidence of a story
func (c *CrossSourceStoryCorrelator) recalculateStoryConfidence(story *IncidentStory) {
	if len(story.Events) == 0 {
		story.Confidence = 0.0
		return
	}

	totalConfidence := 0.0
	for _, event := range story.Events {
		totalConfidence += event.Confidence
	}

	avgConfidence := totalConfidence / float64(len(story.Events))

	// Apply penalties and bonuses
	confidence := avgConfidence

	// Bonus for multiple sources
	sources := make(map[string]bool)
	for _, event := range story.Events {
		sources[event.Source] = true
	}
	if len(sources) >= 3 {
		confidence += 0.1
	}

	// Bonus for temporal clustering
	if c.hasTemporalClustering(story.Events) {
		confidence += 0.05
	}

	// Penalty for too many events (might be noise)
	if len(story.Events) > c.correlationCfg.MaxEventsPerStory/2 {
		confidence -= 0.1
	}

	story.Confidence = math.Max(0.0, math.Min(1.0, confidence))
}

// GetStory retrieves a story by ID
func (c *CrossSourceStoryCorrelator) GetStory(storyID string) (*IncidentStory, error) {
	c.mutex.RLock()
	defer c.mutex.RUnlock()

	story, exists := c.stories[storyID]
	if !exists {
		return nil, fmt.Errorf("story not found: %s", storyID)
	}

	return story, nil
}

// GetActiveStories returns all active stories
func (c *CrossSourceStoryCorrelator) GetActiveStories() []*IncidentStory {
	c.mutex.RLock()
	defer c.mutex.RUnlock()

	var active []*IncidentStory
	for _, story := range c.stories {
		if story.Status == "active" || story.Status == "investigating" {
			active = append(active, story)
		}
	}

	// Sort by last updated (most recent first)
	sort.Slice(active, func(i, j int) bool {
		return active[i].LastUpdated.After(active[j].LastUpdated)
	})

	return active
}

// UpdateStoryStatus updates the status of a story
func (c *CrossSourceStoryCorrelator) UpdateStoryStatus(storyID, status, resolution string) error {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	story, exists := c.stories[storyID]
	if !exists {
		return fmt.Errorf("story not found: %s", storyID)
	}

	oldStatus := story.Status
	story.Status = status
	story.LastUpdated = time.Now()

	if resolution != "" {
		story.Resolution = resolution
	}

	if status == "resolved" && story.EndTime == nil {
		now := time.Now()
		story.EndTime = &now
	}

	// Add timeline event
	story.Timeline = append(story.Timeline, TimelineEvent{
		Timestamp:   time.Now(),
		Type:        "status_changed",
		Description: fmt.Sprintf("Status changed from %s to %s", oldStatus, status),
		Actor:       "operator",
		EventIDs:    []string{},
	})

	return nil
}

// ArchiveOldStories archives stories that haven't been updated recently
func (c *CrossSourceStoryCorrelator) ArchiveOldStories() int {
	c.mutex.Lock()
	defer c.mutex.Unlock()

	archived := 0
	cutoff := time.Now().Add(-time.Duration(c.correlationCfg.AutoArchiveHours) * time.Hour)

	for _, story := range c.stories {
		if story.Status != "archived" && story.LastUpdated.Before(cutoff) {
			story.Status = "archived"
			story.LastUpdated = time.Now()
			archived++
		}
	}

	return archived
}

// GetAnalytics returns correlation analytics
func (c *CrossSourceStoryCorrelator) GetAnalytics() *CorrelationAnalytics {
	c.mutex.RLock()
	defer c.mutex.RUnlock()

	// Calculate current analytics
	analytics := *c.analytics

	if analytics.TotalStories > 0 {
		totalEvents := int64(0)
		totalConfidence := 0.0
		validStories := int64(0)

		for _, story := range c.stories {
			totalEvents += int64(len(story.Events))
			totalConfidence += story.Confidence
			validStories++
		}

		analytics.AvgEventsPerStory = float64(totalEvents) / float64(validStories)
		analytics.AvgConfidence = totalConfidence / float64(validStories)
		analytics.CorrelationRate = float64(totalEvents) / float64(analytics.TotalEvents)
	}

	return &analytics
}

// HTTP Handlers

// HandleGetStories returns all active stories
func (c *CrossSourceStoryCorrelator) HandleGetStories(w http.ResponseWriter, r *http.Request) {
	stories := c.GetActiveStories()

	// Apply filters
	if status := r.URL.Query().Get("status"); status != "" {
		filtered := []*IncidentStory{}
		for _, story := range stories {
			if story.Status == status {
				filtered = append(filtered, story)
			}
		}
		stories = filtered
	}

	if tenantID := r.URL.Query().Get("tenant_id"); tenantID != "" {
		filtered := []*IncidentStory{}
		for _, story := range stories {
			if story.TenantID == tenantID {
				filtered = append(filtered, story)
			}
		}
		stories = filtered
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"stories": stories,
		"total":   len(stories),
	})
}

// HandleGetStory returns a specific story
func (c *CrossSourceStoryCorrelator) HandleGetStory(w http.ResponseWriter, r *http.Request) {
	storyID := r.URL.Query().Get("id")
	if storyID == "" {
		http.Error(w, "story ID required", http.StatusBadRequest)
		return
	}

	story, err := c.GetStory(storyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(story)
}

// HandleUpdateStoryStatus updates a story's status
func (c *CrossSourceStoryCorrelator) HandleUpdateStoryStatus(w http.ResponseWriter, r *http.Request) {
	var request struct {
		StoryID    string `json:"story_id"`
		Status     string `json:"status"`
		Resolution string `json:"resolution,omitempty"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	err := c.UpdateStoryStatus(request.StoryID, request.Status, request.Resolution)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

// HandleCorrelateEvent accepts new events for correlation
func (c *CrossSourceStoryCorrelator) HandleCorrelateEvent(w http.ResponseWriter, r *http.Request) {
	var event CorrelatedEvent
	if err := json.NewDecoder(r.Body).Decode(&event); err != nil {
		http.Error(w, "Invalid event data", http.StatusBadRequest)
		return
	}

	if event.ID == "" {
		event.ID = fmt.Sprintf("evt-%d", time.Now().UnixNano())
	}
	if event.Timestamp.IsZero() {
		event.Timestamp = time.Now()
	}

	story, err := c.CorrelateEvent(r.Context(), event)
	if err != nil {
		http.Error(w, fmt.Sprintf("Correlation failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"story_id": story.ID,
		"status":   "correlated",
	})
}

// HandleGetAnalytics returns correlation analytics
func (c *CrossSourceStoryCorrelator) HandleGetAnalytics(w http.ResponseWriter, r *http.Request) {
	analytics := c.GetAnalytics()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(analytics)
}

// Utility functions

func (c *CrossSourceStoryCorrelator) generateStoryID(event CorrelatedEvent) string {
	data := fmt.Sprintf("%s-%s-%d", event.Source, event.Component, event.Timestamp.Unix())
	hash := md5.Sum([]byte(data))
	return fmt.Sprintf("story-%x", hash)[:16]
}

func (c *CrossSourceStoryCorrelator) generateStoryTitle(event CorrelatedEvent) string {
	return fmt.Sprintf("%s issue in %s", strings.Title(event.Type), event.Component)
}

func (c *CrossSourceStoryCorrelator) generateStoryDescription(event CorrelatedEvent) string {
	return fmt.Sprintf("Incident story started from %s: %s", event.Type, event.Message)
}

func (c *CrossSourceStoryCorrelator) countCommonTags(tags1, tags2 []string) int {
	tagSet := make(map[string]bool)
	for _, tag := range tags1 {
		tagSet[tag] = true
	}

	common := 0
	for _, tag := range tags2 {
		if tagSet[tag] {
			common++
		}
	}

	return common
}

func (c *CrossSourceStoryCorrelator) calculateMessageSimilarity(msg1, msg2 string) float64 {
	// Simple keyword-based similarity
	words1 := strings.Fields(strings.ToLower(msg1))
	words2 := strings.Fields(strings.ToLower(msg2))

	if len(words1) == 0 || len(words2) == 0 {
		return 0.0
	}

	wordSet := make(map[string]bool)
	for _, word := range words1 {
		wordSet[word] = true
	}

	common := 0
	for _, word := range words2 {
		if wordSet[word] {
			common++
		}
	}

	return float64(common) / float64(max(len(words1), len(words2)))
}

func (c *CrossSourceStoryCorrelator) hasTemporalClustering(events []CorrelatedEvent) bool {
	if len(events) < 3 {
		return false
	}

	// Check if events are clustered in time (≥50% within 10 minutes of each other)
	clustered := 0
	for i := 0; i < len(events)-1; i++ {
		for j := i + 1; j < len(events); j++ {
			if abs64(events[i].Timestamp.Unix()-events[j].Timestamp.Unix()) <= 600 { // 10 minutes
				clustered++
			}
		}
	}

	totalPairs := len(events) * (len(events) - 1) / 2
	return float64(clustered)/float64(totalPairs) >= 0.5
}

// Helper functions
func getStringFromFields(fields map[string]interface{}, key string) string {
	if val, ok := fields[key]; ok {
		if str, ok := val.(string); ok {
			return str
		}
	}
	return ""
}

func contains(slice []string, item string) bool {
	for _, s := range slice {
		if s == item {
			return true
		}
	}
	return false
}

func abs(x int) int {
	if x < 0 {
		return -x
	}
	return x
}

func abs64(x int64) int64 {
	if x < 0 {
		return -x
	}
	return x
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}
