package advisor

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strconv"
	"time"
)

// CostBenefitAdvisor provides cost/benefit analysis for retention and storage policies
// Implements Capability 3: Cost/Benefit Advisor Expansion from Horizontal Slice AI Plan #5
type CostBenefitAdvisor struct {
	costCalculator *CostCalculator
	metricsStore   MetricsStore
	config         AdvisorConfig
}

// AdvisorConfig configures the cost/benefit advisor
type AdvisorConfig struct {
	StorageCostPerGB       float64 `json:"storage_cost_per_gb"`      // $/GB/month
	EgressCostPerGB        float64 `json:"egress_cost_per_gb"`       // $/GB
	ComputeCostPerHour     float64 `json:"compute_cost_per_hour"`    // $/hour
	NetworkCostPerGB       float64 `json:"network_cost_per_gb"`      // $/GB
	RetentionAnalysisHours int     `json:"retention_analysis_hours"` // Hours to analyze
	SamplingRate           float64 `json:"sampling_rate"`            // 0.0-1.0
	CostThreshold          float64 `json:"cost_threshold"`           // $ threshold for recommendations
	ProjectionDays         int     `json:"projection_days"`          // Days to project costs
}

// CostBreakdown represents a detailed cost analysis
type CostBreakdown struct {
	TotalCost     float64            `json:"total_cost"`
	StorageCost   float64            `json:"storage_cost"`
	EgressCost    float64            `json:"egress_cost"`
	ComputeCost   float64            `json:"compute_cost"`
	NetworkCost   float64            `json:"network_cost"`
	Period        string             `json:"period"` // "daily", "monthly", "yearly"
	Currency      string             `json:"currency"`
	CostByService map[string]float64 `json:"cost_by_service"`
	CostByTenant  map[string]float64 `json:"cost_by_tenant"`
	Timestamp     time.Time          `json:"timestamp"`
}

// RetentionRecommendation represents a recommendation for data retention
type RetentionRecommendation struct {
	ID                   string        `json:"id"`
	DataType             string        `json:"data_type"`             // "logs", "metrics", "traces"
	CurrentRetention     int           `json:"current_retention"`     // Days
	RecommendedRetention int           `json:"recommended_retention"` // Days
	Reason               string        `json:"reason"`
	EstimatedSavings     CostBreakdown `json:"estimated_savings"`
	AccessPattern        AccessPattern `json:"access_pattern"`
	Priority             int           `json:"priority"`   // 1=High, 5=Low
	Confidence           float64       `json:"confidence"` // 0.0-1.0
	Impact               string        `json:"impact"`     // "low", "medium", "high"
	Tags                 []string      `json:"tags"`
	CreatedAt            time.Time     `json:"created_at"`
}

// AccessPattern represents how data is accessed over time
type AccessPattern struct {
	AccessesLast24h  int       `json:"accesses_last_24h"`
	AccessesLast7d   int       `json:"accesses_last_7d"`
	AccessesLast30d  int       `json:"accesses_last_30d"`
	AverageQuerySize float64   `json:"average_query_size_gb"`
	PeakAccessHour   int       `json:"peak_access_hour"`
	AccessTrend      string    `json:"access_trend"` // "increasing", "decreasing", "stable"
	LastAccessed     time.Time `json:"last_accessed"`
}

// EgressAnalysis represents network egress cost analysis
type EgressAnalysis struct {
	TotalEgressGB        float64                `json:"total_egress_gb"`
	EgressCost           float64                `json:"egress_cost"`
	EgressByRegion       map[string]float64     `json:"egress_by_region"`
	EgressByService      map[string]float64     `json:"egress_by_service"`
	TopConsumers         []EgressConsumer       `json:"top_consumers"`
	Recommendations      []EgressRecommendation `json:"recommendations"`
	Period               string                 `json:"period"`
	ProjectedMonthlyCost float64                `json:"projected_monthly_cost"`
}

// EgressConsumer represents a high egress cost consumer
type EgressConsumer struct {
	Service    string  `json:"service"`
	TenantID   string  `json:"tenant_id"`
	EgressGB   float64 `json:"egress_gb"`
	Cost       float64 `json:"cost"`
	Percentage float64 `json:"percentage"`
}

