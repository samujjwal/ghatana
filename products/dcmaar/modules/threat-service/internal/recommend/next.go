package recommend

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/mux"
)

// NextBuildRecommender analyzes incidents and toil to suggest next development priorities
type NextBuildRecommender struct {
	incidentAnalyzer *IncidentAnalyzer
	toilAnalyzer     *ToilAnalyzer
	roiCalculator    *ROICalculator
	priorityWeights  PriorityWeights
}

// PriorityWeights configures how different factors are weighted in recommendations
type PriorityWeights struct {
	IncidentFrequency     float64 `json:"incident_frequency"`
	IncidentSeverity      float64 `json:"incident_severity"`
	ToilTime              float64 `json:"toil_time"`
	UserImpact            float64 `json:"user_impact"`
	DeveloperProductivity float64 `json:"developer_productivity"`
	TechnicalDebt         float64 `json:"technical_debt"`
}

// Incident represents a system incident
type Incident struct {
	ID          string            `json:"id"`
	Timestamp   time.Time         `json:"timestamp"`
	Severity    int               `json:"severity"`  // 1=Critical, 2=High, 3=Medium, 4=Low
	Category    string            `json:"category"`  // "performance", "reliability", "security", etc.
	Component   string            `json:"component"` // "agent", "server", "desktop", etc.
	Description string            `json:"description"`
	Resolution  string            `json:"resolution"`
	TTR         time.Duration     `json:"ttr"` // Time to Resolution
	Tags        []string          `json:"tags"`
	Metadata    map[string]string `json:"metadata"`
}

// ToilTask represents repetitive manual work
type ToilTask struct {
	ID              string            `json:"id"`
	Name            string            `json:"name"`
	Description     string            `json:"description"`
	Category        string            `json:"category"`         // "deployment", "monitoring", "debugging", etc.
	EstimatedEffort time.Duration     `json:"estimated_effort"` // Per occurrence
	Frequency       int               `json:"frequency"`        // Times per week/month
	LastOccurrence  time.Time         `json:"last_occurrence"`
	AffectedTeams   []string          `json:"affected_teams"`
	AutomationLevel int               `json:"automation_level"` // 0=Manual, 100=Fully Automated
	BusinessImpact  int               `json:"business_impact"`  // 1-10 scale
	TechnicalDebt   bool              `json:"technical_debt"`
	Tags            []string          `json:"tags"`
	Metadata        map[string]string `json:"metadata"`
}

// Recommendation represents a suggested next build item
type Recommendation struct {
	ID              string        `json:"id"`
	Title           string        `json:"title"`
	Description     string        `json:"description"`
	Category        string        `json:"category"`
	Priority        int           `json:"priority"`   // 1=Highest, 5=Lowest
	ROIScore        float64       `json:"roi_score"`  // 0-100
	ConfidenceLevel float64       `json:"confidence"` // 0-1.0
	EstimatedEffort time.Duration `json:"estimated_effort"`
	ExpectedBenefit string        `json:"expected_benefit"`
	RiskLevel       int           `json:"risk_level"` // 1=Low, 3=High
	Dependencies    []string      `json:"dependencies"`
	SupportingData  []Evidence    `json:"supporting_data"`
	RecommendedBy   string        `json:"recommended_by"`
	GeneratedAt     time.Time     `json:"generated_at"`
	Tags            []string      `json:"tags"`
	Rationale       string        `json:"rationale"`
}

// Evidence represents supporting data for a recommendation
type Evidence struct {
	Type        string    `json:"type"`   // "incident", "toil", "metric", "feedback"
	Source      string    `json:"source"` // Source system or identifier
	Value       string    `json:"value"`  // Evidence value
	Impact      float64   `json:"impact"` // Impact score
	Frequency   int       `json:"frequency"`
	Timestamp   time.Time `json:"timestamp"`
	Description string    `json:"description"`
}

// IncidentAnalyzer analyzes incidents to find patterns
type IncidentAnalyzer struct {
	incidents []Incident
}

// NewIncidentAnalyzer creates a new incident analyzer
func NewIncidentAnalyzer() *IncidentAnalyzer {
	return &IncidentAnalyzer{
		incidents: []Incident{},
	}
}

// AddIncident adds an incident to analysis
func (ia *IncidentAnalyzer) AddIncident(incident Incident) {
	ia.incidents = append(ia.incidents, incident)
}

