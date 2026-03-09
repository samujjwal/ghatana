package retention

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"sort"
	"strings"
	"time"
)

// RetentionAdvisor implements the Advisor interface
type RetentionAdvisor struct {
	repository Repository
	config     RetentionAnalysisConfig
}

// NewRetentionAdvisor creates a new retention advisor instance
func NewRetentionAdvisor(repository Repository) *RetentionAdvisor {
	return &RetentionAdvisor{
		repository: repository,
		config:     getDefaultAnalysisConfig(),
	}
}

// AnalyzeRetention analyzes current data retention and generates recommendations
func (a *RetentionAdvisor) AnalyzeRetention(ctx context.Context, config RetentionAnalysisConfig) (*RetentionAnalysisResult, error) {
	startTime := time.Now()

	result := &RetentionAnalysisResult{
		ID:        generateID(),
		Timestamp: startTime,
		Config:    config,
		Summary: AnalysisSummary{
			HighPriorityRecommendations: 0,
			LowRiskRecommendations:      0,
			TotalStorageOptimizable:     0,
			AverageDataAge:              0,
			UnderutilizedTables:         0,
			ComplianceIssues:            0,
		},
	}

	// Get all storage metrics
	allMetrics, err := a.repository.GetAllStorageMetrics(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get storage metrics: %w", err)
	}

	var recommendations []RetentionRecommendation
	var totalDataSize int64
	var totalSavings CostSavings
	var totalDataAgeDays float64

	// Analyze each table
	for _, metrics := range allMetrics {
		// Skip excluded tables
		if contains(config.ExcludeTables, metrics.TableName) {
			continue
		}

		totalDataSize += metrics.DataSize

		// Calculate data age
		dataAge := time.Since(metrics.OldestRecord)
		totalDataAgeDays += dataAge.Hours() / 24

		// Generate recommendations for this table
		tableRecommendations, err := a.analyzeTable(ctx, &metrics, config)
		if err != nil {
			// Log error but continue processing other tables
			continue
		}

		recommendations = append(recommendations, tableRecommendations...)

		// Update summary statistics
		for _, rec := range tableRecommendations {
			totalSavings.MonthlySavings += rec.ExpectedSavings.MonthlySavings
			totalSavings.AnnualSavings += rec.ExpectedSavings.AnnualSavings
			totalSavings.StorageReduced += rec.ExpectedSavings.StorageReduced

			if rec.RiskAssessment.OverallRisk == RiskLevelLow {
				result.Summary.LowRiskRecommendations++
			}

			if rec.ExpectedSavings.MonthlySavings > 1000 { // High priority threshold
				result.Summary.HighPriorityRecommendations++
			}

			if metrics.AccessFrequency < config.AccessThreshold {
				result.Summary.UnderutilizedTables++
			}
		}
	}

	// Sort recommendations by expected savings (descending)
	sort.Slice(recommendations, func(i, j int) bool {
		return recommendations[i].ExpectedSavings.MonthlySavings > recommendations[j].ExpectedSavings.MonthlySavings
	})

	// Calculate final statistics
	result.TablesAnalyzed = len(allMetrics)
	result.TotalDataSize = totalDataSize
	result.Recommendations = recommendations
	result.TotalPotentialSavings = totalSavings
	result.Summary.TotalStorageOptimizable = totalSavings.StorageReduced
	if len(allMetrics) > 0 {
		result.Summary.AverageDataAge = totalDataAgeDays / float64(len(allMetrics))
	}
	result.ProcessingTime = time.Since(startTime)

	// Save analysis result
	if err := a.repository.SaveAnalysisResult(ctx, result); err != nil {
		return nil, fmt.Errorf("failed to save analysis result: %w", err)
	}

	return result, nil
}

