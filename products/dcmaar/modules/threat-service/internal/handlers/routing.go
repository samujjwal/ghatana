package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/gorilla/mux"
	"github.com/samujjwal/dcmaar/apps/server/internal/routing"
)

// RoutingHandler handles HTTP requests for the smart routing service
type RoutingHandler struct {
	service routing.Service
}

// NewRoutingHandler creates a new routing handler
func NewRoutingHandler(service routing.Service) *RoutingHandler {
	return &RoutingHandler{
		service: service,
	}
}

// RegisterRoutes registers all routing endpoints
func (h *RoutingHandler) RegisterRoutes(r *mux.Router) {
	// Routing decision endpoints
	r.HandleFunc("/routing/decide", h.MakeRoutingDecision).Methods("POST")
	r.HandleFunc("/routing/decisions/{decisionId}", h.GetRoutingDecision).Methods("GET")
	r.HandleFunc("/routing/decisions/{decisionId}/status", h.UpdateDecisionStatus).Methods("PUT")
	r.HandleFunc("/routing/decisions", h.GetDecisionHistory).Methods("GET")

	// Target management endpoints
	r.HandleFunc("/routing/targets", h.RegisterTarget).Methods("POST")
	r.HandleFunc("/routing/targets", h.ListTargets).Methods("GET")
	r.HandleFunc("/routing/targets/{targetId}", h.UpdateTarget).Methods("PUT")
	r.HandleFunc("/routing/targets/{targetId}", h.DeregisterTarget).Methods("DELETE")
	r.HandleFunc("/routing/targets/{targetId}/health", h.GetTargetHealth).Methods("GET")
	r.HandleFunc("/routing/targets/{targetId}/metrics", h.GetTargetMetrics).Methods("GET")

	// Strategy management endpoints
	r.HandleFunc("/routing/strategies", h.CreateStrategy).Methods("POST")
	r.HandleFunc("/routing/strategies", h.ListStrategies).Methods("GET")
	r.HandleFunc("/routing/strategies/{strategyName}", h.GetStrategy).Methods("GET")
	r.HandleFunc("/routing/strategies/{strategyName}", h.UpdateStrategy).Methods("PUT")

	// Rule management endpoints
	r.HandleFunc("/routing/rules", h.CreateRule).Methods("POST")
	r.HandleFunc("/routing/rules", h.ListRules).Methods("GET")
	r.HandleFunc("/routing/rules/{ruleId}", h.UpdateRule).Methods("PUT")
	r.HandleFunc("/routing/rules/{ruleId}", h.DeleteRule).Methods("DELETE")

	// Analytics and monitoring endpoints
	r.HandleFunc("/routing/analytics", h.GetRoutingAnalytics).Methods("GET")
	r.HandleFunc("/routing/health/monitor", h.MonitorTargetHealth).Methods("POST")
	r.HandleFunc("/routing/optimization/{serviceName}", h.OptimizeRouting).Methods("POST")
}