// AnalyzePatterns finds patterns in incidents
func (ia *IncidentAnalyzer) AnalyzePatterns() map[string]IncidentPattern {
	patterns := make(map[string]IncidentPattern)

	// Group by component and category
	componentCounts := make(map[string]int)
	categoryCounts := make(map[string]int)
	severityByComponent := make(map[string][]int)

	for _, incident := range ia.incidents {
		key := fmt.Sprintf("%s-%s", incident.Component, incident.Category)

		componentCounts[incident.Component]++
		categoryCounts[incident.Category]++

		if _, exists := severityByComponent[incident.Component]; !exists {
			severityByComponent[incident.Component] = []int{}
		}
		severityByComponent[incident.Component] = append(severityByComponent[incident.Component], incident.Severity)

		if _, exists := patterns[key]; !exists {
			patterns[key] = IncidentPattern{
				Component:  incident.Component,
				Category:   incident.Category,
				Count:      0,
				Severities: []int{},
				TTRs:       []time.Duration{},
			}
		}

		pattern := patterns[key]
		pattern.Count++
		pattern.Severities = append(pattern.Severities, incident.Severity)
		pattern.TTRs = append(pattern.TTRs, incident.TTR)
		patterns[key] = pattern
	}

	// Calculate average severity and TTR for each pattern
	for key, pattern := range patterns {
		avgSeverity := ia.calculateAverageSeverity(pattern.Severities)
		avgTTR := ia.calculateAverageTTR(pattern.TTRs)

		pattern.AverageSeverity = avgSeverity
		pattern.AverageTTR = avgTTR
		pattern.Impact = ia.calculatePatternImpact(pattern)
		patterns[key] = pattern
	}

	return patterns
}

// IncidentPattern represents a pattern in incidents
type IncidentPattern struct {
	Component       string          `json:"component"`
	Category        string          `json:"category"`
	Count           int             `json:"count"`
	Severities      []int           `json:"severities"`
	TTRs            []time.Duration `json:"ttrs"`
	AverageSeverity float64         `json:"average_severity"`
	AverageTTR      time.Duration   `json:"average_ttr"`
	Impact          float64         `json:"impact"`
}

func (ia *IncidentAnalyzer) calculateAverageSeverity(severities []int) float64 {
	if len(severities) == 0 {
		return 0
	}

	sum := 0
	for _, severity := range severities {
		sum += severity
	}

	return float64(sum) / float64(len(severities))
}

func (ia *IncidentAnalyzer) calculateAverageTTR(ttrs []time.Duration) time.Duration {
	if len(ttrs) == 0 {
		return 0
	}

	var sum time.Duration
	for _, ttr := range ttrs {
		sum += ttr
	}

	return sum / time.Duration(len(ttrs))
}

func (ia *IncidentAnalyzer) calculatePatternImpact(pattern IncidentPattern) float64 {
	// Impact = frequency * severity weight * TTR weight
	frequencyWeight := math.Log(float64(pattern.Count + 1))
	severityWeight := (5.0 - pattern.AverageSeverity) / 4.0 // Lower severity number = higher weight
	ttrWeight := math.Log(float64(pattern.AverageTTR.Minutes() + 1))

	return frequencyWeight * severityWeight * ttrWeight
}

// ToilAnalyzer analyzes toil tasks
type ToilAnalyzer struct {
	tasks []ToilTask
}

// NewToilAnalyzer creates a new toil analyzer
func NewToilAnalyzer() *ToilAnalyzer {
	return &ToilAnalyzer{
		tasks: []ToilTask{},
	}
}

// AddTask adds a toil task to analysis
func (ta *ToilAnalyzer) AddTask(task ToilTask) {
	ta.tasks = append(ta.tasks, task)
}

// AnalyzeToil finds the most impactful toil tasks
func (ta *ToilAnalyzer) AnalyzeToil() []ToilImpact {
	impacts := []ToilImpact{}

	for _, task := range ta.tasks {
		impact := ToilImpact{
			Task:          task,
			WeeklyHours:   ta.calculateWeeklyHours(task),
			AnnualCost:    ta.calculateAnnualCost(task),
			AutomationROI: ta.calculateAutomationROI(task),
			Priority:      ta.calculatePriority(task),
		}
		impacts = append(impacts, impact)
	}

	// Sort by priority (higher is better)
	sort.Slice(impacts, func(i, j int) bool {
		return impacts[i].Priority > impacts[j].Priority
	})

	return impacts
}

// ToilImpact represents the impact analysis of a toil task
type ToilImpact struct {
	Task          ToilTask `json:"task"`
	WeeklyHours   float64  `json:"weekly_hours"`
	AnnualCost    float64  `json:"annual_cost"`
	AutomationROI float64  `json:"automation_roi"`
	Priority      float64  `json:"priority"`
}

