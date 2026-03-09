package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/samujjwal/dcmaar/apps/server/internal/miner"
)

// MinerHandler handles HTTP requests for the toil mining service
type MinerHandler struct {
	service *miner.ToilMinerService
}

// NewMinerHandler creates a new miner handler
func NewMinerHandler(service *miner.ToilMinerService) *MinerHandler {
	return &MinerHandler{
		service: service,
	}
}

// RegisterRoutes registers all miner routes
func (h *MinerHandler) RegisterRoutes(mux *http.ServeMux) {
	// Action logging
	mux.HandleFunc("POST /api/v1/miner/actions", h.LogAction)
	mux.HandleFunc("GET /api/v1/miner/actions", h.GetActions)

	// Mining operations
	mux.HandleFunc("POST /api/v1/miner/analyze", h.RunMining)
	mux.HandleFunc("GET /api/v1/miner/results", h.GetMiningResults)

	// Automation candidates
	mux.HandleFunc("GET /api/v1/miner/candidates", h.GetCandidates)
	mux.HandleFunc("POST /api/v1/miner/candidates/review", h.ReviewCandidate)

	// Statistics and insights
	mux.HandleFunc("GET /api/v1/miner/statistics", h.GetStatistics)
}

// LogAction handles POST /api/v1/miner/actions
func (h *MinerHandler) LogAction(w http.ResponseWriter, r *http.Request) {
	var actionLog miner.ActionLog
	if err := json.NewDecoder(r.Body).Decode(&actionLog); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if err := h.service.LogAction(r.Context(), &actionLog); err != nil {
		http.Error(w, fmt.Sprintf("Failed to log action: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"success": true,
		"id":      actionLog.ID,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// GetActions handles GET /api/v1/miner/actions
func (h *MinerHandler) GetActions(w http.ResponseWriter, r *http.Request) {
	_ = h.parseActionFilters(r) // Parse filters but don't use yet

	// For now, return empty results since we need to add GetActionLogs to service interface
	response := map[string]interface{}{
		"actions": []interface{}{},
		"count":   0,
		"message": "Action logs retrieval not yet implemented",
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// RunMining handles POST /api/v1/miner/analyze
func (h *MinerHandler) RunMining(w http.ResponseWriter, r *http.Request) {
	var config miner.MiningConfig
	if err := json.NewDecoder(r.Body).Decode(&config); err != nil {
		// Use default config if no body provided
		config = miner.MiningConfig{
			LookbackPeriod:     30 * 24 * time.Hour,
			MinFrequency:       3,
			MinTimeSaved:       5 * time.Minute,
			MaxComplexity:      0.8,
			MaxRisk:            0.7,
			IncludeTeams:       []string{},
			ExcludeActionTypes: []string{},
			ScoreWeights: miner.ScoreWeights{
				Frequency:      0.3,
				TimeSaved:      0.3,
				SuccessRate:    0.2,
				BusinessImpact: 0.1,
				Complexity:     0.05,
				Risk:           0.05,
			},
		}
	}

	result, err := h.service.RunMining(r.Context(), config)
	if err != nil {
		http.Error(w, fmt.Sprintf("Mining failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// GetMiningResults handles GET /api/v1/miner/results
func (h *MinerHandler) GetMiningResults(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters for pagination and filtering
	limit := 50 // default
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	// For now, return empty results - this would need repository method
	response := map[string]interface{}{
		"results": []interface{}{},
		"count":   0,
		"limit":   limit,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// GetCandidates handles GET /api/v1/miner/candidates
func (h *MinerHandler) GetCandidates(w http.ResponseWriter, r *http.Request) {
	filters := h.parseCandidateFilters(r)

	candidates, err := h.service.GetCandidates(r.Context(), filters)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get candidates: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"candidates": candidates,
		"count":      len(candidates),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// ReviewCandidate handles POST /api/v1/miner/candidates/review
func (h *MinerHandler) ReviewCandidate(w http.ResponseWriter, r *http.Request) {
	// Get candidate ID from query parameter instead of path variable
	candidateID := r.URL.Query().Get("id")

	if candidateID == "" {
		http.Error(w, "Candidate ID is required", http.StatusBadRequest)
		return
	}

	var reviewRequest struct {
		Status     miner.CandidateStatus `json:"status"`
		ReviewedBy string                `json:"reviewed_by"`
		Comments   string                `json:"comments,omitempty"`
	}

	if err := json.NewDecoder(r.Body).Decode(&reviewRequest); err != nil {
		http.Error(w, fmt.Sprintf("Invalid JSON: %v", err), http.StatusBadRequest)
		return
	}

	if reviewRequest.ReviewedBy == "" {
		http.Error(w, "reviewed_by is required", http.StatusBadRequest)
		return
	}

	if err := h.service.ReviewCandidate(r.Context(), candidateID, reviewRequest.Status, reviewRequest.ReviewedBy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to review candidate: %v", err), http.StatusInternalServerError)
		return
	}

	response := map[string]interface{}{
		"success":      true,
		"candidate_id": candidateID,
		"status":       reviewRequest.Status,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// GetStatistics handles GET /api/v1/miner/statistics
func (h *MinerHandler) GetStatistics(w http.ResponseWriter, r *http.Request) {
	// Parse time range
	since := time.Now().Add(-30 * 24 * time.Hour) // Default 30 days
	if s := r.URL.Query().Get("since"); s != "" {
		if parsed, err := time.Parse(time.RFC3339, s); err == nil {
			since = parsed
		}
	}

	stats, err := h.service.GetStatistics(r.Context(), since)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get statistics: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

// Helper methods for parsing query parameters

func (h *MinerHandler) parseActionFilters(r *http.Request) miner.ActionLogFilters {
	query := r.URL.Query()
	filters := miner.ActionLogFilters{
		Limit: 100, // default
	}

	// Parse since timestamp
	if s := query.Get("since"); s != "" {
		if parsed, err := time.Parse(time.RFC3339, s); err == nil {
			filters.Since = &parsed
		}
	}

	// Parse until timestamp
	if u := query.Get("until"); u != "" {
		if parsed, err := time.Parse(time.RFC3339, u); err == nil {
			filters.Until = &parsed
		}
	}

	// Parse user ID
	if userID := query.Get("user_id"); userID != "" {
		filters.UserID = userID
	}

	// Parse action type
	if actionType := query.Get("action_type"); actionType != "" {
		filters.ActionType = actionType
	}

	// Parse limit
	if l := query.Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			filters.Limit = parsed
		}
	}

	// Note: ActionLogFilters doesn't have Offset field

	return filters
}

func (h *MinerHandler) parseCandidateFilters(r *http.Request) miner.CandidateFilters {
	query := r.URL.Query()
	filters := miner.CandidateFilters{
		Limit: 50, // default
	}

	// Parse since timestamp
	if s := query.Get("since"); s != "" {
		if parsed, err := time.Parse(time.RFC3339, s); err == nil {
			filters.Since = &parsed
		}
	}

	// Parse until timestamp
	if u := query.Get("until"); u != "" {
		if parsed, err := time.Parse(time.RFC3339, u); err == nil {
			filters.Until = &parsed
		}
	}

	// Parse status
	if status := query.Get("status"); status != "" {
		candidateStatus := miner.CandidateStatus(status)
		filters.Status = candidateStatus
	}

	// Note: CandidateFilters doesn't have Team field

	// Parse min score
	if minScore := query.Get("min_score"); minScore != "" {
		if parsed, err := strconv.ParseFloat(minScore, 64); err == nil {
			filters.MinScore = parsed
		}
	}

	// Parse limit
	if l := query.Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			filters.Limit = parsed
		}
	}

	// Note: CandidateFilters doesn't have Offset field

	return filters
}
