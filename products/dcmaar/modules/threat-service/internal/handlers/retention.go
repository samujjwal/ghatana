package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/samujjwal/dcmaar/apps/server/internal/retention"
)

// RetentionHandler handles HTTP requests for the retention advisor service
type RetentionHandler struct {
	advisor retention.Advisor
}

// NewRetentionHandler creates a new retention handler
func NewRetentionHandler(advisor retention.Advisor) *RetentionHandler {
	return &RetentionHandler{
		advisor: advisor,
	}
}

// RegisterRoutes registers all retention advisor routes
func (h *RetentionHandler) RegisterRoutes(mux *http.ServeMux) {
	// Analysis operations
	mux.HandleFunc("POST /api/v1/retention/analyze", h.RunAnalysis)
	mux.HandleFunc("GET /api/v1/retention/results", h.GetAnalysisResults)

	// Recommendations
	mux.HandleFunc("GET /api/v1/retention/recommendations", h.GetRecommendations)
	mux.HandleFunc("POST /api/v1/retention/recommendations/review", h.ReviewRecommendation)
	mux.HandleFunc("POST /api/v1/retention/recommendations/implement", h.ImplementRecommendation)

	// Storage metrics
	mux.HandleFunc("GET /api/v1/retention/metrics", h.GetStorageMetrics)

	// Retention policies
	mux.HandleFunc("GET /api/v1/retention/policies", h.GetRetentionPolicies)
	mux.HandleFunc("POST /api/v1/retention/policies", h.CreateRetentionPolicy)
	mux.HandleFunc("PUT /api/v1/retention/policies", h.UpdateRetentionPolicy)
}