// EgressRecommendation represents a recommendation to reduce egress costs
type EgressRecommendation struct {
	Type             string   `json:"type"` // "caching", "compression", "regional"
	Description      string   `json:"description"`
	EstimatedSaving  float64  `json:"estimated_saving"`
	Effort           string   `json:"effort"`   // "low", "medium", "high"
	Timeline         string   `json:"timeline"` // "immediate", "short-term", "long-term"
	AffectedServices []string `json:"affected_services"`
}

// CostOptimizationSuggestion represents a cost optimization opportunity
type CostOptimizationSuggestion struct {
	ID                   string    `json:"id"`
	Type                 string    `json:"type"` // "retention", "compression", "tiering", "egress"
	Title                string    `json:"title"`
	Description          string    `json:"description"`
	CurrentCost          float64   `json:"current_cost"`
	OptimizedCost        float64   `json:"optimized_cost"`
	Savings              float64   `json:"savings"`
	SavingsPercent       float64   `json:"savings_percent"`
	ImplementationEffort string    `json:"implementation_effort"` // "low", "medium", "high"
	RiskLevel            string    `json:"risk_level"`            // "low", "medium", "high"
	Timeline             string    `json:"timeline"`
	Priority             int       `json:"priority"`
	Tags                 []string  `json:"tags"`
	CreatedAt            time.Time `json:"created_at"`
}

// CostCalculator handles cost calculations
type CostCalculator struct {
	config AdvisorConfig
}

// MetricsStore interface for accessing metrics data
type MetricsStore interface {
	GetStorageMetrics(ctx context.Context, hours int) (StorageMetrics, error)
	GetEgressMetrics(ctx context.Context, hours int) (EgressMetrics, error)
	GetAccessPatterns(ctx context.Context, dataType string, hours int) (AccessPattern, error)
}

// StorageMetrics represents storage usage data
type StorageMetrics struct {
	TotalSizeGB        float64            `json:"total_size_gb"`
	SizeByService      map[string]float64 `json:"size_by_service"`
	SizeByTenant       map[string]float64 `json:"size_by_tenant"`
	SizeByDataType     map[string]float64 `json:"size_by_data_type"`
	GrowthRateGBPerDay float64            `json:"growth_rate_gb_per_day"`
	CompressionRatio   float64            `json:"compression_ratio"`
}

// EgressMetrics represents network egress data
type EgressMetrics struct {
	TotalEgressGB      float64            `json:"total_egress_gb"`
	EgressByRegion     map[string]float64 `json:"egress_by_region"`
	EgressByService    map[string]float64 `json:"egress_by_service"`
	EgressByTenant     map[string]float64 `json:"egress_by_tenant"`
	PeakEgressHour     int                `json:"peak_egress_hour"`
	AverageQuerySizeGB float64            `json:"average_query_size_gb"`
}

// NewCostBenefitAdvisor creates a new cost/benefit advisor
func NewCostBenefitAdvisor(metricsStore MetricsStore) *CostBenefitAdvisor {
	config := AdvisorConfig{
		StorageCostPerGB:       0.023, // AWS S3 Standard pricing
		EgressCostPerGB:        0.09,  // AWS data transfer pricing
		ComputeCostPerHour:     0.10,  // Compute cost
		NetworkCostPerGB:       0.02,  // Internal network costs
		RetentionAnalysisHours: 168,   // 1 week
		SamplingRate:           1.0,   // 100% sampling
		CostThreshold:          100.0, // $100 threshold
		ProjectionDays:         30,    // 30 days projection
	}

	return &CostBenefitAdvisor{
		costCalculator: &CostCalculator{config: config},
		metricsStore:   metricsStore,
		config:         config,
	}
}

