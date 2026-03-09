package labeling

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"sort"
	"time"
)

// LabelingService implements the Service interface
type LabelingService struct {
	repository Repository
	config     ServiceConfig
}

// ServiceConfig defines configuration for the labeling service
type ServiceConfig struct {
	DefaultQueueConfig     QueueConfig   `json:"default_queue_config"`
	MaxTasksPerLabeler     int           `json:"max_tasks_per_labeler"`
	TaskAssignmentTimeout  time.Duration `json:"task_assignment_timeout"`
	QualityCheckInterval   time.Duration `json:"quality_check_interval"`
	DriftCheckInterval     time.Duration `json:"drift_check_interval"`
	RetrainingCooldown     time.Duration `json:"retraining_cooldown"`
	MinLabelsForRetraining int           `json:"min_labels_for_retraining"`
	ValidationSampleRate   float64       `json:"validation_sample_rate"`
}

// NewLabelingService creates a new labeling service instance
func NewLabelingService(repository Repository) *LabelingService {
	return &LabelingService{
		repository: repository,
		config:     getDefaultServiceConfig(),
	}
}

// EnqueueTask adds a new task to the labeling queue
func (s *LabelingService) EnqueueTask(ctx context.Context, task *LabelingTask) error {
	// Generate ID if not provided
	if task.ID == "" {
		task.ID = generateID()
	}

	// Set creation time if not provided
	if task.CreatedAt.IsZero() {
		task.CreatedAt = time.Now()
	}

	// Set default status
	if task.Status == "" {
		task.Status = StatusPending
	}

	// Determine priority based on confidence and model drift
	if task.Priority == "" {
		task.Priority = s.determinePriority(task)
	}

	// Validate required fields
	if task.TaskType == "" {
		return fmt.Errorf("task_type is required")
	}
	if task.ModelName == "" {
		return fmt.Errorf("model_name is required")
	}
	if len(task.Features) == 0 {
		return fmt.Errorf("features are required")
	}

	return s.repository.SaveTask(ctx, task)
}

// GetNextTask retrieves the next task for a labeler
func (s *LabelingService) GetNextTask(ctx context.Context, labelerID string, taskTypes []TaskType) (*LabelingTask, error) {
	// Get labeler information to understand their preferences and capabilities
	labeler, err := s.repository.GetLabeler(ctx, labelerID)
	if err != nil {
		return nil, fmt.Errorf("failed to get labeler: %w", err)
	}

	// Build filters based on labeler preferences and task types
	statusPending := StatusPending
	filters := TaskFilters{
		Status: &statusPending,
		Limit:  20, // Get a small batch to choose from
	}

	// Filter by task types if specified
	if len(taskTypes) > 0 {
		// For simplicity, use the first task type
		filters.TaskType = &taskTypes[0]
	}

	// Get candidate tasks
	tasks, err := s.repository.GetTasks(ctx, filters)
	if err != nil {
		return nil, fmt.Errorf("failed to get tasks: %w", err)
	}

	if len(tasks) == 0 {
		return nil, fmt.Errorf("no tasks available")
	}

	// Select the best task for this labeler
	selectedTask := s.selectBestTask(tasks, labeler)

	// Assign the task
	if err := s.AssignTask(ctx, selectedTask.ID, labelerID); err != nil {
		return nil, fmt.Errorf("failed to assign task: %w", err)
	}

	return selectedTask, nil
}

// AssignTask assigns a task to a labeler
func (s *LabelingService) AssignTask(ctx context.Context, taskID, labelerID string) error {
	task, err := s.repository.GetTask(ctx, taskID)
	if err != nil {
		return fmt.Errorf("failed to get task: %w", err)
	}

	if task.Status != StatusPending {
		return fmt.Errorf("task is not available for assignment")
	}

	// Update task assignment
	task.Status = StatusAssigned
	task.AssignedTo = labelerID
	now := time.Now()
	task.AssignedAt = &now

	return s.repository.SaveTask(ctx, task)
}

