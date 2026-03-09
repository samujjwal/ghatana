package canary

import (
	"context"
	"time"
)

// CanarySelection represents a smart canary deployment selection
type CanarySelection struct {
	ID                string                 `json:"id"`
	DeploymentID      string                 `json:"deployment_id"`
	ServiceName       string                 `json:"service_name"`
	Version           string                 `json:"version"`
	SelectionCriteria SelectionCriteria      `json:"selection_criteria"`
	SelectedNodes     []NodeCandidate        `json:"selected_nodes"`
	RiskScore         float64                `json:"risk_score"`
	ConfidenceScore   float64                `json:"confidence_score"`
	Status            SelectionStatus        `json:"status"`
	CreatedAt         time.Time              `json:"created_at"`
	UpdatedAt         time.Time              `json:"updated_at"`
	Metrics           SelectionMetrics       `json:"metrics"`
	Metadata          map[string]interface{} `json:"metadata"`
}

// SelectionCriteria defines the criteria for canary selection
type SelectionCriteria struct {
	TrafficPercentage      float64               `json:"traffic_percentage"`      // Desired traffic percentage
	GeographicDistribution bool                  `json:"geographic_distribution"` // Spread across regions
	LoadBalancing          bool                  `json:"load_balancing"`          // Balance load characteristics
	UserSegmentation       UserSegmentation      `json:"user_segmentation"`       // Target specific user types
	RiskTolerance          RiskLevel             `json:"risk_tolerance"`          // Acceptable risk level
	Duration               time.Duration         `json:"duration"`                // Planned canary duration
	SuccessMetrics         []SuccessMetric       `json:"success_metrics"`         // Required success criteria
	RollbackTriggers       []RollbackTrigger     `json:"rollback_triggers"`       // Automatic rollback conditions
	Constraints            DeploymentConstraints `json:"constraints"`             // Deployment constraints
}

// NodeCandidate represents a potential canary deployment target
type NodeCandidate struct {
	NodeID          string                 `json:"node_id"`
	HostName        string                 `json:"hostname"`
	Region          string                 `json:"region"`
	Zone            string                 `json:"zone"`
	InstanceType    string                 `json:"instance_type"`
	CurrentLoad     LoadMetrics            `json:"current_load"`
	HealthScore     float64                `json:"health_score"`     // 0-1 health rating
	StabilityScore  float64                `json:"stability_score"`  // Historical stability
	TrafficWeight   float64                `json:"traffic_weight"`   // Current traffic percentage
	UserSegments    []string               `json:"user_segments"`    // User types served
	SelectionReason string                 `json:"selection_reason"` // Why this node was selected
	RiskFactors     []RiskFactor           `json:"risk_factors"`     // Identified risks
	Metadata        map[string]interface{} `json:"metadata"`
}

// LoadMetrics represents current system load
type LoadMetrics struct {
	CPUUsage     float64 `json:"cpu_usage"`     // CPU utilization percentage
	MemoryUsage  float64 `json:"memory_usage"`  // Memory utilization percentage
	DiskUsage    float64 `json:"disk_usage"`    // Disk utilization percentage
	NetworkLoad  float64 `json:"network_load"`  // Network utilization
	RequestRate  float64 `json:"request_rate"`  // Requests per second
	ErrorRate    float64 `json:"error_rate"`    // Error percentage
	ResponseTime float64 `json:"response_time"` // Average response time (ms)
	QueueDepth   int     `json:"queue_depth"`   // Current queue depth
}

// UserSegmentation defines user targeting for canary
type UserSegmentation struct {
	EnableSegmentation bool     `json:"enable_segmentation"`
	TargetSegments     []string `json:"target_segments"`   // e.g., "beta_users", "enterprise"
	ExcludeSegments    []string `json:"exclude_segments"`  // Segments to avoid
	SamplingStrategy   string   `json:"sampling_strategy"` // "RANDOM", "HASH", "STICKY"
	UserIDField        string   `json:"user_id_field"`     // Field to use for user identification
}

