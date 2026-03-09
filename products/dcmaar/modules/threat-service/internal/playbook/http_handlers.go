package playbook

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"go.uber.org/zap"
)

// PlaybookHTTPHandler handles HTTP requests for playbook operations using standard library
type PlaybookHTTPHandler struct {
	logger         *zap.Logger
	generator      *PlaybookGenerator
	storage        PlaybookStorage
	templateEngine *SimpleTemplateEngine
}

// NewPlaybookHTTPHandler creates a new playbook HTTP handler
func NewPlaybookHTTPHandler(logger *zap.Logger, generator *PlaybookGenerator, storage PlaybookStorage, templateEngine *SimpleTemplateEngine) *PlaybookHTTPHandler {
	return &PlaybookHTTPHandler{
		logger:         logger,
		generator:      generator,
		storage:        storage,
		templateEngine: templateEngine,
	}
}

// Custom request types for HTTP handlers
type HTTPGeneratePlaybookRequest struct {
	IncidentSummary string                 `json:"incident_summary"`
	IncidentID      string                 `json:"incident_id"`
	Severity        string                 `json:"severity"`
	Category        string                 `json:"category"`
	Environment     string                 `json:"environment"`
	AffectedSystems []string               `json:"affected_systems"`
	Context         map[string]interface{} `json:"context"`
	Template        string                 `json:"template,omitempty"`
	MaxSteps        int                    `json:"max_steps,omitempty"`
	IncludeRollback bool                   `json:"include_rollback"`
	RequireApproval bool                   `json:"require_approval"`
	Priority        string                 `json:"priority"`
	TenantID        string                 `json:"tenant_id"`
}

type HTTPGenerateFromTemplateRequest struct {
	TemplateID string                 `json:"template_id"`
	Incident   SimpleIncidentContext  `json:"incident"`
	Variables  map[string]interface{} `json:"variables"`
}

type HTTPExecutePlaybookRequest struct {
	PlaybookID  string                 `json:"playbook_id"`
	ExecutedBy  string                 `json:"executed_by"`
	Environment string                 `json:"environment"`
	Parameters  map[string]interface{} `json:"parameters"`
	DryRun      bool                   `json:"dry_run"`
}

type HTTPApprovalRequest struct {
	PlaybookID string `json:"playbook_id"`
	ApprovedBy string `json:"approved_by"`
	Comments   string `json:"comments"`
	Approved   bool   `json:"approved"`
}

type HTTPPlaybookListResponse struct {
	Playbooks []Playbook `json:"playbooks"`
	Total     int        `json:"total"`
	Page      int        `json:"page"`
	PageSize  int        `json:"page_size"`
}

type HTTPTemplateMatchRequest struct {
	Incident SimpleIncidentContext `json:"incident"`
}

// RegisterRoutes registers all playbook routes with the default HTTP mux
func (h *PlaybookHTTPHandler) RegisterRoutes() {
	http.HandleFunc("/api/v1/playbooks", h.handlePlaybooks)
	http.HandleFunc("/api/v1/playbooks/generate", h.handleGeneratePlaybook)
	http.HandleFunc("/api/v1/playbooks/generate-from-template", h.handleGenerateFromTemplate)
	http.HandleFunc("/api/v1/playbook-templates/match", h.handleFindMatchingTemplates)
	http.HandleFunc("/api/v1/playbooks/metrics", h.handlePlaybookMetrics)
}

// Main playbook handler that routes based on method and path
func (h *PlaybookHTTPHandler) handlePlaybooks(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodPost:
		h.createPlaybook(w, r)
	case http.MethodGet:
		// Check if it's a specific playbook or list
		if playbookID := h.extractPlaybookID(r.URL.Path); playbookID != "" {
			h.getPlaybook(w, r, playbookID)
		} else {
			h.listPlaybooks(w, r)
		}
	case http.MethodPut:
		if playbookID := h.extractPlaybookID(r.URL.Path); playbookID != "" {
			h.updatePlaybook(w, r, playbookID)
		} else {
			h.writeError(w, http.StatusBadRequest, "Playbook ID required for update", nil)
		}
	case http.MethodDelete:
		if playbookID := h.extractPlaybookID(r.URL.Path); playbookID != "" {
			h.deletePlaybook(w, r, playbookID)
		} else {
			h.writeError(w, http.StatusBadRequest, "Playbook ID required for delete", nil)
		}
	default:
		h.writeError(w, http.StatusMethodNotAllowed, "Method not allowed", nil)
	}
}

