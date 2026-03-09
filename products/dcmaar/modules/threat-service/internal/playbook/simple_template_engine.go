package playbook

import (
	"context"
	"fmt"
	"strings"
	"text/template"
	"time"

	"go.uber.org/zap"
)

// SimpleTemplateEngine provides basic template functionality for existing playbook system
type SimpleTemplateEngine struct {
	logger    *zap.Logger
	templates map[string]*PlaybookTemplate
}

// SimpleIncidentContext provides context for template matching and generation
type SimpleIncidentContext struct {
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
	TenantID      string                 `json:"tenant_id"`
	CreatedAt     time.Time              `json:"created_at"`
}

// TemplateGenerationContext provides context for generating playbooks from templates
type TemplateGenerationContext struct {
	Incident  *SimpleIncidentContext `json:"incident"`
	Variables map[string]interface{} `json:"variables"`
	Timestamp time.Time              `json:"timestamp"`
	TenantID  string                 `json:"tenant_id"`
}

// NewSimpleTemplateEngine creates a new simple template engine
func NewSimpleTemplateEngine(logger *zap.Logger) *SimpleTemplateEngine {
	engine := &SimpleTemplateEngine{
		logger:    logger,
		templates: make(map[string]*PlaybookTemplate),
	}

	// Load built-in templates
	engine.loadBuiltInTemplates()

	return engine
}

// loadBuiltInTemplates loads basic built-in templates
func (e *SimpleTemplateEngine) loadBuiltInTemplates() {
	templates := []*PlaybookTemplate{
		e.createServiceRestartTemplate(),
		e.createHighCPUTemplate(),
		e.createMemoryLeakTemplate(),
		e.createDiskSpaceTemplate(),
		e.createDatabaseConnectionTemplate(),
	}

	for _, tmpl := range templates {
		e.RegisterTemplate(tmpl)
	}

	e.logger.Info("Loaded built-in templates", zap.Int("count", len(templates)))
}

// RegisterTemplate registers a template with the engine
func (e *SimpleTemplateEngine) RegisterTemplate(template *PlaybookTemplate) error {
	if template.TemplateID == "" {
		return fmt.Errorf("template ID is required")
	}

	e.templates[template.TemplateID] = template

	e.logger.Info("Registered template",
		zap.String("template_id", template.TemplateID),
		zap.String("name", template.Name),
		zap.String("category", template.Category))

	return nil
}

// FindMatchingTemplates finds templates that match the incident context
func (e *SimpleTemplateEngine) FindMatchingTemplates(ctx context.Context, incident *SimpleIncidentContext) ([]*PlaybookTemplate, error) {
	e.logger.Debug("Finding matching templates",
		zap.String("incident_id", incident.IncidentID),
		zap.String("category", incident.Category),
		zap.String("severity", incident.Severity))

	var matches []*PlaybookTemplate

	for _, template := range e.templates {
		if e.templateMatches(template, incident) {
			matches = append(matches, template)
		}
	}

	e.logger.Info("Found matching templates",
		zap.String("incident_id", incident.IncidentID),
		zap.Int("match_count", len(matches)))

	return matches, nil
}

// templateMatches checks if a template matches an incident
func (e *SimpleTemplateEngine) templateMatches(template *PlaybookTemplate, incident *SimpleIncidentContext) bool {
	// Simple matching based on category
	if template.Category != "" && template.Category != incident.Category {
		return false
	}

	// Check if template applies to severity
	if template.Severity != "" && template.Severity != incident.Severity {
		return false
	}

	return true
}

// GenerateFromTemplate generates a playbook from a template
func (e *SimpleTemplateEngine) GenerateFromTemplate(ctx context.Context, templateID string, incident *SimpleIncidentContext, variables map[string]interface{}) (*Playbook, error) {
	template, exists := e.templates[templateID]
	if !exists {
		return nil, fmt.Errorf("template not found: %s", templateID)
	}

	e.logger.Debug("Generating playbook from template",
		zap.String("template_id", templateID),
		zap.String("incident_id", incident.IncidentID))

	// Create template context
	templateContext := &TemplateGenerationContext{
		Incident:  incident,
		Variables: variables,
		Timestamp: time.Now(),
		TenantID:  incident.TenantID,
	}

	// Generate playbook
	playbook := &Playbook{
		PlaybookID:      generatePlaybookID(),
		IncidentID:      incident.IncidentID,
		GeneratedAt:     time.Now(),
		GeneratedBy:     "template-engine",
		ModelVersion:    "template-v1.0",
		ConfidenceScore: 0.9, // Templates have high confidence
		Status:          StatusDraft,
		TenantID:        incident.TenantID,
		Environment:     incident.Environment,
		CreatedAt:       time.Now(),
		UpdatedAt:       time.Now(),
		Version:         "1.0",
		Tags:            []string{"template-generated"},
	}

	// Generate basic content from template
	title, err := e.renderTemplate(template.Template, templateContext)
	if err != nil {
		return nil, fmt.Errorf("failed to render template: %w", err)
	}

	playbook.Title = fmt.Sprintf("%s - %s", template.Name, incident.Title)
	playbook.Description = title
	playbook.Severity = incident.Severity
	playbook.Category = template.Category

	// Generate basic steps based on template content
	steps := e.generateBasicSteps(template, templateContext)
	playbook.Steps = steps

	e.logger.Info("Generated playbook from template",
		zap.String("template_id", templateID),
		zap.String("playbook_id", playbook.PlaybookID),
		zap.Int("step_count", len(steps)))

	return playbook, nil
}

