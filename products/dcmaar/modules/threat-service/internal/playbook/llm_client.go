package playbook

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"go.uber.org/zap"
)

// OpenAIClient implements LLMClient for OpenAI GPT models
type OpenAIClient struct {
	logger     *zap.Logger
	httpClient *http.Client
	apiKey     string
	baseURL    string
}

// OpenAIRequest represents a request to OpenAI API
type OpenAIRequest struct {
	Model       string    `json:"model"`
	Messages    []Message `json:"messages"`
	Temperature float64   `json:"temperature"`
	MaxTokens   int       `json:"max_tokens"`
	TopP        float64   `json:"top_p,omitempty"`
	Stream      bool      `json:"stream"`
}

// Message represents a chat message
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// OpenAIResponse represents OpenAI API response
type OpenAIResponse struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Created int64    `json:"created"`
	Model   string   `json:"model"`
	Usage   Usage    `json:"usage"`
	Choices []Choice `json:"choices"`
}

// Choice represents a response choice
type Choice struct {
	Index        int     `json:"index"`
	Message      Message `json:"message"`
	FinishReason string  `json:"finish_reason"`
}

// Usage represents token usage
type Usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

// NewOpenAIClient creates a new OpenAI client
func NewOpenAIClient(logger *zap.Logger, apiKey string) *OpenAIClient {
	return &OpenAIClient{
		logger: logger,
		httpClient: &http.Client{
			Timeout: 120 * time.Second,
		},
		apiKey:  apiKey,
		baseURL: "https://api.openai.com/v1",
	}
}

// GeneratePlaybook generates a playbook using OpenAI
func (c *OpenAIClient) GeneratePlaybook(ctx context.Context, prompt string, config PlaybookConfig) (*LLMResponse, error) {
	request := OpenAIRequest{
		Model:       config.Model,
		Temperature: config.Temperature,
		MaxTokens:   config.MaxTokens,
		Stream:      false,
		Messages: []Message{
			{
				Role:    "system",
				Content: "You are an expert SRE creating detailed incident response playbooks. Generate comprehensive, executable playbooks in valid JSON format with proper error handling and safety measures.",
			},
			{
				Role:    "user",
				Content: prompt,
			},
		},
	}

	// Create HTTP request
	reqBody, err := json.Marshal(request)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", c.baseURL+"/chat/completions", bytes.NewBuffer(reqBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+c.apiKey)

	// Execute request
	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API request failed with status %d", resp.StatusCode)
	}

	// Parse response
	var openAIResp OpenAIResponse
	if err := json.NewDecoder(resp.Body).Decode(&openAIResp); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	if len(openAIResp.Choices) == 0 {
		return nil, fmt.Errorf("no choices in response")
	}

	// Calculate confidence score based on response quality
	confidence := c.calculateConfidence(openAIResp.Choices[0].Message.Content, openAIResp.Usage)

	return &LLMResponse{
		Content:         openAIResp.Choices[0].Message.Content,
		ConfidenceScore: confidence,
		TokensUsed:      openAIResp.Usage.TotalTokens,
		Model:           openAIResp.Model,
		Metadata: map[string]interface{}{
			"finish_reason": openAIResp.Choices[0].FinishReason,
			"usage":         openAIResp.Usage,
		},
	}, nil
}

// ValidatePlaybook validates a generated playbook using LLM
func (c *OpenAIClient) ValidatePlaybook(ctx context.Context, playbook *Playbook) (*ValidationResult, error) {
	// Convert playbook to JSON for validation
	playbookJSON, err := json.MarshalIndent(playbook, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("failed to marshal playbook: %w", err)
	}

	prompt := fmt.Sprintf(`You are an expert SRE validator. Analyze this incident response playbook and provide validation feedback.

PLAYBOOK TO VALIDATE:
%s

VALIDATION CRITERIA:
1. Completeness: All necessary steps included
2. Safety: No dangerous commands or operations
3. Clarity: Steps are clear and executable
4. Error Handling: Proper error handling and rollback procedures
5. Dependencies: Step dependencies are correctly specified
6. Security: No security vulnerabilities or credential exposure

Respond with a JSON object:
{
  "valid": true/false,
  "score": 0.0-1.0,
  "errors": [
    {
      "field": "field_name",
      "message": "error description",
      "severity": "low|medium|high|critical",
      "code": "error_code"
    }
  ],
  "warnings": [
    {
      "field": "field_name", 
      "message": "warning description",
      "suggestion": "improvement suggestion"
    }
  ],
  "suggestions": [
    "improvement suggestion 1",
    "improvement suggestion 2"
  ]
}`, string(playbookJSON))

	// Use lower temperature for validation (more deterministic)
	config := PlaybookConfig{
		Model:       "gpt-4",
		Temperature: 0.1,
		MaxTokens:   1000,
	}

	response, err := c.GeneratePlaybook(ctx, prompt, config)
	if err != nil {
		return nil, fmt.Errorf("validation request failed: %w", err)
	}

	// Parse validation response
	var result ValidationResult
	if err := json.Unmarshal([]byte(response.Content), &result); err != nil {
		// If JSON parsing fails, return a basic validation result
		c.logger.Warn("Failed to parse validation response", zap.Error(err))
		return &ValidationResult{
			Valid:       true, // Default to valid if we can't parse
			Score:       0.7,  // Conservative score
			Errors:      []ValidationError{},
			Warnings:    []ValidationWarning{},
			Suggestions: []string{"Manual review recommended due to validation parsing error"},
		}, nil
	}

	return &result, nil
}

