package routing

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"
)

// InMemoryRepository implements the Repository interface using in-memory storage
type InMemoryRepository struct {
	decisions  map[string]*RoutingDecision
	targets    map[string]*Target
	strategies map[string]*RoutingStrategy
	rules      map[string]*RoutingRule
	mutex      sync.RWMutex
}

// NewInMemoryRepository creates a new in-memory repository
func NewInMemoryRepository() *InMemoryRepository {
	return &InMemoryRepository{
		decisions:  make(map[string]*RoutingDecision),
		targets:    make(map[string]*Target),
		strategies: make(map[string]*RoutingStrategy),
		rules:      make(map[string]*RoutingRule),
	}
}

// Decision management

func (r *InMemoryRepository) SaveDecision(ctx context.Context, decision *RoutingDecision) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	// Create a copy to avoid external modifications
	decisionCopy := *decision
	r.decisions[decision.ID] = &decisionCopy

	return nil
}

func (r *InMemoryRepository) GetDecision(ctx context.Context, decisionID string) (*RoutingDecision, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	decision, exists := r.decisions[decisionID]
	if !exists {
		return nil, fmt.Errorf("decision with ID %s not found", decisionID)
	}

	// Return a copy to avoid external modifications
	decisionCopy := *decision
	return &decisionCopy, nil
}

func (r *InMemoryRepository) UpdateDecision(ctx context.Context, decision *RoutingDecision) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if _, exists := r.decisions[decision.ID]; !exists {
		return fmt.Errorf("decision with ID %s not found", decision.ID)
	}

	// Create a copy to avoid external modifications
	decisionCopy := *decision
	r.decisions[decision.ID] = &decisionCopy

	return nil
}

func (r *InMemoryRepository) ListDecisions(ctx context.Context, filters DecisionFilters) ([]RoutingDecision, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var results []RoutingDecision
	count := 0

	for _, decision := range r.decisions {
		// Apply filters
		if filters.ServiceName != nil && decision.ServiceName != *filters.ServiceName {
			continue
		}

		if filters.Status != nil && decision.Status != *filters.Status {
			continue
		}

		if filters.StartTime != nil && decision.CreatedAt.Before(*filters.StartTime) {
			continue
		}

		if filters.EndTime != nil && decision.CreatedAt.After(*filters.EndTime) {
			continue
		}

		// Apply limit
		if filters.Limit > 0 && count >= filters.Limit {
			break
		}

		// Create a copy to avoid external modifications
		decisionCopy := *decision
		results = append(results, decisionCopy)
		count++
	}

	return results, nil
}

func (r *InMemoryRepository) DeleteDecision(ctx context.Context, decisionID string) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if _, exists := r.decisions[decisionID]; !exists {
		return fmt.Errorf("decision with ID %s not found", decisionID)
	}

	delete(r.decisions, decisionID)
	return nil
}

// Target management

func (r *InMemoryRepository) SaveTarget(ctx context.Context, target *Target) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	// Create a copy to avoid external modifications
	targetCopy := *target
	r.targets[target.ID] = &targetCopy

	return nil
}

func (r *InMemoryRepository) GetTarget(ctx context.Context, targetID string) (*Target, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	target, exists := r.targets[targetID]
	if !exists {
		return nil, fmt.Errorf("target with ID %s not found", targetID)
	}

	// Return a copy to avoid external modifications
	targetCopy := *target
	return &targetCopy, nil
}

func (r *InMemoryRepository) UpdateTarget(ctx context.Context, target *Target) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	_, exists := r.targets[target.ID]
	if !exists {
		return fmt.Errorf("target with ID %s not found", target.ID)
	}

	// Create a copy
	targetCopy := *target
	r.targets[target.ID] = &targetCopy

	return nil
}

func (r *InMemoryRepository) DeleteTarget(ctx context.Context, targetID string) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if _, exists := r.targets[targetID]; !exists {
		return fmt.Errorf("target with ID %s not found", targetID)
	}

	delete(r.targets, targetID)
	return nil
}

