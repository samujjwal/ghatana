package summarizer

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
	"go.uber.org/zap/zaptest"

	"github.com/samujjwal/dcmaar/apps/server/internal/services"
)

// TestLLMSummarizer_PromptGeneration tests the prompt building logic
func TestLLMSummarizer_PromptGeneration(t *testing.T) {
	logger := zaptest.NewLogger(t)
	summarizer := NewLLMSummarizer(logger, "test-key", "https://api.example.com", "test-model")

	// Create a mock incident
	incident := &services.CorrelatedIncident{
		IncidentID:            "test-incident-123",
		CreatedAt:             time.Now(),
		DeviceID:              "device-456",
		IncidentType:          "performance",
		Severity:              "high",
		WindowStart:           time.Now().Add(-10 * time.Minute),
		WindowEnd:             time.Now(),
		CorrelationScore:      0.75,
		Confidence:            0.82,
		AnomalyCount:          3,
		WebEventCount:         2,
		AvgCPUAnomalyScore:    3.2,
		AvgMemoryAnomalyScore: 2.8,
		AvgLatencyMs:          4500.0,
		AffectedDomains:       []string{"example.com", "api.example.com"},
		AnomalyEventIDs:       []string{"anomaly-1", "anomaly-2", "anomaly-3"},
		WebEventIDs:           []string{"web-1", "web-2"},
	}

	config := DefaultSummarizerConfig()
	prompt := summarizer.buildPrompt(incident, config)

	// Verify prompt contains key information
	assert.Contains(t, prompt, "test-incident-123", "Prompt should contain incident ID")
	assert.Contains(t, prompt, "performance", "Prompt should contain incident type")
	assert.Contains(t, prompt, "high", "Prompt should contain severity")
	assert.Contains(t, prompt, "device-456", "Prompt should contain device ID")
	assert.Contains(t, prompt, "0.75", "Prompt should contain correlation score")
	assert.Contains(t, prompt, "example.com", "Prompt should contain affected domains")
	assert.Contains(t, prompt, "JSON", "Prompt should specify JSON response format")

	// Verify prompt structure
	assert.Contains(t, prompt, "RESPONSE FORMAT", "Prompt should include format instructions")
	assert.Contains(t, prompt, "EXAMPLE GOOD SUMMARY", "Prompt should include few-shot example")
	assert.Contains(t, prompt, "INCIDENT DATA", "Prompt should include incident data section")

	t.Logf("Generated prompt length: %d characters", len(prompt))
}

// TestLLMSummarizer_ResponseParsing tests the response parsing logic
func TestLLMSummarizer_ResponseParsing(t *testing.T) {
	logger := zaptest.NewLogger(t)
	summarizer := NewLLMSummarizer(logger, "test-key", "https://api.example.com", "test-model")

	incident := &services.CorrelatedIncident{
		IncidentID:   "test-incident-123",
		DeviceID:     "device-456",
		IncidentType: "performance",
		Severity:     "high",
	}

	config := DefaultSummarizerConfig()

	t.Run("Valid JSON Response", func(t *testing.T) {
		validResponse := `{
			"summary": "Test incident summary with performance issues detected on device-456",
			"timeline": [
				{"timestamp": "2023-09-27T14:30:00Z", "event_type": "anomaly", "source": "agent", "description": "CPU spike detected", "severity": "high"}
			],
			"root_cause": "CPU resource exhaustion",
			"impact": "Performance degradation on web services",
			"recommendations": ["Scale CPU resources", "Investigate process usage"],
			"confidence": 0.85
		}`

		summary, err := summarizer.parseResponse(validResponse, incident, config)
		require.NoError(t, err)

		assert.Equal(t, "test-incident-123", summary.IncidentID)
		assert.Contains(t, summary.Summary, "Test incident summary")
		assert.Equal(t, "CPU resource exhaustion", summary.RootCause)
		assert.Equal(t, "Performance degradation on web services", summary.Impact)
		assert.Len(t, summary.Recommendations, 2)
		assert.Equal(t, 0.85, summary.Confidence)
		assert.Len(t, summary.Timeline, 1)
		assert.Equal(t, "test-model", summary.Model)
	})

	t.Run("Invalid JSON Response - Fallback", func(t *testing.T) {
		invalidResponse := "This is not JSON, just some text about the incident."

		summary, err := summarizer.parseResponse(invalidResponse, incident, config)
		require.NoError(t, err) // Should not error, should use fallback

		assert.Equal(t, "test-incident-123", summary.IncidentID)
		assert.Contains(t, summary.Summary, "Performance incident")
		assert.Contains(t, summary.Summary, "device-456")
		assert.Contains(t, summary.Model, "fallback")
		assert.Equal(t, 0.5, summary.Confidence) // Lower confidence for fallback
		assert.NotEmpty(t, summary.Recommendations)
	})

	t.Run("Mixed Content with JSON", func(t *testing.T) {
		mixedResponse := `Here's the analysis:

		{
			"summary": "Mixed content test summary",
			"root_cause": "Test root cause",
			"impact": "Test impact",
			"recommendations": ["Test recommendation"],
			"confidence": 0.75,
			"timeline": []
		}

		Hope this helps!`

		summary, err := summarizer.parseResponse(mixedResponse, incident, config)
		require.NoError(t, err)

		assert.Equal(t, "Mixed content test summary", summary.Summary)
		assert.Equal(t, "Test root cause", summary.RootCause)
		assert.Equal(t, 0.75, summary.Confidence)
	})
}