// CompleteTask marks a task as completed with a label
func (s *LabelingService) CompleteTask(ctx context.Context, taskID string, label *Label) error {
	task, err := s.repository.GetTask(ctx, taskID)
	if err != nil {
		return fmt.Errorf("failed to get task: %w", err)
	}

	if task.Status != StatusAssigned {
		return fmt.Errorf("task is not assigned")
	}

	// Generate label ID if not provided
	if label.ID == "" {
		label.ID = generateID()
	}

	// Set task ID and completion time
	label.TaskID = taskID
	if label.LabeledAt.IsZero() {
		label.LabeledAt = time.Now()
	}

	// Save the label
	if err := s.repository.SaveLabel(ctx, label); err != nil {
		return fmt.Errorf("failed to save label: %w", err)
	}

	// Update task status
	now := time.Now()
	task.Status = StatusCompleted
	task.CompletedAt = &now

	if err := s.repository.SaveTask(ctx, task); err != nil {
		return fmt.Errorf("failed to update task: %w", err)
	}

	// Update labeler statistics
	if err := s.updateLabelerStats(ctx, task.AssignedTo, label); err != nil {
		// Log error but don't fail the operation
		fmt.Printf("Failed to update labeler stats: %v\n", err)
	}

	// Check if we should trigger retraining
	if err := s.checkRetrainingTrigger(ctx, task.ModelName); err != nil {
		// Log error but don't fail the operation
		fmt.Printf("Failed to check retraining trigger: %v\n", err)
	}

	return nil
}

// SkipTask marks a task as skipped
func (s *LabelingService) SkipTask(ctx context.Context, taskID, reason string) error {
	task, err := s.repository.GetTask(ctx, taskID)
	if err != nil {
		return fmt.Errorf("failed to get task: %w", err)
	}

	if task.Status != StatusAssigned {
		return fmt.Errorf("task is not assigned")
	}

	// Update task status and reset assignment
	task.Status = StatusSkipped
	task.AssignedTo = ""
	task.AssignedAt = nil

	// Add skip reason to metadata
	if task.Metadata == nil {
		task.Metadata = make(map[string]interface{})
	}
	task.Metadata["skip_reason"] = reason
	task.Metadata["skipped_at"] = time.Now()

	return s.repository.SaveTask(ctx, task)
}

// CreateQueue creates a new labeling queue
func (s *LabelingService) CreateQueue(ctx context.Context, queue *LabelingQueue) error {
	if queue.ID == "" {
		queue.ID = generateID()
	}

	if queue.CreatedAt.IsZero() {
		queue.CreatedAt = time.Now()
	}
	queue.UpdatedAt = time.Now()

	// Set default configuration if not provided
	if queue.Config.MaxPendingTasks == 0 {
		queue.Config = s.config.DefaultQueueConfig
	}

	return s.repository.SaveQueue(ctx, queue)
}

// GetQueue retrieves a labeling queue
func (s *LabelingService) GetQueue(ctx context.Context, queueID string) (*LabelingQueue, error) {
	return s.repository.GetQueue(ctx, queueID)
}

// UpdateQueueConfig updates queue configuration
func (s *LabelingService) UpdateQueueConfig(ctx context.Context, queueID string, config QueueConfig) error {
	queue, err := s.repository.GetQueue(ctx, queueID)
	if err != nil {
		return fmt.Errorf("failed to get queue: %w", err)
	}

	queue.Config = config
	queue.UpdatedAt = time.Now()

	return s.repository.UpdateQueue(ctx, queue)
}

