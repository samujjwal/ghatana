package playbook

import (
	"context"
	"fmt"
	"regexp"
	"strings"
	"text/template"
	"time"

	"go.uber.org/zap"
)

// PlaybookTemplateEngine manages playbook templates and generation patterns
type PlaybookTemplateEngine struct {
	logger    *zap.Logger
	templates map[string]*PlaybookTemplate
	registry  *TemplateRegistry
}

// TemplateContext provides context for template rendering
type TemplateContext struct {
	Incident  *IncidentContext       `json:"incident"`
	Variables map[string]interface{} `json:"variables"`
	Timestamp time.Time              `json:"timestamp"`
	TenantID  string                 `json:"tenant_id"`
}

// StepTemplate defines a template for playbook steps
type StepTemplate struct {
	StepNumber         int                    `json:"step_number"`
	Title              string                 `json:"title"`
	Description        string                 `json:"description"`
	ActionType         string                 `json:"action_type"`
	CommandTemplate    string                 `json:"command_template"`
	ParameterTemplates map[string]interface{} `json:"parameter_templates"`
	ExpectedOutput     string                 `json:"expected_output"`
	Timeout            time.Duration          `json:"timeout"`
	RetryCount         int                    `json:"retry_count"`
	RiskLevel          string                 `json:"risk_level"`
	Automated          bool                   `json:"automated"`
	RequiresHuman      bool                   `json:"requires_human"`
	Dependencies       []string               `json:"dependencies"`
	Conditions         []string               `json:"conditions"`
	SafetyChecks       []string               `json:"safety_checks"`
	ValidationRules    []string               `json:"validation_rules"`
	RollbackCommand    string                 `json:"rollback_command"`
	RollbackNotes      string                 `json:"rollback_notes"`
}

// ValidationTemplate defines validation step templates
type ValidationTemplate struct {
	Title           string        `json:"title"`
	Description     string        `json:"description"`
	CheckType       string        `json:"check_type"`
	CommandTemplate string        `json:"command_template"`
	ExpectedResult  string        `json:"expected_result"`
	Timeout         time.Duration `json:"timeout"`
	Automated       bool          `json:"automated"`
	Critical        bool          `json:"critical"`
	SuccessCriteria []string      `json:"success_criteria"`
	FailureCriteria []string      `json:"failure_criteria"`
}

// RollbackTemplate defines rollback step templates
type RollbackTemplate struct {
	Title              string                 `json:"title"`
	Description        string                 `json:"description"`
	CommandTemplate    string                 `json:"command_template"`
	ParameterTemplates map[string]interface{} `json:"parameter_templates"`
	Timeout            time.Duration          `json:"timeout"`
	TriggerConditions  []string               `json:"trigger_conditions"`
	SafetyChecks       []string               `json:"safety_checks"`
	RiskLevel          string                 `json:"risk_level"`
	RequiresHuman      bool                   `json:"requires_human"`
	Automated          bool                   `json:"automated"`
}

// TemplateVariable defines variables required/optional in templates
type TemplateVariable struct {
	Name         string      `json:"name"`
	Type         string      `json:"type"`
	Description  string      `json:"description"`
	Required     bool        `json:"required"`
	DefaultValue interface{} `json:"default_value"`
	Validation   string      `json:"validation"`
	Examples     []string    `json:"examples"`
}

// TemplateRegistry manages template discovery and matching
type TemplateRegistry struct {
	logger     *zap.Logger
	templates  map[string]*PlaybookTemplate
	categories map[string][]*PlaybookTemplate
	matchers   []TemplateMatcher
}

// TemplateMatcher defines how templates are matched to incidents
type TemplateMatcher struct {
	Name        string
	Priority    int
	MatchFunc   func(incident *IncidentContext) bool
	TemplateIDs []string
}

