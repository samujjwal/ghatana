package playbook

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/gorilla/mux"
	"go.uber.org/zap"
)

// PlaybookHandler handles HTTP requests for playbook operations
type PlaybookHandler struct {
	logger         *zap.Logger
	generator      *PlaybookGenerator
	storage        PlaybookStorage
	templateEngine *SimpleTemplateEngine
}

// NewPlaybookHandler creates a new playbook handler
func NewPlaybookHandler(logger *zap.Logger, generator *PlaybookGenerator, storage PlaybookStorage, templateEngine *SimpleTemplateEngine) *PlaybookHandler {
	return &PlaybookHandler{
		logger:         logger,
		generator:      generator,
		storage:        storage,
		templateEngine: templateEngine,
	}
}

// RegisterRoutes registers all playbook routes
func (h *PlaybookHandler) RegisterRoutes(router *mux.Router) {
	// Playbook CRUD operations
	router.HandleFunc("/playbooks", h.CreatePlaybook).Methods("POST")
	router.HandleFunc("/playbooks", h.ListPlaybooks).Methods("GET")
	router.HandleFunc("/playbooks/{playbook_id}", h.GetPlaybook).Methods("GET")
	router.HandleFunc("/playbooks/{playbook_id}", h.UpdatePlaybook).Methods("PUT")
	router.HandleFunc("/playbooks/{playbook_id}", h.DeletePlaybook).Methods("DELETE")

	// Playbook generation
	router.HandleFunc("/playbooks/generate", h.GeneratePlaybook).Methods("POST")
	router.HandleFunc("/playbooks/generate-from-template", h.GenerateFromTemplate).Methods("POST")

	// Playbook execution
	router.HandleFunc("/playbooks/{playbook_id}/execute", h.ExecutePlaybook).Methods("POST")
	router.HandleFunc("/playbooks/{playbook_id}/validate", h.ValidatePlaybook).Methods("POST")
	router.HandleFunc("/playbooks/{playbook_id}/rollback", h.RollbackPlaybook).Methods("POST")

	// Playbook approval workflow
	router.HandleFunc("/playbooks/{playbook_id}/approve", h.ApprovePlaybook).Methods("POST")
	router.HandleFunc("/playbooks/{playbook_id}/reject", h.RejectPlaybook).Methods("POST")

	// Analytics and metrics
	router.HandleFunc("/playbooks/metrics", h.GetPlaybookMetrics).Methods("GET")
	router.HandleFunc("/playbooks/{playbook_id}/history", h.GetExecutionHistory).Methods("GET")

	// Template management
	router.HandleFunc("/playbook-templates", h.ListTemplates).Methods("GET")
	router.HandleFunc("/playbook-templates/{template_id}", h.GetTemplate).Methods("GET")
	router.HandleFunc("/playbook-templates/match", h.FindMatchingTemplates).Methods("POST")
}

// Request/Response types

type GenerateFromTemplateRequest struct {
	TemplateID string                 `json:"template_id"`
	Incident   SimpleIncidentContext  `json:"incident"`
	Variables  map[string]interface{} `json:"variables"`
}

type ExecutePlaybookRequest struct {
	PlaybookID  string                 `json:"playbook_id"`
	ExecutedBy  string                 `json:"executed_by"`
	Environment string                 `json:"environment"`
	Parameters  map[string]interface{} `json:"parameters"`
	DryRun      bool                   `json:"dry_run"`
}

type ApprovalRequest struct {
	PlaybookID string `json:"playbook_id"`
	ApprovedBy string `json:"approved_by"`
	Comments   string `json:"comments"`
	Approved   bool   `json:"approved"`
}

type PlaybookListResponse struct {
	Playbooks []Playbook `json:"playbooks"`
	Total     int        `json:"total"`
	Page      int        `json:"page"`
	PageSize  int        `json:"page_size"`
}

type TemplateMatchRequest struct {
	Incident SimpleIncidentContext `json:"incident"`
}

// HTTP Handlers

