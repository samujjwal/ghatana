package com.ghatana.platform.http.client;

import com.ghatana.platform.observability.MetricsCollector;
import com.google.common.util.concurrent.RateLimiter;
import io.activej.promise.Promise;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Simple OkHttp adapter that centralizes outbound HTTP calls.
 *
 * <p><b>Purpose</b><br>
 * Provides a single, testable HTTP client wrapper used by product modules.
 * It enforces rate limiting (Guava RateLimiter) and records metrics via
 * `core/observability`'s `MetricsCollector`.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OkHttpAdapter client = new OkHttpAdapter(okHttpClient, rateLimiter, metrics);
 * Promise<String> resp = client.postJson("https://api.openai.com/v1/chat/completions", jsonBody);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized HTTP client adapter using OkHttp
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class OkHttpAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OkHttpAdapter.class);

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metrics;
    private final java.util.concurrent.Executor executor;

    public OkHttpAdapter(OkHttpClient client, RateLimiter rateLimiter, MetricsCollector metrics, java.util.concurrent.Executor executor) {
        this.client = Objects.requireNonNull(client);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        this.metrics = Objects.requireNonNull(metrics);
        this.executor = Objects.requireNonNull(executor);
    }

    public Promise<String> postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, Map.of());
    }

    public Promise<String> postJson(String url, String jsonBody, Map<String, String> headers) {
        // Use Promise.ofBlocking to perform blocking I/O off the event loop
        return Promise.ofBlocking(executor, () -> {
            rateLimiter.acquire(); // enforce rate limit

            Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON));

            if (headers != null && !headers.isEmpty()) {
                rb.headers(Headers.of(headers));
            }

            Request request = rb.build();

            try (Response response = client.newCall(request).execute()) {
                String status = String.valueOf(response.code());
                
                // Record metrics
                metrics.incrementCounter("http.client.request.count", "url", url, "status", status);

                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    logger.error("HTTP request failed: {} {}", response.code(), body);
                    throw new IOException("HTTP " + response.code() + ": " + body);
                }

                return response.body() != null ? response.body().string() : "";
            }
        });
    }


}