// analyzeTable generates retention recommendations for a specific table
func (a *RetentionAdvisor) analyzeTable(ctx context.Context, metrics *StorageMetrics, config RetentionAnalysisConfig) ([]RetentionRecommendation, error) {
	var recommendations []RetentionRecommendation

	// Calculate data characteristics
	dataAge := time.Since(metrics.OldestRecord)
	storageGB := float64(metrics.DataSize) / (1024 * 1024 * 1024)
	monthlyStorageCost := storageGB * metrics.StorageCostPerGB

	// Skip if data is too new or cost savings too small
	if dataAge < config.MinDataAge || monthlyStorageCost < config.CostThreshold {
		return recommendations, nil
	}

	// Analyze access patterns (used implicitly in recommendation functions)

	// Check if data can be moved to colder storage
	if coldStorageRec := a.analyzeColdStorageOpportunity(metrics, config); coldStorageRec != nil {
		recommendations = append(recommendations, *coldStorageRec)
	}

	// Check if old data can be archived
	if archiveRec := a.analyzeArchiveOpportunity(metrics, config); archiveRec != nil {
		recommendations = append(recommendations, *archiveRec)
	}

	// Check if data can be compressed more aggressively
	if compressionRec := a.analyzeCompressionOpportunity(metrics, config); compressionRec != nil {
		recommendations = append(recommendations, *compressionRec)
	}

	// Check if old data can be deleted based on retention policies
	if deletionRec := a.analyzeDeletionOpportunity(metrics, config); deletionRec != nil {
		recommendations = append(recommendations, *deletionRec)
	}

	return recommendations, nil
}

// analyzeColdStorageOpportunity checks if data can be moved to colder storage tiers
func (a *RetentionAdvisor) analyzeColdStorageOpportunity(metrics *StorageMetrics, config RetentionAnalysisConfig) *RetentionRecommendation {
	// Only recommend if data is underutilized
	if metrics.AccessFrequency >= config.AccessThreshold {
		return nil
	}

	currentCost := float64(metrics.DataSize) / (1024 * 1024 * 1024) * metrics.StorageCostPerGB
	coldStorageCost := currentCost * 0.4 // Cold storage typically 60% cheaper
	monthlySavings := currentCost - coldStorageCost

	if monthlySavings < config.CostThreshold {
		return nil
	}

	return &RetentionRecommendation{
		ID:        generateID(),
		TableName: metrics.TableName,
		RecommendedPolicy: &RetentionPolicy{
			ID:              generateID(),
			Name:            fmt.Sprintf("Cold Storage Policy - %s", metrics.TableName),
			Description:     "Move underutilized data to cold storage tier",
			DataCategory:    "historical",
			StorageClass:    StorageClassCold,
			CompressionType: "lz4",
			BusinessValue:   0.3, // Lower business value for cold data
		},
		Justification: fmt.Sprintf("Table accessed %.1f times per day (threshold: %.1f). Moving to cold storage reduces costs with minimal impact.",
			metrics.AccessFrequency, config.AccessThreshold),
		ExpectedSavings: CostSavings{
			MonthlySavings:     monthlySavings,
			AnnualSavings:      monthlySavings * 12,
			StorageReduced:     0,              // Same storage, different tier
			ImplementationCost: 50.0,           // Minimal implementation cost
			PaybackPeriod:      24 * time.Hour, // Immediate savings
		},
		RiskAssessment: RiskAssessment{
			OverallRisk:     RiskLevelLow,
			DataLossRisk:    RiskLevelLow,
			ComplianceRisk:  RiskLevelLow,
			PerformanceRisk: RiskLevelMedium,
			RiskFactors:     []string{"Slightly increased query latency"},
			MitigationSteps: []string{"Monitor query performance", "Implement query caching"},
		},
		ImplementationPlan: ImplementationPlan{
			Steps: []ImplementationStep{
				{
					ID:          generateID(),
					Description: "Create cold storage policy",
					Duration:    15 * time.Minute,
					Risk:        RiskLevelLow,
				},
				{
					ID:          generateID(),
					Description: "Move data to cold storage tier",
					Duration:    2 * time.Hour,
					Risk:        RiskLevelLow,
				},
				{
					ID:          generateID(),
					Description: "Verify data accessibility",
					Duration:    30 * time.Minute,
					Risk:        RiskLevelLow,
				},
			},
			EstimatedTime: 3 * time.Hour,
			RequiredTools: []string{"ClickHouse admin", "monitoring tools"},
			RollbackPlan:  []string{"Move data back to hot storage", "Update storage policy"},
		},
		Confidence: 0.85,
		CreatedAt:  time.Now(),
		Status:     StatusPending,
	}
}