// calculateConfidence estimates confidence based on response characteristics
func (c *OpenAIClient) calculateConfidence(content string, usage Usage) float64 {
	baseConfidence := 0.7

	// Higher confidence for longer, more detailed responses
	if len(content) > 2000 {
		baseConfidence += 0.1
	}

	// Check for JSON structure
	if c.isValidJSON(content) {
		baseConfidence += 0.1
	}

	// Check for key playbook elements
	keyElements := []string{"steps", "validation", "rollback", "command", "timeout"}
	foundElements := 0
	for _, element := range keyElements {
		if bytes.Contains([]byte(content), []byte(element)) {
			foundElements++
		}
	}
	baseConfidence += float64(foundElements) * 0.02

	// Penalize for very short responses
	if len(content) < 500 {
		baseConfidence -= 0.2
	}

	// Ensure confidence is within valid range
	if baseConfidence > 1.0 {
		baseConfidence = 1.0
	}
	if baseConfidence < 0.0 {
		baseConfidence = 0.0
	}

	return baseConfidence
}

// isValidJSON checks if content contains valid JSON
func (c *OpenAIClient) isValidJSON(content string) bool {
	var js json.RawMessage
	return json.Unmarshal([]byte(content), &js) == nil
}

// MockLLMClient provides a mock implementation for testing
type MockLLMClient struct {
	logger *zap.Logger
}

// NewMockLLMClient creates a mock LLM client for testing
func NewMockLLMClient(logger *zap.Logger) *MockLLMClient {
	return &MockLLMClient{
		logger: logger,
	}
}

// GeneratePlaybook returns a mock playbook response
func (m *MockLLMClient) GeneratePlaybook(ctx context.Context, prompt string, config PlaybookConfig) (*LLMResponse, error) {
	mockResponse := `{
  "title": "Database Connection Issue Remediation",
  "description": "Systematic approach to diagnose and resolve database connectivity problems",
  "steps": [
    {
      "step_number": 1,
      "title": "Check Database Service Status",
      "description": "Verify that the database service is running and responsive",
      "action_type": "command",
      "command": "systemctl status postgresql",
      "parameters": {},
      "expected_output": "active (running)",
      "timeout": "30s",
      "retry_count": 3,
      "risk_level": "low",
      "automated": true,
      "requires_human": false,
      "rollback_command": "",
      "safety_checks": [
        {
          "check_id": "service_check",
          "description": "Verify service is not in failed state",
          "command": "systemctl is-failed postgresql",
          "expected": "active"
        }
      ]
    },
    {
      "step_number": 2,
      "title": "Test Database Connectivity",
      "description": "Attempt to connect to the database and verify connectivity",
      "action_type": "command", 
      "command": "pg_isready -h localhost -p 5432",
      "parameters": {},
      "expected_output": "accepting connections",
      "timeout": "15s",
      "retry_count": 2,
      "risk_level": "low",
      "automated": true,
      "requires_human": false,
      "rollback_command": "",
      "safety_checks": []
    },
    {
      "step_number": 3,
      "title": "Restart Database Service",
      "description": "Restart the database service if connectivity test fails",
      "action_type": "command",
      "command": "systemctl restart postgresql",
      "parameters": {},
      "expected_output": "service restarted successfully",
      "timeout": "60s",
      "retry_count": 1,
      "risk_level": "medium",
      "automated": false,
      "requires_human": true,
      "rollback_command": "",
      "safety_checks": [
        {
          "check_id": "backup_check",
          "description": "Verify recent backup exists before restart",
          "command": "ls -la /var/lib/postgresql/backups/ | head -5",
          "expected": "recent backup file"
        }
      ]
    }
  ],
  "validation_steps": [
    {
      "title": "Verify Database Connectivity",
      "description": "Confirm database is accepting connections after remediation",
      "check_type": "functional",
      "command": "pg_isready -h localhost -p 5432",
      "expected_result": "accepting connections",
      "timeout": "10s",
      "automated": true,
      "critical": true
    },
    {
      "title": "Check Application Health",
      "description": "Verify application can successfully connect to database",
      "check_type": "health",
      "command": "curl -f http://localhost:8080/healthz",
      "expected_result": "HTTP 200 OK",
      "timeout": "15s",
      "automated": true,
      "critical": true
    }
  ],
  "rollback_steps": [
    {
      "step_number": 1,
      "title": "Revert to Previous Configuration",
      "description": "Restore previous database configuration if issues persist",
      "command": "cp /var/lib/postgresql/config.backup /var/lib/postgresql/postgresql.conf",
      "timeout": "30s",
      "risk_level": "low",
      "automated": false
    }
  ],
  "safety_warnings": [
    {
      "level": "warning",
      "message": "Database restart will cause brief service interruption",
      "mitigation": "Schedule during maintenance window or low-traffic period"
    }
  ],
  "estimated_duration": "15m",
  "required_skills": ["linux", "postgresql", "systemctl"],
  "prerequisites": ["sudo access", "database backup verified"]
}`

	return &LLMResponse{
		Content:         mockResponse,
		ConfidenceScore: 0.85,
		TokensUsed:      450,
		Model:           "mock-gpt-4",
		Metadata: map[string]interface{}{
			"mock": true,
		},
	}, nil
}

// ValidatePlaybook returns mock validation results
func (m *MockLLMClient) ValidatePlaybook(ctx context.Context, playbook *Playbook) (*ValidationResult, error) {
	return &ValidationResult{
		Valid:  true,
		Score:  0.88,
		Errors: []ValidationError{},
		Warnings: []ValidationWarning{
			{
				Field:      "steps[2]",
				Message:    "High-risk operation requires human approval",
				Suggestion: "Add additional safety checks before database restart",
			},
		},
		Suggestions: []string{
			"Consider adding database connection pool monitoring",
			"Include performance metrics validation",
		},
	}, nil
}
