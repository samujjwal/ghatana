package labeling

import (
	"context"
	"fmt"
	"sync"
	"time"
)

// InMemoryRepository implements the Repository interface using in-memory storage
type InMemoryRepository struct {
	mu             sync.RWMutex
	tasks          map[string]*LabelingTask
	labels         map[string]*Label
	queues         map[string]*LabelingQueue
	labelers       map[string]*Labeler
	retrainingJobs map[string]*RetrainingJob
}

// NewInMemoryRepository creates a new in-memory repository
func NewInMemoryRepository() Repository {
	return &InMemoryRepository{
		tasks:          make(map[string]*LabelingTask),
		labels:         make(map[string]*Label),
		queues:         make(map[string]*LabelingQueue),
		labelers:       make(map[string]*Labeler),
		retrainingJobs: make(map[string]*RetrainingJob),
	}
}

// Task operations
func (r *InMemoryRepository) SaveTask(ctx context.Context, task *LabelingTask) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.tasks[task.ID] = task
	return nil
}

func (r *InMemoryRepository) GetTask(ctx context.Context, taskID string) (*LabelingTask, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	task, exists := r.tasks[taskID]
	if !exists {
		return nil, fmt.Errorf("task not found")
	}
	return task, nil
}

func (r *InMemoryRepository) GetTasks(ctx context.Context, filters TaskFilters) ([]LabelingTask, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var tasks []LabelingTask
	count := 0

	for _, task := range r.tasks {
		if count >= filters.Limit {
			break
		}

		// Apply filters
		if filters.Status != nil && task.Status != *filters.Status {
			continue
		}
		if filters.TaskType != nil && task.TaskType != *filters.TaskType {
			continue
		}
		if filters.Priority != nil && task.Priority != *filters.Priority {
			continue
		}
		if filters.AssignedTo != nil && task.AssignedTo != *filters.AssignedTo {
			continue
		}
		if filters.ModelName != nil && task.ModelName != *filters.ModelName {
			continue
		}
		if filters.CreatedSince != nil && task.CreatedAt.Before(*filters.CreatedSince) {
			continue
		}
		if filters.CreatedBefore != nil && task.CreatedAt.After(*filters.CreatedBefore) {
			continue
		}

		tasks = append(tasks, *task)
		count++
	}

	return tasks, nil
}

// Label operations
func (r *InMemoryRepository) SaveLabel(ctx context.Context, label *Label) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.labels[label.ID] = label
	return nil
}

func (r *InMemoryRepository) GetLabel(ctx context.Context, labelID string) (*Label, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	label, exists := r.labels[labelID]
	if !exists {
		return nil, fmt.Errorf("label not found")
	}
	return label, nil
}

func (r *InMemoryRepository) GetLabels(ctx context.Context, filters LabelFilters) ([]Label, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var labels []Label
	count := 0

	for _, label := range r.labels {
		if count >= filters.Limit {
			break
		}

		// Apply filters
		if filters.TaskID != nil && label.TaskID != *filters.TaskID {
			continue
		}
		if filters.LabeledBy != nil && label.LabeledBy != *filters.LabeledBy {
			continue
		}
		if filters.LabeledSince != nil && label.LabeledAt.Before(*filters.LabeledSince) {
			continue
		}
		if filters.LabeledBefore != nil && label.LabeledAt.After(*filters.LabeledBefore) {
			continue
		}

		labels = append(labels, *label)
		count++
	}

	return labels, nil
}

// Queue operations
func (r *InMemoryRepository) SaveQueue(ctx context.Context, queue *LabelingQueue) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.queues[queue.ID] = queue
	return nil
}

func (r *InMemoryRepository) GetQueue(ctx context.Context, queueID string) (*LabelingQueue, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	queue, exists := r.queues[queueID]
	if !exists {
		return nil, fmt.Errorf("queue not found")
	}
	return queue, nil
}

