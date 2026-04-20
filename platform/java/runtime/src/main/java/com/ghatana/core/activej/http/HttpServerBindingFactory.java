/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.http;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Factory for creating HTTP server bindings with environment-aware configuration.
 *
 * <p><b>Purpose</b><br>
 * Simplifies the creation of {@link HttpServerBinding} instances by handling:
 * - Port resolution from environment variables or defaults
 * - Host configuration (default or explicit)
 * - Validation of configuration
 * - Consistent error reporting
 *
 * <p><b>Configuration Sources</b><br>
 * Ports are resolved in this priority order:
 * <ol>
 *   <li>Explicit builder argument: {@code withPort(8080)}</li>
 *   <li>Environment variable: e.g., {@code PHR_HTTP_PORT=8080}</li>
 *   <li>Default: {@code 8080}</li>
 * </ol>
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   HttpServerBinding binding = new HttpServerBindingFactory()
 *       .withServiceName("phr")
 *       .withPort(8081)
 *       .build(eventloop, servlet);
 *
 *   // Alternative: read from environment
 *   HttpServerBinding binding = new HttpServerBindingFactory()
 *       .withServiceName("finance")  // Uses FINANCE_HTTP_PORT env var
 *       .build(eventloop, servlet);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Builder for HTTP server bindings with environment configuration
 * @doc.layer platform
 * @doc.pattern Builder, Factory
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class HttpServerBindingFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerBindingFactory.class);
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";

    private String serviceName;
    private String host = DEFAULT_HOST;
    private Integer port;

    /**
     * Sets the service name for environment variable resolution.
     * Used to construct environment variable names like {@code <SERVICE_NAME>_HTTP_PORT}.
     *
     * @param serviceName the service name (e.g., "phr", "finance")
     * @return this builder for fluent chaining
     */
    public HttpServerBindingFactory withServiceName(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName cannot be null");
        return this;
    }

    /**
     * Sets an explicit port for the server binding.
     * If set, this overrides environment variable resolution.
     *
     * @param port the port number (1-65535)
     * @return this builder for fluent chaining
     * @throws IllegalArgumentException if port is invalid
     */
    public HttpServerBindingFactory withPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
        this.port = port;
        return this;
    }

    /**
     * Sets an explicit host address for the server binding.
     * Default is "0.0.0.0" (bind to all interfaces).
     *
     * @param host the host address (e.g., "localhost", "127.0.0.1", "0.0.0.0")
     * @return this builder for fluent chaining
     * @throws NullPointerException if host is null
     */
    public HttpServerBindingFactory withHost(String host) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        return this;
    }

    /**
     * Builds the HTTP server binding with the configured settings.
     *
     * <p>Port resolution:
     * <ol>
     *   <li>Explicit {@code withPort()} if set</li>
     *   <li>Environment variable {@code <SERVICE_NAME>_HTTP_PORT} if service name is set</li>
     *   <li>Default {@code 8080}</li>
     * </ol>
     *
     * @param eventloop the ActiveJ eventloop
     * @param servlet   the HTTP servlet/router
     * @return configured {@link HttpServerBinding}
     * @throws NullPointerException if eventloop or servlet is null
     * @throws IllegalArgumentException if resolved port is invalid
     */
    public HttpServerBinding build(Eventloop eventloop, AsyncServlet servlet) {
        Objects.requireNonNull(eventloop, "eventloop cannot be null");
        Objects.requireNonNull(servlet, "servlet cannot be null");

        int resolvedPort = resolvePort();
        LOG.debug("Building HTTP server binding for service={}, host={}, port={}", 
            serviceName != null ? serviceName : "unknown", host, resolvedPort);

        return new HttpServerBinding(eventloop, servlet, host, resolvedPort);
    }

    /**
     * Resolves the port to use based on configuration and environment variables.
     */
    private int resolvePort() {
        // 1. Explicit port takes precedence
        if (port != null) {
            LOG.debug("Using explicit port: {}", port);
            return port;
        }

        // 2. Try environment variable if service name is set
        if (serviceName != null && !serviceName.isBlank()) {
            String envVarName = serviceName.toUpperCase() + "_HTTP_PORT";
            Optional<String> envPort = readEnvironmentVariable(envVarName);
            if (envPort.isPresent()) {
                try {
                    int resolvedPort = Integer.parseInt(envPort.get());
                    LOG.debug("Using port from environment variable {}: {}", envVarName, resolvedPort);
                    return resolvedPort;
                } catch (NumberFormatException exception) {
                    LOG.warn("Invalid port value in environment variable {}: {}. Using default.", 
                        envVarName, envPort.get());
                }
            }
        }

        // 3. Use default
        LOG.debug("Using default port: {}", DEFAULT_PORT);
        return DEFAULT_PORT;
    }

    /**
     * Reads an environment variable value safely.
     */
    private Optional<String> readEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return Optional.of(value);
        }
        return Optional.empty();
    }
}
