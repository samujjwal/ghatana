package playbook

import (
	"context"
	"fmt"
	"math"
	"regexp"
	"strings"
	"time"

	"go.uber.org/zap"
)

// PlaybookValidatorImpl implements PlaybookValidator interface
type PlaybookValidatorImpl struct {
	logger *zap.Logger
	config ValidationConfig
}

// ValidationConfig configures validation behavior
type ValidationConfig struct {
	// Safety settings
	ProhibitedCommands   []string `json:"prohibited_commands"`
	DangerousPatterns    []string `json:"dangerous_patterns"`
	RequiredSafetyChecks []string `json:"required_safety_checks"`
	MaxRiskLevel         string   `json:"max_risk_level"`

	// Quality settings
	MinStepsRequired  int  `json:"min_steps_required"`
	MaxStepsAllowed   int  `json:"max_steps_allowed"`
	RequireValidation bool `json:"require_validation"`
	RequireRollback   bool `json:"require_rollback"`

	// Compliance settings
	RequiredFields     []string      `json:"required_fields"`
	AllowedActionTypes []string      `json:"allowed_action_types"`
	MaxTimeout         time.Duration `json:"max_timeout"`

	// Scoring weights
	CompletenessWeight  float64 `json:"completeness_weight"`
	SafetyWeight        float64 `json:"safety_weight"`
	ClarityWeight       float64 `json:"clarity_weight"`
	ExecutabilityWeight float64 `json:"executability_weight"`
}

// NewPlaybookValidator creates a new playbook validator
func NewPlaybookValidator(logger *zap.Logger, config ValidationConfig) *PlaybookValidatorImpl {
	// Apply defaults
	if len(config.ProhibitedCommands) == 0 {
		config.ProhibitedCommands = []string{
			"rm -rf /", "rm -rf /*", ":(){ :|:& };:", "chmod -R 777 /",
			"dd if=/dev/zero of=/dev/sda", "mkfs", "fdisk", "parted",
			"shutdown -h now", "reboot", "halt", "poweroff",
			"userdel", "passwd", "chpasswd", "sudo su -",
		}
	}

	if len(config.DangerousPatterns) == 0 {
		config.DangerousPatterns = []string{
			`rm\s+-r.*\/`, `chmod\s+777`, `chown\s+.*:.*\/`,
			`DROP\s+DATABASE`, `TRUNCATE\s+TABLE`, `DELETE\s+FROM.*WHERE.*1=1`,
			`sudo\s+.*`, `su\s+-`, `passwd\s+`,
		}
	}

	if config.MinStepsRequired == 0 {
		config.MinStepsRequired = 1
	}
	if config.MaxStepsAllowed == 0 {
		config.MaxStepsAllowed = 50
	}
	if config.MaxTimeout == 0 {
		config.MaxTimeout = 10 * time.Minute
	}

	// Default scoring weights
	if config.CompletenessWeight == 0 {
		config.CompletenessWeight = 0.3
	}
	if config.SafetyWeight == 0 {
		config.SafetyWeight = 0.4
	}
	if config.ClarityWeight == 0 {
		config.ClarityWeight = 0.2
	}
	if config.ExecutabilityWeight == 0 {
		config.ExecutabilityWeight = 0.1
	}

	return &PlaybookValidatorImpl{
		logger: logger,
		config: config,
	}
}

// ValidatePlaybook validates a complete playbook
func (v *PlaybookValidatorImpl) ValidatePlaybook(ctx context.Context, playbook *Playbook) (*ValidationResult, error) {
	v.logger.Debug("Validating playbook", zap.String("playbook_id", playbook.PlaybookID))

	result := &ValidationResult{
		Valid:       true,
		Errors:      []ValidationError{},
		Warnings:    []ValidationWarning{},
		Suggestions: []string{},
		Score:       0.0,
	}

	// Validate basic structure
	v.validateStructure(playbook, result)

	// Validate steps
	v.validateSteps(playbook, result)

	// Validate validation steps
	v.validateValidationSteps(playbook, result)

	// Validate rollback steps
	v.validateRollbackSteps(playbook, result)

	// Safety validation
	v.validateSafety(playbook, result)

	// Calculate overall score
	result.Score = v.calculateScore(playbook, result)

	// Determine if playbook is valid
	result.Valid = len(result.Errors) == 0

	v.logger.Debug("Playbook validation completed",
		zap.String("playbook_id", playbook.PlaybookID),
		zap.Bool("valid", result.Valid),
		zap.Float64("score", result.Score),
		zap.Int("errors", len(result.Errors)),
		zap.Int("warnings", len(result.Warnings)))

	return result, nil
}

