package retention

import (
	"context"
	"time"
)

// StorageClass represents different storage tiers and their characteristics
type StorageClass string

const (
	StorageClassHot     StorageClass = "HOT"     // Frequently accessed data
	StorageClassWarm    StorageClass = "WARM"    // Occasionally accessed data
	StorageClassCold    StorageClass = "COLD"    // Rarely accessed data
	StorageClassArchive StorageClass = "ARCHIVE" // Long-term archival
)

// RetentionPolicy defines how long data should be kept and where
type RetentionPolicy struct {
	ID              string        `json:"id"`
	Name            string        `json:"name"`
	Description     string        `json:"description"`
	DataCategory    string        `json:"data_category"`
	RetentionPeriod time.Duration `json:"retention_period"`
	StorageClass    StorageClass  `json:"storage_class"`
	CompressionType string        `json:"compression_type"`
	EncryptionLevel string        `json:"encryption_level"`
	AccessFrequency float64       `json:"access_frequency"` // accesses per day
	BusinessValue   float64       `json:"business_value"`   // 0-1 scale
	ComplianceLevel string        `json:"compliance_level"` // NONE, SOX, HIPAA, GDPR
	CreatedAt       time.Time     `json:"created_at"`
	UpdatedAt       time.Time     `json:"updated_at"`
}

// StorageMetrics represents storage usage and cost information
type StorageMetrics struct {
	TableName        string             `json:"table_name"`
	DataSize         int64              `json:"data_size"` // bytes
	RowCount         int64              `json:"row_count"`
	OldestRecord     time.Time          `json:"oldest_record"`
	NewestRecord     time.Time          `json:"newest_record"`
	DailyGrowthRate  float64            `json:"daily_growth_rate"` // bytes per day
	AccessFrequency  float64            `json:"access_frequency"`  // queries per day
	StorageCostPerGB float64            `json:"storage_cost_per_gb"`
	QueryCost        float64            `json:"query_cost"` // per query
	LastAccessed     *time.Time         `json:"last_accessed"`
	Partitions       []PartitionMetrics `json:"partitions"`
}

// PartitionMetrics represents metrics for individual partitions
type PartitionMetrics struct {
	PartitionID      string       `json:"partition_id"`
	DataSize         int64        `json:"data_size"`
	RowCount         int64        `json:"row_count"`
	MinDate          time.Time    `json:"min_date"`
	MaxDate          time.Time    `json:"max_date"`
	AccessCount      int64        `json:"access_count"`
	LastAccessed     *time.Time   `json:"last_accessed"`
	StorageClass     StorageClass `json:"storage_class"`
	CompressionRatio float64      `json:"compression_ratio"`
}

// RetentionRecommendation represents an AI-generated storage optimization suggestion
type RetentionRecommendation struct {
	ID                 string               `json:"id"`
	TableName          string               `json:"table_name"`
	CurrentPolicy      *RetentionPolicy     `json:"current_policy"`
	RecommendedPolicy  *RetentionPolicy     `json:"recommended_policy"`
	Justification      string               `json:"justification"`
	ExpectedSavings    CostSavings          `json:"expected_savings"`
	RiskAssessment     RiskAssessment       `json:"risk_assessment"`
	ImplementationPlan ImplementationPlan   `json:"implementation_plan"`
	Confidence         float64              `json:"confidence"` // 0-1 scale
	CreatedAt          time.Time            `json:"created_at"`
	Status             RecommendationStatus `json:"status"`
	ReviewedBy         string               `json:"reviewed_by,omitempty"`
	ReviewedAt         *time.Time           `json:"reviewed_at,omitempty"`
	AppliedAt          *time.Time           `json:"applied_at,omitempty"`
}

// CostSavings represents the financial impact of a retention recommendation
type CostSavings struct {
	MonthlySavings     float64       `json:"monthly_savings"`     // USD
	AnnualSavings      float64       `json:"annual_savings"`      // USD
	StorageReduced     int64         `json:"storage_reduced"`     // bytes
	QueryCostChange    float64       `json:"query_cost_change"`   // USD change in query costs
	ImplementationCost float64       `json:"implementation_cost"` // one-time cost
	PaybackPeriod      time.Duration `json:"payback_period"`
}