// analyzeArchiveOpportunity checks if old data can be archived
func (a *RetentionAdvisor) analyzeArchiveOpportunity(metrics *StorageMetrics, config RetentionAnalysisConfig) *RetentionRecommendation {
	dataAge := time.Since(metrics.OldestRecord)

	// Only archive data older than 2 years
	if dataAge < 2*365*24*time.Hour {
		return nil
	}

	// Calculate savings from archival
	currentCost := float64(metrics.DataSize) / (1024 * 1024 * 1024) * metrics.StorageCostPerGB
	archiveCost := currentCost * 0.05 // Archive storage is ~95% cheaper
	monthlySavings := currentCost - archiveCost

	if monthlySavings < config.CostThreshold {
		return nil
	}

	return &RetentionRecommendation{
		ID:        generateID(),
		TableName: metrics.TableName,
		RecommendedPolicy: &RetentionPolicy{
			ID:              generateID(),
			Name:            fmt.Sprintf("Archive Policy - %s", metrics.TableName),
			Description:     "Archive data older than 2 years",
			DataCategory:    "archival",
			StorageClass:    StorageClassArchive,
			CompressionType: "zstd",
			BusinessValue:   0.1, // Low business value for archived data
		},
		Justification: fmt.Sprintf("Data is %.0f days old, rarely accessed. Archival reduces storage costs significantly.",
			dataAge.Hours()/24),
		ExpectedSavings: CostSavings{
			MonthlySavings:     monthlySavings,
			AnnualSavings:      monthlySavings * 12,
			StorageReduced:     int64(float64(metrics.DataSize) * 0.8), // Compression reduces size
			ImplementationCost: 200.0,
			PaybackPeriod:      7 * 24 * time.Hour, // One week payback
		},
		RiskAssessment: RiskAssessment{
			OverallRisk:     RiskLevelMedium,
			DataLossRisk:    RiskLevelLow,
			ComplianceRisk:  RiskLevelMedium,
			PerformanceRisk: RiskLevelHigh,
			RiskFactors:     []string{"Long retrieval times", "Compliance retention requirements"},
			MitigationSteps: []string{"Verify compliance requirements", "Implement retrieval SLA monitoring"},
		},
		ImplementationPlan: ImplementationPlan{
			Steps: []ImplementationStep{
				{
					ID:          generateID(),
					Description: "Verify compliance requirements",
					Duration:    1 * time.Hour,
					Risk:        RiskLevelMedium,
				},
				{
					ID:          generateID(),
					Description: "Create archive storage policy",
					Duration:    30 * time.Minute,
					Risk:        RiskLevelLow,
				},
				{
					ID:          generateID(),
					Description: "Archive old data",
					Duration:    4 * time.Hour,
					Risk:        RiskLevelMedium,
				},
			},
			EstimatedTime: 6 * time.Hour,
			RequiredTools: []string{"ClickHouse admin", "compliance tools", "archive storage"},
			RollbackPlan:  []string{"Restore from archive", "Update retention policy"},
		},
		Confidence: 0.75,
		CreatedAt:  time.Now(),
		Status:     StatusPending,
	}
}

// analyzeCompressionOpportunity checks if better compression can reduce storage costs
func (a *RetentionAdvisor) analyzeCompressionOpportunity(metrics *StorageMetrics, config RetentionAnalysisConfig) *RetentionRecommendation {
	// Calculate average compression ratio across partitions
	var totalCompressionRatio float64
	partitionCount := 0

	for _, partition := range metrics.Partitions {
		if partition.CompressionRatio > 0 {
			totalCompressionRatio += partition.CompressionRatio
			partitionCount++
		}
	}

	if partitionCount == 0 {
		return nil
	}

	avgCompressionRatio := totalCompressionRatio / float64(partitionCount)

	// Only recommend if compression ratio is poor (< 50%)
	if avgCompressionRatio >= 0.5 {
		return nil
	}

	// Estimate savings from better compression
	potentialSavings := float64(metrics.DataSize) * (0.7 - avgCompressionRatio) // Target 70% compression
	if potentialSavings < float64(config.CostThreshold*1024*1024*1024) {        // Convert to bytes
		return nil
	}

	monthlySavings := (potentialSavings / (1024 * 1024 * 1024)) * metrics.StorageCostPerGB

	return &RetentionRecommendation{
		ID:        generateID(),
		TableName: metrics.TableName,
		RecommendedPolicy: &RetentionPolicy{
			ID:              generateID(),
			Name:            fmt.Sprintf("Enhanced Compression Policy - %s", metrics.TableName),
			Description:     "Apply more aggressive compression to reduce storage",
			DataCategory:    "optimized",
			CompressionType: "zstd",
			BusinessValue:   0.8, // High business value due to cost optimization
		},
		Justification: fmt.Sprintf("Current compression ratio is %.1f%%. Enhanced compression can reduce storage by %.1fGB.",
			avgCompressionRatio*100, potentialSavings/(1024*1024*1024)),
		ExpectedSavings: CostSavings{
			MonthlySavings:     monthlySavings,
			AnnualSavings:      monthlySavings * 12,
			StorageReduced:     int64(potentialSavings),
			ImplementationCost: 100.0,
			PaybackPeriod:      3 * 24 * time.Hour, // 3 days payback
		},
		RiskAssessment: RiskAssessment{
			OverallRisk:     RiskLevelLow,
			DataLossRisk:    RiskLevelLow,
			ComplianceRisk:  RiskLevelLow,
			PerformanceRisk: RiskLevelMedium,
			RiskFactors:     []string{"Temporary increased CPU usage during recompression"},
			MitigationSteps: []string{"Schedule during off-peak hours", "Monitor CPU usage"},
		},
		ImplementationPlan: ImplementationPlan{
			Steps: []ImplementationStep{
				{
					ID:          generateID(),
					Description: "Update compression settings",
					Duration:    15 * time.Minute,
					Risk:        RiskLevelLow,
				},
				{
					ID:          generateID(),
					Description: "Recompress existing data",
					Duration:    6 * time.Hour,
					Risk:        RiskLevelMedium,
				},
			},
			EstimatedTime: 7 * time.Hour,
			RequiredTools: []string{"ClickHouse admin"},
			RollbackPlan:  []string{"Restore original compression settings"},
		},
		Confidence: 0.9,
		CreatedAt:  time.Now(),
		Status:     StatusPending,
	}
}

