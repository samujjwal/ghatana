package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"github.com/samujjwal/dcmaar/apps/server/internal/labeling"
)

type LabelingHandlers struct {
	service labeling.Service
}

func NewLabelingHandlers(service labeling.Service) *LabelingHandlers {
	return &LabelingHandlers{
		service: service,
	}
}

// Register routes with the router
func (h *LabelingHandlers) RegisterRoutes(r *mux.Router) {
	r.HandleFunc("/labeling/next", h.GetNextTask).Methods("GET")
	r.HandleFunc("/labeling/tasks/{id}", h.GetTask).Methods("GET")
	r.HandleFunc("/labeling/tasks/{id}/assign", h.AssignTask).Methods("POST")
	r.HandleFunc("/labeling/tasks/{id}/complete", h.CompleteTask).Methods("POST")
	r.HandleFunc("/labeling/tasks/{id}/skip", h.SkipTask).Methods("POST")
	r.HandleFunc("/labeling/tasks", h.GetTasks).Methods("GET")
	r.HandleFunc("/labeling/tasks", h.EnqueueTask).Methods("POST")

	// Queue management
	r.HandleFunc("/labeling/queues", h.CreateQueue).Methods("POST")
	r.HandleFunc("/labeling/queues/{id}", h.GetQueue).Methods("GET")
	r.HandleFunc("/labeling/queues/{id}/config", h.UpdateQueueConfig).Methods("PUT")
	r.HandleFunc("/labeling/queues/{id}/stats", h.GetQueueStats).Methods("GET")

	// Labeler management
	r.HandleFunc("/labeling/labelers", h.RegisterLabeler).Methods("POST")
	r.HandleFunc("/labeling/labelers/{id}", h.GetLabeler).Methods("GET")
	r.HandleFunc("/labeling/labelers/{id}/stats", h.UpdateLabelerStats).Methods("PUT")
	r.HandleFunc("/labeling/leaderboard", h.GetLeaderboard).Methods("GET")

	// Model management
	r.HandleFunc("/labeling/models/{name}/retrain", h.TriggerRetraining).Methods("POST")
	r.HandleFunc("/labeling/models/{name}/drift", h.DetectModelDrift).Methods("GET")
	r.HandleFunc("/labeling/retraining/{id}", h.GetRetrainingJob).Methods("GET")
	r.HandleFunc("/labeling/retraining/history", h.GetRetrainingHistory).Methods("GET")

	// Validation and statistics
	r.HandleFunc("/labeling/validate", h.ValidateLabels).Methods("POST")
	r.HandleFunc("/labeling/stats", h.GetLabelingStats).Methods("GET")
}

