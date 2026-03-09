// Package miner provides Toil Mining capabilities for discovering automation opportunities
//
// This package implements Capability 4: Toil Miner (Server/Storage)
// Mining repetitive manual operator actions and proposing automation candidates
package miner

import (
	"context"
	"time"
)

// ActionLog represents a logged operator action
type ActionLog struct {
	ID          string                 `json:"id"`
	UserID      string                 `json:"user_id"`
	Timestamp   time.Time              `json:"timestamp"`
	ActionType  string                 `json:"action_type"`
	IncidentID  string                 `json:"incident_id,omitempty"`
	ServiceName string                 `json:"service_name,omitempty"`
	Parameters  map[string]interface{} `json:"parameters"`
	Success     bool                   `json:"success"`
	Duration    time.Duration          `json:"duration"`
	Comments    string                 `json:"comments,omitempty"`
	Tags        []string               `json:"tags"`
	Context     ActionContext          `json:"context"`
}

// ActionContext provides environmental context for an action
type ActionContext struct {
	IncidentType     string            `json:"incident_type,omitempty"`
	Severity         string            `json:"severity,omitempty"`
	AffectedServices []string          `json:"affected_services"`
	TimeOfDay        string            `json:"time_of_day"`
	DayOfWeek        string            `json:"day_of_week"`
	TeamOnCall       string            `json:"team_on_call,omitempty"`
	RelatedAlerts    []string          `json:"related_alerts"`
	Environment      string            `json:"environment"`
	Metadata         map[string]string `json:"metadata"`
}

// ActionSequence represents a sequence of related actions
type ActionSequence struct {
	ID           string        `json:"id"`
	Actions      []ActionLog   `json:"actions"`
	IncidentType string        `json:"incident_type"`
	Pattern      string        `json:"pattern"`
	Frequency    int           `json:"frequency"`
	AvgDuration  time.Duration `json:"avg_duration"`
	SuccessRate  float64       `json:"success_rate"`
	FirstSeen    time.Time     `json:"first_seen"`
	LastSeen     time.Time     `json:"last_seen"`
}

// AutomationCandidate represents a potential automation opportunity
type AutomationCandidate struct {
	ID             string                 `json:"id"`
	Title          string                 `json:"title"`
	Description    string                 `json:"description"`
	Priority       Priority               `json:"priority"`
	Score          float64                `json:"score"`
	Evidence       CandidateEvidence      `json:"evidence"`
	Recommendation Recommendation         `json:"recommendation"`
	Status         CandidateStatus        `json:"status"`
	CreatedAt      time.Time              `json:"created_at"`
	UpdatedAt      time.Time              `json:"updated_at"`
	ReviewedBy     string                 `json:"reviewed_by,omitempty"`
	ReviewedAt     *time.Time             `json:"reviewed_at,omitempty"`
	Tags           []string               `json:"tags"`
	Metadata       map[string]interface{} `json:"metadata"`
}

// Priority levels for automation candidates
type Priority string

const (
	PriorityHigh   Priority = "HIGH"
	PriorityMedium Priority = "MEDIUM"
	PriorityLow    Priority = "LOW"
)

// CandidateStatus tracks the lifecycle of automation candidates
type CandidateStatus string

const (
	StatusPending     CandidateStatus = "PENDING"
	StatusReviewed    CandidateStatus = "REVIEWED"
	StatusApproved    CandidateStatus = "APPROVED"
	StatusRejected    CandidateStatus = "REJECTED"
	StatusImplemented CandidateStatus = "IMPLEMENTED"
)

// CandidateEvidence provides supporting data for automation candidates
type CandidateEvidence struct {
	Sequences       []ActionSequence `json:"sequences"`
	Frequency       int              `json:"frequency"`        // How often this pattern occurs
	TimeSaved       time.Duration    `json:"time_saved"`       // Estimated time saved per occurrence
	SuccessRate     float64          `json:"success_rate"`     // Success rate of manual actions
	AffectedTeams   []string         `json:"affected_teams"`   // Teams that perform this action
	BusinessImpact  string           `json:"business_impact"`  // Business justification
	ComplexityScore float64          `json:"complexity_score"` // Implementation complexity (0-1)
	RiskScore       float64          `json:"risk_score"`       // Automation risk (0-1)
}

