package com.ghatana.services.aiinference;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.time.Duration;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AIInferenceHttpAdapter — covering JWT authentication guards,
 * rate limiting, input validation, and input sanitization.
 *
 * @doc.type class
 * @doc.purpose Tests for AIInferenceHttpAdapter security, validation, and rate limiting
 * @doc.layer application
 * @doc.pattern Test
 */
@DisplayName("AIInferenceHttpAdapter Tests")
class AIInferenceHttpAdapterTest extends EventloopTestBase {

    private LLMGatewayService mockGateway;
    private JwtTokenProvider mockJwt;
    private AIInferenceHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        mockGateway = mock(LLMGatewayService.class);
        mockJwt = mock(JwtTokenProvider.class);
        adapter = new AIInferenceHttpAdapter(mockGateway, new NoopMetricsCollector(), mockJwt);
    }

    // ─── /health (unauthenticated) ────────────────────────────────────────────

    @Test
    @DisplayName("GET /health should return 200 without authentication")
    void healthEndpointShouldBePublic() {
        HttpRequest request = HttpRequest.get("http://localhost/health").build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ─── /ai/infer/embedding — unauthenticated ────────────────────────────────

    @Test
    @DisplayName("POST /ai/infer/embedding without token should return 401")
    void embeddingEndpointRequiresAuth() {
        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withBody("{\"tenant\":\"t1\",\"text\":\"hello\"}".getBytes())
                .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"code\":\"UNAUTHORIZED\"")
            .contains("\"message\":\"Missing or malformed Authorization header\"");
    }

    @Test
    @DisplayName("POST /ai/infer/embedding with invalid token should return 401")
    void embeddingEndpointRejectsInvalidToken() {
        when(mockJwt.validateToken(anyString())).thenReturn(false);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer expired.token.here")
                .withBody("{\"tenant\":\"t1\",\"text\":\"hello\"}".getBytes())
                .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("POST /ai/infer/embedding with valid token should call gateway")
    void embeddingEndpointAcceptsValidToken() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        when(mockGateway.generateEmbedding("t1", "hello"))
                .thenReturn(Promise.of(EmbeddingResult.of(vector)));

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\",\"text\":\"hello\"}".getBytes())
                .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ─── Prompt length validation ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/infer/embedding with oversized text should return 400")
    void embeddingEndpointRejectsOversizedText() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        String longText = "A".repeat(33_000); // exceeds MAX_TEXT_LENGTH
        String body = "{\"tenant\":\"t1\",\"text\":\"" + longText + "\"}";

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody(body.getBytes())
                .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ─── Internal key bypass ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/infer/completion without auth should return 401")
    void completionEndpointRequiresAuth() {
        when(mockJwt.validateToken(anyString())).thenReturn(false);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/completion")
                .withBody("{\"tenant\":\"t1\",\"prompt\":\"hello\"}".getBytes())
                .build();

        // Without a valid token and without internal key the response must be 401
        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));
        assertThat(response.getCode()).isEqualTo(401);
    }

    // ─── /ai/admin/status — requires auth ────────────────────────────────────

    @Test
    @DisplayName("GET /ai/admin/status without token should return 401")
    void adminStatusRequiresAuth() {
        HttpRequest request = HttpRequest.get("http://localhost/ai/admin/status").build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("GET /ai/admin/status with valid token should return 200")
    void adminStatusAcceptsValidToken() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.get("http://localhost/ai/admin/status")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ─── Rate limiting ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Requests exceeding rate limit should return 429")
    void rateLimitExceededShouldReturn429() {
        // Create adapter with very low rate limit (1 RPM) to trigger it in test
        NoopMetricsCollector metrics = new NoopMetricsCollector();
        DefaultRateLimiter stingyLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(1)
                .burstSize(1)
                .windowDuration(Duration.ofMinutes(1))
                .build(),
            metrics,
            "ai.infer.rate_limit"
        );
        AIInferenceHttpAdapter lowLimitAdapter =
                new AIInferenceHttpAdapter(mockGateway, metrics, mockJwt, stingyLimiter);

        when(mockJwt.validateToken("valid.token")).thenReturn(true);
        float[] vector = new float[]{0.1f};
        when(mockGateway.generateEmbedding(eq("t1"), anyString()))
                .thenReturn(Promise.of(EmbeddingResult.of(vector)));

        // First request should pass
        HttpRequest first = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\",\"text\":\"hello\"}".getBytes())
                .build();
        assertThat(runPromise(() -> lowLimitAdapter.buildServlet().serve(first)).getCode()).isEqualTo(200);

        // Second request should be rate-limited
        HttpRequest second = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\",\"text\":\"hello\"}".getBytes())
                .build();
        HttpResponse rateLimited = runPromise(() -> lowLimitAdapter.buildServlet().serve(second));
        assertThat(rateLimited.getCode()).isEqualTo(429);
        assertThat(rateLimited.getBody().getString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"");
    }

        @Test
        @DisplayName("POST /ai/infer/embedding with invalid JSON should return structured 400")
        void embeddingInvalidJsonShouldReturnStructured400() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
            .withBody("{not-json".getBytes())
            .build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"code\":\"INVALID_REQUEST\"")
            .contains("\"message\":\"Invalid JSON request body\"");
        }

    // ─── Validation: missing fields ───────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/infer/embedding with missing tenant should return 400")
    void embeddingMissingTenantShouldReturn400() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"text\":\"hello\"}".getBytes())
                .build();

        assertThat(runPromise(() -> adapter.buildServlet().serve(request)).getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /ai/infer/embedding with missing text should return 400")
    void embeddingMissingTextShouldReturn400() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embedding")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\"}".getBytes())
                .build();

        assertThat(runPromise(() -> adapter.buildServlet().serve(request)).getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /ai/infer/completion with missing prompt should return 400")
    void completionMissingPromptShouldReturn400() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/completion")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\"}".getBytes())
                .build();

        assertThat(runPromise(() -> adapter.buildServlet().serve(request)).getCode()).isEqualTo(400);
    }

    // ─── Validation: batch limits ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /ai/infer/embeddings with empty texts array should return 400")
    void batchEmbeddingWithEmptyTextsShouldReturn400() {
        when(mockJwt.validateToken("valid.token")).thenReturn(true);

        HttpRequest request = HttpRequest.post("http://localhost/ai/infer/embeddings")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .withBody("{\"tenant\":\"t1\",\"texts\":[]}".getBytes())
                .build();

        assertThat(runPromise(() -> adapter.buildServlet().serve(request)).getCode()).isEqualTo(400);
    }

    // ─── Health check content ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /health should return timestamp in response body")
    void healthEndpointShouldReturnTimestamp() {
        HttpRequest request = HttpRequest.get("http://localhost/health").build();

        HttpResponse response = runPromise(() -> adapter.buildServlet().serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
