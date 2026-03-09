package labeling

import (
	"context"
	"time"
)

// LabelingTask represents a task that needs human labeling
type LabelingTask struct {
	ID           string                 `json:"id"`
	TaskType     TaskType               `json:"task_type"`
	ModelName    string                 `json:"model_name"`
	ModelVersion string                 `json:"model_version"`
	DataSnapshot map[string]interface{} `json:"data_snapshot"`
	Features     []float64              `json:"features"`
	Confidence   float64                `json:"confidence"`
	Prediction   interface{}            `json:"prediction"`
	Priority     Priority               `json:"priority"`
	Status       TaskStatus             `json:"status"`
	CreatedAt    time.Time              `json:"created_at"`
	AssignedTo   string                 `json:"assigned_to,omitempty"`
	AssignedAt   *time.Time             `json:"assigned_at,omitempty"`
	CompletedAt  *time.Time             `json:"completed_at,omitempty"`
	Metadata     map[string]interface{} `json:"metadata"`
}

// TaskType defines the type of labeling task
type TaskType string

const (
	TaskTypeManuality     TaskType = "MANUALITY"      // Manual vs automated classification
	TaskTypeSessionIntent TaskType = "SESSION_INTENT" // Session intent classification
	TaskTypeAnomalyDetect TaskType = "ANOMALY_DETECT" // Anomaly detection
	TaskTypeEventCategory TaskType = "EVENT_CATEGORY" // Event categorization
)

// Priority defines the priority of a labeling task
type Priority string

const (
	PriorityHigh   Priority = "HIGH"   // Model drift detected or very low confidence
	PriorityMedium Priority = "MEDIUM" // Low confidence or edge case
	PriorityLow    Priority = "LOW"    // Normal active learning sample
)

// TaskStatus defines the status of a labeling task
type TaskStatus string

const (
	StatusPending   TaskStatus = "PENDING"   // Waiting for assignment
	StatusAssigned  TaskStatus = "ASSIGNED"  // Assigned to a labeler
	StatusCompleted TaskStatus = "COMPLETED" // Labeling completed
	StatusSkipped   TaskStatus = "SKIPPED"   // Skipped by labeler
	StatusExpired   TaskStatus = "EXPIRED"   // Task expired
	StatusValidated TaskStatus = "VALIDATED" // Label validated by another labeler
)

// Label represents a human-provided label for a task
type Label struct {
	ID          string                 `json:"id"`
	TaskID      string                 `json:"task_id"`
	LabelValue  interface{}            `json:"label_value"`
	Confidence  float64                `json:"confidence"` // Labeler's confidence in their label
	TimeSpent   time.Duration          `json:"time_spent"` // Time spent on labeling
	LabeledBy   string                 `json:"labeled_by"`
	LabeledAt   time.Time              `json:"labeled_at"`
	Explanation string                 `json:"explanation,omitempty"` // Optional explanation
	Metadata    map[string]interface{} `json:"metadata"`
	ValidatedBy string                 `json:"validated_by,omitempty"`
	ValidatedAt *time.Time             `json:"validated_at,omitempty"`
}

// ManualityLabel represents a label for manual/automated classification
type ManualityLabel struct {
	IsManual    bool   `json:"is_manual"`
	Reasoning   string `json:"reasoning,omitempty"`
	Certainty   string `json:"certainty"` // "CERTAIN", "LIKELY", "UNSURE"
	Subcategory string `json:"subcategory,omitempty"`
}

// SessionIntentLabel represents a label for session intent classification
type SessionIntentLabel struct {
	Intent      string   `json:"intent"`      // Primary intent
	SubIntents  []string `json:"sub_intents"` // Secondary intents
	Confidence  string   `json:"confidence"`  // "HIGH", "MEDIUM", "LOW"
	Context     string   `json:"context,omitempty"`
	IsAmbiguous bool     `json:"is_ambiguous"`
}

// LabelingQueue represents a queue of labeling tasks
type LabelingQueue struct {
	ID          string                 `json:"id"`
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	TaskType    TaskType               `json:"task_type"`
	ModelName   string                 `json:"model_name"`
	Config      QueueConfig            `json:"config"`
	Stats       QueueStats             `json:"stats"`
	CreatedAt   time.Time              `json:"created_at"`
	UpdatedAt   time.Time              `json:"updated_at"`
	IsActive    bool                   `json:"is_active"`
	Metadata    map[string]interface{} `json:"metadata"`
}