// validateStructure validates basic playbook structure
func (v *PlaybookValidatorImpl) validateStructure(playbook *Playbook, result *ValidationResult) {
	// Check required fields
	if playbook.Title == "" {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "title",
			Message:  "Playbook title is required",
			Severity: "high",
			Code:     "MISSING_TITLE",
		})
	}

	if playbook.Description == "" {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "description",
			Message:  "Playbook description is required",
			Severity: "medium",
			Code:     "MISSING_DESCRIPTION",
		})
	}

	if playbook.Severity == "" {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "severity",
			Message:  "Playbook severity is required",
			Severity: "medium",
			Code:     "MISSING_SEVERITY",
		})
	}

	// Validate severity value
	validSeverities := []string{"low", "medium", "high", "critical"}
	if playbook.Severity != "" && !v.contains(validSeverities, playbook.Severity) {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "severity",
			Message:  fmt.Sprintf("Invalid severity '%s'. Must be one of: %v", playbook.Severity, validSeverities),
			Severity: "medium",
			Code:     "INVALID_SEVERITY",
		})
	}

	// Check step count
	stepCount := len(playbook.Steps)
	if stepCount < v.config.MinStepsRequired {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "steps",
			Message:  fmt.Sprintf("Playbook must have at least %d steps, found %d", v.config.MinStepsRequired, stepCount),
			Severity: "high",
			Code:     "INSUFFICIENT_STEPS",
		})
	}

	if stepCount > v.config.MaxStepsAllowed {
		result.Warnings = append(result.Warnings, ValidationWarning{
			Field:      "steps",
			Message:    fmt.Sprintf("Playbook has %d steps, which may be too many", stepCount),
			Suggestion: "Consider breaking into smaller playbooks or combining related steps",
		})
	}

	// Check for validation steps if required
	if v.config.RequireValidation && len(playbook.ValidationSteps) == 0 {
		result.Errors = append(result.Errors, ValidationError{
			Field:    "validation_steps",
			Message:  "Validation steps are required",
			Severity: "medium",
			Code:     "MISSING_VALIDATION",
		})
	}

	// Check for rollback steps if required
	if v.config.RequireRollback && len(playbook.RollbackSteps) == 0 {
		result.Warnings = append(result.Warnings, ValidationWarning{
			Field:      "rollback_steps",
			Message:    "No rollback steps defined",
			Suggestion: "Add rollback procedures for safer execution",
		})
	}
}

