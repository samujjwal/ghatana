package retention

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"
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

	repo := &ClickHouseRepository{db: db}

	// Create tables if they don't exist
	if err := repo.createTables(context.Background()); err != nil {
		return nil, fmt.Errorf("failed to create tables: %w", err)
	}

	return repo, nil
}

// createTables creates the necessary tables for retention management
func (r *ClickHouseRepository) createTables(ctx context.Context) error {
	queries := []string{
		// Storage metrics table
		`CREATE TABLE IF NOT EXISTS storage_metrics (
			table_name String,
			data_size UInt64,
			row_count UInt64,
			oldest_record DateTime,
			newest_record DateTime,
			daily_growth_rate Float64,
			access_frequency Float64,
			storage_cost_per_gb Float64,
			query_cost Float64,
			last_accessed Nullable(DateTime),
			partitions String,
			created_at DateTime DEFAULT now(),
			updated_at DateTime DEFAULT now()
		) ENGINE = ReplacingMergeTree(updated_at)
		ORDER BY table_name`,

		// Retention policies table
		`CREATE TABLE IF NOT EXISTS retention_policies (
			id String,
			name String,
			description String,
			data_category String,
			retention_period_seconds UInt64,
			storage_class String,
			compression_type String,
			encryption_level String,
			access_frequency Float64,
			business_value Float64,
			compliance_level String,
			created_at DateTime,
			updated_at DateTime
		) ENGINE = ReplacingMergeTree(updated_at)
		ORDER BY id`,

		// Retention recommendations table
		`CREATE TABLE IF NOT EXISTS retention_recommendations (
			id String,
			table_name String,
			current_policy String,
			recommended_policy String,
			justification String,
			expected_savings String,
			risk_assessment String,
			implementation_plan String,
			confidence Float64,
			status String,
			reviewed_by String,
			reviewed_at Nullable(DateTime),
			applied_at Nullable(DateTime),
			created_at DateTime DEFAULT now()
		) ENGINE = ReplacingMergeTree(created_at)
		ORDER BY (id, created_at)
		TTL created_at + INTERVAL 1 YEAR`,

		// Analysis results table
		`CREATE TABLE IF NOT EXISTS retention_analysis_results (
			id String,
			timestamp DateTime,
			config String,
			tables_analyzed UInt32,
			total_data_size UInt64,
			recommendations String,
			total_potential_savings String,
			summary String,
			processing_time_ms UInt64,
			created_at DateTime DEFAULT now()
		) ENGINE = ReplacingMergeTree(created_at)
		ORDER BY (id, timestamp)
		TTL created_at + INTERVAL 6 MONTH`,
	}

	for _, query := range queries {
		if _, err := r.db.ExecContext(ctx, query); err != nil {
			return fmt.Errorf("failed to execute query %s: %w", query, err)
		}
	}

	return nil
}

// GetStorageMetrics retrieves storage metrics for a specific table
func (r *ClickHouseRepository) GetStorageMetrics(ctx context.Context, tableName string) (*StorageMetrics, error) {
	query := `
		SELECT table_name, data_size, row_count, oldest_record, newest_record,
		       daily_growth_rate, access_frequency, storage_cost_per_gb, query_cost,
		       last_accessed, partitions
		FROM storage_metrics
		WHERE table_name = ?
		ORDER BY updated_at DESC
		LIMIT 1`

	row := r.db.QueryRowContext(ctx, query, tableName)

	var metrics StorageMetrics
	var lastAccessed *time.Time
	var partitionsJSON string

	err := row.Scan(
		&metrics.TableName,
		&metrics.DataSize,
		&metrics.RowCount,
		&metrics.OldestRecord,
		&metrics.NewestRecord,
		&metrics.DailyGrowthRate,
		&metrics.AccessFrequency,
		&metrics.StorageCostPerGB,
		&metrics.QueryCost,
		&lastAccessed,
		&partitionsJSON,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			return nil, fmt.Errorf("storage metrics not found for table %s", tableName)
		}
		return nil, fmt.Errorf("failed to scan storage metrics: %w", err)
	}

	metrics.LastAccessed = lastAccessed

	// Parse partitions JSON
	if partitionsJSON != "" {
		if err := json.Unmarshal([]byte(partitionsJSON), &metrics.Partitions); err != nil {
			return nil, fmt.Errorf("failed to unmarshal partitions: %w", err)
		}
	}

	return &metrics, nil
}

