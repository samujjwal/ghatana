package summarizer

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/samujjwal/dcmaar/apps/server/internal/services"
)

// LLMSummarizer generates incident summaries using LLM APIs
type LLMSummarizer struct {
	logger     *zap.Logger
	apiKey     string
	apiURL     string
	model      string
	httpClient *http.Client
}

// IncidentSummary represents a generated incident summary
type IncidentSummary struct {
	IncidentID      string            `json:"incident_id"`
	Summary         string            `json:"summary"`
	Timeline        []TimelineEvent   `json:"timeline"`
	RootCause       string            `json:"root_cause"`
	Impact          string            `json:"impact"`
	Recommendations []string          `json:"recommendations"`
	Confidence      float64           `json:"confidence"`
	GeneratedAt     time.Time         `json:"generated_at"`
	Model           string            `json:"model"`
	TokensUsed      int               `json:"tokens_used"`
	Labels          map[string]string `json:"labels"`
}

// TimelineEvent represents a key event in the incident timeline
type TimelineEvent struct {
	Timestamp   time.Time `json:"timestamp"`
	EventType   string    `json:"event_type"`
	Source      string    `json:"source"`
	Description string    `json:"description"`
	Severity    string    `json:"severity"`
}

// SummarizerConfig holds configuration for the LLM summarizer
type SummarizerConfig struct {
	MaxTokens       int     `json:"max_tokens"`
	Temperature     float64 `json:"temperature"`
	MaxSummaryWords int     `json:"max_summary_words"`
	IncludeTimeline bool    `json:"include_timeline"`
	TimelineEvents  int     `json:"timeline_events"`
}

// DefaultSummarizerConfig returns sensible defaults
func DefaultSummarizerConfig() SummarizerConfig {
	return SummarizerConfig{
		MaxTokens:       800, // Keep responses concise
		Temperature:     0.3, // Lower temperature for more consistent, factual responses
		MaxSummaryWords: 400, // Target < 500 words as per requirements
		IncludeTimeline: true,
		TimelineEvents:  8, // Show up to 8 key events
	}
}