// validateSteps validates individual playbook steps
func (v *PlaybookValidatorImpl) validateSteps(playbook *Playbook, result *ValidationResult) {
	stepNumbers := make(map[int]bool)

	for i, step := range playbook.Steps {
		stepField := fmt.Sprintf("steps[%d]", i)

		// Validate step structure
		if step.Title == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".title",
				Message:  "Step title is required",
				Severity: "medium",
				Code:     "MISSING_STEP_TITLE",
			})
		}

		if step.Description == "" {
			result.Warnings = append(result.Warnings, ValidationWarning{
				Field:      stepField + ".description",
				Message:    "Step description is missing",
				Suggestion: "Add detailed description for better clarity",
			})
		}

		// Check step numbers for duplicates
		if stepNumbers[step.StepNumber] {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".step_number",
				Message:  fmt.Sprintf("Duplicate step number %d", step.StepNumber),
				Severity: "high",
				Code:     "DUPLICATE_STEP_NUMBER",
			})
		}
		stepNumbers[step.StepNumber] = true

		// Validate action type
		if step.ActionType == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".action_type",
				Message:  "Action type is required",
				Severity: "high",
				Code:     "MISSING_ACTION_TYPE",
			})
		} else {
			validTypes := []string{"command", "api_call", "manual", "script"}
			if !v.contains(validTypes, step.ActionType) {
				result.Errors = append(result.Errors, ValidationError{
					Field:    stepField + ".action_type",
					Message:  fmt.Sprintf("Invalid action type '%s'. Must be one of: %v", step.ActionType, validTypes),
					Severity: "medium",
					Code:     "INVALID_ACTION_TYPE",
				})
			}
		}

		// Validate command for command-type steps
		if step.ActionType == "command" {
			if step.Command == "" {
				result.Errors = append(result.Errors, ValidationError{
					Field:    stepField + ".command",
					Message:  "Command is required for command-type steps",
					Severity: "high",
					Code:     "MISSING_COMMAND",
				})
			} else {
				// Check for prohibited commands
				v.validateCommand(step.Command, stepField, result)
			}
		}

		// Validate timeout
		if step.Timeout > v.config.MaxTimeout {
			result.Warnings = append(result.Warnings, ValidationWarning{
				Field:      stepField + ".timeout",
				Message:    fmt.Sprintf("Timeout %v exceeds maximum allowed %v", step.Timeout, v.config.MaxTimeout),
				Suggestion: "Consider reducing timeout or breaking into smaller operations",
			})
		}

		// Validate risk level
		validRiskLevels := []string{"low", "medium", "high", "critical"}
		if step.RiskLevel != "" && !v.contains(validRiskLevels, step.RiskLevel) {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".risk_level",
				Message:  fmt.Sprintf("Invalid risk level '%s'. Must be one of: %v", step.RiskLevel, validRiskLevels),
				Severity: "medium",
				Code:     "INVALID_RISK_LEVEL",
			})
		}

		// Check if high-risk steps require human approval
		if (step.RiskLevel == "high" || step.RiskLevel == "critical") && step.Automated && !step.RequiresHuman {
			result.Warnings = append(result.Warnings, ValidationWarning{
				Field:      stepField + ".requires_human",
				Message:    "High-risk automated steps should require human approval",
				Suggestion: "Set requires_human to true for safety",
			})
		}

		// Validate dependencies
		v.validateStepDependencies(step, i, playbook.Steps, stepField, result)
	}
}

// validateCommand checks for dangerous or prohibited commands
func (v *PlaybookValidatorImpl) validateCommand(command, stepField string, result *ValidationResult) {
	// Check prohibited commands
	for _, prohibited := range v.config.ProhibitedCommands {
		if strings.Contains(strings.ToLower(command), strings.ToLower(prohibited)) {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".command",
				Message:  fmt.Sprintf("Command contains prohibited pattern: %s", prohibited),
				Severity: "critical",
				Code:     "PROHIBITED_COMMAND",
			})
		}
	}

	// Check dangerous patterns using regex
	for _, pattern := range v.config.DangerousPatterns {
		matched, err := regexp.MatchString(pattern, command)
		if err != nil {
			continue // Skip invalid regex
		}
		if matched {
			result.Warnings = append(result.Warnings, ValidationWarning{
				Field:      stepField + ".command",
				Message:    fmt.Sprintf("Command matches dangerous pattern: %s", pattern),
				Suggestion: "Review command for safety and add appropriate safety checks",
			})
		}
	}

	// Check for missing command safeguards
	if strings.Contains(command, "rm ") && !strings.Contains(command, "-i") {
		result.Warnings = append(result.Warnings, ValidationWarning{
			Field:      stepField + ".command",
			Message:    "Destructive command without interactive flag",
			Suggestion: "Consider adding -i flag or implementing safety checks",
		})
	}
}

// validateStepDependencies validates step dependencies
func (v *PlaybookValidatorImpl) validateStepDependencies(step PlaybookStep, stepIndex int, allSteps []PlaybookStep, stepField string, result *ValidationResult) {
	stepIDs := make(map[string]bool)
	for _, s := range allSteps {
		stepIDs[s.StepID] = true
	}

	for _, depID := range step.DependsOn {
		if !stepIDs[depID] {
			result.Errors = append(result.Errors, ValidationError{
				Field:    stepField + ".depends_on",
				Message:  fmt.Sprintf("Dependency step '%s' not found", depID),
				Severity: "high",
				Code:     "INVALID_DEPENDENCY",
			})
		}
	}
}

