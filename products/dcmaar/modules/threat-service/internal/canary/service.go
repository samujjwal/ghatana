package canary

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"math"
	mathrand "math/rand"
	"sort"
	"time"
)

// CanaryService implements the Service interface for smart canary selection
type CanaryService struct {
	repository Repository
	config     ServiceConfig
}

// ServiceConfig defines configuration for the canary service
type ServiceConfig struct {
	DefaultTrafficPercentage float64       `json:"default_traffic_percentage"`
	MaxCandidatesPerRegion   int           `json:"max_candidates_per_region"`
	HealthCheckInterval      time.Duration `json:"health_check_interval"`
	RiskAssessmentInterval   time.Duration `json:"risk_assessment_interval"`
	MinHealthScore           float64       `json:"min_health_score"`
	MaxLoadThreshold         float64       `json:"max_load_threshold"`
	GeographicWeighting      bool          `json:"geographic_weighting"`
	LoadBalancingWeight      float64       `json:"load_balancing_weight"`
	HealthWeight             float64       `json:"health_weight"`
	StabilityWeight          float64       `json:"stability_weight"`
}

// NewCanaryService creates a new canary service instance
func NewCanaryService(repository Repository) *CanaryService {
	return &CanaryService{
		repository: repository,
		config:     getDefaultServiceConfig(),
	}
}

// SelectCanaryNodes intelligently selects nodes for canary deployment
func (s *CanaryService) SelectCanaryNodes(ctx context.Context, criteria SelectionCriteria) (*CanarySelection, error) {
	startTime := time.Now()

	// Create selection record
	selection := &CanarySelection{
		ID:                generateID(),
		SelectionCriteria: criteria,
		Status:            StatusSelecting,
		CreatedAt:         time.Now(),
		UpdatedAt:         time.Now(),
		Metadata:          make(map[string]interface{}),
	}

	// Get all candidate nodes
	filters := NodeFilters{
		MinHealthScore: &s.config.MinHealthScore,
		MaxLoadCPU:     &s.config.MaxLoadThreshold,
		Limit:          1000, // Get a large pool to choose from
	}

	candidates, err := s.repository.ListNodes(ctx, filters)
	if err != nil {
		return nil, fmt.Errorf("failed to get candidate nodes: %w", err)
	}

	if len(candidates) == 0 {
		return nil, fmt.Errorf("no candidate nodes available")
	}

	// Apply additional filtering based on criteria
	qualifiedCandidates := s.filterCandidates(candidates, criteria)
	if len(qualifiedCandidates) == 0 {
		return nil, fmt.Errorf("no nodes meet the selection criteria")
	}

	// Score and rank candidates
	scoredCandidates := s.scoreCandidates(qualifiedCandidates, criteria)

	// Select optimal nodes
	selectedNodes := s.selectOptimalNodes(scoredCandidates, criteria)

	// Calculate risk assessment
	riskScore := s.calculateRiskScore(selectedNodes, criteria)
	confidenceScore := s.calculateConfidenceScore(selectedNodes, scoredCandidates)

	// Populate selection results
	selection.SelectedNodes = selectedNodes
	selection.RiskScore = riskScore
	selection.ConfidenceScore = confidenceScore
	selection.Status = StatusSelected
	selection.UpdatedAt = time.Now()

	// Calculate metrics
	selectionTime := time.Since(startTime).Seconds()
	selection.Metrics = SelectionMetrics{
		TotalCandidates:     len(candidates),
		QualifiedCandidates: len(qualifiedCandidates),
		SelectedCandidates:  len(selectedNodes),
		SelectionTime:       selectionTime,
		AverageRiskScore:    riskScore,
		GeographicCoverage:  s.calculateGeographicCoverage(selectedNodes),
		LoadBalanceScore:    s.calculateLoadBalanceScore(selectedNodes),
		DiversityScore:      s.calculateDiversityScore(selectedNodes),
	}

	// Save selection
	if err := s.repository.SaveSelection(ctx, selection); err != nil {
		return nil, fmt.Errorf("failed to save selection: %w", err)
	}

	return selection, nil
}