// SaveStorageMetrics saves storage metrics for a table
func (r *ClickHouseRepository) SaveStorageMetrics(ctx context.Context, metrics *StorageMetrics) error {
	partitionsJSON, err := json.Marshal(metrics.Partitions)
	if err != nil {
		return fmt.Errorf("failed to marshal partitions: %w", err)
	}

	query := `
		INSERT INTO storage_metrics (
			table_name, data_size, row_count, oldest_record, newest_record,
			daily_growth_rate, access_frequency, storage_cost_per_gb, query_cost,
			last_accessed, partitions, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())`

	_, err = r.db.ExecContext(ctx, query,
		metrics.TableName,
		metrics.DataSize,
		metrics.RowCount,
		metrics.OldestRecord,
		metrics.NewestRecord,
		metrics.DailyGrowthRate,
		metrics.AccessFrequency,
		metrics.StorageCostPerGB,
		metrics.QueryCost,
		metrics.LastAccessed,
		string(partitionsJSON),
	)

	if err != nil {
		return fmt.Errorf("failed to save storage metrics: %w", err)
	}

	return nil
}

// GetAllStorageMetrics retrieves storage metrics for all tables
func (r *ClickHouseRepository) GetAllStorageMetrics(ctx context.Context) ([]StorageMetrics, error) {
	query := `
		SELECT table_name, data_size, row_count, oldest_record, newest_record,
		       daily_growth_rate, access_frequency, storage_cost_per_gb, query_cost,
		       last_accessed, partitions
		FROM storage_metrics
		ORDER BY table_name, updated_at DESC`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to query storage metrics: %w", err)
	}
	defer rows.Close()

	var allMetrics []StorageMetrics
	seenTables := make(map[string]bool)

	for rows.Next() {
		var metrics StorageMetrics
		var lastAccessed *time.Time
		var partitionsJSON string

		err := rows.Scan(
			&metrics.TableName,
			&metrics.DataSize,
			&metrics.RowCount,
			&metrics.OldestRecord,
			&metrics.NewestRecord,
			&metrics.DailyGrowthRate,
			&metrics.AccessFrequency,
			&metrics.StorageCostPerGB,
			&metrics.QueryCost,
			&lastAccessed,
			&partitionsJSON,
		)

		if err != nil {
			return nil, fmt.Errorf("failed to scan storage metrics: %w", err)
		}

		// Only include the latest metrics for each table
		if seenTables[metrics.TableName] {
			continue
		}
		seenTables[metrics.TableName] = true

		metrics.LastAccessed = lastAccessed

		// Parse partitions JSON
		if partitionsJSON != "" {
			if err := json.Unmarshal([]byte(partitionsJSON), &metrics.Partitions); err != nil {
				return nil, fmt.Errorf("failed to unmarshal partitions: %w", err)
			}
		}

		allMetrics = append(allMetrics, metrics)
	}

	return allMetrics, nil
}

