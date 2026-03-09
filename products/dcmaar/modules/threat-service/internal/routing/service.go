package routing

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

// RoutingService implements the Service interface for smart routing
type RoutingService struct {
	repository        Repository
	config            ServiceConfig
	targetHealthCache map[string]*TargetHealth
	strategyCache     map[string]*RoutingStrategy
}

// ServiceConfig defines configuration for the routing service
type ServiceConfig struct {
	DefaultStrategy       StrategyType  `json:"default_strategy"`
	DecisionTimeout       time.Duration `json:"decision_timeout"`
	HealthCheckInterval   time.Duration `json:"health_check_interval"`
	CacheTimeout          time.Duration `json:"cache_timeout"`
	MaxTargetsPerDecision int           `json:"max_targets_per_decision"`
	LoadBalanceThreshold  float64       `json:"load_balance_threshold"`
	HealthScoreThreshold  float64       `json:"health_score_threshold"`
	LatencyWeightDefault  float64       `json:"latency_weight_default"`
	LoadWeightDefault     float64       `json:"load_weight_default"`
	HealthWeightDefault   float64       `json:"health_weight_default"`
	EnableMLOptimization  bool          `json:"enable_ml_optimization"`
	CircuitBreakerEnabled bool          `json:"circuit_breaker_enabled"`
}

// NewRoutingService creates a new routing service instance
func NewRoutingService(repository Repository) *RoutingService {
	return &RoutingService{
		repository:        repository,
		config:            getDefaultServiceConfig(),
		targetHealthCache: make(map[string]*TargetHealth),
		strategyCache:     make(map[string]*RoutingStrategy),
	}
}

// MakeRoutingDecision intelligently routes a request to the best target
func (s *RoutingService) MakeRoutingDecision(ctx context.Context, request RoutingRequest) (*RoutingDecision, error) {
	startTime := time.Now()

	// Create decision record
	decision := &RoutingDecision{
		ID:              generateID(),
		RequestID:       request.RequestID,
		ServiceName:     request.ServiceName,
		RequestMetadata: request.Metadata,
		Status:          DecisionPending,
		CreatedAt:       time.Now(),
		Metadata:        make(map[string]interface{}),
	}

	// Get routing strategy
	strategy, err := s.getRoutingStrategy(ctx, request)
	if err != nil {
		return nil, fmt.Errorf("failed to get routing strategy: %w", err)
	}
	decision.RoutingStrategy = *strategy

	// Get candidate targets
	targets, err := s.getCandidateTargets(ctx, request, strategy)
	if err != nil {
		return nil, fmt.Errorf("failed to get candidate targets: %w", err)
	}

	if len(targets) == 0 {
		decision.Status = DecisionFailed
		decision.DecisionReason = "No suitable targets found"
		decision.CompletedAt = &[]time.Time{time.Now()}[0]
		s.repository.SaveDecision(ctx, decision)
		return decision, fmt.Errorf("no suitable targets found")
	}

	// Apply routing rules
	filteredTargets, rulesApplied := s.applyRoutingRules(ctx, targets, request, strategy)
	if len(filteredTargets) == 0 {
		// Fall back to original targets if rules filtered out everything
		filteredTargets = targets
	}

	// Score and rank targets
	scoredTargets := s.scoreTargets(ctx, filteredTargets, request, strategy)

	// Select the best target
	selectedTarget, alternativeTargets := s.selectBestTarget(scoredTargets, strategy)

	// Calculate confidence score
	confidenceScore := s.calculateConfidenceScore(scoredTargets, selectedTarget, strategy)

	// Complete the decision
	decisionTime := time.Since(startTime)
	decision.SelectedTarget = *selectedTarget
	decision.AlternativeTargets = alternativeTargets
	decision.DecisionReason = s.generateDecisionReason(selectedTarget, strategy)
	decision.ConfidenceScore = confidenceScore
	decision.DecisionTime = decisionTime
	decision.Status = DecisionCompleted
	decision.CompletedAt = &[]time.Time{time.Now()}[0]

	// Set metrics
	decision.Metrics = DecisionMetrics{
		TargetsEvaluated:   len(targets),
		RulesApplied:       rulesApplied,
		DecisionTime:       decisionTime,
		CacheHitRate:       0.8, // Simulated
		FallbacksTriggered: 0,
		ScoreVariance:      s.calculateScoreVariance(scoredTargets),
		ConfidenceLevel:    s.getConfidenceLevel(confidenceScore),
	}

	// Save decision
	if err := s.repository.SaveDecision(ctx, decision); err != nil {
		return nil, fmt.Errorf("failed to save decision: %w", err)
	}

	return decision, nil
}