// validateValidationSteps validates validation procedures
func (v *PlaybookValidatorImpl) validateValidationSteps(playbook *Playbook, result *ValidationResult) {
	for i, validation := range playbook.ValidationSteps {
		validationField := fmt.Sprintf("validation_steps[%d]", i)

		if validation.Title == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    validationField + ".title",
				Message:  "Validation title is required",
				Severity: "medium",
				Code:     "MISSING_VALIDATION_TITLE",
			})
		}

		if validation.CheckType == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    validationField + ".check_type",
				Message:  "Validation check type is required",
				Severity: "medium",
				Code:     "MISSING_CHECK_TYPE",
			})
		} else {
			validTypes := []string{"health", "performance", "security", "functional"}
			if !v.contains(validTypes, validation.CheckType) {
				result.Errors = append(result.Errors, ValidationError{
					Field:    validationField + ".check_type",
					Message:  fmt.Sprintf("Invalid check type '%s'. Must be one of: %v", validation.CheckType, validTypes),
					Severity: "medium",
					Code:     "INVALID_CHECK_TYPE",
				})
			}
		}

		if validation.Command == "" && validation.Automated {
			result.Errors = append(result.Errors, ValidationError{
				Field:    validationField + ".command",
				Message:  "Command is required for automated validations",
				Severity: "high",
				Code:     "MISSING_VALIDATION_COMMAND",
			})
		}
	}
}

// validateRollbackSteps validates rollback procedures
func (v *PlaybookValidatorImpl) validateRollbackSteps(playbook *Playbook, result *ValidationResult) {
	for i, rollback := range playbook.RollbackSteps {
		rollbackField := fmt.Sprintf("rollback_steps[%d]", i)

		if rollback.Title == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    rollbackField + ".title",
				Message:  "Rollback title is required",
				Severity: "medium",
				Code:     "MISSING_ROLLBACK_TITLE",
			})
		}

		if rollback.Command == "" {
			result.Errors = append(result.Errors, ValidationError{
				Field:    rollbackField + ".command",
				Message:  "Rollback command is required",
				Severity: "high",
				Code:     "MISSING_ROLLBACK_COMMAND",
			})
		} else {
			// Validate rollback command safety
			v.validateCommand(rollback.Command, rollbackField, result)
		}
	}
}

// validateSafety performs safety validation
func (v *PlaybookValidatorImpl) validateSafety(playbook *Playbook, result *ValidationResult) {
	// Check for high-risk operations without safety measures
	highRiskSteps := 0
	for _, step := range playbook.Steps {
		if step.RiskLevel == "high" || step.RiskLevel == "critical" {
			highRiskSteps++

			// High-risk steps should have safety checks
			if len(step.SafetyChecks) == 0 {
				result.Warnings = append(result.Warnings, ValidationWarning{
					Field:      fmt.Sprintf("steps[%d].safety_checks", step.StepNumber-1),
					Message:    "High-risk step lacks safety checks",
					Suggestion: "Add safety checks to verify system state before execution",
				})
			}
		}
	}

	// Warn about playbooks with many high-risk steps
	if highRiskSteps > len(playbook.Steps)/2 {
		result.Warnings = append(result.Warnings, ValidationWarning{
			Field:      "steps",
			Message:    fmt.Sprintf("Playbook has %d high-risk steps out of %d total", highRiskSteps, len(playbook.Steps)),
			Suggestion: "Consider breaking into smaller, safer playbooks",
		})
	}

	// Check for missing safety warnings
	if highRiskSteps > 0 && len(playbook.SafetyWarnings) == 0 {
		result.Warnings = append(result.Warnings, ValidationWarning{
			Field:      "safety_warnings",
			Message:    "High-risk playbook lacks safety warnings",
			Suggestion: "Add safety warnings to alert operators",
		})
	}
}

