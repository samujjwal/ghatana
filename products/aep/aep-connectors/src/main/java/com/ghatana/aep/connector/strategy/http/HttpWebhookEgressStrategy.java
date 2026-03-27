/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.http;

import com.ghatana.aep.connector.AbstractResilientConnector;
import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP webhook egress strategy — POSTs messages to an HTTP endpoint using the
 * Java built-in {@link HttpClient} with exponential-backoff retry.
 *
 * <p>Any 2xx response is treated as success. Non-2xx responses are treated as
 * retriable failures.
 *
 * @doc.type class
 * @doc.purpose HTTP webhook egress strategy with real HTTP I/O and retry
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class HttpWebhookEgressStrategy extends AbstractResilientConnector implements QueueProducerStrategy {

    private final HttpIngressConfig config;
    private volatile HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HttpWebhookEgressStrategy(HttpIngressConfig config) {
        super(config.retryConfig());
        this.config = config;
    }

    @Override
    public boolean send(QueueMessage message) {
        if (!running.get() || httpClient == null) {
            throw new IllegalStateException("HttpWebhookEgressStrategy is not started");
        }
        try {
            return withRetry("http.webhook.send", () -> {
                String url = buildUrl(config.endpoint(), config.httpPort(), config.path());
                HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method(config.method(), HttpRequest.BodyPublishers.ofString(message.getBody()))
                    .header("Content-Type", "application/json")
                    .timeout(config.readTimeout());
                // Propagate message headers as HTTP headers
                message.getHeaders().forEach(req::header);
                HttpResponse<Void> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.debug("Webhook delivered to {} — status={}", url, response.statusCode());
                    return true;
                }
                throw new RuntimeException("Webhook returned non-2xx status: " + response.statusCode());
            });
        } catch (Exception e) {
            log.error("Failed to deliver webhook to {}: {}", config.endpoint(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(ioExecutor, () -> {
            if (running.compareAndSet(false, true)) {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.connectionTimeout())
                    .build();
                log.info("HttpWebhookEgressStrategy started — endpoint={}", config.endpoint());
            }
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(ioExecutor, () -> {
            running.set(false);
            httpClient = null;
            log.info("HttpWebhookEgressStrategy stopped");
            return null;
        });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private static String buildUrl(String endpoint, int port, String path) {
        // If endpoint already has scheme/port, don't append port again
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String p = path.startsWith("/") ? path : "/" + path;
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            return "http://" + base + ":" + port + p;
        }
        return base + p;
    }
}
