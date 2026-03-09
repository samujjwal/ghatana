package nlq

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"
)

// Handler provides HTTP endpoints for the NLQ service
type Handler struct {
	service Service
}

// NewHandler creates a new HTTP handler for NLQ endpoints
func NewHandler(service Service) *Handler {
	return &Handler{
		service: service,
	}
}

// RegisterRoutes registers NLQ endpoints with the HTTP router
func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("/api/nlq/query", h.handleQuery)
	mux.HandleFunc("/api/nlq/suggestions", h.handleSuggestions)
	mux.HandleFunc("/api/nlq/preview", h.handlePreview)
	mux.HandleFunc("/api/nlq/history", h.handleHistory)
	mux.HandleFunc("/api/nlq/metadata", h.handleMetadata)
	mux.HandleFunc("/api/nlq/examples", h.handleExamples)
}

// QueryRequest represents the JSON request for executing a natural language query
type QueryRequest struct {
	Query  string `json:"query"`
	UserID string `json:"user_id"`
}

// QueryResponse represents the JSON response for query execution
type QueryResponse struct {
	Success bool         `json:"success"`
	Result  *QueryResult `json:"result,omitempty"`
	Error   string       `json:"error,omitempty"`
}

// SuggestionsRequest represents the JSON request for query suggestions
type SuggestionsRequest struct {
	Partial string `json:"partial"`
}

// SuggestionsResponse represents the JSON response for query suggestions
type SuggestionsResponse struct {
	Success     bool     `json:"success"`
	Suggestions []string `json:"suggestions,omitempty"`
	Error       string   `json:"error,omitempty"`
}

// PreviewRequest represents the JSON request for query preview
type PreviewRequest struct {
	Query  string `json:"query"`
	UserID string `json:"user_id"`
	Limit  int    `json:"limit,omitempty"`
}

// HistoryResponse represents the JSON response for query history
type HistoryResponse struct {
	Success bool           `json:"success"`
	History []*QueryResult `json:"history,omitempty"`
	Error   string         `json:"error,omitempty"`
}

// MetadataResponse represents the JSON response for database metadata
type MetadataResponse struct {
	Success  bool           `json:"success"`
	Metadata *QueryMetadata `json:"metadata,omitempty"`
	Error    string         `json:"error,omitempty"`
}

// ExamplesResponse represents the JSON response for example queries
type ExamplesResponse struct {
	Success  bool     `json:"success"`
	Examples []string `json:"examples,omitempty"`
	Error    string   `json:"error,omitempty"`
}

