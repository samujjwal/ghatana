package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/policy"
)

// PolicySandboxHandler handles HTTP requests for policy simulation
type PolicySandboxHandler struct {
	logger  *zap.Logger
	sandbox *policy.PolicySandbox
}

// NewPolicySandboxHandler creates a new policy sandbox handler
func NewPolicySandboxHandler(logger *zap.Logger, sandbox *policy.PolicySandbox) *PolicySandboxHandler {
	return &PolicySandboxHandler{
		logger:  logger.Named("policy_sandbox_handler"),
		sandbox: sandbox,
	}
}

// CreateSimulationRequest represents the API request for creating a simulation
type CreateSimulationRequest struct {
	Name             string                   `json:"name"`
	Description      string                   `json:"description"`
	PolicyBundle     policy.PolicyBundle      `json:"policy_bundle"`
	TimeRange        policy.TimeRange         `json:"time_range"`
	EventFilters     *policy.EventFilters     `json:"event_filters,omitempty"`
	SimulationConfig *policy.SimulationConfig `json:"simulation_config,omitempty"`
}

// SimulationResponse represents the API response for simulation operations
type SimulationResponse struct {
	SimulationID string                   `json:"simulation_id"`
	Status       string                   `json:"status"`
	Message      string                   `json:"message"`
	Result       *policy.SimulationResult `json:"result,omitempty"`
	CreatedAt    time.Time                `json:"created_at"`
}

// ListSimulationsResponse represents the response for listing simulations
type ListSimulationsResponse struct {
	Simulations []SimulationSummary `json:"simulations"`
	Total       int                 `json:"total"`
	Page        int                 `json:"page"`
	PageSize    int                 `json:"page_size"`
}

// SimulationSummary provides a summary view of simulations
type SimulationSummary struct {
	SimulationID     string         `json:"simulation_id"`
	Name             string         `json:"name"`
	Status           string         `json:"status"`
	RequestedBy      string         `json:"requested_by"`
	CreatedAt        time.Time      `json:"created_at"`
	Duration         *time.Duration `json:"duration,omitempty"`
	EventsProcessed  int            `json:"events_processed"`
	IncidentDelta    int            `json:"incident_delta"`
	ImpactAssessment string         `json:"impact_assessment"`
}

// CreateSimulation starts a new policy simulation
func (h *PolicySandboxHandler) CreateSimulation(w http.ResponseWriter, r *http.Request) {
	var req CreateSimulationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, fmt.Sprintf("invalid request body: %v", err))
		return
	}

	// Validate required fields
	if req.Name == "" {
		h.writeErrorResponse(w, http.StatusBadRequest, "simulation name is required")
		return
	}

	if req.TimeRange.StartTime.IsZero() || req.TimeRange.EndTime.IsZero() {
		h.writeErrorResponse(w, http.StatusBadRequest, "valid time range is required")
		return
	}

	if req.TimeRange.EndTime.Before(req.TimeRange.StartTime) {
		h.writeErrorResponse(w, http.StatusBadRequest, "end time must be after start time")
		return
	}

	// Set defaults
	if req.EventFilters == nil {
		req.EventFilters = &policy.EventFilters{
			SourceTypes: []string{"agent", "extension"},
			EventTypes:  []string{"anomaly", "browser"},
		}
	}

	if req.SimulationConfig == nil {
		defaultConfig := policy.DefaultSimulationConfig()
		req.SimulationConfig = &defaultConfig
	}

	// Get requesting user (would come from auth middleware)
	requestedBy := r.Header.Get("X-User-ID")
	if requestedBy == "" {
		requestedBy = "anonymous"
	}

	// Create simulation request
	simulationID := uuid.New().String()
	simulationReq := &policy.SimulationRequest{
		SimulationID:     simulationID,
		Name:             req.Name,
		Description:      req.Description,
		PolicyBundle:     req.PolicyBundle,
		TimeRange:        req.TimeRange,
		EventFilters:     *req.EventFilters,
		SimulationConfig: *req.SimulationConfig,
		RequestedBy:      requestedBy,
		RequestedAt:      time.Now(),
	}

	h.logger.Info("Creating policy simulation",
		zap.String("simulation_id", simulationID),
		zap.String("name", req.Name),
		zap.String("requested_by", requestedBy))

	// Run simulation (this could be async for long-running simulations)
	result, err := h.sandbox.RunSimulation(r.Context(), simulationReq)
	if err != nil {
		h.logger.Error("Failed to run simulation",
			zap.String("simulation_id", simulationID),
			zap.Error(err))
		h.writeErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf("simulation failed: %v", err))
		return
	}

	response := SimulationResponse{
		SimulationID: simulationID,
		Status:       result.Status,
		Message:      fmt.Sprintf("Simulation '%s' %s", req.Name, result.Status),
		Result:       result,
		CreatedAt:    time.Now(),
	}

	h.writeJSONResponse(w, http.StatusCreated, response)
}

