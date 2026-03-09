package miner

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	_ "github.com/ClickHouse/clickhouse-go/v2"
)

// ClickHouseRepository implements the Repository interface using ClickHouse
type ClickHouseRepository struct {
	db *sql.DB
}

// NewClickHouseRepository creates a new ClickHouse repository
func NewClickHouseRepository(dsn string) (*ClickHouseRepository, error) {
	db, err := sql.Open("clickhouse", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to ClickHouse: %w", err)
	}

	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping ClickHouse: %w", err)
	}

	repo := &ClickHouseRepository{db: db}

	// Ensure tables exist
	if err := repo.createTables(); err != nil {
		return nil, fmt.Errorf("failed to create tables: %w", err)
	}

	return repo, nil
}

// SaveActionLog stores an action log entry
func (r *ClickHouseRepository) SaveActionLog(ctx context.Context, log *ActionLog) error {
	query := `
		INSERT INTO action_logs (
			id, user_id, timestamp, action_type, incident_id, service_name,
			parameters, success, duration_ms, comments, tags, context
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`

	parametersJSON, err := json.Marshal(log.Parameters)
	if err != nil {
		return fmt.Errorf("failed to marshal parameters: %w", err)
	}

	contextJSON, err := json.Marshal(log.Context)
	if err != nil {
		return fmt.Errorf("failed to marshal context: %w", err)
	}

	_, err = r.db.ExecContext(ctx, query,
		log.ID,
		log.UserID,
		log.Timestamp,
		log.ActionType,
		log.IncidentID,
		log.ServiceName,
		string(parametersJSON),
		log.Success,
		int64(log.Duration.Milliseconds()),
		log.Comments,
		log.Tags,
		string(contextJSON),
	)

	return err
}