// GetCanarySelection retrieves a canary selection
func (s *CanaryService) GetCanarySelection(ctx context.Context, selectionID string) (*CanarySelection, error) {
	return s.repository.GetSelection(ctx, selectionID)
}

// UpdateSelectionStatus updates the status of a canary selection
func (s *CanaryService) UpdateSelectionStatus(ctx context.Context, selectionID string, status SelectionStatus) error {
	selection, err := s.repository.GetSelection(ctx, selectionID)
	if err != nil {
		return fmt.Errorf("failed to get selection: %w", err)
	}

	selection.Status = status
	selection.UpdatedAt = time.Now()

	return s.repository.UpdateSelection(ctx, selection)
}

// ListCandidateNodes returns available candidate nodes
func (s *CanaryService) ListCandidateNodes(ctx context.Context, filters NodeFilters) ([]NodeCandidate, error) {
	return s.repository.ListNodes(ctx, filters)
}

// GetNodeMetrics retrieves current metrics for a node
func (s *CanaryService) GetNodeMetrics(ctx context.Context, nodeID string) (*LoadMetrics, error) {
	// In a real implementation, this would fetch from monitoring systems
	// For now, simulate with random but realistic values
	return &LoadMetrics{
		CPUUsage:     float64(30 + mathrand.Intn(40)),   // 30-70%
		MemoryUsage:  float64(40 + mathrand.Intn(30)),   // 40-70%
		DiskUsage:    float64(20 + mathrand.Intn(50)),   // 20-70%
		NetworkLoad:  float64(10 + mathrand.Intn(60)),   // 10-70%
		RequestRate:  float64(100 + mathrand.Intn(400)), // 100-500 RPS
		ErrorRate:    float64(mathrand.Intn(5)),         // 0-5%
		ResponseTime: float64(50 + mathrand.Intn(200)),  // 50-250ms
		QueueDepth:   mathrand.Intn(20),                 // 0-20 items
	}, nil
}

// ValidateNodeHealth performs comprehensive node health check
func (s *CanaryService) ValidateNodeHealth(ctx context.Context, nodeID string) (*HealthReport, error) {
	// Get current metrics
	metrics, err := s.GetNodeMetrics(ctx, nodeID)
	if err != nil {
		return nil, fmt.Errorf("failed to get node metrics: %w", err)
	}

	// Calculate health factors
	healthFactors := []HealthFactor{
		{
			Name:        "CPU Health",
			Score:       math.Max(0, 1.0-(metrics.CPUUsage/100.0)),
			Weight:      0.25,
			Status:      getHealthStatus(metrics.CPUUsage, 80, 90),
			Description: fmt.Sprintf("CPU usage at %.1f%%", metrics.CPUUsage),
		},
		{
			Name:        "Memory Health",
			Score:       math.Max(0, 1.0-(metrics.MemoryUsage/100.0)),
			Weight:      0.25,
			Status:      getHealthStatus(metrics.MemoryUsage, 85, 95),
			Description: fmt.Sprintf("Memory usage at %.1f%%", metrics.MemoryUsage),
		},
		{
			Name:        "Response Time Health",
			Score:       math.Max(0, 1.0-(metrics.ResponseTime/1000.0)), // Normalize to 1s max
			Weight:      0.20,
			Status:      getHealthStatus(metrics.ResponseTime, 200, 500),
			Description: fmt.Sprintf("Average response time %.1fms", metrics.ResponseTime),
		},
		{
			Name:        "Error Rate Health",
			Score:       math.Max(0, 1.0-(metrics.ErrorRate/10.0)), // Normalize to 10% max
			Weight:      0.30,
			Status:      getHealthStatus(metrics.ErrorRate, 2, 5),
			Description: fmt.Sprintf("Error rate at %.2f%%", metrics.ErrorRate),
		},
	}

	// Calculate overall health
	var weightedScore float64
	var totalWeight float64
	for _, factor := range healthFactors {
		weightedScore += factor.Score * factor.Weight
		totalWeight += factor.Weight
	}
	overallHealth := weightedScore / totalWeight

	// Generate recommendations
	var recommendations []string
	for _, factor := range healthFactors {
		if factor.Status == "WARNING" {
			recommendations = append(recommendations, fmt.Sprintf("Monitor %s - %s", factor.Name, factor.Description))
		} else if factor.Status == "CRITICAL" {
			recommendations = append(recommendations, fmt.Sprintf("Address %s immediately - %s", factor.Name, factor.Description))
		}
	}

	if len(recommendations) == 0 {
		recommendations = append(recommendations, "Node is healthy and ready for deployment")
	}

	return &HealthReport{
		NodeID:          nodeID,
		OverallHealth:   overallHealth,
		HealthFactors:   healthFactors,
		Recommendations: recommendations,
		LastChecked:     time.Now(),
		Metadata:        make(map[string]interface{}),
	}, nil
}