// ValidateStep validates a single step
func (v *PlaybookValidatorImpl) ValidateStep(ctx context.Context, step *PlaybookStep) (*StepValidationResult, error) {
	result := &StepValidationResult{
		Valid:       true,
		Errors:      []string{},
		Warnings:    []string{},
		SafetyScore: 1.0,
		RiskLevel:   "low",
	}

	// Basic validation
	if step.Title == "" {
		result.Errors = append(result.Errors, "Step title is required")
		result.Valid = false
	}

	if step.ActionType == "" {
		result.Errors = append(result.Errors, "Action type is required")
		result.Valid = false
	}

	// Command validation
	if step.ActionType == "command" && step.Command != "" {
		v.validateStepCommand(step.Command, result)
	}

	// Risk assessment
	result.RiskLevel = v.assessStepRisk(step)
	result.SafetyScore = v.calculateStepSafetyScore(step)

	return result, nil
}

// validateStepCommand validates a step command
func (v *PlaybookValidatorImpl) validateStepCommand(command string, result *StepValidationResult) {
	// Check prohibited commands
	for _, prohibited := range v.config.ProhibitedCommands {
		if strings.Contains(strings.ToLower(command), strings.ToLower(prohibited)) {
			result.Errors = append(result.Errors, fmt.Sprintf("Command contains prohibited pattern: %s", prohibited))
			result.Valid = false
			result.SafetyScore = 0.0
			result.RiskLevel = "critical"
		}
	}

	// Check dangerous patterns
	for _, pattern := range v.config.DangerousPatterns {
		matched, _ := regexp.MatchString(pattern, command)
		if matched {
			result.Warnings = append(result.Warnings, fmt.Sprintf("Command matches dangerous pattern: %s", pattern))
			result.SafetyScore *= 0.7
		}
	}
}

// assessStepRisk assesses the risk level of a step
func (v *PlaybookValidatorImpl) assessStepRisk(step *PlaybookStep) string {
	if step.RiskLevel != "" {
		return step.RiskLevel
	}

	// Auto-assess based on command patterns
	if step.ActionType == "command" && step.Command != "" {
		command := strings.ToLower(step.Command)

		// Critical risk patterns
		criticalPatterns := []string{"rm ", "del ", "format", "mkfs", "fdisk", "shutdown", "reboot"}
		for _, pattern := range criticalPatterns {
			if strings.Contains(command, pattern) {
				return "critical"
			}
		}

		// High risk patterns
		highPatterns := []string{"sudo", "systemctl restart", "service restart", "kill -9"}
		for _, pattern := range highPatterns {
			if strings.Contains(command, pattern) {
				return "high"
			}
		}

		// Medium risk patterns
		mediumPatterns := []string{"chmod", "chown", "systemctl", "service"}
		for _, pattern := range mediumPatterns {
			if strings.Contains(command, pattern) {
				return "medium"
			}
		}
	}

	return "low"
}

// calculateStepSafetyScore calculates safety score for a step
func (v *PlaybookValidatorImpl) calculateStepSafetyScore(step *PlaybookStep) float64 {
	score := 1.0

	// Reduce score for high risk
	switch step.RiskLevel {
	case "critical":
		score *= 0.3
	case "high":
		score *= 0.5
	case "medium":
		score *= 0.7
	}

	// Increase score for safety measures
	if len(step.SafetyChecks) > 0 {
		score += 0.1
	}

	if step.RequiresHuman && (step.RiskLevel == "high" || step.RiskLevel == "critical") {
		score += 0.1
	}

	if step.RollbackCommand != "" {
		score += 0.05
	}

	// Ensure score is within bounds
	if score > 1.0 {
		score = 1.0
	}
	if score < 0.0 {
		score = 0.0
	}

	return score
}