// generateBasicSteps creates basic steps from template
func (e *SimpleTemplateEngine) generateBasicSteps(template *PlaybookTemplate, context *TemplateGenerationContext) []PlaybookStep {
	var steps []PlaybookStep

	// Create steps based on template variables and category
	switch template.Category {
	case "service":
		steps = append(steps, PlaybookStep{
			StepID:      generateStepID(),
			StepNumber:  1,
			Title:       "Check Service Status",
			Description: fmt.Sprintf("Check the status of %s service", context.Incident.Component),
			ActionType:  "check",
			Command:     "systemctl status {{.service_name}}",
			Parameters:  map[string]interface{}{"service_name": context.Incident.Component},
			Timeout:     30 * time.Second,
			RiskLevel:   "low",
			Automated:   true,
		})

		steps = append(steps, PlaybookStep{
			StepID:        generateStepID(),
			StepNumber:    2,
			Title:         "Restart Service",
			Description:   fmt.Sprintf("Restart %s service if needed", context.Incident.Component),
			ActionType:    "remediate",
			Command:       "systemctl restart {{.service_name}}",
			Parameters:    map[string]interface{}{"service_name": context.Incident.Component},
			Timeout:       60 * time.Second,
			RiskLevel:     "medium",
			Automated:     false,
			RequiresHuman: true,
		})

	case "performance":
		steps = append(steps, PlaybookStep{
			StepID:      generateStepID(),
			StepNumber:  1,
			Title:       "Check System Resources",
			Description: "Check CPU, memory, and disk usage",
			ActionType:  "check",
			Command:     "top -n 1 && free -m && df -h",
			Timeout:     30 * time.Second,
			RiskLevel:   "low",
			Automated:   true,
		})

		steps = append(steps, PlaybookStep{
			StepID:      generateStepID(),
			StepNumber:  2,
			Title:       "Identify Resource-Heavy Processes",
			Description: "Identify processes consuming high resources",
			ActionType:  "analyze",
			Command:     "ps aux --sort=-%cpu | head -10",
			Timeout:     30 * time.Second,
			RiskLevel:   "low",
			Automated:   true,
		})

	default:
		// Generic investigation steps
		steps = append(steps, PlaybookStep{
			StepID:        generateStepID(),
			StepNumber:    1,
			Title:         "Initial Investigation",
			Description:   "Gather initial information about the incident",
			ActionType:    "investigate",
			Command:       "echo 'Manual investigation required'",
			Timeout:       300 * time.Second,
			RiskLevel:     "low",
			Automated:     false,
			RequiresHuman: true,
		})
	}

	return steps
}

// renderTemplate renders a template string with context
func (e *SimpleTemplateEngine) renderTemplate(templateStr string, context *TemplateGenerationContext) (string, error) {
	if templateStr == "" {
		return "", nil
	}

	tmpl, err := template.New("playbook").Parse(templateStr)
	if err != nil {
		return "", err
	}

	var result strings.Builder
	err = tmpl.Execute(&result, context)
	if err != nil {
		return "", err
	}

	return result.String(), nil
}

// Built-in template creation methods
func (e *SimpleTemplateEngine) createServiceRestartTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "service-restart-001",
		Name:        "Service Restart Playbook",
		Description: "Standard playbook for restarting services",
		Category:    "service",
		Severity:    "medium",
		Template:    "This playbook provides steps to safely restart {{.Incident.Component}} service in {{.Incident.Environment}} environment.",
		Variables: []TemplateVariable{
			{
				Name:        "service_name",
				Type:        "string",
				Description: "Name of the service to restart",
				Required:    true,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func (e *SimpleTemplateEngine) createHighCPUTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "cpu-high-001",
		Name:        "High CPU Usage Investigation",
		Description: "Playbook for investigating high CPU usage",
		Category:    "performance",
		Severity:    "high",
		Template:    "This playbook investigates high CPU usage on {{.Incident.Component}} in {{.Incident.Environment}}.",
		Variables: []TemplateVariable{
			{
				Name:        "cpu_threshold",
				Type:        "number",
				Description: "CPU usage threshold percentage",
				Required:    false,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func (e *SimpleTemplateEngine) createMemoryLeakTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "memory-leak-001",
		Name:        "Memory Leak Investigation",
		Description: "Playbook for investigating memory leaks",
		Category:    "performance",
		Severity:    "high",
		Template:    "This playbook investigates potential memory leaks in {{.Incident.Component}}.",
		Variables: []TemplateVariable{
			{
				Name:        "memory_threshold",
				Type:        "number",
				Description: "Memory usage threshold percentage",
				Required:    false,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func (e *SimpleTemplateEngine) createDiskSpaceTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "disk-space-001",
		Name:        "Disk Space Cleanup",
		Description: "Playbook for handling disk space issues",
		Category:    "infrastructure",
		Severity:    "medium",
		Template:    "This playbook addresses disk space issues on {{.Incident.Component}}.",
		Variables: []TemplateVariable{
			{
				Name:        "disk_threshold",
				Type:        "number",
				Description: "Disk usage threshold percentage",
				Required:    false,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func (e *SimpleTemplateEngine) createDatabaseConnectionTemplate() *PlaybookTemplate {
	return &PlaybookTemplate{
		TemplateID:  "db-connection-001",
		Name:        "Database Connection Issues",
		Description: "Playbook for database connectivity problems",
		Category:    "database",
		Severity:    "critical",
		Template:    "This playbook troubleshoots database connection issues for {{.Incident.Component}}.",
		Variables: []TemplateVariable{
			{
				Name:        "database_host",
				Type:        "string",
				Description: "Database host address",
				Required:    false,
			},
			{
				Name:        "database_port",
				Type:        "number",
				Description: "Database port number",
				Required:    false,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}