func (ta *ToilAnalyzer) calculateWeeklyHours(task ToilTask) float64 {
	hoursPerOccurrence := task.EstimatedEffort.Hours()
	return hoursPerOccurrence * float64(task.Frequency)
}

func (ta *ToilAnalyzer) calculateAnnualCost(task ToilTask) float64 {
	weeklyHours := ta.calculateWeeklyHours(task)
	annualHours := weeklyHours * 52
	// Assume $100/hour fully loaded cost
	return annualHours * 100
}

func (ta *ToilAnalyzer) calculateAutomationROI(task ToilTask) float64 {
	annualCost := ta.calculateAnnualCost(task)
	// Assume automation takes 2 weeks at $100/hour
	automationCost := 80 * 100 // 80 hours * $100

	if automationCost == 0 {
		return 0
	}

	// ROI over 2 years
	return (annualCost*2 - float64(automationCost)) / float64(automationCost) * 100
}

func (ta *ToilAnalyzer) calculatePriority(task ToilTask) float64 {
	// Priority based on multiple factors
	frequencyWeight := math.Log(float64(task.Frequency + 1))
	effortWeight := task.EstimatedEffort.Hours()
	impactWeight := float64(task.BusinessImpact) / 10.0
	automationWeight := (100.0 - float64(task.AutomationLevel)) / 100.0

	return frequencyWeight * effortWeight * impactWeight * automationWeight
}

// ROICalculator calculates return on investment for recommendations
type ROICalculator struct {
	developerHourlyRate float64
	maintenanceFactor   float64
}

// NewROICalculator creates a new ROI calculator
func NewROICalculator() *ROICalculator {
	return &ROICalculator{
		developerHourlyRate: 100.0, // $100/hour fully loaded
		maintenanceFactor:   0.2,   // 20% of initial development for maintenance
	}
}

// CalculateROI calculates ROI for a recommendation
func (rc *ROICalculator) CalculateROI(rec Recommendation, incidents []Incident, toil []ToilTask) float64 {
	// Calculate benefits (cost savings)
	incidentSavings := rc.calculateIncidentSavings(rec, incidents)
	toilSavings := rc.calculateToilSavings(rec, toil)
	productivityGains := rc.calculateProductivityGains(rec)

	totalBenefits := incidentSavings + toilSavings + productivityGains

	// Calculate costs (development + maintenance)
	developmentCost := rec.EstimatedEffort.Hours() * rc.developerHourlyRate
	annualMaintenanceCost := developmentCost * rc.maintenanceFactor

	// ROI over 2 years
	totalCosts := developmentCost + (annualMaintenanceCost * 2)

	if totalCosts == 0 {
		return 0
	}

	return ((totalBenefits * 2) - totalCosts) / totalCosts * 100
}

func (rc *ROICalculator) calculateIncidentSavings(rec Recommendation, incidents []Incident) float64 {
	// Estimate incident reduction based on recommendation category
	reductionFactor := 0.0

	switch rec.Category {
	case "reliability":
		reductionFactor = 0.4 // 40% reduction in reliability incidents
	case "performance":
		reductionFactor = 0.3 // 30% reduction in performance incidents
	case "monitoring":
		reductionFactor = 0.5 // 50% reduction in monitoring gaps
	case "automation":
		reductionFactor = 0.6 // 60% reduction in manual error incidents
	default:
		reductionFactor = 0.2 // 20% general reduction
	}

	// Calculate current incident cost
	var currentCost float64
	for _, incident := range incidents {
		// Cost = TTR in hours * hourly rate * number of people involved
		incidentCost := incident.TTR.Hours() * rc.developerHourlyRate * 2 // Assume 2 people per incident
		currentCost += incidentCost
	}

	return currentCost * reductionFactor
}

func (rc *ROICalculator) calculateToilSavings(rec Recommendation, tasks []ToilTask) float64 {
	var savings float64

	for _, task := range tasks {
		if rc.isRecommendationRelevantToToil(rec, task) {
			weeklyHours := task.EstimatedEffort.Hours() * float64(task.Frequency)
			annualHours := weeklyHours * 52
			annualCost := annualHours * rc.developerHourlyRate

			// Estimate reduction based on automation level increase
			currentAutomation := float64(task.AutomationLevel) / 100.0
			targetAutomation := math.Min(currentAutomation+0.7, 1.0) // Up to 70% improvement
			reductionFactor := targetAutomation - currentAutomation

			savings += annualCost * reductionFactor
		}
	}

	return savings
}