func (r *InMemoryRepository) ListTargets(ctx context.Context, filters TargetFilters) ([]Target, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var results []Target
	count := 0

	for _, target := range r.targets {
		// Apply filters
		if filters.Region != nil && target.Region != *filters.Region {
			continue
		}

		if filters.Zone != nil && target.Zone != *filters.Zone {
			continue
		}

		if filters.MinHealthScore != nil && target.HealthScore < *filters.MinHealthScore {
			continue
		}

		if filters.MaxLoadCPU != nil && target.CurrentLoad.CPUUsage > *filters.MaxLoadCPU {
			continue
		}

		if filters.TargetType != nil && target.Type != *filters.TargetType {
			continue
		}

		if len(filters.SupportedFeatures) > 0 {
			if !r.hasAllFeatures(target.SupportedFeatures, filters.SupportedFeatures) {
				continue
			}
		}

		// Apply limit
		if filters.Limit > 0 && count >= filters.Limit {
			break
		}

		// Create a copy to avoid external modifications
		targetCopy := *target
		results = append(results, targetCopy)
		count++
	}

	return results, nil
}

// Strategy management

func (r *InMemoryRepository) SaveStrategy(ctx context.Context, strategy *RoutingStrategy) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	// Create a copy to avoid external modifications
	strategyCopy := *strategy
	r.strategies[strategy.Name] = &strategyCopy

	return nil
}

func (r *InMemoryRepository) GetStrategy(ctx context.Context, strategyName string) (*RoutingStrategy, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	strategy, exists := r.strategies[strategyName]
	if !exists {
		return nil, fmt.Errorf("strategy with name %s not found", strategyName)
	}

	// Return a copy to avoid external modifications
	strategyCopy := *strategy
	return &strategyCopy, nil
}

func (r *InMemoryRepository) UpdateStrategy(ctx context.Context, strategy *RoutingStrategy) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	_, exists := r.strategies[strategy.Name]
	if !exists {
		return fmt.Errorf("strategy with name %s not found", strategy.Name)
	}

	// Create a copy
	strategyCopy := *strategy
	r.strategies[strategy.Name] = &strategyCopy

	return nil
}

func (r *InMemoryRepository) DeleteStrategy(ctx context.Context, strategyName string) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if _, exists := r.strategies[strategyName]; !exists {
		return fmt.Errorf("strategy with name %s not found", strategyName)
	}

	delete(r.strategies, strategyName)
	return nil
}

func (r *InMemoryRepository) ListStrategies(ctx context.Context) ([]RoutingStrategy, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var results []RoutingStrategy

	for _, strategy := range r.strategies {
		// Create a copy to avoid external modifications
		strategyCopy := *strategy
		results = append(results, strategyCopy)
	}

	return results, nil
}

// Rule management

func (r *InMemoryRepository) SaveRule(ctx context.Context, rule *RoutingRule) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	// Create a copy to avoid external modifications
	ruleCopy := *rule
	r.rules[rule.ID] = &ruleCopy

	return nil
}

func (r *InMemoryRepository) GetRule(ctx context.Context, ruleID string) (*RoutingRule, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	rule, exists := r.rules[ruleID]
	if !exists {
		return nil, fmt.Errorf("rule with ID %s not found", ruleID)
	}

	// Return a copy to avoid external modifications
	ruleCopy := *rule
	return &ruleCopy, nil
}

func (r *InMemoryRepository) UpdateRule(ctx context.Context, rule *RoutingRule) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	_, exists := r.rules[rule.ID]
	if !exists {
		return fmt.Errorf("rule with ID %s not found", rule.ID)
	}

	// Create a copy for update
	ruleCopy := *rule
	r.rules[rule.ID] = &ruleCopy

	return nil
}

