/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Prometheus {@code /metrics} scrape endpoint (STORY-K06-006).
 *
 * <p>Returns the complete Prometheus text-format exposition produced by a shared
 * {@link PrometheusMeterRegistry}. Mount this servlet at {@code /metrics} in the
 * gateway or observability sidecar.
 *
 * <h3>Included metrics</h3>
 * <ul>
 *   <li>Standard JVM process metrics (CPU, heap, GC, threads, file descriptors).</li>
 *   <li>All finance kernel meters registered via {@link LedgerMetrics} and
 *       {@link FinanceMetricNames} constants.</li>
 *   <li>Any additional meters bound to the shared registry at runtime.</li>
 * </ul>
 *
 * <h3>Access control notice</h3>
 * <p>The {@code /metrics} endpoint should be protected at the network or gateway level
 * (e.g., internal-only, or behind a sidecar with mTLS). This handler does not enforce
 * authentication itself to remain composable.
 *
 * @doc.type class
 * @doc.purpose Prometheus /metrics scrape endpoint (K06-006)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class MetricsEndpointHandler implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(MetricsEndpointHandler.class);

    /**
     * MIME type specified in the Prometheus text format specification.
     * Include charset to help clients that parse before the body is fully read.
     */
    private static final String CONTENT_TYPE_PROMETHEUS =
        "text/plain; version=0.0.4; charset=utf-8";

    private final PrometheusMeterRegistry registry;

    /**
     * @param registry the application-wide Prometheus registry populated with all meters
     */
    public MetricsEndpointHandler(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    // ──────────────────────────────────────────────────────────────────────
    // AsyncServlet
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        try {
            String exposition = registry.scrape();
            log.debug("[/metrics] Scraped {} bytes", exposition.length());
            return Promise.of(
                HttpResponse.ok200()
                    .withHeader(io.activej.http.HttpHeaders.of("Content-Type"), CONTENT_TYPE_PROMETHEUS)
                    .withBody(exposition.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            log.error("[/metrics] Scrape failed: {}", e.getMessage(), e);
            return Promise.of(HttpResponse.ofCode(500).withPlainText("Metrics collection failed"));
        }
    }
}
