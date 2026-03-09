package miner

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// SmartCandidateGenerator creates automation candidates with intelligent recommendations
type SmartCandidateGenerator struct {
	actionTemplates   map[string]ActionTemplate
	playbookTemplates map[string]PlaybookTemplate
}

// ActionTemplate defines a template for creating automated actions
type ActionTemplate struct {
	Name        string
	Type        string
	Description string
	Command     string
	Parameters  map[string]interface{}
	Timeout     time.Duration
	Complexity  float64 // 0-1 scale
	Risk        float64 // 0-1 scale
	Tags        []string
}

// PlaybookTemplate defines a template for creating automation playbooks
type PlaybookTemplate struct {
	Name          string
	Description   string
	ActionTypes   []string
	Complexity    float64
	Risk          float64
	Prerequisites []string
	Tags          []string
}

// NewSmartCandidateGenerator creates a new candidate generator
func NewSmartCandidateGenerator() *SmartCandidateGenerator {
	generator := &SmartCandidateGenerator{
		actionTemplates:   make(map[string]ActionTemplate),
		playbookTemplates: make(map[string]PlaybookTemplate),
	}

	generator.initializeTemplates()
	return generator
}

// GenerateCandidate creates automation candidate from sequence
func (g *SmartCandidateGenerator) GenerateCandidate(ctx context.Context, sequence ActionSequence) (*AutomationCandidate, error) {
	candidate := &AutomationCandidate{
		ID:          generateCandidateID(sequence.Pattern),
		Title:       g.generateTitle(sequence),
		Description: g.generateDescription(sequence),
		Status:      StatusPending,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
		Tags:        g.generateTags(sequence),
		Evidence: CandidateEvidence{
			Sequences:       []ActionSequence{sequence},
			Frequency:       sequence.Frequency,
			TimeSaved:       sequence.AvgDuration,
			SuccessRate:     sequence.SuccessRate,
			AffectedTeams:   g.extractAffectedTeams(sequence),
			BusinessImpact:  g.generateBusinessImpact(sequence),
			ComplexityScore: g.estimateComplexity(sequence),
			RiskScore:       g.estimateRisk(sequence),
		},
	}

	// Generate recommendation
	recommendation, err := g.GenerateRecommendation(ctx, candidate)
	if err != nil {
		return nil, fmt.Errorf("failed to generate recommendation: %w", err)
	}
	candidate.Recommendation = *recommendation

	return candidate, nil
}

// GenerateRecommendation creates specific automation recommendations
func (g *SmartCandidateGenerator) GenerateRecommendation(ctx context.Context, candidate *AutomationCandidate) (*Recommendation, error) {
	sequence := candidate.Evidence.Sequences[0]
	actionTypes := strings.Split(sequence.Pattern, "->")

	recommendation := &Recommendation{
		ImplementationSteps: g.generateImplementationSteps(sequence),
		Prerequisites:       g.generatePrerequisites(sequence),
		TestPlan:            g.generateTestPlan(sequence),
		RollbackPlan:        g.generateRollbackPlan(sequence),
		EstimatedEffort:     g.estimateEffort(sequence),
	}

	// Determine recommendation type and generate specs
	if len(actionTypes) == 1 {
		// Single action - create action spec
		recommendation.Type = RecommendationAction
		actionSpec, err := g.generateActionSpec(actionTypes[0], sequence)
		if err != nil {
			return nil, err
		}
		recommendation.ActionSpec = *actionSpec
	} else if len(actionTypes) <= 6 {
		// Multi-step sequence - create playbook spec
		recommendation.Type = RecommendationPlaybook
		playbookSpec, err := g.generatePlaybookSpec(actionTypes, sequence)
		if err != nil {
			return nil, err
		}
		recommendation.PlaybookSpec = *playbookSpec
	} else {
		// Too complex for direct automation - recommend policy
		recommendation.Type = RecommendationPolicy
		recommendation.ImplementationSteps = append(
			[]string{"Break down complex workflow into smaller automation units"},
			recommendation.ImplementationSteps...,
		)
	}

	return recommendation, nil
}

