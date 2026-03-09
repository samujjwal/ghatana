package policy

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"time"
)

// PolicyDriftSimulator simulates the effect of outdated policies on historical workloads
// Implements Capability 4: Policy Drift Simulator from Horizontal Slice AI Plan #5
type PolicyDriftSimulator struct {
	policyStore   PolicyStore
	workloadStore WorkloadStore
	config        SimulatorConfig
}

// SimulatorConfig configures the policy drift simulator
type SimulatorConfig struct {
	LookbackDays          int     `json:"lookback_days"`           // Days of historical data to analyze
	SamplingRate          float64 `json:"sampling_rate"`           // 0.0-1.0, fraction of workloads to analyze
	MaxWorkloadsPerRun    int     `json:"max_workloads_per_run"`   // Limit for performance
	DriftThreshold        float64 `json:"drift_threshold"`         // Minimum drift % to report
	ConcurrencyLimit      int     `json:"concurrency_limit"`       // Max concurrent simulations
	IncludeSuccessfulRuns bool    `json:"include_successful_runs"` // Include runs that would succeed
}

// PolicyVersion represents a version of policies at a point in time
type PolicyVersion struct {
	Version     string            `json:"version"`
	Timestamp   time.Time         `json:"timestamp"`
	Policies    map[string]Policy `json:"policies"` // policy_name -> policy
	Description string            `json:"description"`
	CreatedBy   string            `json:"created_by"`
	Tags        []string          `json:"tags"`
}

// Policy represents a security/operational policy
type Policy struct {
	ID           string                 `json:"id"`
	Name         string                 `json:"name"`
	Type         string                 `json:"type"` // "security", "operational", "compliance"
	Rules        []PolicyRule           `json:"rules"`
	Conditions   map[string]interface{} `json:"conditions"`
	Actions      []string               `json:"actions"`  // "allow", "deny", "flag", "audit"
	Severity     int                    `json:"severity"` // 1=Critical, 5=Low
	Enabled      bool                   `json:"enabled"`
	Version      string                 `json:"version"`
	LastModified time.Time              `json:"last_modified"`
	CreatedBy    string                 `json:"created_by"`
	Description  string                 `json:"description"`
	Tags         []string               `json:"tags"`
}

// PolicyRule represents a single rule within a policy
type PolicyRule struct {
	ID          string                 `json:"id"`
	Description string                 `json:"description"`
	Condition   string                 `json:"condition"` // Expression to evaluate
	Action      string                 `json:"action"`    // Action if condition matches
	Priority    int                    `json:"priority"`  // Rule priority (lower = higher priority)
	Metadata    map[string]interface{} `json:"metadata"`
}

// HistoricalWorkload represents a workload that ran in the past
type HistoricalWorkload struct {
	ID            string                 `json:"id"`
	Type          string                 `json:"type"` // "deployment", "batch_job", "api_request"
	Timestamp     time.Time              `json:"timestamp"`
	TenantID      string                 `json:"tenant_id"`
	Service       string                 `json:"service"`
	Action        string                 `json:"action"`
	Resource      string                 `json:"resource"`
	User          string                 `json:"user"`
	Context       WorkloadContext        `json:"context"`
	ActualOutcome string                 `json:"actual_outcome"` // "success", "failure", "blocked"
	PolicyVersion string                 `json:"policy_version"` // Policy version when workload ran
	Duration      time.Duration          `json:"duration"`
	ErrorMessage  string                 `json:"error_message,omitempty"`
	Metadata      map[string]interface{} `json:"metadata"`
}

// WorkloadContext represents the context in which a workload ran
type WorkloadContext struct {
	Environment    string            `json:"environment"` // "prod", "staging", "dev"
	Region         string            `json:"region"`
	IPAddress      string            `json:"ip_address"`
	UserAgent      string            `json:"user_agent"`
	RequestHeaders map[string]string `json:"request_headers"`
	SystemLoad     float64           `json:"system_load"`
	TimeOfDay      int               `json:"time_of_day"` // Hour 0-23
	DayOfWeek      int               `json:"day_of_week"` // 0=Sunday
}