// AssessDeploymentRisk analyzes the risk of the selected deployment
func (s *CanaryService) AssessDeploymentRisk(ctx context.Context, selection *CanarySelection) (*RiskAssessment, error) {
	var riskFactors []RiskFactor

	// Analyze geographic distribution risk
	regions := make(map[string]int)
	for _, node := range selection.SelectedNodes {
		regions[node.Region]++
	}

	if len(regions) < 2 && len(selection.SelectedNodes) > 1 {
		riskFactors = append(riskFactors, RiskFactor{
			Type:        RiskTypeGeographic,
			Severity:    "MEDIUM",
			Description: "All canary nodes in single region - geographic concentration risk",
			Impact:      0.6,
			Likelihood:  0.3,
			Mitigation:  "Spread deployment across multiple regions",
		})
	}

	// Analyze capacity risk
	var avgLoad float64
	for _, node := range selection.SelectedNodes {
		avgLoad += node.CurrentLoad.CPUUsage
	}
	avgLoad /= float64(len(selection.SelectedNodes))

	if avgLoad > 70 {
		riskFactors = append(riskFactors, RiskFactor{
			Type:        RiskTypeCapacity,
			Severity:    "HIGH",
			Description: fmt.Sprintf("High average CPU usage (%.1f%%) on selected nodes", avgLoad),
			Impact:      0.8,
			Likelihood:  0.7,
			Mitigation:  "Select nodes with lower current load or scale resources",
		})
	}

	// Analyze user impact risk
	if selection.SelectionCriteria.TrafficPercentage > 20 {
		riskFactors = append(riskFactors, RiskFactor{
			Type:        RiskTypeUserImpact,
			Severity:    "MEDIUM",
			Description: fmt.Sprintf("High traffic percentage (%.1f%%) increases user impact", selection.SelectionCriteria.TrafficPercentage),
			Impact:      0.5,
			Likelihood:  0.4,
			Mitigation:  "Reduce traffic percentage or implement gradual rollout",
		})
	}

	// Calculate overall risk
	var totalRisk float64
	for _, factor := range riskFactors {
		totalRisk += factor.Impact * factor.Likelihood
	}
	overallRisk := math.Min(1.0, totalRisk/float64(len(riskFactors)))

	// Determine risk level
	var riskLevel RiskLevel
	switch {
	case overallRisk < 0.2:
		riskLevel = RiskLevelVeryLow
	case overallRisk < 0.4:
		riskLevel = RiskLevelLow
	case overallRisk < 0.7:
		riskLevel = RiskLevelMedium
	default:
		riskLevel = RiskLevelHigh
	}

	// Generate mitigations
	mitigations, err := s.GetRiskRecommendations(ctx, riskFactors)
	if err != nil {
		mitigations = []RiskMitigation{} // Continue with empty mitigations
	}

	return &RiskAssessment{
		SelectionID:     selection.ID,
		OverallRisk:     overallRisk,
		RiskLevel:       riskLevel,
		RiskFactors:     riskFactors,
		Mitigations:     mitigations,
		Recommendations: generateRiskRecommendations(riskFactors),
		AssessedAt:      time.Now(),
	}, nil
}