// QueueConfig defines configuration for a labeling queue
type QueueConfig struct {
	MaxPendingTasks     int           `json:"max_pending_tasks"`    // Maximum tasks in pending state
	ConfidenceThreshold float64       `json:"confidence_threshold"` // Below this, enqueue for labeling
	DriftThreshold      float64       `json:"drift_threshold"`      // Model drift detection threshold
	SamplingRate        float64       `json:"sampling_rate"`        // Random sampling rate for active learning
	TaskExpiration      time.Duration `json:"task_expiration"`      // How long before tasks expire
	RequireValidation   bool          `json:"require_validation"`   // Whether labels need validation
	ValidationThreshold float64       `json:"validation_threshold"` // Agreement threshold for validation
	AutoRetrain         bool          `json:"auto_retrain"`         // Whether to automatically retrain
	RetrainThreshold    int           `json:"retrain_threshold"`    // Number of new labels before retrain
	QualityThreshold    float64       `json:"quality_threshold"`    // Minimum labeler quality score
}

// QueueStats represents statistics for a labeling queue
type QueueStats struct {
	TotalTasks        int       `json:"total_tasks"`
	PendingTasks      int       `json:"pending_tasks"`
	CompletedTasks    int       `json:"completed_tasks"`
	SkippedTasks      int       `json:"skipped_tasks"`
	ExpiredTasks      int       `json:"expired_tasks"`
	AverageTimeSpent  float64   `json:"average_time_spent"` // seconds
	ThroughputPerHour float64   `json:"throughput_per_hour"`
	QualityScore      float64   `json:"quality_score"` // Inter-labeler agreement
	LastUpdated       time.Time `json:"last_updated"`
}

// Labeler represents a person who provides labels
type Labeler struct {
	ID           string                 `json:"id"`
	Name         string                 `json:"name"`
	Email        string                 `json:"email"`
	Role         string                 `json:"role"`
	Specialties  []TaskType             `json:"specialties"` // Types of tasks they're good at
	Stats        LabelerStats           `json:"stats"`
	Preferences  LabelerPrefs           `json:"preferences"`
	IsActive     bool                   `json:"is_active"`
	CreatedAt    time.Time              `json:"created_at"`
	LastActiveAt *time.Time             `json:"last_active_at"`
	Metadata     map[string]interface{} `json:"metadata"`
}

// LabelerStats represents statistics for an individual labeler
type LabelerStats struct {
	TotalTasksCompleted int       `json:"total_tasks_completed"`
	TasksCompletedToday int       `json:"tasks_completed_today"`
	AverageTimePerTask  float64   `json:"average_time_per_task"` // seconds
	AccuracyScore       float64   `json:"accuracy_score"`        // Based on validation
	ConsistencyScore    float64   `json:"consistency_score"`     // Self-consistency over time
	QualityScore        float64   `json:"quality_score"`         // Overall quality metric
	ThroughputScore     float64   `json:"throughput_score"`      // Tasks per hour
	StreakDays          int       `json:"streak_days"`           // Consecutive days active
	LastUpdated         time.Time `json:"last_updated"`
}

// LabelerPrefs represents labeler preferences
type LabelerPrefs struct {
	PreferredTaskTypes   []TaskType        `json:"preferred_task_types"`
	MaxTasksPerSession   int               `json:"max_tasks_per_session"`
	PreferredDifficulty  string            `json:"preferred_difficulty"` // "EASY", "MEDIUM", "HARD"
	NotificationSettings map[string]bool   `json:"notification_settings"`
	KeyboardShortcuts    map[string]string `json:"keyboard_shortcuts"`
	Theme                string            `json:"theme"`
}

// RetrainingJob represents a model retraining job
type RetrainingJob struct {
	ID                string                 `json:"id"`
	ModelName         string                 `json:"model_name"`
	CurrentVersion    string                 `json:"current_version"`
	NewVersion        string                 `json:"new_version"`
	Status            JobStatus              `json:"status"`
	TriggerReason     string                 `json:"trigger_reason"`
	NewLabelsCount    int                    `json:"new_labels_count"`
	TotalLabelsCount  int                    `json:"total_labels_count"`
	TrainingMetrics   TrainingMetrics        `json:"training_metrics"`
	ValidationMetrics ValidationMetrics      `json:"validation_metrics"`
	ImprovementPct    float64                `json:"improvement_pct"`
	ShouldDeploy      bool                   `json:"should_deploy"`
	StartedAt         time.Time              `json:"started_at"`
	CompletedAt       *time.Time             `json:"completed_at"`
	ErrorMessage      string                 `json:"error_message,omitempty"`
	Metadata          map[string]interface{} `json:"metadata"`
}