func (r *InMemoryRepository) UpdateQueue(ctx context.Context, queue *LabelingQueue) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.queues[queue.ID]; !exists {
		return fmt.Errorf("queue not found")
	}

	r.queues[queue.ID] = queue
	return nil
}

// Labeler operations
func (r *InMemoryRepository) SaveLabeler(ctx context.Context, labeler *Labeler) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.labelers[labeler.ID] = labeler
	return nil
}

func (r *InMemoryRepository) GetLabeler(ctx context.Context, labelerID string) (*Labeler, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	labeler, exists := r.labelers[labelerID]
	if !exists {
		return nil, fmt.Errorf("labeler not found")
	}
	return labeler, nil
}

func (r *InMemoryRepository) UpdateLabeler(ctx context.Context, labeler *Labeler) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.labelers[labeler.ID]; !exists {
		return fmt.Errorf("labeler not found")
	}

	r.labelers[labeler.ID] = labeler
	return nil
}

func (r *InMemoryRepository) GetActiveLabelers(ctx context.Context) ([]Labeler, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var activeLabelers []Labeler
	cutoff := time.Now().Add(-24 * time.Hour) // Consider active if seen in last 24 hours

	for _, labeler := range r.labelers {
		if labeler.LastActiveAt != nil && labeler.LastActiveAt.After(cutoff) {
			activeLabelers = append(activeLabelers, *labeler)
		}
	}

	return activeLabelers, nil
}

// Retraining job operations
func (r *InMemoryRepository) SaveRetrainingJob(ctx context.Context, job *RetrainingJob) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.retrainingJobs[job.ID] = job
	return nil
}

func (r *InMemoryRepository) GetRetrainingJob(ctx context.Context, jobID string) (*RetrainingJob, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	job, exists := r.retrainingJobs[jobID]
	if !exists {
		return nil, fmt.Errorf("retraining job not found")
	}
	return job, nil
}

func (r *InMemoryRepository) GetRetrainingJobs(ctx context.Context, modelName string, limit int) ([]RetrainingJob, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var jobs []RetrainingJob
	count := 0

	for _, job := range r.retrainingJobs {
		if count >= limit {
			break
		}

		if job.ModelName == modelName {
			jobs = append(jobs, *job)
			count++
		}
	}

	return jobs, nil
}

// Additional Repository interface methods

func (r *InMemoryRepository) UpdateTask(ctx context.Context, task *LabelingTask) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.tasks[task.ID]; !exists {
		return fmt.Errorf("task not found")
	}

	r.tasks[task.ID] = task
	return nil
}

func (r *InMemoryRepository) UpdateTaskStatus(ctx context.Context, taskID string, status TaskStatus) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	task, exists := r.tasks[taskID]
	if !exists {
		return fmt.Errorf("task not found")
	}

	task.Status = status
	return nil
}

func (r *InMemoryRepository) DeleteTask(ctx context.Context, taskID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.tasks[taskID]; !exists {
		return fmt.Errorf("task not found")
	}

	delete(r.tasks, taskID)
	return nil
}

func (r *InMemoryRepository) ValidateLabel(ctx context.Context, labelID, validatorID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	label, exists := r.labels[labelID]
	if !exists {
		return fmt.Errorf("label not found")
	}

	label.ValidatedBy = validatorID
	now := time.Now()
	label.ValidatedAt = &now
	return nil
}

func (r *InMemoryRepository) DeleteQueue(ctx context.Context, queueID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.queues[queueID]; !exists {
		return fmt.Errorf("queue not found")
	}

	delete(r.queues, queueID)
	return nil
}

func (r *InMemoryRepository) UpdateRetrainingJob(ctx context.Context, job *RetrainingJob) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if _, exists := r.retrainingJobs[job.ID]; !exists {
		return fmt.Errorf("retraining job not found")
	}

	r.retrainingJobs[job.ID] = job
	return nil
}