// GetQueueStats calculates and returns queue statistics
func (s *LabelingService) GetQueueStats(ctx context.Context, queueID string) (*QueueStats, error) {
	// Get all tasks for this queue (assuming queue maps to task type or model)
	filters := TaskFilters{
		Limit: 10000, // Get all tasks for accurate stats
	}

	tasks, err := s.repository.GetTasks(ctx, filters)
	if err != nil {
		return nil, fmt.Errorf("failed to get tasks: %w", err)
	}

	stats := &QueueStats{
		LastUpdated: time.Now(),
	}

	var totalTimeSpent float64
	var completedTasksCount int

	// Calculate statistics
	for _, task := range tasks {
		stats.TotalTasks++

		switch task.Status {
		case StatusPending:
			stats.PendingTasks++
		case StatusCompleted:
			stats.CompletedTasks++
			completedTasksCount++
		case StatusSkipped:
			stats.SkippedTasks++
		case StatusExpired:
			stats.ExpiredTasks++
		}

		// Get label for completed tasks to calculate time spent
		if task.Status == StatusCompleted {
			labelFilters := LabelFilters{
				TaskID: &task.ID,
				Limit:  1,
			}
			labels, err := s.repository.GetLabels(ctx, labelFilters)
			if err == nil && len(labels) > 0 {
				totalTimeSpent += labels[0].TimeSpent.Seconds()
			}
		}
	}

	// Calculate derived metrics
	if completedTasksCount > 0 {
		stats.AverageTimeSpent = totalTimeSpent / float64(completedTasksCount)

		// Calculate throughput (tasks per hour)
		if stats.AverageTimeSpent > 0 {
			stats.ThroughputPerHour = 3600.0 / stats.AverageTimeSpent
		}
	}

	// TODO: Calculate quality score based on validation results
	stats.QualityScore = 0.95 // Placeholder

	return stats, nil
}

// RegisterLabeler registers a new labeler
func (s *LabelingService) RegisterLabeler(ctx context.Context, labeler *Labeler) error {
	if labeler.ID == "" {
		labeler.ID = generateID()
	}

	if labeler.CreatedAt.IsZero() {
		labeler.CreatedAt = time.Now()
	}

	// Initialize statistics
	labeler.Stats = LabelerStats{
		LastUpdated: time.Now(),
	}

	// Set default preferences
	if labeler.Preferences.MaxTasksPerSession == 0 {
		labeler.Preferences.MaxTasksPerSession = 50
	}

	return s.repository.SaveLabeler(ctx, labeler)
}

// GetLabeler retrieves a labeler
func (s *LabelingService) GetLabeler(ctx context.Context, labelerID string) (*Labeler, error) {
	return s.repository.GetLabeler(ctx, labelerID)
}

// UpdateLabelerStats updates labeler statistics
func (s *LabelingService) UpdateLabelerStats(ctx context.Context, labelerID string, stats LabelerStats) error {
	labeler, err := s.repository.GetLabeler(ctx, labelerID)
	if err != nil {
		return fmt.Errorf("failed to get labeler: %w", err)
	}

	stats.LastUpdated = time.Now()
	labeler.Stats = stats

	return s.repository.UpdateLabeler(ctx, labeler)
}

// GetLeaderboard returns top labelers by various metrics
func (s *LabelingService) GetLeaderboard(ctx context.Context, timeframe string) ([]LabelerStats, error) {
	labelers, err := s.repository.GetActiveLabelers(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get labelers: %w", err)
	}

	// Extract stats and sort by quality score
	var stats []LabelerStats
	for _, labeler := range labelers {
		stats = append(stats, labeler.Stats)
	}

	sort.Slice(stats, func(i, j int) bool {
		return stats[i].QualityScore > stats[j].QualityScore
	})

	// Return top 10
	if len(stats) > 10 {
		stats = stats[:10]
	}

	return stats, nil
}

// TriggerRetraining starts a model retraining job
func (s *LabelingService) TriggerRetraining(ctx context.Context, modelName string, reason string) (*RetrainingJob, error) {
	job := &RetrainingJob{
		ID:             generateID(),
		ModelName:      modelName,
		CurrentVersion: "v1.0", // TODO: Get actual version
		NewVersion:     fmt.Sprintf("v1.%d", time.Now().Unix()),
		Status:         JobStatusQueued,
		TriggerReason:  reason,
		StartedAt:      time.Now(),
	}

	// Count new labels since last retraining
	// TODO: Implement proper counting logic
	job.NewLabelsCount = 100    // Placeholder
	job.TotalLabelsCount = 1000 // Placeholder

	if err := s.repository.SaveRetrainingJob(ctx, job); err != nil {
		return nil, fmt.Errorf("failed to save retraining job: %w", err)
	}

	// TODO: Actually trigger the retraining process
	// For now, simulate completion
	go s.simulateRetraining(job)

	return job, nil
}