// ValidateCandidate checks if candidate is viable
func (g *SmartCandidateGenerator) ValidateCandidate(ctx context.Context, candidate *AutomationCandidate) error {
	evidence := candidate.Evidence

	// Check minimum viability thresholds
	if evidence.Frequency < 3 {
		return fmt.Errorf("insufficient frequency: %d (minimum 3)", evidence.Frequency)
	}

	if evidence.SuccessRate < 0.6 {
		return fmt.Errorf("success rate too low: %.2f (minimum 0.6)", evidence.SuccessRate)
	}

	if evidence.TimeSaved < 2*time.Minute {
		return fmt.Errorf("time savings too low: %s (minimum 2m)", evidence.TimeSaved)
	}

	if evidence.ComplexityScore > 0.8 {
		return fmt.Errorf("complexity too high: %.2f (maximum 0.8)", evidence.ComplexityScore)
	}

	if evidence.RiskScore > 0.7 {
		return fmt.Errorf("risk too high: %.2f (maximum 0.7)", evidence.RiskScore)
	}

	return nil
}

// Initialize action and playbook templates
func (g *SmartCandidateGenerator) initializeTemplates() {
	// Common action templates
	g.actionTemplates = map[string]ActionTemplate{
		"restart_service": {
			Name:        "Restart Service",
			Type:        "service_operation",
			Description: "Restart a system service",
			Command:     "systemctl restart {{.service_name}}",
			Parameters:  map[string]interface{}{"service_name": ""},
			Timeout:     2 * time.Minute,
			Complexity:  0.2,
			Risk:        0.3,
			Tags:        []string{"service", "restart", "low-risk"},
		},
		"clear_cache": {
			Name:        "Clear Cache",
			Type:        "cache_operation",
			Description: "Clear application cache",
			Command:     "redis-cli FLUSHDB",
			Parameters:  map[string]interface{}{},
			Timeout:     30 * time.Second,
			Complexity:  0.1,
			Risk:        0.2,
			Tags:        []string{"cache", "redis", "low-risk"},
		},
		"scale_deployment": {
			Name:        "Scale Deployment",
			Type:        "kubernetes_operation",
			Description: "Scale Kubernetes deployment",
			Command:     "kubectl scale deployment {{.deployment}} --replicas={{.replicas}}",
			Parameters:  map[string]interface{}{"deployment": "", "replicas": 3},
			Timeout:     5 * time.Minute,
			Complexity:  0.4,
			Risk:        0.4,
			Tags:        []string{"kubernetes", "scaling", "medium-risk"},
		},
		"run_health_check": {
			Name:        "Run Health Check",
			Type:        "diagnostic",
			Description: "Execute health check on service",
			Command:     "curl -f {{.health_endpoint}}/health",
			Parameters:  map[string]interface{}{"health_endpoint": ""},
			Timeout:     30 * time.Second,
			Complexity:  0.1,
			Risk:        0.1,
			Tags:        []string{"health-check", "diagnostic", "safe"},
		},
		"collect_logs": {
			Name:        "Collect Logs",
			Type:        "diagnostic",
			Description: "Collect logs for analysis",
			Command:     "kubectl logs {{.pod}} --tail=1000",
			Parameters:  map[string]interface{}{"pod": ""},
			Timeout:     1 * time.Minute,
			Complexity:  0.2,
			Risk:        0.1,
			Tags:        []string{"logs", "diagnostic", "safe"},
		},
		"toggle_feature_flag": {
			Name:        "Toggle Feature Flag",
			Type:        "configuration",
			Description: "Enable or disable feature flag",
			Command:     "curl -X POST {{.config_api}}/flags/{{.flag_name}}/{{.action}}",
			Parameters:  map[string]interface{}{"config_api": "", "flag_name": "", "action": "enable"},
			Timeout:     15 * time.Second,
			Complexity:  0.3,
			Risk:        0.5,
			Tags:        []string{"feature-flag", "configuration", "medium-risk"},
		},
	}

	// Common playbook templates
	g.playbookTemplates = map[string]PlaybookTemplate{
		"service_recovery": {
			Name:          "Service Recovery Playbook",
			Description:   "Standard service recovery procedure",
			ActionTypes:   []string{"run_health_check", "collect_logs", "restart_service", "run_health_check"},
			Complexity:    0.4,
			Risk:          0.3,
			Prerequisites: []string{"Service monitoring configured", "Log aggregation available"},
			Tags:          []string{"recovery", "service", "standard"},
		},
		"incident_response": {
			Name:          "Incident Response Playbook",
			Description:   "Automated incident response workflow",
			ActionTypes:   []string{"collect_logs", "run_health_check", "scale_deployment", "toggle_feature_flag"},
			Complexity:    0.6,
			Risk:          0.4,
			Prerequisites: []string{"Incident management system", "Auto-scaling permissions"},
			Tags:          []string{"incident", "response", "complex"},
		},
		"diagnostic_sequence": {
			Name:          "Diagnostic Playbook",
			Description:   "Automated diagnostic workflow",
			ActionTypes:   []string{"run_health_check", "collect_logs", "collect_metrics"},
			Complexity:    0.2,
			Risk:          0.1,
			Prerequisites: []string{"Monitoring infrastructure"},
			Tags:          []string{"diagnostic", "safe", "read-only"},
		},
	}
}

