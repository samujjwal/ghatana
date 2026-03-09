package approval

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

// ApprovalWorkflowService manages human-in-the-loop approval workflows
// Implements Capability 5: Human-in-the-Loop Approval Workflow from Horizontal Slice AI Plan #5
type ApprovalWorkflowService struct {
	store  ApprovalStore
	config WorkflowConfig
}

// WorkflowConfig configures the approval workflow system
type WorkflowConfig struct {
	DefaultExpirationHours int  `json:"default_expiration_hours"`
	RequireReason          bool `json:"require_reason"`
	MaxPendingActions      int  `json:"max_pending_actions"`
	NotificationEnabled    bool `json:"notification_enabled"`
}

// PendingAction represents an action awaiting human approval
type PendingAction struct {
	ID                string                 `json:"id"`
	Type              string                 `json:"type"` // policy_update, config_change, deployment, remediation, scaling
	Title             string                 `json:"title"`
	Description       string                 `json:"description"`
	CreatedAt         time.Time              `json:"created_at"`
	CreatedBy         string                 `json:"created_by"` // AI system or user
	Priority          string                 `json:"priority"`   // low, medium, high, critical
	Status            string                 `json:"status"`     // pending, approved, rejected, expired
	ExpiresAt         time.Time              `json:"expires_at"`
	Context           ActionContext          `json:"context"`
	Diff              ActionDiff             `json:"diff"`
	BlastRadius       BlastRadius            `json:"blast_radius"`
	RiskAssessment    RiskAssessment         `json:"risk_assessment"`
	Recommendations   []ActionRecommendation `json:"recommendations"`
	SimulationResults *SimulationResult      `json:"simulation_results,omitempty"`
	ApprovalHistory   []ApprovalEvent        `json:"approval_history"`
}

// ActionContext provides contextual information about the action
type ActionContext struct {
	TriggerEvent          string   `json:"trigger_event"`
	AffectedServices      []string `json:"affected_services"`
	AffectedTenants       []string `json:"affected_tenants"`
	Environment           string   `json:"environment"`
	Region                string   `json:"region"`
	ConfidenceScore       float64  `json:"confidence_score"`
	DataSources           []string `json:"data_sources"`
	RelatedIncidents      []string `json:"related_incidents"`
	BusinessJustification string   `json:"business_justification"`
}

// ActionDiff represents the changes proposed by the action
type ActionDiff struct {
	Type         string       `json:"type"` // config, policy, code, infrastructure
	OldVersion   string       `json:"old_version"`
	NewVersion   string       `json:"new_version"`
	Changes      []ChangeItem `json:"changes"`
	FilesChanged int          `json:"files_changed"`
	LinesAdded   int          `json:"lines_added"`
	LinesRemoved int          `json:"lines_removed"`
	DiffContent  string       `json:"diff_content"` // Unified diff format
}

// ChangeItem represents a single change within a diff
type ChangeItem struct {
	Path      string `json:"path"`
	Type      string `json:"type"` // added, modified, removed
	Summary   string `json:"summary"`
	RiskLevel string `json:"risk_level"` // low, medium, high
}

// BlastRadius analyzes the potential impact of an action
type BlastRadius struct {
	Scope              string       `json:"scope"` // single_service, multiple_services, tenant, region, global
	AffectedUsers      int          `json:"affected_users"`
	AffectedServices   []string     `json:"affected_services"`
	AffectedDataVolume string       `json:"affected_data_volume"` // "10GB", "1TB", etc.
	EstimatedDowntime  string       `json:"estimated_downtime"`   // "0s", "30s", "5m", etc.
	RollbackTime       string       `json:"rollback_time"`        // Time to rollback if needed
	Dependencies       []string     `json:"dependencies"`
	RiskFactors        []RiskFactor `json:"risk_factors"`
}

// RiskFactor represents a specific risk associated with an action
type RiskFactor struct {
	Category    string `json:"category"` // performance, security, compliance, availability
	Description string `json:"description"`
	Likelihood  string `json:"likelihood"` // low, medium, high
	Impact      string `json:"impact"`     // low, medium, high
	Mitigation  string `json:"mitigation"`
}

// RiskAssessment provides an overall risk analysis
type RiskAssessment struct {
	OverallRisk    string             `json:"overall_risk"` // low, medium, high, critical
	RiskScore      int                `json:"risk_score"`   // 0-100
	Confidence     int                `json:"confidence"`   // 0-100
	RiskCategories RiskCategoryScores `json:"risk_categories"`
	Concerns       []string           `json:"concerns"`
	Mitigations    []string           `json:"mitigations"`
}

