package com.ghatana.pipeline.registry.web;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Web layer tracing configuration placeholder.
 *
 * <p>Purpose: Placeholder configuration class for web-layer tracing integration.
 * Reserved for future implementation of HTTP request tracing with OpenTelemetry.
 * Currently a no-op as Spring WebMVC is not used.</p>
 *
 * @doc.type class
 * @doc.purpose Placeholder for web-layer OpenTelemetry tracing configuration
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class TracingWebConfig {

    @SuppressWarnings("unused")
    private final OpenTelemetry openTelemetry;

    public TracingWebConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }
}