// RiskAssessment evaluates the risks of applying a retention recommendation
type RiskAssessment struct {
	OverallRisk     RiskLevel `json:"overall_risk"`
	DataLossRisk    RiskLevel `json:"data_loss_risk"`
	ComplianceRisk  RiskLevel `json:"compliance_risk"`
	PerformanceRisk RiskLevel `json:"performance_risk"`
	RiskFactors     []string  `json:"risk_factors"`
	MitigationSteps []string  `json:"mitigation_steps"`
}

// RiskLevel represents the level of risk
type RiskLevel string

const (
	RiskLevelLow    RiskLevel = "LOW"
	RiskLevelMedium RiskLevel = "MEDIUM"
	RiskLevelHigh   RiskLevel = "HIGH"
)

// ImplementationPlan defines the steps to apply a retention recommendation
type ImplementationPlan struct {
	Steps         []ImplementationStep `json:"steps"`
	EstimatedTime time.Duration        `json:"estimated_time"`
	RequiredTools []string             `json:"required_tools"`
	Prerequisites []string             `json:"prerequisites"`
	RollbackPlan  []string             `json:"rollback_plan"`
	TestingSteps  []string             `json:"testing_steps"`
}

// ImplementationStep represents a single step in the implementation plan
type ImplementationStep struct {
	ID           string        `json:"id"`
	Description  string        `json:"description"`
	Command      string        `json:"command,omitempty"`
	Duration     time.Duration `json:"duration"`
	Risk         RiskLevel     `json:"risk"`
	Dependencies []string      `json:"dependencies"`
}

// RecommendationStatus represents the status of a retention recommendation
type RecommendationStatus string

const (
	StatusPending     RecommendationStatus = "PENDING"
	StatusApproved    RecommendationStatus = "APPROVED"
	StatusRejected    RecommendationStatus = "REJECTED"
	StatusImplemented RecommendationStatus = "IMPLEMENTED"
	StatusFailed      RecommendationStatus = "FAILED"
)

// RetentionAnalysisConfig defines parameters for retention analysis
type RetentionAnalysisConfig struct {
	AnalysisPeriod      time.Duration `json:"analysis_period"`       // how far back to analyze
	MinDataAge          time.Duration `json:"min_data_age"`          // minimum age to consider for optimization
	CostThreshold       float64       `json:"cost_threshold"`        // minimum monthly savings to recommend
	AccessThreshold     float64       `json:"access_threshold"`      // queries per day threshold for "cold" data
	BusinessValueWeight float64       `json:"business_value_weight"` // weight for business value in scoring
	ComplianceWeight    float64       `json:"compliance_weight"`     // weight for compliance requirements
	CostWeight          float64       `json:"cost_weight"`           // weight for cost savings
	RiskTolerance       RiskLevel     `json:"risk_tolerance"`        // acceptable risk level
	ExcludeTables       []string      `json:"exclude_tables"`        // tables to skip
	IncludeCategories   []string      `json:"include_categories"`    // data categories to analyze
}

// Advisor interface defines the retention advisor service
type Advisor interface {
	// AnalyzeRetention analyzes current data retention and generates recommendations
	AnalyzeRetention(ctx context.Context, config RetentionAnalysisConfig) (*RetentionAnalysisResult, error)

	// GetRecommendations retrieves retention recommendations with optional filters
	GetRecommendations(ctx context.Context, filters RecommendationFilters) ([]RetentionRecommendation, error)

	// GetStorageMetrics retrieves current storage metrics for analysis
	GetStorageMetrics(ctx context.Context, tableName string) (*StorageMetrics, error)

	// ReviewRecommendation updates the status of a recommendation after review
	ReviewRecommendation(ctx context.Context, id string, status RecommendationStatus, reviewedBy string) error

	// ImplementRecommendation applies a retention recommendation
	ImplementRecommendation(ctx context.Context, id string) (*ImplementationResult, error)

	// GetRetentionPolicies retrieves current retention policies
	GetRetentionPolicies(ctx context.Context) ([]RetentionPolicy, error)

	// CreateRetentionPolicy creates a new retention policy
	CreateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error

	// UpdateRetentionPolicy updates an existing retention policy
	UpdateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error
}

