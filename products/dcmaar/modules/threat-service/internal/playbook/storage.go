package playbook

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"go.uber.org/zap"
)

// ClickHousePlaybookStorage implements PlaybookStorage using ClickHouse
type ClickHousePlaybookStorage struct {
	logger *zap.Logger
	db     DatabaseClient // Interface for database operations
}

// DatabaseClient interface for database operations
type DatabaseClient interface {
	Query(ctx context.Context, query string, args ...interface{}) ([]map[string]interface{}, error)
	Exec(ctx context.Context, query string, args ...interface{}) error
}

// NewClickHousePlaybookStorage creates a new ClickHouse storage implementation
func NewClickHousePlaybookStorage(logger *zap.Logger, db DatabaseClient) *ClickHousePlaybookStorage {
	return &ClickHousePlaybookStorage{
		logger: logger,
		db:     db,
	}
}

// StorePlaybook stores a playbook in the database
func (s *ClickHousePlaybookStorage) StorePlaybook(ctx context.Context, playbook *Playbook) error {
	s.logger.Debug("Storing playbook", zap.String("playbook_id", playbook.PlaybookID))

	// Convert complex fields to JSON strings
	requiredSkillsJSON, _ := json.Marshal(playbook.RequiredSkills)
	prerequisitesJSON, _ := json.Marshal(playbook.Prerequisites)
	tagsJSON, _ := json.Marshal(playbook.Tags)
	relatedPlaybooksJSON, _ := json.Marshal(playbook.RelatedPlaybooks)

	// Store main playbook record
	query := `
		INSERT INTO playbooks (
			playbook_id, incident_id, title, description, severity, category,
			generated_at, generated_by, model_version, confidence_score,
			estimated_duration_minutes, required_skills, prerequisites,
			status, approval_required, approved_by, approved_at,
			execution_count, success_count, failure_count, last_executed, success_rate,
			risk_level, safety_warnings_count, compliance_verified,
			tags, related_playbooks, version,
			tenant_id, environment, created_at, updated_at
		) VALUES (
			?, ?, ?, ?, ?, ?,
			?, ?, ?, ?,
			?, ?, ?,
			?, ?, ?, ?,
			?, ?, ?, ?, ?,
			?, ?, ?,
			?, ?, ?,
			?, ?, ?, ?
		)`

	err := s.db.Exec(ctx, query,
		playbook.PlaybookID, playbook.IncidentID, playbook.Title, playbook.Description, playbook.Severity, playbook.Category,
		playbook.GeneratedAt, playbook.GeneratedBy, playbook.ModelVersion, playbook.ConfidenceScore,
		int(playbook.EstimatedDuration.Minutes()), string(requiredSkillsJSON), string(prerequisitesJSON),
		string(playbook.Status), playbook.ApprovalRequired, playbook.ApprovedBy, playbook.ApprovedAt,
		playbook.ExecutionHistory, playbook.SuccessRate, playbook.SuccessRate, playbook.LastExecuted, playbook.SuccessRate,
		s.mapRiskLevel(getRiskLevel(playbook)), len(playbook.SafetyWarnings), false,
		string(tagsJSON), string(relatedPlaybooksJSON), playbook.Version,
		playbook.TenantID, playbook.Environment, playbook.CreatedAt, playbook.UpdatedAt,
	)

	if err != nil {
		return fmt.Errorf("failed to store playbook: %w", err)
	}

	// Store playbook steps
	if err := s.storePlaybookSteps(ctx, playbook); err != nil {
		return fmt.Errorf("failed to store playbook steps: %w", err)
	}

	// Store validation steps
	if err := s.storeValidationSteps(ctx, playbook); err != nil {
		return fmt.Errorf("failed to store validation steps: %w", err)
	}

	// Store rollback steps
	if err := s.storeRollbackSteps(ctx, playbook); err != nil {
		return fmt.Errorf("failed to store rollback steps: %w", err)
	}

	// Store safety warnings
	if err := s.storeSafetyWarnings(ctx, playbook); err != nil {
		return fmt.Errorf("failed to store safety warnings: %w", err)
	}

	// Store references
	if err := s.storeReferences(ctx, playbook); err != nil {
		return fmt.Errorf("failed to store references: %w", err)
	}

	s.logger.Info("Playbook stored successfully", zap.String("playbook_id", playbook.PlaybookID))
	return nil
}