// GetRiskRecommendations provides mitigation strategies for identified risks
func (s *CanaryService) GetRiskRecommendations(ctx context.Context, riskFactors []RiskFactor) ([]RiskMitigation, error) {
	var mitigations []RiskMitigation

	for _, risk := range riskFactors {
		switch risk.Type {
		case RiskTypeGeographic:
			mitigations = append(mitigations, RiskMitigation{
				RiskType:           RiskTypeGeographic,
				Strategy:           "Multi-region deployment",
				EffectivenessScore: 0.8,
				ImplementationCost: "MEDIUM",
				TimeToImplement:    15 * time.Minute,
				Description:        "Redistribute nodes across multiple geographic regions",
			})
		case RiskTypeCapacity:
			mitigations = append(mitigations, RiskMitigation{
				RiskType:           RiskTypeCapacity,
				Strategy:           "Load balancing and scaling",
				EffectivenessScore: 0.9,
				ImplementationCost: "HIGH",
				TimeToImplement:    30 * time.Minute,
				Description:        "Scale resources or redistribute load to lower-usage nodes",
			})
		case RiskTypeUserImpact:
			mitigations = append(mitigations, RiskMitigation{
				RiskType:           RiskTypeUserImpact,
				Strategy:           "Gradual traffic increase",
				EffectivenessScore: 0.7,
				ImplementationCost: "LOW",
				TimeToImplement:    5 * time.Minute,
				Description:        "Start with lower traffic percentage and gradually increase",
			})
		}
	}

	return mitigations, nil
}

// MonitorCanaryHealth monitors the health of an active canary deployment
func (s *CanaryService) MonitorCanaryHealth(ctx context.Context, selectionID string) (*CanaryHealthReport, error) {
	selection, err := s.repository.GetSelection(ctx, selectionID)
	if err != nil {
		return nil, fmt.Errorf("failed to get selection: %w", err)
	}

	// Get health for each node
	nodeHealth := make(map[string]float64)
	var activeAlerts []HealthAlert
	var overallHealth float64

	for _, node := range selection.SelectedNodes {
		health, err := s.ValidateNodeHealth(ctx, node.NodeID)
		if err != nil {
			nodeHealth[node.NodeID] = 0.0
			activeAlerts = append(activeAlerts, HealthAlert{
				ID:          generateID(),
				Severity:    "CRITICAL",
				Title:       "Health Check Failed",
				Description: fmt.Sprintf("Unable to get health status for node %s", node.NodeID),
				NodeID:      node.NodeID,
				CreatedAt:   time.Now(),
			})
			continue
		}

		nodeHealth[node.NodeID] = health.OverallHealth
		overallHealth += health.OverallHealth

		// Generate alerts for unhealthy factors
		for _, factor := range health.HealthFactors {
			if factor.Status == "CRITICAL" {
				activeAlerts = append(activeAlerts, HealthAlert{
					ID:          generateID(),
					Severity:    "CRITICAL",
					Title:       fmt.Sprintf("%s Critical", factor.Name),
					Description: factor.Description,
					NodeID:      node.NodeID,
					CreatedAt:   time.Now(),
				})
			} else if factor.Status == "WARNING" {
				activeAlerts = append(activeAlerts, HealthAlert{
					ID:          generateID(),
					Severity:    "WARNING",
					Title:       fmt.Sprintf("%s Warning", factor.Name),
					Description: factor.Description,
					NodeID:      node.NodeID,
					CreatedAt:   time.Now(),
				})
			}
		}
	}

	if len(selection.SelectedNodes) > 0 {
		overallHealth /= float64(len(selection.SelectedNodes))
	}

	// Generate recommendations
	var recommendations []string
	if overallHealth < 0.5 {
		recommendations = append(recommendations, "Consider immediate rollback - overall health is poor")
	} else if overallHealth < 0.7 {
		recommendations = append(recommendations, "Monitor closely - health below optimal threshold")
	} else {
		recommendations = append(recommendations, "Canary deployment is healthy")
	}

	// Check metric status (simulated)
	metricStatus := map[string]string{
		"error_rate":    "HEALTHY",
		"response_time": "HEALTHY",
		"throughput":    "HEALTHY",
	}

	if overallHealth < 0.7 {
		metricStatus["error_rate"] = "WARNING"
	}
	if overallHealth < 0.5 {
		metricStatus["response_time"] = "CRITICAL"
	}

	return &CanaryHealthReport{
		SelectionID:     selectionID,
		OverallHealth:   overallHealth,
		NodeHealth:      nodeHealth,
		MetricStatus:    metricStatus,
		ActiveAlerts:    activeAlerts,
		Recommendations: recommendations,
		LastUpdated:     time.Now(),
		Metadata:        make(map[string]interface{}),
	}, nil
}

