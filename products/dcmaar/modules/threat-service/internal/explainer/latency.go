// Package explainer provides latency root-cause analysis
// Implements Capability 2: Latency Root-Cause Explainer from Horizontal Slice AI Plan #4
package explainer

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"time"
)

// LatencyAttribution represents the breakdown of latency causes
type LatencyAttribution struct {
	Site    float64 `json:"site_percent"`    // Server-side latency %
	Client  float64 `json:"client_percent"`  // Client-side latency %
	Network float64 `json:"network_percent"` // Network latency %
}

// LatencyEvidence represents supporting evidence for latency attribution
type LatencyEvidence struct {
	Type       string                 `json:"type"`       // "dns", "tcp", "ssl", "http", "processing"
	Component  string                 `json:"component"`  // Which component contributed
	Latency    float64                `json:"latency_ms"` // Measured latency in ms
	Confidence float64                `json:"confidence"` // 0.0 to 1.0
	Details    map[string]interface{} `json:"details"`    // Additional context
}

// LatencyExplanation represents the complete latency breakdown
type LatencyExplanation struct {
	RequestID          string             `json:"request_id"`
	TotalLatency       float64            `json:"total_latency_ms"`
	Attribution        LatencyAttribution `json:"attribution"`
	Evidence           []LatencyEvidence  `json:"evidence"`
	Confidence         float64            `json:"overall_confidence"`
	Timestamp          time.Time          `json:"timestamp"`
	RecommendedActions []string           `json:"recommended_actions"`
}

// NetworkMetrics represents network-level measurements
type NetworkMetrics struct {
	DNSLatency   float64 `json:"dns_latency_ms"`
	TCPHandshake float64 `json:"tcp_handshake_ms"`
	SSLHandshake float64 `json:"ssl_handshake_ms"`
	FirstByte    float64 `json:"first_byte_ms"`
	PacketLoss   float64 `json:"packet_loss_percent"`
	Retransmits  int     `json:"retransmit_count"`
}

// ServerMetrics represents server-side performance data
type ServerMetrics struct {
	ProcessingTime  float64 `json:"processing_time_ms"`
	QueueTime       float64 `json:"queue_time_ms"`
	DatabaseLatency float64 `json:"database_latency_ms"`
	CacheHitRatio   float64 `json:"cache_hit_ratio"`
	CPUUtilization  float64 `json:"cpu_utilization_percent"`
	MemoryPressure  float64 `json:"memory_pressure_percent"`
}

// ClientMetrics represents client-side performance data
type ClientMetrics struct {
	RenderTime       float64 `json:"render_time_ms"`
	DOMReady         float64 `json:"dom_ready_ms"`
	JavaScriptTime   float64 `json:"javascript_time_ms"`
	ResourceLoadTime float64 `json:"resource_load_time_ms"`
}

// LatencyExplainer analyzes and explains latency root causes
type LatencyExplainer struct {
	metricsStore    map[string]interface{} // Mock metrics store
	dnsResolver     *DNSAnalyzer
	networkAnalyzer *NetworkAnalyzer
}

// NewLatencyExplainer creates a new latency explainer
func NewLatencyExplainer() *LatencyExplainer {
	return &LatencyExplainer{
		metricsStore:    make(map[string]interface{}),
		dnsResolver:     NewDNSAnalyzer(),
		networkAnalyzer: NewNetworkAnalyzer(),
	}
}

// ExplainLatency analyzes latency for a specific request
func (le *LatencyExplainer) ExplainLatency(ctx context.Context, requestID string) (*LatencyExplanation, error) {
	// Gather metrics from various sources
	networkMetrics, err := le.gatherNetworkMetrics(ctx, requestID)
	if err != nil {
		return nil, fmt.Errorf("failed to gather network metrics: %w", err)
	}

	serverMetrics, err := le.gatherServerMetrics(ctx, requestID)
	if err != nil {
		return nil, fmt.Errorf("failed to gather server metrics: %w", err)
	}

	clientMetrics, err := le.gatherClientMetrics(ctx, requestID)
	if err != nil {
		return nil, fmt.Errorf("failed to gather client metrics: %w", err)
	}

	// Calculate total latency
	totalLatency := networkMetrics.FirstByte + serverMetrics.ProcessingTime + clientMetrics.RenderTime

	// Analyze evidence and build explanation
	evidence := le.analyzeEvidence(networkMetrics, serverMetrics, clientMetrics)
	attribution := le.calculateAttribution(evidence, totalLatency)

	// Generate actionable recommendations
	actions := le.generateRecommendations(evidence, attribution)

	// Calculate overall confidence based on data quality
	confidence := le.calculateConfidence(evidence)

	return &LatencyExplanation{
		RequestID:          requestID,
		TotalLatency:       totalLatency,
		Attribution:        attribution,
		Evidence:           evidence,
		Confidence:         confidence,
		Timestamp:          time.Now(),
		RecommendedActions: actions,
	}, nil
}

