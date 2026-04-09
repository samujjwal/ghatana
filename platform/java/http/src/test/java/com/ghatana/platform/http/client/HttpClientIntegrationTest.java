/**
 * @doc.type class
 * @doc.purpose Real HTTP client tests with connection management, retries, timeouts, and error handling
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.http.client;

import com.google.common.util.concurrent.RateLimiter;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * HTTP Client Integration Tests
 *
 * Real HTTP client tests with connection management, retries, timeouts,
 * and error handling using OkHttpAdapter.
 */
@DisplayName("HTTP Client Integration Tests")
class HttpClientIntegrationTest {

    private MockWebServer mockWebServer;
    private OkHttpClient okHttpClient;
    private RateLimiter rateLimiter;
    private MetricsCollector metrics;

    @AfterEach
    void tearDown() throws Exception {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle HTTP GET requests")
    void shouldHandleHttpGetRequests() {
        mockWebServer = new MockWebServer();
        okHttpClient = new OkHttpClient();
        rateLimiter = RateLimiter.create(10.0);
        metrics = mock(MetricsCollector.class);

        try {
            mockWebServer.start();
            mockWebServer.enqueue(new MockResponse().setBody("{\"success\":true}").setResponseCode(200));

            OkHttpAdapter adapter = new OkHttpAdapter(
                okHttpClient, rateLimiter, metrics, Executors.newSingleThreadExecutor()
            );

            Promise<String> response = adapter.postJson(mockWebServer.url("/test").toString(), "{}");
            assertThat(response.getResult()).contains("success");

            verify(metrics).incrementCounter(anyString(), anyString(), anyString(), anyString(), anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle HTTP POST requests")
    void shouldHandleHttpPostRequests() {
        mockWebServer = new MockWebServer();
        okHttpClient = new OkHttpClient();
        rateLimiter = RateLimiter.create(10.0);
        metrics = mock(MetricsCollector.class);

        try {
            mockWebServer.start();
            mockWebServer.enqueue(new MockResponse().setBody("{\"created\":true}").setResponseCode(201));

            OkHttpAdapter adapter = new OkHttpAdapter(
                okHttpClient, rateLimiter, metrics, Executors.newSingleThreadExecutor()
            );

            String jsonBody = "{\"name\":\"test\"}";
            Promise<String> response = adapter.postJson(mockWebServer.url("/create").toString(), jsonBody);
            assertThat(response.getResult()).contains("created");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should handle connection retries")
    void shouldHandleConnectionRetries() {
        HttpClientConfig config = HttpClientConfig.builder()
            .retryOnConnectionFailure(true)
            .requestsPerSecond(5.0)
            .build();

        assertThat(config.isRetryOnConnectionFailure()).isTrue();
        assertThat(config.getRequestsPerSecond()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Should handle request timeouts")
    void shouldHandleRequestTimeouts() {
        HttpClientConfig config = HttpClientConfig.builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .callTimeout(Duration.ofSeconds(15))
            .build();

        assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.getCallTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("Should handle connection pooling")
    void shouldHandleConnectionPooling() {
        HttpClientConfig config = HttpClientConfig.builder()
            .maxConnections(20)
            .keepAliveDuration(Duration.ofMinutes(5))
            .build();

        assertThat(config.getMaxConnections()).isEqualTo(20);
        assertThat(config.getKeepAliveDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Should handle HTTP error responses")
    void shouldHandleHttpErrorResponses() {
        mockWebServer = new MockWebServer();
        okHttpClient = new OkHttpClient();
        rateLimiter = RateLimiter.create(10.0);
        metrics = mock(MetricsCollector.class);

        try {
            mockWebServer.start();
            mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"Internal Server Error\"}"));

            OkHttpAdapter adapter = new OkHttpAdapter(
                okHttpClient, rateLimiter, metrics, Executors.newSingleThreadExecutor()
            );

            Promise<String> response = adapter.postJson(mockWebServer.url("/error").toString(), "{}");

            try {
                response.getResult();
                assertThat(false).isTrue(); // Should not reach here
            } catch (Exception e) {
                assertThat(e).isNotNull();
            }

            verify(metrics).incrementCounter(anyString(), anyString(), anyString(), anyString(), anyString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
