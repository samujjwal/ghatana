/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Distributed Tracing Configuration
 */
package com.ghatana.yappc.services.lifecycle.config;

import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.observability.TracingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures OpenTelemetry distributed tracing for the YAPPC Lifecycle Service.
 *
 * <p>Wraps {@link TracingManager} initialization and exposes named {@link TracingProvider}
 * scopes for the key lifecycle instrumentation points:
 * <ul>
 *   <li>{@code lifecycle.phase} — phase transition spans</li>
 *   <li>{@code lifecycle.approval} — human approval gate spans</li>
 *   <li>{@code lifecycle.ai} — AI integration call spans</li>
 *   <li>{@code lifecycle.http} — inbound HTTP request spans</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Tracing is configured via environment variables:
 * <ul>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT} — OTLP collector endpoint (default: {@code http://localhost:4317})</li>
 *   <li>{@code YAPPC_SERVICE_VERSION} — service version string injected into resource attributes (default: {@code 1.0.0})</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OpenTelemetry tracing configuration for YAPPC Lifecycle Service
 * @doc.layer product
 * @doc.pattern Configuration, Factory
 */
public final class LifecycleTracingConfig {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleTracingConfig.class);

    static final String SERVICE_NAME = "yappc-lifecycle";

    private final TracingManager tracingManager;

    private LifecycleTracingConfig(TracingManager tracingManager) {
        this.tracingManager = tracingManager;
    }

    /**
     * Creates the tracing configuration from environment variables.
     *
     * <p>When {@code OTEL_EXPORTER_OTLP_ENDPOINT} is not set, a warning is logged and a
     * no-op tracing manager is used. This means the service starts successfully even when
     * no OTLP collector is configured (e.g. in local dev or unit tests).
     */
    public static LifecycleTracingConfig fromEnvironment() {
        String endpoint = System.getenv().getOrDefault(
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");
        String version = System.getenv().getOrDefault("YAPPC_SERVICE_VERSION", "1.0.0");

        logger.info("Initializing lifecycle tracing: service={} version={} endpoint={}",
                SERVICE_NAME, version, endpoint);

        TracingManager manager;
        try {
            manager = TracingManager.createDefault(SERVICE_NAME, version, endpoint);
            logger.info("OpenTelemetry tracing initialized — exporting to {}", endpoint);
        } catch (Exception e) {
            logger.warn("Failed to initialize OTLP tracing ({}), falling back to no-op: {}",
                    endpoint, e.getMessage());
            manager = TracingManager.createNoOp();
        }

        return new LifecycleTracingConfig(manager);
    }

    /** Returns a tracing provider for lifecycle phase transition spans. */
    public TracingProvider phaseTracer() {
        return tracingManager.getProvider("lifecycle.phase");
    }

    /** Returns a tracing provider for human approval gate spans. */
    public TracingProvider approvalTracer() {
        return tracingManager.getProvider("lifecycle.approval");
    }

    /** Returns a tracing provider for AI integration call spans. */
    public TracingProvider aiTracer() {
        return tracingManager.getProvider("lifecycle.ai");
    }

    /** Returns a tracing provider for inbound HTTP spans. */
    public TracingProvider httpTracer() {
        return tracingManager.getProvider("lifecycle.http");
    }

    /** Returns the underlying {@link TracingManager} for span scope access. */
    public TracingManager tracingManager() {
        return tracingManager;
    }
}