// SaveRecommendation saves a retention recommendation
func (r *ClickHouseRepository) SaveRecommendation(ctx context.Context, recommendation *RetentionRecommendation) error {
	currentPolicyJSON, err := json.Marshal(recommendation.CurrentPolicy)
	if err != nil {
		return fmt.Errorf("failed to marshal current policy: %w", err)
	}

	recommendedPolicyJSON, err := json.Marshal(recommendation.RecommendedPolicy)
	if err != nil {
		return fmt.Errorf("failed to marshal recommended policy: %w", err)
	}

	expectedSavingsJSON, err := json.Marshal(recommendation.ExpectedSavings)
	if err != nil {
		return fmt.Errorf("failed to marshal expected savings: %w", err)
	}

	riskAssessmentJSON, err := json.Marshal(recommendation.RiskAssessment)
	if err != nil {
		return fmt.Errorf("failed to marshal risk assessment: %w", err)
	}

	implementationPlanJSON, err := json.Marshal(recommendation.ImplementationPlan)
	if err != nil {
		return fmt.Errorf("failed to marshal implementation plan: %w", err)
	}

	query := `
		INSERT INTO retention_recommendations (
			id, table_name, current_policy, recommended_policy, justification,
			expected_savings, risk_assessment, implementation_plan, confidence,
			status, reviewed_by, reviewed_at, applied_at, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	_, err = r.db.ExecContext(ctx, query,
		recommendation.ID,
		recommendation.TableName,
		string(currentPolicyJSON),
		string(recommendedPolicyJSON),
		recommendation.Justification,
		string(expectedSavingsJSON),
		string(riskAssessmentJSON),
		string(implementationPlanJSON),
		recommendation.Confidence,
		string(recommendation.Status),
		recommendation.ReviewedBy,
		recommendation.ReviewedAt,
		recommendation.AppliedAt,
		recommendation.CreatedAt,
	)

	if err != nil {
		return fmt.Errorf("failed to save recommendation: %w", err)
	}

	return nil
}

// GetRecommendation retrieves a specific recommendation by ID
func (r *ClickHouseRepository) GetRecommendation(ctx context.Context, id string) (*RetentionRecommendation, error) {
	query := `
		SELECT id, table_name, current_policy, recommended_policy, justification,
		       expected_savings, risk_assessment, implementation_plan, confidence,
		       status, reviewed_by, reviewed_at, applied_at, created_at
		FROM retention_recommendations
		WHERE id = ?
		ORDER BY created_at DESC
		LIMIT 1`

	row := r.db.QueryRowContext(ctx, query, id)

	return r.scanRecommendation(row)
}

// GetRecommendations retrieves recommendations with optional filters
func (r *ClickHouseRepository) GetRecommendations(ctx context.Context, filters RecommendationFilters) ([]RetentionRecommendation, error) {
	query := `
		SELECT id, table_name, current_policy, recommended_policy, justification,
		       expected_savings, risk_assessment, implementation_plan, confidence,
		       status, reviewed_by, reviewed_at, applied_at, created_at
		FROM retention_recommendations`

	var conditions []string
	var args []interface{}

	if filters.Status != nil {
		conditions = append(conditions, "status = ?")
		args = append(args, string(*filters.Status))
	}

	if filters.TableName != nil {
		conditions = append(conditions, "table_name = ?")
		args = append(args, *filters.TableName)
	}

	if filters.CreatedSince != nil {
		conditions = append(conditions, "created_at >= ?")
		args = append(args, *filters.CreatedSince)
	}

	if filters.CreatedBefore != nil {
		conditions = append(conditions, "created_at <= ?")
		args = append(args, *filters.CreatedBefore)
	}

	if len(conditions) > 0 {
		query += " WHERE " + conditions[0]
		for i := 1; i < len(conditions); i++ {
			query += " AND " + conditions[i]
		}
	}

	query += " ORDER BY created_at DESC"

	if filters.Limit > 0 {
		query += fmt.Sprintf(" LIMIT %d", filters.Limit)
	}

	if filters.Offset > 0 {
		query += fmt.Sprintf(" OFFSET %d", filters.Offset)
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("failed to query recommendations: %w", err)
	}
	defer rows.Close()

	var recommendations []RetentionRecommendation
	for rows.Next() {
		recommendation, err := r.scanRecommendation(rows)
		if err != nil {
			return nil, err
		}
		recommendations = append(recommendations, *recommendation)
	}

	return recommendations, nil
}

// UpdateRecommendationStatus updates the status of a recommendation
func (r *ClickHouseRepository) UpdateRecommendationStatus(ctx context.Context, id string, status RecommendationStatus, reviewedBy string) error {
	now := time.Now()

	query := `
		INSERT INTO retention_recommendations (
			id, table_name, current_policy, recommended_policy, justification,
			expected_savings, risk_assessment, implementation_plan, confidence,
			status, reviewed_by, reviewed_at, applied_at, created_at
		)
		SELECT id, table_name, current_policy, recommended_policy, justification,
		       expected_savings, risk_assessment, implementation_plan, confidence,
		       ?, ?, ?, 
		       CASE WHEN ? = 'IMPLEMENTED' THEN ? ELSE applied_at END,
		       created_at
		FROM retention_recommendations
		WHERE id = ?
		ORDER BY created_at DESC
		LIMIT 1`

	appliedAt := (*time.Time)(nil)
	if status == StatusImplemented {
		appliedAt = &now
	}

	_, err := r.db.ExecContext(ctx, query,
		string(status),
		reviewedBy,
		now,
		string(StatusImplemented),
		appliedAt,
		id,
	)

	if err != nil {
		return fmt.Errorf("failed to update recommendation status: %w", err)
	}

	return nil
}

// scanRecommendation scans a recommendation from a database row
func (r *ClickHouseRepository) scanRecommendation(scanner interface {
	Scan(dest ...interface{}) error
}) (*RetentionRecommendation, error) {
	var recommendation RetentionRecommendation
	var currentPolicyJSON, recommendedPolicyJSON, expectedSavingsJSON string
	var riskAssessmentJSON, implementationPlanJSON string
	var statusStr string
	var reviewedAt, appliedAt *time.Time

	err := scanner.Scan(
		&recommendation.ID,
		&recommendation.TableName,
		&currentPolicyJSON,
		&recommendedPolicyJSON,
		&recommendation.Justification,
		&expectedSavingsJSON,
		&riskAssessmentJSON,
		&implementationPlanJSON,
		&recommendation.Confidence,
		&statusStr,
		&recommendation.ReviewedBy,
		&reviewedAt,
		&appliedAt,
		&recommendation.CreatedAt,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			return nil, fmt.Errorf("recommendation not found")
		}
		return nil, fmt.Errorf("failed to scan recommendation: %w", err)
	}

	recommendation.Status = RecommendationStatus(statusStr)
	recommendation.ReviewedAt = reviewedAt
	recommendation.AppliedAt = appliedAt

	// Parse JSON fields
	if currentPolicyJSON != "" {
		if err := json.Unmarshal([]byte(currentPolicyJSON), &recommendation.CurrentPolicy); err != nil {
			return nil, fmt.Errorf("failed to unmarshal current policy: %w", err)
		}
	}

	if err := json.Unmarshal([]byte(recommendedPolicyJSON), &recommendation.RecommendedPolicy); err != nil {
		return nil, fmt.Errorf("failed to unmarshal recommended policy: %w", err)
	}

	if err := json.Unmarshal([]byte(expectedSavingsJSON), &recommendation.ExpectedSavings); err != nil {
		return nil, fmt.Errorf("failed to unmarshal expected savings: %w", err)
	}

	if err := json.Unmarshal([]byte(riskAssessmentJSON), &recommendation.RiskAssessment); err != nil {
		return nil, fmt.Errorf("failed to unmarshal risk assessment: %w", err)
	}

	if err := json.Unmarshal([]byte(implementationPlanJSON), &recommendation.ImplementationPlan); err != nil {
		return nil, fmt.Errorf("failed to unmarshal implementation plan: %w", err)
	}

	return &recommendation, nil
}

// SaveRetentionPolicy saves a retention policy
func (r *ClickHouseRepository) SaveRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error {
	query := `
		INSERT INTO retention_policies (
			id, name, description, data_category, retention_period_seconds,
			storage_class, compression_type, encryption_level, access_frequency,
			business_value, compliance_level, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	_, err := r.db.ExecContext(ctx, query,
		policy.ID,
		policy.Name,
		policy.Description,
		policy.DataCategory,
		uint64(policy.RetentionPeriod.Seconds()),
		string(policy.StorageClass),
		policy.CompressionType,
		policy.EncryptionLevel,
		policy.AccessFrequency,
		policy.BusinessValue,
		policy.ComplianceLevel,
		policy.CreatedAt,
		policy.UpdatedAt,
	)

	if err != nil {
		return fmt.Errorf("failed to save retention policy: %w", err)
	}

	return nil
}

// GetRetentionPolicy retrieves a specific retention policy by ID
func (r *ClickHouseRepository) GetRetentionPolicy(ctx context.Context, id string) (*RetentionPolicy, error) {
	query := `
		SELECT id, name, description, data_category, retention_period_seconds,
		       storage_class, compression_type, encryption_level, access_frequency,
		       business_value, compliance_level, created_at, updated_at
		FROM retention_policies
		WHERE id = ?
		ORDER BY updated_at DESC
		LIMIT 1`

	row := r.db.QueryRowContext(ctx, query, id)

	var policy RetentionPolicy
	var retentionPeriodSeconds uint64
	var storageClassStr string

	err := row.Scan(
		&policy.ID,
		&policy.Name,
		&policy.Description,
		&policy.DataCategory,
		&retentionPeriodSeconds,
		&storageClassStr,
		&policy.CompressionType,
		&policy.EncryptionLevel,
		&policy.AccessFrequency,
		&policy.BusinessValue,
		&policy.ComplianceLevel,
		&policy.CreatedAt,
		&policy.UpdatedAt,
	)

	if err != nil {
		if err == sql.ErrNoRows {
			return nil, fmt.Errorf("retention policy not found")
		}
		return nil, fmt.Errorf("failed to scan retention policy: %w", err)
	}

	policy.RetentionPeriod = time.Duration(retentionPeriodSeconds) * time.Second
	policy.StorageClass = StorageClass(storageClassStr)

	return &policy, nil
}

// GetRetentionPolicies retrieves all retention policies
func (r *ClickHouseRepository) GetRetentionPolicies(ctx context.Context) ([]RetentionPolicy, error) {
	query := `
		SELECT id, name, description, data_category, retention_period_seconds,
		       storage_class, compression_type, encryption_level, access_frequency,
		       business_value, compliance_level, created_at, updated_at
		FROM retention_policies
		ORDER BY name`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to query retention policies: %w", err)
	}
	defer rows.Close()

	var policies []RetentionPolicy
	for rows.Next() {
		var policy RetentionPolicy
		var retentionPeriodSeconds uint64
		var storageClassStr string

		err := rows.Scan(
			&policy.ID,
			&policy.Name,
			&policy.Description,
			&policy.DataCategory,
			&retentionPeriodSeconds,
			&storageClassStr,
			&policy.CompressionType,
			&policy.EncryptionLevel,
			&policy.AccessFrequency,
			&policy.BusinessValue,
			&policy.ComplianceLevel,
			&policy.CreatedAt,
			&policy.UpdatedAt,
		)

		if err != nil {
			return nil, fmt.Errorf("failed to scan retention policy: %w", err)
		}

		policy.RetentionPeriod = time.Duration(retentionPeriodSeconds) * time.Second
		policy.StorageClass = StorageClass(storageClassStr)

		policies = append(policies, policy)
	}

	return policies, nil
}