// analyzeDeletionOpportunity checks if old data can be safely deleted
func (a *RetentionAdvisor) analyzeDeletionOpportunity(metrics *StorageMetrics, config RetentionAnalysisConfig) *RetentionRecommendation {
	dataAge := time.Since(metrics.OldestRecord)

	// Only recommend deletion for very old, unused data (>5 years, no access in 1 year)
	if dataAge < 5*365*24*time.Hour {
		return nil
	}

	if metrics.LastAccessed != nil && time.Since(*metrics.LastAccessed) < 365*24*time.Hour {
		return nil
	}

	// Calculate potential savings
	monthlySavings := float64(metrics.DataSize) / (1024 * 1024 * 1024) * metrics.StorageCostPerGB

	if monthlySavings < config.CostThreshold {
		return nil
	}

	return &RetentionRecommendation{
		ID:        generateID(),
		TableName: metrics.TableName,
		RecommendedPolicy: &RetentionPolicy{
			ID:              generateID(),
			Name:            fmt.Sprintf("Data Deletion Policy - %s", metrics.TableName),
			Description:     "Delete very old, unused data",
			DataCategory:    "expired",
			RetentionPeriod: 5 * 365 * 24 * time.Hour, // 5 years
			BusinessValue:   0.05,                     // Very low business value
		},
		Justification: fmt.Sprintf("Data is %.0f days old with no access in %.0f days. Safe for deletion.",
			dataAge.Hours()/24, time.Since(*metrics.LastAccessed).Hours()/24),
		ExpectedSavings: CostSavings{
			MonthlySavings:     monthlySavings,
			AnnualSavings:      monthlySavings * 12,
			StorageReduced:     metrics.DataSize,
			ImplementationCost: 0.0, // No cost to delete
			PaybackPeriod:      0,   // Immediate savings
		},
		RiskAssessment: RiskAssessment{
			OverallRisk:     RiskLevelHigh,
			DataLossRisk:    RiskLevelHigh,
			ComplianceRisk:  RiskLevelHigh,
			PerformanceRisk: RiskLevelLow,
			RiskFactors:     []string{"Permanent data loss", "Potential compliance violations", "Irreversible action"},
			MitigationSteps: []string{"Verify compliance requirements", "Create backup before deletion", "Get explicit approval"},
		},
		ImplementationPlan: ImplementationPlan{
			Steps: []ImplementationStep{
				{
					ID:          generateID(),
					Description: "Verify compliance and legal requirements",
					Duration:    4 * time.Hour,
					Risk:        RiskLevelHigh,
				},
				{
					ID:          generateID(),
					Description: "Create backup of data to be deleted",
					Duration:    2 * time.Hour,
					Risk:        RiskLevelMedium,
				},
				{
					ID:          generateID(),
					Description: "Delete old data",
					Duration:    1 * time.Hour,
					Risk:        RiskLevelHigh,
				},
			},
			EstimatedTime: 8 * time.Hour,
			RequiredTools: []string{"ClickHouse admin", "backup tools", "compliance verification"},
			RollbackPlan:  []string{"Restore from backup (if available)"},
		},
		Confidence: 0.6, // Lower confidence due to high risk
		CreatedAt:  time.Now(),
		Status:     StatusPending,
	}
}