// TriggerRollback initiates rollback of a canary deployment
func (s *CanaryService) TriggerRollback(ctx context.Context, selectionID string, reason string) error {
	selection, err := s.repository.GetSelection(ctx, selectionID)
	if err != nil {
		return fmt.Errorf("failed to get selection: %w", err)
	}

	if selection.Status != StatusActive {
		return fmt.Errorf("can only rollback active deployments, current status: %s", selection.Status)
	}

	// Update status
	selection.Status = StatusRolledBack
	selection.UpdatedAt = time.Now()

	// Add rollback reason to metadata
	if selection.Metadata == nil {
		selection.Metadata = make(map[string]interface{})
	}
	selection.Metadata["rollback_reason"] = reason
	selection.Metadata["rollback_timestamp"] = time.Now()

	return s.repository.UpdateSelection(ctx, selection)
}

// GetSelectionHistory retrieves historical selections for a service
func (s *CanaryService) GetSelectionHistory(ctx context.Context, serviceID string, limit int) ([]CanarySelection, error) {
	return s.repository.GetSelectionHistory(ctx, serviceID, limit)
}

// GetSelectionAnalytics provides analytics for canary selections
func (s *CanaryService) GetSelectionAnalytics(ctx context.Context, timeframe string) (*SelectionAnalytics, error) {
	// This would typically query historical data
	// For now, provide simulated analytics

	analytics := &SelectionAnalytics{
		TotalSelections:      150,
		SuccessRate:          0.87,
		AverageSelectionTime: 45.2,
		RiskDistribution: map[RiskLevel]int{
			RiskLevelVeryLow: 45,
			RiskLevelLow:     62,
			RiskLevelMedium:  35,
			RiskLevelHigh:    8,
		},
		PopularRegions: []RegionStats{
			{Region: "us-east-1", SelectionCount: 45, SuccessRate: 0.91, AverageRiskScore: 0.23},
			{Region: "us-west-2", SelectionCount: 38, SuccessRate: 0.89, AverageRiskScore: 0.28},
			{Region: "eu-west-1", SelectionCount: 32, SuccessRate: 0.84, AverageRiskScore: 0.31},
		},
		CommonRiskFactors: []RiskFactorStats{
			{RiskType: RiskTypeCapacity, Frequency: 67, AverageImpact: 0.6, MitigationRate: 0.8},
			{RiskType: RiskTypeGeographic, Frequency: 23, AverageImpact: 0.4, MitigationRate: 0.9},
			{RiskType: RiskTypeUserImpact, Frequency: 15, AverageImpact: 0.5, MitigationRate: 0.7},
		},
		Timeframe:   timeframe,
		GeneratedAt: time.Now(),
	}

	return analytics, nil
}