// IncidentContext provides context for template matching
type IncidentContext struct {
	IncidentID    string                 `json:"incident_id"`
	Title         string                 `json:"title"`
	Description   string                 `json:"description"`
	Severity      string                 `json:"severity"`
	Category      string                 `json:"category"`
	Tags          []string               `json:"tags"`
	Environment   string                 `json:"environment"`
	Component     string                 `json:"component"`
	Metrics       map[string]interface{} `json:"metrics"`
	Symptoms      []string               `json:"symptoms"`
	ErrorMessages []string               `json:"error_messages"`

	// Historical context
	SimilarIncidents []string `json:"similar_incidents"`
	PreviousActions  []string `json:"previous_actions"`

	// System context
	TenantID  string    `json:"tenant_id"`
	CreatedAt time.Time `json:"created_at"`
}

// NewPlaybookTemplateEngine creates a new template engine
func NewPlaybookTemplateEngine(logger *zap.Logger) *PlaybookTemplateEngine {
	engine := &PlaybookTemplateEngine{
		logger:    logger,
		templates: make(map[string]*PlaybookTemplate),
		registry:  NewTemplateRegistry(logger),
	}

	// Load built-in templates
	engine.loadBuiltInTemplates()

	return engine
}

// NewTemplateRegistry creates a new template registry
func NewTemplateRegistry(logger *zap.Logger) *TemplateRegistry {
	registry := &TemplateRegistry{
		logger:     logger,
		templates:  make(map[string]*PlaybookTemplate),
		categories: make(map[string][]*PlaybookTemplate),
		matchers:   []TemplateMatcher{},
	}

	// Initialize built-in matchers
	registry.initializeMatchers()

	return registry
}

// loadBuiltInTemplates loads default templates
func (e *PlaybookTemplateEngine) loadBuiltInTemplates() {
	templates := []*PlaybookTemplate{
		e.createServiceRestartTemplate(),
		e.createDatabaseConnectionTemplate(),
		e.createHighCPUTemplate(),
		e.createMemoryLeakTemplate(),
		e.createNetworkConnectivityTemplate(),
		e.createDiskSpaceTemplate(),
		e.createApplicationErrorTemplate(),
		e.createLoadBalancerTemplate(),
		e.createCacheInvalidationTemplate(),
		e.createSecurityIncidentTemplate(),
	}

	for _, tmpl := range templates {
		e.RegisterTemplate(tmpl)
	}

	e.logger.Info("Loaded built-in templates", zap.Int("count", len(templates)))
}

// RegisterTemplate registers a new template
func (e *PlaybookTemplateEngine) RegisterTemplate(template *PlaybookTemplate) error {
	if template.TemplateID == "" {
		return fmt.Errorf("template ID is required")
	}

	// Validate template
	if err := e.validateTemplate(template); err != nil {
		return fmt.Errorf("template validation failed: %w", err)
	}

	e.templates[template.TemplateID] = template
	e.registry.RegisterTemplate(template)

	e.logger.Info("Registered template",
		zap.String("template_id", template.TemplateID),
		zap.String("name", template.Name),
		zap.String("category", template.Category))

	return nil
}

// FindMatchingTemplates finds templates that match the incident context
func (e *PlaybookTemplateEngine) FindMatchingTemplates(ctx context.Context, incident *IncidentContext) ([]*PlaybookTemplate, error) {
	e.logger.Debug("Finding matching templates",
		zap.String("incident_id", incident.IncidentID),
		zap.String("category", incident.Category),
		zap.String("severity", incident.Severity))

	matches := e.registry.FindMatches(incident)

	// Sort by priority and success rate
	sortedMatches := e.sortTemplatesByRelevance(matches, incident)

	e.logger.Info("Found matching templates",
		zap.String("incident_id", incident.IncidentID),
		zap.Int("match_count", len(sortedMatches)))

	return sortedMatches, nil
}