// AnalyzeRetentionCosts analyzes current retention costs and provides recommendations
func (c *CostBenefitAdvisor) AnalyzeRetentionCosts(ctx context.Context) ([]RetentionRecommendation, error) {
	recommendations := []RetentionRecommendation{}

	// Get current storage metrics
	storageMetrics, err := c.metricsStore.GetStorageMetrics(ctx, c.config.RetentionAnalysisHours)
	if err != nil {
		return nil, fmt.Errorf("failed to get storage metrics: %w", err)
	}

	// Analyze each data type
	dataTypes := []string{"logs", "metrics", "traces"}
	for _, dataType := range dataTypes {
		recommendation, err := c.analyzeDataTypeRetention(ctx, dataType, storageMetrics)
		if err != nil {
			continue // Skip on error
		}
		if recommendation != nil {
			recommendations = append(recommendations, *recommendation)
		}
	}

	// Sort by estimated savings (highest first)
	sort.Slice(recommendations, func(i, j int) bool {
		return recommendations[i].EstimatedSavings.TotalCost > recommendations[j].EstimatedSavings.TotalCost
	})

	return recommendations, nil
}

// analyzeDataTypeRetention analyzes retention for a specific data type
func (c *CostBenefitAdvisor) analyzeDataTypeRetention(ctx context.Context, dataType string,
	storageMetrics StorageMetrics) (*RetentionRecommendation, error) {

	// Get access patterns for this data type
	accessPattern, err := c.metricsStore.GetAccessPatterns(ctx, dataType, c.config.RetentionAnalysisHours)
	if err != nil {
		return nil, err
	}

	// Calculate current costs
	currentSizeGB := storageMetrics.SizeByDataType[dataType]
	if currentSizeGB == 0 {
		return nil, nil // No data for this type
	}

	currentMonthlyCost := c.costCalculator.calculateStorageCost(currentSizeGB, 30)

	// Determine recommended retention based on access patterns
	currentRetention := 90 // Default assumption
	recommendedRetention := c.calculateOptimalRetention(accessPattern, dataType)

	if recommendedRetention >= currentRetention {
		return nil, nil // No optimization opportunity
	}

	// Calculate savings
	savingsRatio := float64(currentRetention-recommendedRetention) / float64(currentRetention)
	monthlySavings := currentMonthlyCost * savingsRatio

	if monthlySavings < c.config.CostThreshold {
		return nil, nil // Savings below threshold
	}

	recommendation := &RetentionRecommendation{
		ID:                   fmt.Sprintf("retention-%s-%d", dataType, time.Now().Unix()),
		DataType:             dataType,
		CurrentRetention:     currentRetention,
		RecommendedRetention: recommendedRetention,
		Reason:               c.generateRetentionReason(accessPattern),
		EstimatedSavings: CostBreakdown{
			TotalCost:   monthlySavings,
			StorageCost: monthlySavings,
			Period:      "monthly",
			Currency:    "USD",
			Timestamp:   time.Now(),
		},
		AccessPattern: accessPattern,
		Priority:      c.calculatePriority(monthlySavings),
		Confidence:    c.calculateConfidence(accessPattern),
		Impact:        c.calculateImpact(accessPattern),
		Tags:          []string{dataType, "retention", "cost-optimization"},
		CreatedAt:     time.Now(),
	}

	return recommendation, nil
}

// AnalyzeEgressCosts analyzes network egress costs and provides optimization recommendations
func (c *CostBenefitAdvisor) AnalyzeEgressCosts(ctx context.Context) (*EgressAnalysis, error) {
	// Get egress metrics
	egressMetrics, err := c.metricsStore.GetEgressMetrics(ctx, c.config.RetentionAnalysisHours)
	if err != nil {
		return nil, fmt.Errorf("failed to get egress metrics: %w", err)
	}

	// Calculate costs
	totalCost := egressMetrics.TotalEgressGB * c.config.EgressCostPerGB
	projectedMonthlyCost := totalCost * 30 / float64(c.config.RetentionAnalysisHours/24)

	// Find top consumers
	topConsumers := c.identifyTopEgressConsumers(egressMetrics)

	// Generate recommendations
	recommendations := c.generateEgressRecommendations(egressMetrics, topConsumers)

	analysis := &EgressAnalysis{
		TotalEgressGB:        egressMetrics.TotalEgressGB,
		EgressCost:           totalCost,
		EgressByRegion:       egressMetrics.EgressByRegion,
		EgressByService:      egressMetrics.EgressByService,
		TopConsumers:         topConsumers,
		Recommendations:      recommendations,
		Period:               fmt.Sprintf("%d hours", c.config.RetentionAnalysisHours),
		ProjectedMonthlyCost: projectedMonthlyCost,
	}

	return analysis, nil
}