// GetPerformanceMetrics provides detailed performance metrics for a selection
func (s *CanaryService) GetPerformanceMetrics(ctx context.Context, selectionID string) (*PerformanceReport, error) {
	selection, err := s.repository.GetSelection(ctx, selectionID)
	if err != nil {
		return nil, fmt.Errorf("failed to get selection: %w", err)
	}

	// Simulate performance metrics
	nodePerformance := make(map[string]NodePerformance)
	for i, node := range selection.SelectedNodes {
		metrics, _ := s.GetNodeMetrics(ctx, node.NodeID)
		nodePerformance[node.NodeID] = NodePerformance{
			NodeID:              node.NodeID,
			TrafficShare:        float64(100 / len(selection.SelectedNodes)),
			ErrorRate:           metrics.ErrorRate,
			AverageResponseTime: metrics.ResponseTime,
			ThroughputRPS:       metrics.RequestRate,
			ResourceUtilization: *metrics,
			HealthScore:         0.8 + float64(i%3)*0.1, // Vary between 0.8-1.0
		}
	}

	report := &PerformanceReport{
		SelectionID:         selectionID,
		Duration:            time.Since(selection.CreatedAt),
		TrafficProcessed:    int64(500000 + mathrand.Intn(1000000)), // 500K-1.5M requests
		ErrorRate:           0.5 + mathrand.Float64()*2,             // 0.5-2.5%
		AverageResponseTime: 80 + mathrand.Float64()*40,             // 80-120ms
		ThroughputRPS:       200 + mathrand.Float64()*300,           // 200-500 RPS
		NodePerformance:     nodePerformance,
		GeneratedAt:         time.Now(),
	}

	return report, nil
}

// Helper methods

func (s *CanaryService) filterCandidates(candidates []NodeCandidate, criteria SelectionCriteria) []NodeCandidate {
	var filtered []NodeCandidate

	for _, candidate := range candidates {
		// Check constraints
		if !s.meetsConstraints(candidate, criteria.Constraints) {
			continue
		}

		// Check user segmentation
		if criteria.UserSegmentation.EnableSegmentation {
			if !s.meetsSegmentation(candidate, criteria.UserSegmentation) {
				continue
			}
		}

		// Check health and load thresholds
		if candidate.HealthScore < s.config.MinHealthScore {
			continue
		}

		if candidate.CurrentLoad.CPUUsage > s.config.MaxLoadThreshold {
			continue
		}

		filtered = append(filtered, candidate)
	}

	return filtered
}

func (s *CanaryService) scoreCandidates(candidates []NodeCandidate, criteria SelectionCriteria) []ScoredCandidate {
	var scored []ScoredCandidate

	for _, candidate := range candidates {
		score := s.calculateNodeScore(candidate, criteria)
		scored = append(scored, ScoredCandidate{
			NodeCandidate: candidate,
			Score:         score,
		})
	}

	// Sort by score descending
	sort.Slice(scored, func(i, j int) bool {
		return scored[i].Score > scored[j].Score
	})

	return scored
}

func (s *CanaryService) calculateNodeScore(candidate NodeCandidate, criteria SelectionCriteria) float64 {
	score := 0.0

	// Health score component
	score += candidate.HealthScore * s.config.HealthWeight

	// Stability score component
	score += candidate.StabilityScore * s.config.StabilityWeight

	// Load balancing component (prefer lower load)
	loadScore := 1.0 - (candidate.CurrentLoad.CPUUsage / 100.0)
	score += loadScore * s.config.LoadBalancingWeight

	// Geographic diversity bonus
	if s.config.GeographicWeighting {
		// This would be more sophisticated in a real implementation
		score += 0.1 // Small bonus for geographic diversity
	}

	return score
}

