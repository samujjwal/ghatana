package playbook

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"go.uber.org/zap"
)

// PlaybookGenerator generates automated remediation playbooks from incident summaries
type PlaybookGenerator struct {
	logger    *zap.Logger
	llmClient LLMClient
	storage   PlaybookStorage
	templates TemplateEngine
	validator PlaybookValidator
	config    PlaybookConfig
}

// PlaybookConfig configures playbook generation behavior
type PlaybookConfig struct {
	// LLM Configuration
	Model           string        `json:"model" yaml:"model"`
	Temperature     float64       `json:"temperature" yaml:"temperature"`
	MaxTokens       int           `json:"max_tokens" yaml:"max_tokens"`
	TimeoutDuration time.Duration `json:"timeout_duration" yaml:"timeout_duration"`

	// Generation Settings
	MaxStepsPerPlaybook int    `json:"max_steps_per_playbook" yaml:"max_steps_per_playbook"`
	MinStepsPerPlaybook int    `json:"min_steps_per_playbook" yaml:"min_steps_per_playbook"`
	RequireValidation   bool   `json:"require_validation" yaml:"require_validation"`
	RequireRollback     bool   `json:"require_rollback" yaml:"require_rollback"`
	DefaultPriority     string `json:"default_priority" yaml:"default_priority"`

	// Safety and Compliance
	EnableSafetyChecks  bool     `json:"enable_safety_checks" yaml:"enable_safety_checks"`
	ProhibitedActions   []string `json:"prohibited_actions" yaml:"prohibited_actions"`
	RequiredApprovals   []string `json:"required_approvals" yaml:"required_approvals"`
	ComplianceTemplates []string `json:"compliance_templates" yaml:"compliance_templates"`

	// Quality Control
	MinConfidenceScore   float64 `json:"min_confidence_score" yaml:"min_confidence_score"`
	EnablePeerReview     bool    `json:"enable_peer_review" yaml:"enable_peer_review"`
	AutoApproveThreshold float64 `json:"auto_approve_threshold" yaml:"auto_approve_threshold"`

	// Performance
	CacheEnabled  bool          `json:"cache_enabled" yaml:"cache_enabled"`
	CacheTTL      time.Duration `json:"cache_ttl" yaml:"cache_ttl"`
	BatchSize     int           `json:"batch_size" yaml:"batch_size"`
	MaxConcurrent int           `json:"max_concurrent" yaml:"max_concurrent"`
}