// DriftSimulationResult represents the result of simulating policy drift
type DriftSimulationResult struct {
	SimulationID          string                `json:"simulation_id"`
	OldPolicyVersion      string                `json:"old_policy_version"`
	NewPolicyVersion      string                `json:"new_policy_version"`
	SimulationPeriod      string                `json:"simulation_period"`
	TotalWorkloads        int                   `json:"total_workloads"`
	AnalyzedWorkloads     int                   `json:"analyzed_workloads"`
	DriftPercentage       float64               `json:"drift_percentage"`
	MissedIncidents       []MissedIncident      `json:"missed_incidents"`
	NewlyBlockedWorkloads []BlockedWorkload     `json:"newly_blocked_workloads"`
	PolicyImpactAnalysis  []PolicyImpactSummary `json:"policy_impact_analysis"`
	RiskAssessment        RiskAssessment        `json:"risk_assessment"`
	Recommendations       []DriftRecommendation `json:"recommendations"`
	SimulatedAt           time.Time             `json:"simulated_at"`
	SimulationDuration    time.Duration         `json:"simulation_duration"`
}

// MissedIncident represents an incident that would have been caught by newer policies
type MissedIncident struct {
	WorkloadID       string    `json:"workload_id"`
	IncidentType     string    `json:"incident_type"`
	Severity         int       `json:"severity"`
	Description      string    `json:"description"`
	DetectedByPolicy string    `json:"detected_by_policy"`
	PotentialImpact  string    `json:"potential_impact"`
	Timestamp        time.Time `json:"timestamp"`
	ActualOutcome    string    `json:"actual_outcome"`
}

// BlockedWorkload represents a workload that would be blocked by newer policies
type BlockedWorkload struct {
	WorkloadID     string    `json:"workload_id"`
	BlockingPolicy string    `json:"blocking_policy"`
	BlockingRule   string    `json:"blocking_rule"`
	Reason         string    `json:"reason"`
	BusinessImpact string    `json:"business_impact"` // "low", "medium", "high"
	Workaround     string    `json:"workaround"`
	Timestamp      time.Time `json:"timestamp"`
	WasSuccessful  bool      `json:"was_successful"` // Whether it succeeded with old policy
}

// PolicyImpactSummary summarizes the impact of a specific policy change
type PolicyImpactSummary struct {
	PolicyID          string  `json:"policy_id"`
	PolicyName        string  `json:"policy_name"`
	ChangeType        string  `json:"change_type"` // "added", "modified", "removed"
	AffectedWorkloads int     `json:"affected_workloads"`
	PositiveImpacts   int     `json:"positive_impacts"` // Incidents prevented
	NegativeImpacts   int     `json:"negative_impacts"` // Valid workloads blocked
	NetImpactScore    float64 `json:"net_impact_score"` // Overall impact score
}

// RiskAssessment represents an overall risk assessment of policy drift
type RiskAssessment struct {
	OverallRiskLevel   string  `json:"overall_risk_level"`   // "low", "medium", "high", "critical"
	SecurityRisk       int     `json:"security_risk"`        // 1-5 scale
	OperationalRisk    int     `json:"operational_risk"`     // 1-5 scale
	ComplianceRisk     int     `json:"compliance_risk"`      // 1-5 scale
	BusinessImpactRisk int     `json:"business_impact_risk"` // 1-5 scale
	RecommendedAction  string  `json:"recommended_action"`
	UrgencyLevel       string  `json:"urgency_level"` // "immediate", "high", "medium", "low"
	RiskScore          float64 `json:"risk_score"`    // 0-100
}

