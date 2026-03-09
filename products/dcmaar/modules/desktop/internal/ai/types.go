package ai

import (
	"context"
	"time"
)

// DesktopAIService defines the interface for desktop AI capabilities
type DesktopAIService interface {
	// Intent Recognition
	RecognizeIntent(ctx context.Context, input string) (*IntentResult, error)

	// Smart Suggestions
	GenerateSuggestions(ctx context.Context, context string) (*SuggestionResponse, error)

	// Workflow Automation
	CreateWorkflow(ctx context.Context, workflow *WorkflowDefinition) error
	ExecuteWorkflow(ctx context.Context, workflowID string, params map[string]interface{}) (*WorkflowExecution, error)

	// Code Intelligence
	AnalyzeCode(ctx context.Context, request *CodeAnalysisRequest) (*CodeAnalysisResult, error)
	GenerateCode(ctx context.Context, request *CodeGenerationRequest) (*CodeGenerationResult, error)

	// Natural Language Processing
	ProcessNaturalLanguage(ctx context.Context, request *NLPRequest) (*NLPResponse, error)

	// Learning and Adaptation
	RecordUserInteraction(ctx context.Context, interaction *UserInteraction) error
	AdaptToUserBehavior(ctx context.Context, userID string) (*AdaptationResult, error)

	// System Intelligence
	OptimizeSystemUsage(ctx context.Context) (*OptimizationResult, error)
	PredictUserNeeds(ctx context.Context, userID string) (*PredictionResult, error)
}

// IntentResult represents the result of intent recognition
type IntentResult struct {
	Intent           string                 `json:"intent"`
	Confidence       float64                `json:"confidence"`
	Entities         []Entity               `json:"entities"`
	Context          map[string]interface{} `json:"context"`
	SuggestedActions []Action               `json:"suggested_actions"`
	Metadata         map[string]interface{} `json:"metadata"`
}

// Entity represents a recognized entity in user input
type Entity struct {
	Name       string      `json:"name"`
	Value      interface{} `json:"value"`
	Type       string      `json:"type"`
	Confidence float64     `json:"confidence"`
	Position   Position    `json:"position"`
}

// Position represents the position of an entity in text
type Position struct {
	Start int `json:"start"`
	End   int `json:"end"`
}

// Action represents a suggested action
type Action struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	Parameters  map[string]interface{} `json:"parameters"`
	Confidence  float64                `json:"confidence"`
	Priority    int                    `json:"priority"`
}

// SuggestionResponse contains AI-generated suggestions
type SuggestionResponse struct {
	Suggestions []Suggestion           `json:"suggestions"`
	Context     string                 `json:"context"`
	Confidence  float64                `json:"confidence"`
	GeneratedAt time.Time              `json:"generated_at"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// Suggestion represents a single AI suggestion
type Suggestion struct {
	ID          string                 `json:"id"`
	Type        SuggestionType         `json:"type"`
	Title       string                 `json:"title"`
	Description string                 `json:"description"`
	Action      *Action                `json:"action,omitempty"`
	Priority    int                    `json:"priority"`
	Confidence  float64                `json:"confidence"`
	Tags        []string               `json:"tags"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// SuggestionType defines types of suggestions
type SuggestionType string

const (
	SuggestionTypeCommand      SuggestionType = "COMMAND"
	SuggestionTypeWorkflow     SuggestionType = "WORKFLOW"
	SuggestionTypeCode         SuggestionType = "CODE"
	SuggestionTypeFile         SuggestionType = "FILE"
	SuggestionTypeOptimization SuggestionType = "OPTIMIZATION"
	SuggestionTypeAutomation   SuggestionType = "AUTOMATION"
)

// WorkflowDefinition defines an automated workflow
type WorkflowDefinition struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	Trigger     WorkflowTrigger        `json:"trigger"`
	Steps       []WorkflowStep         `json:"steps"`
	Variables   map[string]interface{} `json:"variables"`
	Metadata    map[string]interface{} `json:"metadata"`
	CreatedAt   time.Time              `json:"created_at"`
	UpdatedAt   time.Time              `json:"updated_at"`
}

// WorkflowTrigger defines when a workflow should execute
type WorkflowTrigger struct {
	Type       TriggerType            `json:"type"`
	Conditions []TriggerCondition     `json:"conditions"`
	Schedule   string                 `json:"schedule,omitempty"`
	Parameters map[string]interface{} `json:"parameters"`
}