// JobStatus defines the status of a retraining job
type JobStatus string

const (
	JobStatusQueued    JobStatus = "QUEUED"
	JobStatusRunning   JobStatus = "RUNNING"
	JobStatusCompleted JobStatus = "COMPLETED"
	JobStatusFailed    JobStatus = "FAILED"
	JobStatusCancelled JobStatus = "CANCELLED"
)

// TrainingMetrics represents metrics from model training
type TrainingMetrics struct {
	TrainingLoss       float64            `json:"training_loss"`
	ValidationLoss     float64            `json:"validation_loss"`
	TrainingAccuracy   float64            `json:"training_accuracy"`
	ValidationAccuracy float64            `json:"validation_accuracy"`
	EpochsCompleted    int                `json:"epochs_completed"`
	TrainingTime       time.Duration      `json:"training_time"`
	ModelSize          int64              `json:"model_size"` // bytes
	ParameterCount     int64              `json:"parameter_count"`
	Metrics            map[string]float64 `json:"metrics"` // Additional metrics
}

// ValidationMetrics represents validation metrics for model comparison
type ValidationMetrics struct {
	Accuracy     float64                 `json:"accuracy"`
	Precision    float64                 `json:"precision"`
	Recall       float64                 `json:"recall"`
	F1Score      float64                 `json:"f1_score"`
	AUC          float64                 `json:"auc"`
	LogLoss      float64                 `json:"log_loss"`
	Confusion    [][]int                 `json:"confusion_matrix"`
	ClassMetrics map[string]ClassMetrics `json:"class_metrics"`
}

// ClassMetrics represents per-class validation metrics
type ClassMetrics struct {
	Precision float64 `json:"precision"`
	Recall    float64 `json:"recall"`
	F1Score   float64 `json:"f1_score"`
	Support   int     `json:"support"`
}

// Service interface defines the active learning service
type Service interface {
	// Task management
	EnqueueTask(ctx context.Context, task *LabelingTask) error
	GetNextTask(ctx context.Context, labelerID string, taskTypes []TaskType) (*LabelingTask, error)
	AssignTask(ctx context.Context, taskID, labelerID string) error
	CompleteTask(ctx context.Context, taskID string, label *Label) error
	SkipTask(ctx context.Context, taskID, reason string) error

	// Queue management
	CreateQueue(ctx context.Context, queue *LabelingQueue) error
	GetQueue(ctx context.Context, queueID string) (*LabelingQueue, error)
	UpdateQueueConfig(ctx context.Context, queueID string, config QueueConfig) error
	GetQueueStats(ctx context.Context, queueID string) (*QueueStats, error)

	// Labeler management
	RegisterLabeler(ctx context.Context, labeler *Labeler) error
	GetLabeler(ctx context.Context, labelerID string) (*Labeler, error)
	UpdateLabelerStats(ctx context.Context, labelerID string, stats LabelerStats) error
	GetLeaderboard(ctx context.Context, timeframe string) ([]LabelerStats, error)

	// Retraining management
	TriggerRetraining(ctx context.Context, modelName string, reason string) (*RetrainingJob, error)
	GetRetrainingJob(ctx context.Context, jobID string) (*RetrainingJob, error)
	GetRetrainingHistory(ctx context.Context, modelName string, limit int) ([]RetrainingJob, error)

	// Analytics and monitoring
	GetLabelingStats(ctx context.Context, timeframe string) (*LabelingStats, error)
	DetectModelDrift(ctx context.Context, modelName string) (*DriftReport, error)
	ValidateLabels(ctx context.Context, taskIDs []string) (*ValidationReport, error)
}