// gatherNetworkMetrics collects network-level performance data
func (le *LatencyExplainer) gatherNetworkMetrics(ctx context.Context, requestID string) (*NetworkMetrics, error) {
	// In production, this would query Prometheus or other metrics backends
	// For now, return mock data with some variation based on request ID
	_ = requestID

	// Simulate varying network conditions
	return &NetworkMetrics{
		DNSLatency:   12.5 + float64(len(requestID)%10),
		TCPHandshake: 8.2 + float64(len(requestID)%5),
		SSLHandshake: 15.7 + float64(len(requestID)%8),
		FirstByte:    45.3 + float64(len(requestID)%20),
		PacketLoss:   0.1,
		Retransmits:  len(requestID) % 3,
	}, nil
}

// gatherServerMetrics collects server-side performance data
func (le *LatencyExplainer) gatherServerMetrics(ctx context.Context, requestID string) (*ServerMetrics, error) {
	// In production, this would query actual server metrics
	return &ServerMetrics{
		ProcessingTime:  125.4,
		QueueTime:       8.9,
		DatabaseLatency: 67.2,
		CacheHitRatio:   0.85,
		CPUUtilization:  45.2,
		MemoryPressure:  62.1,
	}, nil
}

// gatherClientMetrics collects client-side performance data
func (le *LatencyExplainer) gatherClientMetrics(ctx context.Context, requestID string) (*ClientMetrics, error) {
	// In production, this would query browser performance API data
	return &ClientMetrics{
		RenderTime:       89.3,
		DOMReady:         156.7,
		JavaScriptTime:   45.8,
		ResourceLoadTime: 234.5,
	}, nil
}

// analyzeEvidence builds a list of latency evidence from metrics
func (le *LatencyExplainer) analyzeEvidence(
	network *NetworkMetrics,
	server *ServerMetrics,
	client *ClientMetrics,
) []LatencyEvidence {
	var evidence []LatencyEvidence

	// Network evidence
	if network.DNSLatency > 10 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "dns",
			Component:  "network",
			Latency:    network.DNSLatency,
			Confidence: 0.9,
			Details: map[string]interface{}{
				"threshold_ms": 10,
				"severity":     "high",
			},
		})
	}

	if network.TCPHandshake > 50 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "tcp",
			Component:  "network",
			Latency:    network.TCPHandshake,
			Confidence: 0.85,
			Details: map[string]interface{}{
				"threshold_ms": 50,
				"likely_cause": "geographic_distance",
			},
		})
	}

	if network.PacketLoss > 0.5 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "packet_loss",
			Component:  "network",
			Latency:    network.PacketLoss * 100, // Convert to impact estimate
			Confidence: 0.95,
			Details: map[string]interface{}{
				"loss_percent": network.PacketLoss,
				"impact":       "high",
			},
		})
	}

	// Server evidence
	if server.ProcessingTime > 100 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "processing",
			Component:  "server",
			Latency:    server.ProcessingTime,
			Confidence: 0.9,
			Details: map[string]interface{}{
				"threshold_ms":    100,
				"cpu_utilization": server.CPUUtilization,
				"memory_pressure": server.MemoryPressure,
			},
		})
	}

	if server.DatabaseLatency > 50 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "database",
			Component:  "server",
			Latency:    server.DatabaseLatency,
			Confidence: 0.95,
			Details: map[string]interface{}{
				"threshold_ms":    50,
				"cache_hit_ratio": server.CacheHitRatio,
			},
		})
	}

	if server.QueueTime > 5 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "queue",
			Component:  "server",
			Latency:    server.QueueTime,
			Confidence: 0.8,
			Details: map[string]interface{}{
				"threshold_ms": 5,
				"backlog":      "high",
			},
		})
	}

	// Client evidence
	if client.RenderTime > 100 {
		evidence = append(evidence, LatencyEvidence{
			Type:       "render",
			Component:  "client",
			Latency:    client.RenderTime,
			Confidence: 0.85,
			Details: map[string]interface{}{
				"threshold_ms":       100,
				"javascript_time_ms": client.JavaScriptTime,
			},
		})
	}

	return evidence
}

