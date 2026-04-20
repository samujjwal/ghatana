/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.http;

import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpServer;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Manages HTTP server binding to a port with proper lifecycle management.
 *
 * <p><b>Purpose</b><br>
 * Wraps an ActiveJ {@link HttpServer} and manages its lifecycle including port binding,
 * startup, shutdown, and health checks. Integrates with the kernel lifecycle framework.
 *
 * <p><b>Configuration</b><br>
 * - Host: Default "0.0.0.0" (bind to all interfaces), configurable
 * - Port: Required, typically from environment variable or config
 * - Servlet: The route handler to mount on the server
 *
 * <p><b>Lifecycle</b><br>
 * - On start: Binds to the configured port and begins accepting connections
 * - On stop: Closes all connections and releases the port
 * - Health check: Returns true when server is bound and accepting connections
 *
 * <p><b>Example Usage</b><br>
 * <pre>{@code
 *   int port = 8080;
 *   AsyncServlet routes = RoutingServlet.create()
 *       .with(HttpMethod.GET, "/", myHandler)
 *       .build();
 *   HttpServerBinding binding = new HttpServerBinding(eventloop, routes, port);
 *   Promise<Void> startPromise = binding.start();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP server lifecycle management with port binding
 * @doc.layer platform
 * @doc.pattern Lifecycle, Adapter
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class HttpServerBinding {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerBinding.class);
    private static final String DEFAULT_HOST = "0.0.0.0";

    private final Eventloop eventloop;
    private final AsyncServlet servlet;
    private final String host;
    private final int port;
    private volatile HttpServer server;
    private volatile Thread eventloopThread;
    private volatile boolean started = false;

    /**
     * Creates an HTTP server binding with default host (0.0.0.0) and the given port.
     *
     * @param eventloop the ActiveJ eventloop; must not be null
     * @param servlet   the HTTP servlet/router; must not be null
     * @param port      the port to bind to; must be between 1-65535
     * @throws IllegalArgumentException if port is invalid
     * @throws NullPointerException     if eventloop or servlet is null
     */
    public HttpServerBinding(Eventloop eventloop, AsyncServlet servlet, int port) {
        this(eventloop, servlet, DEFAULT_HOST, port);
    }

    /**
     * Creates an HTTP server binding with the given host and port.
     *
     * @param eventloop the ActiveJ eventloop; must not be null
     * @param servlet   the HTTP servlet/router; must not be null
     * @param host      the host address to bind to; must not be blank
     * @param port      the port to bind to; must be between 1-65535
     * @throws IllegalArgumentException if host is blank or port is invalid
     * @throws NullPointerException     if eventloop or servlet is null
     */
    public HttpServerBinding(Eventloop eventloop, AsyncServlet servlet, String host, int port) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.servlet = Objects.requireNonNull(servlet, "servlet cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");

        if (host.isBlank()) {
            throw new IllegalArgumentException("host cannot be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }

        this.port = port;
    }

    public Promise<Void> start() {
        if (started) {
            LOG.warn("HttpServerBinding already started on {}:{}", host, port);
            return Promise.complete();
        }

        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            server = HttpServer.builder(eventloop, servlet)
                .withListenAddress(address)
                .build();

            eventloop.keepAlive(true);
            eventloopThread = new Thread(eventloop::run, "http-binding-" + port);
            eventloopThread.start();

            eventloop.execute(() -> {
                try {
                    server.listen();
                    started = true;
                    LOG.info("HTTP server listening on {}:{}", host, port);
                } catch (Exception exception) {
                    LOG.error("Failed to bind HTTP server to {}:{}", host, port, exception);
                }
            });

            return Promise.complete();
        } catch (Exception exception) {
            LOG.error("Failed to create HTTP server binding for {}:{}", host, port, exception);
            return Promise.ofException(exception);
        }
    }

    public Promise<Void> stop() {
        if (!started || server == null) {
            return Promise.complete();
        }

        try {
            server.close();
            eventloop.keepAlive(false);
            started = false;
            server = null;
            eventloopThread = null;
            LOG.info("HTTP server stopped on {}:{}", host, port);
            return Promise.complete();
        } catch (Exception exception) {
            LOG.error("Unexpected error while stopping HTTP server", exception);
            started = false;
            server = null;
            eventloopThread = null;
            return Promise.ofException(exception);
        }
    }

    public boolean isHealthy() {
        return started && server != null;
    }

    public String getName() {
        return "http-server-" + host + "-" + port;
    }

    /**
     * Returns the host address this server is bound to.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port this server is bound to.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the underlying ActiveJ HttpServer, or null if not started.
     */
    public HttpServer getServer() {
        return server;
    }

    /**
     * Returns whether the server is currently bound and accepting connections.
     */
    public boolean isStarted() {
        return started;
    }
}
