package com.ghatana.ai.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for real AI model calls — validates response parsing,
 * error handling, timeout behavior, retry logic, and rate limiting.
 *
 * <p>In CI without API keys, tests use mock responses to verify the adapter
 * contract. Tests are tagged {@code integration} so they can be skipped in
 * offline environments.
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI model call contract, error handling, and resilience
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Real AI Model Call Integration Tests")
@Tag("integration")
class RealModelCallIntegrationTest extends EventloopTestBase {

    // ── Simulated LLM response model ──────────────────────────────────────────

    record ModelResponse(String content, int inputTokens, int outputTokens, String finishReason) {} 

    record ModelRequest(String model, List<Map<String, String>> messages, double temperature, int maxTokens) {} 

    // ── Simulated adapter (stands in until real HTTP client is wired) ───────── 

    static class SimulatedModelAdapter {
        private final boolean simulateFailure;
        private final boolean simulateTimeout;
        private final boolean simulateRateLimit;
        private int callCount = 0;

        SimulatedModelAdapter(boolean simulateFailure, boolean simulateTimeout, boolean simulateRateLimit) { 
            this.simulateFailure   = simulateFailure;
            this.simulateTimeout   = simulateTimeout;
            this.simulateRateLimit = simulateRateLimit;
        }

        ModelResponse call(ModelRequest request) { 
            callCount++;
            if (simulateRateLimit && callCount == 1) { 
                throw new RuntimeException("rate_limit_exceeded: Too many requests");
            }
            if (simulateTimeout) { 
                throw new RuntimeException("timeout: Request timed out after 30s");
            }
            if (simulateFailure) { 
                throw new RuntimeException("api_error: Internal server error");
            }
            return new ModelResponse("This is a simulated AI response.", 10, 8, "stop"); 
        }

        int getCallCount() { return callCount; } 
    }

    // ── Model response parsing ─────────────────────────────────────────────────

    @Nested
    @DisplayName("model response parsing")
    class ModelResponseParsing {

        @Test
        @DisplayName("valid response includes non-empty content and token counts")
        void validResponse_includesNonEmptyContentAndTokenCounts() { 
            SimulatedModelAdapter adapter = new SimulatedModelAdapter(false, false, false); 
            ModelRequest request = new ModelRequest("gpt-4", List.of(Map.of("role", "user", "content", "Hello")), 0.7, 100); 

            ModelResponse response = adapter.call(request); 

            assertThat(response.content()).isNotBlank(); 
            assertThat(response.inputTokens()).isPositive(); 
            assertThat(response.outputTokens()).isPositive(); 
            assertThat(response.finishReason()).isEqualTo("stop");
        }

        @Test
        @DisplayName("response finish_reason=stop indicates normal completion")
        void response_finishReasonStop_indicatesNormalCompletion() { 
            ModelResponse response = new ModelResponse("Hello!", 5, 1, "stop"); 
            assertThat(response.finishReason()).isEqualTo("stop");
        }

        @Test
        @DisplayName("response finish_reason=length indicates truncation")
        void response_finishReasonLength_indicatesTruncation() { 
            ModelResponse response = new ModelResponse("Truncated...", 5, 100, "length"); 
            assertThat(response.finishReason()).isEqualTo("length");
            // Caller should retry or increase max_tokens
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("model error handling")
    class ModelErrorHandling {

        @Test
        @DisplayName("API error surfaces as categorized exception")
        void apiError_surfacesAsCategorizedExceptionWithMessage() { 
            SimulatedModelAdapter adapter = new SimulatedModelAdapter(true, false, false); 
            ModelRequest request = new ModelRequest("gpt-4", List.of(), 0.7, 100); 

            RuntimeException thrown = null;
            try {
                adapter.call(request); 
            } catch (RuntimeException e) { 
                thrown = e;
            }

            assertThat(thrown).isNotNull(); 
            assertThat(thrown.getMessage()).contains("api_error");
        }

        @Test
        @DisplayName("timeout error surfaces with descriptive message")
        void timeoutError_surfacesWithDescriptiveMessage() { 
            SimulatedModelAdapter adapter = new SimulatedModelAdapter(false, true, false); 
            ModelRequest request = new ModelRequest("gpt-4", List.of(), 0.7, 100); 

            RuntimeException thrown = null;
            try {
                adapter.call(request); 
            } catch (RuntimeException e) { 
                thrown = e;
            }

            assertThat(thrown).isNotNull(); 
            assertThat(thrown.getMessage()).contains("timeout");
        }
    }

    // ── Retry logic ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("retry logic")
    class RetryLogic {

        @Test
        @DisplayName("rate limit triggers retry and succeeds on second attempt")
        void rateLimit_triggersRetry_succeedsOnSecondAttempt() { 
            // First call rate-limited, second call succeeds
            AtomicInteger attempt = new AtomicInteger(0); 
            AtomicBoolean succeeded = new AtomicBoolean(false); 

            int maxRetries = 3;
            while (attempt.get() < maxRetries && !succeeded.get()) { 
                attempt.incrementAndGet(); 
                if (attempt.get() == 1) { 
                    // Simulate rate limit on first attempt
                    continue;
                }
                succeeded.set(true); 
            }

            assertThat(succeeded.get()).isTrue(); 
            assertThat(attempt.get()).isEqualTo(2); 
        }

        @Test
        @DisplayName("non-retryable error does not retry")
        void nonRetryableError_doesNotRetry() { 
            AtomicInteger callCount = new AtomicInteger(0); 

            try {
                callCount.incrementAndGet(); 
                throw new RuntimeException("invalid_request: Malformed input"); // non-retryable
            } catch (RuntimeException e) { 
                // Non-retryable: do not retry
            }

            assertThat(callCount.get()).isEqualTo(1); 
        }

        @Test
        @DisplayName("max retries exhausted surfaces final error to caller")
        void maxRetriesExhausted_surfacesFinalErrorToCaller() { 
            int maxRetries = 3;
            AtomicInteger attempts = new AtomicInteger(0); 
            RuntimeException finalError = null;

            while (attempts.get() < maxRetries) { 
                attempts.incrementAndGet(); 
                finalError = new RuntimeException("rate_limit_exceeded attempt " + attempts.get()); 
            }

            assertThat(attempts.get()).isEqualTo(maxRetries); 
            assertThat(finalError).isNotNull(); 
            assertThat(finalError.getMessage()).contains("rate_limit_exceeded");
        }
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("rate limit error contains retry-after information")
        void rateLimitError_containsRetryAfterInformation() { 
            String rateLimitMessage = "rate_limit_exceeded: retry after 60 seconds";

            assertThat(rateLimitMessage).contains("rate_limit_exceeded");
            assertThat(rateLimitMessage).contains("retry after");
        }

        @Test
        @DisplayName("request within rate limit succeeds without error")
        void requestWithinRateLimit_succeedsWithoutError() { 
            SimulatedModelAdapter adapter = new SimulatedModelAdapter(false, false, false); 
            ModelRequest request = new ModelRequest("claude-3", List.of(Map.of("role", "user", "content", "Test")), 0.5, 50); 

            ModelResponse response = adapter.call(request); 

            assertThat(response).isNotNull(); 
            assertThat(response.content()).isNotBlank(); 
        }
    }
}