// calculateAttribution determines percentage attribution to site/client/network
func (le *LatencyExplainer) calculateAttribution(evidence []LatencyEvidence, totalLatency float64) LatencyAttribution {
	var siteLatency, clientLatency, networkLatency float64

	for _, ev := range evidence {
		weightedLatency := ev.Latency * ev.Confidence

		switch ev.Component {
		case "server":
			siteLatency += weightedLatency
		case "client":
			clientLatency += weightedLatency
		case "network":
			networkLatency += weightedLatency
		}
	}

	// Normalize to percentages
	total := siteLatency + clientLatency + networkLatency
	if total == 0 {
		return LatencyAttribution{Site: 33.33, Client: 33.33, Network: 33.33}
	}

	return LatencyAttribution{
		Site:    (siteLatency / total) * 100,
		Client:  (clientLatency / total) * 100,
		Network: (networkLatency / total) * 100,
	}
}

// generateRecommendations provides actionable insights
func (le *LatencyExplainer) generateRecommendations(evidence []LatencyEvidence, attribution LatencyAttribution) []string {
	var recommendations []string

	// Sort evidence by impact (latency * confidence)
	sort.Slice(evidence, func(i, j int) bool {
		return evidence[i].Latency*evidence[i].Confidence > evidence[j].Latency*evidence[j].Confidence
	})

	// Generate recommendations based on top evidence
	for i, ev := range evidence {
		if i >= 3 { // Limit to top 3 recommendations
			break
		}

		switch ev.Type {
		case "dns":
			recommendations = append(recommendations, "Consider using a faster DNS resolver or implementing DNS caching")
		case "tcp":
			recommendations = append(recommendations, "Investigate geographic routing or CDN optimization")
		case "packet_loss":
			recommendations = append(recommendations, "Check network infrastructure and ISP connectivity")
		case "processing":
			recommendations = append(recommendations, "Optimize server processing time or scale compute resources")
		case "database":
			recommendations = append(recommendations, "Review database query performance and caching strategy")
		case "queue":
			recommendations = append(recommendations, "Scale request processing capacity or implement load balancing")
		case "render":
			recommendations = append(recommendations, "Optimize client-side JavaScript and rendering performance")
		}
	}

	// Add attribution-based recommendations
	if attribution.Network > 50 {
		recommendations = append(recommendations, "Network is the primary bottleneck - focus on CDN and routing optimization")
	} else if attribution.Site > 50 {
		recommendations = append(recommendations, "Server processing is the primary bottleneck - focus on backend optimization")
	} else if attribution.Client > 50 {
		recommendations = append(recommendations, "Client rendering is the primary bottleneck - focus on frontend optimization")
	}

	return recommendations
}

// calculateConfidence determines overall confidence in the explanation
func (le *LatencyExplainer) calculateConfidence(evidence []LatencyEvidence) float64 {
	if len(evidence) == 0 {
		return 0.5 // Medium confidence when no evidence
	}

	// Weighted average of evidence confidence
	var totalWeight, weightedSum float64
	for _, ev := range evidence {
		weight := ev.Latency // Weight by latency impact
		totalWeight += weight
		weightedSum += ev.Confidence * weight
	}

	baseConfidence := weightedSum / totalWeight

	// Adjust confidence based on evidence diversity
	components := make(map[string]bool)
	for _, ev := range evidence {
		components[ev.Component] = true
	}

	diversityBonus := float64(len(components)) * 0.1 // Up to 30% bonus for diverse evidence
	return math.Min(1.0, baseConfidence+diversityBonus)
}

// HTTPHandler handles HTTP requests for latency explanation
func (le *LatencyExplainer) HTTPHandler(w http.ResponseWriter, r *http.Request) {
	// Extract request ID from URL path or query params
	requestID := r.URL.Query().Get("request_id")
	if requestID == "" {
		// Try to extract from path segments
		path := r.URL.Path
		if len(path) > 0 && path[len(path)-1] != '/' {
			// Extract last segment as request ID
			segments := []rune(path)
			for i := len(segments) - 1; i >= 0; i-- {
				if segments[i] == '/' {
					requestID = string(segments[i+1:])
					break
				}
			}
		}
	}

	if requestID == "" {
		http.Error(w, "Missing request ID in URL path or query params", http.StatusBadRequest)
		return
	}

	explanation, err := le.ExplainLatency(r.Context(), requestID)
	if err != nil {
		http.Error(w, fmt.Sprintf("Failed to explain latency: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(explanation); err != nil {
		http.Error(w, "Failed to encode response", http.StatusInternalServerError)
		return
	}
}

// DNSAnalyzer provides DNS-specific latency analysis
type DNSAnalyzer struct{}

// NewDNSAnalyzer creates a new DNS analyzer
func NewDNSAnalyzer() *DNSAnalyzer {
	return &DNSAnalyzer{}
}

// NetworkAnalyzer provides network-specific latency analysis
type NetworkAnalyzer struct{}

// NewNetworkAnalyzer creates a new network analyzer
func NewNetworkAnalyzer() *NetworkAnalyzer {
	return &NetworkAnalyzer{}
}