// RiskCategoryScores breaks down risk by category
type RiskCategoryScores struct {
	Security    int `json:"security"`
	Operational int `json:"operational"`
	Compliance  int `json:"compliance"`
	Performance int `json:"performance"`
}

// ActionRecommendation represents AI-generated recommendations
type ActionRecommendation struct {
	Type                   string   `json:"type"` // approve, reject, modify, defer
	Reason                 string   `json:"reason"`
	Confidence             float64  `json:"confidence"`
	SuggestedModifications []string `json:"suggested_modifications,omitempty"`
	AlternativeActions     []string `json:"alternative_actions,omitempty"`
}

// SimulationResult contains results from action simulation
type SimulationResult struct {
	SimulatedAt       time.Time `json:"simulated_at"`
	SuccessRate       float64   `json:"success_rate"`
	FailureScenarios  []string  `json:"failure_scenarios"`
	PerformanceImpact string    `json:"performance_impact"`
	EstimatedDuration string    `json:"estimated_duration"`
}

// ApprovalEvent records approval workflow events
type ApprovalEvent struct {
	Timestamp  time.Time `json:"timestamp"`
	Action     string    `json:"action"` // approved, rejected, requested_changes
	User       string    `json:"user"`
	Reason     string    `json:"reason"`
	Conditions []string  `json:"conditions,omitempty"`
}

// ApprovalDecision represents a human approval decision
type ApprovalDecision struct {
	Decision   string   `json:"decision"` // approve, reject
	Reason     string   `json:"reason"`
	Conditions []string `json:"conditions,omitempty"`
	User       string   `json:"user"`
}

// ApprovalStore interface for persisting approval data
type ApprovalStore interface {
	CreatePendingAction(ctx context.Context, action *PendingAction) error
	GetPendingActions(ctx context.Context) ([]PendingAction, error)
	GetPendingAction(ctx context.Context, id string) (*PendingAction, error)
	UpdateActionStatus(ctx context.Context, id string, status string, event ApprovalEvent) error
	GetApprovalHistory(ctx context.Context, actionID string) ([]ApprovalEvent, error)
	CleanupExpiredActions(ctx context.Context) error
}

// NewApprovalWorkflowService creates a new approval workflow service
func NewApprovalWorkflowService(store ApprovalStore) *ApprovalWorkflowService {
	config := WorkflowConfig{
		DefaultExpirationHours: 24,   // 24 hours to approve
		RequireReason:          true, // Always require approval reason
		MaxPendingActions:      100,  // Max 100 pending actions
		NotificationEnabled:    true, // Enable notifications
	}

	return &ApprovalWorkflowService{
		store:  store,
		config: config,
	}
}

// CreatePendingAction creates a new action that requires approval
func (s *ApprovalWorkflowService) CreatePendingAction(ctx context.Context, req CreateActionRequest) (*PendingAction, error) {
	// Generate action ID
	actionID := fmt.Sprintf("action-%d", time.Now().UnixNano())

	// Calculate expiration time
	expiresAt := time.Now().Add(time.Duration(s.config.DefaultExpirationHours) * time.Hour)

	// Analyze blast radius
	blastRadius := s.analyzeBlastRadius(req)

	// Assess risk
	riskAssessment := s.assessRisk(req, blastRadius)

	// Generate AI recommendations
	recommendations := s.generateRecommendations(req, riskAssessment)

	// Create pending action
	action := &PendingAction{
		ID:                actionID,
		Type:              req.Type,
		Title:             req.Title,
		Description:       req.Description,
		CreatedAt:         time.Now(),
		CreatedBy:         req.CreatedBy,
		Priority:          s.calculatePriority(riskAssessment),
		Status:            "pending",
		ExpiresAt:         expiresAt,
		Context:           req.Context,
		Diff:              req.Diff,
		BlastRadius:       blastRadius,
		RiskAssessment:    riskAssessment,
		Recommendations:   recommendations,
		SimulationResults: req.SimulationResults,
		ApprovalHistory:   []ApprovalEvent{},
	}

	// Save to store
	if err := s.store.CreatePendingAction(ctx, action); err != nil {
		return nil, fmt.Errorf("failed to create pending action: %w", err)
	}

	// Send notifications if enabled
	if s.config.NotificationEnabled {
		s.sendNotification(action)
	}

	return action, nil
}