// handleQuery processes natural language queries and returns results
func (h *Handler) handleQuery(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Parse request
	var req QueryRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.sendError(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate required fields
	if req.Query == "" {
		h.sendError(w, "Query is required", http.StatusBadRequest)
		return
	}
	if req.UserID == "" {
		h.sendError(w, "User ID is required", http.StatusBadRequest)
		return
	}

	// Create context with timeout
	ctx, cancel := context.WithTimeout(r.Context(), 60*time.Second)
	defer cancel()

	// Process query
	result, err := h.service.ProcessQuery(ctx, req.Query, req.UserID)
	if err != nil {
		h.sendQueryResponse(w, false, nil, err.Error())
		return
	}

	h.sendQueryResponse(w, true, result, "")
}

// handleSuggestions provides query suggestions based on partial input
func (h *Handler) handleSuggestions(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Parse request
	var req SuggestionsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.sendError(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Get suggestions
	suggestions, err := h.service.GetSuggestions(r.Context(), req.Partial)
	if err != nil {
		h.sendSuggestionsResponse(w, false, nil, err.Error())
		return
	}

	h.sendSuggestionsResponse(w, true, suggestions, "")
}

// handlePreview provides a limited preview of query results
func (h *Handler) handlePreview(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Parse request
	var req PreviewRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.sendError(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	// Validate required fields
	if req.Query == "" {
		h.sendError(w, "Query is required", http.StatusBadRequest)
		return
	}
	if req.UserID == "" {
		h.sendError(w, "User ID is required", http.StatusBadRequest)
		return
	}

	// Set default limit if not specified
	if req.Limit == 0 {
		req.Limit = 10
	}

	// Create context with timeout
	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	// Parse query to get intent
	intent, err := h.service.Parse(ctx, req.Query)
	if err != nil {
		h.sendQueryResponse(w, false, nil, fmt.Sprintf("Parse error: %v", err))
		return
	}

	// Generate SQL
	sql, err := h.service.GenerateSQL(ctx, intent)
	if err != nil {
		h.sendQueryResponse(w, false, nil, fmt.Sprintf("SQL generation error: %v", err))
		return
	}

	// Validate SQL
	if err := h.service.ValidateSQL(ctx, sql); err != nil {
		h.sendQueryResponse(w, false, nil, fmt.Sprintf("SQL validation error: %v", err))
		return
	}

	// Run preview with limit
	result, err := h.service.Preview(ctx, sql, req.Limit)
	if err != nil {
		h.sendQueryResponse(w, false, nil, err.Error())
		return
	}

	// Add metadata to result
	result.UserID = req.UserID
	result.Intent = *intent

	h.sendQueryResponse(w, true, result, "")
}

// handleHistory returns query history for a user
func (h *Handler) handleHistory(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Get parameters
	userID := r.URL.Query().Get("user_id")
	if userID == "" {
		h.sendError(w, "User ID is required", http.StatusBadRequest)
		return
	}

	limitStr := r.URL.Query().Get("limit")
	limit := 50 // Default limit
	if limitStr != "" {
		if parsed, err := strconv.Atoi(limitStr); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	// Get history
	history, err := h.service.GetQueryHistory(r.Context(), userID, limit)
	if err != nil {
		h.sendHistoryResponse(w, false, nil, err.Error())
		return
	}

	h.sendHistoryResponse(w, true, history, "")
}

// handleMetadata returns database schema metadata
func (h *Handler) handleMetadata(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Get metadata
	metadata, err := h.service.GetMetadata(r.Context())
	if err != nil {
		h.sendMetadataResponse(w, false, nil, err.Error())
		return
	}

	h.sendMetadataResponse(w, true, metadata, "")
}

// handleExamples returns example queries for demonstration
func (h *Handler) handleExamples(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	h.sendExamplesResponse(w, true, CannedQueries, "")
}

// Helper methods for sending JSON responses
func (h *Handler) sendQueryResponse(w http.ResponseWriter, success bool, result *QueryResult, errorMsg string) {
	response := QueryResponse{
		Success: success,
		Result:  result,
		Error:   errorMsg,
	}
	h.sendJSON(w, response)
}

func (h *Handler) sendSuggestionsResponse(w http.ResponseWriter, success bool, suggestions []string, errorMsg string) {
	response := SuggestionsResponse{
		Success:     success,
		Suggestions: suggestions,
		Error:       errorMsg,
	}
	h.sendJSON(w, response)
}

func (h *Handler) sendHistoryResponse(w http.ResponseWriter, success bool, history []*QueryResult, errorMsg string) {
	response := HistoryResponse{
		Success: success,
		History: history,
		Error:   errorMsg,
	}
	h.sendJSON(w, response)
}

func (h *Handler) sendMetadataResponse(w http.ResponseWriter, success bool, metadata *QueryMetadata, errorMsg string) {
	response := MetadataResponse{
		Success:  success,
		Metadata: metadata,
		Error:    errorMsg,
	}
	h.sendJSON(w, response)
}

func (h *Handler) sendExamplesResponse(w http.ResponseWriter, success bool, examples []string, errorMsg string) {
	response := ExamplesResponse{
		Success:  success,
		Examples: examples,
		Error:    errorMsg,
	}
	h.sendJSON(w, response)
}

func (h *Handler) sendJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(data); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
	}
}

func (h *Handler) sendError(w http.ResponseWriter, message string, statusCode int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	json.NewEncoder(w).Encode(map[string]string{
		"error": message,
	})
}