// GenerateFromTemplate generates a playbook from a template
func (e *PlaybookTemplateEngine) GenerateFromTemplate(ctx context.Context, templateID string, incident *IncidentContext, variables map[string]interface{}) (*Playbook, error) {
	template, exists := e.templates[templateID]
	if !exists {
		return nil, fmt.Errorf("template not found: %s", templateID)
	}

	e.logger.Debug("Generating playbook from template",
		zap.String("template_id", templateID),
		zap.String("incident_id", incident.IncidentID))

	// Validate required variables
	if err := e.validateVariables(template, variables); err != nil {
		return nil, fmt.Errorf("variable validation failed: %w", err)
	}

	// Merge default parameters
	allVariables := e.mergeVariables(template.DefaultParameters, variables)
	allVariables["incident"] = incident
	allVariables["timestamp"] = time.Now()

	// Generate playbook
	playbook := &Playbook{
		PlaybookID:       generatePlaybookID(),
		IncidentID:       incident.IncidentID,
		GeneratedAt:      time.Now(),
		GeneratedBy:      "template-engine",
		ModelVersion:     "template-v1.0",
		ConfidenceScore:  0.9, // Templates have high confidence
		Status:           PlaybookStatusDraft,
		ApprovalRequired: template.RequiresApproval,
		TenantID:         incident.TenantID,
		Environment:      incident.Environment,
		CreatedAt:        time.Now(),
		UpdatedAt:        time.Now(),
		Version:          "1.0",
		Tags:             append(template.Tags, "template-generated"),
	}

	// Generate title and description
	title, err := e.renderTemplate(template.TitleTemplate, allVariables)
	if err != nil {
		return nil, fmt.Errorf("failed to render title: %w", err)
	}
	playbook.Title = title

	description, err := e.renderTemplate(template.DescriptionTemplate, allVariables)
	if err != nil {
		return nil, fmt.Errorf("failed to render description: %w", err)
	}
	playbook.Description = description

	// Set basic properties
	playbook.Severity = incident.Severity
	playbook.Category = template.Category

	// Generate steps
	steps, err := e.generateSteps(template.StepTemplates, allVariables)
	if err != nil {
		return nil, fmt.Errorf("failed to generate steps: %w", err)
	}
	playbook.Steps = steps

	// Generate validation steps
	validations, err := e.generateValidations(template.ValidationTemplates, allVariables)
	if err != nil {
		return nil, fmt.Errorf("failed to generate validations: %w", err)
	}
	playbook.ValidationSteps = validations

	// Generate rollback steps
	rollbacks, err := e.generateRollbacks(template.RollbackTemplates, allVariables)
	if err != nil {
		return nil, fmt.Errorf("failed to generate rollbacks: %w", err)
	}
	playbook.RollbackSteps = rollbacks

	// Add safety warnings from template
	for _, warning := range template.SafetyWarnings {
		playbook.SafetyWarnings = append(playbook.SafetyWarnings, SafetyWarning{
			Level:      "warning",
			Message:    warning,
			Mitigation: "Review carefully before execution",
		})
	}

	// Update template usage
	template.UsageCount++
	template.LastUsed = &playbook.GeneratedAt

	e.logger.Info("Generated playbook from template",
		zap.String("template_id", templateID),
		zap.String("playbook_id", playbook.PlaybookID),
		zap.Int("step_count", len(steps)),
		zap.Int("validation_count", len(validations)))

	return playbook, nil
}