func (s *CanaryService) selectOptimalNodes(scored []ScoredCandidate, criteria SelectionCriteria) []NodeCandidate {
	targetCount := s.calculateTargetNodeCount(len(scored), criteria.TrafficPercentage)

	var selected []NodeCandidate
	regionCounts := make(map[string]int)
	zoneCounts := make(map[string]int)

	for _, candidate := range scored {
		if len(selected) >= targetCount {
			break
		}

		// Check regional distribution constraints
		if criteria.Constraints.MaxNodesPerRegion > 0 {
			if regionCounts[candidate.NodeCandidate.Region] >= criteria.Constraints.MaxNodesPerRegion {
				continue
			}
		}

		// Check zone distribution constraints
		if criteria.Constraints.MaxNodesPerZone > 0 {
			if zoneCounts[candidate.NodeCandidate.Zone] >= criteria.Constraints.MaxNodesPerZone {
				continue
			}
		}

		// Add reason for selection
		candidate.NodeCandidate.SelectionReason = fmt.Sprintf("Score: %.3f, Health: %.2f, Load: %.1f%%",
			candidate.Score, candidate.NodeCandidate.HealthScore, candidate.NodeCandidate.CurrentLoad.CPUUsage)

		selected = append(selected, candidate.NodeCandidate)
		regionCounts[candidate.NodeCandidate.Region]++
		zoneCounts[candidate.NodeCandidate.Zone]++
	}

	return selected
}

func (s *CanaryService) calculateTargetNodeCount(totalCandidates int, trafficPercentage float64) int {
	// Simple calculation - could be more sophisticated
	targetCount := int(math.Ceil(float64(totalCandidates) * trafficPercentage / 100.0))

	// Ensure minimum and maximum bounds
	if targetCount < 1 {
		targetCount = 1
	}
	if targetCount > 20 { // Cap at 20 nodes for safety
		targetCount = 20
	}

	return targetCount
}

func (s *CanaryService) calculateRiskScore(nodes []NodeCandidate, criteria SelectionCriteria) float64 {
	if len(nodes) == 0 {
		return 1.0 // Maximum risk if no nodes
	}

	riskScore := 0.0

	// Load risk - higher load = higher risk
	var avgLoad float64
	for _, node := range nodes {
		avgLoad += node.CurrentLoad.CPUUsage
	}
	avgLoad /= float64(len(nodes))
	riskScore += (avgLoad / 100.0) * 0.3

	// Geographic concentration risk
	regions := make(map[string]bool)
	for _, node := range nodes {
		regions[node.Region] = true
	}
	if len(regions) == 1 && len(nodes) > 1 {
		riskScore += 0.4 // High risk for single region
	}

	// Traffic percentage risk
	if criteria.TrafficPercentage > 20 {
		riskScore += (criteria.TrafficPercentage - 20) / 100.0
	}

	return math.Min(1.0, riskScore)
}

func (s *CanaryService) calculateConfidenceScore(selected []NodeCandidate, allCandidates []ScoredCandidate) float64 {
	if len(selected) == 0 || len(allCandidates) == 0 {
		return 0.0
	}

	// Calculate average score of selected nodes
	var totalScore float64
	selectedIDs := make(map[string]bool)
	for _, node := range selected {
		selectedIDs[node.NodeID] = true
	}

	for _, candidate := range allCandidates {
		if selectedIDs[candidate.NodeCandidate.NodeID] {
			totalScore += candidate.Score
		}
	}

	return totalScore / float64(len(selected))
}

func (s *CanaryService) calculateGeographicCoverage(nodes []NodeCandidate) float64 {
	if len(nodes) == 0 {
		return 0.0
	}

	regions := make(map[string]bool)
	zones := make(map[string]bool)

	for _, node := range nodes {
		regions[node.Region] = true
		zones[node.Zone] = true
	}

	// Score based on region and zone diversity
	regionScore := math.Min(1.0, float64(len(regions))/3.0) // Normalize to 3 regions
	zoneScore := math.Min(1.0, float64(len(zones))/6.0)     // Normalize to 6 zones

	return (regionScore + zoneScore) / 2.0
}

