package com.ghatana.platform.observability.http.handlers;

import com.ghatana.platform.observability.MetricsRegistry;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * HTTP handler for metrics endpoints.
 *
 * <p>Handles:
 * <ul>
 *   <li>GET /metrics - Prometheus scrape endpoint (if using PrometheusMeterRegistry)</li>
 *   <li>GET /metrics/json - JSON summary of registered meters</li>
 * </ul>
 * </p>
 *
 * <p>This is the canonical metrics handler for the platform. Products should
 * register routes using this handler instead of creating their own MetricsController
 * implementations.</p>
 *
 * @author Ghatana Platform Team
 * @since 2.4.0
 * @doc.type class
 * @doc.purpose Canonical HTTP handler for Prometheus metrics scrape endpoint
 * @doc.layer observability
 * @doc.pattern Handler, ActiveJ HTTP Handler
 */
public class MetricsHandler {

    private static final Logger logger = LoggerFactory.getLogger(MetricsHandler.class);
    private static final String CONTENT_TYPE_TEXT = "text/plain; version=0.0.4; charset=utf-8";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final MetricsRegistry metricsRegistry;
    private final Instant startTime;

    /**
     * Constructs a MetricsHandler.
     *
     * @param metricsRegistry the platform metrics registry (not null)
     * @throws NullPointerException if metricsRegistry is null
     */
    public MetricsHandler(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "MetricsRegistry cannot be null");
        this.startTime = Instant.now();
    }

    /**
     * Handles GET /metrics — Prometheus scrape endpoint.
     *
     * <p>If the underlying {@link MeterRegistry} is a {@link PrometheusMeterRegistry},
     * returns the Prometheus text format. Otherwise, returns a plain text summary
     * of registered meter names.</p>
     *
     * @param request the HTTP request
     * @return promise of HTTP response with Prometheus metrics
     */
    public Promise<HttpResponse> handleScrape(HttpRequest request) {
        try {
            MeterRegistry meterRegistry = metricsRegistry.getMeterRegistry();

            if (meterRegistry instanceof PrometheusMeterRegistry prometheusRegistry) {
                String scrape = prometheusRegistry.scrape();
                return Promise.of(
                        HttpResponse.ok200()
                                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_TEXT)
                                .withBody(scrape.getBytes())
                                .build()
                );
            }

            // Fallback: list meter names if not Prometheus
            StringBuilder sb = new StringBuilder();
            sb.append("# Registered meters (non-Prometheus registry)\n");
            meterRegistry.getMeters().forEach(meter ->
                    sb.append(meter.getId().getName()).append('\n')
            );

            return Promise.of(
                    HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_TEXT)
                            .withBody(sb.toString().getBytes())
                            .build()
            );
        } catch (Exception e) {
            logger.error("Failed to scrape metrics", e);
            return Promise.of(
                    HttpResponse.ofCode(503)
                            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_TEXT)
                            .withBody(("# Metrics unavailable: " + e.getMessage()).getBytes())
                            .build()
            );
        }
    }

    /**
     * Handles GET /metrics/json — JSON summary of metrics.
     *
     * <p>Returns a JSON object with basic metrics metadata including
     * meter count, uptime, and registry type.</p>
     *
     * @param request the HTTP request
     * @return promise of HTTP response with JSON metrics summary
     */
    public Promise<HttpResponse> handleJsonSummary(HttpRequest request) {
        try {
            MeterRegistry meterRegistry = metricsRegistry.getMeterRegistry();
            long uptimeSeconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();

            String json = String.format(
                    "{\"status\":\"up\",\"meters\":%d,\"uptimeSeconds\":%d,\"registryType\":\"%s\"}",
                    meterRegistry.getMeters().size(),
                    uptimeSeconds,
                    meterRegistry.getClass().getSimpleName()
            );

            return Promise.of(
                    HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                            .withBody(json.getBytes())
                            .build()
            );
        } catch (Exception e) {
            logger.error("Failed to generate metrics summary", e);
            return Promise.of(
                    HttpResponse.ofCode(503)
                            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                            .withBody("{\"status\":\"error\",\"message\":\"Metrics unavailable\"}".getBytes())
                            .build()
            );
        }
    }
}