// SuccessMetric defines a metric that must be met for canary success
type SuccessMetric struct {
	Name       string        `json:"name"`        // Metric name (e.g., "error_rate")
	Threshold  float64       `json:"threshold"`   // Success threshold
	Operator   string        `json:"operator"`    // Comparison operator ("LT", "GT", "EQ")
	TimeWindow time.Duration `json:"time_window"` // Evaluation window
	Critical   bool          `json:"critical"`    // Whether failure triggers immediate rollback
}

// RollbackTrigger defines conditions that trigger automatic rollback
type RollbackTrigger struct {
	Name         string        `json:"name"`          // Trigger name
	MetricName   string        `json:"metric_name"`   // Metric to monitor
	Threshold    float64       `json:"threshold"`     // Trigger threshold
	Operator     string        `json:"operator"`      // Comparison operator
	Duration     time.Duration `json:"duration"`      // How long condition must persist
	Severity     string        `json:"severity"`      // "WARNING", "CRITICAL"
	AutoRollback bool          `json:"auto_rollback"` // Whether to rollback automatically
}

// DeploymentConstraints defines deployment limitations
type DeploymentConstraints struct {
	MaxNodesPerRegion  int                 `json:"max_nodes_per_region"`
	MaxNodesPerZone    int                 `json:"max_nodes_per_zone"`
	RequiredRegions    []string            `json:"required_regions"`
	ExcludedRegions    []string            `json:"excluded_regions"`
	MinHealthScore     float64             `json:"min_health_score"`
	MaxLoadThreshold   float64             `json:"max_load_threshold"`
	RequiredTags       map[string]string   `json:"required_tags"`
	MaintenanceWindows []MaintenanceWindow `json:"maintenance_windows"`
}

// MaintenanceWindow defines when deployments are allowed
type MaintenanceWindow struct {
	StartTime time.Time `json:"start_time"`
	EndTime   time.Time `json:"end_time"`
	Timezone  string    `json:"timezone"`
	Recurring bool      `json:"recurring"`
	Days      []string  `json:"days"` // Day names for recurring windows
}

// RiskFactor represents a potential risk in the deployment
type RiskFactor struct {
	Type        RiskType `json:"type"`
	Severity    string   `json:"severity"` // "LOW", "MEDIUM", "HIGH"
	Description string   `json:"description"`
	Impact      float64  `json:"impact"`     // Impact score 0-1
	Likelihood  float64  `json:"likelihood"` // Likelihood score 0-1
	Mitigation  string   `json:"mitigation"` // Mitigation strategy
}

// RiskType defines categories of deployment risks
type RiskType string

const (
	RiskTypeHardware   RiskType = "HARDWARE"    // Hardware-related risks
	RiskTypeNetwork    RiskType = "NETWORK"     // Network connectivity risks
	RiskTypeCapacity   RiskType = "CAPACITY"    // Resource capacity risks
	RiskTypeGeographic RiskType = "GEOGRAPHIC"  // Geographic distribution risks
	RiskTypeUserImpact RiskType = "USER_IMPACT" // Potential user impact
	RiskTypeCompliance RiskType = "COMPLIANCE"  // Regulatory compliance risks
	RiskTypeDependency RiskType = "DEPENDENCY"  // External dependency risks
)

// RiskLevel defines acceptable risk levels
type RiskLevel string

const (
	RiskLevelVeryLow RiskLevel = "VERY_LOW" // Minimal risk tolerance
	RiskLevelLow     RiskLevel = "LOW"      // Low risk tolerance
	RiskLevelMedium  RiskLevel = "MEDIUM"   // Moderate risk tolerance
	RiskLevelHigh    RiskLevel = "HIGH"     // High risk tolerance
)

// SelectionStatus defines the status of canary selection
type SelectionStatus string