func (g *SmartCandidateGenerator) generateTitle(sequence ActionSequence) string {
	actionTypes := strings.Split(sequence.Pattern, "->")

	if len(actionTypes) == 1 {
		if template, exists := g.actionTemplates[actionTypes[0]]; exists {
			return fmt.Sprintf("Automate: %s", template.Name)
		}
		return fmt.Sprintf("Automate: %s", strings.ReplaceAll(actionTypes[0], "_", " "))
	}

	// Check for known playbook patterns
	for _, template := range g.playbookTemplates {
		if g.matchesTemplate(actionTypes, template.ActionTypes) {
			return fmt.Sprintf("Automate: %s", template.Name)
		}
	}

	return fmt.Sprintf("Automate %d-step workflow for %s", len(actionTypes), sequence.IncidentType)
}

func (g *SmartCandidateGenerator) generateDescription(sequence ActionSequence) string {
	return fmt.Sprintf(
		"This workflow occurs %d times with %.1f%% success rate, taking an average of %s. "+
			"Automation could save approximately %s per incident and reduce manual errors.",
		sequence.Frequency,
		sequence.SuccessRate*100,
		sequence.AvgDuration,
		sequence.AvgDuration,
	)
}

func (g *SmartCandidateGenerator) generateTags(sequence ActionSequence) []string {
	tags := []string{"toil-miner", "automation-candidate"}

	// Add incident type tags
	if sequence.IncidentType != "" {
		tags = append(tags, sequence.IncidentType)
	}

	// Add frequency-based tags
	if sequence.Frequency >= 20 {
		tags = append(tags, "high-frequency")
	} else if sequence.Frequency >= 10 {
		tags = append(tags, "medium-frequency")
	}

	// Add complexity tags
	actionCount := strings.Count(sequence.Pattern, "->") + 1
	if actionCount <= 2 {
		tags = append(tags, "simple")
	} else if actionCount <= 4 {
		tags = append(tags, "moderate")
	} else {
		tags = append(tags, "complex")
	}

	return tags
}

func (g *SmartCandidateGenerator) generateBusinessImpact(sequence ActionSequence) string {
	totalTimeSaved := time.Duration(sequence.Frequency) * sequence.AvgDuration
	hours := totalTimeSaved.Hours()

	if hours < 1 {
		return fmt.Sprintf("Low impact: %.1f hours saved per period", hours)
	} else if hours < 8 {
		return fmt.Sprintf("Medium impact: %.1f hours saved per period", hours)
	} else {
		return fmt.Sprintf("High impact: %.1f hours saved per period (%.1f days)", hours, hours/8)
	}
}

func (g *SmartCandidateGenerator) estimateComplexity(sequence ActionSequence) float64 {
	actionTypes := strings.Split(sequence.Pattern, "->")
	totalComplexity := 0.0

	for _, actionType := range actionTypes {
		if template, exists := g.actionTemplates[actionType]; exists {
			totalComplexity += template.Complexity
		} else {
			// Unknown action - assume medium complexity
			totalComplexity += 0.5
		}
	}

	// Average complexity per action, with sequence complexity bonus
	baseComplexity := totalComplexity / float64(len(actionTypes))
	sequenceBonus := float64(len(actionTypes)-1) * 0.1 // Each additional step adds 10%

	return minFloat(baseComplexity+sequenceBonus, 1.0)
}