// Extract playbook ID from URL path like /api/v1/playbooks/{id}
func (h *PlaybookHTTPHandler) extractPlaybookID(path string) string {
	parts := strings.Split(path, "/")
	if len(parts) >= 5 && parts[4] != "" {
		return parts[4]
	}
	return ""
}

// createPlaybook creates a new playbook manually
func (h *PlaybookHTTPHandler) createPlaybook(w http.ResponseWriter, r *http.Request) {
	var playbook Playbook
	if err := json.NewDecoder(r.Body).Decode(&playbook); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Set metadata
	playbook.CreatedAt = time.Now()
	playbook.UpdatedAt = time.Now()
	playbook.Status = StatusDraft

	if err := h.storage.StorePlaybook(r.Context(), &playbook); err != nil {
		h.logger.Error("Failed to store playbook", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to create playbook", err)
		return
	}

	h.writeJSON(w, http.StatusCreated, playbook)
}

// handleGeneratePlaybook generates a new playbook using AI
func (h *PlaybookHTTPHandler) handleGeneratePlaybook(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		h.writeError(w, http.StatusMethodNotAllowed, "Method not allowed", nil)
		return
	}

	var req HTTPGeneratePlaybookRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Validate required fields
	if req.IncidentID == "" || req.IncidentSummary == "" {
		h.writeError(w, http.StatusBadRequest, "incident_id and incident_summary are required", nil)
		return
	}

	// Convert to generator request format
	genRequest := &GeneratePlaybookRequest{
		IncidentSummary: req.IncidentSummary,
		IncidentID:      req.IncidentID,
		Severity:        req.Severity,
		Category:        req.Category,
		AffectedSystems: req.AffectedSystems,
		Context:         req.Context,
		Template:        req.Template,
		MaxSteps:        req.MaxSteps,
		IncludeRollback: req.IncludeRollback,
		RequireApproval: req.RequireApproval,
		Priority:        req.Priority,
	}

	// Generate playbook
	response, err := h.generator.GeneratePlaybook(r.Context(), genRequest)
	if err != nil {
		h.logger.Error("Failed to generate playbook", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to generate playbook", err)
		return
	}

	// Store generated playbook if response contains one
	if response.Playbook != nil {
		if err := h.storage.StorePlaybook(r.Context(), response.Playbook); err != nil {
			h.logger.Error("Failed to store generated playbook", zap.Error(err))
			h.writeError(w, http.StatusInternalServerError, "Failed to store playbook", err)
			return
		}
	}

	h.logger.Info("Generated playbook",
		zap.String("incident_id", req.IncidentID))

	h.writeJSON(w, http.StatusCreated, response)
}

// handleGenerateFromTemplate generates a playbook from a template
func (h *PlaybookHTTPHandler) handleGenerateFromTemplate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		h.writeError(w, http.StatusMethodNotAllowed, "Method not allowed", nil)
		return
	}

	var req HTTPGenerateFromTemplateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Validate required fields
	if req.TemplateID == "" || req.Incident.IncidentID == "" {
		h.writeError(w, http.StatusBadRequest, "template_id and incident.incident_id are required", nil)
		return
	}

	// Generate playbook from template
	playbook, err := h.templateEngine.GenerateFromTemplate(r.Context(), req.TemplateID, &req.Incident, req.Variables)
	if err != nil {
		h.logger.Error("Failed to generate playbook from template", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to generate playbook from template", err)
		return
	}

	// Store generated playbook
	if err := h.storage.StorePlaybook(r.Context(), playbook); err != nil {
		h.logger.Error("Failed to store template-generated playbook", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to store playbook", err)
		return
	}

	h.logger.Info("Generated playbook from template",
		zap.String("playbook_id", playbook.PlaybookID),
		zap.String("template_id", req.TemplateID),
		zap.String("incident_id", req.Incident.IncidentID))

	h.writeJSON(w, http.StatusCreated, playbook)
}

// getPlaybook retrieves a specific playbook
func (h *PlaybookHTTPHandler) getPlaybook(w http.ResponseWriter, r *http.Request, playbookID string) {
	playbook, err := h.storage.GetPlaybook(r.Context(), playbookID)
	if err != nil {
		h.logger.Error("Failed to get playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusNotFound, "Playbook not found", err)
		return
	}

	h.writeJSON(w, http.StatusOK, playbook)
}

// listPlaybooks lists playbooks with optional filtering
func (h *PlaybookHTTPHandler) listPlaybooks(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	query := r.URL.Query()

	filters := PlaybookFilters{
		TenantID:    query.Get("tenant_id"),
		Environment: query.Get("environment"),
		Category:    query["category"],
		Status:      parseStatusFilter(query["status"]),
	}

	// Parse pagination
	page, _ := strconv.Atoi(query.Get("page"))
	if page < 1 {
		page = 1
	}

	pageSize, _ := strconv.Atoi(query.Get("page_size"))
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	filters.Limit = pageSize
	filters.Offset = (page - 1) * pageSize

	playbooks, err := h.storage.ListPlaybooks(r.Context(), filters)
	if err != nil {
		h.logger.Error("Failed to list playbooks", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to list playbooks", err)
		return
	}

	response := HTTPPlaybookListResponse{
		Playbooks: playbooks,
		Total:     len(playbooks), // In a real implementation, get actual total count
		Page:      page,
		PageSize:  pageSize,
	}

	h.writeJSON(w, http.StatusOK, response)
}

// updatePlaybook updates an existing playbook
func (h *PlaybookHTTPHandler) updatePlaybook(w http.ResponseWriter, r *http.Request, playbookID string) {
	var playbook Playbook
	if err := json.NewDecoder(r.Body).Decode(&playbook); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Ensure the ID matches
	playbook.PlaybookID = playbookID
	playbook.UpdatedAt = time.Now()

	if err := h.storage.UpdatePlaybook(r.Context(), &playbook); err != nil {
		h.logger.Error("Failed to update playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to update playbook", err)
		return
	}

	h.writeJSON(w, http.StatusOK, playbook)
}

// deletePlaybook deletes a playbook
func (h *PlaybookHTTPHandler) deletePlaybook(w http.ResponseWriter, r *http.Request, playbookID string) {
	if err := h.storage.DeletePlaybook(r.Context(), playbookID); err != nil {
		h.logger.Error("Failed to delete playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to delete playbook", err)
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]string{"message": "Playbook deleted successfully"})
}

// handleFindMatchingTemplates finds templates that match an incident
func (h *PlaybookHTTPHandler) handleFindMatchingTemplates(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		h.writeError(w, http.StatusMethodNotAllowed, "Method not allowed", nil)
		return
	}

	var req HTTPTemplateMatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	templates, err := h.templateEngine.FindMatchingTemplates(r.Context(), &req.Incident)
	if err != nil {
		h.logger.Error("Failed to find matching templates", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to find templates", err)
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]interface{}{
		"incident_id": req.Incident.IncidentID,
		"templates":   templates,
		"count":       len(templates),
	})
}

// handlePlaybookMetrics returns analytics metrics
func (h *PlaybookHTTPHandler) handlePlaybookMetrics(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		h.writeError(w, http.StatusMethodNotAllowed, "Method not allowed", nil)
		return
	}

	// Parse time range from query parameters
	timeRange := TimeRange{
		StartTime: time.Now().AddDate(0, -1, 0), // Last month
		EndTime:   time.Now(),
	}

	metrics, err := h.storage.GetPlaybookMetrics(r.Context(), timeRange)
	if err != nil {
		h.logger.Error("Failed to get playbook metrics", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to get metrics", err)
		return
	}

	h.writeJSON(w, http.StatusOK, metrics)
}

// Helper methods

func (h *PlaybookHTTPHandler) writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func (h *PlaybookHTTPHandler) writeError(w http.ResponseWriter, status int, message string, err error) {
	response := map[string]interface{}{
		"error":     message,
		"status":    status,
		"timestamp": time.Now(),
	}

	if err != nil {
		response["details"] = err.Error()
	}

	h.writeJSON(w, status, response)
}

// parseStatusFilter is defined in handlers.go to avoid duplication

// ExecutePlaybook executes a playbook (placeholder implementation)
func (h *PlaybookHTTPHandler) ExecutePlaybook(ctx context.Context, request *HTTPExecutePlaybookRequest) error {
	h.logger.Info("Executing playbook",
		zap.String("playbook_id", request.PlaybookID),
		zap.String("executed_by", request.ExecutedBy),
		zap.Bool("dry_run", request.DryRun))

	// Placeholder - in real implementation would trigger actual execution
	return nil
}

// ValidatePlaybook validates a playbook using the validator
func (h *PlaybookHTTPHandler) ValidatePlaybook(ctx context.Context, playbookID string) (*ValidationResult, error) {
	playbook, err := h.storage.GetPlaybook(ctx, playbookID)
	if err != nil {
		return nil, fmt.Errorf("failed to get playbook: %w", err)
	}

	return h.generator.validator.ValidatePlaybook(ctx, playbook)
}
