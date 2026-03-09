package com.ghatana.http.client;

import com.ghatana.platform.http.client.OkHttpAdapter;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.google.common.util.concurrent.RateLimiter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OkHttpAdapter Tests")
class OkHttpAdapterTest extends EventloopTestBase {

    private MockWebServer server;
    private OkHttpAdapter adapter;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new OkHttpClient.Builder().build();
        RateLimiter limiter = RateLimiter.create(100.0); // high limit for tests
        executor = Executors.newSingleThreadExecutor();
        adapter = new OkHttpAdapter(client, limiter, new NoopMetricsCollector(), executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
        }
        server.shutdown();
    }

    @Test
    @DisplayName("Should post JSON and return response body")
    void shouldPostJsonAndReturnResponseBody() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        String url = server.url("/test").toString();
        String json = "{\"hello\":\"world\"}";

        // When
        String resp = runPromise(() -> adapter.postJson(url, json));

        // Then
        assertThat(resp).contains("ok");
    }

    @Test
    @DisplayName("Should send headers and receive response")
    void shouldSendHeadersAndReceiveResponse() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(200).setBody("success"));
        String url = server.url("/hdr").toString();
        String json = "{}";

        // When
        String resp = runPromise(() -> adapter.postJson(url, json, Map.of("X-Test", "1")));

        // Then
        assertThat(resp).isEqualTo("success");
    }
}