// TriggerType defines types of workflow triggers
type TriggerType string

const (
	TriggerTypeManual    TriggerType = "MANUAL"
	TriggerTypeScheduled TriggerType = "SCHEDULED"
	TriggerTypeEvent     TriggerType = "EVENT"
	TriggerTypeIntent    TriggerType = "INTENT"
	TriggerTypeFile      TriggerType = "FILE"
	TriggerTypeSystem    TriggerType = "SYSTEM"
)

// TriggerCondition defines a condition for workflow triggers
type TriggerCondition struct {
	Field    string      `json:"field"`
	Operator string      `json:"operator"`
	Value    interface{} `json:"value"`
}

// WorkflowStep defines a single step in a workflow
type WorkflowStep struct {
	ID         string                 `json:"id"`
	Name       string                 `json:"name"`
	Type       StepType               `json:"type"`
	Action     string                 `json:"action"`
	Parameters map[string]interface{} `json:"parameters"`
	Conditions []StepCondition        `json:"conditions"`
	OnSuccess  string                 `json:"on_success,omitempty"`
	OnFailure  string                 `json:"on_failure,omitempty"`
	Timeout    time.Duration          `json:"timeout"`
	Retries    int                    `json:"retries"`
}

// StepType defines types of workflow steps
type StepType string

const (
	StepTypeCommand      StepType = "COMMAND"
	StepTypeAPI          StepType = "API"
	StepTypeFile         StepType = "FILE"
	StepTypeCondition    StepType = "CONDITION"
	StepTypeLoop         StepType = "LOOP"
	StepTypeDelay        StepType = "DELAY"
	StepTypeNotification StepType = "NOTIFICATION"
)

// StepCondition defines a condition for step execution
type StepCondition struct {
	Field    string      `json:"field"`
	Operator string      `json:"operator"`
	Value    interface{} `json:"value"`
}

