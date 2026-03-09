package integration

import (
	"fmt"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestServiceUnavailability(t *testing.T) {
	env := SetupTestEnvironment(t)
	defer env.Cleanup()
	// Skip if no local server is running
	SkipIfServerDown(t, env)

	// Simulate server unavailability
	t.Run("server unavailable", func(t *testing.T) {
		env.EnableFailure("http_server_down")
		defer env.DisableFailure("http_server_down")

		// Try to make a request to the server
		req := TestRequest{
			Method:      http.MethodGet,
			URL:         "http://localhost:8080/health",
			ExpectError: true, // We expect the request to fail
		}

		_, _ = DoTestRequest(t, req)
	})

	// Simulate database unavailability
	t.Run("database unavailable", func(t *testing.T) {
		env.EnableFailure("database_down")
		defer env.DisableFailure("database_down")

		// Make a request that requires database access
		req := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/data",
			ExpectCode: http.StatusInternalServerError,
		}

		resp, body := DoTestRequest(t, req)
		if resp != nil {
			AssertErrorResponse(t, resp, body, http.StatusInternalServerError)
		}
	})
}

func TestNetworkPartition(t *testing.T) {
	env := SetupTestEnvironment(t)
	defer env.Cleanup()
	// Skip if no local server is running
	SkipIfServerDown(t, env)

	t.Run("database partition", func(t *testing.T) {
		env.EnableFailure("network_partition_db")
		defer env.DisableFailure("network_partition_db")

		req := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/data",
			ExpectCode: http.StatusServiceUnavailable,
		}

		resp, body := DoTestRequest(t, req)
		if resp != nil {
			// Verify the response contains an appropriate error message
			AssertErrorResponse(t, resp, body, http.StatusServiceUnavailable)
			assert.Contains(t, strings.ToLower(string(body)), "database", 
				"Error message should mention database")
		}
	})

	t.Run("cache partition", func(t *testing.T) {
		// First request - should hit the database and populate the cache
		req1 := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/cached-data",
			ExpectCode: http.StatusOK,
		}
		resp1, _ := DoTestRequest(t, req1)
		require.NotNil(t, resp1, "First request should succeed")

		// Second request - should be served from cache
		req2 := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/cached-data",
			ExpectCode: http.StatusOK,
		}
		resp2, _ := DoTestRequest(t, req2)
		require.NotNil(t, resp2, "Second request should be served from cache")

		// Enable cache partition failure
		env.EnableFailure("network_partition_cache")
		defer env.DisableFailure("network_partition_cache")

		// Third request - should still work because it can fall back to the database
		req3 := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/cached-data",
			ExpectCode: http.StatusOK,
		}
		resp3, _ := DoTestRequest(t, req3)
		require.NotNil(t, resp3, "Third request should fall back to database")

		// Verify in logs or metrics that fallback occurred
		// (implementation depends on your logging/metrics system)
	})
}