// RunAnalysis handles POST /api/v1/retention/analyze
func (h *RetentionHandler) RunAnalysis(w http.ResponseWriter, r *http.Request) {
	var config retention.RetentionAnalysisConfig
	if err := json.NewDecoder(r.Body).Decode(&config); err != nil {
		// Use default config if no body provided or invalid JSON
		config = retention.RetentionAnalysisConfig{
			AnalysisPeriod:      90 * 24 * time.Hour, // 90 days
			MinDataAge:          30 * 24 * time.Hour, // 30 days
			CostThreshold:       100.0,               // $100/month
			AccessThreshold:     1.0,                 // 1 access per day
			BusinessValueWeight: 0.3,
			ComplianceWeight:    0.4,
			CostWeight:          0.3,
			RiskTolerance:       retention.RiskLevelMedium,
			ExcludeTables:       []string{"system", "information_schema"},
			IncludeCategories:   []string{"events", "metrics", "logs"},
		}
	}

	result, err := h.advisor.AnalyzeRetention(r.Context(), config)
	if err != nil {
		http.Error(w, fmt.Sprintf("Analysis failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// GetAnalysisResults handles GET /api/v1/retention/results
func (h *RetentionHandler) GetAnalysisResults(w http.ResponseWriter, r *http.Request) {
	_ = r.URL.Query().Get("limit") // Parse limit but don't use yet

	// This would require adding GetAnalysisResults to the Advisor interface
	// For now, return empty results
	response := map[string]interface{}{
		"results": []interface{}{},
		"count":   0,
		"message": "Analysis results retrieval not yet implemented",
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// GetRecommendations handles GET /api/v1/retention/recommendations
func (h *RetentionHandler) GetRecommendations(w http.ResponseWriter, r *http.Request) {
	filters := h.parseRecommendationFilters(r)

	recommendations, err := h.advisor.GetRecommendations(r.Context(), filters)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get recommendations: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"recommendations": recommendations,
		"count":           len(recommendations),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// ReviewRecommendation handles POST /api/v1/retention/recommendations/review
func (h *RetentionHandler) ReviewRecommendation(w http.ResponseWriter, r *http.Request) {
	var reviewRequest struct {
		ID         string                         `json:"id"`
		Status     retention.RecommendationStatus `json:"status"`
		ReviewedBy string                         `json:"reviewed_by"`
		Comments   string                         `json:"comments,omitempty"`
	}

	if err := json.NewDecoder(r.Body).Decode(&reviewRequest); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if reviewRequest.ID == "" {
		http.Error(w, "Recommendation ID is required", http.StatusBadRequest)
		return
	}

	if reviewRequest.ReviewedBy == "" {
		http.Error(w, "reviewed_by is required", http.StatusBadRequest)
		return
	}

	if err := h.advisor.ReviewRecommendation(r.Context(), reviewRequest.ID, reviewRequest.Status, reviewRequest.ReviewedBy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to review recommendation: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"success":           true,
		"recommendation_id": reviewRequest.ID,
		"status":            reviewRequest.Status,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// ImplementRecommendation handles POST /api/v1/retention/recommendations/implement
func (h *RetentionHandler) ImplementRecommendation(w http.ResponseWriter, r *http.Request) {
	var implementRequest struct {
		ID string `json:"id"`
	}

	if err := json.NewDecoder(r.Body).Decode(&implementRequest); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if implementRequest.ID == "" {
		http.Error(w, "Recommendation ID is required", http.StatusBadRequest)
		return
	}

	result, err := h.advisor.ImplementRecommendation(r.Context(), implementRequest.ID)
	if err != nil {
		http.Error(w, fmt.Sprintf("Implementation failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// GetStorageMetrics handles GET /api/v1/retention/metrics
func (h *RetentionHandler) GetStorageMetrics(w http.ResponseWriter, r *http.Request) {
	tableName := r.URL.Query().Get("table")
	if tableName == "" {
		http.Error(w, "table parameter is required", http.StatusBadRequest)
		return
	}

	metrics, err := h.advisor.GetStorageMetrics(r.Context(), tableName)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get storage metrics: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(metrics)
}

// GetRetentionPolicies handles GET /api/v1/retention/policies
func (h *RetentionHandler) GetRetentionPolicies(w http.ResponseWriter, r *http.Request) {
	policies, err := h.advisor.GetRetentionPolicies(r.Context())
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get retention policies: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"policies": policies,
		"count":    len(policies),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// CreateRetentionPolicy handles POST /api/v1/retention/policies
func (h *RetentionHandler) CreateRetentionPolicy(w http.ResponseWriter, r *http.Request) {
	var policy retention.RetentionPolicy
	if err := json.NewDecoder(r.Body).Decode(&policy); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if policy.Name == "" {
		http.Error(w, "Policy name is required", http.StatusBadRequest)
		return
	}

	if err := h.advisor.CreateRetentionPolicy(r.Context(), &policy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to create retention policy: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"success": true,
		"id":      policy.ID,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// UpdateRetentionPolicy handles PUT /api/v1/retention/policies
func (h *RetentionHandler) UpdateRetentionPolicy(w http.ResponseWriter, r *http.Request) {
	var policy retention.RetentionPolicy
	if err := json.NewDecoder(r.Body).Decode(&policy); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if policy.ID == "" {
		http.Error(w, "Policy ID is required", http.StatusBadRequest)
		return
	}

	if err := h.advisor.UpdateRetentionPolicy(r.Context(), &policy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to update retention policy: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"success": true,
		"id":      policy.ID,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// Helper method for parsing recommendation filters
func (h *RetentionHandler) parseRecommendationFilters(r *http.Request) retention.RecommendationFilters {
	query := r.URL.Query()
	filters := retention.RecommendationFilters{
		Limit: 50, // default
	}

	// Parse status
	if status := query.Get("status"); status != "" {
		recommendationStatus := retention.RecommendationStatus(status)
		filters.Status = &recommendationStatus
	}

	// Parse table name
	if tableName := query.Get("table"); tableName != "" {
		filters.TableName = &tableName
	}

	// Parse min savings
	if minSavings := query.Get("min_savings"); minSavings != "" {
		if parsed, err := strconv.ParseFloat(minSavings, 64); err == nil {
			filters.MinSavings = &parsed
		}
	}

	// Parse max risk
	if maxRisk := query.Get("max_risk"); maxRisk != "" {
		riskLevel := retention.RiskLevel(maxRisk)
		filters.MaxRisk = &riskLevel
	}

	// Parse created since
	if since := query.Get("since"); since != "" {
		if parsed, err := time.Parse(time.RFC3339, since); err == nil {
			filters.CreatedSince = &parsed
		}
	}

	// Parse created before
	if before := query.Get("before"); before != "" {
		if parsed, err := time.Parse(time.RFC3339, before); err == nil {
			filters.CreatedBefore = &parsed
		}
	}

	// Parse limit
	if l := query.Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			filters.Limit = parsed
		}
	}

	// Parse offset
	if o := query.Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil && parsed >= 0 {
			filters.Offset = parsed
		}
	}

	return filters
}