// CheckSafety performs comprehensive safety check
func (v *PlaybookValidatorImpl) CheckSafety(ctx context.Context, playbook *Playbook) (*SafetyResult, error) {
	result := &SafetyResult{
		Safe:        true,
		RiskLevel:   "low",
		Warnings:    []SafetyWarning{},
		Mitigations: []string{},
		Prohibited:  []string{},
	}

	overallRisk := "low"
	prohibitedFound := false

	// Check each step for safety issues
	for i, step := range playbook.Steps {
		stepResult, err := v.ValidateStep(ctx, &step)
		if err != nil {
			continue
		}

		// Track highest risk level
		if v.isHigherRisk(stepResult.RiskLevel, overallRisk) {
			overallRisk = stepResult.RiskLevel
		}

		// Collect prohibited operations
		for _, error := range stepResult.Errors {
			if strings.Contains(error, "prohibited") {
				result.Prohibited = append(result.Prohibited, fmt.Sprintf("Step %d: %s", i+1, error))
				prohibitedFound = true
			}
		}

		// Convert step warnings to safety warnings
		for _, warning := range stepResult.Warnings {
			result.Warnings = append(result.Warnings, SafetyWarning{
				WarningID:  fmt.Sprintf("step-%d-warning", i+1),
				Level:      "warning",
				Message:    warning,
				Mitigation: "Review and add appropriate safety measures",
			})
		}
	}

	result.RiskLevel = overallRisk
	result.Safe = !prohibitedFound && overallRisk != "critical"

	// Add general mitigations based on risk level
	switch overallRisk {
	case "critical":
		result.Mitigations = append(result.Mitigations, "Require senior engineer approval before execution")
		result.Mitigations = append(result.Mitigations, "Execute during maintenance window only")
		result.Mitigations = append(result.Mitigations, "Ensure full system backup before execution")
	case "high":
		result.Mitigations = append(result.Mitigations, "Require team lead approval")
		result.Mitigations = append(result.Mitigations, "Test in staging environment first")
		result.Mitigations = append(result.Mitigations, "Have rollback plan ready")
	case "medium":
		result.Mitigations = append(result.Mitigations, "Notify team before execution")
		result.Mitigations = append(result.Mitigations, "Monitor system during execution")
	}

	return result, nil
}

// calculateScore calculates overall playbook quality score
func (v *PlaybookValidatorImpl) calculateScore(playbook *Playbook, result *ValidationResult) float64 {
	// Start with base score
	score := 1.0

	// Penalize for errors
	for _, error := range result.Errors {
		switch error.Severity {
		case "critical":
			score -= 0.3
		case "high":
			score -= 0.2
		case "medium":
			score -= 0.1
		default:
			score -= 0.05
		}
	}

	// Small penalty for warnings
	score -= float64(len(result.Warnings)) * 0.02

	// Completeness score
	completenessScore := v.calculateCompletenessScore(playbook)

	// Safety score
	safetyScore := v.calculateSafetyScore(playbook)

	// Clarity score
	clarityScore := v.calculateClarityScore(playbook)

	// Executability score
	executabilityScore := v.calculateExecutabilityScore(playbook)

	// Weighted average
	finalScore := (completenessScore * v.config.CompletenessWeight) +
		(safetyScore * v.config.SafetyWeight) +
		(clarityScore * v.config.ClarityWeight) +
		(executabilityScore * v.config.ExecutabilityWeight)

	// Apply error penalties
	finalScore = math.Min(finalScore, score)

	// Ensure score is within bounds
	if finalScore > 1.0 {
		finalScore = 1.0
	}
	if finalScore < 0.0 {
		finalScore = 0.0
	}

	return finalScore
}

// Helper scoring functions
func (v *PlaybookValidatorImpl) calculateCompletenessScore(playbook *Playbook) float64 {
	score := 0.0
	total := 8.0 // Total aspects to check

	if playbook.Title != "" {
		score += 1.0
	}
	if playbook.Description != "" {
		score += 1.0
	}
	if len(playbook.Steps) > 0 {
		score += 1.0
	}
	if len(playbook.ValidationSteps) > 0 {
		score += 1.0
	}
	if len(playbook.RollbackSteps) > 0 {
		score += 1.0
	}
	if len(playbook.Prerequisites) > 0 {
		score += 1.0
	}
	if len(playbook.RequiredSkills) > 0 {
		score += 1.0
	}
	if playbook.EstimatedDuration > 0 {
		score += 1.0
	}

	return score / total
}