// generateSteps generates playbook steps from templates
func (e *PlaybookTemplateEngine) generateSteps(stepTemplates []StepTemplate, variables map[string]interface{}) ([]PlaybookStep, error) {
	var steps []PlaybookStep

	for _, stepTemplate := range stepTemplates {
		step := PlaybookStep{
			StepID:          generateStepID(),
			StepNumber:      stepTemplate.StepNumber,
			ActionType:      stepTemplate.ActionType,
			Timeout:         stepTemplate.Timeout,
			RetryCount:      stepTemplate.RetryCount,
			RiskLevel:       stepTemplate.RiskLevel,
			Automated:       stepTemplate.Automated,
			RequiresHuman:   stepTemplate.RequiresHuman,
			DependsOn:       stepTemplate.Dependencies,
			Conditions:      stepTemplate.Conditions,
			SafetyChecks:    stepTemplate.SafetyChecks,
			ValidationRules: stepTemplate.ValidationRules,
		}

		// Render templates
		title, err := e.renderTemplate(stepTemplate.Title, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render step title: %w", err)
		}
		step.Title = title

		description, err := e.renderTemplate(stepTemplate.Description, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render step description: %w", err)
		}
		step.Description = description

		command, err := e.renderTemplate(stepTemplate.CommandTemplate, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render step command: %w", err)
		}
		step.Command = command

		expectedOutput, err := e.renderTemplate(stepTemplate.ExpectedOutput, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render expected output: %w", err)
		}
		step.ExpectedOutput = expectedOutput

		// Render rollback command if present
		if stepTemplate.RollbackCommand != "" {
			rollbackCmd, err := e.renderTemplate(stepTemplate.RollbackCommand, variables)
			if err != nil {
				return nil, fmt.Errorf("failed to render rollback command: %w", err)
			}
			step.RollbackCommand = rollbackCmd
		}

		if stepTemplate.RollbackNotes != "" {
			rollbackNotes, err := e.renderTemplate(stepTemplate.RollbackNotes, variables)
			if err != nil {
				return nil, fmt.Errorf("failed to render rollback notes: %w", err)
			}
			step.RollbackNotes = rollbackNotes
		}

		// Render parameters
		renderedParams := make(map[string]interface{})
		for key, value := range stepTemplate.ParameterTemplates {
			if strValue, ok := value.(string); ok {
				rendered, err := e.renderTemplate(strValue, variables)
				if err != nil {
					return nil, fmt.Errorf("failed to render parameter %s: %w", key, err)
				}
				renderedParams[key] = rendered
			} else {
				renderedParams[key] = value
			}
		}
		step.Parameters = renderedParams

		steps = append(steps, step)
	}

	return steps, nil
}

// generateValidations generates validation steps from templates
func (e *PlaybookTemplateEngine) generateValidations(validationTemplates []ValidationTemplate, variables map[string]interface{}) ([]ValidationStep, error) {
	var validations []ValidationStep

	for _, validationTemplate := range validationTemplates {
		validation := ValidationStep{
			ValidationID:    generateValidationID(),
			CheckType:       validationTemplate.CheckType,
			Timeout:         validationTemplate.Timeout,
			Automated:       validationTemplate.Automated,
			Critical:        validationTemplate.Critical,
			SuccessCriteria: validationTemplate.SuccessCriteria,
			FailureCriteria: validationTemplate.FailureCriteria,
		}

		// Render templates
		title, err := e.renderTemplate(validationTemplate.Title, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render validation title: %w", err)
		}
		validation.Title = title

		description, err := e.renderTemplate(validationTemplate.Description, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render validation description: %w", err)
		}
		validation.Description = description

		command, err := e.renderTemplate(validationTemplate.CommandTemplate, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render validation command: %w", err)
		}
		validation.Command = command

		expectedResult, err := e.renderTemplate(validationTemplate.ExpectedResult, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render expected result: %w", err)
		}
		validation.ExpectedResult = expectedResult

		validations = append(validations, validation)
	}

	return validations, nil
}

// generateRollbacks generates rollback steps from templates
func (e *PlaybookTemplateEngine) generateRollbacks(rollbackTemplates []RollbackTemplate, variables map[string]interface{}) ([]RollbackStep, error) {
	var rollbacks []RollbackStep

	for i, rollbackTemplate := range rollbackTemplates {
		rollback := RollbackStep{
			RollbackID:        generateRollbackID(),
			StepNumber:        i + 1,
			Timeout:           rollbackTemplate.Timeout,
			TriggerConditions: rollbackTemplate.TriggerConditions,
			SafetyChecks:      rollbackTemplate.SafetyChecks,
			RiskLevel:         rollbackTemplate.RiskLevel,
			RequiresHuman:     rollbackTemplate.RequiresHuman,
			Automated:         rollbackTemplate.Automated,
		}

		// Render templates
		title, err := e.renderTemplate(rollbackTemplate.Title, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render rollback title: %w", err)
		}
		rollback.Title = title

		description, err := e.renderTemplate(rollbackTemplate.Description, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render rollback description: %w", err)
		}
		rollback.Description = description

		command, err := e.renderTemplate(rollbackTemplate.CommandTemplate, variables)
		if err != nil {
			return nil, fmt.Errorf("failed to render rollback command: %w", err)
		}
		rollback.Command = command

		// Render parameters
		renderedParams := make(map[string]interface{})
		for key, value := range rollbackTemplate.ParameterTemplates {
			if strValue, ok := value.(string); ok {
				rendered, err := e.renderTemplate(strValue, variables)
				if err != nil {
					return nil, fmt.Errorf("failed to render rollback parameter %s: %w", key, err)
				}
				renderedParams[key] = rendered
			} else {
				renderedParams[key] = value
			}
		}
		rollback.Parameters = renderedParams

		rollbacks = append(rollbacks, rollback)
	}

	return rollbacks, nil
}