// WorkflowExecution represents the execution of a workflow
type WorkflowExecution struct {
	ID          string                 `json:"id"`
	WorkflowID  string                 `json:"workflow_id"`
	Status      ExecutionStatus        `json:"status"`
	StartedAt   time.Time              `json:"started_at"`
	CompletedAt *time.Time             `json:"completed_at,omitempty"`
	Steps       []StepExecution        `json:"steps"`
	Variables   map[string]interface{} `json:"variables"`
	Output      map[string]interface{} `json:"output"`
	Error       string                 `json:"error,omitempty"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// ExecutionStatus defines workflow execution status
type ExecutionStatus string

const (
	ExecutionStatusPending   ExecutionStatus = "PENDING"
	ExecutionStatusRunning   ExecutionStatus = "RUNNING"
	ExecutionStatusCompleted ExecutionStatus = "COMPLETED"
	ExecutionStatusFailed    ExecutionStatus = "FAILED"
	ExecutionStatusCancelled ExecutionStatus = "CANCELLED"
)

// StepExecution represents the execution of a workflow step
type StepExecution struct {
	StepID      string                 `json:"step_id"`
	Status      ExecutionStatus        `json:"status"`
	StartedAt   time.Time              `json:"started_at"`
	CompletedAt *time.Time             `json:"completed_at,omitempty"`
	Output      map[string]interface{} `json:"output"`
	Error       string                 `json:"error,omitempty"`
	Retries     int                    `json:"retries"`
}

// CodeAnalysisRequest represents a request for code analysis
type CodeAnalysisRequest struct {
	Code         string                 `json:"code"`
	Language     string                 `json:"language"`
	FilePath     string                 `json:"file_path,omitempty"`
	Context      string                 `json:"context,omitempty"`
	AnalysisType []AnalysisType         `json:"analysis_type"`
	Options      map[string]interface{} `json:"options"`
}

// AnalysisType defines types of code analysis
type AnalysisType string

const (
	AnalysisTypeSyntax      AnalysisType = "SYNTAX"
	AnalysisTypeSemantics   AnalysisType = "SEMANTICS"
	AnalysisTypeSecurity    AnalysisType = "SECURITY"
	AnalysisTypePerformance AnalysisType = "PERFORMANCE"
	AnalysisTypeComplexity  AnalysisType = "COMPLEXITY"
	AnalysisTypeStyle       AnalysisType = "STYLE"
	AnalysisTypeBugs        AnalysisType = "BUGS"
)

// CodeAnalysisResult contains the results of code analysis
type CodeAnalysisResult struct {
	Issues      []CodeIssue            `json:"issues"`
	Metrics     CodeMetrics            `json:"metrics"`
	Suggestions []CodeSuggestion       `json:"suggestions"`
	Summary     string                 `json:"summary"`
	Confidence  float64                `json:"confidence"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// CodeIssue represents an issue found in code
type CodeIssue struct {
	ID          string       `json:"id"`
	Type        AnalysisType `json:"type"`
	Severity    Severity     `json:"severity"`
	Message     string       `json:"message"`
	Description string       `json:"description"`
	Location    Location     `json:"location"`
	Rule        string       `json:"rule"`
	Fix         *CodeFix     `json:"fix,omitempty"`
}

// Severity defines issue severity levels
type Severity string

const (
	SeverityInfo     Severity = "INFO"
	SeverityWarning  Severity = "WARNING"
	SeverityError    Severity = "ERROR"
	SeverityCritical Severity = "CRITICAL"
)

// Location represents a location in code
type Location struct {
	Line   int `json:"line"`
	Column int `json:"column"`
	Start  int `json:"start"`
	End    int `json:"end"`
}

// CodeFix represents a suggested fix for a code issue
type CodeFix struct {
	Description string  `json:"description"`
	OldCode     string  `json:"old_code"`
	NewCode     string  `json:"new_code"`
	Confidence  float64 `json:"confidence"`
}

// CodeMetrics contains various code metrics
type CodeMetrics struct {
	LinesOfCode          int     `json:"lines_of_code"`
	CyclomaticComplexity int     `json:"cyclomatic_complexity"`
	CognitiveComplexity  int     `json:"cognitive_complexity"`
	Maintainability      float64 `json:"maintainability"`
	TestCoverage         float64 `json:"test_coverage"`
	Duplication          float64 `json:"duplication"`
	TechnicalDebt        string  `json:"technical_debt"`
}

// CodeSuggestion represents a suggestion for code improvement
type CodeSuggestion struct {
	ID          string                 `json:"id"`
	Type        SuggestionType         `json:"type"`
	Title       string                 `json:"title"`
	Description string                 `json:"description"`
	Code        string                 `json:"code"`
	Location    *Location              `json:"location,omitempty"`
	Confidence  float64                `json:"confidence"`
	Impact      string                 `json:"impact"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// CodeGenerationRequest represents a request for code generation
type CodeGenerationRequest struct {
	Prompt      string                 `json:"prompt"`
	Language    string                 `json:"language"`
	Context     string                 `json:"context,omitempty"`
	Style       string                 `json:"style,omitempty"`
	Framework   string                 `json:"framework,omitempty"`
	Examples    []string               `json:"examples,omitempty"`
	Constraints []string               `json:"constraints,omitempty"`
	Options     map[string]interface{} `json:"options"`
}

// CodeGenerationResult contains generated code
type CodeGenerationResult struct {
	Code          string                 `json:"code"`
	Explanation   string                 `json:"explanation"`
	Language      string                 `json:"language"`
	Confidence    float64                `json:"confidence"`
	Alternatives  []CodeAlternative      `json:"alternatives"`
	Tests         []string               `json:"tests,omitempty"`
	Documentation string                 `json:"documentation,omitempty"`
	Metadata      map[string]interface{} `json:"metadata"`
}

// CodeAlternative represents an alternative code solution
type CodeAlternative struct {
	Code        string   `json:"code"`
	Description string   `json:"description"`
	Confidence  float64  `json:"confidence"`
	Pros        []string `json:"pros"`
	Cons        []string `json:"cons"`
}

// NLPRequest represents a natural language processing request
type NLPRequest struct {
	Text     string                 `json:"text"`
	Task     NLPTask                `json:"task"`
	Language string                 `json:"language,omitempty"`
	Context  string                 `json:"context,omitempty"`
	Options  map[string]interface{} `json:"options"`
}

// NLPTask defines types of NLP tasks
type NLPTask string

const (
	NLPTaskSentiment      NLPTask = "SENTIMENT"
	NLPTaskSummarization  NLPTask = "SUMMARIZATION"
	NLPTaskTranslation    NLPTask = "TRANSLATION"
	NLPTaskClassification NLPTask = "CLASSIFICATION"
	NLPTaskExtraction     NLPTask = "EXTRACTION"
	NLPTaskGeneration     NLPTask = "GENERATION"
)

// NLPResponse contains the result of natural language processing
type NLPResponse struct {
	Result     interface{}            `json:"result"`
	Confidence float64                `json:"confidence"`
	Task       NLPTask                `json:"task"`
	Language   string                 `json:"language"`
	Metadata   map[string]interface{} `json:"metadata"`
}

// UserInteraction represents a user interaction for learning
type UserInteraction struct {
	ID        string                 `json:"id"`
	UserID    string                 `json:"user_id"`
	SessionID string                 `json:"session_id"`
	Type      InteractionType        `json:"type"`
	Context   string                 `json:"context"`
	Input     string                 `json:"input"`
	Output    string                 `json:"output"`
	Feedback  *Feedback              `json:"feedback,omitempty"`
	Timestamp time.Time              `json:"timestamp"`
	Duration  time.Duration          `json:"duration"`
	Metadata  map[string]interface{} `json:"metadata"`
}

// InteractionType defines types of user interactions
type InteractionType string

const (
	InteractionTypeCommand      InteractionType = "COMMAND"
	InteractionTypeQuery        InteractionType = "QUERY"
	InteractionTypeCode         InteractionType = "CODE"
	InteractionTypeWorkflow     InteractionType = "WORKFLOW"
	InteractionTypeSuggestion   InteractionType = "SUGGESTION"
	InteractionTypeOptimization InteractionType = "OPTIMIZATION"
)

// Feedback represents user feedback on AI interactions
type Feedback struct {
	Rating      int                    `json:"rating"`
	Helpful     bool                   `json:"helpful"`
	Accurate    bool                   `json:"accurate"`
	Comments    string                 `json:"comments,omitempty"`
	Corrections string                 `json:"corrections,omitempty"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// AdaptationResult represents the result of behavioral adaptation
type AdaptationResult struct {
	UserID          string                 `json:"user_id"`
	Adaptations     []Adaptation           `json:"adaptations"`
	Confidence      float64                `json:"confidence"`
	EffectiveDate   time.Time              `json:"effective_date"`
	PreviousProfile *UserProfile           `json:"previous_profile,omitempty"`
	UpdatedProfile  UserProfile            `json:"updated_profile"`
	Metadata        map[string]interface{} `json:"metadata"`
}

// Adaptation represents a specific behavioral adaptation
type Adaptation struct {
	Type        AdaptationType `json:"type"`
	Description string         `json:"description"`
	Parameter   string         `json:"parameter"`
	OldValue    interface{}    `json:"old_value"`
	NewValue    interface{}    `json:"new_value"`
	Confidence  float64        `json:"confidence"`
	Reason      string         `json:"reason"`
}

// AdaptationType defines types of adaptations
type AdaptationType string

const (
	AdaptationTypePreference AdaptationType = "PREFERENCE"
	AdaptationTypeWorkflow   AdaptationType = "WORKFLOW"
	AdaptationTypeSuggestion AdaptationType = "SUGGESTION"
	AdaptationTypeInterface  AdaptationType = "INTERFACE"
	AdaptationTypeAutomation AdaptationType = "AUTOMATION"
)

// UserProfile represents a user's behavioral profile
type UserProfile struct {
	UserID           string                 `json:"user_id"`
	Preferences      map[string]interface{} `json:"preferences"`
	WorkflowPatterns []WorkflowPattern      `json:"workflow_patterns"`
	SkillLevel       SkillLevel             `json:"skill_level"`
	Interests        []string               `json:"interests"`
	Tools            []string               `json:"tools"`
	Languages        []string               `json:"languages"`
	LastUpdated      time.Time              `json:"last_updated"`
	Metadata         map[string]interface{} `json:"metadata"`
}

// WorkflowPattern represents a user's workflow pattern
type WorkflowPattern struct {
	Name       string    `json:"name"`
	Commands   []string  `json:"commands"`
	Frequency  int       `json:"frequency"`
	LastUsed   time.Time `json:"last_used"`
	Confidence float64   `json:"confidence"`
}

// SkillLevel defines user skill levels
type SkillLevel string

const (
	SkillLevelBeginner     SkillLevel = "BEGINNER"
	SkillLevelIntermediate SkillLevel = "INTERMEDIATE"
	SkillLevelAdvanced     SkillLevel = "ADVANCED"
	SkillLevelExpert       SkillLevel = "EXPERT"
)

// OptimizationResult represents system optimization results
type OptimizationResult struct {
	Optimizations   []SystemOptimization         `json:"optimizations"`
	ImpactForecast  ImpactForecast               `json:"impact_forecast"`
	Recommendations []OptimizationRecommendation `json:"recommendations"`
	Confidence      float64                      `json:"confidence"`
	GeneratedAt     time.Time                    `json:"generated_at"`
	Metadata        map[string]interface{}       `json:"metadata"`
}

// SystemOptimization represents a specific system optimization
type SystemOptimization struct {
	ID          string                 `json:"id"`
	Type        OptimizationType       `json:"type"`
	Description string                 `json:"description"`
	Action      string                 `json:"action"`
	Parameters  map[string]interface{} `json:"parameters"`
	Impact      OptimizationImpact     `json:"impact"`
	Confidence  float64                `json:"confidence"`
	Priority    int                    `json:"priority"`
}

// OptimizationType defines types of system optimizations
type OptimizationType string

const (
	OptimizationTypePerformance OptimizationType = "PERFORMANCE"
	OptimizationTypeMemory      OptimizationType = "MEMORY"
	OptimizationTypeCPU         OptimizationType = "CPU"
	OptimizationTypeDisk        OptimizationType = "DISK"
	OptimizationTypeNetwork     OptimizationType = "NETWORK"
	OptimizationTypeWorkflow    OptimizationType = "WORKFLOW"
)

// OptimizationImpact represents the impact of an optimization
type OptimizationImpact struct {
	Performance float64 `json:"performance"`
	Memory      float64 `json:"memory"`
	CPU         float64 `json:"cpu"`
	Disk        float64 `json:"disk"`
	UserTime    float64 `json:"user_time"`
}

// ImpactForecast forecasts the impact of optimizations
type ImpactForecast struct {
	TotalImprovement   float64         `json:"total_improvement"`
	PerformanceGain    float64         `json:"performance_gain"`
	ResourceSavings    ResourceSavings `json:"resource_savings"`
	TimeReduction      time.Duration   `json:"time_reduction"`
	UserSatisfaction   float64         `json:"user_satisfaction"`
	ImplementationCost EstimatedCost   `json:"implementation_cost"`
}

// ResourceSavings represents estimated resource savings
type ResourceSavings struct {
	CPU    float64 `json:"cpu"`
	Memory float64 `json:"memory"`
	Disk   float64 `json:"disk"`
	Power  float64 `json:"power"`
}

// EstimatedCost represents estimated implementation cost
type EstimatedCost struct {
	Time       time.Duration `json:"time"`
	Complexity string        `json:"complexity"`
	Risk       string        `json:"risk"`
}

// OptimizationRecommendation represents a recommendation for optimization
type OptimizationRecommendation struct {
	ID          string                 `json:"id"`
	Title       string                 `json:"title"`
	Description string                 `json:"description"`
	Category    string                 `json:"category"`
	Priority    int                    `json:"priority"`
	Effort      string                 `json:"effort"`
	Impact      string                 `json:"impact"`
	Steps       []string               `json:"steps"`
	Resources   []string               `json:"resources"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// PredictionResult represents user need predictions
type PredictionResult struct {
	UserID      string                 `json:"user_id"`
	Predictions []UserNeedPrediction   `json:"predictions"`
	Confidence  float64                `json:"confidence"`
	TimeFrame   time.Duration          `json:"time_frame"`
	Context     string                 `json:"context"`
	GeneratedAt time.Time              `json:"generated_at"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// UserNeedPrediction represents a prediction of user needs
type UserNeedPrediction struct {
	Need        string                 `json:"need"`
	Probability float64                `json:"probability"`
	Category    PredictionCategory     `json:"category"`
	TimeWindow  time.Duration          `json:"time_window"`
	Context     string                 `json:"context"`
	Actions     []PredictedAction      `json:"actions"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// PredictionCategory defines categories of predictions
type PredictionCategory string

const (
	PredictionCategoryTool     PredictionCategory = "TOOL"
	PredictionCategoryWorkflow PredictionCategory = "WORKFLOW"
	PredictionCategoryFile     PredictionCategory = "FILE"
	PredictionCategoryCommand  PredictionCategory = "COMMAND"
	PredictionCategoryProject  PredictionCategory = "PROJECT"
	PredictionCategoryLearning PredictionCategory = "LEARNING"
)

// PredictedAction represents a predicted user action
type PredictedAction struct {
	Action      string                 `json:"action"`
	Probability float64                `json:"probability"`
	Context     string                 `json:"context"`
	Parameters  map[string]interface{} `json:"parameters"`
	Benefit     string                 `json:"benefit"`
}