// GenerateOptimizationSuggestions generates comprehensive cost optimization suggestions
func (c *CostBenefitAdvisor) GenerateOptimizationSuggestions(ctx context.Context) ([]CostOptimizationSuggestion, error) {
	suggestions := []CostOptimizationSuggestion{}

	// Get retention recommendations
	retentionRecs, err := c.AnalyzeRetentionCosts(ctx)
	if err == nil {
		for _, rec := range retentionRecs {
			suggestion := CostOptimizationSuggestion{
				ID:                   fmt.Sprintf("opt-retention-%s", rec.ID),
				Type:                 "retention",
				Title:                fmt.Sprintf("Optimize %s retention", rec.DataType),
				Description:          fmt.Sprintf("Reduce %s retention from %d to %d days", rec.DataType, rec.CurrentRetention, rec.RecommendedRetention),
				CurrentCost:          rec.EstimatedSavings.TotalCost / 0.3, // Reverse calculate current cost
				OptimizedCost:        rec.EstimatedSavings.TotalCost / 0.3 * 0.7,
				Savings:              rec.EstimatedSavings.TotalCost,
				SavingsPercent:       30.0, // Simplified
				ImplementationEffort: "low",
				RiskLevel:            rec.Impact,
				Timeline:             "immediate",
				Priority:             rec.Priority,
				Tags:                 rec.Tags,
				CreatedAt:            time.Now(),
			}
			suggestions = append(suggestions, suggestion)
		}
	}

	// Get egress recommendations
	egressAnalysis, err := c.AnalyzeEgressCosts(ctx)
	if err == nil {
		for _, rec := range egressAnalysis.Recommendations {
			suggestion := CostOptimizationSuggestion{
				ID:                   fmt.Sprintf("opt-egress-%d", time.Now().UnixNano()),
				Type:                 "egress",
				Title:                rec.Description,
				Description:          fmt.Sprintf("Implement %s optimization", rec.Type),
				CurrentCost:          rec.EstimatedSaving / 0.2, // Reverse calculate
				OptimizedCost:        rec.EstimatedSaving / 0.2 * 0.8,
				Savings:              rec.EstimatedSaving,
				SavingsPercent:       20.0, // Simplified
				ImplementationEffort: rec.Effort,
				RiskLevel:            "medium",
				Timeline:             rec.Timeline,
				Priority:             2,
				Tags:                 []string{"egress", "network", "cost-optimization"},
				CreatedAt:            time.Now(),
			}
			suggestions = append(suggestions, suggestion)
		}
	}

	// Add strategic optimization suggestions
	strategicSuggestions := c.generateStrategicOptimizations(ctx)
	suggestions = append(suggestions, strategicSuggestions...)

	// Sort by savings (highest first)
	sort.Slice(suggestions, func(i, j int) bool {
		return suggestions[i].Savings > suggestions[j].Savings
	})

	return suggestions, nil
}

// Helper methods

func (c *CostCalculator) calculateStorageCost(sizeGB float64, days int) float64 {
	return sizeGB * c.config.StorageCostPerGB * float64(days) / 30.0
}

func (c *CostBenefitAdvisor) calculateOptimalRetention(pattern AccessPattern, dataType string) int {
	// Base retention on access patterns
	if pattern.AccessesLast7d == 0 {
		return 7 // Very low access, keep only 1 week
	}
	if pattern.AccessesLast30d < 5 {
		return 30 // Low access, keep 1 month
	}
	if pattern.AccessTrend == "decreasing" {
		return 60 // Decreasing access, keep 2 months
	}

	// Data type specific defaults
	switch dataType {
	case "logs":
		return 90 // 3 months for logs
	case "metrics":
		return 180 // 6 months for metrics
	case "traces":
		return 30 // 1 month for traces
	default:
		return 90
	}
}