// Repository interface defines storage operations for retention management
type Repository interface {
	// Storage metrics operations
	GetStorageMetrics(ctx context.Context, tableName string) (*StorageMetrics, error)
	SaveStorageMetrics(ctx context.Context, metrics *StorageMetrics) error
	GetAllStorageMetrics(ctx context.Context) ([]StorageMetrics, error)

	// Retention recommendation operations
	SaveRecommendation(ctx context.Context, recommendation *RetentionRecommendation) error
	GetRecommendation(ctx context.Context, id string) (*RetentionRecommendation, error)
	GetRecommendations(ctx context.Context, filters RecommendationFilters) ([]RetentionRecommendation, error)
	UpdateRecommendationStatus(ctx context.Context, id string, status RecommendationStatus, reviewedBy string) error

	// Retention policy operations
	SaveRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error
	GetRetentionPolicy(ctx context.Context, id string) (*RetentionPolicy, error)
	GetRetentionPolicies(ctx context.Context) ([]RetentionPolicy, error)
	UpdateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error
	DeleteRetentionPolicy(ctx context.Context, id string) error

	// Analysis results operations
	SaveAnalysisResult(ctx context.Context, result *RetentionAnalysisResult) error
	GetAnalysisResults(ctx context.Context, limit int) ([]RetentionAnalysisResult, error)
}

// RetentionAnalysisResult represents the result of a retention analysis
type RetentionAnalysisResult struct {
	ID                    string                    `json:"id"`
	Timestamp             time.Time                 `json:"timestamp"`
	Config                RetentionAnalysisConfig   `json:"config"`
	TablesAnalyzed        int                       `json:"tables_analyzed"`
	TotalDataSize         int64                     `json:"total_data_size"`
	Recommendations       []RetentionRecommendation `json:"recommendations"`
	TotalPotentialSavings CostSavings               `json:"total_potential_savings"`
	Summary               AnalysisSummary           `json:"summary"`
	ProcessingTime        time.Duration             `json:"processing_time"`
}

// AnalysisSummary provides high-level insights from the retention analysis
type AnalysisSummary struct {
	HighPriorityRecommendations int     `json:"high_priority_recommendations"`
	LowRiskRecommendations      int     `json:"low_risk_recommendations"`
	TotalStorageOptimizable     int64   `json:"total_storage_optimizable"`
	AverageDataAge              float64 `json:"average_data_age_days"`
	UnderutilizedTables         int     `json:"underutilized_tables"`
	ComplianceIssues            int     `json:"compliance_issues"`
}

// RecommendationFilters defines filters for querying recommendations
type RecommendationFilters struct {
	Status        *RecommendationStatus `json:"status,omitempty"`
	MinSavings    *float64              `json:"min_savings,omitempty"`
	MaxRisk       *RiskLevel            `json:"max_risk,omitempty"`
	TableName     *string               `json:"table_name,omitempty"`
	CreatedSince  *time.Time            `json:"created_since,omitempty"`
	CreatedBefore *time.Time            `json:"created_before,omitempty"`
	Limit         int                   `json:"limit,omitempty"`
	Offset        int                   `json:"offset,omitempty"`
}

// ImplementationResult represents the outcome of implementing a recommendation
type ImplementationResult struct {
	RecommendationID string               `json:"recommendation_id"`
	Success          bool                 `json:"success"`
	ActualSavings    *CostSavings         `json:"actual_savings,omitempty"`
	ExecutedSteps    []ImplementationStep `json:"executed_steps"`
	Errors           []string             `json:"errors,omitempty"`
	Duration         time.Duration        `json:"duration"`
	RollbackRequired bool                 `json:"rollback_required"`
	Timestamp        time.Time            `json:"timestamp"`
}