// GetRetrainingJob retrieves a retraining job
func (s *LabelingService) GetRetrainingJob(ctx context.Context, jobID string) (*RetrainingJob, error) {
	return s.repository.GetRetrainingJob(ctx, jobID)
}

// GetRetrainingHistory retrieves retraining job history
func (s *LabelingService) GetRetrainingHistory(ctx context.Context, modelName string, limit int) ([]RetrainingJob, error) {
	return s.repository.GetRetrainingJobs(ctx, modelName, limit)
}

// GetLabelingStats returns overall labeling statistics
func (s *LabelingService) GetLabelingStats(ctx context.Context, timeframe string) (*LabelingStats, error) {
	// Get all tasks
	tasks, err := s.repository.GetTasks(ctx, TaskFilters{Limit: 10000})
	if err != nil {
		return nil, fmt.Errorf("failed to get tasks: %w", err)
	}

	// Get active labelers
	labelers, err := s.repository.GetActiveLabelers(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get labelers: %w", err)
	}

	stats := &LabelingStats{
		TotalTasks:     len(tasks),
		ActiveLabelers: len(labelers),
		TasksByType:    make(map[TaskType]int),
		TasksByStatus:  make(map[TaskStatus]int),
		Timestamp:      time.Now(),
	}

	// Count tasks by type and status
	for _, task := range tasks {
		stats.TasksByType[task.TaskType]++
		stats.TasksByStatus[task.Status]++

		if task.Status == StatusCompleted {
			stats.CompletedTasks++
		}
	}

	// Calculate derived metrics
	if stats.CompletedTasks > 0 {
		stats.AverageTimePerTask = 30.0 // Placeholder - should calculate from actual labels
		stats.ThroughputPerHour = 120.0 // Placeholder
	}

	stats.QualityScore = 0.92 // Placeholder

	// Get labeler rankings
	rankings, err := s.GetLeaderboard(ctx, timeframe)
	if err == nil {
		stats.LabelerRankings = rankings
	}

	return stats, nil
}

// DetectModelDrift analyzes model performance to detect drift
func (s *LabelingService) DetectModelDrift(ctx context.Context, modelName string) (*DriftReport, error) {
	// This is a simplified drift detection implementation
	// In production, this would analyze prediction distributions, feature drift, etc.

	report := &DriftReport{
		ModelName:         modelName,
		ModelVersion:      "v1.0", // TODO: Get actual version
		DriftScore:        0.15,   // Placeholder
		IsSignificant:     false,
		DriftType:         "COVARIATE",
		DetectionMethod:   "KL_DIVERGENCE",
		AffectedFeatures:  []string{"feature_1", "feature_3"},
		RecommendedAction: "Monitor for now, consider retraining if drift increases",
		Timestamp:         time.Now(),
		Metadata:          make(map[string]interface{}),
	}

	if report.DriftScore > 0.3 {
		report.IsSignificant = true
		report.RecommendedAction = "Immediate retraining recommended"
	}

	return report, nil
}

// ValidateLabels validates labels for quality assurance
func (s *LabelingService) ValidateLabels(ctx context.Context, taskIDs []string) (*ValidationReport, error) {
	// This is a simplified validation implementation
	// In production, this would compare labels from multiple labelers, check consistency, etc.

	report := &ValidationReport{
		TaskIDs:            taskIDs,
		TotalTasks:         len(taskIDs),
		ValidatedTasks:     len(taskIDs),
		AgreementRate:      0.88, // Placeholder
		ConflictingTasks:   []string{},
		QualityScore:       0.90, // Placeholder
		LabelerPerformance: make(map[string]float64),
		Recommendations:    []string{"Overall quality is good", "Consider additional training for edge cases"},
		Timestamp:          time.Now(),
	}

	return report, nil
}

// Helper methods

func (s *LabelingService) determinePriority(task *LabelingTask) Priority {
	// Determine priority based on confidence threshold
	if task.Confidence < 0.3 {
		return PriorityHigh
	} else if task.Confidence < 0.7 {
		return PriorityMedium
	}
	return PriorityLow
}

