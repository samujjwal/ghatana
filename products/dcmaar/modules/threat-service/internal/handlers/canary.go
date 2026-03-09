package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gorilla/mux"
	"github.com/samujjwal/dcmaar/apps/server/internal/canary"
)

type CanaryHandlers struct {
	service canary.Service
}

func NewCanaryHandlers(service canary.Service) *CanaryHandlers {
	return &CanaryHandlers{
		service: service,
	}
}

// Register routes with the router
func (h *CanaryHandlers) RegisterRoutes(r *mux.Router) {
	// Selection operations
	r.HandleFunc("/canary/select", h.SelectCanaryNodes).Methods("POST")
	r.HandleFunc("/canary/selections/{id}", h.GetCanarySelection).Methods("GET")
	r.HandleFunc("/canary/selections/{id}/status", h.UpdateSelectionStatus).Methods("PUT")
	r.HandleFunc("/canary/selections", h.ListSelections).Methods("GET")

	// Node operations
	r.HandleFunc("/canary/nodes", h.ListCandidateNodes).Methods("GET")
	r.HandleFunc("/canary/nodes/{id}/metrics", h.GetNodeMetrics).Methods("GET")
	r.HandleFunc("/canary/nodes/{id}/health", h.ValidateNodeHealth).Methods("GET")

	// Risk assessment
	r.HandleFunc("/canary/selections/{id}/risk", h.AssessDeploymentRisk).Methods("GET")
	r.HandleFunc("/canary/risk/recommendations", h.GetRiskRecommendations).Methods("POST")

	// Monitoring and control
	r.HandleFunc("/canary/selections/{id}/health", h.MonitorCanaryHealth).Methods("GET")
	r.HandleFunc("/canary/selections/{id}/rollback", h.TriggerRollback).Methods("POST")
	r.HandleFunc("/canary/selections/history", h.GetSelectionHistory).Methods("GET")

	// Analytics
	r.HandleFunc("/canary/analytics", h.GetSelectionAnalytics).Methods("GET")
	r.HandleFunc("/canary/selections/{id}/performance", h.GetPerformanceMetrics).Methods("GET")
}