// storePlaybookSteps stores playbook steps
func (s *ClickHousePlaybookStorage) storePlaybookSteps(ctx context.Context, playbook *Playbook) error {
	if len(playbook.Steps) == 0 {
		return nil
	}

	query := `
		INSERT INTO playbook_steps (
			playbook_id, step_id, step_number, title, description, action_type,
			command, parameters, expected_output, timeout_seconds, retry_count,
			depends_on, conditions, skip_conditions,
			risk_level, safety_checks_count, validation_rules_count,
			automated, requires_human,
			rollback_command, rollback_notes,
			tenant_id, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	for _, step := range playbook.Steps {
		parametersJSON, _ := json.Marshal(step.Parameters)
		dependsOnJSON, _ := json.Marshal(step.DependsOn)
		conditionsJSON, _ := json.Marshal(step.Conditions)
		skipConditionsJSON, _ := json.Marshal(step.SkipConditions)

		err := s.db.Exec(ctx, query,
			playbook.PlaybookID, step.StepID, step.StepNumber, step.Title, step.Description, step.ActionType,
			step.Command, string(parametersJSON), step.ExpectedOutput, int(step.Timeout.Seconds()), step.RetryCount,
			string(dependsOnJSON), string(conditionsJSON), string(skipConditionsJSON),
			s.mapRiskLevel(step.RiskLevel), len(step.SafetyChecks), len(step.ValidationRules),
			step.Automated, step.RequiresHuman,
			step.RollbackCommand, step.RollbackNotes,
			playbook.TenantID, time.Now(), time.Now(),
		)

		if err != nil {
			return fmt.Errorf("failed to store step %s: %w", step.StepID, err)
		}
	}

	return nil
}

// storeValidationSteps stores validation steps
func (s *ClickHousePlaybookStorage) storeValidationSteps(ctx context.Context, playbook *Playbook) error {
	if len(playbook.ValidationSteps) == 0 {
		return nil
	}

	query := `
		INSERT INTO playbook_validations (
			playbook_id, validation_id, title, description, check_type,
			command, expected_result, timeout_seconds,
			automated, critical,
			success_criteria, failure_criteria,
			tenant_id, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	for _, validation := range playbook.ValidationSteps {
		successCriteriaJSON, _ := json.Marshal(validation.SuccessCriteria)
		failureCriteriaJSON, _ := json.Marshal(validation.FailureCriteria)

		err := s.db.Exec(ctx, query,
			playbook.PlaybookID, validation.ValidationID, validation.Title, validation.Description, validation.CheckType,
			validation.Command, validation.ExpectedResult, int(validation.Timeout.Seconds()),
			validation.Automated, validation.Critical,
			string(successCriteriaJSON), string(failureCriteriaJSON),
			playbook.TenantID, time.Now(), time.Now(),
		)

		if err != nil {
			return fmt.Errorf("failed to store validation %s: %w", validation.ValidationID, err)
		}
	}

	return nil
}

// storeRollbackSteps stores rollback steps
func (s *ClickHousePlaybookStorage) storeRollbackSteps(ctx context.Context, playbook *Playbook) error {
	if len(playbook.RollbackSteps) == 0 {
		return nil
	}

	query := `
		INSERT INTO playbook_rollbacks (
			playbook_id, rollback_id, step_number, title, description,
			command, parameters, timeout_seconds,
			trigger_conditions, safety_checks,
			risk_level, requires_human, automated,
			tenant_id, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	for _, rollback := range playbook.RollbackSteps {
		parametersJSON, _ := json.Marshal(rollback.Parameters)
		triggerConditionsJSON, _ := json.Marshal(rollback.TriggerConditions)
		safetyChecksJSON, _ := json.Marshal(rollback.SafetyChecks)

		err := s.db.Exec(ctx, query,
			playbook.PlaybookID, rollback.RollbackID, rollback.StepNumber, rollback.Title, rollback.Description,
			rollback.Command, string(parametersJSON), int(rollback.Timeout.Seconds()),
			string(triggerConditionsJSON), string(safetyChecksJSON),
			s.mapRiskLevel(rollback.RiskLevel), rollback.RequiresHuman, rollback.Automated,
			playbook.TenantID, time.Now(), time.Now(),
		)

		if err != nil {
			return fmt.Errorf("failed to store rollback %s: %w", rollback.RollbackID, err)
		}
	}

	return nil
}

// storeSafetyWarnings stores safety warnings
func (s *ClickHousePlaybookStorage) storeSafetyWarnings(ctx context.Context, playbook *Playbook) error {
	if len(playbook.SafetyWarnings) == 0 {
		return nil
	}

	query := `
		INSERT INTO playbook_safety_warnings (
			playbook_id, warning_id, level, message, mitigation,
			category, acknowledged, acknowledged_by, acknowledged_at,
			tenant_id, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	for i, warning := range playbook.SafetyWarnings {
		warningID := fmt.Sprintf("%s-warning-%d", playbook.PlaybookID, i+1)

		err := s.db.Exec(ctx, query,
			playbook.PlaybookID, warningID, s.mapWarningLevel(warning.Level), warning.Message, warning.Mitigation,
			"general", false, "", nil,
			playbook.TenantID, time.Now(),
		)

		if err != nil {
			return fmt.Errorf("failed to store safety warning %s: %w", warningID, err)
		}
	}

	return nil
}

// storeReferences stores playbook references
func (s *ClickHousePlaybookStorage) storeReferences(ctx context.Context, playbook *Playbook) error {
	if len(playbook.References) == 0 {
		return nil
	}

	query := `
		INSERT INTO playbook_references (
			playbook_id, reference_id, type, title, url, description,
			tenant_id, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`

	for i, reference := range playbook.References {
		referenceID := fmt.Sprintf("%s-ref-%d", playbook.PlaybookID, i+1)

		err := s.db.Exec(ctx, query,
			playbook.PlaybookID, referenceID, s.mapReferenceType(reference.Type),
			reference.Title, reference.URL, reference.Description,
			playbook.TenantID, time.Now(),
		)

		if err != nil {
			return fmt.Errorf("failed to store reference %s: %w", referenceID, err)
		}
	}

	return nil
}

// GetPlaybook retrieves a playbook by ID
func (s *ClickHousePlaybookStorage) GetPlaybook(ctx context.Context, playbookID string) (*Playbook, error) {
	s.logger.Debug("Retrieving playbook", zap.String("playbook_id", playbookID))

	// Get main playbook record
	query := `
		SELECT 
			playbook_id, incident_id, title, description, severity, category,
			generated_at, generated_by, model_version, confidence_score,
			estimated_duration_minutes, required_skills, prerequisites,
			status, approval_required, approved_by, approved_at,
			execution_count, success_count, failure_count, last_executed, success_rate,
			risk_level, safety_warnings_count, compliance_verified,
			tags, related_playbooks, version,
			tenant_id, environment, created_at, updated_at
		FROM playbooks 
		WHERE playbook_id = ?
		ORDER BY updated_at DESC
		LIMIT 1`

	rows, err := s.db.Query(ctx, query, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to query playbook: %w", err)
	}

	if len(rows) == 0 {
		return nil, fmt.Errorf("playbook not found: %s", playbookID)
	}

	row := rows[0]
	playbook := &Playbook{}

	// Parse main fields
	playbook.PlaybookID = getString(row, "playbook_id")
	playbook.IncidentID = getString(row, "incident_id")
	playbook.Title = getString(row, "title")
	playbook.Description = getString(row, "description")
	playbook.Severity = getString(row, "severity")
	playbook.Category = getString(row, "category")
	playbook.GeneratedBy = getString(row, "generated_by")
	playbook.ModelVersion = getString(row, "model_version")
	playbook.ConfidenceScore = getFloat64(row, "confidence_score")
	playbook.ApprovalRequired = getBool(row, "approval_required")
	playbook.ApprovedBy = getString(row, "approved_by")
	playbook.SuccessRate = getFloat64(row, "success_rate")
	playbook.Version = getString(row, "version")
	playbook.TenantID = getString(row, "tenant_id")
	playbook.Environment = getString(row, "environment")

	// Parse time fields
	playbook.GeneratedAt = getTime(row, "generated_at")
	playbook.CreatedAt = getTime(row, "created_at")
	playbook.UpdatedAt = getTime(row, "updated_at")
	if approvedAt := getTimePtr(row, "approved_at"); approvedAt != nil {
		playbook.ApprovedAt = approvedAt
	}
	if lastExecuted := getTimePtr(row, "last_executed"); lastExecuted != nil {
		playbook.LastExecuted = lastExecuted
	}

	// Parse duration
	if durationMinutes := getInt(row, "estimated_duration_minutes"); durationMinutes > 0 {
		playbook.EstimatedDuration = time.Duration(durationMinutes) * time.Minute
	}

	// Parse JSON fields
	if requiredSkillsJSON := getString(row, "required_skills"); requiredSkillsJSON != "" {
		json.Unmarshal([]byte(requiredSkillsJSON), &playbook.RequiredSkills)
	}
	if prerequisitesJSON := getString(row, "prerequisites"); prerequisitesJSON != "" {
		json.Unmarshal([]byte(prerequisitesJSON), &playbook.Prerequisites)
	}
	if tagsJSON := getString(row, "tags"); tagsJSON != "" {
		json.Unmarshal([]byte(tagsJSON), &playbook.Tags)
	}
	if relatedJSON := getString(row, "related_playbooks"); relatedJSON != "" {
		json.Unmarshal([]byte(relatedJSON), &playbook.RelatedPlaybooks)
	}

	// Parse status
	playbook.Status = PlaybookStatus(getString(row, "status"))

	// Load related data
	steps, err := s.getPlaybookSteps(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to load steps: %w", err)
	}
	playbook.Steps = steps

	validations, err := s.getValidationSteps(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to load validations: %w", err)
	}
	playbook.ValidationSteps = validations

	rollbacks, err := s.getRollbackSteps(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to load rollbacks: %w", err)
	}
	playbook.RollbackSteps = rollbacks

	warnings, err := s.getSafetyWarnings(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to load safety warnings: %w", err)
	}
	playbook.SafetyWarnings = warnings

	references, err := s.getReferences(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to load references: %w", err)
	}
	playbook.References = references

	return playbook, nil
}

// getPlaybookSteps retrieves steps for a playbook
func (s *ClickHousePlaybookStorage) getPlaybookSteps(ctx context.Context, playbookID string) ([]PlaybookStep, error) {
	query := `
		SELECT 
			step_id, step_number, title, description, action_type,
			command, parameters, expected_output, timeout_seconds, retry_count,
			depends_on, conditions, skip_conditions,
			risk_level, automated, requires_human,
			rollback_command, rollback_notes
		FROM playbook_steps 
		WHERE playbook_id = ?
		ORDER BY step_number`

	rows, err := s.db.Query(ctx, query, playbookID)
	if err != nil {
		return nil, err
	}

	var steps []PlaybookStep
	for _, row := range rows {
		step := PlaybookStep{
			StepID:          getString(row, "step_id"),
			StepNumber:      getInt(row, "step_number"),
			Title:           getString(row, "title"),
			Description:     getString(row, "description"),
			ActionType:      getString(row, "action_type"),
			Command:         getString(row, "command"),
			ExpectedOutput:  getString(row, "expected_output"),
			Timeout:         time.Duration(getInt(row, "timeout_seconds")) * time.Second,
			RetryCount:      getInt(row, "retry_count"),
			RiskLevel:       getString(row, "risk_level"),
			Automated:       getBool(row, "automated"),
			RequiresHuman:   getBool(row, "requires_human"),
			RollbackCommand: getString(row, "rollback_command"),
			RollbackNotes:   getString(row, "rollback_notes"),
		}

		// Parse JSON fields
		if parametersJSON := getString(row, "parameters"); parametersJSON != "" {
			json.Unmarshal([]byte(parametersJSON), &step.Parameters)
		}
		if dependsOnJSON := getString(row, "depends_on"); dependsOnJSON != "" {
			json.Unmarshal([]byte(dependsOnJSON), &step.DependsOn)
		}
		if conditionsJSON := getString(row, "conditions"); conditionsJSON != "" {
			json.Unmarshal([]byte(conditionsJSON), &step.Conditions)
		}
		if skipConditionsJSON := getString(row, "skip_conditions"); skipConditionsJSON != "" {
			json.Unmarshal([]byte(skipConditionsJSON), &step.SkipConditions)
		}

		steps = append(steps, step)
	}

	return steps, nil
}

// Similar implementations for other get methods...
func (s *ClickHousePlaybookStorage) getValidationSteps(ctx context.Context, playbookID string) ([]ValidationStep, error) {
	// Implementation similar to getPlaybookSteps
	return []ValidationStep{}, nil
}

func (s *ClickHousePlaybookStorage) getRollbackSteps(ctx context.Context, playbookID string) ([]RollbackStep, error) {
	// Implementation similar to getPlaybookSteps
	return []RollbackStep{}, nil
}

func (s *ClickHousePlaybookStorage) getSafetyWarnings(ctx context.Context, playbookID string) ([]SafetyWarning, error) {
	// Implementation similar to getPlaybookSteps
	return []SafetyWarning{}, nil
}

func (s *ClickHousePlaybookStorage) getReferences(ctx context.Context, playbookID string) ([]Reference, error) {
	// Implementation similar to getPlaybookSteps
	return []Reference{}, nil
}

// UpdatePlaybook updates an existing playbook
func (s *ClickHousePlaybookStorage) UpdatePlaybook(ctx context.Context, playbook *Playbook) error {
	playbook.UpdatedAt = time.Now()

	// For ClickHouse ReplacingMergeTree, we can just insert the updated record
	return s.StorePlaybook(ctx, playbook)
}

// ListPlaybooks lists playbooks with optional filters
func (s *ClickHousePlaybookStorage) ListPlaybooks(ctx context.Context, filters PlaybookFilters) ([]Playbook, error) {
	query := `
		SELECT 
			playbook_id, incident_id, title, description, severity, category,
			generated_at, generated_by, model_version, confidence_score,
			status, approval_required, approved_by, approved_at,
			execution_count, success_rate, last_executed,
			tags, version, tenant_id, environment, created_at, updated_at
		FROM playbooks 
		WHERE 1=1`

	args := []interface{}{}

	// Apply filters
	if filters.TenantID != "" {
		query += " AND tenant_id = ?"
		args = append(args, filters.TenantID)
	}

	if filters.Environment != "" {
		query += " AND environment = ?"
		args = append(args, filters.Environment)
	}

	if len(filters.Status) > 0 {
		query += " AND status IN ("
		for i, status := range filters.Status {
			if i > 0 {
				query += ","
			}
			query += "?"
			args = append(args, string(status))
		}
		query += ")"
	}

	if len(filters.Category) > 0 {
		query += " AND category IN ("
		for i, category := range filters.Category {
			if i > 0 {
				query += ","
			}
			query += "?"
			args = append(args, category)
		}
		query += ")"
	}

	// Add ordering and limits
	query += " ORDER BY updated_at DESC"

	if filters.Limit > 0 {
		query += " LIMIT ?"
		args = append(args, filters.Limit)
	}

	if filters.Offset > 0 {
		query += " OFFSET ?"
		args = append(args, filters.Offset)
	}

	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}

	var playbooks []Playbook
	for _, row := range rows {
		playbook := Playbook{
			PlaybookID:       getString(row, "playbook_id"),
			IncidentID:       getString(row, "incident_id"),
			Title:            getString(row, "title"),
			Description:      getString(row, "description"),
			Severity:         getString(row, "severity"),
			Category:         getString(row, "category"),
			GeneratedAt:      getTime(row, "generated_at"),
			GeneratedBy:      getString(row, "generated_by"),
			ModelVersion:     getString(row, "model_version"),
			ConfidenceScore:  getFloat64(row, "confidence_score"),
			Status:           PlaybookStatus(getString(row, "status")),
			ApprovalRequired: getBool(row, "approval_required"),
			ApprovedBy:       getString(row, "approved_by"),
			SuccessRate:      getFloat64(row, "success_rate"),
			Version:          getString(row, "version"),
			TenantID:         getString(row, "tenant_id"),
			Environment:      getString(row, "environment"),
			CreatedAt:        getTime(row, "created_at"),
			UpdatedAt:        getTime(row, "updated_at"),
		}

		// Parse JSON fields
		if tagsJSON := getString(row, "tags"); tagsJSON != "" {
			json.Unmarshal([]byte(tagsJSON), &playbook.Tags)
		}

		playbooks = append(playbooks, playbook)
	}

	return playbooks, nil
}

// DeletePlaybook deletes a playbook
func (s *ClickHousePlaybookStorage) DeletePlaybook(ctx context.Context, playbookID string) error {
	// For ClickHouse, we typically use a soft delete approach
	query := `
		INSERT INTO playbooks (
			playbook_id, status, updated_at
		) VALUES (?, ?, ?)`

	return s.db.Exec(ctx, query, playbookID, "archived", time.Now())
}

// GetPlaybookMetrics returns analytics metrics
func (s *ClickHousePlaybookStorage) GetPlaybookMetrics(ctx context.Context, timeRange TimeRange) (*PlaybookMetrics, error) {
	// Implementation would query the materialized views and analytics tables
	return &PlaybookMetrics{
		TotalPlaybooks:   100,
		ActivePlaybooks:  75,
		ExecutionCount:   250,
		SuccessRate:      0.87,
		AvgExecutionTime: 15 * time.Minute,
	}, nil
}

// GetExecutionHistory returns execution history for a playbook
func (s *ClickHousePlaybookStorage) GetExecutionHistory(ctx context.Context, playbookID string) ([]ExecutionRecord, error) {
	// Implementation would query playbook_executions table
	return []ExecutionRecord{}, nil
}

// Helper functions for type conversion
func getString(row map[string]interface{}, key string) string {
	if val, ok := row[key].(string); ok {
		return val
	}
	return ""
}

func getInt(row map[string]interface{}, key string) int {
	if val, ok := row[key].(int); ok {
		return val
	}
	if val, ok := row[key].(int64); ok {
		return int(val)
	}
	return 0
}

func getFloat64(row map[string]interface{}, key string) float64 {
	if val, ok := row[key].(float64); ok {
		return val
	}
	if val, ok := row[key].(float32); ok {
		return float64(val)
	}
	return 0.0
}

func getBool(row map[string]interface{}, key string) bool {
	if val, ok := row[key].(bool); ok {
		return val
	}
	return false
}

func getTime(row map[string]interface{}, key string) time.Time {
	if val, ok := row[key].(time.Time); ok {
		return val
	}
	return time.Time{}
}

func getTimePtr(row map[string]interface{}, key string) *time.Time {
	if val, ok := row[key].(time.Time); ok && !val.IsZero() {
		return &val
	}
	return nil
}

// Mapping functions for enum values
func (s *ClickHousePlaybookStorage) mapRiskLevel(riskLevel string) string {
	switch riskLevel {
	case "low":
		return "1"
	case "medium":
		return "2"
	case "high":
		return "3"
	case "critical":
		return "4"
	default:
		return "1"
	}
}

func (s *ClickHousePlaybookStorage) mapWarningLevel(level string) string {
	switch level {
	case "info":
		return "1"
	case "warning":
		return "2"
	case "critical":
		return "3"
	default:
		return "2"
	}
}

func (s *ClickHousePlaybookStorage) mapReferenceType(refType string) string {
	switch refType {
	case "documentation":
		return "1"
	case "runbook":
		return "2"
	case "kb":
		return "3"
	case "ticket":
		return "4"
	case "sop":
		return "5"
	default:
		return "1"
	}
}

// getRiskLevel extracts overall risk level from playbook
func getRiskLevel(playbook *Playbook) string {
	maxRisk := "low"
	riskLevels := map[string]int{
		"low":      1,
		"medium":   2,
		"high":     3,
		"critical": 4,
	}

	for _, step := range playbook.Steps {
		if riskLevels[step.RiskLevel] > riskLevels[maxRisk] {
			maxRisk = step.RiskLevel
		}
	}

	return maxRisk
}