// NewLLMSummarizer creates a new LLM-powered incident summarizer
func NewLLMSummarizer(logger *zap.Logger, apiKey, apiURL, model string) *LLMSummarizer {
	return &LLMSummarizer{
		logger: logger.Named("llm_summarizer"),
		apiKey: apiKey,
		apiURL: apiURL,
		model:  model,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// GenerateSummary creates an AI-powered summary of a correlated incident
func (ls *LLMSummarizer) GenerateSummary(ctx context.Context, incident *services.CorrelatedIncident, config SummarizerConfig) (*IncidentSummary, error) {
	ls.logger.Info("Generating incident summary",
		zap.String("incident_id", incident.IncidentID),
		zap.String("incident_type", incident.IncidentType),
		zap.String("severity", incident.Severity))

	// Build the prompt with incident data
	prompt := ls.buildPrompt(incident, config)

	// Call the LLM API
	response, err := ls.callLLM(ctx, prompt, config)
	if err != nil {
		return nil, fmt.Errorf("failed to call LLM: %w", err)
	}

	// Parse the LLM response into structured summary
	summary, err := ls.parseResponse(response, incident, config)
	if err != nil {
		return nil, fmt.Errorf("failed to parse LLM response: %w", err)
	}

	ls.logger.Info("Generated incident summary",
		zap.String("incident_id", incident.IncidentID),
		zap.Int("summary_words", len(strings.Fields(summary.Summary))),
		zap.Int("timeline_events", len(summary.Timeline)),
		zap.Float64("confidence", summary.Confidence))

	return summary, nil
}

// buildPrompt constructs the few-shot prompt for the LLM
func (ls *LLMSummarizer) buildPrompt(incident *services.CorrelatedIncident, config SummarizerConfig) string {
	var promptBuilder strings.Builder

	// System prompt with role definition
	promptBuilder.WriteString(`You are an expert SRE analyzing a performance incident. Generate a concise, technical summary for operations teams.

RESPONSE FORMAT (JSON):
{
  "summary": "Brief technical summary (< 400 words)",
  "timeline": [{"timestamp": "ISO8601", "event_type": "anomaly|latency", "source": "agent|extension", "description": "what happened", "severity": "low|medium|high|critical"}],
  "root_cause": "Primary root cause hypothesis",
  "impact": "Business/user impact description", 
  "recommendations": ["action 1", "action 2", "action 3"],
  "confidence": 0.85
}

EXAMPLE GOOD SUMMARY:
{
  "summary": "Multi-component performance degradation detected on device-123 between 14:30-14:35 UTC. Agent detected CPU anomaly (3.2σ above baseline) followed by memory pressure (2.8σ). Browser extension simultaneously reported severe latency spikes on example.com (4.8s) and api.example.com (7.2s). Strong correlation (r=0.74) suggests resource exhaustion impacting web performance. Peak impact affected 2 domains with sustained high latency.",
  "timeline": [
    {"timestamp": "2023-09-27T14:30:00Z", "event_type": "anomaly", "source": "agent", "description": "CPU usage anomaly detected (3.2σ)", "severity": "high"},
    {"timestamp": "2023-09-27T14:31:30Z", "event_type": "latency", "source": "extension", "description": "Page load latency spike on example.com (4.8s)", "severity": "high"},
    {"timestamp": "2023-09-27T14:32:15Z", "event_type": "anomaly", "source": "agent", "description": "Memory pressure anomaly (2.8σ)", "severity": "medium"},
    {"timestamp": "2023-09-27T14:33:00Z", "event_type": "latency", "source": "extension", "description": "API timeout on api.example.com (7.2s)", "severity": "critical"}
  ],
  "root_cause": "Resource exhaustion (CPU + memory) causing downstream web performance degradation",
  "impact": "User-facing performance degradation on 2 domains, potential service timeouts",
  "recommendations": ["Investigate CPU-intensive processes", "Check memory leaks", "Scale web service capacity", "Monitor domain-specific performance"],
  "confidence": 0.82
}

INCIDENT DATA:
`)

	// Add incident details
	promptBuilder.WriteString(fmt.Sprintf(`
Incident ID: %s
Type: %s
Severity: %s
Time Window: %s to %s (%.1f minutes)
Device: %s
Correlation Score: %.2f
Confidence: %.2f

Anomaly Events: %d events (Avg CPU Score: %.2f, Avg Memory Score: %.2f)
Web Events: %d events (Avg Latency: %.1fms)
Affected Domains: %s

Event IDs:
- Anomaly Events: %s
- Web Events: %s
`,
		incident.IncidentID,
		incident.IncidentType,
		incident.Severity,
		incident.WindowStart.Format("15:04:05"),
		incident.WindowEnd.Format("15:04:05"),
		incident.WindowEnd.Sub(incident.WindowStart).Minutes(),
		incident.DeviceID,
		incident.CorrelationScore,
		incident.Confidence,
		incident.AnomalyCount,
		incident.AvgCPUAnomalyScore,
		incident.AvgMemoryAnomalyScore,
		incident.WebEventCount,
		incident.AvgLatencyMs,
		strings.Join(incident.AffectedDomains, ", "),
		strings.Join(incident.AnomalyEventIDs, ", "),
		strings.Join(incident.WebEventIDs, ", "),
	))

	promptBuilder.WriteString("\nGenerate technical incident summary (JSON format only):")

	return promptBuilder.String()
}

// OpenAIRequest represents the structure for OpenAI API calls
type OpenAIRequest struct {
	Model       string    `json:"model"`
	Messages    []Message `json:"messages"`
	MaxTokens   int       `json:"max_tokens"`
	Temperature float64   `json:"temperature"`
}

// Message represents a chat message for OpenAI API
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// OpenAIResponse represents the OpenAI API response
type OpenAIResponse struct {
	Choices []Choice `json:"choices"`
	Usage   Usage    `json:"usage"`
}

// Choice represents a response choice from OpenAI
type Choice struct {
	Message Message `json:"message"`
}

// Usage represents token usage information
type Usage struct {
	TotalTokens int `json:"total_tokens"`
}

// callLLM makes the actual API call to the LLM service
func (ls *LLMSummarizer) callLLM(ctx context.Context, prompt string, config SummarizerConfig) (string, error) {
	// Prepare the request payload
	reqPayload := OpenAIRequest{
		Model: ls.model,
		Messages: []Message{
			{
				Role:    "user",
				Content: prompt,
			},
		},
		MaxTokens:   config.MaxTokens,
		Temperature: config.Temperature,
	}

	jsonData, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create HTTP request
	req, err := http.NewRequestWithContext(ctx, "POST", ls.apiURL, bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ls.apiKey)

	// Make the API call
	resp, err := ls.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("failed to make API call: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("API call failed with status %d: %s", resp.StatusCode, string(body))
	}

	// Parse the response
	var apiResp OpenAIResponse
	if err := json.Unmarshal(body, &apiResp); err != nil {
		return "", fmt.Errorf("failed to unmarshal response: %w", err)
	}

	if len(apiResp.Choices) == 0 {
		return "", fmt.Errorf("no choices returned from API")
	}

	return apiResp.Choices[0].Message.Content, nil
}

// parseResponse parses the LLM response into a structured summary
func (ls *LLMSummarizer) parseResponse(response string, incident *services.CorrelatedIncident, config SummarizerConfig) (*IncidentSummary, error) {
	// Try to extract JSON from the response (in case there's extra text)
	jsonStart := strings.Index(response, "{")
	jsonEnd := strings.LastIndex(response, "}") + 1

	if jsonStart == -1 || jsonEnd <= jsonStart {
		return nil, fmt.Errorf("no valid JSON found in response")
	}

	jsonContent := response[jsonStart:jsonEnd]

	// Parse the JSON response
	var parsedResponse struct {
		Summary         string          `json:"summary"`
		Timeline        []TimelineEvent `json:"timeline"`
		RootCause       string          `json:"root_cause"`
		Impact          string          `json:"impact"`
		Recommendations []string        `json:"recommendations"`
		Confidence      float64         `json:"confidence"`
	}

	if err := json.Unmarshal([]byte(jsonContent), &parsedResponse); err != nil {
		ls.logger.Warn("Failed to parse JSON response, using fallback",
			zap.Error(err),
			zap.String("response", response))

		// Fallback: create a basic summary from the raw response
		return ls.createFallbackSummary(response, incident), nil
	}

	// Validate word count
	wordCount := len(strings.Fields(parsedResponse.Summary))
	if wordCount > config.MaxSummaryWords {
		ls.logger.Warn("Summary exceeds word limit",
			zap.Int("word_count", wordCount),
			zap.Int("max_words", config.MaxSummaryWords))
	}

	// Create the structured summary
	summary := &IncidentSummary{
		IncidentID:      incident.IncidentID,
		Summary:         parsedResponse.Summary,
		Timeline:        parsedResponse.Timeline,
		RootCause:       parsedResponse.RootCause,
		Impact:          parsedResponse.Impact,
		Recommendations: parsedResponse.Recommendations,
		Confidence:      parsedResponse.Confidence,
		GeneratedAt:     time.Now(),
		Model:           ls.model,
		TokensUsed:      0, // Would be filled from API response usage info
		Labels: map[string]string{
			"generation_method": "llm",
			"incident_type":     incident.IncidentType,
			"severity":          incident.Severity,
		},
	}

	return summary, nil
}

// createFallbackSummary creates a basic summary when LLM parsing fails
func (ls *LLMSummarizer) createFallbackSummary(response string, incident *services.CorrelatedIncident) *IncidentSummary {
	// Create a basic summary with available data
	summary := fmt.Sprintf("Performance incident %s detected on device %s. Incident type: %s, Severity: %s. "+
		"Correlation between %d anomaly events and %d web events with %.2f correlation score. "+
		"Average latency: %.1fms across %d affected domains: %s.",
		incident.IncidentID,
		incident.DeviceID,
		incident.IncidentType,
		incident.Severity,
		incident.AnomalyCount,
		incident.WebEventCount,
		incident.CorrelationScore,
		incident.AvgLatencyMs,
		len(incident.AffectedDomains),
		strings.Join(incident.AffectedDomains, ", "))

	return &IncidentSummary{
		IncidentID: incident.IncidentID,
		Summary:    summary,
		Timeline:   []TimelineEvent{}, // Empty timeline for fallback
		RootCause:  fmt.Sprintf("Correlated %s incident", incident.IncidentType),
		Impact:     "Performance degradation detected",
		Recommendations: []string{
			"Investigate resource usage patterns",
			"Monitor affected domains",
			"Check for similar incidents",
		},
		Confidence:  0.5, // Lower confidence for fallback
		GeneratedAt: time.Now(),
		Model:       ls.model + "_fallback",
		TokensUsed:  0,
		Labels: map[string]string{
			"generation_method": "fallback",
			"incident_type":     incident.IncidentType,
			"severity":          incident.Severity,
		},
	}
}