func (s *LabelingService) selectBestTask(tasks []LabelingTask, labeler *Labeler) *LabelingTask {
	// Simple selection: highest priority first, then lowest confidence
	sort.Slice(tasks, func(i, j int) bool {
		if tasks[i].Priority != tasks[j].Priority {
			return getPriorityValue(tasks[i].Priority) > getPriorityValue(tasks[j].Priority)
		}
		return tasks[i].Confidence < tasks[j].Confidence
	})

	return &tasks[0]
}

func (s *LabelingService) updateLabelerStats(ctx context.Context, labelerID string, label *Label) error {
	labeler, err := s.repository.GetLabeler(ctx, labelerID)
	if err != nil {
		return err
	}

	// Update statistics
	labeler.Stats.TotalTasksCompleted++
	labeler.Stats.TasksCompletedToday++ // TODO: Reset daily

	// Update average time per task
	totalTime := float64(labeler.Stats.TotalTasksCompleted-1)*labeler.Stats.AverageTimePerTask + label.TimeSpent.Seconds()
	labeler.Stats.AverageTimePerTask = totalTime / float64(labeler.Stats.TotalTasksCompleted)

	// Update last active time
	now := time.Now()
	labeler.LastActiveAt = &now
	labeler.Stats.LastUpdated = now

	return s.repository.UpdateLabeler(ctx, labeler)
}

func (s *LabelingService) checkRetrainingTrigger(ctx context.Context, modelName string) error {
	// Count labels since last retraining
	// TODO: Implement proper counting logic

	// For now, trigger retraining every 100 new labels
	newLabelsCount := 105 // Placeholder

	if newLabelsCount >= s.config.MinLabelsForRetraining {
		_, err := s.TriggerRetraining(ctx, modelName, "Reached minimum labels threshold")
		return err
	}

	return nil
}

func (s *LabelingService) simulateRetraining(job *RetrainingJob) {
	// Simulate retraining process
	time.Sleep(30 * time.Second)

	// Update job status
	job.Status = JobStatusCompleted
	now := time.Now()
	job.CompletedAt = &now

	// Simulate metrics
	job.TrainingMetrics = TrainingMetrics{
		TrainingLoss:       0.15,
		ValidationLoss:     0.18,
		TrainingAccuracy:   0.92,
		ValidationAccuracy: 0.89,
		EpochsCompleted:    10,
		TrainingTime:       25 * time.Second,
	}

	job.ValidationMetrics = ValidationMetrics{
		Accuracy:  0.89,
		Precision: 0.87,
		Recall:    0.91,
		F1Score:   0.89,
		AUC:       0.94,
	}

	job.ImprovementPct = 2.5 // 2.5% improvement
	job.ShouldDeploy = job.ImprovementPct >= 1.0

	// Save updated job (in production, this would be done in a proper job queue)
	// For now, just log completion
	fmt.Printf("Retraining job %s completed with %.1f%% improvement\n", job.ID, job.ImprovementPct)
}

func getDefaultServiceConfig() ServiceConfig {
	return ServiceConfig{
		DefaultQueueConfig: QueueConfig{
			MaxPendingTasks:     1000,
			ConfidenceThreshold: 0.7,
			DriftThreshold:      0.3,
			SamplingRate:        0.1,
			TaskExpiration:      24 * time.Hour,
			RequireValidation:   false,
			ValidationThreshold: 0.8,
			AutoRetrain:         true,
			RetrainThreshold:    100,
			QualityThreshold:    0.8,
		},
		MaxTasksPerLabeler:     5,
		TaskAssignmentTimeout:  15 * time.Minute,
		QualityCheckInterval:   1 * time.Hour,
		DriftCheckInterval:     6 * time.Hour,
		RetrainingCooldown:     24 * time.Hour,
		MinLabelsForRetraining: 100,
		ValidationSampleRate:   0.2,
	}
}

func getPriorityValue(priority Priority) int {
	switch priority {
	case PriorityHigh:
		return 3
	case PriorityMedium:
		return 2
	case PriorityLow:
		return 1
	default:
		return 0
	}
}

func generateID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}