func TestResourceExhaustion(t *testing.T) {
	env := SetupTestEnvironment(t)
	defer env.Cleanup()
	// Skip if no local server is running
	SkipIfServerDown(t, env)

	t.Run("memory pressure", func(t *testing.T) {
		env.EnableFailure("memory_pressure")
		defer env.DisableFailure("memory_pressure")

		req := TestRequest{
			Method:     http.MethodGet,
			URL:        "http://localhost:8080/api/large-dataset",
			ExpectCode: http.StatusInternalServerError, // or http.StatusServiceUnavailable
			Validate: func(t *testing.T, resp *http.Response, body []byte) {
				// Verify the response contains an appropriate error message
				assert.Contains(t, strings.ToLower(string(body)), "memory", 
					"Error message should mention memory")
			},
		}

		_, _ = DoTestRequest(t, req)
	})

	t.Run("connection pool exhaustion", func(t *testing.T) {
		env.EnableFailure("connection_pool_exhausted")
		defer env.DisableFailure("connection_pool_exhausted")

		// Use a wait group to make concurrent requests
		var wg sync.WaitGroup
		errCh := make(chan error, 10) // Buffer for errors
		reqCount := 50

		// Make more concurrent requests than the connection pool size
		for i := 0; i < reqCount; i++ {
			wg.Add(1)
			go func(id int) {
				defer wg.Done()
				
				req := TestRequest{
					Method:     http.MethodGet,
					URL:        "http://localhost:8080/api/data",
					ExpectCode: 0, // We'll check the status code manually
				}

				resp, _ := DoTestRequest(t, req)
				if resp != nil {
					// We accept 200 (success) or 503 (service unavailable)
					if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusServiceUnavailable {
						errCh <- fmt.Errorf("unexpected status code: %d", resp.StatusCode)
					}
				}
			}(i)
		}

		// Wait for all requests to complete or timeout
		done := make(chan struct{})
		go func() {
			wg.Wait()
			close(done)
		}()

		select {
		case <-done:
			// All requests completed
		case <-time.After(30 * time.Second):
			t.Fatal("Test timed out waiting for requests to complete")
		}

		// Check for errors
		close(errCh)
		var errs []error
		for err := range errCh {
			errs = append(errs, err)
		}

		// We expect some errors due to connection pool exhaustion
		assert.Greater(t, len(errs), 0, 
			"Expected some errors due to connection pool exhaustion, got %d", len(errs))
	})
}

func TestConcurrentFailures(t *testing.T) {
	env := SetupTestEnvironment(t)
	defer env.Cleanup()
	// Skip if no local server is running
	SkipIfServerDown(t, env)

	t.Run("multiple concurrent failures", func(t *testing.T) {
		// Enable multiple failure modes
		env.EnableFailure("database_slow")
		env.EnableFailure("cache_unavailable")
		defer func() {
			env.DisableFailure("database_slow")
			env.DisableFailure("cache_unavailable")
		}()

		// Use a wait group to make concurrent requests
		var wg sync.WaitGroup
		errCh := make(chan error, 20) // Buffer for errors
		successCount := int32(0)
		errorCount := int32(0)
		concurrentRequests := 20

		// Make concurrent requests with different failure modes
		for i := 0; i < concurrentRequests; i++ {
			wg.Add(1)
			go func(id int) {
				defer wg.Done()
				
				// Alternate between different endpoints
				url := "http://localhost:8080/api/data"
				if id%2 == 0 {
					url = "http://localhost:8080/api/cached-data"
				}

				req := TestRequest{
					Method:     http.MethodGet,
					URL:        url,
					ExpectCode: 0, // We'll check status codes manually
				}

				resp, _ := DoTestRequest(t, req)
				if resp != nil {
					// Check response status
					if resp.StatusCode >= 200 && resp.StatusCode < 300 {
						atomic.AddInt32(&successCount, 1)
					} else if resp.StatusCode >= 500 {
						errCh <- fmt.Errorf("server error from %s: %d", url, resp.StatusCode)
						atomic.AddInt32(&errorCount, 1)
					}
				} else {
					errCh <- fmt.Errorf("request to %s failed", url)
					atomic.AddInt32(&errorCount, 1)
				}
			}(i)
		}

		// Wait for all requests to complete or timeout
		done := make(chan struct{})
		go func() {
			wg.Wait()
			close(done)
		}()

		select {
		case <-done:
			// All requests completed
		case <-time.After(60 * time.Second):
			t.Fatal("Test timed out waiting for requests to complete")
		}

		// Check results
		t.Logf("Completed with %d successes and %d errors", successCount, errorCount)
		
		// We expect some requests to succeed and some to fail
		assert.True(t, successCount > 0, "Expected at least some requests to succeed")
		assert.True(t, errorCount > 0, "Expected at least some requests to fail")

		// Check for unexpected errors
		close(errCh)
		var errs []error
		for err := range errCh {
			errs = append(errs, err)
		}

		// Log all errors for debugging
		for _, err := range errs {
			t.Logf("Error: %v", err)
		}
	})
}