// Recommendation provides specific automation suggestions
type Recommendation struct {
	Type                RecommendationType `json:"type"`
	ActionSpec          ActionSpec         `json:"action_spec,omitempty"`
	PlaybookSpec        PlaybookSpec       `json:"playbook_spec,omitempty"`
	ImplementationSteps []string           `json:"implementation_steps"`
	Prerequisites       []string           `json:"prerequisites"`
	TestPlan            []string           `json:"test_plan"`
	RollbackPlan        string             `json:"rollback_plan"`
	EstimatedEffort     string             `json:"estimated_effort"`
}

// RecommendationType defines the type of automation recommended
type RecommendationType string

const (
	RecommendationAction    RecommendationType = "ACTION"    // Single automated action
	RecommendationPlaybook  RecommendationType = "PLAYBOOK"  // Multi-step playbook
	RecommendationPolicy    RecommendationType = "POLICY"    // Policy-based automation
	RecommendationDashboard RecommendationType = "DASHBOARD" // Monitoring dashboard
)

// ActionSpec defines a single automated action
type ActionSpec struct {
	Name        string                 `json:"name"`
	Type        string                 `json:"type"`
	Command     string                 `json:"command,omitempty"`
	Parameters  map[string]interface{} `json:"parameters"`
	Timeout     time.Duration          `json:"timeout"`
	RetryPolicy RetryPolicy            `json:"retry_policy"`
	Validation  ValidationRules        `json:"validation"`
	Rollback    string                 `json:"rollback,omitempty"`
}

// PlaybookSpec defines a multi-step automation playbook
type PlaybookSpec struct {
	Name         string       `json:"name"`
	Description  string       `json:"description"`
	Steps        []ActionSpec `json:"steps"`
	Conditions   []string     `json:"conditions"`
	Dependencies []string     `json:"dependencies"`
	FailureMode  string       `json:"failure_mode"`
}

// RetryPolicy defines retry behavior for actions
type RetryPolicy struct {
	MaxAttempts int           `json:"max_attempts"`
	Backoff     time.Duration `json:"backoff"`
	Multiplier  float64       `json:"multiplier"`
	MaxBackoff  time.Duration `json:"max_backoff"`
}

// ValidationRules define pre and post conditions for actions
type ValidationRules struct {
	Preconditions  []string `json:"preconditions"`
	Postconditions []string `json:"postconditions"`
	SafetyChecks   []string `json:"safety_checks"`
}

// MiningConfig configures the toil mining process
type MiningConfig struct {
	LookbackPeriod     time.Duration `json:"lookback_period"`      // How far back to analyze
	MinFrequency       int           `json:"min_frequency"`        // Minimum occurrences to consider
	MinTimeSaved       time.Duration `json:"min_time_saved"`       // Minimum time savings to recommend
	MaxComplexity      float64       `json:"max_complexity"`       // Maximum complexity threshold
	MaxRisk            float64       `json:"max_risk"`             // Maximum risk threshold
	IncludeTeams       []string      `json:"include_teams"`        // Teams to include in analysis
	ExcludeActionTypes []string      `json:"exclude_action_types"` // Action types to exclude
	ScoreWeights       ScoreWeights  `json:"score_weights"`        // Weights for scoring algorithm
}

// ScoreWeights defines weights for candidate scoring
type ScoreWeights struct {
	Frequency      float64 `json:"frequency"`       // Weight for frequency of occurrence
	TimeSaved      float64 `json:"time_saved"`      // Weight for time savings
	SuccessRate    float64 `json:"success_rate"`    // Weight for success rate
	BusinessImpact float64 `json:"business_impact"` // Weight for business impact
	Complexity     float64 `json:"complexity"`      // Weight for implementation complexity (negative)
	Risk           float64 `json:"risk"`            // Weight for automation risk (negative)
}

// MiningResult represents the output of a mining operation
type MiningResult struct {
	ID                string                `json:"id"`
	Timestamp         time.Time             `json:"timestamp"`
	Config            MiningConfig          `json:"config"`
	Candidates        []AutomationCandidate `json:"candidates"`
	TotalActions      int                   `json:"total_actions"`
	AnalyzedSequences int                   `json:"analyzed_sequences"`
	ProcessingTime    time.Duration         `json:"processing_time"`
	Statistics        MiningStatistics      `json:"statistics"`
}

