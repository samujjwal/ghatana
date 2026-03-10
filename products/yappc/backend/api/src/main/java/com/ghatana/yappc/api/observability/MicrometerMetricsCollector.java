/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.observability;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.ghatana.platform.observability.SimpleMetricsCollector;

/**
 * Production MetricsCollector backed by a Prometheus {@link PrometheusMeterRegistry}.
 *
 * <p><b>Purpose</b><br>
 * Replaces {@link com.ghatana.platform.observability.NoopMetricsCollector} in production with
 * a registry that exports metrics in the Prometheus text format via {@link #scrape()}.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MicrometerMetricsCollector collector = MicrometerMetricsCollector.create();
 * // wire into services…
 * // expose via HTTP:
 * String prometheusText = collector.scrape();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Created once in {@code ApiApplication.createMetricsCollector()} and passed to
 * {@code ProductionModule}. The same instance is referenced by the {@code /metrics} scrape
 * endpoint without going through the DI container.
 *
 * @doc.type class
 * @doc.purpose Prometheus-backed MetricsCollector for production observability
 * @doc.layer product
 * @doc.pattern Facade, Adapter
 */
public class MicrometerMetricsCollector extends SimpleMetricsCollector {

  private final PrometheusMeterRegistry prometheusRegistry;

  /**
   * Private constructor — use {@link #create()} factory method.
   *
   * @param prometheusRegistry the Prometheus-backed Micrometer registry
   */
  private MicrometerMetricsCollector(PrometheusMeterRegistry prometheusRegistry) {
    super(prometheusRegistry);
    this.prometheusRegistry = prometheusRegistry;
  }

  /**
   * Creates a new {@link MicrometerMetricsCollector} with a default {@link PrometheusMeterRegistry}.
   *
   * @return ready-to-use collector
   */
  public static MicrometerMetricsCollector create() {
    PrometheusMeterRegistry pr = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    return new MicrometerMetricsCollector(pr);
  }

  /**
   * Returns the Prometheus text-format scrape payload.
   *
   * <p>This string should be served at {@code GET /metrics} with content-type
   * {@code text/plain; version=0.0.4; charset=utf-8}.
   *
   * @return Prometheus scrape output
   */
  public String scrape() {
    return prometheusRegistry.scrape();
  }
}