// GetRoutingDecision retrieves a routing decision
func (s *RoutingService) GetRoutingDecision(ctx context.Context, decisionID string) (*RoutingDecision, error) {
	return s.repository.GetDecision(ctx, decisionID)
}

// UpdateDecisionStatus updates the status of a routing decision
func (s *RoutingService) UpdateDecisionStatus(ctx context.Context, decisionID string, status DecisionStatus) error {
	decision, err := s.repository.GetDecision(ctx, decisionID)
	if err != nil {
		return fmt.Errorf("failed to get decision: %w", err)
	}

	decision.Status = status
	if status == DecisionCompleted || status == DecisionFailed || status == DecisionTimeout {
		now := time.Now()
		decision.CompletedAt = &now
	}

	return s.repository.UpdateDecision(ctx, decision)
}

// RegisterTarget registers a new routing target
func (s *RoutingService) RegisterTarget(ctx context.Context, target *Target) error {
	if target.ID == "" {
		target.ID = generateID()
	}

	// Initialize scores if not provided
	if target.HealthScore == 0 {
		target.HealthScore = 1.0 // Start with perfect health
	}
	if target.CapacityScore == 0 {
		target.CapacityScore = 1.0 // Start with full capacity
	}

	return s.repository.SaveTarget(ctx, target)
}

// UpdateTarget updates an existing routing target
func (s *RoutingService) UpdateTarget(ctx context.Context, target *Target) error {
	// Update overall score based on individual scores
	target.OverallScore = s.calculateOverallTargetScore(target)

	// Clear cache for this target
	delete(s.targetHealthCache, target.ID)

	return s.repository.UpdateTarget(ctx, target)
}

// DeregisterTarget removes a routing target
func (s *RoutingService) DeregisterTarget(ctx context.Context, targetID string) error {
	// Clear cache
	delete(s.targetHealthCache, targetID)

	return s.repository.DeleteTarget(ctx, targetID)
}

// ListTargets lists available routing targets
func (s *RoutingService) ListTargets(ctx context.Context, filters TargetFilters) ([]Target, error) {
	return s.repository.ListTargets(ctx, filters)
}