// MiningStatistics provides analysis statistics
type MiningStatistics struct {
	ActionsByType         map[string]int `json:"actions_by_type"`
	SequencesByPattern    map[string]int `json:"sequences_by_pattern"`
	TeamActivity          map[string]int `json:"team_activity"`
	IncidentTypeFrequency map[string]int `json:"incident_type_frequency"`
	AvgSequenceLength     float64        `json:"avg_sequence_length"`
	TotalTimeSavings      time.Duration  `json:"total_time_savings"`
	PotentialROI          float64        `json:"potential_roi"`
}

// SequenceMiner interface for discovering action patterns
type SequenceMiner interface {
	// DiscoverSequences finds common action patterns
	DiscoverSequences(ctx context.Context, actions []ActionLog, config MiningConfig) ([]ActionSequence, error)

	// AnalyzePatterns identifies automation opportunities in sequences
	AnalyzePatterns(ctx context.Context, sequences []ActionSequence) ([]AutomationCandidate, error)

	// ScoreCandidate calculates automation opportunity score
	ScoreCandidate(ctx context.Context, candidate *AutomationCandidate, config MiningConfig) error
}

// CandidateGenerator interface for creating automation candidates
type CandidateGenerator interface {
	// GenerateCandidate creates automation candidate from sequence
	GenerateCandidate(ctx context.Context, sequence ActionSequence) (*AutomationCandidate, error)

	// GenerateRecommendation creates specific automation recommendations
	GenerateRecommendation(ctx context.Context, candidate *AutomationCandidate) (*Recommendation, error)

	// ValidateCandidate checks if candidate is viable
	ValidateCandidate(ctx context.Context, candidate *AutomationCandidate) error
}

// Repository interface for persisting mining data
type Repository interface {
	// SaveActionLog stores an action log entry
	SaveActionLog(ctx context.Context, log *ActionLog) error

	// GetActionLogs retrieves action logs with filters
	GetActionLogs(ctx context.Context, filters ActionLogFilters) ([]ActionLog, error)

	// SaveCandidate stores an automation candidate
	SaveCandidate(ctx context.Context, candidate *AutomationCandidate) error

	// GetCandidates retrieves automation candidates
	GetCandidates(ctx context.Context, filters CandidateFilters) ([]AutomationCandidate, error)

	// UpdateCandidateStatus updates candidate review status
	UpdateCandidateStatus(ctx context.Context, id string, status CandidateStatus, reviewedBy string) error

	// SaveMiningResult stores mining analysis results
	SaveMiningResult(ctx context.Context, result *MiningResult) error

	// GetMiningHistory retrieves historical mining results
	GetMiningHistory(ctx context.Context, limit int) ([]MiningResult, error)
}

// ActionLogFilters defines filters for querying action logs
type ActionLogFilters struct {
	UserID       string     `json:"user_id,omitempty"`
	ActionType   string     `json:"action_type,omitempty"`
	IncidentType string     `json:"incident_type,omitempty"`
	TeamOnCall   string     `json:"team_on_call,omitempty"`
	Environment  string     `json:"environment,omitempty"`
	Since        *time.Time `json:"since,omitempty"`
	Until        *time.Time `json:"until,omitempty"`
	Success      *bool      `json:"success,omitempty"`
	Tags         []string   `json:"tags,omitempty"`
	Limit        int        `json:"limit,omitempty"`
}

// CandidateFilters defines filters for querying automation candidates
type CandidateFilters struct {
	Status     CandidateStatus `json:"status,omitempty"`
	Priority   Priority        `json:"priority,omitempty"`
	MinScore   float64         `json:"min_score,omitempty"`
	Tags       []string        `json:"tags,omitempty"`
	ReviewedBy string          `json:"reviewed_by,omitempty"`
	Since      *time.Time      `json:"since,omitempty"`
	Until      *time.Time      `json:"until,omitempty"`
	Limit      int             `json:"limit,omitempty"`
}

// Service combines all toil mining functionality
type Service interface {
	SequenceMiner
	CandidateGenerator

	// LogAction records an operator action for analysis
	LogAction(ctx context.Context, log *ActionLog) error

	// RunMining performs toil mining analysis
	RunMining(ctx context.Context, config MiningConfig) (*MiningResult, error)

	// GetCandidates retrieves automation candidates
	GetCandidates(ctx context.Context, filters CandidateFilters) ([]AutomationCandidate, error)

	// ReviewCandidate updates candidate status after review
	ReviewCandidate(ctx context.Context, id string, status CandidateStatus, reviewedBy string) error

	// GetStatistics returns mining statistics and insights
	GetStatistics(ctx context.Context, since time.Time) (*MiningStatistics, error)
}