const (
	StatusSelecting  SelectionStatus = "SELECTING"   // Selection in progress
	StatusSelected   SelectionStatus = "SELECTED"    // Nodes selected
	StatusDeploying  SelectionStatus = "DEPLOYING"   // Deployment in progress
	StatusActive     SelectionStatus = "ACTIVE"      // Canary is active
	StatusSuccess    SelectionStatus = "SUCCESS"     // Canary succeeded
	StatusFailed     SelectionStatus = "FAILED"      // Canary failed
	StatusRolledBack SelectionStatus = "ROLLED_BACK" // Rolled back
)

// SelectionMetrics contains metrics about the selection process
type SelectionMetrics struct {
	TotalCandidates     int     `json:"total_candidates"`     // Total available candidates
	QualifiedCandidates int     `json:"qualified_candidates"` // Candidates meeting criteria
	SelectedCandidates  int     `json:"selected_candidates"`  // Final selected candidates
	SelectionTime       float64 `json:"selection_time"`       // Time taken to select (seconds)
	AverageRiskScore    float64 `json:"average_risk_score"`   // Average risk of selected nodes
	GeographicCoverage  float64 `json:"geographic_coverage"`  // Geographic distribution score
	LoadBalanceScore    float64 `json:"load_balance_score"`   // Load balancing effectiveness
	DiversityScore      float64 `json:"diversity_score"`      // Diversity of selected nodes
}

// Service defines the interface for canary selection operations
type Service interface {
	// Selection operations
	SelectCanaryNodes(ctx context.Context, criteria SelectionCriteria) (*CanarySelection, error)
	GetCanarySelection(ctx context.Context, selectionID string) (*CanarySelection, error)
	UpdateSelectionStatus(ctx context.Context, selectionID string, status SelectionStatus) error

	// Node operations
	ListCandidateNodes(ctx context.Context, filters NodeFilters) ([]NodeCandidate, error)
	GetNodeMetrics(ctx context.Context, nodeID string) (*LoadMetrics, error)
	ValidateNodeHealth(ctx context.Context, nodeID string) (*HealthReport, error)

	// Risk assessment
	AssessDeploymentRisk(ctx context.Context, selection *CanarySelection) (*RiskAssessment, error)
	GetRiskRecommendations(ctx context.Context, riskFactors []RiskFactor) ([]RiskMitigation, error)

	// Monitoring and control
	MonitorCanaryHealth(ctx context.Context, selectionID string) (*CanaryHealthReport, error)
	TriggerRollback(ctx context.Context, selectionID string, reason string) error
	GetSelectionHistory(ctx context.Context, serviceID string, limit int) ([]CanarySelection, error)

	// Analytics
	GetSelectionAnalytics(ctx context.Context, timeframe string) (*SelectionAnalytics, error)
	GetPerformanceMetrics(ctx context.Context, selectionID string) (*PerformanceReport, error)
}

// Repository defines the interface for canary selection data access
type Repository interface {
	// Selection operations
	SaveSelection(ctx context.Context, selection *CanarySelection) error
	GetSelection(ctx context.Context, selectionID string) (*CanarySelection, error)
	UpdateSelection(ctx context.Context, selection *CanarySelection) error
	DeleteSelection(ctx context.Context, selectionID string) error
	ListSelections(ctx context.Context, filters SelectionFilters) ([]CanarySelection, error)

	// Node operations
	SaveNode(ctx context.Context, node *NodeCandidate) error
	GetNode(ctx context.Context, nodeID string) (*NodeCandidate, error)
	UpdateNodeMetrics(ctx context.Context, nodeID string, metrics LoadMetrics) error
	ListNodes(ctx context.Context, filters NodeFilters) ([]NodeCandidate, error)

	// Metrics and history
	SaveSelectionMetrics(ctx context.Context, selectionID string, metrics SelectionMetrics) error
	GetSelectionHistory(ctx context.Context, serviceID string, limit int) ([]CanarySelection, error)
}

// Supporting types for filters and reports