// DriftRecommendation represents a recommendation based on drift analysis
type DriftRecommendation struct {
	Type             string   `json:"type"`     // "policy_update", "rollback", "gradual_rollout"
	Priority         int      `json:"priority"` // 1=Highest
	Title            string   `json:"title"`
	Description      string   `json:"description"`
	Action           string   `json:"action"`
	AffectedPolicies []string `json:"affected_policies"`
	EstimatedEffort  string   `json:"estimated_effort"` // "low", "medium", "high"
	Timeline         string   `json:"timeline"`         // "immediate", "short-term", "long-term"
	RiskMitigation   string   `json:"risk_mitigation"`
}

// PolicyStore interface for accessing policy data
type PolicyStore interface {
	GetPolicyVersion(ctx context.Context, version string) (*PolicyVersion, error)
	GetPolicyVersions(ctx context.Context, since time.Time) ([]PolicyVersion, error)
	GetCurrentPolicies(ctx context.Context) (map[string]Policy, error)
}

// WorkloadStore interface for accessing historical workload data
type WorkloadStore interface {
	GetHistoricalWorkloads(ctx context.Context, since time.Time, limit int) ([]HistoricalWorkload, error)
	GetWorkloadsByPolicyVersion(ctx context.Context, version string, limit int) ([]HistoricalWorkload, error)
}

// NewPolicyDriftSimulator creates a new policy drift simulator
func NewPolicyDriftSimulator(policyStore PolicyStore, workloadStore WorkloadStore) *PolicyDriftSimulator {
	config := SimulatorConfig{
		LookbackDays:          30,    // 30 days lookback
		SamplingRate:          0.1,   // 10% sampling for performance
		MaxWorkloadsPerRun:    1000,  // Max 1000 workloads per simulation
		DriftThreshold:        5.0,   // 5% minimum drift to report
		ConcurrencyLimit:      10,    // Max 10 concurrent simulations
		IncludeSuccessfulRuns: false, // Focus on problematic cases
	}

	return &PolicyDriftSimulator{
		policyStore:   policyStore,
		workloadStore: workloadStore,
		config:        config,
	}
}

// SimulatePolicyDrift simulates the effect of running historical workloads against different policy versions
func (s *PolicyDriftSimulator) SimulatePolicyDrift(ctx context.Context, oldVersion, newVersion string) (*DriftSimulationResult, error) {
	startTime := time.Now()
	simulationID := fmt.Sprintf("drift-sim-%d", startTime.Unix())

	// Get policy versions
	oldPolicies, err := s.policyStore.GetPolicyVersion(ctx, oldVersion)
	if err != nil {
		return nil, fmt.Errorf("failed to get old policy version: %w", err)
	}

	newPolicies, err := s.policyStore.GetPolicyVersion(ctx, newVersion)
	if err != nil {
		return nil, fmt.Errorf("failed to get new policy version: %w", err)
	}

	// Get historical workloads from the old policy period
	since := time.Now().AddDate(0, 0, -s.config.LookbackDays)
	workloads, err := s.workloadStore.GetWorkloadsByPolicyVersion(ctx, oldVersion, s.config.MaxWorkloadsPerRun)
	if err != nil {
		return nil, fmt.Errorf("failed to get historical workloads: %w", err)
	}

	// Sample workloads if needed
	if s.config.SamplingRate < 1.0 && len(workloads) > 0 {
		sampleSize := int(float64(len(workloads)) * s.config.SamplingRate)
		if sampleSize > 0 && sampleSize < len(workloads) {
			workloads = workloads[:sampleSize]
		}
	}

	// Run simulation
	result := &DriftSimulationResult{
		SimulationID:          simulationID,
		OldPolicyVersion:      oldVersion,
		NewPolicyVersion:      newVersion,
		SimulationPeriod:      fmt.Sprintf("%s to %s", since.Format("2006-01-02"), time.Now().Format("2006-01-02")),
		TotalWorkloads:        len(workloads),
		AnalyzedWorkloads:     0,
		MissedIncidents:       []MissedIncident{},
		NewlyBlockedWorkloads: []BlockedWorkload{},
		PolicyImpactAnalysis:  []PolicyImpactSummary{},
		SimulatedAt:           startTime,
	}

	// Analyze each workload
	for _, workload := range workloads {
		s.analyzeWorkload(workload, oldPolicies, newPolicies, result)
		result.AnalyzedWorkloads++
	}

	// Calculate drift percentage
	totalChanges := len(result.MissedIncidents) + len(result.NewlyBlockedWorkloads)
	if result.AnalyzedWorkloads > 0 {
		result.DriftPercentage = float64(totalChanges) / float64(result.AnalyzedWorkloads) * 100
	}

	// Skip if drift is below threshold
	if result.DriftPercentage < s.config.DriftThreshold {
		result.DriftPercentage = 0 // Negligible drift
	}

	// Generate policy impact analysis
	result.PolicyImpactAnalysis = s.analyzePolicyImpacts(oldPolicies, newPolicies, result)

	// Generate risk assessment
	result.RiskAssessment = s.assessRisk(result)

	// Generate recommendations
	result.Recommendations = s.generateRecommendations(result)

	result.SimulationDuration = time.Since(startTime)

	return result, nil
}