func (v *PlaybookValidatorImpl) calculateSafetyScore(playbook *Playbook) float64 {
	if len(playbook.Steps) == 0 {
		return 0.0
	}

	totalSteps := float64(len(playbook.Steps))
	safeSteps := 0.0

	for _, step := range playbook.Steps {
		stepSafety := 1.0

		// Penalize high-risk steps
		switch step.RiskLevel {
		case "critical":
			stepSafety = 0.2
		case "high":
			stepSafety = 0.4
		case "medium":
			stepSafety = 0.7
		}

		// Bonus for safety measures
		if len(step.SafetyChecks) > 0 {
			stepSafety += 0.1
		}
		if step.RequiresHuman && (step.RiskLevel == "high" || step.RiskLevel == "critical") {
			stepSafety += 0.1
		}

		safeSteps += math.Min(stepSafety, 1.0)
	}

	return safeSteps / totalSteps
}

func (v *PlaybookValidatorImpl) calculateClarityScore(playbook *Playbook) float64 {
	if len(playbook.Steps) == 0 {
		return 0.0
	}

	totalSteps := float64(len(playbook.Steps))
	clearSteps := 0.0

	for _, step := range playbook.Steps {
		stepClarity := 0.0

		if step.Title != "" {
			stepClarity += 0.3
		}
		if step.Description != "" {
			stepClarity += 0.3
		}
		if step.ExpectedOutput != "" {
			stepClarity += 0.2
		}
		if step.Command != "" || step.ActionType == "manual" {
			stepClarity += 0.2
		}

		clearSteps += stepClarity
	}

	return clearSteps / totalSteps
}

func (v *PlaybookValidatorImpl) calculateExecutabilityScore(playbook *Playbook) float64 {
	if len(playbook.Steps) == 0 {
		return 0.0
	}

	totalSteps := float64(len(playbook.Steps))
	executableSteps := 0.0

	for _, step := range playbook.Steps {
		stepExecutability := 0.0

		// Has executable command or clear manual instructions
		if step.Command != "" || step.ActionType == "manual" {
			stepExecutability += 0.4
		}

		// Has timeout specified
		if step.Timeout > 0 {
			stepExecutability += 0.2
		}

		// Has retry count
		if step.RetryCount > 0 {
			stepExecutability += 0.2
		}

		// Has expected output
		if step.ExpectedOutput != "" {
			stepExecutability += 0.2
		}

		executableSteps += stepExecutability
	}

	return executableSteps / totalSteps
}

// Helper functions
func (v *PlaybookValidatorImpl) contains(slice []string, item string) bool {
	for _, s := range slice {
		if s == item {
			return true
		}
	}
	return false
}

func (v *PlaybookValidatorImpl) isHigherRisk(risk1, risk2 string) bool {
	riskLevels := map[string]int{
		"low":      1,
		"medium":   2,
		"high":     3,
		"critical": 4,
	}

	return riskLevels[risk1] > riskLevels[risk2]
}

// DefaultValidationConfig returns default validation configuration
func DefaultValidationConfig() ValidationConfig {
	return ValidationConfig{
		ProhibitedCommands: []string{
			"rm -rf /", "rm -rf /*", ":(){ :|:& };:", "chmod -R 777 /",
			"dd if=/dev/zero of=/dev/sda", "mkfs", "fdisk", "parted",
			"shutdown -h now", "reboot", "halt", "poweroff",
			"userdel", "passwd", "chpasswd", "sudo su -",
		},
		DangerousPatterns: []string{
			`rm\s+-r.*\/`, `chmod\s+777`, `chown\s+.*:.*\/`,
			`DROP\s+DATABASE`, `TRUNCATE\s+TABLE`, `DELETE\s+FROM.*WHERE.*1=1`,
			`sudo\s+.*`, `su\s+-`, `passwd\s+`,
		},
		RequiredSafetyChecks: []string{"backup_verified", "rollback_ready"},
		MaxRiskLevel:         "high",
		MinStepsRequired:     1,
		MaxStepsAllowed:      50,
		RequireValidation:    true,
		RequireRollback:      false,
		RequiredFields:       []string{"title", "description", "severity"},
		AllowedActionTypes:   []string{"command", "api_call", "manual", "script"},
		MaxTimeout:           10 * time.Minute,
		CompletenessWeight:   0.3,
		SafetyWeight:         0.4,
		ClarityWeight:        0.2,
		ExecutabilityWeight:  0.1,
	}
}