// NodeFilters defines filters for node queries
type NodeFilters struct {
	Region         *string           `json:"region,omitempty"`
	Zone           *string           `json:"zone,omitempty"`
	InstanceType   *string           `json:"instance_type,omitempty"`
	MinHealthScore *float64          `json:"min_health_score,omitempty"`
	MaxLoadCPU     *float64          `json:"max_load_cpu,omitempty"`
	UserSegments   []string          `json:"user_segments,omitempty"`
	Tags           map[string]string `json:"tags,omitempty"`
	Limit          int               `json:"limit,omitempty"`
	Offset         int               `json:"offset,omitempty"`
}

// SelectionFilters defines filters for selection queries
type SelectionFilters struct {
	ServiceName   *string          `json:"service_name,omitempty"`
	Status        *SelectionStatus `json:"status,omitempty"`
	CreatedAfter  *time.Time       `json:"created_after,omitempty"`
	CreatedBefore *time.Time       `json:"created_before,omitempty"`
	Limit         int              `json:"limit,omitempty"`
	Offset        int              `json:"offset,omitempty"`
}

// HealthReport represents node health assessment
type HealthReport struct {
	NodeID          string                 `json:"node_id"`
	OverallHealth   float64                `json:"overall_health"`
	HealthFactors   []HealthFactor         `json:"health_factors"`
	Recommendations []string               `json:"recommendations"`
	LastChecked     time.Time              `json:"last_checked"`
	Metadata        map[string]interface{} `json:"metadata"`
}

// HealthFactor represents a factor affecting node health
type HealthFactor struct {
	Name        string  `json:"name"`
	Score       float64 `json:"score"`  // 0-1 score
	Weight      float64 `json:"weight"` // Importance weight
	Status      string  `json:"status"` // "HEALTHY", "WARNING", "CRITICAL"
	Description string  `json:"description"`
}

// RiskAssessment represents overall deployment risk analysis
type RiskAssessment struct {
	SelectionID     string           `json:"selection_id"`
	OverallRisk     float64          `json:"overall_risk"` // 0-1 risk score
	RiskLevel       RiskLevel        `json:"risk_level"`
	RiskFactors     []RiskFactor     `json:"risk_factors"`
	Mitigations     []RiskMitigation `json:"mitigations"`
	Recommendations []string         `json:"recommendations"`
	AssessedAt      time.Time        `json:"assessed_at"`
}

// RiskMitigation represents a strategy to reduce risk
type RiskMitigation struct {
	RiskType           RiskType      `json:"risk_type"`
	Strategy           string        `json:"strategy"`
	EffectivenessScore float64       `json:"effectiveness_score"` // 0-1 effectiveness
	ImplementationCost string        `json:"implementation_cost"` // "LOW", "MEDIUM", "HIGH"
	TimeToImplement    time.Duration `json:"time_to_implement"`
	Description        string        `json:"description"`
}

// CanaryHealthReport represents current canary health status
type CanaryHealthReport struct {
	SelectionID     string                 `json:"selection_id"`
	OverallHealth   float64                `json:"overall_health"`
	NodeHealth      map[string]float64     `json:"node_health"`   // Health by node
	MetricStatus    map[string]string      `json:"metric_status"` // Status by metric
	ActiveAlerts    []HealthAlert          `json:"active_alerts"`
	Recommendations []string               `json:"recommendations"`
	LastUpdated     time.Time              `json:"last_updated"`
	Metadata        map[string]interface{} `json:"metadata"`
}

// HealthAlert represents a health-related alert
type HealthAlert struct {
	ID           string    `json:"id"`
	Severity     string    `json:"severity"` // "INFO", "WARNING", "CRITICAL"
	Title        string    `json:"title"`
	Description  string    `json:"description"`
	NodeID       string    `json:"node_id,omitempty"`
	MetricName   string    `json:"metric_name,omitempty"`
	Threshold    float64   `json:"threshold,omitempty"`
	CurrentValue float64   `json:"current_value,omitempty"`
	CreatedAt    time.Time `json:"created_at"`
}