func (c *CostBenefitAdvisor) generateRetentionReason(pattern AccessPattern) string {
	if pattern.AccessesLast7d == 0 {
		return "No access in the last 7 days, indicating low value for long-term retention"
	}
	if pattern.AccessTrend == "decreasing" {
		return "Access trend is decreasing, suggesting reduced need for extended retention"
	}
	if pattern.AccessesLast30d < pattern.AccessesLast7d*4 {
		return "Most access occurs within the first week, limited value in longer retention"
	}
	return "Based on access patterns, shorter retention period would be sufficient"
}

func (c *CostBenefitAdvisor) calculatePriority(savings float64) int {
	if savings > 1000 {
		return 1 // High priority
	} else if savings > 500 {
		return 2 // Medium-high priority
	} else if savings > 100 {
		return 3 // Medium priority
	} else {
		return 4 // Low priority
	}
}

func (c *CostBenefitAdvisor) calculateConfidence(pattern AccessPattern) float64 {
	confidence := 0.7 // Base confidence

	// Higher confidence if we have more data points
	if pattern.AccessesLast30d > 50 {
		confidence += 0.2
	}

	// Higher confidence if trend is clear
	if pattern.AccessTrend != "stable" {
		confidence += 0.1
	}

	return math.Min(confidence, 1.0)
}

func (c *CostBenefitAdvisor) calculateImpact(pattern AccessPattern) string {
	if pattern.AccessesLast30d == 0 {
		return "low"
	} else if pattern.AccessesLast30d < 10 {
		return "medium"
	} else {
		return "high"
	}
}

func (c *CostBenefitAdvisor) identifyTopEgressConsumers(metrics EgressMetrics) []EgressConsumer {
	consumers := []EgressConsumer{}

	// Convert service egress to consumers
	for service, egress := range metrics.EgressByService {
		cost := egress * c.config.EgressCostPerGB
		percentage := egress / metrics.TotalEgressGB * 100

		consumer := EgressConsumer{
			Service:    service,
			EgressGB:   egress,
			Cost:       cost,
			Percentage: percentage,
		}
		consumers = append(consumers, consumer)
	}

	// Sort by egress volume
	sort.Slice(consumers, func(i, j int) bool {
		return consumers[i].EgressGB > consumers[j].EgressGB
	})

	// Return top 10
	if len(consumers) > 10 {
		consumers = consumers[:10]
	}

	return consumers
}

func (c *CostBenefitAdvisor) generateEgressRecommendations(metrics EgressMetrics,
	consumers []EgressConsumer) []EgressRecommendation {

	recommendations := []EgressRecommendation{}

	// Caching recommendation
	if metrics.TotalEgressGB > 100 { // Threshold for caching
		cachingSavings := metrics.TotalEgressGB * 0.3 * c.config.EgressCostPerGB
		recommendations = append(recommendations, EgressRecommendation{
			Type:             "caching",
			Description:      "Implement response caching to reduce repeated data transfers",
			EstimatedSaving:  cachingSavings,
			Effort:           "medium",
			Timeline:         "short-term",
			AffectedServices: []string{"api-server", "dashboard"},
		})
	}

	// Compression recommendation
	if metrics.AverageQuerySizeGB > 0.1 { // Large average query size
		compressionSavings := metrics.TotalEgressGB * 0.6 * c.config.EgressCostPerGB
		recommendations = append(recommendations, EgressRecommendation{
			Type:             "compression",
			Description:      "Enable data compression to reduce transfer sizes",
			EstimatedSaving:  compressionSavings,
			Effort:           "low",
			Timeline:         "immediate",
			AffectedServices: []string{"api-server"},
		})
	}

	// Regional optimization
	maxRegionEgress := 0.0
	for _, egress := range metrics.EgressByRegion {
		if egress > maxRegionEgress {
			maxRegionEgress = egress
		}
	}

	if maxRegionEgress > metrics.TotalEgressGB*0.7 { // One region dominates
		regionalSavings := maxRegionEgress * 0.4 * c.config.EgressCostPerGB
		recommendations = append(recommendations, EgressRecommendation{
			Type:             "regional",
			Description:      "Deploy regional caches to reduce cross-region transfers",
			EstimatedSaving:  regionalSavings,
			Effort:           "high",
			Timeline:         "long-term",
			AffectedServices: []string{"cdn", "cache"},
		})
	}

	return recommendations
}