// renderTemplate renders a template string with variables
func (e *PlaybookTemplateEngine) renderTemplate(templateStr string, variables map[string]interface{}) (string, error) {
	if templateStr == "" {
		return "", nil
	}

	tmpl, err := template.New("playbook").Parse(templateStr)
	if err != nil {
		return "", err
	}

	var result strings.Builder
	err = tmpl.Execute(&result, variables)
	if err != nil {
		return "", err
	}

	return result.String(), nil
}

// validateTemplate validates a template structure
func (e *PlaybookTemplateEngine) validateTemplate(template *PlaybookTemplate) error {
	if template.Name == "" {
		return fmt.Errorf("template name is required")
	}

	if template.Category == "" {
		return fmt.Errorf("template category is required")
	}

	if len(template.StepTemplates) == 0 {
		return fmt.Errorf("template must have at least one step")
	}

	// Validate step templates
	for i, step := range template.StepTemplates {
		if step.Title == "" {
			return fmt.Errorf("step %d: title is required", i)
		}
		if step.CommandTemplate == "" {
			return fmt.Errorf("step %d: command template is required", i)
		}
	}

	return nil
}

// validateVariables validates that required variables are provided
func (e *PlaybookTemplateEngine) validateVariables(template *PlaybookTemplate, variables map[string]interface{}) error {
	for _, variable := range template.RequiredVariables {
		if variable.Required {
			if _, exists := variables[variable.Name]; !exists {
				return fmt.Errorf("required variable missing: %s", variable.Name)
			}

			// Validate variable format if validation pattern is provided
			if variable.Validation != "" {
				value, ok := variables[variable.Name].(string)
				if !ok {
					return fmt.Errorf("variable %s must be a string", variable.Name)
				}

				matched, err := regexp.MatchString(variable.Validation, value)
				if err != nil {
					return fmt.Errorf("invalid validation pattern for %s: %w", variable.Name, err)
				}
				if !matched {
					return fmt.Errorf("variable %s does not match required pattern: %s", variable.Name, variable.Validation)
				}
			}
		}
	}

	return nil
}

// mergeVariables merges default parameters with provided variables
func (e *PlaybookTemplateEngine) mergeVariables(defaults map[string]interface{}, variables map[string]interface{}) map[string]interface{} {
	merged := make(map[string]interface{})

	// Start with defaults
	for key, value := range defaults {
		merged[key] = value
	}

	// Override with provided variables
	for key, value := range variables {
		merged[key] = value
	}

	return merged
}

// sortTemplatesByRelevance sorts templates by relevance to the incident
func (e *PlaybookTemplateEngine) sortTemplatesByRelevance(templates []*PlaybookTemplate, incident *IncidentContext) []*PlaybookTemplate {
	// Simple scoring based on category match, severity, and success rate
	// In a real implementation, this could use ML-based scoring

	return templates // For now, return as-is
}

// Placeholder ID generation functions
func generatePlaybookID() string {
	return fmt.Sprintf("pb-%d", time.Now().UnixNano())
}

func generateStepID() string {
	return fmt.Sprintf("step-%d", time.Now().UnixNano())
}

func generateValidationID() string {
	return fmt.Sprintf("val-%d", time.Now().UnixNano())
}

func generateRollbackID() string {
	return fmt.Sprintf("rb-%d", time.Now().UnixNano())
}