// CreatePlaybook creates a new playbook manually
func (h *PlaybookHandler) CreatePlaybook(w http.ResponseWriter, r *http.Request) {
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

// GeneratePlaybook generates a new playbook using AI
func (h *PlaybookHandler) GeneratePlaybook(w http.ResponseWriter, r *http.Request) {
	var req GeneratePlaybookRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// Validate required fields
	if req.IncidentID == "" || req.IncidentSummary == "" {
		h.writeError(w, http.StatusBadRequest, "incident_id and incident_summary are required", nil)
		return
	}

	// Create generation request
	genRequest := &GeneratePlaybookRequest{
		IncidentID:      req.IncidentID,
		IncidentSummary: req.IncidentSummary,
		Severity:        req.Severity,
		Category:        req.Category,
		AffectedSystems: req.AffectedSystems,
		Context:         req.Context,
		TenantID:        req.TenantID,
	}

	// Generate playbook
	playbook, err := h.generator.GeneratePlaybook(r.Context(), genRequest)
	if err != nil {
		h.logger.Error("Failed to generate playbook", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to generate playbook", err)
		return
	}

	// Store generated playbook
	if err := h.storage.StorePlaybook(r.Context(), playbook); err != nil {
		h.logger.Error("Failed to store generated playbook", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to store playbook", err)
		return
	}

	h.logger.Info("Generated playbook",
		zap.String("playbook_id", playbook.PlaybookID),
		zap.String("incident_id", req.IncidentID))

	h.writeJSON(w, http.StatusCreated, playbook)
}

// GenerateFromTemplate generates a playbook from a template
func (h *PlaybookHandler) GenerateFromTemplate(w http.ResponseWriter, r *http.Request) {
	var req GenerateFromTemplateRequest
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

// GetPlaybook retrieves a specific playbook
func (h *PlaybookHandler) GetPlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	if playbookID == "" {
		h.writeError(w, http.StatusBadRequest, "playbook_id is required", nil)
		return
	}

	playbook, err := h.storage.GetPlaybook(r.Context(), playbookID)
	if err != nil {
		h.logger.Error("Failed to get playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusNotFound, "Playbook not found", err)
		return
	}

	h.writeJSON(w, http.StatusOK, playbook)
}

// ListPlaybooks lists playbooks with optional filtering
func (h *PlaybookHandler) ListPlaybooks(w http.ResponseWriter, r *http.Request) {
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

	response := PlaybookListResponse{
		Playbooks: playbooks,
		Total:     len(playbooks), // In a real implementation, get actual total count
		Page:      page,
		PageSize:  pageSize,
	}

	h.writeJSON(w, http.StatusOK, response)
}

// UpdatePlaybook updates an existing playbook
func (h *PlaybookHandler) UpdatePlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	if playbookID == "" {
		h.writeError(w, http.StatusBadRequest, "playbook_id is required", nil)
		return
	}

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

// DeletePlaybook deletes a playbook
func (h *PlaybookHandler) DeletePlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	if playbookID == "" {
		h.writeError(w, http.StatusBadRequest, "playbook_id is required", nil)
		return
	}

	if err := h.storage.DeletePlaybook(r.Context(), playbookID); err != nil {
		h.logger.Error("Failed to delete playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to delete playbook", err)
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]string{"message": "Playbook deleted successfully"})
}

// ExecutePlaybook executes a playbook
func (h *PlaybookHandler) ExecutePlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	var req ExecutePlaybookRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	// For now, just return a placeholder response
	// In a real implementation, this would trigger actual execution
	response := map[string]interface{}{
		"execution_id": fmt.Sprintf("exec-%d", time.Now().UnixNano()),
		"playbook_id":  playbookID,
		"status":       "started",
		"started_at":   time.Now(),
		"dry_run":      req.DryRun,
	}

	h.logger.Info("Started playbook execution",
		zap.String("playbook_id", playbookID),
		zap.String("executed_by", req.ExecutedBy),
		zap.Bool("dry_run", req.DryRun))

	h.writeJSON(w, http.StatusAccepted, response)
}

// ValidatePlaybook validates a playbook
func (h *PlaybookHandler) ValidatePlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	playbook, err := h.storage.GetPlaybook(r.Context(), playbookID)
	if err != nil {
		h.writeError(w, http.StatusNotFound, "Playbook not found", err)
		return
	}

	// Use validator to validate the playbook
	validationResult, err := h.generator.validator.ValidatePlaybook(r.Context(), playbook)
	if err != nil {
		h.logger.Error("Failed to validate playbook", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to validate playbook", err)
		return
	}

	h.writeJSON(w, http.StatusOK, validationResult)
}

// ApprovePlaybook approves a playbook
func (h *PlaybookHandler) ApprovePlaybook(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	var req ApprovalRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "Invalid request body", err)
		return
	}

	playbook, err := h.storage.GetPlaybook(r.Context(), playbookID)
	if err != nil {
		h.writeError(w, http.StatusNotFound, "Playbook not found", err)
		return
	}

	// Update approval status
	if req.Approved {
		playbook.Status = StatusApproved
		playbook.ApprovedBy = req.ApprovedBy
		now := time.Now()
		playbook.ApprovedAt = &now
	} else {
		playbook.Status = StatusReview // Rejected, back to review
	}

	playbook.UpdatedAt = time.Now()

	if err := h.storage.UpdatePlaybook(r.Context(), playbook); err != nil {
		h.logger.Error("Failed to update playbook approval", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to update playbook", err)
		return
	}

	h.logger.Info("Playbook approval updated",
		zap.String("playbook_id", playbookID),
		zap.String("approved_by", req.ApprovedBy),
		zap.Bool("approved", req.Approved))

	h.writeJSON(w, http.StatusOK, playbook)
}