// GetTargetHealth gets comprehensive health information for a target
func (s *RoutingService) GetTargetHealth(ctx context.Context, targetID string) (*TargetHealth, error) {
	// Check cache first
	if health, exists := s.targetHealthCache[targetID]; exists {
		if time.Since(health.LastChecked) < s.config.CacheTimeout {
			return health, nil
		}
	}

	// Get target
	target, err := s.repository.GetTarget(ctx, targetID)
	if err != nil {
		return nil, fmt.Errorf("failed to get target: %w", err)
	}

	// Calculate health factors
	healthFactors := []HealthFactor{
		{
			Name:        "Load Health",
			Score:       1.0 - (target.CurrentLoad.CPUUsage / 100.0),
			Weight:      0.3,
			Status:      getHealthStatus(target.CurrentLoad.CPUUsage, 70, 90),
			Description: fmt.Sprintf("CPU usage at %.1f%%", target.CurrentLoad.CPUUsage),
			Threshold:   70.0,
		},
		{
			Name:        "Memory Health",
			Score:       1.0 - (target.CurrentLoad.MemoryUsage / 100.0),
			Weight:      0.25,
			Status:      getHealthStatus(target.CurrentLoad.MemoryUsage, 80, 95),
			Description: fmt.Sprintf("Memory usage at %.1f%%", target.CurrentLoad.MemoryUsage),
			Threshold:   80.0,
		},
		{
			Name:        "Latency Health",
			Score:       math.Max(0, 1.0-(target.CurrentLoad.AverageLatency/1000.0)), // Normalize to 1s
			Weight:      0.25,
			Status:      getHealthStatus(target.CurrentLoad.AverageLatency, 200, 500),
			Description: fmt.Sprintf("Average latency %.1fms", target.CurrentLoad.AverageLatency),
			Threshold:   200.0,
		},
		{
			Name:        "Error Rate Health",
			Score:       math.Max(0, 1.0-(target.CurrentLoad.ErrorRate/10.0)), // Normalize to 10%
			Weight:      0.2,
			Status:      getHealthStatus(target.CurrentLoad.ErrorRate, 2, 5),
			Description: fmt.Sprintf("Error rate at %.2f%%", target.CurrentLoad.ErrorRate),
			Threshold:   2.0,
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

	// Determine status
	status := "HEALTHY"
	if overallHealth < 0.3 {
		status = "CRITICAL"
	} else if overallHealth < 0.7 {
		status = "WARNING"
	}

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
		recommendations = append(recommendations, "Target is healthy and ready for traffic")
	}

	health := &TargetHealth{
		TargetID:        targetID,
		OverallHealth:   overallHealth,
		HealthFactors:   healthFactors,
		Status:          status,
		LastChecked:     time.Now(),
		Recommendations: recommendations,
		Metadata:        make(map[string]interface{}),
	}

	// Cache the result
	s.targetHealthCache[targetID] = health

	return health, nil
}

// CreateStrategy creates a new routing strategy
func (s *RoutingService) CreateStrategy(ctx context.Context, strategy *RoutingStrategy) error {
	// Set default weights if not provided
	if strategy.Weights == (StrategyWeights{}) {
		strategy.Weights = s.getDefaultWeights()
	}

	// Clear cache
	delete(s.strategyCache, strategy.Name)

	return s.repository.SaveStrategy(ctx, strategy)
}

// UpdateStrategy updates an existing routing strategy
func (s *RoutingService) UpdateStrategy(ctx context.Context, strategy *RoutingStrategy) error {
	// Clear cache
	delete(s.strategyCache, strategy.Name)

	return s.repository.UpdateStrategy(ctx, strategy)
}

// GetStrategy retrieves a routing strategy
func (s *RoutingService) GetStrategy(ctx context.Context, strategyName string) (*RoutingStrategy, error) {
	// Check cache first
	if strategy, exists := s.strategyCache[strategyName]; exists {
		return strategy, nil
	}

	strategy, err := s.repository.GetStrategy(ctx, strategyName)
	if err != nil {
		return nil, err
	}

	// Cache the result
	s.strategyCache[strategyName] = strategy

	return strategy, nil
}

// ListStrategies lists all routing strategies
func (s *RoutingService) ListStrategies(ctx context.Context) ([]RoutingStrategy, error) {
	return s.repository.ListStrategies(ctx)
}

// CreateRule creates a new routing rule
func (s *RoutingService) CreateRule(ctx context.Context, rule *RoutingRule) error {
	if rule.ID == "" {
		rule.ID = generateID()
	}

	return s.repository.SaveRule(ctx, rule)
}

// UpdateRule updates an existing routing rule
func (s *RoutingService) UpdateRule(ctx context.Context, rule *RoutingRule) error {
	return s.repository.UpdateRule(ctx, rule)
}

// DeleteRule deletes a routing rule
func (s *RoutingService) DeleteRule(ctx context.Context, ruleID string) error {
	return s.repository.DeleteRule(ctx, ruleID)
}

// ListRules lists routing rules with filtering
func (s *RoutingService) ListRules(ctx context.Context, filters RuleFilters) ([]RoutingRule, error) {
	return s.repository.ListRules(ctx, filters)
}

// GetRoutingAnalytics provides analytics for routing decisions
func (s *RoutingService) GetRoutingAnalytics(ctx context.Context, timeframe string) (*RoutingAnalytics, error) {
	// This would typically query historical data
	// For now, provide simulated analytics

	analytics := &RoutingAnalytics{
		TotalDecisions:      2500,
		SuccessRate:         0.94,
		AverageDecisionTime: 15.3,
		TargetUtilization: map[string]float64{
			"service-a": 75.2,
			"service-b": 68.7,
			"service-c": 82.1,
		},
		StrategyDistribution: map[StrategyType]int{
			StrategyLatencyBased:  1200,
			StrategyLoadBased:     800,
			StrategyMultiCriteria: 500,
		},
		RegionDistribution: map[string]int{
			"us-east-1": 1000,
			"us-west-2": 850,
			"eu-west-1": 650,
		},
		ErrorDistribution: map[string]int{
			"target_unavailable": 45,
			"timeout":            35,
			"overloaded":         20,
		},
		LatencyDistribution: []LatencyBucket{
			{MinLatency: 0, MaxLatency: 50 * time.Millisecond, Count: 1250, Percentage: 50.0},
			{MinLatency: 50 * time.Millisecond, MaxLatency: 100 * time.Millisecond, Count: 750, Percentage: 30.0},
			{MinLatency: 100 * time.Millisecond, MaxLatency: 200 * time.Millisecond, Count: 375, Percentage: 15.0},
			{MinLatency: 200 * time.Millisecond, MaxLatency: 500 * time.Millisecond, Count: 125, Percentage: 5.0},
		},
		TopTargets: []TargetUsageStats{
			{TargetID: "target-1", TargetName: "Service A Primary", RequestCount: 1200, SuccessRate: 0.96, AvgLatency: 45.2, Utilization: 0.75},
			{TargetID: "target-2", TargetName: "Service B Primary", RequestCount: 900, SuccessRate: 0.94, AvgLatency: 52.1, Utilization: 0.68},
			{TargetID: "target-3", TargetName: "Service C Primary", RequestCount: 400, SuccessRate: 0.91, AvgLatency: 38.7, Utilization: 0.82},
		},
		Timeframe:   timeframe,
		GeneratedAt: time.Now(),
	}

	return analytics, nil
}

// GetTargetMetrics provides detailed metrics for a specific target
func (s *RoutingService) GetTargetMetrics(ctx context.Context, targetID string, timeframe string) (*TargetMetrics, error) {
	// Simulate metrics for the target
	metrics := &TargetMetrics{
		TargetID:       targetID,
		RequestCount:   int64(1500 + mathrand.Intn(500)),
		SuccessRate:    0.92 + mathrand.Float64()*(0.08), // 92-100%
		AverageLatency: 40 + mathrand.Float64()*30,       // 40-70ms
		ThroughputRPS:  150 + mathrand.Float64()*50,      // 150-200 RPS
		ResourceUtilization: LoadMetrics{
			CPUUsage:        60 + mathrand.Float64()*20,  // 60-80%
			MemoryUsage:     50 + mathrand.Float64()*30,  // 50-80%
			ConnectionCount: 100 + mathrand.Intn(50),     // 100-150
			RequestRate:     150 + mathrand.Float64()*50, // 150-200 RPS
			ErrorRate:       mathrand.Float64() * 3,      // 0-3%
			AverageLatency:  40 + mathrand.Float64()*30,  // 40-70ms
			QueueDepth:      mathrand.Intn(10),           // 0-10
			ThroughputMBps:  10 + mathrand.Float64()*20,  // 10-30 Mbps
		},
		Timeframe:   timeframe,
		GeneratedAt: time.Now(),
	}

	return metrics, nil
}

// GetDecisionHistory retrieves historical routing decisions
func (s *RoutingService) GetDecisionHistory(ctx context.Context, filters DecisionFilters) ([]RoutingDecision, error) {
	return s.repository.ListDecisions(ctx, filters)
}

// MonitorTargetHealth monitors the health of all targets
func (s *RoutingService) MonitorTargetHealth(ctx context.Context) error {
	// Get all targets
	targets, err := s.repository.ListTargets(ctx, TargetFilters{Limit: 1000})
	if err != nil {
		return fmt.Errorf("failed to get targets: %w", err)
	}

	// Check health of each target
	for _, target := range targets {
		health, err := s.GetTargetHealth(ctx, target.ID)
		if err != nil {
			// Log error but continue with other targets
			continue
		}

		// Update target health score
		target.HealthScore = health.OverallHealth
		s.repository.UpdateTarget(ctx, &target)
	}

	return nil
}

// OptimizeRouting analyzes and optimizes routing for a service
func (s *RoutingService) OptimizeRouting(ctx context.Context, serviceName string) (*OptimizationResult, error) {
	// Get current strategy for the service
	currentStrategy, err := s.getServiceStrategy(ctx, serviceName)
	if err != nil {
		return nil, fmt.Errorf("failed to get current strategy: %w", err)
	}

	// Analyze current performance
	currentPerf := s.simulateCurrentPerformance(serviceName)

	// Generate optimized strategy
	optimizedStrategy := s.generateOptimizedStrategy(currentStrategy, currentPerf)

	// Predict performance with optimized strategy
	predictedPerf := s.predictOptimizedPerformance(currentPerf, optimizedStrategy)

	// Calculate improvement
	improvement := s.calculateImprovement(currentPerf, predictedPerf)

	// Identify bottlenecks
	bottlenecks := s.identifyBottlenecks(currentPerf)

	// Generate recommendations
	recommendations := s.generateOptimizationActions(bottlenecks, currentStrategy)

	result := &OptimizationResult{
		ServiceName:         serviceName,
		CurrentStrategy:     *currentStrategy,
		RecommendedStrategy: optimizedStrategy,
		ExpectedImprovement: improvement,
		OptimizationReason:  []string{"Load balancing improvement", "Latency optimization"},
		AnalysisData: OptimizationAnalysis{
			CurrentPerformance:    currentPerf,
			PredictedPerformance:  predictedPerf,
			BottlenecksIdentified: bottlenecks,
			Recommendations:       recommendations,
			ConfidenceScore:       0.85,
			AnalysisMethod:        "Multi-criteria optimization with ML prediction",
		},
		ShouldApply: improvement > 5.0, // Apply if improvement > 5%
		GeneratedAt: time.Now(),
	}

	return result, nil
}

// Helper methods

func (s *RoutingService) getRoutingStrategy(ctx context.Context, request RoutingRequest) (*RoutingStrategy, error) {
	// Use request-specific strategy if provided
	if request.Strategy != nil {
		return request.Strategy, nil
	}

	// Try to get service-specific strategy
	strategyName := fmt.Sprintf("%s-strategy", request.ServiceName)
	strategy, err := s.GetStrategy(ctx, strategyName)
	if err == nil {
		return strategy, nil
	}

	// Fall back to default strategy
	return s.getDefaultStrategy(), nil
}

func (s *RoutingService) getCandidateTargets(ctx context.Context, request RoutingRequest, strategy *RoutingStrategy) ([]Target, error) {
	filters := TargetFilters{
		MinHealthScore: &s.config.HealthScoreThreshold,
		Limit:          s.config.MaxTargetsPerDecision,
	}

	// Apply geographic preferences
	if len(strategy.Preferences.PreferredRegions) > 0 {
		// For simplicity, just get all targets and filter later
	}

	// Apply required features
	if len(strategy.Preferences.RequiredFeatures) > 0 {
		filters.SupportedFeatures = strategy.Preferences.RequiredFeatures
	}

	return s.repository.ListTargets(ctx, filters)
}

func (s *RoutingService) applyRoutingRules(ctx context.Context, targets []Target, request RoutingRequest, strategy *RoutingStrategy) ([]Target, int) {
	filteredTargets := targets
	rulesApplied := 0

	// Apply strategy rules
	for _, rule := range strategy.Rules {
		if !rule.Enabled {
			continue
		}

		if s.evaluateRuleConditions(rule.Conditions, request) {
			filteredTargets = s.applyRuleActions(filteredTargets, rule.Actions, request)
			rulesApplied++
		}
	}

	return filteredTargets, rulesApplied
}

func (s *RoutingService) scoreTargets(ctx context.Context, targets []Target, request RoutingRequest, strategy *RoutingStrategy) []ScoredTarget {
	var scored []ScoredTarget

	for _, target := range targets {
		score := s.calculateTargetScore(target, request, strategy)
		scored = append(scored, ScoredTarget{
			Target: target,
			Score:  score,
		})
	}

	// Sort by score descending
	sort.Slice(scored, func(i, j int) bool {
		return scored[i].Score > scored[j].Score
	})

	return scored
}

func (s *RoutingService) calculateTargetScore(target Target, request RoutingRequest, strategy *RoutingStrategy) float64 {
	score := 0.0

	// Health component
	score += target.HealthScore * strategy.Weights.HealthWeight

	// Load component (lower load = higher score)
	loadScore := 1.0 - (target.CurrentLoad.CPUUsage / 100.0)
	score += loadScore * strategy.Weights.LoadWeight

	// Latency component (lower latency = higher score)
	latencyScore := math.Max(0, 1.0-(target.CurrentLoad.AverageLatency/1000.0)) // Normalize to 1s
	score += latencyScore * strategy.Weights.LatencyWeight

	// Cost component (simulate with random value)
	costScore := target.CostScore
	if costScore == 0 {
		costScore = 0.5 + mathrand.Float64()*0.5 // 0.5-1.0
	}
	score += costScore * strategy.Weights.CostWeight

	// Geographic component
	if strategy.Weights.GeographicWeight > 0 {
		geoScore := s.calculateGeographicScore(target, request)
		score += geoScore * strategy.Weights.GeographicWeight
	}

	// Compliance component
	complianceScore := target.ComplianceScore
	if complianceScore == 0 {
		complianceScore = 1.0 // Assume compliant if not specified
	}
	score += complianceScore * strategy.Weights.ComplianceWeight

	// Capacity component
	capacityScore := target.CapacityScore
	if capacityScore == 0 {
		capacityScore = 1.0 - (target.CurrentLoad.CPUUsage / 100.0) // Use CPU as proxy
	}
	score += capacityScore * strategy.Weights.CapacityWeight

	return score
}

func (s *RoutingService) calculateGeographicScore(target Target, request RoutingRequest) float64 {
	// Simple geographic scoring based on region match
	if target.Region == request.Metadata.GeographicHints.SourceRegion {
		return 1.0 // Perfect match
	}

	// Check if target region is in preferred regions
	for _, preferred := range request.Metadata.GeographicHints.TargetRegions {
		if target.Region == preferred {
			return 0.8 // Good match
		}
	}

	return 0.5 // Default score for other regions
}

func (s *RoutingService) selectBestTarget(scored []ScoredTarget, strategy *RoutingStrategy) (*Target, []Target) {
	if len(scored) == 0 {
		return nil, nil
	}

	// Select based on strategy type
	switch strategy.Type {
	case StrategyRoundRobin:
		// For round-robin, we'd need to track state
		// For simplicity, use the highest scored target
		return &scored[0].Target, s.getAlternatives(scored, 3)

	case StrategyLatencyBased:
		// Find target with lowest latency among top scorers
		return s.selectByLatency(scored), s.getAlternatives(scored, 3)

	case StrategyLoadBased:
		// Find target with lowest load among top scorers
		return s.selectByLoad(scored), s.getAlternatives(scored, 3)

	default:
		// Use highest scored target
		return &scored[0].Target, s.getAlternatives(scored, 3)
	}
}

func (s *RoutingService) selectByLatency(scored []ScoredTarget) *Target {
	// Take top 3 by score, then select by lowest latency
	topTargets := scored
	if len(topTargets) > 3 {
		topTargets = topTargets[:3]
	}

	bestTarget := &topTargets[0].Target
	bestLatency := topTargets[0].Target.CurrentLoad.AverageLatency

	for i := 1; i < len(topTargets); i++ {
		if topTargets[i].Target.CurrentLoad.AverageLatency < bestLatency {
			bestTarget = &topTargets[i].Target
			bestLatency = topTargets[i].Target.CurrentLoad.AverageLatency
		}
	}

	return bestTarget
}

func (s *RoutingService) selectByLoad(scored []ScoredTarget) *Target {
	// Take top 3 by score, then select by lowest CPU load
	topTargets := scored
	if len(topTargets) > 3 {
		topTargets = topTargets[:3]
	}

	bestTarget := &topTargets[0].Target
	bestLoad := topTargets[0].Target.CurrentLoad.CPUUsage

	for i := 1; i < len(topTargets); i++ {
		if topTargets[i].Target.CurrentLoad.CPUUsage < bestLoad {
			bestTarget = &topTargets[i].Target
			bestLoad = topTargets[i].Target.CurrentLoad.CPUUsage
		}
	}

	return bestTarget
}

func (s *RoutingService) getAlternatives(scored []ScoredTarget, count int) []Target {
	var alternatives []Target
	start := 1 // Skip the selected target

	for i := start; i < len(scored) && len(alternatives) < count; i++ {
		alternatives = append(alternatives, scored[i].Target)
	}

	return alternatives
}

func (s *RoutingService) calculateConfidenceScore(scored []ScoredTarget, selected *Target, strategy *RoutingStrategy) float64 {
	if len(scored) == 0 {
		return 0.0
	}

	// Base confidence on score of selected target
	baseConfidence := scored[0].Score // Assuming selected is first

	// Adjust based on score distribution
	if len(scored) > 1 {
		scoreDiff := scored[0].Score - scored[1].Score
		if scoreDiff > 0.2 {
			baseConfidence += 0.1 // Boost confidence if clear winner
		}
	}

	// Adjust based on target health
	if selected.HealthScore > 0.9 {
		baseConfidence += 0.05
	}

	return math.Min(1.0, baseConfidence)
}

func (s *RoutingService) generateDecisionReason(target *Target, strategy *RoutingStrategy) string {
	return fmt.Sprintf("Selected %s (score: %.3f) using %s strategy - Health: %.2f, Load: %.1f%%, Latency: %.1fms",
		target.Name, target.OverallScore, strategy.Type, target.HealthScore,
		target.CurrentLoad.CPUUsage, target.CurrentLoad.AverageLatency)
}

func (s *RoutingService) calculateScoreVariance(scored []ScoredTarget) float64 {
	if len(scored) <= 1 {
		return 0.0
	}

	var sum, sumSquares float64
	for _, st := range scored {
		sum += st.Score
		sumSquares += st.Score * st.Score
	}

	mean := sum / float64(len(scored))
	variance := (sumSquares / float64(len(scored))) - (mean * mean)
	return variance
}

func (s *RoutingService) getConfidenceLevel(score float64) string {
	if score >= 0.8 {
		return "HIGH"
	} else if score >= 0.6 {
		return "MEDIUM"
	}
	return "LOW"
}

func (s *RoutingService) calculateOverallTargetScore(target *Target) float64 {
	// Simple weighted average of individual scores
	weights := s.getDefaultWeights()
	score := 0.0

	score += target.HealthScore * weights.HealthWeight
	score += target.CapacityScore * weights.CapacityWeight
	score += target.LatencyScore * weights.LatencyWeight
	score += target.CostScore * weights.CostWeight
	score += target.ComplianceScore * weights.ComplianceWeight

	return score
}

func (s *RoutingService) evaluateRuleConditions(conditions []RuleCondition, request RoutingRequest) bool {
	// Simplified rule evaluation
	for _, condition := range conditions {
		if !s.evaluateCondition(condition, request) {
			return false // All conditions must be true
		}
	}
	return true
}

func (s *RoutingService) evaluateCondition(condition RuleCondition, request RoutingRequest) bool {
	// Simplified condition evaluation
	switch condition.Field {
	case "user_segment":
		result := request.Metadata.UserSegment == condition.Value
		if condition.Negate {
			result = !result
		}
		return result
	case "priority":
		result := string(request.Metadata.Priority) == condition.Value
		if condition.Negate {
			result = !result
		}
		return result
	default:
		return true // Unknown fields default to true
	}
}

func (s *RoutingService) applyRuleActions(targets []Target, actions []RuleAction, request RoutingRequest) []Target {
	// Simplified action application
	filteredTargets := targets

	for _, action := range actions {
		switch action.Type {
		case ActionRoute:
			// Filter to specific targets
			if targetID, ok := action.Parameters["target_id"].(string); ok {
				var filtered []Target
				for _, target := range filteredTargets {
					if target.ID == targetID {
						filtered = append(filtered, target)
					}
				}
				if len(filtered) > 0 {
					filteredTargets = filtered
				}
			}
		case ActionReject:
			// Remove specific targets
			if targetID, ok := action.Parameters["target_id"].(string); ok {
				var filtered []Target
				for _, target := range filteredTargets {
					if target.ID != targetID {
						filtered = append(filtered, target)
					}
				}
				filteredTargets = filtered
			}
		}
	}

	return filteredTargets
}

// Service-specific helpers

func (s *RoutingService) getServiceStrategy(ctx context.Context, serviceName string) (*RoutingStrategy, error) {
	strategyName := fmt.Sprintf("%s-strategy", serviceName)
	strategy, err := s.GetStrategy(ctx, strategyName)
	if err != nil {
		// Return default strategy if service-specific not found
		return s.getDefaultStrategy(), nil
	}
	return strategy, nil
}

func (s *RoutingService) simulateCurrentPerformance(serviceName string) PerformanceMetrics {
	return PerformanceMetrics{
		AverageLatency:     80 + mathrand.Float64()*40,   // 80-120ms
		P95Latency:         150 + mathrand.Float64()*100, // 150-250ms
		P99Latency:         300 + mathrand.Float64()*200, // 300-500ms
		ThroughputRPS:      200 + mathrand.Float64()*100, // 200-300 RPS
		ErrorRate:          1 + mathrand.Float64()*3,     // 1-4%
		ResourceEfficiency: 0.6 + mathrand.Float64()*0.3, // 60-90%
	}
}

func (s *RoutingService) generateOptimizedStrategy(current *RoutingStrategy, performance PerformanceMetrics) RoutingStrategy {
	optimized := *current // Copy current strategy

	// Adjust weights based on performance
	if performance.AverageLatency > 100 {
		optimized.Weights.LatencyWeight += 0.1
		optimized.Weights.LoadWeight -= 0.05
	}

	if performance.ErrorRate > 2 {
		optimized.Weights.HealthWeight += 0.1
		optimized.Weights.CostWeight -= 0.05
	}

	// Change strategy type if needed
	if performance.AverageLatency > 120 {
		optimized.Type = StrategyLatencyBased
	}

	return optimized
}

func (s *RoutingService) predictOptimizedPerformance(current PerformanceMetrics, optimized RoutingStrategy) PerformanceMetrics {
	predicted := current

	// Simulate improvements based on strategy changes
	if optimized.Type == StrategyLatencyBased {
		predicted.AverageLatency *= 0.85 // 15% improvement
		predicted.P95Latency *= 0.8      // 20% improvement
	}

	if optimized.Weights.HealthWeight > 0.4 {
		predicted.ErrorRate *= 0.7 // 30% improvement
	}

	predicted.ResourceEfficiency = math.Min(1.0, predicted.ResourceEfficiency*1.1) // 10% improvement

	return predicted
}

func (s *RoutingService) calculateImprovement(current, predicted PerformanceMetrics) float64 {
	latencyImprovement := (current.AverageLatency - predicted.AverageLatency) / current.AverageLatency
	errorImprovement := (current.ErrorRate - predicted.ErrorRate) / current.ErrorRate
	efficiencyImprovement := (predicted.ResourceEfficiency - current.ResourceEfficiency) / current.ResourceEfficiency

	// Weighted average of improvements
	totalImprovement := (latencyImprovement*0.4 + errorImprovement*0.3 + efficiencyImprovement*0.3) * 100

	return math.Max(0, totalImprovement)
}

func (s *RoutingService) identifyBottlenecks(performance PerformanceMetrics) []Bottleneck {
	var bottlenecks []Bottleneck

	if performance.AverageLatency > 100 {
		bottlenecks = append(bottlenecks, Bottleneck{
			Type:        "LATENCY",
			Severity:    "MEDIUM",
			Description: fmt.Sprintf("High average latency: %.1fms", performance.AverageLatency),
			Impact:      0.7,
			Targets:     []string{"service-a", "service-b"},
		})
	}

	if performance.ErrorRate > 2 {
		bottlenecks = append(bottlenecks, Bottleneck{
			Type:        "CAPACITY",
			Severity:    "HIGH",
			Description: fmt.Sprintf("High error rate: %.2f%%", performance.ErrorRate),
			Impact:      0.8,
			Targets:     []string{"service-c"},
		})
	}

	return bottlenecks
}

func (s *RoutingService) generateOptimizationActions(bottlenecks []Bottleneck, current *RoutingStrategy) []OptimizationAction {
	var actions []OptimizationAction

	for _, bottleneck := range bottlenecks {
		switch bottleneck.Type {
		case "LATENCY":
			actions = append(actions, OptimizationAction{
				Action:      "OPTIMIZE_LATENCY",
				Description: "Switch to latency-based routing strategy",
				Priority:    "HIGH",
				Impact:      0.8,
				Effort:      "LOW",
				Parameters: map[string]interface{}{
					"strategy_type":  "LATENCY_BASED",
					"latency_weight": 0.5,
					"load_weight":    0.3,
				},
			})
		case "CAPACITY":
			actions = append(actions, OptimizationAction{
				Action:      "IMPROVE_HEALTH_MONITORING",
				Description: "Increase health score weight in routing decisions",
				Priority:    "MEDIUM",
				Impact:      0.6,
				Effort:      "LOW",
				Parameters: map[string]interface{}{
					"health_weight":    0.4,
					"min_health_score": 0.8,
				},
			})
		}
	}

	return actions
}

// Helper types and functions

type ScoredTarget struct {
	Target Target
	Score  float64
}

func getDefaultServiceConfig() ServiceConfig {
	return ServiceConfig{
		DefaultStrategy:       StrategyMultiCriteria,
		DecisionTimeout:       500 * time.Millisecond,
		HealthCheckInterval:   30 * time.Second,
		CacheTimeout:          5 * time.Minute,
		MaxTargetsPerDecision: 20,
		LoadBalanceThreshold:  80.0,
		HealthScoreThreshold:  0.7,
		LatencyWeightDefault:  0.3,
		LoadWeightDefault:     0.3,
		HealthWeightDefault:   0.4,
		EnableMLOptimization:  true,
		CircuitBreakerEnabled: true,
	}
}

func (s *RoutingService) getDefaultStrategy() *RoutingStrategy {
	return &RoutingStrategy{
		Name:    "default",
		Type:    s.config.DefaultStrategy,
		Weights: s.getDefaultWeights(),
		Preferences: RoutingPreferences{
			MaxLatency:       200 * time.Millisecond,
			MinHealthScore:   s.config.HealthScoreThreshold,
			MaxLoadThreshold: s.config.LoadBalanceThreshold,
			LoadBalancing:    true,
		},
		Rules:         []RoutingRule{},
		Configuration: make(map[string]interface{}),
	}
}

func (s *RoutingService) getDefaultWeights() StrategyWeights {
	return StrategyWeights{
		LatencyWeight:    s.config.LatencyWeightDefault,
		LoadWeight:       s.config.LoadWeightDefault,
		HealthWeight:     s.config.HealthWeightDefault,
		CostWeight:       0.1,
		GeographicWeight: 0.2,
		ComplianceWeight: 0.2,
		CapacityWeight:   0.1,
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

func generateID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}