// analyzeWorkload analyzes a single workload against both policy versions
func (s *PolicyDriftSimulator) analyzeWorkload(workload HistoricalWorkload,
	oldPolicies, newPolicies *PolicyVersion, result *DriftSimulationResult) {

	// Evaluate workload against old policies (baseline)
	oldEvaluation := s.evaluateWorkloadAgainstPolicies(workload, oldPolicies.Policies)

	// Evaluate workload against new policies
	newEvaluation := s.evaluateWorkloadAgainstPolicies(workload, newPolicies.Policies)

	// Compare results
	if oldEvaluation.Action != newEvaluation.Action {
		// Different outcomes indicate drift

		if oldEvaluation.Action == "allow" && newEvaluation.Action != "allow" {
			// Workload would now be blocked
			blocked := BlockedWorkload{
				WorkloadID:     workload.ID,
				BlockingPolicy: newEvaluation.TriggeredPolicy,
				BlockingRule:   newEvaluation.TriggeredRule,
				Reason:         newEvaluation.Reason,
				BusinessImpact: s.assessBusinessImpact(workload),
				Workaround:     s.suggestWorkaround(workload, newEvaluation),
				Timestamp:      workload.Timestamp,
				WasSuccessful:  workload.ActualOutcome == "success",
			}
			result.NewlyBlockedWorkloads = append(result.NewlyBlockedWorkloads, blocked)
		}

		if oldEvaluation.Action != "flag" && newEvaluation.Action == "flag" && workload.ActualOutcome == "failure" {
			// New policies would have flagged a workload that caused an incident
			incident := MissedIncident{
				WorkloadID:       workload.ID,
				IncidentType:     s.inferIncidentType(workload),
				Severity:         s.inferSeverity(workload),
				Description:      fmt.Sprintf("Workload would be flagged by new policy: %s", newEvaluation.Reason),
				DetectedByPolicy: newEvaluation.TriggeredPolicy,
				PotentialImpact:  s.assessPotentialImpact(workload),
				Timestamp:        workload.Timestamp,
				ActualOutcome:    workload.ActualOutcome,
			}
			result.MissedIncidents = append(result.MissedIncidents, incident)
		}
	}
}

// PolicyEvaluation represents the result of evaluating a workload against policies
type PolicyEvaluation struct {
	Action          string // "allow", "deny", "flag", "audit"
	TriggeredPolicy string
	TriggeredRule   string
	Reason          string
	Confidence      float64
	RiskScore       float64
}