func (rc *ROICalculator) calculateProductivityGains(rec Recommendation) float64 {
	// Estimate productivity gains based on recommendation type
	baseGain := 0.0

	switch rec.Category {
	case "tooling":
		baseGain = 10000 // $10k annual productivity gain
	case "automation":
		baseGain = 15000 // $15k annual productivity gain
	case "monitoring":
		baseGain = 5000 // $5k annual productivity gain
	case "infrastructure":
		baseGain = 8000 // $8k annual productivity gain
	default:
		baseGain = 3000 // $3k annual productivity gain
	}

	return baseGain
}

func (rc *ROICalculator) isRecommendationRelevantToToil(rec Recommendation, task ToilTask) bool {
	// Simple relevance check based on categories and tags
	if rec.Category == "automation" && strings.Contains(strings.ToLower(task.Description), "manual") {
		return true
	}

	// Check for tag overlap
	for _, recTag := range rec.Tags {
		for _, taskTag := range task.Tags {
			if strings.EqualFold(recTag, taskTag) {
				return true
			}
		}
	}

	return false
}

// NewNextBuildRecommender creates a new recommender
func NewNextBuildRecommender() *NextBuildRecommender {
	return &NextBuildRecommender{
		incidentAnalyzer: NewIncidentAnalyzer(),
		toilAnalyzer:     NewToilAnalyzer(),
		roiCalculator:    NewROICalculator(),
		priorityWeights: PriorityWeights{
			IncidentFrequency:     0.25,
			IncidentSeverity:      0.20,
			ToilTime:              0.20,
			UserImpact:            0.15,
			DeveloperProductivity: 0.15,
			TechnicalDebt:         0.05,
		},
	}
}

// GenerateRecommendations generates prioritized recommendations
func (nbr *NextBuildRecommender) GenerateRecommendations(ctx context.Context) ([]Recommendation, error) {
	// Analyze current state
	incidentPatterns := nbr.incidentAnalyzer.AnalyzePatterns()
	toilImpacts := nbr.toilAnalyzer.AnalyzeToil()

	// Generate recommendations based on patterns
	recommendations := []Recommendation{}

	// Add incident-based recommendations
	for _, pattern := range incidentPatterns {
		rec := nbr.generateIncidentRecommendation(pattern)
		if rec != nil {
			recommendations = append(recommendations, *rec)
		}
	}

	// Add toil-based recommendations
	for _, impact := range toilImpacts[:10] { // Top 10 toil items
		rec := nbr.generateToilRecommendation(impact)
		if rec != nil {
			recommendations = append(recommendations, *rec)
		}
	}

	// Add strategic recommendations
	strategicRecs := nbr.generateStrategicRecommendations()
	recommendations = append(recommendations, strategicRecs...)

	// Calculate ROI and prioritize
	for i := range recommendations {
		roi := nbr.roiCalculator.CalculateROI(
			recommendations[i],
			nbr.incidentAnalyzer.incidents,
			nbr.toilAnalyzer.tasks,
		)
		recommendations[i].ROIScore = roi
		recommendations[i].Priority = nbr.calculatePriority(recommendations[i])
	}

	// Sort by priority
	sort.Slice(recommendations, func(i, j int) bool {
		return recommendations[i].Priority < recommendations[j].Priority
	})

	return recommendations, nil
}

func (nbr *NextBuildRecommender) generateIncidentRecommendation(pattern IncidentPattern) *Recommendation {
	if pattern.Count < 3 || pattern.Impact < 10 { // Minimum thresholds
		return nil
	}

	rec := &Recommendation{
		ID:              fmt.Sprintf("incident-%s-%s", pattern.Component, pattern.Category),
		Title:           fmt.Sprintf("Improve %s %s Reliability", pattern.Component, pattern.Category),
		Description:     fmt.Sprintf("Address recurring %s issues in %s component", pattern.Category, pattern.Component),
		Category:        "reliability",
		EstimatedEffort: time.Hour * 40, // 1 week default
		RiskLevel:       2,
		RecommendedBy:   "incident-analyzer",
		GeneratedAt:     time.Now(),
		Tags:            []string{pattern.Component, pattern.Category, "reliability"},
		SupportingData: []Evidence{
			{
				Type:        "incident",
				Source:      fmt.Sprintf("%s-%s", pattern.Component, pattern.Category),
				Value:       fmt.Sprintf("%d incidents", pattern.Count),
				Impact:      pattern.Impact,
				Frequency:   pattern.Count,
				Timestamp:   time.Now(),
				Description: fmt.Sprintf("Pattern of %s incidents in %s", pattern.Category, pattern.Component),
			},
		},
		Rationale: fmt.Sprintf(
			"This component has had %d incidents with average severity %.1f and average TTR of %v. "+
				"Addressing this pattern could prevent future incidents and reduce operational overhead.",
			pattern.Count, pattern.AverageSeverity, pattern.AverageTTR,
		),
	}

	// Adjust effort based on complexity
	if pattern.Count > 10 {
		rec.EstimatedEffort = time.Hour * 80 // 2 weeks for complex issues
	}
	if pattern.AverageSeverity < 2 { // Critical/High severity
		rec.EstimatedEffort = time.Hour * 120 // 3 weeks for critical issues
	}

	return rec
}