func (g *SmartCandidateGenerator) estimateRisk(sequence ActionSequence) float64 {
	actionTypes := strings.Split(sequence.Pattern, "->")
	maxRisk := 0.0

	for _, actionType := range actionTypes {
		if template, exists := g.actionTemplates[actionType]; exists {
			maxRisk = maxFloat(maxRisk, template.Risk)
		} else {
			// Unknown action - assume medium risk
			maxRisk = maxFloat(maxRisk, 0.5)
		}
	}

	// Sequence risk increases with length and decreases with success rate
	sequenceRiskMultiplier := 1.0 + (float64(len(actionTypes)-1) * 0.1)
	successRateBonus := sequence.SuccessRate * 0.2 // Up to 20% risk reduction

	finalRisk := (maxRisk * sequenceRiskMultiplier) - successRateBonus
	return minFloat(maxFloat(finalRisk, 0.0), 1.0)
}

func (g *SmartCandidateGenerator) extractAffectedTeams(sequence ActionSequence) []string {
	// This would typically analyze the actual action logs to extract team information
	// For now, return placeholder based on incident type
	teams := []string{}

	if sequence.IncidentType != "" {
		switch sequence.IncidentType {
		case "service_outage":
			teams = []string{"sre", "backend"}
		case "database_issue":
			teams = []string{"dba", "backend"}
		case "frontend_error":
			teams = []string{"frontend", "sre"}
		default:
			teams = []string{"operations"}
		}
	}

	return teams
}

func (g *SmartCandidateGenerator) generateActionSpec(actionType string, sequence ActionSequence) (*ActionSpec, error) {
	template, exists := g.actionTemplates[actionType]
	if !exists {
		// Create generic action spec
		return &ActionSpec{
			Name:    strings.ReplaceAll(actionType, "_", " "),
			Type:    "generic",
			Command: fmt.Sprintf("# Implement %s automation", actionType),
			Timeout: 5 * time.Minute,
			RetryPolicy: RetryPolicy{
				MaxAttempts: 3,
				Backoff:     10 * time.Second,
				Multiplier:  2.0,
				MaxBackoff:  5 * time.Minute,
			},
			Validation: ValidationRules{
				Preconditions:  []string{fmt.Sprintf("Verify %s is safe to execute", actionType)},
				Postconditions: []string{fmt.Sprintf("Confirm %s completed successfully", actionType)},
				SafetyChecks:   []string{"Check system health", "Verify no ongoing maintenance"},
			},
		}, nil
	}

	return &ActionSpec{
		Name:       template.Name,
		Type:       template.Type,
		Command:    template.Command,
		Parameters: template.Parameters,
		Timeout:    template.Timeout,
		RetryPolicy: RetryPolicy{
			MaxAttempts: 3,
			Backoff:     10 * time.Second,
			Multiplier:  2.0,
			MaxBackoff:  template.Timeout,
		},
		Validation: ValidationRules{
			Preconditions:  []string{"System is healthy", "No maintenance windows active"},
			Postconditions: []string{"Action completed successfully", "System remains stable"},
			SafetyChecks:   []string{"Verify target exists", "Check resource availability"},
		},
	}, nil
}

func (g *SmartCandidateGenerator) generatePlaybookSpec(actionTypes []string, sequence ActionSequence) (*PlaybookSpec, error) {
	// Check for matching playbook template
	for _, template := range g.playbookTemplates {
		if g.matchesTemplate(actionTypes, template.ActionTypes) {
			steps := make([]ActionSpec, len(actionTypes))
			for i, actionType := range actionTypes {
				spec, err := g.generateActionSpec(actionType, sequence)
				if err != nil {
					return nil, err
				}
				steps[i] = *spec
			}

			return &PlaybookSpec{
				Name:         template.Name,
				Description:  template.Description,
				Steps:        steps,
				Conditions:   []string{"System is stable", "Required permissions available"},
				Dependencies: template.Prerequisites,
				FailureMode:  "Stop on first failure and alert operator",
			}, nil
		}
	}

	// Create generic playbook
	steps := make([]ActionSpec, len(actionTypes))
	for i, actionType := range actionTypes {
		spec, err := g.generateActionSpec(actionType, sequence)
		if err != nil {
			return nil, err
		}
		steps[i] = *spec
	}

	return &PlaybookSpec{
		Name:         fmt.Sprintf("%s Recovery Playbook", sequence.IncidentType),
		Description:  fmt.Sprintf("Automated playbook for %s incidents", sequence.IncidentType),
		Steps:        steps,
		Conditions:   []string{"Incident confirmed", "System accessible"},
		Dependencies: []string{"Monitoring available", "Required permissions"},
		FailureMode:  "Escalate to human operator on failure",
	}, nil
}

