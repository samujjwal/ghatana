package com.ghatana.platform.observability;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * HTTP metrics filter for ActiveJ servers recording request latency and counts.
 *
 * <p>HttpMetricsFilter wraps AsyncServlet handlers to record HTTP metrics including
 * request latency (timer), request counts (counter), and status codes. Integrates with
 * Micrometer for metrics collection and export to Prometheus.</p>
 *
 * <p><b>Recorded Metrics:</b></p>
 * <ul>
 *   <li><b>ingress.request.latency</b> (Timer) - Request duration with p50, p95, p99 percentiles</li>
 *   <li><b>ingress.requests</b> (Counter) - Request count</li>
 *   <li><b>Tags:</b> path (request path), method (HTTP method), status (response code or "EX" for exceptions)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * HttpMetricsFilter metricsFilter = new HttpMetricsFilter(meterRegistry);
 * AsyncServlet servlet = metricsFilter.measure(request -> {
 *     return handleRequest(request);
 * });
 * }</pre>
 *
 * <p><b>Latency Measurement:</b></p>
 * <ul>
 *   <li>Starts timer on request arrival (System.nanoTime())</li>
 *   <li>Records duration on response completion or exception</li>
 *   <li>Captured in nanoseconds, converted to Duration for Micrometer</li>
 * </ul>
 *
 * <p><b>Exception Handling:</b></p>
 * <ul>
 *   <li>Synchronous exceptions (in delegate.serve()) - recorded with status="EX"</li>
 *   <li>Asynchronous exceptions (in Promise) - recorded with status="EX" via whenException</li>
 *   <li>Exceptions are re-thrown after recording</li>
 * </ul>
 *
 * <p><b>Performance:</b></p>
 * <ul>
 *   <li>Minimal overhead (< 1µs per request for timer creation and recording)</li>
 *   <li>Async Promise integration (no blocking)</li>
 *   <li>Tag cardinality: Consider path normalization for high-cardinality endpoints</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via MeterRegistry.</p>
 *
 * @see Meters for static meter creation utilities
 * @see MetricsRegistry for EventCloud metrics taxonomy
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type HTTP Filter (Metrics Interceptor)
 * @purpose HTTP request metrics collection for latency and counts
 * @pattern Decorator pattern, Filter pattern, Interceptor pattern
 * @responsibility HTTP request metrics recording, latency measurement, exception tracking
 * @usage Wrap AsyncServlet with measure(): `filter.measure(delegate)`
 * @examples See class-level JavaDoc for AsyncServlet wrapping example
 * @testing Test metrics recording, latency measurement, status codes, exception handling
 * @notes ActiveJ Promise integration; records both sync and async exceptions; use path normalization for high-cardinality endpoints
 *
 * @doc.type class
 * @doc.purpose HTTP request metrics filter recording latency and counts via Micrometer
 * @doc.layer platform
 * @doc.pattern Filter
 */
public final class HttpMetricsFilter {
    private final MeterRegistry registry;

    public HttpMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    public AsyncServlet measure(AsyncServlet delegate) {
        return request -> {
            try {
                return measureRequest(delegate, request);
            } catch (Exception e) {
                // If delegate throws synchronously, still record and return failed promise
                record(request, null, 0L);
                return Promise.ofException(e);
            }
        };
    }

    private Promise<HttpResponse> measureRequest(AsyncServlet delegate, HttpRequest request) throws Exception {
        long startNanos = System.nanoTime();
        return delegate.serve(request)
                .map(response -> {
                    record(request, response, System.nanoTime() - startNanos);
                    return response;
                })
                .whenException(e -> record(request, null, System.nanoTime() - startNanos));
    }

    private void record(HttpRequest request, HttpResponse response, long durationNanos) {
        String path = safe(request.getPath());
        String method = request.getMethod().name();
        String status = response == null ? "EX" : String.valueOf(response.getCode());
        Timer timer = Meters.timer(registry, "ingress.request.latency", "path", path, "method", method, "status", status);
        timer.record(Duration.ofNanos(durationNanos));
        Meters.counter(registry, "ingress.requests", "path", path, "method", method, "status", status).increment();
    }

    private static String safe(String s) {
        return s == null ? "/" : s;
    }
}