func (nbr *NextBuildRecommender) generateToilRecommendation(impact ToilImpact) *Recommendation {
	if impact.WeeklyHours < 2 || impact.AutomationROI < 50 { // Minimum thresholds
		return nil
	}

	rec := &Recommendation{
		ID:              fmt.Sprintf("toil-%s", impact.Task.ID),
		Title:           fmt.Sprintf("Automate %s", impact.Task.Name),
		Description:     fmt.Sprintf("Reduce toil by automating: %s", impact.Task.Description),
		Category:        "automation",
		EstimatedEffort: time.Duration(impact.WeeklyHours*2) * time.Hour, // 2x weekly effort for automation
		RiskLevel:       1,
		RecommendedBy:   "toil-analyzer",
		GeneratedAt:     time.Now(),
		Tags:            append(impact.Task.Tags, "automation", "toil"),
		SupportingData: []Evidence{
			{
				Type:        "toil",
				Source:      impact.Task.ID,
				Value:       fmt.Sprintf("%.1f hours/week", impact.WeeklyHours),
				Impact:      impact.Priority,
				Frequency:   impact.Task.Frequency,
				Timestamp:   time.Now(),
				Description: fmt.Sprintf("Manual task consuming %.1f hours weekly", impact.WeeklyHours),
			},
		},
		Rationale: fmt.Sprintf(
			"This task consumes %.1f hours per week and has an automation ROI of %.0f%%. "+
				"Automation could save $%.0f annually and reduce manual errors.",
			impact.WeeklyHours, impact.AutomationROI, impact.AnnualCost,
		),
	}

	return rec
}

func (nbr *NextBuildRecommender) generateStrategicRecommendations() []Recommendation {
	// Generate strategic recommendations based on industry best practices
	return []Recommendation{
		{
			ID:              "strategic-observability",
			Title:           "Enhance Observability Stack",
			Description:     "Implement comprehensive distributed tracing and enhanced metrics",
			Category:        "monitoring",
			Priority:        3,
			EstimatedEffort: time.Hour * 160, // 4 weeks
			RiskLevel:       2,
			RecommendedBy:   "strategic-analyzer",
			GeneratedAt:     time.Now(),
			Tags:            []string{"observability", "monitoring", "strategic"},
			Rationale: "Improved observability reduces MTTR and enables proactive issue detection. " +
				"Industry benchmark shows 40% reduction in incident resolution time.",
		},
		{
			ID:              "strategic-testing",
			Title:           "Expand Automated Testing Coverage",
			Description:     "Increase test coverage and implement chaos engineering",
			Category:        "quality",
			Priority:        4,
			EstimatedEffort: time.Hour * 120, // 3 weeks
			RiskLevel:       1,
			RecommendedBy:   "strategic-analyzer",
			GeneratedAt:     time.Now(),
			Tags:            []string{"testing", "quality", "strategic"},
			Rationale: "Higher test coverage reduces production incidents and increases deployment confidence. " +
				"Target 80% coverage to match industry standards.",
		},
	}
}