// ProcessApprovalDecision processes a human approval decision
func (s *ApprovalWorkflowService) ProcessApprovalDecision(ctx context.Context, actionID string, decision ApprovalDecision) error {
	// Get pending action
	action, err := s.store.GetPendingAction(ctx, actionID)
	if err != nil {
		return fmt.Errorf("failed to get pending action: %w", err)
	}

	if action.Status != "pending" {
		return fmt.Errorf("action %s is not pending (status: %s)", actionID, action.Status)
	}

	// Check if expired
	if time.Now().After(action.ExpiresAt) {
		return fmt.Errorf("action %s has expired", actionID)
	}

	// Validate decision
	if decision.Decision != "approve" && decision.Decision != "reject" {
		return fmt.Errorf("invalid decision: %s", decision.Decision)
	}

	if s.config.RequireReason && decision.Reason == "" {
		return fmt.Errorf("approval reason is required")
	}

	// Create approval event
	event := ApprovalEvent{
		Timestamp:  time.Now(),
		Action:     decision.Decision + "d", // approved/rejected
		User:       decision.User,
		Reason:     decision.Reason,
		Conditions: decision.Conditions,
	}

	// Update action status
	newStatus := decision.Decision + "d" // approved/rejected
	if err := s.store.UpdateActionStatus(ctx, actionID, newStatus, event); err != nil {
		return fmt.Errorf("failed to update action status: %w", err)
	}

	// Execute action if approved
	if decision.Decision == "approve" {
		if err := s.executeAction(ctx, action); err != nil {
			// Log execution error but don't fail the approval
			fmt.Printf("Failed to execute approved action %s: %v\n", actionID, err)
		}
	}

	return nil
}

// GetPendingActions returns all pending actions
func (s *ApprovalWorkflowService) GetPendingActions(ctx context.Context) ([]PendingAction, error) {
	actions, err := s.store.GetPendingActions(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get pending actions: %w", err)
	}

	// Filter out expired actions
	var validActions []PendingAction
	now := time.Now()
	for _, action := range actions {
		if action.Status == "pending" && now.Before(action.ExpiresAt) {
			validActions = append(validActions, action)
		}
	}

	return validActions, nil
}

// CreateActionRequest represents a request to create a pending action
type CreateActionRequest struct {
	Type              string            `json:"type"`
	Title             string            `json:"title"`
	Description       string            `json:"description"`
	CreatedBy         string            `json:"created_by"`
	Context           ActionContext     `json:"context"`
	Diff              ActionDiff        `json:"diff"`
	SimulationResults *SimulationResult `json:"simulation_results,omitempty"`
}

// Helper methods for risk analysis and recommendations

func (s *ApprovalWorkflowService) analyzeBlastRadius(req CreateActionRequest) BlastRadius {
	// Simplified blast radius analysis
	scope := "single_service"
	if len(req.Context.AffectedServices) > 1 {
		scope = "multiple_services"
	}
	if len(req.Context.AffectedTenants) > 1 {
		scope = "tenant"
	}

	// Estimate affected users based on services and environment
	affectedUsers := len(req.Context.AffectedServices) * 1000
	if req.Context.Environment == "prod" {
		affectedUsers *= 10
	}

	// Generate risk factors
	riskFactors := s.generateRiskFactors(req)

	return BlastRadius{
		Scope:              scope,
		AffectedUsers:      affectedUsers,
		AffectedServices:   req.Context.AffectedServices,
		AffectedDataVolume: s.estimateDataVolume(req),
		EstimatedDowntime:  s.estimateDowntime(req),
		RollbackTime:       s.estimateRollbackTime(req),
		Dependencies:       s.identifyDependencies(req),
		RiskFactors:        riskFactors,
	}
}

