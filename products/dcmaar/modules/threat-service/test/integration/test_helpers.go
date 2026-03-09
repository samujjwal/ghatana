package integration

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestRequest represents a test HTTP request
type TestRequest struct {
	Method      string
	URL         string
	Body        io.Reader
	Headers     map[string]string
	ExpectError bool
	ExpectCode  int
	Validate    func(t *testing.T, resp *http.Response, body []byte)
}

// DoTestRequest executes an HTTP request and performs common assertions
func DoTestRequest(t *testing.T, reqDef TestRequest) (*http.Response, []byte) {
	req, err := http.NewRequest(reqDef.Method, reqDef.URL, reqDef.Body)
	require.NoError(t, err, "Failed to create request")

	// Set default headers if not provided
	if reqDef.Headers == nil {
		reqDef.Headers = map[string]string{
			"Content-Type": "application/json",
			"Accept":       "application/json",
		}
	}

	// Set request headers
	for k, v := range reqDef.Headers {
		req.Header.Set(k, v)
	}

	// Execute the request
	client := &http.Client{
		Timeout: 30 * time.Second,
	}

	resp, err := client.Do(req)

	// Handle expected errors
	if reqDef.ExpectError {
		assert.Error(t, err, "Expected request to fail but it succeeded")
		return nil, nil
	}

	require.NoError(t, err, "Request failed unexpectedly")
	defer resp.Body.Close()

	// Read response body
	body, err := io.ReadAll(resp.Body)
	require.NoError(t, err, "Failed to read response body")

	// Check status code if expected
	if reqDef.ExpectCode != 0 {
		assert.Equal(t, reqDef.ExpectCode, resp.StatusCode, 
			"Unexpected status code. Response: %s", string(body))
	}

	// Run custom validation if provided
	if reqDef.Validate != nil {
		reqDef.Validate(t, resp, body)
	}

	return resp, body
}

// ParseJSONResponse parses a JSON response into the target struct
func ParseJSONResponse(t *testing.T, body []byte, target interface{}) {
	err := json.Unmarshal(body, target)
	require.NoError(t, err, "Failed to parse JSON response: %s", string(body))
}

// WaitForCondition waits for a condition to be true with a timeout
func WaitForCondition(t *testing.T, timeout time.Duration, condition func() bool, msg string) bool {
	t.Helper()
	timer := time.NewTimer(timeout)
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-timer.C:
			assert.Fail(t, "Condition not met within timeout", msg)
			return false
		case <-ticker.C:
			if condition() {
				return true
			}
		}
	}
}

// AssertErrorResponse checks if the response is an error response with the expected status code
func AssertErrorResponse(t *testing.T, resp *http.Response, body []byte, expectedCode int) {
	t.Helper()
	assert.Equal(t, expectedCode, resp.StatusCode, "Unexpected status code")
	
	// Check if the response is JSON
	contentType := resp.Header.Get("Content-Type")
	assert.True(t, strings.Contains(contentType, "application/json"), 
		"Expected JSON response, got %s", contentType)

	// Parse the error response
	var errResp struct {
		Error   string `json:"error"`
		Message string `json:"message"`
	}
	err := json.Unmarshal(body, &errResp)
	require.NoError(t, err, "Failed to parse error response")

	// Check that we have an error message
	assert.NotEmpty(t, errResp.Error, "Error field should not be empty")
	assert.NotEmpty(t, errResp.Message, "Error message should not be empty")
}

// IsServerUp performs a fast health check against the default server
// It returns true if the server responds with any HTTP status (connection established).
func (e *TestEnvironment) IsServerUp() bool {
	client := &http.Client{Timeout: 500 * time.Millisecond}
	// Try common health endpoints
	endpoints := []string{
		"http://localhost:8080/health",
		"http://localhost:8080/healthz",
	}
	for _, url := range endpoints {
		resp, err := client.Get(url)
		if err == nil {
			resp.Body.Close()
			return true
		}
	}
	return false
}

// SkipIfServerDown skips the test if the HTTP server isn't running locally.
func SkipIfServerDown(t *testing.T, env *TestEnvironment) {
	t.Helper()
	if !env.IsServerUp() {
		t.Skip("HTTP server not running on http://localhost:8080. Start it with 'make run-server' or use the mock server.")
	}
}