// SelectCanaryNodes intelligently selects nodes for canary deployment
func (h *CanaryHandlers) SelectCanaryNodes(w http.ResponseWriter, r *http.Request) {
	var criteria canary.SelectionCriteria
	if err := json.NewDecoder(r.Body).Decode(&criteria); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate required fields
	if criteria.TrafficPercentage <= 0 || criteria.TrafficPercentage > 100 {
		http.Error(w, "traffic_percentage must be between 0 and 100", http.StatusBadRequest)
		return
	}

	selection, err := h.service.SelectCanaryNodes(r.Context(), criteria)
	if err != nil {
		if strings.Contains(err.Error(), "no candidate nodes") || strings.Contains(err.Error(), "no nodes meet") {
			http.Error(w, err.Error(), http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(selection)
}

// GetCanarySelection retrieves a specific canary selection
func (h *CanaryHandlers) GetCanarySelection(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	selection, err := h.service.GetCanarySelection(r.Context(), selectionID)
	if err != nil {
		http.Error(w, "Selection not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(selection)
}

// UpdateSelectionStatus updates the status of a canary selection
func (h *CanaryHandlers) UpdateSelectionStatus(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	var request struct {
		Status canary.SelectionStatus `json:"status"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate status
	validStatuses := []canary.SelectionStatus{
		canary.StatusSelecting, canary.StatusSelected, canary.StatusDeploying,
		canary.StatusActive, canary.StatusSuccess, canary.StatusFailed, canary.StatusRolledBack,
	}

	isValid := false
	for _, validStatus := range validStatuses {
		if request.Status == validStatus {
			isValid = true
			break
		}
	}

	if !isValid {
		http.Error(w, "Invalid status", http.StatusBadRequest)
		return
	}

	err := h.service.UpdateSelectionStatus(r.Context(), selectionID, request.Status)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Selection not found", http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// ListSelections lists canary selections with filtering
func (h *CanaryHandlers) ListSelections(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	filters := canary.SelectionFilters{
		Limit: 50, // Default limit
	}

	if serviceName := r.URL.Query().Get("service_name"); serviceName != "" {
		filters.ServiceName = &serviceName
	}

	if status := r.URL.Query().Get("status"); status != "" {
		selectionStatus := canary.SelectionStatus(status)
		filters.Status = &selectionStatus
	}

	if createdAfter := r.URL.Query().Get("created_after"); createdAfter != "" {
		if t, err := time.Parse(time.RFC3339, createdAfter); err == nil {
			filters.CreatedAfter = &t
		}
	}

	if createdBefore := r.URL.Query().Get("created_before"); createdBefore != "" {
		if t, err := time.Parse(time.RFC3339, createdBefore); err == nil {
			filters.CreatedBefore = &t
		}
	}

	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil && limit > 0 && limit <= 1000 {
			filters.Limit = limit
		}
	}

	if offsetStr := r.URL.Query().Get("offset"); offsetStr != "" {
		if offset, err := strconv.Atoi(offsetStr); err == nil && offset >= 0 {
			filters.Offset = offset
		}
	}

	// For now, return empty list since we need repository implementation
	selections := []canary.CanarySelection{}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"selections": selections,
		"total":      len(selections),
		"limit":      filters.Limit,
		"offset":     filters.Offset,
	})
}

// ListCandidateNodes returns available candidate nodes
func (h *CanaryHandlers) ListCandidateNodes(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	filters := canary.NodeFilters{
		Limit: 100, // Default limit
	}

	if region := r.URL.Query().Get("region"); region != "" {
		filters.Region = &region
	}

	if zone := r.URL.Query().Get("zone"); zone != "" {
		filters.Zone = &zone
	}

	if instanceType := r.URL.Query().Get("instance_type"); instanceType != "" {
		filters.InstanceType = &instanceType
	}

	if minHealthStr := r.URL.Query().Get("min_health_score"); minHealthStr != "" {
		if minHealth, err := strconv.ParseFloat(minHealthStr, 64); err == nil {
			filters.MinHealthScore = &minHealth
		}
	}

	if maxLoadStr := r.URL.Query().Get("max_load_cpu"); maxLoadStr != "" {
		if maxLoad, err := strconv.ParseFloat(maxLoadStr, 64); err == nil {
			filters.MaxLoadCPU = &maxLoad
		}
	}

	if userSegments := r.URL.Query().Get("user_segments"); userSegments != "" {
		filters.UserSegments = strings.Split(userSegments, ",")
	}

	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil && limit > 0 && limit <= 1000 {
			filters.Limit = limit
		}
	}

	nodes, err := h.service.ListCandidateNodes(r.Context(), filters)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"nodes": nodes,
		"total": len(nodes),
	})
}

// GetNodeMetrics retrieves current metrics for a node
func (h *CanaryHandlers) GetNodeMetrics(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	nodeID := vars["id"]

	metrics, err := h.service.GetNodeMetrics(r.Context(), nodeID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(metrics)
}

// ValidateNodeHealth performs comprehensive node health check
func (h *CanaryHandlers) ValidateNodeHealth(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	nodeID := vars["id"]

	health, err := h.service.ValidateNodeHealth(r.Context(), nodeID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

// AssessDeploymentRisk analyzes the risk of the selected deployment
func (h *CanaryHandlers) AssessDeploymentRisk(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	selection, err := h.service.GetCanarySelection(r.Context(), selectionID)
	if err != nil {
		http.Error(w, "Selection not found", http.StatusNotFound)
		return
	}

	risk, err := h.service.AssessDeploymentRisk(r.Context(), selection)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(risk)
}

// GetRiskRecommendations provides mitigation strategies for identified risks
func (h *CanaryHandlers) GetRiskRecommendations(w http.ResponseWriter, r *http.Request) {
	var request struct {
		RiskFactors []canary.RiskFactor `json:"risk_factors"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	recommendations, err := h.service.GetRiskRecommendations(r.Context(), request.RiskFactors)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"recommendations": recommendations,
	})
}

// MonitorCanaryHealth monitors the health of an active canary deployment
func (h *CanaryHandlers) MonitorCanaryHealth(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	health, err := h.service.MonitorCanaryHealth(r.Context(), selectionID)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Selection not found", http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

// TriggerRollback initiates rollback of a canary deployment
func (h *CanaryHandlers) TriggerRollback(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	var request struct {
		Reason string `json:"reason"`
	}

	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if request.Reason == "" {
		request.Reason = "Manual rollback requested"
	}

	err := h.service.TriggerRollback(r.Context(), selectionID, request.Reason)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Selection not found", http.StatusNotFound)
		} else if strings.Contains(err.Error(), "can only rollback") {
			http.Error(w, err.Error(), http.StatusConflict)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetSelectionHistory retrieves historical selections for a service
func (h *CanaryHandlers) GetSelectionHistory(w http.ResponseWriter, r *http.Request) {
	serviceID := r.URL.Query().Get("service_id")
	if serviceID == "" {
		http.Error(w, "service_id is required", http.StatusBadRequest)
		return
	}

	limitStr := r.URL.Query().Get("limit")
	limit := 50 // Default limit
	if limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 && l <= 1000 {
			limit = l
		}
	}

	history, err := h.service.GetSelectionHistory(r.Context(), serviceID, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"history":    history,
		"service_id": serviceID,
		"count":      len(history),
	})
}

// GetSelectionAnalytics provides analytics for canary selections
func (h *CanaryHandlers) GetSelectionAnalytics(w http.ResponseWriter, r *http.Request) {
	timeframe := r.URL.Query().Get("timeframe")
	if timeframe == "" {
		timeframe = "week"
	}

	// Validate timeframe
	validTimeframes := []string{"day", "week", "month", "quarter", "year"}
	isValid := false
	for _, valid := range validTimeframes {
		if timeframe == valid {
			isValid = true
			break
		}
	}

	if !isValid {
		http.Error(w, "Invalid timeframe. Must be one of: day, week, month, quarter, year", http.StatusBadRequest)
		return
	}

	analytics, err := h.service.GetSelectionAnalytics(r.Context(), timeframe)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(analytics)
}

// GetPerformanceMetrics provides detailed performance metrics for a selection
func (h *CanaryHandlers) GetPerformanceMetrics(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	selectionID := vars["id"]

	metrics, err := h.service.GetPerformanceMetrics(r.Context(), selectionID)
	if err != nil {
		if strings.Contains(err.Error(), "not found") {
			http.Error(w, "Selection not found", http.StatusNotFound)
		} else {
			http.Error(w, err.Error(), http.StatusInternalServerError)
		}
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(metrics)
}