func (s *ApprovalWorkflowService) assessRisk(req CreateActionRequest, blastRadius BlastRadius) RiskAssessment {
	// Calculate risk scores by category
	security := s.calculateSecurityRisk(req)
	operational := s.calculateOperationalRisk(req, blastRadius)
	compliance := s.calculateComplianceRisk(req)
	performance := s.calculatePerformanceRisk(req)

	// Overall risk score (weighted average)
	overallScore := (security*30 + operational*30 + compliance*20 + performance*20) / 100

	// Determine overall risk level
	var overallRisk string
	switch {
	case overallScore >= 80:
		overallRisk = "critical"
	case overallScore >= 60:
		overallRisk = "high"
	case overallScore >= 40:
		overallRisk = "medium"
	default:
		overallRisk = "low"
	}

	// Generate concerns and mitigations
	concerns := s.generateConcerns(req, blastRadius)
	mitigations := s.generateMitigations(req, blastRadius)

	return RiskAssessment{
		OverallRisk: overallRisk,
		RiskScore:   overallScore,
		Confidence:  int(req.Context.ConfidenceScore * 100),
		RiskCategories: RiskCategoryScores{
			Security:    security,
			Operational: operational,
			Compliance:  compliance,
			Performance: performance,
		},
		Concerns:    concerns,
		Mitigations: mitigations,
	}
}

func (s *ApprovalWorkflowService) generateRecommendations(req CreateActionRequest, risk RiskAssessment) []ActionRecommendation {
	var recommendations []ActionRecommendation

	// High confidence, low risk -> approve
	if req.Context.ConfidenceScore > 0.8 && risk.RiskScore < 40 {
		recommendations = append(recommendations, ActionRecommendation{
			Type:       "approve",
			Reason:     "High confidence AI recommendation with low risk assessment",
			Confidence: 0.9,
		})
	}

	// High risk -> reject or defer
	if risk.RiskScore > 70 {
		recommendations = append(recommendations, ActionRecommendation{
			Type:       "reject",
			Reason:     "High risk assessment indicates potential for significant negative impact",
			Confidence: 0.8,
		})
	}

	// Medium risk -> approve with conditions
	if risk.RiskScore >= 40 && risk.RiskScore <= 70 {
		recommendations = append(recommendations, ActionRecommendation{
			Type:                   "approve",
			Reason:                 "Medium risk - approve with additional monitoring and quick rollback plan",
			Confidence:             0.7,
			SuggestedModifications: []string{"Enable enhanced monitoring", "Prepare rollback plan"},
		})
	}

	return recommendations
}

func (s *ApprovalWorkflowService) calculatePriority(risk RiskAssessment) string {
	switch risk.OverallRisk {
	case "critical":
		return "critical"
	case "high":
		return "high"
	case "medium":
		return "medium"
	default:
		return "low"
	}
}

// Helper methods for risk calculations
func (s *ApprovalWorkflowService) calculateSecurityRisk(req CreateActionRequest) int {
	risk := 20 // Base risk

	if req.Type == "policy_update" {
		risk += 30
	}
	if req.Context.Environment == "prod" {
		risk += 20
	}
	if req.Context.ConfidenceScore < 0.7 {
		risk += 30
	}

	return minInt(risk, 100)
}

func (s *ApprovalWorkflowService) calculateOperationalRisk(req CreateActionRequest, blastRadius BlastRadius) int {
	risk := 10 // Base risk

	if blastRadius.AffectedUsers > 10000 {
		risk += 40
	} else if blastRadius.AffectedUsers > 1000 {
		risk += 20
	}

	if len(req.Context.AffectedServices) > 3 {
		risk += 30
	}

	return minInt(risk, 100)
}

func (s *ApprovalWorkflowService) calculateComplianceRisk(req CreateActionRequest) int {
	risk := 10 // Base risk

	if req.Context.Environment == "prod" {
		risk += 20
	}
	if req.Type == "config_change" {
		risk += 15
	}

	return minInt(risk, 100)
}

func (s *ApprovalWorkflowService) calculatePerformanceRisk(req CreateActionRequest) int {
	risk := 15 // Base risk

	if req.Type == "scaling" {
		risk += 25
	}
	if len(req.Context.AffectedServices) > 2 {
		risk += 20
	}

	return minInt(risk, 100)
}

func (s *ApprovalWorkflowService) generateRiskFactors(req CreateActionRequest) []RiskFactor {
	var factors []RiskFactor

	if req.Context.Environment == "prod" {
		factors = append(factors, RiskFactor{
			Category:    "availability",
			Description: "Production environment changes carry risk of service disruption",
			Likelihood:  "medium",
			Impact:      "high",
			Mitigation:  "Use blue-green deployment and immediate rollback capability",
		})
	}

	if req.Context.ConfidenceScore < 0.8 {
		factors = append(factors, RiskFactor{
			Category:    "operational",
			Description: "Low AI confidence may indicate edge case or insufficient training data",
			Likelihood:  "medium",
			Impact:      "medium",
			Mitigation:  "Manual review and additional validation before execution",
		})
	}

	return factors
}