// MakeRoutingDecision handles intelligent routing decisions
func (h *RoutingHandler) MakeRoutingDecision(w http.ResponseWriter, r *http.Request) {
	var request routing.RoutingRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Validate required fields
	if request.ServiceName == "" {
		http.Error(w, "service_name is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	decision, err := h.service.MakeRoutingDecision(ctx, request)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to make routing decision: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(decision)
}

// GetRoutingDecision retrieves a specific routing decision
func (h *RoutingHandler) GetRoutingDecision(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	decisionID := vars["decisionId"]

	if decisionID == "" {
		http.Error(w, "decision ID is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	decision, err := h.service.GetRoutingDecision(ctx, decisionID)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get routing decision: %v", err), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(decision)
}

// UpdateDecisionStatus updates the status of a routing decision
func (h *RoutingHandler) UpdateDecisionStatus(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	decisionID := vars["decisionId"]

	var statusUpdate struct {
		Status routing.DecisionStatus `json:"status"`
	}

	if err := json.NewDecoder(r.Body).Decode(&statusUpdate); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.UpdateDecisionStatus(ctx, decisionID, statusUpdate.Status); err != nil {
		http.Error(w, fmt.Sprintf("Failed to update decision status: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "updated"})
}

// GetDecisionHistory retrieves historical routing decisions
func (h *RoutingHandler) GetDecisionHistory(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()

	// Parse filters
	filters := routing.DecisionFilters{}

	if serviceName := query.Get("service_name"); serviceName != "" {
		filters.ServiceName = &serviceName
	}

	if status := query.Get("status"); status != "" {
		decisionStatus := routing.DecisionStatus(status)
		filters.Status = &decisionStatus
	}

	if limitStr := query.Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil {
			filters.Limit = limit
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	decisions, err := h.service.GetDecisionHistory(ctx, filters)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get decision history: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"decisions": decisions,
		"count":     len(decisions),
	})
}

// RegisterTarget registers a new routing target
func (h *RoutingHandler) RegisterTarget(w http.ResponseWriter, r *http.Request) {
	var target routing.Target
	if err := json.NewDecoder(r.Body).Decode(&target); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Validate required fields
	if target.Name == "" || target.Endpoint == "" {
		http.Error(w, "name and endpoint are required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.RegisterTarget(ctx, &target); err != nil {
		http.Error(w, fmt.Sprintf("Failed to register target: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(target)
}

// ListTargets lists available routing targets
func (h *RoutingHandler) ListTargets(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()

	// Parse filters
	filters := routing.TargetFilters{}

	if region := query.Get("region"); region != "" {
		filters.Region = &region
	}

	if minHealthStr := query.Get("min_health_score"); minHealthStr != "" {
		if minHealth, err := strconv.ParseFloat(minHealthStr, 64); err == nil {
			filters.MinHealthScore = &minHealth
		}
	}

	if limitStr := query.Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil {
			filters.Limit = limit
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	targets, err := h.service.ListTargets(ctx, filters)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to list targets: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"targets": targets,
		"count":   len(targets),
	})
}

// UpdateTarget updates an existing routing target
func (h *RoutingHandler) UpdateTarget(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	targetID := vars["targetId"]

	var target routing.Target
	if err := json.NewDecoder(r.Body).Decode(&target); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Ensure ID matches URL parameter
	target.ID = targetID

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.UpdateTarget(ctx, &target); err != nil {
		http.Error(w, fmt.Sprintf("Failed to update target: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(target)
}

// DeregisterTarget removes a routing target
func (h *RoutingHandler) DeregisterTarget(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	targetID := vars["targetId"]

	if targetID == "" {
		http.Error(w, "target ID is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.DeregisterTarget(ctx, targetID); err != nil {
		http.Error(w, fmt.Sprintf("Failed to deregister target: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetTargetHealth gets comprehensive health information for a target
func (h *RoutingHandler) GetTargetHealth(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	targetID := vars["targetId"]

	if targetID == "" {
		http.Error(w, "target ID is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	health, err := h.service.GetTargetHealth(ctx, targetID)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get target health: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

// GetTargetMetrics provides detailed metrics for a specific target
func (h *RoutingHandler) GetTargetMetrics(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	targetID := vars["targetId"]

	if targetID == "" {
		http.Error(w, "target ID is required", http.StatusBadRequest)
		return
	}

	query := r.URL.Query()
	timeframe := query.Get("timeframe")
	if timeframe == "" {
		timeframe = "1h" // Default to 1 hour
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	metrics, err := h.service.GetTargetMetrics(ctx, targetID, timeframe)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get target metrics: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(metrics)
}

// CreateStrategy creates a new routing strategy
func (h *RoutingHandler) CreateStrategy(w http.ResponseWriter, r *http.Request) {
	var strategy routing.RoutingStrategy
	if err := json.NewDecoder(r.Body).Decode(&strategy); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Validate required fields
	if strategy.Name == "" {
		http.Error(w, "strategy name is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.CreateStrategy(ctx, &strategy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to create strategy: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(strategy)
}

// ListStrategies lists all routing strategies
func (h *RoutingHandler) ListStrategies(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	strategies, err := h.service.ListStrategies(ctx)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to list strategies: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"strategies": strategies,
		"count":      len(strategies),
	})
}

// GetStrategy retrieves a specific routing strategy
func (h *RoutingHandler) GetStrategy(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	strategyName := vars["strategyName"]

	if strategyName == "" {
		http.Error(w, "strategy name is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	strategy, err := h.service.GetStrategy(ctx, strategyName)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get strategy: %v", err), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(strategy)
}

// UpdateStrategy updates an existing routing strategy
func (h *RoutingHandler) UpdateStrategy(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	strategyName := vars["strategyName"]

	var strategy routing.RoutingStrategy
	if err := json.NewDecoder(r.Body).Decode(&strategy); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Ensure name matches URL parameter
	strategy.Name = strategyName

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.UpdateStrategy(ctx, &strategy); err != nil {
		http.Error(w, fmt.Sprintf("Failed to update strategy: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(strategy)
}

// CreateRule creates a new routing rule
func (h *RoutingHandler) CreateRule(w http.ResponseWriter, r *http.Request) {
	var rule routing.RoutingRule
	if err := json.NewDecoder(r.Body).Decode(&rule); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Validate required fields
	if rule.Name == "" {
		http.Error(w, "rule name is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.CreateRule(ctx, &rule); err != nil {
		http.Error(w, fmt.Sprintf("Failed to create rule: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(rule)
}

// ListRules lists routing rules with filtering
func (h *RoutingHandler) ListRules(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()

	// Parse filters
	filters := routing.RuleFilters{}

	if serviceName := query.Get("service_name"); serviceName != "" {
		filters.ServiceName = &serviceName
	}

	if enabled := query.Get("enabled"); enabled != "" {
		if enabledBool, err := strconv.ParseBool(enabled); err == nil {
			filters.Enabled = &enabledBool
		}
	}

	if limitStr := query.Get("limit"); limitStr != "" {
		if limit, err := strconv.Atoi(limitStr); err == nil {
			filters.Limit = limit
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	rules, err := h.service.ListRules(ctx, filters)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to list rules: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"rules": rules,
		"count": len(rules),
	})
}

// UpdateRule updates an existing routing rule
func (h *RoutingHandler) UpdateRule(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	ruleID := vars["ruleId"]

	var rule routing.RoutingRule
	if err := json.NewDecoder(r.Body).Decode(&rule); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request body: %v", err), http.StatusBadRequest)
		return
	}

	// Ensure ID matches URL parameter
	rule.ID = ruleID

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.UpdateRule(ctx, &rule); err != nil {
		http.Error(w, fmt.Sprintf("Failed to update rule: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(rule)
}

// DeleteRule deletes a routing rule
func (h *RoutingHandler) DeleteRule(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	ruleID := vars["ruleId"]

	if ruleID == "" {
		http.Error(w, "rule ID is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := h.service.DeleteRule(ctx, ruleID); err != nil {
		http.Error(w, fmt.Sprintf("Failed to delete rule: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetRoutingAnalytics provides analytics for routing decisions
func (h *RoutingHandler) GetRoutingAnalytics(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	timeframe := query.Get("timeframe")
	if timeframe == "" {
		timeframe = "24h" // Default to 24 hours
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	analytics, err := h.service.GetRoutingAnalytics(ctx, timeframe)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to get routing analytics: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(analytics)
}

// MonitorTargetHealth monitors the health of all targets
func (h *RoutingHandler) MonitorTargetHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := h.service.MonitorTargetHealth(ctx); err != nil {
		http.Error(w, fmt.Sprintf("Failed to monitor target health: %v", err), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{
		"status":  "completed",
		"message": "Target health monitoring completed successfully",
	})
}

// OptimizeRouting analyzes and optimizes routing for a service
func (h *RoutingHandler) OptimizeRouting(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	serviceName := vars["serviceName"]

	if serviceName == "" {
		http.Error(w, "service name is required", http.StatusBadRequest)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	result, err := h.service.OptimizeRouting(ctx, serviceName)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to optimize routing: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// Health check endpoint for the routing service
func (h *RoutingHandler) HealthCheck(w http.ResponseWriter, r *http.Request) {
	health := map[string]interface{}{
		"service":   "routing",
		"status":    "healthy",
		"timestamp": time.Now(),
		"version":   "1.0.0",
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}
