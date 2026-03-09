package routing

import (
	"context"
	"time"
)

// RoutingDecision represents an intelligent routing decision
type RoutingDecision struct {
	ID                 string                 `json:"id"`
	RequestID          string                 `json:"request_id"`
	ServiceName        string                 `json:"service_name"`
	SourceRegion       string                 `json:"source_region"`
	RequestMetadata    RequestMetadata        `json:"request_metadata"`
	RoutingStrategy    RoutingStrategy        `json:"routing_strategy"`
	SelectedTarget     Target                 `json:"selected_target"`
	AlternativeTargets []Target               `json:"alternative_targets"`
	DecisionReason     string                 `json:"decision_reason"`
	ConfidenceScore    float64                `json:"confidence_score"`
	DecisionTime       time.Duration          `json:"decision_time"`
	Status             DecisionStatus         `json:"status"`
	CreatedAt          time.Time              `json:"created_at"`
	CompletedAt        *time.Time             `json:"completed_at,omitempty"`
	Metrics            DecisionMetrics        `json:"metrics"`
	Metadata           map[string]interface{} `json:"metadata"`
}

// RequestMetadata contains information about the incoming request
type RequestMetadata struct {
	UserID               string               `json:"user_id,omitempty"`
	UserSegment          string               `json:"user_segment,omitempty"`
	RequestType          string               `json:"request_type"`
	Priority             RequestPriority      `json:"priority"`
	ExpectedLatency      time.Duration        `json:"expected_latency"`
	RequiredSLA          ServiceLevel         `json:"required_sla"`
	Headers              map[string]string    `json:"headers"`
	GeographicHints      GeographicContext    `json:"geographic_hints"`
	LoadCharacteristics  LoadCharacteristics  `json:"load_characteristics"`
	SecurityRequirements SecurityRequirements `json:"security_requirements"`
	Constraints          RoutingConstraints   `json:"constraints"`
}

// Target represents a potential routing destination
type Target struct {
	ID                string                 `json:"id"`
	Name              string                 `json:"name"`
	Type              TargetType             `json:"type"`
	Region            string                 `json:"region"`
	Zone              string                 `json:"zone"`
	Endpoint          string                 `json:"endpoint"`
	CurrentLoad       LoadMetrics            `json:"current_load"`
	HealthScore       float64                `json:"health_score"`
	CapacityScore     float64                `json:"capacity_score"`
	LatencyScore      float64                `json:"latency_score"`
	CostScore         float64                `json:"cost_score"`
	ComplianceScore   float64                `json:"compliance_score"`
	OverallScore      float64                `json:"overall_score"`
	SupportedFeatures []string               `json:"supported_features"`
	ServiceLevels     []ServiceLevel         `json:"service_levels"`
	Metadata          map[string]interface{} `json:"metadata"`
}

// RoutingStrategy defines how routing decisions are made
type RoutingStrategy struct {
	Name             string                 `json:"name"`
	Type             StrategyType           `json:"type"`
	Weights          StrategyWeights        `json:"weights"`
	Preferences      RoutingPreferences     `json:"preferences"`
	FallbackStrategy *RoutingStrategy       `json:"fallback_strategy,omitempty"`
	Rules            []RoutingRule          `json:"rules"`
	Configuration    map[string]interface{} `json:"configuration"`
}

// StrategyWeights defines the importance of different factors
type StrategyWeights struct {
	LatencyWeight    float64 `json:"latency_weight"`    // Importance of low latency
	LoadWeight       float64 `json:"load_weight"`       // Importance of low load
	HealthWeight     float64 `json:"health_weight"`     // Importance of health status
	CostWeight       float64 `json:"cost_weight"`       // Importance of cost optimization
	GeographicWeight float64 `json:"geographic_weight"` // Importance of geographic proximity
	ComplianceWeight float64 `json:"compliance_weight"` // Importance of compliance requirements
	CapacityWeight   float64 `json:"capacity_weight"`   // Importance of available capacity
}

