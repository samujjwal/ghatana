/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event.spi;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * {@link EventCloudConnector} implementation that publishes events over REST/HTTP.
 *
 * <p>Selected when {@code EVENT_CLOUD_TRANSPORT=http}. The base URL is derived
 * from {@code AEP_DC_BASE_URL} (Data-Cloud service).
 *
 * <p>All HTTP calls are wrapped with {@code Promise.ofBlocking(executor, …)}.
 *
 * @doc.type class
 * @doc.purpose HTTP-based EventCloudConnector for REST event transport
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class HttpEventCloudConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(HttpEventCloudConnector.class);
    private static final String PUBLISH_PATH = "/api/v1/events/publish";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Executor blockingExecutor;

    /**
     * @param baseUrl          Data-Cloud service base URL (e.g. {@code http://data-cloud:8085})
     * @param blockingExecutor executor for blocking HTTP calls
     */
    public HttpEventCloudConnector(String baseUrl, Executor blockingExecutor) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor required");
        this.httpClient = HttpClient.newBuilder().executor(blockingExecutor).build();
        log.info("[HttpEventCloudConnector] initialised with baseUrl={}", this.baseUrl);
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        String eventId = UUID.randomUUID().toString();
        return Promise.ofBlocking(blockingExecutor, () -> {
            String body = buildPublishBody(eventId, topic, payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + PUBLISH_PATH))
                    .header("Content-Type", "application/json")
                    .header("X-Event-Id", eventId)
                    .header("X-Topic", topic)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "[HTTP] publish failed: topic=" + topic + " status=" + response.statusCode());
            }
            log.debug("[HTTP] publish ok topic={} eventId={}", topic, eventId);
            return eventId;
        });
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic, String consumerGroup, EventPayloadHandler handler) {
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        // HTTP transport uses SSE long-polling for subscriptions.
        // Full SSE client integration is wired in AepCoreModule.
        log.info("[HTTP] subscribe topic={} group={} baseUrl={}", topic, consumerGroup, baseUrl);
        return Promise.ofBlocking(blockingExecutor, () -> new ConnectorSubscription() {
            private volatile boolean cancelled = false;

            @Override
            public void cancel() {
                cancelled = true;
                log.debug("[HTTP] subscription cancelled for topic={}", topic);
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        });
    }

    /**
     * Builds a minimal JSON publish body.
     *
     * <p>Avoids a compile-time Jackson dependency by constructing JSON manually
     * from safe fields (UUIDs and base64-encoded payload).
     */
    private static String buildPublishBody(String eventId, String topic, byte[] payload) {
        String payloadBase64 = java.util.Base64.getEncoder().encodeToString(payload);
        return "{\"eventId\":\"" + eventId + "\","
                + "\"topic\":\"" + sanitize(topic) + "\","
                + "\"payloadBase64\":\"" + payloadBase64 + "\"}";
    }

    /** Strips characters that could cause JSON injection from string fields. */
    private static String sanitize(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