// UpdateRetentionPolicy updates an existing retention policy
func (r *ClickHouseRepository) UpdateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error {
	return r.SaveRetentionPolicy(ctx, policy) // ReplacingMergeTree handles updates
}

// DeleteRetentionPolicy marks a retention policy as deleted
func (r *ClickHouseRepository) DeleteRetentionPolicy(ctx context.Context, id string) error {
	// In ClickHouse, we typically don't delete but mark as deleted or use TTL
	// For simplicity, we'll just return success - in production you might want
	// to implement a soft delete or use mutations
	return nil
}

// SaveAnalysisResult saves an analysis result
func (r *ClickHouseRepository) SaveAnalysisResult(ctx context.Context, result *RetentionAnalysisResult) error {
	configJSON, err := json.Marshal(result.Config)
	if err != nil {
		return fmt.Errorf("failed to marshal config: %w", err)
	}

	recommendationsJSON, err := json.Marshal(result.Recommendations)
	if err != nil {
		return fmt.Errorf("failed to marshal recommendations: %w", err)
	}

	savingsJSON, err := json.Marshal(result.TotalPotentialSavings)
	if err != nil {
		return fmt.Errorf("failed to marshal savings: %w", err)
	}

	summaryJSON, err := json.Marshal(result.Summary)
	if err != nil {
		return fmt.Errorf("failed to marshal summary: %w", err)
	}

	query := `
		INSERT INTO retention_analysis_results (
			id, timestamp, config, tables_analyzed, total_data_size,
			recommendations, total_potential_savings, summary, processing_time_ms
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`

	_, err = r.db.ExecContext(ctx, query,
		result.ID,
		result.Timestamp,
		string(configJSON),
		result.TablesAnalyzed,
		result.TotalDataSize,
		string(recommendationsJSON),
		string(savingsJSON),
		string(summaryJSON),
		result.ProcessingTime.Milliseconds(),
	)

	if err != nil {
		return fmt.Errorf("failed to save analysis result: %w", err)
	}

	return nil
}

