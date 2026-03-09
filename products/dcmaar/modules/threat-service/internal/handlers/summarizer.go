package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/gorilla/mux"
	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/summarizer"
)

// SummarizerHandler handles HTTP requests for incident summarization
type SummarizerHandler struct {
	logger        *zap.Logger
	summarizerSvc *summarizer.SummarizerService
}

// NewSummarizerHandler creates a new summarizer handler
func NewSummarizerHandler(logger *zap.Logger, summarizerSvc *summarizer.SummarizerService) *SummarizerHandler {
	return &SummarizerHandler{
		logger:        logger.Named("summarizer_handler"),
		summarizerSvc: summarizerSvc,
	}
}

// RegisterRoutes registers all summarizer-related routes
func (h *SummarizerHandler) RegisterRoutes(router *mux.Router) {
	// Get summary for a specific incident
	router.HandleFunc("/incidents/{id}/summary", h.GetIncidentSummary).Methods("GET")

	// Generate new summary for an incident
	router.HandleFunc("/incidents/{id}/summary", h.GenerateIncidentSummary).Methods("POST")

	// Get summaries within a time range
	router.HandleFunc("/summaries", h.GetSummariesByTimeRange).Methods("GET")

	// Trigger auto-summarization of recent incidents
	router.HandleFunc("/summaries/auto-generate", h.TriggerAutoSummarization).Methods("POST")
}

// GetIncidentSummary retrieves an existing summary for an incident
func (h *SummarizerHandler) GetIncidentSummary(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	incidentID := vars["id"]

	if incidentID == "" {
		h.writeErrorResponse(w, http.StatusBadRequest, "incident ID is required")
		return
	}

	h.logger.Info("Getting incident summary",
		zap.String("incident_id", incidentID))

	summary, err := h.summarizerSvc.GetStoredSummary(r.Context(), incidentID)
	if err != nil {
		h.logger.Error("Failed to get incident summary",
			zap.String("incident_id", incidentID),
			zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, "failed to retrieve summary")
		return
	}

	if summary == nil {
		h.writeErrorResponse(w, http.StatusNotFound, "summary not found for incident")
		return
	}

	h.writeJSONResponse(w, http.StatusOK, summary)
}

// GenerateIncidentSummary creates a new summary for an incident
func (h *SummarizerHandler) GenerateIncidentSummary(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	incidentID := vars["id"]

	if incidentID == "" {
		h.writeErrorResponse(w, http.StatusBadRequest, "incident ID is required")
		return
	}

	// Parse optional configuration from request body
	config := summarizer.DefaultSummarizerConfig()
	if r.Body != nil {
		var reqConfig summarizer.SummarizerConfig
		if err := json.NewDecoder(r.Body).Decode(&reqConfig); err == nil {
			// Override defaults with provided config
			if reqConfig.MaxTokens > 0 {
				config.MaxTokens = reqConfig.MaxTokens
			}
			if reqConfig.Temperature > 0 {
				config.Temperature = reqConfig.Temperature
			}
			if reqConfig.MaxSummaryWords > 0 {
				config.MaxSummaryWords = reqConfig.MaxSummaryWords
			}
			config.IncludeTimeline = reqConfig.IncludeTimeline
			if reqConfig.TimelineEvents > 0 {
				config.TimelineEvents = reqConfig.TimelineEvents
			}
		}
	}

	h.logger.Info("Generating incident summary",
		zap.String("incident_id", incidentID),
		zap.Int("max_tokens", config.MaxTokens),
		zap.Float64("temperature", config.Temperature))

	summary, err := h.summarizerSvc.SummarizeIncident(r.Context(), incidentID, config)
	if err != nil {
		h.logger.Error("Failed to generate incident summary",
			zap.String("incident_id", incidentID),
			zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf("failed to generate summary: %v", err))
		return
	}

	h.writeJSONResponse(w, http.StatusCreated, summary)
}

// GetSummariesByTimeRange retrieves summaries within a specified time range
func (h *SummarizerHandler) GetSummariesByTimeRange(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	startTimeStr := r.URL.Query().Get("start_time")
	endTimeStr := r.URL.Query().Get("end_time")
	limitStr := r.URL.Query().Get("limit")

	// Default time range: last 24 hours
	endTime := time.Now()
	startTime := endTime.Add(-24 * time.Hour)

	if startTimeStr != "" {
		if parsed, err := time.Parse(time.RFC3339, startTimeStr); err == nil {
			startTime = parsed
		} else {
			h.writeErrorResponse(w, http.StatusBadRequest, "invalid start_time format, use RFC3339")
			return
		}
	}

	if endTimeStr != "" {
		if parsed, err := time.Parse(time.RFC3339, endTimeStr); err == nil {
			endTime = parsed
		} else {
			h.writeErrorResponse(w, http.StatusBadRequest, "invalid end_time format, use RFC3339")
			return
		}
	}

	// Default limit: 50 summaries
	limit := 50
	if limitStr != "" {
		if parsed, err := strconv.Atoi(limitStr); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	h.logger.Info("Getting summaries by time range",
		zap.Time("start_time", startTime),
		zap.Time("end_time", endTime),
		zap.Int("limit", limit))

	summaries, err := h.summarizerSvc.GetSummariesByTimeRange(r.Context(), startTime, endTime, limit)
	if err != nil {
		h.logger.Error("Failed to get summaries by time range", zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, "failed to retrieve summaries")
		return
	}

	response := map[string]interface{}{
		"summaries":  summaries,
		"count":      len(summaries),
		"start_time": startTime,
		"end_time":   endTime,
		"limit":      limit,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// TriggerAutoSummarization manually triggers auto-summarization of recent incidents
func (h *SummarizerHandler) TriggerAutoSummarization(w http.ResponseWriter, r *http.Request) {
	// Parse optional configuration
	config := summarizer.DefaultSummarizerConfig()
	if r.Body != nil {
		var reqConfig summarizer.SummarizerConfig
		if err := json.NewDecoder(r.Body).Decode(&reqConfig); err == nil {
			config = reqConfig
		}
	}

	h.logger.Info("Triggering auto-summarization")

	err := h.summarizerSvc.AutoSummarizeNewIncidents(r.Context(), config)
	if err != nil {
		h.logger.Error("Auto-summarization failed", zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf("auto-summarization failed: %v", err))
		return
	}

	response := map[string]interface{}{
		"message":      "Auto-summarization completed successfully",
		"status":       "success",
		"triggered_at": time.Now(),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// writeJSONResponse writes a JSON response with the given status code
func (h *SummarizerHandler) writeJSONResponse(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)

	if err := json.NewEncoder(w).Encode(data); err != nil {
		h.logger.Error("Failed to encode JSON response", zap.Error(err))
	}
}

// writeErrorResponse writes a JSON error response
func (h *SummarizerHandler) writeErrorResponse(w http.ResponseWriter, statusCode int, message string) {
	response := map[string]interface{}{
		"error":     message,
		"status":    statusCode,
		"timestamp": time.Now(),
	}

	h.writeJSONResponse(w, statusCode, response)
}