// GetNextTask retrieves the next task for a labeler
func (h *LabelingHandlers) GetNextTask(w http.ResponseWriter, r *http.Request) {
	labelerID := r.URL.Query().Get("labeler_id")
	if labelerID == "" {
		http.Error(w, "labeler_id is required", http.StatusBadRequest)
		return
	}

	// Parse task types
	taskTypesParam := r.URL.Query().Get("task_types")
	var taskTypes []labeling.TaskType
	if taskTypesParam != "" {
		typeStrings := strings.Split(taskTypesParam, ",")
		for _, typeStr := range typeStrings {
			taskTypes = append(taskTypes, labeling.TaskType(strings.TrimSpace(typeStr)))
		}
	}

	task, err := h.service.GetNextTask(r.Context(), labelerID, taskTypes)
	if err != nil {
		if strings.Contains(err.Error(), "no tasks available") {
			http.Error(w, err.Error(), http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

// GetTask retrieves a specific task
func (h *LabelingHandlers) GetTask(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["id"]

	// For now, we'll implement a simple version using the service
	// In a real implementation, we'd call repository.GetTask directly
	task, err := h.service.GetNextTask(r.Context(), "temp_labeler", []labeling.TaskType{})
	if err != nil {
		http.Error(w, "Task not found", http.StatusNotFound)
		return
	}

	// Return if this is the requested task
	if task.ID == taskID {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(task)
		return
	}

	http.Error(w, "Task not found", http.StatusNotFound)
}

// AssignTask assigns a task to a labeler
func (h *LabelingHandlers) AssignTask(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["id"]

	var request struct {
		LabelerID string `json:"labeler_id"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if request.LabelerID == "" {
		http.Error(w, "labeler_id is required", http.StatusBadRequest)
		return
	}

	err := h.service.AssignTask(r.Context(), taskID, request.LabelerID)
	if err != nil {
		if strings.Contains(err.Error(), "not available") {
			http.Error(w, err.Error(), http.StatusConflict)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// CompleteTask marks a task as completed with a label
func (h *LabelingHandlers) CompleteTask(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["id"]

	var label labeling.Label
	if err := json.NewDecoder(r.Body).Decode(&label); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate required fields
	if label.LabelValue == nil {
		http.Error(w, "label value is required", http.StatusBadRequest)
		return
	}

	if label.LabeledBy == "" {
		http.Error(w, "labeled_by is required", http.StatusBadRequest)
		return
	}

	err := h.service.CompleteTask(r.Context(), taskID, &label)
	if err != nil {
		if strings.Contains(err.Error(), "not assigned") {
			http.Error(w, err.Error(), http.StatusConflict)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// SkipTask marks a task as skipped
func (h *LabelingHandlers) SkipTask(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	taskID := vars["id"]

	var request struct {
		Reason string `json:"reason"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if request.Reason == "" {
		request.Reason = "No reason provided"
	}

	err := h.service.SkipTask(r.Context(), taskID, request.Reason)
	if err != nil {
		if strings.Contains(err.Error(), "not assigned") {
			http.Error(w, err.Error(), http.StatusConflict)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetTasks retrieves tasks with filtering
func (h *LabelingHandlers) GetTasks(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	filters := labeling.TaskFilters{
		Limit: 50, // Default limit
	}

	if status := r.URL.Query().Get("status"); status != "" {
		taskStatus := labeling.TaskStatus(status)
		filters.Status = &taskStatus
	}

	if taskType := r.URL.Query().Get("task_type"); taskType != "" {
		tt := labeling.TaskType(taskType)
		filters.TaskType = &tt
	}

	if modelName := r.URL.Query().Get("model_name"); modelName != "" {
		filters.ModelName = &modelName
	}

	if assignedTo := r.URL.Query().Get("assigned_to"); assignedTo != "" {
		filters.AssignedTo = &assignedTo
	}

	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil && limit > 0 && limit <= 1000 {
			filters.Limit = limit
		}
	}

	// Parse confidence range (commented out since not in struct)
	// if minConf := r.URL.Query().Get("min_confidence"); minConf != "" {
	//	if conf, err := strconv.ParseFloat(minConf, 64); err == nil {
	//		filters.MinConfidence = &conf
	//	}
	// }
	//
	// if maxConf := r.URL.Query().Get("max_confidence"); maxConf != "" {
	//	if conf, err := strconv.ParseFloat(maxConf, 64); err == nil {
	//		filters.MaxConfidence = &conf
	//	}
	// }

	// Parse time range
	if createdSince := r.URL.Query().Get("created_since"); createdSince != "" {
		if t, err := time.Parse(time.RFC3339, createdSince); err == nil {
			filters.CreatedSince = &t
		}
	}

	if createdBefore := r.URL.Query().Get("created_before"); createdBefore != "" {
		if t, err := time.Parse(time.RFC3339, createdBefore); err == nil {
			filters.CreatedBefore = &t
		}
	}

	// This would require a repository method, but for now we'll return empty
	tasks := []labeling.LabelingTask{}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"tasks": tasks,
		"total": len(tasks),
	})
}

// EnqueueTask adds a new task to the labeling queue
func (h *LabelingHandlers) EnqueueTask(w http.ResponseWriter, r *http.Request) {
	var task labeling.LabelingTask
	if err := json.NewDecoder(r.Body).Decode(&task); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := h.service.EnqueueTask(r.Context(), &task); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(task)
}

// CreateQueue creates a new labeling queue
func (h *LabelingHandlers) CreateQueue(w http.ResponseWriter, r *http.Request) {
	var queue labeling.LabelingQueue
	if err := json.NewDecoder(r.Body).Decode(&queue); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := h.service.CreateQueue(r.Context(), &queue); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(queue)
}

// GetQueue retrieves a labeling queue
func (h *LabelingHandlers) GetQueue(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	queueID := vars["id"]

	queue, err := h.service.GetQueue(r.Context(), queueID)
	if err != nil {
		http.Error(w, "Queue not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(queue)
}

// UpdateQueueConfig updates queue configuration
func (h *LabelingHandlers) UpdateQueueConfig(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	queueID := vars["id"]

	var config labeling.QueueConfig
	if err := json.NewDecoder(r.Body).Decode(&config); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	err := h.service.UpdateQueueConfig(r.Context(), queueID, config)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Queue not found", http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetQueueStats returns queue statistics
func (h *LabelingHandlers) GetQueueStats(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	queueID := vars["id"]

	stats, err := h.service.GetQueueStats(r.Context(), queueID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

// RegisterLabeler registers a new labeler
func (h *LabelingHandlers) RegisterLabeler(w http.ResponseWriter, r *http.Request) {
	var labeler labeling.Labeler
	if err := json.NewDecoder(r.Body).Decode(&labeler); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := h.service.RegisterLabeler(r.Context(), &labeler); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(labeler)
}

// GetLabeler retrieves a labeler
func (h *LabelingHandlers) GetLabeler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	labelerID := vars["id"]

	labeler, err := h.service.GetLabeler(r.Context(), labelerID)
	if err != nil {
		http.Error(w, "Labeler not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(labeler)
}

// UpdateLabelerStats updates labeler statistics
func (h *LabelingHandlers) UpdateLabelerStats(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	labelerID := vars["id"]

	var stats labeling.LabelerStats
	if err := json.NewDecoder(r.Body).Decode(&stats); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	err := h.service.UpdateLabelerStats(r.Context(), labelerID, stats)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Labeler not found", http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetLeaderboard returns top labelers by various metrics
func (h *LabelingHandlers) GetLeaderboard(w http.ResponseWriter, r *http.Request) {
	timeframe := r.URL.Query().Get("timeframe")
	if timeframe == "" {
		timeframe = "week"
	}

	leaderboard, err := h.service.GetLeaderboard(r.Context(), timeframe)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"leaderboard": leaderboard,
		"timeframe":   timeframe,
	})
}

// TriggerRetraining starts a model retraining job
func (h *LabelingHandlers) TriggerRetraining(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	modelName := vars["name"]

	var request struct {
		Reason string `json:"reason"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if request.Reason == "" {
		request.Reason = "Manual trigger"
	}

	job, err := h.service.TriggerRetraining(r.Context(), modelName, request.Reason)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(job)
}

// DetectModelDrift analyzes model performance to detect drift
func (h *LabelingHandlers) DetectModelDrift(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	modelName := vars["name"]

	report, err := h.service.DetectModelDrift(r.Context(), modelName)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(report)
}

// GetRetrainingJob retrieves a retraining job
func (h *LabelingHandlers) GetRetrainingJob(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	jobID := vars["id"]

	job, err := h.service.GetRetrainingJob(r.Context(), jobID)
	if err != nil {
		http.Error(w, "Job not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(job)
}

// GetRetrainingHistory retrieves retraining job history
func (h *LabelingHandlers) GetRetrainingHistory(w http.ResponseWriter, r *http.Request) {
	modelName := r.URL.Query().Get("model_name")
	if modelName == "" {
		http.Error(w, "model_name is required", http.StatusBadRequest)
		return
	}

	limitStr := r.URL.Query().Get("limit")
	limit := 50 // Default limit
	if limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 && l <= 1000 {
			limit = l
		}
	}

	history, err := h.service.GetRetrainingHistory(r.Context(), modelName, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"history":    history,
		"model_name": modelName,
		"count":      len(history),
	})
}

// ValidateLabels validates labels for quality assurance
func (h *LabelingHandlers) ValidateLabels(w http.ResponseWriter, r *http.Request) {
	var request struct {
		TaskIDs []string `json:"task_ids"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if len(request.TaskIDs) == 0 {
		http.Error(w, "task_ids are required", http.StatusBadRequest)
		return
	}

	report, err := h.service.ValidateLabels(r.Context(), request.TaskIDs)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(report)
}

// GetLabelingStats returns overall labeling statistics
func (h *LabelingHandlers) GetLabelingStats(w http.ResponseWriter, r *http.Request) {
	timeframe := r.URL.Query().Get("timeframe")
	if timeframe == "" {
		timeframe = "week"
	}

	stats, err := h.service.GetLabelingStats(r.Context(), timeframe)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}