// GetSimulation retrieves a specific simulation result
func (h *PolicySandboxHandler) GetSimulation(w http.ResponseWriter, r *http.Request) {
	simulationID := r.URL.Query().Get("id")
	if simulationID == "" {
		h.writeErrorResponse(w, http.StatusBadRequest, "simulation_id parameter is required")
		return
	}

	h.logger.Info("Getting simulation result", zap.String("simulation_id", simulationID))

	// TODO: Implement storage retrieval
	// For now, return a mock response
	response := SimulationResponse{
		SimulationID: simulationID,
		Status:       "completed",
		Message:      "Simulation result retrieved",
		CreatedAt:    time.Now(),
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// ListSimulations retrieves a list of simulations with optional filtering
func (h *PolicySandboxHandler) ListSimulations(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	pageStr := r.URL.Query().Get("page")
	pageSizeStr := r.URL.Query().Get("page_size")
	status := r.URL.Query().Get("status")
	requestedBy := r.URL.Query().Get("requested_by")

	page := 1
	if pageStr != "" {
		if parsed, err := strconv.Atoi(pageStr); err == nil && parsed > 0 {
			page = parsed
		}
	}

	pageSize := 20
	if pageSizeStr != "" {
		if parsed, err := strconv.Atoi(pageSizeStr); err == nil && parsed > 0 && parsed <= 100 {
			pageSize = parsed
		}
	}

	h.logger.Info("Listing simulations",
		zap.Int("page", page),
		zap.Int("page_size", pageSize),
		zap.String("status", status),
		zap.String("requested_by", requestedBy))

	// TODO: Implement actual database query
	// For now, return mock data
	mockSimulations := []SimulationSummary{
		{
			SimulationID:     "sim-001",
			Name:             "Test Policy Bundle v1.2",
			Status:           "completed",
			RequestedBy:      "admin@example.com",
			CreatedAt:        time.Now().Add(-1 * time.Hour),
			EventsProcessed:  50000,
			IncidentDelta:    -3,
			ImpactAssessment: "Policy changes would prevent 3 incidents",
		},
		{
			SimulationID:     "sim-002",
			Name:             "Anomaly Threshold Adjustment",
			Status:           "completed",
			RequestedBy:      "sre@example.com",
			CreatedAt:        time.Now().Add(-3 * time.Hour),
			EventsProcessed:  125000,
			IncidentDelta:    2,
			ImpactAssessment: "Policy changes would result in 2 additional incidents detected",
		},
	}

	response := ListSimulationsResponse{
		Simulations: mockSimulations,
		Total:       len(mockSimulations),
		Page:        page,
		PageSize:    pageSize,
	}

	h.writeJSONResponse(w, http.StatusOK, response)
}

// ValidatePolicyBundle validates a policy bundle without running simulation
func (h *PolicySandboxHandler) ValidatePolicyBundle(w http.ResponseWriter, r *http.Request) {
	var policyBundle policy.PolicyBundle
	if err := json.NewDecoder(r.Body).Decode(&policyBundle); err != nil {
		h.writeErrorResponse(w, http.StatusBadRequest, fmt.Sprintf("invalid policy bundle: %v", err))
		return
	}

	h.logger.Info("Validating policy bundle", zap.String("version", policyBundle.Version))

	// Perform validation
	validationResult := h.validatePolicyBundle(policyBundle)

	h.writeJSONResponse(w, http.StatusOK, validationResult)
}

// GetDefaultPolicyBundle returns a default policy bundle template
func (h *PolicySandboxHandler) GetDefaultPolicyBundle(w http.ResponseWriter, r *http.Request) {
	defaultBundle := policy.PolicyBundle{
		Version: "1.0.0",
		AnomalyPolicies: []policy.AnomalyPolicy{
			{
				PolicyID:      "cpu-anomaly-policy",
				MetricName:    "cpu_usage",
				DetectionType: "zscore",
				Threshold:     2.0,
				WindowSize:    10,
				Sensitivity:   0.8,
				Enabled:       true,
				Conditions: []policy.PolicyCondition{
					{
						Field:    "device_type",
						Operator: "eq",
						Value:    "server",
					},
				},
			},
			{
				PolicyID:      "memory-anomaly-policy",
				MetricName:    "mem_usage",
				DetectionType: "zscore",
				Threshold:     2.5,
				WindowSize:    10,
				Sensitivity:   0.7,
				Enabled:       true,
			},
		},
		FilterRules: []policy.FilterRule{
			{
				RuleID:     "allow-critical-events",
				Name:       "Allow Critical Events",
				EventTypes: []string{"anomaly", "browser"},
				Action:     "allow",
				Conditions: []policy.PolicyCondition{
					{
						Field:    "severity",
						Operator: "in",
						Value:    []interface{}{"high", "critical"},
					},
				},
				Priority: 1,
				Enabled:  true,
			},
			{
				RuleID:     "suppress-test-devices",
				Name:       "Suppress Test Device Events",
				EventTypes: []string{"anomaly", "browser"},
				Action:     "deny",
				Conditions: []policy.PolicyCondition{
					{
						Field:    "device_id",
						Operator: "contains",
						Value:    "test-",
					},
				},
				Priority: 2,
				Enabled:  true,
			},
		},
		Thresholds: map[string]float64{
			"cpu_critical":    90.0,
			"memory_critical": 85.0,
			"latency_high":    5000.0,
		},
		AllowLists: map[string][]string{
			"domains": {"api.internal.com", "monitoring.internal.com"},
			"users":   {"admin", "sre-team"},
		},
		DenyLists: map[string][]string{
			"domains": {"malicious.com", "spam.com"},
			"ips":     {"192.168.1.100", "10.0.0.50"},
		},
		Metadata: map[string]interface{}{
			"created_by":  "system",
			"description": "Default policy bundle template",
			"environment": "development",
		},
	}

	h.writeJSONResponse(w, http.StatusOK, defaultBundle)
}

// PolicyValidationResult represents the result of policy bundle validation
type PolicyValidationResult struct {
	IsValid     bool                      `json:"is_valid"`
	Errors      []PolicyValidationError   `json:"errors"`
	Warnings    []PolicyValidationWarning `json:"warnings"`
	Summary     PolicyValidationSummary   `json:"summary"`
	ValidatedAt time.Time                 `json:"validated_at"`
}

// PolicyValidationError represents a validation error
type PolicyValidationError struct {
	Field   string `json:"field"`
	Message string `json:"message"`
	Code    string `json:"code"`
}

// PolicyValidationWarning represents a validation warning
type PolicyValidationWarning struct {
	Field   string `json:"field"`
	Message string `json:"message"`
	Code    string `json:"code"`
}

// PolicyValidationSummary provides a summary of the validation
type PolicyValidationSummary struct {
	TotalPolicies    int `json:"total_policies"`
	EnabledPolicies  int `json:"enabled_policies"`
	DisabledPolicies int `json:"disabled_policies"`
	TotalRules       int `json:"total_rules"`
	EnabledRules     int `json:"enabled_rules"`
	DisabledRules    int `json:"disabled_rules"`
}

// validatePolicyBundle performs validation on a policy bundle
func (h *PolicySandboxHandler) validatePolicyBundle(bundle policy.PolicyBundle) PolicyValidationResult {
	var errors []PolicyValidationError
	var warnings []PolicyValidationWarning

	// Validate version
	if bundle.Version == "" {
		errors = append(errors, PolicyValidationError{
			Field:   "version",
			Message: "Version is required",
			Code:    "MISSING_VERSION",
		})
	}

	// Validate anomaly policies
	enabledPolicies := 0
	for i, policy := range bundle.AnomalyPolicies {
		if policy.PolicyID == "" {
			errors = append(errors, PolicyValidationError{
				Field:   fmt.Sprintf("anomaly_policies[%d].policy_id", i),
				Message: "Policy ID is required",
				Code:    "MISSING_POLICY_ID",
			})
		}

		if policy.MetricName == "" {
			errors = append(errors, PolicyValidationError{
				Field:   fmt.Sprintf("anomaly_policies[%d].metric_name", i),
				Message: "Metric name is required",
				Code:    "MISSING_METRIC_NAME",
			})
		}

		if policy.Threshold <= 0 {
			warnings = append(warnings, PolicyValidationWarning{
				Field:   fmt.Sprintf("anomaly_policies[%d].threshold", i),
				Message: "Threshold should be positive",
				Code:    "INVALID_THRESHOLD",
			})
		}

		if policy.Enabled {
			enabledPolicies++
		}
	}

	// Validate filter rules
	enabledRules := 0
	for i, rule := range bundle.FilterRules {
		if rule.RuleID == "" {
			errors = append(errors, PolicyValidationError{
				Field:   fmt.Sprintf("filter_rules[%d].rule_id", i),
				Message: "Rule ID is required",
				Code:    "MISSING_RULE_ID",
			})
		}

		if len(rule.EventTypes) == 0 {
			warnings = append(warnings, PolicyValidationWarning{
				Field:   fmt.Sprintf("filter_rules[%d].event_types", i),
				Message: "No event types specified - rule will not match any events",
				Code:    "EMPTY_EVENT_TYPES",
			})
		}

		if rule.Enabled {
			enabledRules++
		}
	}

	// Check for potential conflicts
	if enabledPolicies == 0 {
		warnings = append(warnings, PolicyValidationWarning{
			Field:   "anomaly_policies",
			Message: "No anomaly policies are enabled",
			Code:    "NO_ENABLED_POLICIES",
		})
	}

	summary := PolicyValidationSummary{
		TotalPolicies:    len(bundle.AnomalyPolicies),
		EnabledPolicies:  enabledPolicies,
		DisabledPolicies: len(bundle.AnomalyPolicies) - enabledPolicies,
		TotalRules:       len(bundle.FilterRules),
		EnabledRules:     enabledRules,
		DisabledRules:    len(bundle.FilterRules) - enabledRules,
	}

	return PolicyValidationResult{
		IsValid:     len(errors) == 0,
		Errors:      errors,
		Warnings:    warnings,
		Summary:     summary,
		ValidatedAt: time.Now(),
	}
}

// Helper methods

func (h *PolicySandboxHandler) writeJSONResponse(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)

	if err := json.NewEncoder(w).Encode(data); err != nil {
		h.logger.Error("Failed to encode JSON response", zap.Error(err))
	}
}

func (h *PolicySandboxHandler) writeErrorResponse(w http.ResponseWriter, statusCode int, message string) {
	response := map[string]interface{}{
		"error":     message,
		"status":    statusCode,
		"timestamp": time.Now(),
	}

	h.writeJSONResponse(w, statusCode, response)
}