// evaluateWorkloadAgainstPolicies evaluates a workload against a set of policies
func (s *PolicyDriftSimulator) evaluateWorkloadAgainstPolicies(workload HistoricalWorkload,
	policies map[string]Policy) PolicyEvaluation {

	evaluation := PolicyEvaluation{
		Action:     "allow", // Default allow
		Confidence: 1.0,
		RiskScore:  0.0,
	}

	// Sort policies by severity (critical first)
	var sortedPolicies []Policy
	for _, policy := range policies {
		if policy.Enabled {
			sortedPolicies = append(sortedPolicies, policy)
		}
	}

	sort.Slice(sortedPolicies, func(i, j int) bool {
		return sortedPolicies[i].Severity < sortedPolicies[j].Severity
	})

	// Evaluate against each policy
	for _, policy := range sortedPolicies {
		if s.matchesPolicy(workload, policy) {
			// Policy matches - determine action
			action := s.determinePolicyAction(workload, policy)

			if action != "allow" {
				evaluation.Action = action
				evaluation.TriggeredPolicy = policy.ID
				evaluation.TriggeredRule = s.findMatchingRule(workload, policy)
				evaluation.Reason = fmt.Sprintf("Policy %s triggered: %s", policy.Name, policy.Description)
				evaluation.RiskScore = float64(6 - policy.Severity) // Convert severity to risk score
				break                                               // First non-allow action wins
			}
		}
	}

	return evaluation
}

// matchesPolicy checks if a workload matches a policy's conditions
func (s *PolicyDriftSimulator) matchesPolicy(workload HistoricalWorkload, policy Policy) bool {
	// Simplified policy matching logic
	// In a real implementation, this would use a proper policy engine

	for _, rule := range policy.Rules {
		if s.matchesRule(workload, rule) {
			return true
		}
	}

	return false
}

// matchesRule checks if a workload matches a specific rule
func (s *PolicyDriftSimulator) matchesRule(workload HistoricalWorkload, rule PolicyRule) bool {
	// Simplified rule matching - in reality this would parse and evaluate expressions
	condition := rule.Condition

	// Example rule patterns
	if condition == "high_risk_deployment" && workload.Type == "deployment" && workload.Context.Environment == "prod" {
		return true
	}

	if condition == "after_hours_access" && (workload.Context.TimeOfDay < 8 || workload.Context.TimeOfDay > 18) {
		return true
	}

	if condition == "external_ip" && !s.isInternalIP(workload.Context.IPAddress) {
		return true
	}

	if condition == "privileged_action" && s.isPrivilegedAction(workload.Action) {
		return true
	}

	return false
}

// determinePolicyAction determines what action a policy should take
func (s *PolicyDriftSimulator) determinePolicyAction(workload HistoricalWorkload, policy Policy) string {
	// Default to the first action in the policy
	if len(policy.Actions) > 0 {
		return policy.Actions[0]
	}
	return "allow"
}

// findMatchingRule finds which rule in a policy matched the workload
func (s *PolicyDriftSimulator) findMatchingRule(workload HistoricalWorkload, policy Policy) string {
	for _, rule := range policy.Rules {
		if s.matchesRule(workload, rule) {
			return rule.ID
		}
	}
	return "unknown"
}

// Helper methods for business logic
func (s *PolicyDriftSimulator) assessBusinessImpact(workload HistoricalWorkload) string {
	if workload.Context.Environment == "prod" {
		return "high"
	} else if workload.Context.Environment == "staging" {
		return "medium"
	} else {
		return "low"
	}
}

func (s *PolicyDriftSimulator) suggestWorkaround(workload HistoricalWorkload, evaluation PolicyEvaluation) string {
	return fmt.Sprintf("Request policy exception for %s workloads", workload.Type)
}

func (s *PolicyDriftSimulator) inferIncidentType(workload HistoricalWorkload) string {
	if workload.ActualOutcome == "failure" {
		return "operational_failure"
	}
	return "security_violation"
}

func (s *PolicyDriftSimulator) inferSeverity(workload HistoricalWorkload) int {
	if workload.Context.Environment == "prod" {
		return 1 // Critical
	} else if workload.Context.Environment == "staging" {
		return 2 // High
	} else {
		return 3 // Medium
	}
}

func (s *PolicyDriftSimulator) assessPotentialImpact(workload HistoricalWorkload) string {
	if workload.Context.Environment == "prod" {
		return "Service disruption, potential data breach"
	}
	return "Limited impact, testing environment affected"
}

