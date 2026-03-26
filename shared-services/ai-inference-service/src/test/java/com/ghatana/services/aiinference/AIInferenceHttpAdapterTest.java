package com.ghatana.services.aiinference;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AIInferenceHttpAdapter — specifically the JWT authentication guards
 * added to fix FINDING-002 (security vulnerabilities in shared services).
 *
 * @doc.type class
 * @doc.purpose Tests for JWT auth enforcement on AI inference endpoints
 * @doc.layer application
 * @doc.pattern Test
 */
@DisplayName("AIInferenceHttpAdapter Security Tests")
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
}