// SelectionAnalytics represents analytics for canary selections
type SelectionAnalytics struct {
	TotalSelections      int                `json:"total_selections"`
	SuccessRate          float64            `json:"success_rate"`
	AverageSelectionTime float64            `json:"average_selection_time"`
	RiskDistribution     map[RiskLevel]int  `json:"risk_distribution"`
	PopularRegions       []RegionStats      `json:"popular_regions"`
	CommonRiskFactors    []RiskFactorStats  `json:"common_risk_factors"`
	PerformanceTrends    []PerformanceTrend `json:"performance_trends"`
	Timeframe            string             `json:"timeframe"`
	GeneratedAt          time.Time          `json:"generated_at"`
}

// RegionStats represents statistics for a region
type RegionStats struct {
	Region           string  `json:"region"`
	SelectionCount   int     `json:"selection_count"`
	SuccessRate      float64 `json:"success_rate"`
	AverageRiskScore float64 `json:"average_risk_score"`
}

// RiskFactorStats represents statistics for risk factors
type RiskFactorStats struct {
	RiskType       RiskType `json:"risk_type"`
	Frequency      int      `json:"frequency"`
	AverageImpact  float64  `json:"average_impact"`
	MitigationRate float64  `json:"mitigation_rate"`
}

// PerformanceTrend represents performance over time
type PerformanceTrend struct {
	Date               time.Time `json:"date"`
	SelectionCount     int       `json:"selection_count"`
	SuccessRate        float64   `json:"success_rate"`
	AverageRiskScore   float64   `json:"average_risk_score"`
	AverageHealthScore float64   `json:"average_health_score"`
}

// PerformanceReport represents detailed performance metrics
type PerformanceReport struct {
	SelectionID         string                     `json:"selection_id"`
	Duration            time.Duration              `json:"duration"`
	TrafficProcessed    int64                      `json:"traffic_processed"`
	ErrorRate           float64                    `json:"error_rate"`
	AverageResponseTime float64                    `json:"average_response_time"`
	ThroughputRPS       float64                    `json:"throughput_rps"`
	NodePerformance     map[string]NodePerformance `json:"node_performance"`
	MetricHistory       []MetricSnapshot           `json:"metric_history"`
	Anomalies           []PerformanceAnomaly       `json:"anomalies"`
	GeneratedAt         time.Time                  `json:"generated_at"`
}

// NodePerformance represents performance metrics for a specific node
type NodePerformance struct {
	NodeID              string      `json:"node_id"`
	TrafficShare        float64     `json:"traffic_share"`
	ErrorRate           float64     `json:"error_rate"`
	AverageResponseTime float64     `json:"average_response_time"`
	ThroughputRPS       float64     `json:"throughput_rps"`
	ResourceUtilization LoadMetrics `json:"resource_utilization"`
	HealthScore         float64     `json:"health_score"`
}

// MetricSnapshot represents metrics at a point in time
type MetricSnapshot struct {
	Timestamp     time.Time `json:"timestamp"`
	ErrorRate     float64   `json:"error_rate"`
	ResponseTime  float64   `json:"response_time"`
	ThroughputRPS float64   `json:"throughput_rps"`
	HealthScore   float64   `json:"health_score"`
}

// PerformanceAnomaly represents detected performance anomalies
type PerformanceAnomaly struct {
	ID            string     `json:"id"`
	Type          string     `json:"type"` // "SPIKE", "DROP", "TREND"
	MetricName    string     `json:"metric_name"`
	Severity      string     `json:"severity"`
	Description   string     `json:"description"`
	StartTime     time.Time  `json:"start_time"`
	EndTime       *time.Time `json:"end_time,omitempty"`
	AffectedNodes []string   `json:"affected_nodes"`
	Impact        float64    `json:"impact"` // Impact score 0-1
}