// Repository interface defines storage operations for active learning
type Repository interface {
	// Task operations
	SaveTask(ctx context.Context, task *LabelingTask) error
	GetTask(ctx context.Context, taskID string) (*LabelingTask, error)
	GetTasks(ctx context.Context, filters TaskFilters) ([]LabelingTask, error)
	UpdateTaskStatus(ctx context.Context, taskID string, status TaskStatus) error
	DeleteTask(ctx context.Context, taskID string) error

	// Label operations
	SaveLabel(ctx context.Context, label *Label) error
	GetLabel(ctx context.Context, labelID string) (*Label, error)
	GetLabels(ctx context.Context, filters LabelFilters) ([]Label, error)
	ValidateLabel(ctx context.Context, labelID, validatorID string) error

	// Queue operations
	SaveQueue(ctx context.Context, queue *LabelingQueue) error
	GetQueue(ctx context.Context, queueID string) (*LabelingQueue, error)
	UpdateQueue(ctx context.Context, queue *LabelingQueue) error
	DeleteQueue(ctx context.Context, queueID string) error

	// Labeler operations
	SaveLabeler(ctx context.Context, labeler *Labeler) error
	GetLabeler(ctx context.Context, labelerID string) (*Labeler, error)
	UpdateLabeler(ctx context.Context, labeler *Labeler) error
	GetActiveLabelers(ctx context.Context) ([]Labeler, error)

	// Retraining job operations
	SaveRetrainingJob(ctx context.Context, job *RetrainingJob) error
	GetRetrainingJob(ctx context.Context, jobID string) (*RetrainingJob, error)
	UpdateRetrainingJob(ctx context.Context, job *RetrainingJob) error
	GetRetrainingJobs(ctx context.Context, modelName string, limit int) ([]RetrainingJob, error)
}

// Additional supporting types

// TaskFilters defines filters for querying tasks
type TaskFilters struct {
	TaskType      *TaskType   `json:"task_type,omitempty"`
	Status        *TaskStatus `json:"status,omitempty"`
	Priority      *Priority   `json:"priority,omitempty"`
	AssignedTo    *string     `json:"assigned_to,omitempty"`
	ModelName     *string     `json:"model_name,omitempty"`
	CreatedSince  *time.Time  `json:"created_since,omitempty"`
	CreatedBefore *time.Time  `json:"created_before,omitempty"`
	Limit         int         `json:"limit,omitempty"`
	Offset        int         `json:"offset,omitempty"`
}

// LabelFilters defines filters for querying labels
type LabelFilters struct {
	TaskID        *string    `json:"task_id,omitempty"`
	LabeledBy     *string    `json:"labeled_by,omitempty"`
	ValidatedBy   *string    `json:"validated_by,omitempty"`
	LabeledSince  *time.Time `json:"labeled_since,omitempty"`
	LabeledBefore *time.Time `json:"labeled_before,omitempty"`
	Limit         int        `json:"limit,omitempty"`
	Offset        int        `json:"offset,omitempty"`
}

// LabelingStats represents overall labeling statistics
type LabelingStats struct {
	TotalTasks         int                `json:"total_tasks"`
	CompletedTasks     int                `json:"completed_tasks"`
	ActiveLabelers     int                `json:"active_labelers"`
	AverageTimePerTask float64            `json:"average_time_per_task"`
	ThroughputPerHour  float64            `json:"throughput_per_hour"`
	QualityScore       float64            `json:"quality_score"`
	TasksByType        map[TaskType]int   `json:"tasks_by_type"`
	TasksByStatus      map[TaskStatus]int `json:"tasks_by_status"`
	LabelerRankings    []LabelerStats     `json:"labeler_rankings"`
	Timestamp          time.Time          `json:"timestamp"`
}

// DriftReport represents model drift detection results
type DriftReport struct {
	ModelName         string                 `json:"model_name"`
	ModelVersion      string                 `json:"model_version"`
	DriftScore        float64                `json:"drift_score"`        // 0-1, higher means more drift
	IsSignificant     bool                   `json:"is_significant"`     // Whether drift is significant
	DriftType         string                 `json:"drift_type"`         // "COVARIATE", "PRIOR", "CONCEPT"
	DetectionMethod   string                 `json:"detection_method"`   // Algorithm used
	AffectedFeatures  []string               `json:"affected_features"`  // Features showing drift
	RecommendedAction string                 `json:"recommended_action"` // What to do
	Timestamp         time.Time              `json:"timestamp"`
	Metadata          map[string]interface{} `json:"metadata"`
}

// ValidationReport represents label validation results
type ValidationReport struct {
	TaskIDs            []string           `json:"task_ids"`
	TotalTasks         int                `json:"total_tasks"`
	ValidatedTasks     int                `json:"validated_tasks"`
	AgreementRate      float64            `json:"agreement_rate"`      // Inter-labeler agreement
	ConflictingTasks   []string           `json:"conflicting_tasks"`   // Tasks with disagreements
	QualityScore       float64            `json:"quality_score"`       // Overall quality
	LabelerPerformance map[string]float64 `json:"labeler_performance"` // Per-labeler accuracy
	Recommendations    []string           `json:"recommendations"`     // Improvement suggestions
	Timestamp          time.Time          `json:"timestamp"`
}