// RoutingPreferences defines preferences for routing decisions
type RoutingPreferences struct {
	PreferredRegions     []string      `json:"preferred_regions"`
	AvoidRegions         []string      `json:"avoid_regions"`
	MaxLatency           time.Duration `json:"max_latency"`
	MinHealthScore       float64       `json:"min_health_score"`
	MaxLoadThreshold     float64       `json:"max_load_threshold"`
	RequiredFeatures     []string      `json:"required_features"`
	PreferredTargetTypes []TargetType  `json:"preferred_target_types"`
	StickyRouting        bool          `json:"sticky_routing"` // Route similar requests to same target
	LoadBalancing        bool          `json:"load_balancing"` // Distribute load evenly
}

// RoutingRule defines conditional routing logic
type RoutingRule struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Priority    int                    `json:"priority"`
	Conditions  []RuleCondition        `json:"conditions"`
	Actions     []RuleAction           `json:"actions"`
	Enabled     bool                   `json:"enabled"`
	Description string                 `json:"description"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// RuleCondition defines a condition for routing rules
type RuleCondition struct {
	Field    string      `json:"field"`    // Field to check (e.g., "user_segment", "region")
	Operator string      `json:"operator"` // Comparison operator ("EQ", "NE", "IN", "GT", "LT")
	Value    interface{} `json:"value"`    // Value to compare against
	Negate   bool        `json:"negate"`   // Whether to negate the condition
}

// RuleAction defines an action to take when conditions are met
type RuleAction struct {
	Type       ActionType             `json:"type"`       // Type of action
	Parameters map[string]interface{} `json:"parameters"` // Action parameters
}

// LoadMetrics represents current load and performance metrics
type LoadMetrics struct {
	CPUUsage        float64 `json:"cpu_usage"`        // CPU utilization percentage
	MemoryUsage     float64 `json:"memory_usage"`     // Memory utilization percentage
	ConnectionCount int     `json:"connection_count"` // Active connections
	RequestRate     float64 `json:"request_rate"`     // Requests per second
	ErrorRate       float64 `json:"error_rate"`       // Error percentage
	AverageLatency  float64 `json:"average_latency"`  // Average response time (ms)
	QueueDepth      int     `json:"queue_depth"`      // Pending requests
	ThroughputMBps  float64 `json:"throughput_mbps"`  // Network throughput
}

// GeographicContext provides geographic information for routing
type GeographicContext struct {
	SourceRegion       string   `json:"source_region"`
	SourceCountry      string   `json:"source_country"`
	TargetRegions      []string `json:"target_regions,omitempty"`
	DataResidency      string   `json:"data_residency,omitempty"`  // Required data location
	ComplianceZone     string   `json:"compliance_zone,omitempty"` // Regulatory compliance zone
	PreferredProximity bool     `json:"preferred_proximity"`       // Prefer geographically close targets
}

// LoadCharacteristics describes the expected load pattern
type LoadCharacteristics struct {
	ExpectedDuration   time.Duration `json:"expected_duration"`   // Expected request duration
	ResourceIntensity  string        `json:"resource_intensity"`  // "LOW", "MEDIUM", "HIGH"
	ConcurrencyLevel   string        `json:"concurrency_level"`   // Expected concurrency
	MemoryRequirements string        `json:"memory_requirements"` // Memory needs
	CPURequirements    string        `json:"cpu_requirements"`    // CPU needs
	NetworkIntensive   bool          `json:"network_intensive"`   // High network usage expected
}

// SecurityRequirements defines security constraints for routing
type SecurityRequirements struct {
	RequireEncryption bool     `json:"require_encryption"` // Require TLS/encryption
	RequireMTLS       bool     `json:"require_mtls"`       // Require mutual TLS
	AllowedCiphers    []string `json:"allowed_ciphers"`    // Acceptable cipher suites
	ComplianceLevel   string   `json:"compliance_level"`   // Required compliance (e.g., "SOC2", "HIPAA")
	IsolationLevel    string   `json:"isolation_level"`    // Required isolation level
	AuditRequired     bool     `json:"audit_required"`     // Whether audit logging is required
}

// RoutingConstraints defines hard constraints for routing
type RoutingConstraints struct {
	ExcludeTargets   []string      `json:"exclude_targets"`   // Specific targets to exclude
	RequireTargets   []string      `json:"require_targets"`   // Only consider these targets
	MaxHops          int           `json:"max_hops"`          // Maximum network hops
	TimeoutThreshold time.Duration `json:"timeout_threshold"` // Maximum acceptable timeout
	RetryLimit       int           `json:"retry_limit"`       // Maximum retry attempts
	CircuitBreaker   bool          `json:"circuit_breaker"`   // Enable circuit breaker
}

// ServiceLevel defines service level requirements
type ServiceLevel struct {
	Name            string        `json:"name"`             // SLA name (e.g., "Gold", "Silver")
	MaxLatency      time.Duration `json:"max_latency"`      // Maximum acceptable latency
	MinThroughput   float64       `json:"min_throughput"`   // Minimum required throughput
	MaxErrorRate    float64       `json:"max_error_rate"`   // Maximum acceptable error rate
	AvailabilityPct float64       `json:"availability_pct"` // Required availability percentage
	Priority        int           `json:"priority"`         // Service priority level
}

// DecisionMetrics contains metrics about the routing decision
type DecisionMetrics struct {
	TargetsEvaluated   int           `json:"targets_evaluated"`   // Number of targets considered
	RulesApplied       int           `json:"rules_applied"`       // Number of routing rules applied
	DecisionTime       time.Duration `json:"decision_time"`       // Time taken to make decision
	CacheHitRate       float64       `json:"cache_hit_rate"`      // Rate of cache hits during decision
	FallbacksTriggered int           `json:"fallbacks_triggered"` // Number of fallback strategies used
	ScoreVariance      float64       `json:"score_variance"`      // Variance in target scores
	ConfidenceLevel    string        `json:"confidence_level"`    // "HIGH", "MEDIUM", "LOW"
}

// Enums and constants

// TargetType defines the type of routing target
type TargetType string

const (
	TargetTypeService   TargetType = "SERVICE"   // Microservice endpoint
	TargetTypeFunction  TargetType = "FUNCTION"  // Serverless function
	TargetTypeContainer TargetType = "CONTAINER" // Container instance
	TargetTypeVM        TargetType = "VM"        // Virtual machine
	TargetTypeEdge      TargetType = "EDGE"      // Edge computing node
	TargetTypeCDN       TargetType = "CDN"       // Content delivery network
)

// StrategyType defines the type of routing strategy
type StrategyType string

const (
	StrategyRoundRobin    StrategyType = "ROUND_ROBIN"    // Simple round-robin
	StrategyWeightedRound StrategyType = "WEIGHTED_ROUND" // Weighted round-robin
	StrategyLeastConn     StrategyType = "LEAST_CONN"     // Least connections
	StrategyLatencyBased  StrategyType = "LATENCY_BASED"  // Route to lowest latency
	StrategyLoadBased     StrategyType = "LOAD_BASED"     // Route to lowest load
	StrategyGeographic    StrategyType = "GEOGRAPHIC"     // Geographic proximity
	StrategyMultiCriteria StrategyType = "MULTI_CRITERIA" // Multiple criteria optimization
	StrategyML            StrategyType = "ML"             // Machine learning based
)

// RequestPriority defines the priority level of requests
type RequestPriority string

const (
	PriorityLow      RequestPriority = "LOW"      // Low priority requests
	PriorityNormal   RequestPriority = "NORMAL"   // Normal priority requests
	PriorityHigh     RequestPriority = "HIGH"     // High priority requests
	PriorityCritical RequestPriority = "CRITICAL" // Critical priority requests
)

// DecisionStatus defines the status of a routing decision
type DecisionStatus string

const (
	DecisionPending   DecisionStatus = "PENDING"   // Decision in progress
	DecisionCompleted DecisionStatus = "COMPLETED" // Decision completed successfully
	DecisionFailed    DecisionStatus = "FAILED"    // Decision failed
	DecisionTimeout   DecisionStatus = "TIMEOUT"   // Decision timed out
	DecisionCancelled DecisionStatus = "CANCELLED" // Decision was cancelled
)

// ActionType defines the type of routing rule action
type ActionType string

const (
	ActionRoute     ActionType = "ROUTE"     // Route to specific target
	ActionReject    ActionType = "REJECT"    // Reject the request
	ActionRedirect  ActionType = "REDIRECT"  // Redirect to different endpoint
	ActionTransform ActionType = "TRANSFORM" // Transform the request
	ActionThrottle  ActionType = "THROTTLE"  // Apply rate limiting
	ActionCache     ActionType = "CACHE"     // Cache the response
	ActionLog       ActionType = "LOG"       // Log specific information
	ActionAlert     ActionType = "ALERT"     // Trigger an alert
)

// Service defines the interface for smart routing operations
type Service interface {
	// Routing decisions
	MakeRoutingDecision(ctx context.Context, request RoutingRequest) (*RoutingDecision, error)
	GetRoutingDecision(ctx context.Context, decisionID string) (*RoutingDecision, error)
	UpdateDecisionStatus(ctx context.Context, decisionID string, status DecisionStatus) error

	// Target management
	RegisterTarget(ctx context.Context, target *Target) error
	UpdateTarget(ctx context.Context, target *Target) error
	DeregisterTarget(ctx context.Context, targetID string) error
	ListTargets(ctx context.Context, filters TargetFilters) ([]Target, error)
	GetTargetHealth(ctx context.Context, targetID string) (*TargetHealth, error)

	// Strategy management
	CreateStrategy(ctx context.Context, strategy *RoutingStrategy) error
	UpdateStrategy(ctx context.Context, strategy *RoutingStrategy) error
	GetStrategy(ctx context.Context, strategyName string) (*RoutingStrategy, error)
	ListStrategies(ctx context.Context) ([]RoutingStrategy, error)

	// Rule management
	CreateRule(ctx context.Context, rule *RoutingRule) error
	UpdateRule(ctx context.Context, rule *RoutingRule) error
	DeleteRule(ctx context.Context, ruleID string) error
	ListRules(ctx context.Context, filters RuleFilters) ([]RoutingRule, error)

	// Analytics and monitoring
	GetRoutingAnalytics(ctx context.Context, timeframe string) (*RoutingAnalytics, error)
	GetTargetMetrics(ctx context.Context, targetID string, timeframe string) (*TargetMetrics, error)
	GetDecisionHistory(ctx context.Context, filters DecisionFilters) ([]RoutingDecision, error)

	// Real-time monitoring
	MonitorTargetHealth(ctx context.Context) error
	OptimizeRouting(ctx context.Context, serviceName string) (*OptimizationResult, error)
}

// Repository defines the interface for routing data access
type Repository interface {
	// Decision operations
	SaveDecision(ctx context.Context, decision *RoutingDecision) error
	GetDecision(ctx context.Context, decisionID string) (*RoutingDecision, error)
	UpdateDecision(ctx context.Context, decision *RoutingDecision) error
	ListDecisions(ctx context.Context, filters DecisionFilters) ([]RoutingDecision, error)

	// Target operations
	SaveTarget(ctx context.Context, target *Target) error
	GetTarget(ctx context.Context, targetID string) (*Target, error)
	UpdateTarget(ctx context.Context, target *Target) error
	DeleteTarget(ctx context.Context, targetID string) error
	ListTargets(ctx context.Context, filters TargetFilters) ([]Target, error)

	// Strategy operations
	SaveStrategy(ctx context.Context, strategy *RoutingStrategy) error
	GetStrategy(ctx context.Context, strategyName string) (*RoutingStrategy, error)
	UpdateStrategy(ctx context.Context, strategy *RoutingStrategy) error
	ListStrategies(ctx context.Context) ([]RoutingStrategy, error)

	// Rule operations
	SaveRule(ctx context.Context, rule *RoutingRule) error
	GetRule(ctx context.Context, ruleID string) (*RoutingRule, error)
	UpdateRule(ctx context.Context, rule *RoutingRule) error
	DeleteRule(ctx context.Context, ruleID string) error
	ListRules(ctx context.Context, filters RuleFilters) ([]RoutingRule, error)

	// Metrics operations
	SaveDecisionMetrics(ctx context.Context, decisionID string, metrics DecisionMetrics) error
	GetTargetMetrics(ctx context.Context, targetID string, timeframe string) (*TargetMetrics, error)
}

// Supporting types for requests, filters, and reports

// RoutingRequest represents an incoming request that needs routing
type RoutingRequest struct {
	RequestID   string                 `json:"request_id"`
	ServiceName string                 `json:"service_name"`
	RequestType string                 `json:"request_type"`
	Metadata    RequestMetadata        `json:"metadata"`
	Strategy    *RoutingStrategy       `json:"strategy,omitempty"`    // Override default strategy
	Constraints *RoutingConstraints    `json:"constraints,omitempty"` // Additional constraints
	Context     map[string]interface{} `json:"context"`               // Additional context
}

// TargetFilters defines filters for target queries
type TargetFilters struct {
	TargetType        *TargetType `json:"target_type,omitempty"`
	Region            *string     `json:"region,omitempty"`
	Zone              *string     `json:"zone,omitempty"`
	MinHealthScore    *float64    `json:"min_health_score,omitempty"`
	MaxLoadCPU        *float64    `json:"max_load_cpu,omitempty"`
	SupportedFeatures []string    `json:"supported_features,omitempty"`
	ServiceLevels     []string    `json:"service_levels,omitempty"`
	Limit             int         `json:"limit,omitempty"`
	Offset            int         `json:"offset,omitempty"`
}

// RuleFilters defines filters for rule queries
type RuleFilters struct {
	Enabled     *bool   `json:"enabled,omitempty"`
	Priority    *int    `json:"priority,omitempty"`
	ServiceName *string `json:"service_name,omitempty"`
	Limit       int     `json:"limit,omitempty"`
	Offset      int     `json:"offset,omitempty"`
}

// DecisionFilters defines filters for decision queries
type DecisionFilters struct {
	ServiceName  *string         `json:"service_name,omitempty"`
	SourceRegion *string         `json:"source_region,omitempty"`
	Status       *DecisionStatus `json:"status,omitempty"`
	StartTime    *time.Time      `json:"start_time,omitempty"`
	EndTime      *time.Time      `json:"end_time,omitempty"`
	Limit        int             `json:"limit,omitempty"`
	Offset       int             `json:"offset,omitempty"`
}

// TargetHealth represents the health status of a target
type TargetHealth struct {
	TargetID        string                 `json:"target_id"`
	OverallHealth   float64                `json:"overall_health"`
	HealthFactors   []HealthFactor         `json:"health_factors"`
	Status          string                 `json:"status"` // "HEALTHY", "WARNING", "CRITICAL"
	LastChecked     time.Time              `json:"last_checked"`
	Recommendations []string               `json:"recommendations"`
	Metadata        map[string]interface{} `json:"metadata"`
}

// HealthFactor represents a factor affecting target health
type HealthFactor struct {
	Name        string  `json:"name"`
	Score       float64 `json:"score"`  // 0-1 score
	Weight      float64 `json:"weight"` // Importance weight
	Status      string  `json:"status"` // "HEALTHY", "WARNING", "CRITICAL"
	Description string  `json:"description"`
	Threshold   float64 `json:"threshold"` // Warning threshold
}

// RoutingAnalytics represents analytics for routing decisions
type RoutingAnalytics struct {
	TotalDecisions       int                  `json:"total_decisions"`
	SuccessRate          float64              `json:"success_rate"`
	AverageDecisionTime  float64              `json:"average_decision_time"`
	TargetUtilization    map[string]float64   `json:"target_utilization"`
	StrategyDistribution map[StrategyType]int `json:"strategy_distribution"`
	RegionDistribution   map[string]int       `json:"region_distribution"`
	ErrorDistribution    map[string]int       `json:"error_distribution"`
	LatencyDistribution  []LatencyBucket      `json:"latency_distribution"`
	TopTargets           []TargetUsageStats   `json:"top_targets"`
	Timeframe            string               `json:"timeframe"`
	GeneratedAt          time.Time            `json:"generated_at"`
}

// TargetMetrics represents metrics for a specific target
type TargetMetrics struct {
	TargetID            string                `json:"target_id"`
	RequestCount        int64                 `json:"request_count"`
	SuccessRate         float64               `json:"success_rate"`
	AverageLatency      float64               `json:"average_latency"`
	ThroughputRPS       float64               `json:"throughput_rps"`
	ResourceUtilization LoadMetrics           `json:"resource_utilization"`
	HealthHistory       []HealthSnapshot      `json:"health_history"`
	PerformanceTrends   []PerformanceSnapshot `json:"performance_trends"`
	Timeframe           string                `json:"timeframe"`
	GeneratedAt         time.Time             `json:"generated_at"`
}

// OptimizationResult represents the result of routing optimization
type OptimizationResult struct {
	ServiceName         string               `json:"service_name"`
	CurrentStrategy     RoutingStrategy      `json:"current_strategy"`
	RecommendedStrategy RoutingStrategy      `json:"recommended_strategy"`
	ExpectedImprovement float64              `json:"expected_improvement"` // Percentage improvement
	OptimizationReason  []string             `json:"optimization_reason"`
	AnalysisData        OptimizationAnalysis `json:"analysis_data"`
	ShouldApply         bool                 `json:"should_apply"`
	GeneratedAt         time.Time            `json:"generated_at"`
}

// Supporting analytics types

// LatencyBucket represents latency distribution bucket
type LatencyBucket struct {
	MinLatency time.Duration `json:"min_latency"`
	MaxLatency time.Duration `json:"max_latency"`
	Count      int           `json:"count"`
	Percentage float64       `json:"percentage"`
}

// TargetUsageStats represents usage statistics for a target
type TargetUsageStats struct {
	TargetID     string  `json:"target_id"`
	TargetName   string  `json:"target_name"`
	RequestCount int64   `json:"request_count"`
	SuccessRate  float64 `json:"success_rate"`
	AvgLatency   float64 `json:"avg_latency"`
	Utilization  float64 `json:"utilization"`
}

// HealthSnapshot represents health at a point in time
type HealthSnapshot struct {
	Timestamp    time.Time `json:"timestamp"`
	HealthScore  float64   `json:"health_score"`
	CPUUsage     float64   `json:"cpu_usage"`
	MemoryUsage  float64   `json:"memory_usage"`
	ErrorRate    float64   `json:"error_rate"`
	ResponseTime float64   `json:"response_time"`
}

// PerformanceSnapshot represents performance at a point in time
type PerformanceSnapshot struct {
	Timestamp     time.Time `json:"timestamp"`
	RequestCount  int64     `json:"request_count"`
	SuccessRate   float64   `json:"success_rate"`
	AvgLatency    float64   `json:"avg_latency"`
	ThroughputRPS float64   `json:"throughput_rps"`
}

// OptimizationAnalysis contains detailed analysis for optimization
type OptimizationAnalysis struct {
	CurrentPerformance    PerformanceMetrics   `json:"current_performance"`
	PredictedPerformance  PerformanceMetrics   `json:"predicted_performance"`
	BottlenecksIdentified []Bottleneck         `json:"bottlenecks_identified"`
	Recommendations       []OptimizationAction `json:"recommendations"`
	ConfidenceScore       float64              `json:"confidence_score"`
	AnalysisMethod        string               `json:"analysis_method"`
}

// PerformanceMetrics represents performance measurements
type PerformanceMetrics struct {
	AverageLatency     float64 `json:"average_latency"`
	P95Latency         float64 `json:"p95_latency"`
	P99Latency         float64 `json:"p99_latency"`
	ThroughputRPS      float64 `json:"throughput_rps"`
	ErrorRate          float64 `json:"error_rate"`
	ResourceEfficiency float64 `json:"resource_efficiency"`
}

// Bottleneck represents an identified performance bottleneck
type Bottleneck struct {
	Type        string   `json:"type"`     // "LATENCY", "CAPACITY", "GEOGRAPHIC"
	Severity    string   `json:"severity"` // "LOW", "MEDIUM", "HIGH"
	Description string   `json:"description"`
	Impact      float64  `json:"impact"`  // Impact score 0-1
	Targets     []string `json:"targets"` // Affected targets
}

// OptimizationAction represents a recommended optimization action
type OptimizationAction struct {
	Action      string                 `json:"action"`      // Action type
	Description string                 `json:"description"` // Action description
	Priority    string                 `json:"priority"`    // "LOW", "MEDIUM", "HIGH"
	Impact      float64                `json:"impact"`      // Expected impact 0-1
	Effort      string                 `json:"effort"`      // Implementation effort
	Parameters  map[string]interface{} `json:"parameters"`  // Action parameters
}