func (nbr *NextBuildRecommender) calculatePriority(rec Recommendation) int {
	// Calculate priority score based on multiple factors
	score := 0.0

	// ROI factor (higher ROI = higher priority)
	if rec.ROIScore > 200 {
		score += 30
	} else if rec.ROIScore > 100 {
		score += 20
	} else if rec.ROIScore > 50 {
		score += 10
	}

	// Risk factor (lower risk = higher priority)
	score += float64(4-rec.RiskLevel) * 10

	// Effort factor (lower effort = higher priority)
	effortHours := rec.EstimatedEffort.Hours()
	if effortHours < 40 {
		score += 20
	} else if effortHours < 80 {
		score += 15
	} else if effortHours < 160 {
		score += 10
	}

	// Category factor
	switch rec.Category {
	case "reliability":
		score += 25
	case "automation":
		score += 20
	case "monitoring":
		score += 15
	case "performance":
		score += 15
	default:
		score += 10
	}

	// Convert to 1-5 scale (1 = highest priority)
	if score >= 70 {
		return 1
	} else if score >= 60 {
		return 2
	} else if score >= 50 {
		return 3
	} else if score >= 40 {
		return 4
	} else {
		return 5
	}
}

// HTTP Handlers

func (nbr *NextBuildRecommender) HandleGetRecommendations(w http.ResponseWriter, r *http.Request) {
	recommendations, err := nbr.GenerateRecommendations(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to generate recommendations: %v", err), http.StatusInternalServerError)
		return
	}

	// Filter by query parameters
	if category := r.URL.Query().Get("category"); category != "" {
		filtered := []Recommendation{}
		for _, rec := range recommendations {
			if rec.Category == category {
				filtered = append(filtered, rec)
			}
		}
		recommendations = filtered
	}

	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil && limit > 0 && limit < len(recommendations) {
			recommendations = recommendations[:limit]
		}
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(map[string]interface{}{
		"recommendations": recommendations,
		"total":           len(recommendations),
		"generated_at":    time.Now().UTC(),
	}); err != nil {
		log.Printf("Error encoding recommendations response: %v", err)
	}
}

func (nbr *NextBuildRecommender) HandleAddIncident(w http.ResponseWriter, r *http.Request) {
	var incident Incident
	if err := json.NewDecoder(r.Body).Decode(&incident); err != nil {
		http.Error(w, "Invalid incident data", http.StatusBadRequest)
		return
	}

	if incident.ID == "" {
		incident.ID = fmt.Sprintf("inc-%d", time.Now().Unix())
	}
	if incident.Timestamp.IsZero() {
		incident.Timestamp = time.Now()
	}

	nbr.incidentAnalyzer.AddIncident(incident)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status": "success",
		"id":     incident.ID,
	})
}

func (nbr *NextBuildRecommender) HandleAddToilTask(w http.ResponseWriter, r *http.Request) {
	var task ToilTask
	if err := json.NewDecoder(r.Body).Decode(&task); err != nil {
		http.Error(w, "Invalid toil task data", http.StatusBadRequest)
		return
	}

	if task.ID == "" {
		task.ID = fmt.Sprintf("toil-%d", time.Now().Unix())
	}
	if task.LastOccurrence.IsZero() {
		task.LastOccurrence = time.Now()
	}

	nbr.toilAnalyzer.AddTask(task)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status": "success",
		"id":     task.ID,
	})
}

func (nbr *NextBuildRecommender) HandleGetAnalytics(w http.ResponseWriter, r *http.Request) {
	incidentPatterns := nbr.incidentAnalyzer.AnalyzePatterns()
	toilImpacts := nbr.toilAnalyzer.AnalyzeToil()

	// Calculate summary statistics
	totalIncidents := len(nbr.incidentAnalyzer.incidents)
	totalToilTasks := len(nbr.toilAnalyzer.tasks)

	var totalWeeklyToilHours float64
	for _, impact := range toilImpacts {
		totalWeeklyToilHours += impact.WeeklyHours
	}

	analytics := map[string]interface{}{
		"summary": map[string]interface{}{
			"total_incidents":         totalIncidents,
			"total_toil_tasks":        totalToilTasks,
			"total_weekly_toil_hours": totalWeeklyToilHours,
			"incident_patterns":       len(incidentPatterns),
		},
		"incident_patterns": incidentPatterns,
		"toil_impacts":      toilImpacts[:min(10, len(toilImpacts))], // Top 10
		"generated_at":      time.Now().UTC(),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(analytics)
}

// RegisterRoutes registers HTTP routes for the recommender
func (nbr *NextBuildRecommender) RegisterRoutes(router *mux.Router) {
	router.HandleFunc("/api/recommend/next", nbr.HandleGetRecommendations).Methods("GET")
	router.HandleFunc("/api/recommend/incidents", nbr.HandleAddIncident).Methods("POST")
	router.HandleFunc("/api/recommend/toil", nbr.HandleAddToilTask).Methods("POST")
	router.HandleFunc("/api/recommend/analytics", nbr.HandleGetAnalytics).Methods("GET")
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