func (g *SmartCandidateGenerator) matchesTemplate(actionTypes, templateTypes []string) bool {
	if len(actionTypes) != len(templateTypes) {
		return false
	}

	for i, actionType := range actionTypes {
		if actionType != templateTypes[i] {
			return false
		}
	}

	return true
}

func (g *SmartCandidateGenerator) generateImplementationSteps(sequence ActionSequence) []string {
	steps := []string{
		"1. Review and validate automation requirements",
		"2. Set up development and testing environment",
		"3. Implement automation scripts/workflows",
		"4. Test automation in staging environment",
		"5. Create monitoring and alerting for automation",
		"6. Document automation procedures and rollback",
		"7. Train team on new automated workflows",
		"8. Deploy to production with gradual rollout",
		"9. Monitor automation performance and success rate",
		"10. Iterate and improve based on feedback",
	}

	actionCount := strings.Count(sequence.Pattern, "->") + 1
	if actionCount <= 2 {
		// Simple automation - remove some steps
		return steps[0:7]
	}

	return steps
}

func (g *SmartCandidateGenerator) generatePrerequisites(sequence ActionSequence) []string {
	prerequisites := []string{
		"Automation platform (e.g., Ansible, Kubernetes Jobs)",
		"Monitoring and alerting infrastructure",
		"Version control for automation scripts",
		"Testing environment that mirrors production",
		"Access controls and permission management",
	}

	// Add specific prerequisites based on actions
	actionTypes := strings.Split(sequence.Pattern, "->")
	for _, actionType := range actionTypes {
		switch actionType {
		case "restart_service":
			prerequisites = append(prerequisites, "Service discovery and health checks")
		case "scale_deployment":
			prerequisites = append(prerequisites, "Container orchestration platform")
		case "toggle_feature_flag":
			prerequisites = append(prerequisites, "Feature flag management system")
		}
	}

	return prerequisites
}

func (g *SmartCandidateGenerator) generateTestPlan(sequence ActionSequence) []string {
	return []string{
		"1. Unit tests for individual automation components",
		"2. Integration tests with staging environment",
		"3. Chaos engineering tests for failure scenarios",
		"4. Performance tests under various load conditions",
		"5. Security tests for access controls and permissions",
		"6. End-to-end tests simulating real incidents",
		"7. Rollback tests to ensure safe recovery",
		"8. Documentation and runbook validation",
	}
}

func (g *SmartCandidateGenerator) generateRollbackPlan(sequence ActionSequence) string {
	return fmt.Sprintf(
		"If automation fails or causes issues: (1) Immediately disable automation, "+
			"(2) Alert on-call engineer, (3) Revert to manual process, "+
			"(4) Assess impact and root cause, (5) Fix automation before re-enabling. "+
			"Automation will be disabled automatically after %d consecutive failures.",
		3, // Default failure threshold
	)
}

func (g *SmartCandidateGenerator) estimateEffort(sequence ActionSequence) string {
	actionCount := strings.Count(sequence.Pattern, "->") + 1
	complexity := g.estimateComplexity(sequence)

	baseDays := float64(actionCount) * 0.5   // 0.5 days per action
	complexityMultiplier := 1.0 + complexity // 1.0 to 2.0 multiplier

	totalDays := baseDays * complexityMultiplier

	if totalDays < 1 {
		return "1-2 days"
	} else if totalDays < 3 {
		return "2-5 days"
	} else if totalDays < 7 {
		return "1-2 weeks"
	} else {
		return "2-4 weeks"
	}
}