func (s *PolicyDriftSimulator) isInternalIP(ip string) bool {
	// Simplified internal IP check
	return ip == "10.0.0.1" || ip == "192.168.1.1" || ip == "localhost"
}

func (s *PolicyDriftSimulator) isPrivilegedAction(action string) bool {
	privilegedActions := []string{"delete", "modify_permissions", "admin_access", "root_access"}
	for _, privileged := range privilegedActions {
		if action == privileged {
			return true
		}
	}
	return false
}

// analyzePolicyImpacts analyzes the impact of individual policy changes
func (s *PolicyDriftSimulator) analyzePolicyImpacts(oldPolicies, newPolicies *PolicyVersion,
	result *DriftSimulationResult) []PolicyImpactSummary {

	impacts := []PolicyImpactSummary{}

	// Find policies that were added, modified, or removed
	policyChanges := s.identifyPolicyChanges(oldPolicies.Policies, newPolicies.Policies)

	for policyID, changeType := range policyChanges {
		impact := PolicyImpactSummary{
			PolicyID:          policyID,
			PolicyName:        s.getPolicyName(policyID, oldPolicies, newPolicies),
			ChangeType:        changeType,
			AffectedWorkloads: s.countAffectedWorkloads(policyID, result),
			PositiveImpacts:   s.countPositiveImpacts(policyID, result),
			NegativeImpacts:   s.countNegativeImpacts(policyID, result),
		}

		// Calculate net impact score
		impact.NetImpactScore = float64(impact.PositiveImpacts*2 - impact.NegativeImpacts)

		impacts = append(impacts, impact)
	}

	return impacts
}

func (s *PolicyDriftSimulator) identifyPolicyChanges(oldPolicies, newPolicies map[string]Policy) map[string]string {
	changes := make(map[string]string)

	// Check for added or modified policies
	for policyID, newPolicy := range newPolicies {
		if oldPolicy, exists := oldPolicies[policyID]; exists {
			if !s.policiesEqual(oldPolicy, newPolicy) {
				changes[policyID] = "modified"
			}
		} else {
			changes[policyID] = "added"
		}
	}

	// Check for removed policies
	for policyID := range oldPolicies {
		if _, exists := newPolicies[policyID]; !exists {
			changes[policyID] = "removed"
		}
	}

	return changes
}

func (s *PolicyDriftSimulator) policiesEqual(p1, p2 Policy) bool {
	// Simplified policy comparison - in reality this would be more thorough
	return p1.Version == p2.Version && p1.Enabled == p2.Enabled
}

func (s *PolicyDriftSimulator) getPolicyName(policyID string, oldPolicies, newPolicies *PolicyVersion) string {
	if policy, exists := newPolicies.Policies[policyID]; exists {
		return policy.Name
	}
	if policy, exists := oldPolicies.Policies[policyID]; exists {
		return policy.Name
	}
	return policyID
}

func (s *PolicyDriftSimulator) countAffectedWorkloads(policyID string, result *DriftSimulationResult) int {
	count := 0
	for _, incident := range result.MissedIncidents {
		if incident.DetectedByPolicy == policyID {
			count++
		}
	}
	for _, blocked := range result.NewlyBlockedWorkloads {
		if blocked.BlockingPolicy == policyID {
			count++
		}
	}
	return count
}

func (s *PolicyDriftSimulator) countPositiveImpacts(policyID string, result *DriftSimulationResult) int {
	count := 0
	for _, incident := range result.MissedIncidents {
		if incident.DetectedByPolicy == policyID {
			count++
		}
	}
	return count
}

func (s *PolicyDriftSimulator) countNegativeImpacts(policyID string, result *DriftSimulationResult) int {
	count := 0
	for _, blocked := range result.NewlyBlockedWorkloads {
		if blocked.BlockingPolicy == policyID && blocked.WasSuccessful {
			count++
		}
	}
	return count
}