// Playbook represents a complete remediation playbook
type Playbook struct {
	PlaybookID  string `json:"playbook_id"`
	IncidentID  string `json:"incident_id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Severity    string `json:"severity"`
	Category    string `json:"category"`

	// Generation metadata
	GeneratedAt     time.Time `json:"generated_at"`
	GeneratedBy     string    `json:"generated_by"`
	ModelVersion    string    `json:"model_version"`
	ConfidenceScore float64   `json:"confidence_score"`

	// Execution details
	EstimatedDuration time.Duration `json:"estimated_duration"`
	RequiredSkills    []string      `json:"required_skills"`
	Prerequisites     []string      `json:"prerequisites"`

	// Steps and procedures
	Steps           []PlaybookStep   `json:"steps"`
	ValidationSteps []ValidationStep `json:"validation_steps"`
	RollbackSteps   []RollbackStep   `json:"rollback_steps"`

	// Safety and compliance
	SafetyWarnings   []SafetyWarning `json:"safety_warnings"`
	ComplianceNotes  []string        `json:"compliance_notes"`
	ApprovalRequired bool            `json:"approval_required"`
	ApprovedBy       string          `json:"approved_by,omitempty"`
	ApprovedAt       *time.Time      `json:"approved_at,omitempty"`

	// Execution tracking
	Status           PlaybookStatus    `json:"status"`
	ExecutionHistory []ExecutionRecord `json:"execution_history"`
	LastExecuted     *time.Time        `json:"last_executed,omitempty"`
	SuccessRate      float64           `json:"success_rate"`

	// Metadata
	Tags             []string    `json:"tags"`
	RelatedPlaybooks []string    `json:"related_playbooks"`
	References       []Reference `json:"references"`
	Version          string      `json:"version"`

	// Context
	TenantID    string    `json:"tenant_id"`
	Environment string    `json:"environment"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// PlaybookStep represents a single remediation step
type PlaybookStep struct {
	StepID      string `json:"step_id"`
	StepNumber  int    `json:"step_number"`
	Title       string `json:"title"`
	Description string `json:"description"`
	ActionType  string `json:"action_type"` // "command", "api_call", "manual", "script"

	// Execution details
	Command        string                 `json:"command,omitempty"`
	Parameters     map[string]interface{} `json:"parameters,omitempty"`
	ExpectedOutput string                 `json:"expected_output,omitempty"`
	Timeout        time.Duration          `json:"timeout"`
	RetryCount     int                    `json:"retry_count"`

	// Dependencies and conditions
	DependsOn      []string        `json:"depends_on"`
	Conditions     []StepCondition `json:"conditions"`
	SkipConditions []StepCondition `json:"skip_conditions"`

	// Safety and validation
	SafetyChecks    []SafetyCheck    `json:"safety_checks"`
	ValidationRules []ValidationRule `json:"validation_rules"`
	RiskLevel       string           `json:"risk_level"` // "low", "medium", "high", "critical"

	// Automation
	Automated     bool `json:"automated"`
	RequiresHuman bool `json:"requires_human"`

	// Rollback information
	RollbackCommand string `json:"rollback_command,omitempty"`
	RollbackNotes   string `json:"rollback_notes,omitempty"`
}

// ValidationStep represents a validation check
type ValidationStep struct {
	ValidationID string `json:"validation_id"`
	Title        string `json:"title"`
	Description  string `json:"description"`
	CheckType    string `json:"check_type"` // "health", "performance", "security", "functional"

	// Check details
	Command        string        `json:"command,omitempty"`
	ExpectedResult string        `json:"expected_result"`
	Timeout        time.Duration `json:"timeout"`

	// Criteria
	SuccessCriteria []ValidationCriteria `json:"success_criteria"`
	FailureCriteria []ValidationCriteria `json:"failure_criteria"`

	// Execution
	Automated bool `json:"automated"`
	Critical  bool `json:"critical"` // If true, failure blocks execution
}

// RollbackStep represents a rollback procedure
type RollbackStep struct {
	RollbackID  string `json:"rollback_id"`
	StepNumber  int    `json:"step_number"`
	Title       string `json:"title"`
	Description string `json:"description"`

	// Rollback details
	Command    string                 `json:"command"`
	Parameters map[string]interface{} `json:"parameters,omitempty"`
	Timeout    time.Duration          `json:"timeout"`

	// Conditions
	TriggerConditions []RollbackTrigger `json:"trigger_conditions"`
	SafetyChecks      []SafetyCheck     `json:"safety_checks"`

	// Metadata
	RiskLevel     string `json:"risk_level"`
	RequiresHuman bool   `json:"requires_human"`
	Automated     bool   `json:"automated"`
}

// Supporting types for playbook structure

type PlaybookStatus string

const (
	StatusDraft      PlaybookStatus = "draft"
	StatusReview     PlaybookStatus = "review"
	StatusApproved   PlaybookStatus = "approved"
	StatusActive     PlaybookStatus = "active"
	StatusDeprecated PlaybookStatus = "deprecated"
	StatusArchived   PlaybookStatus = "archived"
)

type StepCondition struct {
	Field    string      `json:"field"`
	Operator string      `json:"operator"`
	Value    interface{} `json:"value"`
}

type SafetyCheck struct {
	CheckID     string `json:"check_id"`
	Description string `json:"description"`
	CheckType   string `json:"check_type"`
	Command     string `json:"command,omitempty"`
	Expected    string `json:"expected"`
}

type ValidationRule struct {
	RuleID      string `json:"rule_id"`
	Description string `json:"description"`
	Rule        string `json:"rule"`
	Severity    string `json:"severity"`
}

type ValidationCriteria struct {
	Metric    string      `json:"metric"`
	Operator  string      `json:"operator"`
	Threshold interface{} `json:"threshold"`
}

type RollbackTrigger struct {
	TriggerID   string `json:"trigger_id"`
	Condition   string `json:"condition"`
	Description string `json:"description"`
}

type SafetyWarning struct {
	WarningID  string `json:"warning_id"`
	Level      string `json:"level"`
	Message    string `json:"message"`
	Mitigation string `json:"mitigation"`
}

type ExecutionRecord struct {
	ExecutionID   string        `json:"execution_id"`
	ExecutedBy    string        `json:"executed_by"`
	ExecutedAt    time.Time     `json:"executed_at"`
	Status        string        `json:"status"`
	Duration      time.Duration `json:"duration"`
	StepsExecuted int           `json:"steps_executed"`
	StepsFailed   int           `json:"steps_failed"`
	Notes         string        `json:"notes"`
}

type Reference struct {
	Type        string `json:"type"` // "documentation", "runbook", "kb", "ticket"
	Title       string `json:"title"`
	URL         string `json:"url"`
	Description string `json:"description"`
}

// Request and response types

type GeneratePlaybookRequest struct {
	IncidentSummary string                 `json:"incident_summary"`
	IncidentID      string                 `json:"incident_id"`
	Severity        string                 `json:"severity"`
	Category        string                 `json:"category"`
	AffectedSystems []string               `json:"affected_systems"`
	Context         map[string]interface{} `json:"context"`

	// Generation preferences
	Template        string `json:"template,omitempty"`
	MaxSteps        int    `json:"max_steps,omitempty"`
	IncludeRollback bool   `json:"include_rollback"`
	RequireApproval bool   `json:"require_approval"`
	Priority        string `json:"priority"`

	// Metadata
	RequestedBy string `json:"requested_by"`
	TenantID    string `json:"tenant_id"`
	Environment string `json:"environment"`
}

type GeneratePlaybookResponse struct {
	Playbook        *Playbook     `json:"playbook"`
	GenerationTime  time.Duration `json:"generation_time"`
	ConfidenceScore float64       `json:"confidence_score"`
	Warnings        []string      `json:"warnings"`
	Suggestions     []string      `json:"suggestions"`

	// Quality metrics
	StepCount       int `json:"step_count"`
	ValidationCount int `json:"validation_count"`
	RollbackCount   int `json:"rollback_count"`
	SafetyWarnings  int `json:"safety_warnings"`
}

// Interface definitions

type LLMClient interface {
	GeneratePlaybook(ctx context.Context, prompt string, config PlaybookConfig) (*LLMResponse, error)
	ValidatePlaybook(ctx context.Context, playbook *Playbook) (*ValidationResult, error)
}

type PlaybookStorage interface {
	StorePlaybook(ctx context.Context, playbook *Playbook) error
	GetPlaybook(ctx context.Context, playbookID string) (*Playbook, error)
	UpdatePlaybook(ctx context.Context, playbook *Playbook) error
	ListPlaybooks(ctx context.Context, filters PlaybookFilters) ([]Playbook, error)
	DeletePlaybook(ctx context.Context, playbookID string) error

	// Analytics
	GetPlaybookMetrics(ctx context.Context, timeRange TimeRange) (*PlaybookMetrics, error)
	GetExecutionHistory(ctx context.Context, playbookID string) ([]ExecutionRecord, error)
}

type TemplateEngine interface {
	GetTemplate(category string, severity string) (*PlaybookTemplate, error)
	RenderTemplate(template *PlaybookTemplate, context map[string]interface{}) (string, error)
	ListTemplates() ([]PlaybookTemplate, error)
}

type PlaybookValidator interface {
	ValidatePlaybook(ctx context.Context, playbook *Playbook) (*ValidationResult, error)
	ValidateStep(ctx context.Context, step *PlaybookStep) (*StepValidationResult, error)
	CheckSafety(ctx context.Context, playbook *Playbook) (*SafetyResult, error)
}

// Additional supporting types

type LLMResponse struct {
	Content         string                 `json:"content"`
	ConfidenceScore float64                `json:"confidence_score"`
	TokensUsed      int                    `json:"tokens_used"`
	Model           string                 `json:"model"`
	Metadata        map[string]interface{} `json:"metadata"`
}

type ValidationResult struct {
	Valid       bool                `json:"valid"`
	Errors      []ValidationError   `json:"errors"`
	Warnings    []ValidationWarning `json:"warnings"`
	Score       float64             `json:"score"`
	Suggestions []string            `json:"suggestions"`
}

type ValidationError struct {
	Field    string `json:"field"`
	Message  string `json:"message"`
	Severity string `json:"severity"`
	Code     string `json:"code"`
}

type ValidationWarning struct {
	Field      string `json:"field"`
	Message    string `json:"message"`
	Suggestion string `json:"suggestion"`
}

type StepValidationResult struct {
	Valid       bool     `json:"valid"`
	Errors      []string `json:"errors"`
	Warnings    []string `json:"warnings"`
	SafetyScore float64  `json:"safety_score"`
	RiskLevel   string   `json:"risk_level"`
}

type SafetyResult struct {
	Safe        bool            `json:"safe"`
	RiskLevel   string          `json:"risk_level"`
	Warnings    []SafetyWarning `json:"warnings"`
	Mitigations []string        `json:"mitigations"`
	Prohibited  []string        `json:"prohibited"`
}

type PlaybookTemplate struct {
	TemplateID  string             `json:"template_id"`
	Name        string             `json:"name"`
	Category    string             `json:"category"`
	Severity    string             `json:"severity"`
	Description string             `json:"description"`
	Template    string             `json:"template"`
	Variables   []TemplateVariable `json:"variables"`
	Examples    []string           `json:"examples"`
	CreatedAt   time.Time          `json:"created_at"`
	UpdatedAt   time.Time          `json:"updated_at"`
}

type TemplateVariable struct {
	Name         string      `json:"name"`
	Type         string      `json:"type"`
	Required     bool        `json:"required"`
	DefaultValue interface{} `json:"default_value"`
	Description  string      `json:"description"`
	Examples     []string    `json:"examples"`
}

type PlaybookFilters struct {
	Status        []PlaybookStatus `json:"status"`
	Category      []string         `json:"category"`
	Severity      []string         `json:"severity"`
	TenantID      string           `json:"tenant_id"`
	Environment   string           `json:"environment"`
	Tags          []string         `json:"tags"`
	CreatedAfter  *time.Time       `json:"created_after"`
	CreatedBefore *time.Time       `json:"created_before"`
	Limit         int              `json:"limit"`
	Offset        int              `json:"offset"`
}

type PlaybookMetrics struct {
	TotalPlaybooks   int               `json:"total_playbooks"`
	ActivePlaybooks  int               `json:"active_playbooks"`
	ExecutionCount   int64             `json:"execution_count"`
	SuccessRate      float64           `json:"success_rate"`
	AvgExecutionTime time.Duration     `json:"avg_execution_time"`
	TopCategories    []CategoryMetric  `json:"top_categories"`
	RecentExecutions []ExecutionRecord `json:"recent_executions"`
	TrendData        []TrendPoint      `json:"trend_data"`
}

type CategoryMetric struct {
	Category    string        `json:"category"`
	Count       int           `json:"count"`
	SuccessRate float64       `json:"success_rate"`
	AvgDuration time.Duration `json:"avg_duration"`
}

type TrendPoint struct {
	Timestamp      time.Time `json:"timestamp"`
	ExecutionCount int       `json:"execution_count"`
	SuccessRate    float64   `json:"success_rate"`
}

type TimeRange struct {
	StartTime time.Time `json:"start_time"`
	EndTime   time.Time `json:"end_time"`
}

// NewPlaybookGenerator creates a new playbook generator
func NewPlaybookGenerator(
	logger *zap.Logger,
	llmClient LLMClient,
	storage PlaybookStorage,
	templates TemplateEngine,
	validator PlaybookValidator,
	config PlaybookConfig,
) *PlaybookGenerator {
	// Apply defaults
	if config.Model == "" {
		config.Model = "gpt-4"
	}
	if config.Temperature == 0 {
		config.Temperature = 0.3
	}
	if config.MaxTokens == 0 {
		config.MaxTokens = 2000
	}
	if config.TimeoutDuration == 0 {
		config.TimeoutDuration = 60 * time.Second
	}
	if config.MaxStepsPerPlaybook == 0 {
		config.MaxStepsPerPlaybook = 20
	}
	if config.MinStepsPerPlaybook == 0 {
		config.MinStepsPerPlaybook = 3
	}
	if config.DefaultPriority == "" {
		config.DefaultPriority = "medium"
	}
	if config.MinConfidenceScore == 0 {
		config.MinConfidenceScore = 0.7
	}

	return &PlaybookGenerator{
		logger:    logger,
		llmClient: llmClient,
		storage:   storage,
		templates: templates,
		validator: validator,
		config:    config,
	}
}

// GeneratePlaybook generates a playbook from an incident summary
func (pg *PlaybookGenerator) GeneratePlaybook(ctx context.Context, request *GeneratePlaybookRequest) (*GeneratePlaybookResponse, error) {
	startTime := time.Now()

	pg.logger.Info("Generating playbook",
		zap.String("incident_id", request.IncidentID),
		zap.String("severity", request.Severity),
		zap.String("category", request.Category))

	// Get appropriate template
	template, err := pg.getTemplate(request.Category, request.Severity)
	if err != nil {
		pg.logger.Warn("Failed to get template, using default", zap.Error(err))
		template = pg.getDefaultTemplate()
	}

	// Build generation prompt
	prompt := pg.buildGenerationPrompt(request, template)

	// Generate playbook using LLM
	llmResponse, err := pg.llmClient.GeneratePlaybook(ctx, prompt, pg.config)
	if err != nil {
		return nil, fmt.Errorf("failed to generate playbook: %w", err)
	}

	// Parse LLM response into playbook structure
	playbook, err := pg.parsePlaybookResponse(llmResponse.Content, request)
	if err != nil {
		return nil, fmt.Errorf("failed to parse playbook response: %w", err)
	}

	// Set metadata
	pg.setPlaybookMetadata(playbook, request, llmResponse)

	// Validate generated playbook
	validation, err := pg.validator.ValidatePlaybook(ctx, playbook)
	if err != nil {
		pg.logger.Error("Playbook validation failed", zap.Error(err))
		return nil, fmt.Errorf("playbook validation failed: %w", err)
	}

	if !validation.Valid && pg.config.RequireValidation {
		return nil, fmt.Errorf("generated playbook failed validation: %v", validation.Errors)
	}

	// Safety checks
	safety, err := pg.validator.CheckSafety(ctx, playbook)
	if err != nil {
		pg.logger.Error("Safety check failed", zap.Error(err))
		return nil, fmt.Errorf("safety check failed: %w", err)
	}

	if !safety.Safe && pg.config.EnableSafetyChecks {
		return nil, fmt.Errorf("playbook failed safety checks: %v", safety.Warnings)
	}

	// Apply safety warnings and mitigations
	pg.applySafetyMeasures(playbook, safety)

	// Store playbook
	if err := pg.storage.StorePlaybook(ctx, playbook); err != nil {
		pg.logger.Error("Failed to store playbook", zap.Error(err))
		return nil, fmt.Errorf("failed to store playbook: %w", err)
	}

	// Build response
	response := &GeneratePlaybookResponse{
		Playbook:        playbook,
		GenerationTime:  time.Since(startTime),
		ConfidenceScore: llmResponse.ConfidenceScore,
		Warnings:        validation.Warnings,
		Suggestions:     validation.Suggestions,
		StepCount:       len(playbook.Steps),
		ValidationCount: len(playbook.ValidationSteps),
		RollbackCount:   len(playbook.RollbackSteps),
		SafetyWarnings:  len(playbook.SafetyWarnings),
	}

	pg.logger.Info("Playbook generated successfully",
		zap.String("playbook_id", playbook.PlaybookID),
		zap.Duration("generation_time", response.GenerationTime),
		zap.Float64("confidence_score", response.ConfidenceScore),
		zap.Int("step_count", response.StepCount))

	return response, nil
}

// getTemplate retrieves the appropriate template for the incident
func (pg *PlaybookGenerator) getTemplate(category, severity string) (*PlaybookTemplate, error) {
	template, err := pg.templates.GetTemplate(category, severity)
	if err != nil {
		return nil, err
	}
	return template, nil
}

// getDefaultTemplate returns a basic template for any incident type
func (pg *PlaybookGenerator) getDefaultTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "default",
		Name:        "Generic Incident Response",
		Category:    "general",
		Severity:    "medium",
		Description: "Generic template for incident response",
		Template: `# Incident Response Playbook

## Overview
Incident: {{.incident_summary}}
Severity: {{.severity}}
Affected Systems: {{.affected_systems}}

## Remediation Steps
Generate step-by-step remediation procedures based on the incident summary.

## Validation
Include validation steps to verify the fix was successful.

## Rollback
Provide rollback procedures in case the remediation causes issues.`,
		Variables: []TemplateVariable{
			{Name: "incident_summary", Type: "string", Required: true, Description: "Brief summary of the incident"},
			{Name: "severity", Type: "string", Required: true, Description: "Incident severity level"},
			{Name: "affected_systems", Type: "array", Required: false, Description: "List of affected systems"},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

// buildGenerationPrompt constructs the prompt for LLM generation
func (pg *PlaybookGenerator) buildGenerationPrompt(request *GeneratePlaybookRequest, template *PlaybookTemplate) string {
	context := map[string]interface{}{
		"incident_summary": request.IncidentSummary,
		"incident_id":      request.IncidentID,
		"severity":         request.Severity,
		"category":         request.Category,
		"affected_systems": request.AffectedSystems,
	}

	// Merge additional context
	for k, v := range request.Context {
		context[k] = v
	}

	// Render template with context
	templateContent, err := pg.templates.RenderTemplate(template, context)
	if err != nil {
		pg.logger.Warn("Failed to render template, using default", zap.Error(err))
		templateContent = template.Template
	}

	// Build comprehensive prompt
	prompt := fmt.Sprintf(`You are an expert SRE creating a detailed incident response playbook. Generate a complete, executable playbook in JSON format.

INCIDENT DETAILS:
%s

REQUIREMENTS:
- Generate %d to %d specific, actionable steps
- Include validation steps for each major action
- Provide rollback procedures for risky operations
- Use proper command syntax and parameters
- Include safety checks and warnings
- Estimate realistic timeouts and retry counts
- Categorize risk levels appropriately

RESPONSE FORMAT:
Return a valid JSON object with the following structure:
{
  "title": "Descriptive playbook title",
  "description": "Detailed description of the remediation approach",
  "steps": [
    {
      "step_number": 1,
      "title": "Step title",
      "description": "Detailed description",
      "action_type": "command|api_call|manual|script",
      "command": "actual command to execute",
      "parameters": {},
      "expected_output": "what output indicates success",
      "timeout": "30s",
      "retry_count": 3,
      "risk_level": "low|medium|high|critical",
      "automated": true,
      "requires_human": false,
      "rollback_command": "command to undo this step",
      "safety_checks": [
        {
          "check_id": "unique_id",
          "description": "what to check",
          "command": "verification command",
          "expected": "expected result"
        }
      ]
    }
  ],
  "validation_steps": [
    {
      "title": "Validation title",
      "description": "What to validate",
      "check_type": "health|performance|security|functional",
      "command": "validation command",
      "expected_result": "what indicates success",
      "timeout": "10s",
      "automated": true,
      "critical": true
    }
  ],
  "rollback_steps": [
    {
      "step_number": 1,
      "title": "Rollback step title",
      "description": "How to rollback",
      "command": "rollback command",
      "timeout": "30s",
      "risk_level": "low|medium|high",
      "automated": false
    }
  ],
  "safety_warnings": [
    {
      "level": "warning|critical",
      "message": "Warning message",
      "mitigation": "How to mitigate the risk"
    }
  ],
  "estimated_duration": "45m",
  "required_skills": ["linux", "kubernetes", "database"],
  "prerequisites": ["access to prod cluster", "backup verified"]
}

Generate a comprehensive, production-ready playbook now:`, templateContent, pg.config.MinStepsPerPlaybook, pg.config.MaxStepsPerPlaybook)

	return prompt
}

// parsePlaybookResponse parses the LLM response into a playbook structure
func (pg *PlaybookGenerator) parsePlaybookResponse(content string, request *GeneratePlaybookRequest) (*Playbook, error) {
	// Clean the response to extract JSON
	jsonContent := pg.extractJSON(content)

	var playbookData struct {
		Title             string            `json:"title"`
		Description       string            `json:"description"`
		Steps             []json.RawMessage `json:"steps"`
		ValidationSteps   []json.RawMessage `json:"validation_steps"`
		RollbackSteps     []json.RawMessage `json:"rollback_steps"`
		SafetyWarnings    []SafetyWarning   `json:"safety_warnings"`
		EstimatedDuration string            `json:"estimated_duration"`
		RequiredSkills    []string          `json:"required_skills"`
		Prerequisites     []string          `json:"prerequisites"`
	}

	if err := json.Unmarshal([]byte(jsonContent), &playbookData); err != nil {
		return nil, fmt.Errorf("failed to parse JSON response: %w", err)
	}

	// Create playbook structure
	playbook := &Playbook{
		PlaybookID:     pg.generatePlaybookID(request.IncidentID),
		IncidentID:     request.IncidentID,
		Title:          playbookData.Title,
		Description:    playbookData.Description,
		Severity:       request.Severity,
		Category:       request.Category,
		RequiredSkills: playbookData.RequiredSkills,
		Prerequisites:  playbookData.Prerequisites,
		SafetyWarnings: playbookData.SafetyWarnings,
		Status:         StatusDraft,
		Tags:           []string{request.Category, request.Severity},
		TenantID:       request.TenantID,
		Environment:    request.Environment,
		CreatedAt:      time.Now(),
		UpdatedAt:      time.Now(),
		Version:        "1.0.0",
	}

	// Parse estimated duration
	if playbookData.EstimatedDuration != "" {
		if duration, err := time.ParseDuration(playbookData.EstimatedDuration); err == nil {
			playbook.EstimatedDuration = duration
		}
	}

	// Parse steps
	for i, stepData := range playbookData.Steps {
		step, err := pg.parsePlaybookStep(stepData, i+1)
		if err != nil {
			pg.logger.Warn("Failed to parse step", zap.Int("step", i+1), zap.Error(err))
			continue
		}
		playbook.Steps = append(playbook.Steps, *step)
	}

	// Parse validation steps
	for _, validationData := range playbookData.ValidationSteps {
		validation, err := pg.parseValidationStep(validationData)
		if err != nil {
			pg.logger.Warn("Failed to parse validation step", zap.Error(err))
			continue
		}
		playbook.ValidationSteps = append(playbook.ValidationSteps, *validation)
	}

	// Parse rollback steps
	for i, rollbackData := range playbookData.RollbackSteps {
		rollback, err := pg.parseRollbackStep(rollbackData, i+1)
		if err != nil {
			pg.logger.Warn("Failed to parse rollback step", zap.Int("step", i+1), zap.Error(err))
			continue
		}
		playbook.RollbackSteps = append(playbook.RollbackSteps, *rollback)
	}

	return playbook, nil
}

// extractJSON extracts JSON content from LLM response
func (pg *PlaybookGenerator) extractJSON(content string) string {
	// Find JSON block in markdown code blocks
	jsonRegex := regexp.MustCompile("```(?:json)?\n?(.*?)```")
	matches := jsonRegex.FindStringSubmatch(content)
	if len(matches) > 1 {
		return strings.TrimSpace(matches[1])
	}

	// Try to find JSON object directly
	start := strings.Index(content, "{")
	end := strings.LastIndex(content, "}")
	if start != -1 && end != -1 && end > start {
		return content[start : end+1]
	}

	return content
}

// parsePlaybookStep parses a single playbook step
func (pg *PlaybookGenerator) parsePlaybookStep(data json.RawMessage, stepNumber int) (*PlaybookStep, error) {
	var stepData struct {
		Title           string                 `json:"title"`
		Description     string                 `json:"description"`
		ActionType      string                 `json:"action_type"`
		Command         string                 `json:"command"`
		Parameters      map[string]interface{} `json:"parameters"`
		ExpectedOutput  string                 `json:"expected_output"`
		Timeout         string                 `json:"timeout"`
		RetryCount      int                    `json:"retry_count"`
		RiskLevel       string                 `json:"risk_level"`
		Automated       bool                   `json:"automated"`
		RequiresHuman   bool                   `json:"requires_human"`
		RollbackCommand string                 `json:"rollback_command"`
		SafetyChecks    []SafetyCheck          `json:"safety_checks"`
		DependsOn       []string               `json:"depends_on"`
	}

	if err := json.Unmarshal(data, &stepData); err != nil {
		return nil, err
	}

	step := &PlaybookStep{
		StepID:          fmt.Sprintf("step-%d", stepNumber),
		StepNumber:      stepNumber,
		Title:           stepData.Title,
		Description:     stepData.Description,
		ActionType:      stepData.ActionType,
		Command:         stepData.Command,
		Parameters:      stepData.Parameters,
		ExpectedOutput:  stepData.ExpectedOutput,
		RetryCount:      stepData.RetryCount,
		RiskLevel:       stepData.RiskLevel,
		Automated:       stepData.Automated,
		RequiresHuman:   stepData.RequiresHuman,
		RollbackCommand: stepData.RollbackCommand,
		SafetyChecks:    stepData.SafetyChecks,
		DependsOn:       stepData.DependsOn,
	}

	// Parse timeout
	if stepData.Timeout != "" {
		if timeout, err := time.ParseDuration(stepData.Timeout); err == nil {
			step.Timeout = timeout
		} else {
			step.Timeout = 30 * time.Second // Default
		}
	}

	return step, nil
}

// parseValidationStep parses a validation step
func (pg *PlaybookGenerator) parseValidationStep(data json.RawMessage) (*ValidationStep, error) {
	var validationData struct {
		Title          string `json:"title"`
		Description    string `json:"description"`
		CheckType      string `json:"check_type"`
		Command        string `json:"command"`
		ExpectedResult string `json:"expected_result"`
		Timeout        string `json:"timeout"`
		Automated      bool   `json:"automated"`
		Critical       bool   `json:"critical"`
	}

	if err := json.Unmarshal(data, &validationData); err != nil {
		return nil, err
	}

	validation := &ValidationStep{
		ValidationID:   fmt.Sprintf("validation-%d", time.Now().UnixNano()),
		Title:          validationData.Title,
		Description:    validationData.Description,
		CheckType:      validationData.CheckType,
		Command:        validationData.Command,
		ExpectedResult: validationData.ExpectedResult,
		Automated:      validationData.Automated,
		Critical:       validationData.Critical,
	}

	// Parse timeout
	if validationData.Timeout != "" {
		if timeout, err := time.ParseDuration(validationData.Timeout); err == nil {
			validation.Timeout = timeout
		} else {
			validation.Timeout = 10 * time.Second // Default
		}
	}

	return validation, nil
}

// parseRollbackStep parses a rollback step
func (pg *PlaybookGenerator) parseRollbackStep(data json.RawMessage, stepNumber int) (*RollbackStep, error) {
	var rollbackData struct {
		Title         string                 `json:"title"`
		Description   string                 `json:"description"`
		Command       string                 `json:"command"`
		Parameters    map[string]interface{} `json:"parameters"`
		Timeout       string                 `json:"timeout"`
		RiskLevel     string                 `json:"risk_level"`
		RequiresHuman bool                   `json:"requires_human"`
		Automated     bool                   `json:"automated"`
	}

	if err := json.Unmarshal(data, &rollbackData); err != nil {
		return nil, err
	}

	rollback := &RollbackStep{
		RollbackID:    fmt.Sprintf("rollback-%d", stepNumber),
		StepNumber:    stepNumber,
		Title:         rollbackData.Title,
		Description:   rollbackData.Description,
		Command:       rollbackData.Command,
		Parameters:    rollbackData.Parameters,
		RiskLevel:     rollbackData.RiskLevel,
		RequiresHuman: rollbackData.RequiresHuman,
		Automated:     rollbackData.Automated,
	}

	// Parse timeout
	if rollbackData.Timeout != "" {
		if timeout, err := time.ParseDuration(rollbackData.Timeout); err == nil {
			rollback.Timeout = timeout
		} else {
			rollback.Timeout = 30 * time.Second // Default
		}
	}

	return rollback, nil
}

// setPlaybookMetadata sets metadata fields on the playbook
func (pg *PlaybookGenerator) setPlaybookMetadata(playbook *Playbook, request *GeneratePlaybookRequest, llmResponse *LLMResponse) {
	playbook.GeneratedAt = time.Now()
	playbook.GeneratedBy = request.RequestedBy
	playbook.ModelVersion = llmResponse.Model
	playbook.ConfidenceScore = llmResponse.ConfidenceScore

	// Set approval requirement
	if request.RequireApproval || pg.config.RequiredApprovals != nil {
		playbook.ApprovalRequired = true
	}

	// Add additional tags
	playbook.Tags = append(playbook.Tags, "auto-generated")
	if llmResponse.ConfidenceScore > pg.config.AutoApproveThreshold {
		playbook.Tags = append(playbook.Tags, "high-confidence")
	}
}

// applySafetyMeasures applies safety warnings and mitigations to the playbook
func (pg *PlaybookGenerator) applySafetyMeasures(playbook *Playbook, safety *SafetyResult) {
	// Add safety warnings
	playbook.SafetyWarnings = append(playbook.SafetyWarnings, safety.Warnings...)

	// Add compliance notes
	playbook.ComplianceNotes = safety.Mitigations

	// Mark high-risk steps as requiring human approval
	for i := range playbook.Steps {
		step := &playbook.Steps[i]
		if step.RiskLevel == "high" || step.RiskLevel == "critical" {
			step.RequiresHuman = true
			step.Automated = false
		}
	}

	// Set overall approval requirement for high-risk playbooks
	if safety.RiskLevel == "high" || safety.RiskLevel == "critical" {
		playbook.ApprovalRequired = true
	}
}

// generatePlaybookID generates a unique ID for the playbook
func (pg *PlaybookGenerator) generatePlaybookID(incidentID string) string {
	timestamp := time.Now().Unix()
	return fmt.Sprintf("playbook-%s-%d", incidentID, timestamp)
}

// GetPlaybook retrieves a playbook by ID
func (pg *PlaybookGenerator) GetPlaybook(ctx context.Context, playbookID string) (*Playbook, error) {
	return pg.storage.GetPlaybook(ctx, playbookID)
}

// ListPlaybooks lists playbooks with optional filters
func (pg *PlaybookGenerator) ListPlaybooks(ctx context.Context, filters PlaybookFilters) ([]Playbook, error) {
	return pg.storage.ListPlaybooks(ctx, filters)
}

// UpdatePlaybook updates an existing playbook
func (pg *PlaybookGenerator) UpdatePlaybook(ctx context.Context, playbook *Playbook) error {
	playbook.UpdatedAt = time.Now()
	return pg.storage.UpdatePlaybook(ctx, playbook)
}

// ApprovePlaybook approves a playbook for execution
func (pg *PlaybookGenerator) ApprovePlaybook(ctx context.Context, playbookID, approvedBy string) error {
	playbook, err := pg.storage.GetPlaybook(ctx, playbookID)
	if err != nil {
		return err
	}

	now := time.Now()
	playbook.ApprovedBy = approvedBy
	playbook.ApprovedAt = &now
	playbook.Status = StatusApproved
	playbook.UpdatedAt = now

	return pg.storage.UpdatePlaybook(ctx, playbook)
}

// GetPlaybookMetrics returns analytics metrics for playbooks
func (pg *PlaybookGenerator) GetPlaybookMetrics(ctx context.Context, timeRange TimeRange) (*PlaybookMetrics, error) {
	return pg.storage.GetPlaybookMetrics(ctx, timeRange)
}

// DefaultPlaybookConfig returns default configuration
func DefaultPlaybookConfig() PlaybookConfig {
	return PlaybookConfig{
		Model:                "gpt-4",
		Temperature:          0.3,
		MaxTokens:            2000,
		TimeoutDuration:      60 * time.Second,
		MaxStepsPerPlaybook:  20,
		MinStepsPerPlaybook:  3,
		RequireValidation:    true,
		RequireRollback:      true,
		DefaultPriority:      "medium",
		EnableSafetyChecks:   true,
		ProhibitedActions:    []string{"rm -rf /", "DROP DATABASE", "shutdown"},
		RequiredApprovals:    []string{"high", "critical"},
		MinConfidenceScore:   0.7,
		EnablePeerReview:     true,
		AutoApproveThreshold: 0.9,
		CacheEnabled:         true,
		CacheTTL:             1 * time.Hour,
		BatchSize:            10,
		MaxConcurrent:        4,
	}
}
