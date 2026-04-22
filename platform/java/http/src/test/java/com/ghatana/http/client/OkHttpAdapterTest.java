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

@DisplayName("OkHttpAdapter Tests [GH-90000]")
class OkHttpAdapterTest extends EventloopTestBase {

    private MockWebServer server;
    private OkHttpAdapter adapter;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        server = new MockWebServer(); // GH-90000
        server.start(); // GH-90000

        OkHttpClient client = new OkHttpClient.Builder().build(); // GH-90000
        RateLimiter limiter = RateLimiter.create(100.0); // high limit for tests // GH-90000
        executor = Executors.newSingleThreadExecutor(); // GH-90000
        adapter = new OkHttpAdapter(client, limiter, new NoopMetricsCollector(), executor); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (executor != null) { // GH-90000
            executor.shutdownNow(); // GH-90000
        }
        server.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("Should post JSON and return response body [GH-90000]")
    void shouldPostJsonAndReturnResponseBody() { // GH-90000
        // Given
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}")); // GH-90000
        String url = server.url("/test [GH-90000]").toString();
        String json = "{\"hello\":\"world\"}";

        // When
        String resp = runPromise(() -> adapter.postJson(url, json)); // GH-90000

        // Then
        assertThat(resp).contains("ok [GH-90000]");
    }

    @Test
    @DisplayName("Should send headers and receive response [GH-90000]")
    void shouldSendHeadersAndReceiveResponse() { // GH-90000
        // Given
        server.enqueue(new MockResponse().setResponseCode(200).setBody("success [GH-90000]"));
        String url = server.url("/hdr [GH-90000]").toString();
        String json = "{}";

        // When
        String resp = runPromise(() -> adapter.postJson(url, json, Map.of("X-Test", "1"))); // GH-90000

        // Then
        assertThat(resp).isEqualTo("success [GH-90000]");
    }
}