// assessRisk generates a risk assessment based on simulation results
func (s *PolicyDriftSimulator) assessRisk(result *DriftSimulationResult) RiskAssessment {
	securityRisk := minInt(len(result.MissedIncidents), 5)
	operationalRisk := minInt(len(result.NewlyBlockedWorkloads)/5, 5)
	complianceRisk := s.calculateComplianceRisk(result)
	businessImpactRisk := s.calculateBusinessImpactRisk(result)

	avgRisk := float64(securityRisk+operationalRisk+complianceRisk+businessImpactRisk) / 4.0
	riskScore := avgRisk * 20 // Convert to 0-100 scale

	var overallRisk, urgency, action string
	if avgRisk >= 4 {
		overallRisk = "critical"
		urgency = "immediate"
		action = "Immediate policy review and rollback consideration required"
	} else if avgRisk >= 3 {
		overallRisk = "high"
		urgency = "high"
		action = "Policy updates needed within 24 hours"
	} else if avgRisk >= 2 {
		overallRisk = "medium"
		urgency = "medium"
		action = "Schedule policy review within 1 week"
	} else {
		overallRisk = "low"
		urgency = "low"
		action = "Monitor and review in next regular cycle"
	}

	return RiskAssessment{
		OverallRiskLevel:   overallRisk,
		SecurityRisk:       securityRisk,
		OperationalRisk:    operationalRisk,
		ComplianceRisk:     complianceRisk,
		BusinessImpactRisk: businessImpactRisk,
		RecommendedAction:  action,
		UrgencyLevel:       urgency,
		RiskScore:          riskScore,
	}
}

func (s *PolicyDriftSimulator) calculateComplianceRisk(result *DriftSimulationResult) int {
	// Count critical missed incidents
	criticalMissed := 0
	for _, incident := range result.MissedIncidents {
		if incident.Severity <= 2 {
			criticalMissed++
		}
	}
	return minInt(criticalMissed, 5)
}

func (s *PolicyDriftSimulator) calculateBusinessImpactRisk(result *DriftSimulationResult) int {
	// Count high business impact blocked workloads
	highImpactBlocked := 0
	for _, blocked := range result.NewlyBlockedWorkloads {
		if blocked.BusinessImpact == "high" {
			highImpactBlocked++
		}
	}
	return minInt(highImpactBlocked/2, 5)
}

// generateRecommendations generates recommendations based on drift analysis
func (s *PolicyDriftSimulator) generateRecommendations(result *DriftSimulationResult) []DriftRecommendation {
	recommendations := []DriftRecommendation{}

	// High drift percentage recommendation
	if result.DriftPercentage > 20 {
		recommendations = append(recommendations, DriftRecommendation{
			Type:             "rollback",
			Priority:         1,
			Title:            "Consider Policy Rollback",
			Description:      fmt.Sprintf("High drift percentage (%.1f%%) indicates significant policy impact", result.DriftPercentage),
			Action:           "Review and consider rolling back problematic policies",
			AffectedPolicies: s.getHighImpactPolicies(result),
			EstimatedEffort:  "low",
			Timeline:         "immediate",
			RiskMitigation:   "Reduces operational disruption",
		})
	}

	// Many missed incidents recommendation
	if len(result.MissedIncidents) > 5 {
		recommendations = append(recommendations, DriftRecommendation{
			Type:             "policy_update",
			Priority:         1,
			Title:            "Urgent Security Policy Update",
			Description:      fmt.Sprintf("%d potential security incidents were missed by old policies", len(result.MissedIncidents)),
			Action:           "Deploy updated security policies immediately",
			AffectedPolicies: s.getSecurityPolicies(result),
			EstimatedEffort:  "medium",
			Timeline:         "immediate",
			RiskMitigation:   "Prevents future security incidents",
		})
	}

	// Many blocked workloads recommendation
	if len(result.NewlyBlockedWorkloads) > 10 {
		recommendations = append(recommendations, DriftRecommendation{
			Type:             "gradual_rollout",
			Priority:         2,
			Title:            "Gradual Policy Rollout",
			Description:      fmt.Sprintf("%d workloads would be blocked by new policies", len(result.NewlyBlockedWorkloads)),
			Action:           "Implement gradual rollout with exceptions for critical workloads",
			AffectedPolicies: s.getOperationalPolicies(result),
			EstimatedEffort:  "high",
			Timeline:         "short-term",
			RiskMitigation:   "Minimizes business disruption",
		})
	}

	// Low drift recommendation
	if result.DriftPercentage < 5 && len(result.MissedIncidents) == 0 {
		recommendations = append(recommendations, DriftRecommendation{
			Type:             "policy_update",
			Priority:         3,
			Title:            "Safe Policy Update",
			Description:      "Low drift detected, safe to proceed with policy updates",
			Action:           "Deploy new policies with standard monitoring",
			AffectedPolicies: []string{},
			EstimatedEffort:  "low",
			Timeline:         "short-term",
			RiskMitigation:   "Standard monitoring and rollback plan",
		})
	}

	return recommendations
}