func (s *ApprovalWorkflowService) generateConcerns(req CreateActionRequest, blastRadius BlastRadius) []string {
	var concerns []string

	if blastRadius.AffectedUsers > 10000 {
		concerns = append(concerns, "Large number of users affected")
	}

	if req.Context.ConfidenceScore < 0.7 {
		concerns = append(concerns, "Low AI confidence in recommendation")
	}

	if len(req.Context.AffectedServices) > 5 {
		concerns = append(concerns, "Multiple services affected - potential for cascading failures")
	}

	return concerns
}

func (s *ApprovalWorkflowService) generateMitigations(req CreateActionRequest, blastRadius BlastRadius) []string {
	var mitigations []string

	mitigations = append(mitigations, "Automated rollback plan prepared")
	mitigations = append(mitigations, "Enhanced monitoring enabled during change window")

	if req.Context.Environment == "prod" {
		mitigations = append(mitigations, "Change scheduled during low-traffic period")
	}

	return mitigations
}

// Additional helper methods
func (s *ApprovalWorkflowService) estimateDataVolume(req CreateActionRequest) string {
	// Simplified estimation based on services
	serviceCount := len(req.Context.AffectedServices)
	switch {
	case serviceCount > 10:
		return "1TB+"
	case serviceCount > 5:
		return "100GB"
	case serviceCount > 2:
		return "10GB"
	default:
		return "1GB"
	}
}

func (s *ApprovalWorkflowService) estimateDowntime(req CreateActionRequest) string {
	if req.Type == "deployment" {
		return "30s"
	} else if req.Type == "config_change" {
		return "0s"
	} else {
		return "5m"
	}
}

func (s *ApprovalWorkflowService) estimateRollbackTime(req CreateActionRequest) string {
	switch req.Type {
	case "deployment":
		return "2m"
	case "config_change":
		return "30s"
	case "policy_update":
		return "1m"
	default:
		return "5m"
	}
}

func (s *ApprovalWorkflowService) identifyDependencies(req CreateActionRequest) []string {
	// Simplified dependency identification
	var deps []string
	for _, service := range req.Context.AffectedServices {
		deps = append(deps, service+"-db")
		deps = append(deps, service+"-cache")
	}
	return deps
}

func (s *ApprovalWorkflowService) executeAction(ctx context.Context, action *PendingAction) error {
	// This would integrate with actual execution systems
	// For now, just log the execution
	fmt.Printf("Executing approved action: %s (%s)\n", action.ID, action.Type)
	return nil
}

func (s *ApprovalWorkflowService) sendNotification(action *PendingAction) {
	// This would integrate with notification systems (Slack, email, etc.)
	fmt.Printf("Notification: New approval required for %s (Priority: %s)\n", action.Title, action.Priority)
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// HTTP Handlers

// HandleCreateAction handles POST /approval/actions
func (s *ApprovalWorkflowService) HandleCreateAction(w http.ResponseWriter, r *http.Request) {
	var request CreateActionRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	action, err := s.CreatePendingAction(r.Context(), request)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to create action: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(action)
}

// HandleGetPendingActions handles GET /approval/pending
func (s *ApprovalWorkflowService) HandleGetPendingActions(w http.ResponseWriter, r *http.Request) {
	actions, err := s.GetPendingActions(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get pending actions: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"actions": actions,
		"total":   len(actions),
	})
}

// HandleProcessDecision handles POST /approval/{id}
func (s *ApprovalWorkflowService) HandleProcessDecision(w http.ResponseWriter, r *http.Request) {
	// Extract action ID from URL path
	actionID := r.URL.Path[len("/approval/"):]
	if actionID == "" {
		http.Error(w, "Action ID is required", http.StatusBadRequest)
		return
	}

	var decision ApprovalDecision
	if err := json.NewDecoder(r.Body).Decode(&decision); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Set user from context or header (simplified)
	if decision.User == "" {
		decision.User = r.Header.Get("X-User-ID")
		if decision.User == "" {
			decision.User = "unknown"
		}
	}

	err := s.ProcessApprovalDecision(r.Context(), actionID, decision)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to process decision: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status":  "success",
		"message": fmt.Sprintf("Action %s %s successfully", actionID, decision.Decision),
	})
}