func (s *CanaryService) calculateLoadBalanceScore(nodes []NodeCandidate) float64 {
	if len(nodes) <= 1 {
		return 1.0
	}

	// Calculate coefficient of variation for CPU usage
	var sum, sumSquares float64
	for _, node := range nodes {
		sum += node.CurrentLoad.CPUUsage
		sumSquares += node.CurrentLoad.CPUUsage * node.CurrentLoad.CPUUsage
	}

	mean := sum / float64(len(nodes))
	variance := (sumSquares / float64(len(nodes))) - (mean * mean)
	stdDev := math.Sqrt(variance)

	// Lower coefficient of variation = better load balance
	cv := stdDev / mean
	return math.Max(0, 1.0-cv) // Invert so higher score = better balance
}

func (s *CanaryService) calculateDiversityScore(nodes []NodeCandidate) float64 {
	if len(nodes) == 0 {
		return 0.0
	}

	// Count unique instance types
	instanceTypes := make(map[string]bool)
	for _, node := range nodes {
		instanceTypes[node.InstanceType] = true
	}

	// Simple diversity metric
	return math.Min(1.0, float64(len(instanceTypes))/float64(len(nodes)))
}

func (s *CanaryService) meetsConstraints(candidate NodeCandidate, constraints DeploymentConstraints) bool {
	// Check health score
	if candidate.HealthScore < constraints.MinHealthScore {
		return false
	}

	// Check load threshold
	if candidate.CurrentLoad.CPUUsage > constraints.MaxLoadThreshold {
		return false
	}

	// Check required regions
	if len(constraints.RequiredRegions) > 0 {
		found := false
		for _, region := range constraints.RequiredRegions {
			if candidate.Region == region {
				found = true
				break
			}
		}
		if !found {
			return false
		}
	}

	// Check excluded regions
	for _, region := range constraints.ExcludedRegions {
		if candidate.Region == region {
			return false
		}
	}

	// Check required tags
	for key, value := range constraints.RequiredTags {
		if candidateValue, exists := candidate.Metadata[key]; !exists || candidateValue != value {
			return false
		}
	}

	return true
}

func (s *CanaryService) meetsSegmentation(candidate NodeCandidate, segmentation UserSegmentation) bool {
	// Check if candidate serves target segments
	if len(segmentation.TargetSegments) > 0 {
		hasTargetSegment := false
		for _, target := range segmentation.TargetSegments {
			for _, candidateSegment := range candidate.UserSegments {
				if candidateSegment == target {
					hasTargetSegment = true
					break
				}
			}
			if hasTargetSegment {
				break
			}
		}
		if !hasTargetSegment {
			return false
		}
	}

	// Check if candidate serves excluded segments
	for _, excluded := range segmentation.ExcludeSegments {
		for _, candidateSegment := range candidate.UserSegments {
			if candidateSegment == excluded {
				return false
			}
		}
	}

	return true
}

// Helper types

type ScoredCandidate struct {
	NodeCandidate NodeCandidate
	Score         float64
}

// Helper functions

func getDefaultServiceConfig() ServiceConfig {
	return ServiceConfig{
		DefaultTrafficPercentage: 5.0,
		MaxCandidatesPerRegion:   10,
		HealthCheckInterval:      5 * time.Minute,
		RiskAssessmentInterval:   15 * time.Minute,
		MinHealthScore:           0.7,
		MaxLoadThreshold:         80.0,
		GeographicWeighting:      true,
		LoadBalancingWeight:      0.3,
		HealthWeight:             0.4,
		StabilityWeight:          0.3,
	}
}

func getHealthStatus(value, warningThreshold, criticalThreshold float64) string {
	if value >= criticalThreshold {
		return "CRITICAL"
	} else if value >= warningThreshold {
		return "WARNING"
	}
	return "HEALTHY"
}

func generateRiskRecommendations(riskFactors []RiskFactor) []string {
	var recommendations []string

	for _, risk := range riskFactors {
		if risk.Severity == "HIGH" {
			recommendations = append(recommendations, fmt.Sprintf("Address %s risk: %s", risk.Type, risk.Mitigation))
		}
	}

	if len(recommendations) == 0 {
		recommendations = append(recommendations, "Risk assessment looks good - proceed with deployment")
	}

	return recommendations
}

func generateID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}