// RejectPlaybook rejects a playbook
func (h *PlaybookHandler) RejectPlaybook(w http.ResponseWriter, r *http.Request) {
	// Implement rejection logic similar to approval
	req := ApprovalRequest{Approved: false}
	json.NewDecoder(r.Body).Decode(&req)

	h.ApprovePlaybook(w, r)
}

// GetPlaybookMetrics returns analytics metrics
func (h *PlaybookHandler) GetPlaybookMetrics(w http.ResponseWriter, r *http.Request) {
	// Parse time range from query parameters
	timeRange := TimeRange{
		Start: time.Now().AddDate(0, -1, 0), // Last month
		End:   time.Now(),
	}

	metrics, err := h.storage.GetPlaybookMetrics(r.Context(), timeRange)
	if err != nil {
		h.logger.Error("Failed to get playbook metrics", zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to get metrics", err)
		return
	}

	h.writeJSON(w, http.StatusOK, metrics)
}

// GetExecutionHistory returns execution history for a playbook
func (h *PlaybookHandler) GetExecutionHistory(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	playbookID := vars["playbook_id"]

	history, err := h.storage.GetExecutionHistory(r.Context(), playbookID)
	if err != nil {
		h.logger.Error("Failed to get execution history", zap.String("playbook_id", playbookID), zap.Error(err))
		h.writeError(w, http.StatusInternalServerError, "Failed to get history", err)
		return
	}

	h.writeJSON(w, http.StatusOK, map[string]interface{}{
		"playbook_id": playbookID,
		"history":     history,
	})
}

// ListTemplates lists available templates
func (h *PlaybookHandler) ListTemplates(w http.ResponseWriter, r *http.Request) {
	// For now, return empty list - in real implementation, get from template engine
	templates := []PlaybookTemplate{}

	h.writeJSON(w, http.StatusOK, map[string]interface{}{
		"templates": templates,
		"total":     len(templates),
	})
}

// GetTemplate gets a specific template
func (h *PlaybookHandler) GetTemplate(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	templateID := vars["template_id"]

	// Placeholder implementation
	h.writeError(w, http.StatusNotFound, "Template not found", nil)
}

// FindMatchingTemplates finds templates that match an incident
func (h *PlaybookHandler) FindMatchingTemplates(w http.ResponseWriter, r *http.Request) {
	var req TemplateMatchRequest
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

// Helper methods

func (h *PlaybookHandler) writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func (h *PlaybookHandler) writeError(w http.ResponseWriter, status int, message string, err error) {
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

func parseStatusFilter(statusStrings []string) []PlaybookStatus {
	var statuses []PlaybookStatus
	for _, s := range statusStrings {
		statuses = append(statuses, PlaybookStatus(s))
	}
	return statuses
}