func (c *CostBenefitAdvisor) generateStrategicOptimizations(ctx context.Context) []CostOptimizationSuggestion {
	suggestions := []CostOptimizationSuggestion{
		{
			ID:                   "opt-strategic-compression",
			Type:                 "compression",
			Title:                "Implement Universal Data Compression",
			Description:          "Deploy compression across all data pipelines to reduce storage and transfer costs",
			CurrentCost:          1000.0,
			OptimizedCost:        400.0,
			Savings:              600.0,
			SavingsPercent:       60.0,
			ImplementationEffort: "medium",
			RiskLevel:            "low",
			Timeline:             "short-term",
			Priority:             1,
			Tags:                 []string{"compression", "strategic", "infrastructure"},
			CreatedAt:            time.Now(),
		},
		{
			ID:                   "opt-strategic-tiering",
			Type:                 "tiering",
			Title:                "Implement Intelligent Data Tiering",
			Description:          "Automatically move older data to cheaper storage tiers based on access patterns",
			CurrentCost:          800.0,
			OptimizedCost:        320.0,
			Savings:              480.0,
			SavingsPercent:       60.0,
			ImplementationEffort: "high",
			RiskLevel:            "medium",
			Timeline:             "long-term",
			Priority:             2,
			Tags:                 []string{"tiering", "strategic", "storage"},
			CreatedAt:            time.Now(),
		},
	}

	return suggestions
}

// HTTP Handlers

// HandleGetRetentionRecommendations handles GET /advisor/retention
func (c *CostBenefitAdvisor) HandleGetRetentionRecommendations(w http.ResponseWriter, r *http.Request) {
	recommendations, err := c.AnalyzeRetentionCosts(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to analyze retention costs: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"recommendations": recommendations,
		"total":           len(recommendations),
		"analyzed_at":     time.Now().UTC(),
	})
}

// HandleGetEgressAnalysis handles GET /advisor/egress
func (c *CostBenefitAdvisor) HandleGetEgressAnalysis(w http.ResponseWriter, r *http.Request) {
	analysis, err := c.AnalyzeEgressCosts(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to analyze egress costs: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(analysis)
}

// HandleGetOptimizationSuggestions handles GET /advisor/optimize
func (c *CostBenefitAdvisor) HandleGetOptimizationSuggestions(w http.ResponseWriter, r *http.Request) {
	suggestions, err := c.GenerateOptimizationSuggestions(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to generate optimization suggestions: %v", err), http.StatusInternalServerError)
		return
	}

	// Apply filters
	if optimizationType := r.URL.Query().Get("type"); optimizationType != "" {
		filtered := []CostOptimizationSuggestion{}
		for _, suggestion := range suggestions {
			if suggestion.Type == optimizationType {
				filtered = append(filtered, suggestion)
			}
		}
		suggestions = filtered
	}

	if minSavings := r.URL.Query().Get("min_savings"); minSavings != "" {
		if threshold, err := strconv.ParseFloat(minSavings, 64); err == nil {
			filtered := []CostOptimizationSuggestion{}
			for _, suggestion := range suggestions {
				if suggestion.Savings >= threshold {
					filtered = append(filtered, suggestion)
				}
			}
			suggestions = filtered
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"suggestions":   suggestions,
		"total":         len(suggestions),
		"total_savings": c.calculateTotalSavings(suggestions),
		"generated_at":  time.Now().UTC(),
	})
}

func (c *CostBenefitAdvisor) calculateTotalSavings(suggestions []CostOptimizationSuggestion) float64 {
	total := 0.0
	for _, suggestion := range suggestions {
		total += suggestion.Savings
	}
	return total
}
