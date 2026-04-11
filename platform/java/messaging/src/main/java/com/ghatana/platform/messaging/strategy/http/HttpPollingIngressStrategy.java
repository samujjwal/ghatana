/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging.strategy.http;

import com.ghatana.platform.messaging.AbstractResilientConnector;
import com.ghatana.platform.messaging.strategy.QueueConsumerStrategy;
import io.activej.promise.Promise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * HTTP polling ingress strategy — periodically GETs a configured endpoint and
 * passes any non-empty 200 response body to the message handler.
 *
 * @doc.type class
 * @doc.purpose HTTP polling ingress strategy with real HTTP polling
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class HttpPollingIngressStrategy extends AbstractResilientConnector implements QueueConsumerStrategy {

    private final HttpIngressConfig config;
    private final Consumer<String> messageHandler;
    private final Duration pollInterval;
    private volatile HttpClient httpClient;
    private volatile Thread pollingThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Construct with a message handler and configurable poll interval.
     *
     * @param config         HTTP ingress configuration
     * @param messageHandler handler called with each non-empty response body
     * @param pollInterval   how often to poll the endpoint
     */
    public HttpPollingIngressStrategy(HttpIngressConfig config, Consumer<String> messageHandler, Duration pollInterval) {
        super(config.retryConfig());
        this.config = config;
        this.messageHandler = messageHandler;
        this.pollInterval = pollInterval;
    }

    /** Construct with a no-op handler and 5-second poll interval. */
    public HttpPollingIngressStrategy(HttpIngressConfig config) {
        this(config, body -> {}, Duration.ofSeconds(5));
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (running.compareAndSet(false, true)) {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.connectionTimeout())
                    .build();

                pollingThread = Thread.ofVirtual().name("http-ingress-poller").start(() -> {
                    while (running.get()) {
                        try {
                            String url = buildUrl(config.endpoint(), config.httpPort(), config.path());
                            HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .timeout(config.readTimeout())
                                .build();
                            HttpResponse<String> response = httpClient.send(
                                request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() == 200 && !response.body().isBlank()) {
                                messageHandler.accept(response.body());
                            }
                        } catch (Exception e) {
                            if (running.get()) {
                                log.warn("HTTP polling error for endpoint={}: {}",
                                    config.endpoint(), e.getMessage());
                            }
                        }
                        try {
                            Thread.sleep(pollInterval.toMillis());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                log.info("HttpPollingIngressStrategy started — endpoint={} interval={}",
                    config.endpoint(), pollInterval);
            }
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running.set(false);
            Thread t = pollingThread;
            if (t != null) t.interrupt();
            httpClient = null;
            log.info("HttpPollingIngressStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private static String buildUrl(String endpoint, int port, String path) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String p = path.startsWith("/") ? path : "/" + path;
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            return "http://" + base + ":" + port + p;
        }
        return base + p;
    }
}