// TestSummarizerConfig tests configuration handling
func TestSummarizerConfig(t *testing.T) {
	t.Run("Default Configuration", func(t *testing.T) {
		config := DefaultSummarizerConfig()

		assert.Equal(t, 800, config.MaxTokens)
		assert.Equal(t, 0.3, config.Temperature)
		assert.Equal(t, 400, config.MaxSummaryWords)
		assert.True(t, config.IncludeTimeline)
		assert.Equal(t, 8, config.TimelineEvents)
	})

	t.Run("Word Count Validation", func(t *testing.T) {
		config := DefaultSummarizerConfig()

		// Test that word count limits are reasonable
		assert.Less(t, config.MaxSummaryWords, 500, "Summary should be under 500 words per requirements")
		assert.Greater(t, config.MaxSummaryWords, 100, "Summary should be substantial enough")
	})
}

// TestIncidentSummary_Validation tests summary structure validation
func TestIncidentSummary_Validation(t *testing.T) {
	summary := &IncidentSummary{
		IncidentID: "test-123",
		Summary:    "This is a test summary with multiple words to test word counting functionality.",
		Timeline: []TimelineEvent{
			{
				Timestamp:   time.Now(),
				EventType:   "anomaly",
				Source:      "agent",
				Description: "Test event",
				Severity:    "high",
			},
		},
		RootCause:       "Test root cause",
		Impact:          "Test impact",
		Recommendations: []string{"Recommendation 1", "Recommendation 2"},
		Confidence:      0.85,
		GeneratedAt:     time.Now(),
		Model:           "test-model",
		Labels: map[string]string{
			"incident_type": "performance",
			"severity":      "high",
		},
	}

	// Validate structure
	assert.NotEmpty(t, summary.IncidentID)
	assert.NotEmpty(t, summary.Summary)
	assert.NotEmpty(t, summary.RootCause)
	assert.NotEmpty(t, summary.Impact)
	assert.NotEmpty(t, summary.Recommendations)
	assert.Greater(t, summary.Confidence, 0.0)
	assert.LessOrEqual(t, summary.Confidence, 1.0)
	assert.NotEmpty(t, summary.Timeline)
	assert.NotEmpty(t, summary.Labels)

	// Validate word count
	wordCount := len(strings.Fields(summary.Summary))
	assert.Greater(t, wordCount, 5, "Summary should have meaningful content")

	// Validate timeline structure
	assert.Equal(t, "anomaly", summary.Timeline[0].EventType)
	assert.Equal(t, "agent", summary.Timeline[0].Source)
	assert.Equal(t, "high", summary.Timeline[0].Severity)
}

// MockLLMSummarizer for testing without external API calls
type MockLLMSummarizer struct {
	logger       *zap.Logger
	mockResponse string
}

func NewMockLLMSummarizer(logger *zap.Logger, mockResponse string) *MockLLMSummarizer {
	return &MockLLMSummarizer{
		logger:       logger,
		mockResponse: mockResponse,
	}
}

func (m *MockLLMSummarizer) GenerateSummary(ctx context.Context, incident *services.CorrelatedIncident, config SummarizerConfig) (*IncidentSummary, error) {
	// Return a mock summary for testing
	return &IncidentSummary{
		IncidentID:      incident.IncidentID,
		Summary:         "Mock incident summary for testing purposes",
		RootCause:       "Mock root cause",
		Impact:          "Mock impact assessment",
		Recommendations: []string{"Mock recommendation 1", "Mock recommendation 2"},
		Confidence:      0.80,
		GeneratedAt:     time.Now(),
		Model:           "mock-model",
		Timeline: []TimelineEvent{
			{
				Timestamp:   time.Now(),
				EventType:   "anomaly",
				Source:      "agent",
				Description: "Mock anomaly event",
				Severity:    "high",
			},
		},
		Labels: map[string]string{
			"incident_type": incident.IncidentType,
			"severity":      incident.Severity,
			"mock":          "true",
		},
	}, nil
}

// TestMockSummarizer tests the mock implementation
func TestMockSummarizer(t *testing.T) {
	logger := zaptest.NewLogger(t)
	mockSummarizer := NewMockLLMSummarizer(logger, "mock response")

	incident := &services.CorrelatedIncident{
		IncidentID:   "test-123",
		IncidentType: "performance",
		Severity:     "high",
		DeviceID:     "device-456",
	}

	config := DefaultSummarizerConfig()
	summary, err := mockSummarizer.GenerateSummary(context.Background(), incident, config)

	require.NoError(t, err)
	assert.Equal(t, "test-123", summary.IncidentID)
	assert.Contains(t, summary.Summary, "Mock incident summary")
	assert.Equal(t, "performance", summary.Labels["incident_type"])
	assert.Equal(t, "high", summary.Labels["severity"])
	assert.Equal(t, "true", summary.Labels["mock"])
}