func (s *PolicyDriftSimulator) getHighImpactPolicies(result *DriftSimulationResult) []string {
	policies := make(map[string]bool)
	for _, impact := range result.PolicyImpactAnalysis {
		if math.Abs(impact.NetImpactScore) > 5 {
			policies[impact.PolicyID] = true
		}
	}

	var policyList []string
	for policy := range policies {
		policyList = append(policyList, policy)
	}
	return policyList
}

func (s *PolicyDriftSimulator) getSecurityPolicies(result *DriftSimulationResult) []string {
	policies := make(map[string]bool)
	for _, incident := range result.MissedIncidents {
		policies[incident.DetectedByPolicy] = true
	}

	var policyList []string
	for policy := range policies {
		policyList = append(policyList, policy)
	}
	return policyList
}

func (s *PolicyDriftSimulator) getOperationalPolicies(result *DriftSimulationResult) []string {
	policies := make(map[string]bool)
	for _, blocked := range result.NewlyBlockedWorkloads {
		policies[blocked.BlockingPolicy] = true
	}

	var policyList []string
	for policy := range policies {
		policyList = append(policyList, policy)
	}
	return policyList
}

// HTTP Handlers

// HandleSimulateDrift handles POST /policy/simulate-drift
func (s *PolicyDriftSimulator) HandleSimulateDrift(w http.ResponseWriter, r *http.Request) {
	var request struct {
		OldVersion string `json:"old_version"`
		NewVersion string `json:"new_version"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if request.OldVersion == "" || request.NewVersion == "" {
		http.Error(w, "Both old_version and new_version are required", http.StatusBadRequest)
		return
	}

	result, err := s.SimulatePolicyDrift(r.Context(), request.OldVersion, request.NewVersion)
	if err != nil {
		http.Error(w, fmt.Sprintf("Simulation failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// HandleGetPolicyVersions handles GET /policy/versions
func (s *PolicyDriftSimulator) HandleGetPolicyVersions(w http.ResponseWriter, r *http.Request) {
	since := time.Now().AddDate(0, -3, 0) // Last 3 months
	if sinceParam := r.URL.Query().Get("since"); sinceParam != "" {
		if parsedTime, err := time.Parse(time.RFC3339, sinceParam); err == nil {
			since = parsedTime
		}
	}

	versions, err := s.policyStore.GetPolicyVersions(r.Context(), since)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get policy versions: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"versions": versions,
		"total":    len(versions),
	})
}

// HandleGetSimulationHistory handles GET /policy/simulations
func (s *PolicyDriftSimulator) HandleGetSimulationHistory(w http.ResponseWriter, r *http.Request) {
	// This would typically query a database of previous simulations
	// For now, return empty list
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"simulations": []interface{}{},
		"total":       0,
	})
}

// Use math.Min for floating point, this helper for integers
func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