func (r *InMemoryRepository) DeleteRule(ctx context.Context, ruleID string) error {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	if _, exists := r.rules[ruleID]; !exists {
		return fmt.Errorf("rule with ID %s not found", ruleID)
	}

	delete(r.rules, ruleID)
	return nil
}

func (r *InMemoryRepository) ListRules(ctx context.Context, filters RuleFilters) ([]RoutingRule, error) {
	r.mutex.RLock()
	defer r.mutex.RUnlock()

	var results []RoutingRule
	count := 0

	for _, rule := range r.rules {
		// Apply filters
		if filters.Enabled != nil && rule.Enabled != *filters.Enabled {
			continue
		}

		if filters.Priority != nil && rule.Priority != *filters.Priority {
			continue
		}

		// Apply limit
		if filters.Limit > 0 && count >= filters.Limit {
			break
		}

		// Create a copy to avoid external modifications
		ruleCopy := *rule
		results = append(results, ruleCopy)
		count++
	}

	return results, nil
}

// Helper methods

func (r *InMemoryRepository) hasAllFeatures(targetFeatures, requiredFeatures []string) bool {
	featureMap := make(map[string]bool)
	for _, feature := range targetFeatures {
		featureMap[strings.ToLower(feature)] = true
	}

	for _, required := range requiredFeatures {
		if !featureMap[strings.ToLower(required)] {
			return false
		}
	}

	return true
}

// Initialize with some default data for testing
func (r *InMemoryRepository) InitializeDefaults() {
	// Add default routing strategy
	defaultStrategy := &RoutingStrategy{
		Name: "default",
		Type: StrategyMultiCriteria,
		Weights: StrategyWeights{
			LatencyWeight:    0.3,
			LoadWeight:       0.3,
			HealthWeight:     0.4,
			CostWeight:       0.1,
			GeographicWeight: 0.2,
			ComplianceWeight: 0.2,
			CapacityWeight:   0.1,
		},
		Preferences: RoutingPreferences{
			MaxLatency:       200 * time.Millisecond,
			MinHealthScore:   0.7,
			MaxLoadThreshold: 80.0,
			LoadBalancing:    true,
		},
		Rules:         []RoutingRule{},
		Configuration: make(map[string]interface{}),
	}
	r.strategies["default"] = defaultStrategy

	// Add latency-based strategy
	latencyStrategy := &RoutingStrategy{
		Name: "latency-optimized",
		Type: StrategyLatencyBased,
		Weights: StrategyWeights{
			LatencyWeight:    0.6,
			LoadWeight:       0.2,
			HealthWeight:     0.2,
			CostWeight:       0.05,
			GeographicWeight: 0.3,
			ComplianceWeight: 0.1,
			CapacityWeight:   0.1,
		},
		Preferences: RoutingPreferences{
			MaxLatency:       100 * time.Millisecond,
			MinHealthScore:   0.8,
			MaxLoadThreshold: 70.0,
			LoadBalancing:    true,
		},
		Rules:         []RoutingRule{},
		Configuration: make(map[string]interface{}),
	}
	r.strategies["latency-optimized"] = latencyStrategy

	// Add load-based strategy
	loadStrategy := &RoutingStrategy{
		Name: "load-balanced",
		Type: StrategyLoadBased,
		Weights: StrategyWeights{
			LatencyWeight:    0.2,
			LoadWeight:       0.5,
			HealthWeight:     0.3,
			CostWeight:       0.1,
			GeographicWeight: 0.15,
			ComplianceWeight: 0.15,
			CapacityWeight:   0.2,
		},
		Preferences: RoutingPreferences{
			MaxLatency:       300 * time.Millisecond,
			MinHealthScore:   0.6,
			MaxLoadThreshold: 60.0,
			LoadBalancing:    true,
		},
		Rules:         []RoutingRule{},
		Configuration: make(map[string]interface{}),
	}
	r.strategies["load-balanced"] = loadStrategy
}