// GetActionLogs retrieves action logs with filters
func (r *ClickHouseRepository) GetActionLogs(ctx context.Context, filters ActionLogFilters) ([]ActionLog, error) {
	query := "SELECT id, user_id, timestamp, action_type, incident_id, service_name, parameters, success, duration_ms, comments, tags, context FROM action_logs WHERE 1=1"
	args := make([]interface{}, 0)

	// Apply filters
	if filters.UserID != "" {
		query += " AND user_id = ?"
		args = append(args, filters.UserID)
	}

	if filters.ActionType != "" {
		query += " AND action_type = ?"
		args = append(args, filters.ActionType)
	}

	if filters.IncidentType != "" {
		query += " AND JSONExtractString(context, 'incident_type') = ?"
		args = append(args, filters.IncidentType)
	}

	if filters.TeamOnCall != "" {
		query += " AND JSONExtractString(context, 'team_on_call') = ?"
		args = append(args, filters.TeamOnCall)
	}

	if filters.Environment != "" {
		query += " AND JSONExtractString(context, 'environment') = ?"
		args = append(args, filters.Environment)
	}

	if filters.Since != nil {
		query += " AND timestamp >= ?"
		args = append(args, *filters.Since)
	}

	if filters.Until != nil {
		query += " AND timestamp <= ?"
		args = append(args, *filters.Until)
	}

	if filters.Success != nil {
		query += " AND success = ?"
		args = append(args, *filters.Success)
	}

	if len(filters.Tags) > 0 {
		query += " AND hasAny(tags, ?)"
		args = append(args, filters.Tags)
	}

	// Add ordering and limit
	query += " ORDER BY timestamp DESC"
	if filters.Limit > 0 {
		query += " LIMIT ?"
		args = append(args, filters.Limit)
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var logs []ActionLog
	for rows.Next() {
		var log ActionLog
		var parametersJSON, contextJSON string
		var durationMs int64

		err := rows.Scan(
			&log.ID,
			&log.UserID,
			&log.Timestamp,
			&log.ActionType,
			&log.IncidentID,
			&log.ServiceName,
			&parametersJSON,
			&log.Success,
			&durationMs,
			&log.Comments,
			&log.Tags,
			&contextJSON,
		)
		if err != nil {
			return nil, err
		}

		log.Duration = time.Duration(durationMs) * time.Millisecond

		if err := json.Unmarshal([]byte(parametersJSON), &log.Parameters); err != nil {
			return nil, fmt.Errorf("failed to unmarshal parameters: %w", err)
		}

		if err := json.Unmarshal([]byte(contextJSON), &log.Context); err != nil {
			return nil, fmt.Errorf("failed to unmarshal context: %w", err)
		}

		logs = append(logs, log)
	}

	return logs, rows.Err()
}

// SaveCandidate stores an automation candidate
func (r *ClickHouseRepository) SaveCandidate(ctx context.Context, candidate *AutomationCandidate) error {
	query := `
		INSERT INTO automation_candidates (
			id, title, description, priority, score, evidence, recommendation,
			status, created_at, updated_at, reviewed_by, reviewed_at, tags, metadata
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`

	evidenceJSON, err := json.Marshal(candidate.Evidence)
	if err != nil {
		return fmt.Errorf("failed to marshal evidence: %w", err)
	}

	recommendationJSON, err := json.Marshal(candidate.Recommendation)
	if err != nil {
		return fmt.Errorf("failed to marshal recommendation: %w", err)
	}

	metadataJSON, err := json.Marshal(candidate.Metadata)
	if err != nil {
		return fmt.Errorf("failed to marshal metadata: %w", err)
	}

	_, err = r.db.ExecContext(ctx, query,
		candidate.ID,
		candidate.Title,
		candidate.Description,
		string(candidate.Priority),
		candidate.Score,
		string(evidenceJSON),
		string(recommendationJSON),
		string(candidate.Status),
		candidate.CreatedAt,
		candidate.UpdatedAt,
		candidate.ReviewedBy,
		candidate.ReviewedAt,
		candidate.Tags,
		string(metadataJSON),
	)

	return err
}

// GetCandidates retrieves automation candidates
func (r *ClickHouseRepository) GetCandidates(ctx context.Context, filters CandidateFilters) ([]AutomationCandidate, error) {
	query := `
		SELECT id, title, description, priority, score, evidence, recommendation,
			   status, created_at, updated_at, reviewed_by, reviewed_at, tags, metadata
		FROM automation_candidates WHERE 1=1
	`
	args := make([]interface{}, 0)

	// Apply filters
	if filters.Status != "" {
		query += " AND status = ?"
		args = append(args, string(filters.Status))
	}

	if filters.Priority != "" {
		query += " AND priority = ?"
		args = append(args, string(filters.Priority))
	}

	if filters.MinScore > 0 {
		query += " AND score >= ?"
		args = append(args, filters.MinScore)
	}

	if len(filters.Tags) > 0 {
		query += " AND hasAny(tags, ?)"
		args = append(args, filters.Tags)
	}

	if filters.ReviewedBy != "" {
		query += " AND reviewed_by = ?"
		args = append(args, filters.ReviewedBy)
	}

	if filters.Since != nil {
		query += " AND created_at >= ?"
		args = append(args, *filters.Since)
	}

	if filters.Until != nil {
		query += " AND created_at <= ?"
		args = append(args, *filters.Until)
	}

	// Add ordering and limit
	query += " ORDER BY score DESC, created_at DESC"
	if filters.Limit > 0 {
		query += " LIMIT ?"
		args = append(args, filters.Limit)
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var candidates []AutomationCandidate
	for rows.Next() {
		var candidate AutomationCandidate
		var evidenceJSON, recommendationJSON, metadataJSON string
		var reviewedAt sql.NullTime

		err := rows.Scan(
			&candidate.ID,
			&candidate.Title,
			&candidate.Description,
			&candidate.Priority,
			&candidate.Score,
			&evidenceJSON,
			&recommendationJSON,
			&candidate.Status,
			&candidate.CreatedAt,
			&candidate.UpdatedAt,
			&candidate.ReviewedBy,
			&reviewedAt,
			&candidate.Tags,
			&metadataJSON,
		)
		if err != nil {
			return nil, err
		}

		if reviewedAt.Valid {
			candidate.ReviewedAt = &reviewedAt.Time
		}

		if err := json.Unmarshal([]byte(evidenceJSON), &candidate.Evidence); err != nil {
			return nil, fmt.Errorf("failed to unmarshal evidence: %w", err)
		}

		if err := json.Unmarshal([]byte(recommendationJSON), &candidate.Recommendation); err != nil {
			return nil, fmt.Errorf("failed to unmarshal recommendation: %w", err)
		}

		if err := json.Unmarshal([]byte(metadataJSON), &candidate.Metadata); err != nil {
			return nil, fmt.Errorf("failed to unmarshal metadata: %w", err)
		}

		candidates = append(candidates, candidate)
	}

	return candidates, rows.Err()
}

// UpdateCandidateStatus updates candidate review status
func (r *ClickHouseRepository) UpdateCandidateStatus(ctx context.Context, id string, status CandidateStatus, reviewedBy string) error {
	query := `
		ALTER TABLE automation_candidates 
		UPDATE status = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
		WHERE id = ?
	`

	now := time.Now()
	_, err := r.db.ExecContext(ctx, query, string(status), reviewedBy, now, now, id)
	return err
}

// SaveMiningResult stores mining analysis results
func (r *ClickHouseRepository) SaveMiningResult(ctx context.Context, result *MiningResult) error {
	query := `
		INSERT INTO mining_results (
			id, timestamp, config, candidates, total_actions, analyzed_sequences,
			processing_time_ms, statistics
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`

	configJSON, err := json.Marshal(result.Config)
	if err != nil {
		return fmt.Errorf("failed to marshal config: %w", err)
	}

	candidatesJSON, err := json.Marshal(result.Candidates)
	if err != nil {
		return fmt.Errorf("failed to marshal candidates: %w", err)
	}

	statisticsJSON, err := json.Marshal(result.Statistics)
	if err != nil {
		return fmt.Errorf("failed to marshal statistics: %w", err)
	}

	_, err = r.db.ExecContext(ctx, query,
		result.ID,
		result.Timestamp,
		string(configJSON),
		string(candidatesJSON),
		result.TotalActions,
		result.AnalyzedSequences,
		int64(result.ProcessingTime.Milliseconds()),
		string(statisticsJSON),
	)

	return err
}

// GetMiningHistory retrieves historical mining results
func (r *ClickHouseRepository) GetMiningHistory(ctx context.Context, limit int) ([]MiningResult, error) {
	query := `
		SELECT id, timestamp, config, candidates, total_actions, analyzed_sequences,
			   processing_time_ms, statistics
		FROM mining_results 
		ORDER BY timestamp DESC
	`

	if limit > 0 {
		query += " LIMIT ?"
	}

	var rows *sql.Rows
	var err error

	if limit > 0 {
		rows, err = r.db.QueryContext(ctx, query, limit)
	} else {
		rows, err = r.db.QueryContext(ctx, query)
	}

	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []MiningResult
	for rows.Next() {
		var result MiningResult
		var configJSON, candidatesJSON, statisticsJSON string
		var processingTimeMs int64

		err := rows.Scan(
			&result.ID,
			&result.Timestamp,
			&configJSON,
			&candidatesJSON,
			&result.TotalActions,
			&result.AnalyzedSequences,
			&processingTimeMs,
			&statisticsJSON,
		)
		if err != nil {
			return nil, err
		}

		result.ProcessingTime = time.Duration(processingTimeMs) * time.Millisecond

		if err := json.Unmarshal([]byte(configJSON), &result.Config); err != nil {
			return nil, fmt.Errorf("failed to unmarshal config: %w", err)
		}

		if err := json.Unmarshal([]byte(candidatesJSON), &result.Candidates); err != nil {
			return nil, fmt.Errorf("failed to unmarshal candidates: %w", err)
		}

		if err := json.Unmarshal([]byte(statisticsJSON), &result.Statistics); err != nil {
			return nil, fmt.Errorf("failed to unmarshal statistics: %w", err)
		}

		results = append(results, result)
	}

	return results, rows.Err()
}

// createTables creates the necessary database tables
func (r *ClickHouseRepository) createTables() error {
	queries := []string{
		// Action logs table
		`CREATE TABLE IF NOT EXISTS action_logs (
			id String,
			user_id String,
			timestamp DateTime,
			action_type String,
			incident_id String,
			service_name String,
			parameters String,
			success Bool,
			duration_ms UInt64,
			comments String,
			tags Array(String),
			context String
		) ENGINE = MergeTree()
		ORDER BY (timestamp, user_id)
		PARTITION BY toDate(timestamp)
		TTL timestamp + INTERVAL 180 DAY DELETE`,

		// Automation candidates table
		`CREATE TABLE IF NOT EXISTS automation_candidates (
			id String,
			title String,
			description String,
			priority String,
			score Float64,
			evidence String,
			recommendation String,
			status String,
			created_at DateTime,
			updated_at DateTime,
			reviewed_by String,
			reviewed_at Nullable(DateTime),
			tags Array(String),
			metadata String
		) ENGINE = MergeTree()
		ORDER BY (status, score, created_at)
		PARTITION BY toYYYYMM(created_at)`,

		// Mining results table
		`CREATE TABLE IF NOT EXISTS mining_results (
			id String,
			timestamp DateTime,
			config String,
			candidates String,
			total_actions UInt32,
			analyzed_sequences UInt32,
			processing_time_ms UInt64,
			statistics String
		) ENGINE = MergeTree()
		ORDER BY timestamp
		PARTITION BY toYYYYMM(timestamp)
		TTL timestamp + INTERVAL 365 DAY DELETE`,
	}

	for _, query := range queries {
		if _, err := r.db.Exec(query); err != nil {
			return fmt.Errorf("failed to create table: %w", err)
		}
	}

	return nil
}

// Close closes the database connection
func (r *ClickHouseRepository) Close() error {
	return r.db.Close()
}