// GetRecommendations retrieves retention recommendations with optional filters
func (a *RetentionAdvisor) GetRecommendations(ctx context.Context, filters RecommendationFilters) ([]RetentionRecommendation, error) {
	return a.repository.GetRecommendations(ctx, filters)
}

// GetStorageMetrics retrieves current storage metrics for analysis
func (a *RetentionAdvisor) GetStorageMetrics(ctx context.Context, tableName string) (*StorageMetrics, error) {
	return a.repository.GetStorageMetrics(ctx, tableName)
}

// ReviewRecommendation updates the status of a recommendation after review
func (a *RetentionAdvisor) ReviewRecommendation(ctx context.Context, id string, status RecommendationStatus, reviewedBy string) error {
	return a.repository.UpdateRecommendationStatus(ctx, id, status, reviewedBy)
}

// ImplementRecommendation applies a retention recommendation
func (a *RetentionAdvisor) ImplementRecommendation(ctx context.Context, id string) (*ImplementationResult, error) {
	recommendation, err := a.repository.GetRecommendation(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to get recommendation: %w", err)
	}

	if recommendation.Status != StatusApproved {
		return nil, fmt.Errorf("recommendation must be approved before implementation")
	}

	startTime := time.Now()
	result := &ImplementationResult{
		RecommendationID: id,
		Success:          false,
		ExecutedSteps:    []ImplementationStep{},
		Errors:           []string{},
		Timestamp:        startTime,
	}

	// Execute implementation steps
	for _, step := range recommendation.ImplementationPlan.Steps {
		// For now, simulate step execution
		// In a real implementation, this would execute actual commands
		result.ExecutedSteps = append(result.ExecutedSteps, step)

		// Simulate some processing time
		time.Sleep(100 * time.Millisecond)
	}

	result.Success = true
	result.Duration = time.Since(startTime)

	// Update recommendation status
	if err := a.repository.UpdateRecommendationStatus(ctx, id, StatusImplemented, "system"); err != nil {
		result.Errors = append(result.Errors, fmt.Sprintf("Failed to update status: %v", err))
	}

	return result, nil
}

// GetRetentionPolicies retrieves current retention policies
func (a *RetentionAdvisor) GetRetentionPolicies(ctx context.Context) ([]RetentionPolicy, error) {
	return a.repository.GetRetentionPolicies(ctx)
}

// CreateRetentionPolicy creates a new retention policy
func (a *RetentionAdvisor) CreateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error {
	if policy.ID == "" {
		policy.ID = generateID()
	}
	if policy.CreatedAt.IsZero() {
		policy.CreatedAt = time.Now()
	}
	policy.UpdatedAt = time.Now()

	return a.repository.SaveRetentionPolicy(ctx, policy)
}

// UpdateRetentionPolicy updates an existing retention policy
func (a *RetentionAdvisor) UpdateRetentionPolicy(ctx context.Context, policy *RetentionPolicy) error {
	policy.UpdatedAt = time.Now()
	return a.repository.UpdateRetentionPolicy(ctx, policy)
}

// Helper functions

func getDefaultAnalysisConfig() RetentionAnalysisConfig {
	return RetentionAnalysisConfig{
		AnalysisPeriod:      90 * 24 * time.Hour, // 90 days
		MinDataAge:          30 * 24 * time.Hour, // 30 days
		CostThreshold:       100.0,               // $100/month
		AccessThreshold:     1.0,                 // 1 access per day
		BusinessValueWeight: 0.3,
		ComplianceWeight:    0.4,
		CostWeight:          0.3,
		RiskTolerance:       RiskLevelMedium,
		ExcludeTables:       []string{"system", "information_schema"},
		IncludeCategories:   []string{"events", "metrics", "logs"},
	}
}

func generateID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

func contains(slice []string, item string) bool {
	for _, s := range slice {
		if strings.EqualFold(s, item) {
			return true
		}
	}
	return false
}