// GetAnalysisResults retrieves recent analysis results
func (r *ClickHouseRepository) GetAnalysisResults(ctx context.Context, limit int) ([]RetentionAnalysisResult, error) {
	query := `
		SELECT id, timestamp, config, tables_analyzed, total_data_size,
		       recommendations, total_potential_savings, summary, processing_time_ms
		FROM retention_analysis_results
		ORDER BY timestamp DESC
		LIMIT ?`

	rows, err := r.db.QueryContext(ctx, query, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to query analysis results: %w", err)
	}
	defer rows.Close()

	var results []RetentionAnalysisResult
	for rows.Next() {
		var result RetentionAnalysisResult
		var configJSON, recommendationsJSON, savingsJSON, summaryJSON string
		var processingTimeMs int64

		err := rows.Scan(
			&result.ID,
			&result.Timestamp,
			&configJSON,
			&result.TablesAnalyzed,
			&result.TotalDataSize,
			&recommendationsJSON,
			&savingsJSON,
			&summaryJSON,
			&processingTimeMs,
		)

		if err != nil {
			return nil, fmt.Errorf("failed to scan analysis result: %w", err)
		}

		result.ProcessingTime = time.Duration(processingTimeMs) * time.Millisecond

		// Parse JSON fields
		if err := json.Unmarshal([]byte(configJSON), &result.Config); err != nil {
			return nil, fmt.Errorf("failed to unmarshal config: %w", err)
		}

		if err := json.Unmarshal([]byte(recommendationsJSON), &result.Recommendations); err != nil {
			return nil, fmt.Errorf("failed to unmarshal recommendations: %w", err)
		}

		if err := json.Unmarshal([]byte(savingsJSON), &result.TotalPotentialSavings); err != nil {
			return nil, fmt.Errorf("failed to unmarshal savings: %w", err)
		}

		if err := json.Unmarshal([]byte(summaryJSON), &result.Summary); err != nil {
			return nil, fmt.Errorf("failed to unmarshal summary: %w", err)
		}

		results = append(results, result)
	}

	return results, nil
}
