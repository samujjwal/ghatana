/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import io.activej.bytebuf.ByteBuf;
import io.activej.csp.queue.ChannelBuffer;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for Server-Sent Events (SSE) streaming and heartbeat management.
 *
 * @doc.type class
 * @doc.purpose SSE real-time event streaming infrastructure
 * @doc.layer product
 * @doc.pattern Service
 */
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final Map<String, List<ChannelBuffer<ByteBuf>>> sseSubscribers = new java.util.HashMap<>();
    @Nullable
    private Eventloop eventloop;

    /**
     * Initializes the SSE controller with the event loop reference and starts the heartbeat.
     * Must be called from the event-loop thread.
     */
    public void init(Eventloop eventloop) {
        this.eventloop = eventloop;
        scheduleHeartbeat();
    }

    /**
     * Handles an incoming SSE stream connection.
     *
     * <p>Authentication is enforced by {@code AepAuthFilter} before this method is reached.
     * This method validates that the resolved tenant ID is syntactically valid and non-empty.
     */
    public Promise<HttpResponse> handleSseStream(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);

        // Validate tenant — auth filter already verified identity; ensure tenant is present and safe
        if (tenantId == null || tenantId.isBlank() || "default".equals(tenantId)) {
            log.warn("SSE connection rejected: missing or default tenant ID");
            String errorBody = "{\"error\":\"Forbidden\",\"message\":\"Tenant ID is required for SSE subscription\"}";
            return Promise.of(HttpResponse.ofCode(403)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
                .withBody(errorBody.getBytes(StandardCharsets.UTF_8))
                .build());
        }

        if (!tenantId.matches("^[a-zA-Z0-9._-]{1,128}$")) {
            log.warn("SSE connection rejected: invalid tenant ID format: {}", tenantId);
            String errorBody = "{\"error\":\"Bad Request\",\"message\":\"Invalid tenant ID format\"}";
            return Promise.of(HttpResponse.ofCode(400)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/json"))
                .withBody(errorBody.getBytes(StandardCharsets.UTF_8))
                .build());
        }

        ChannelBuffer<ByteBuf> queue = new ChannelBuffer<>(8, 256);
        sseSubscribers.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(queue);

        byte[] connected = ("retry: 5000\nevent: connected\ndata: {\"service\":\"aep\",\"ts\":\""
            + Instant.now() + "\"}\n\n").getBytes(StandardCharsets.UTF_8);
        queue.put(ByteBuf.wrapForReading(connected));

        List<ChannelBuffer<ByteBuf>> tenantQueues = sseSubscribers.get(tenantId);
        ChannelBuffer<ByteBuf> finalQueue = queue;

        return Promise.of(
            HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/event-stream"))
                .withHeader(HttpHeaders.of("Cache-Control"), HttpHeaderValue.of("no-cache"))
                .withHeader(HttpHeaders.of("X-Accel-Buffering"), HttpHeaderValue.of("no"))
                .withHeader(HttpHeaders.of("Connection"), HttpHeaderValue.of("keep-alive"))
                .withBodyStream(
                    finalQueue.getSupplier()
                        .withEndOfStream(eos ->
                            eos.whenComplete(() -> tenantQueues.remove(finalQueue)))
                )
                .build()
        );
    }

    /**
     * Pushes an SSE event to all active subscribers of the given tenant.
     * MUST be called from the event-loop thread.
     */
    public void publishSseTo(String tenantId, String type, Map<String, Object> data) {
        List<ChannelBuffer<ByteBuf>> queues = sseSubscribers.get(tenantId);
        if (queues == null || queues.isEmpty()) return;
        String msg;
        try {
            String json = HttpHelper.mapper().writeValueAsString(data);
            msg = "event: " + type + "\ndata: " + json + "\n\n";
        } catch (Exception e) {
            log.warn("SSE serialization failed for type={}: {}", type, e.getMessage());
            return;
        }
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        for (int i = queues.size() - 1; i >= 0; i--) {
            ChannelBuffer<ByteBuf> q = queues.get(i);
            if (q.isSaturated()) {
                queues.remove(i);
                q.closeEx(new java.io.IOException(
                    "SSE publisher: subscriber removed (backpressure)"));
            } else {
                final int idx = i;
                q.put(ByteBuf.wrapForReading(bytes))
                    .whenException(e -> {
                        log.debug("SSE subscriber disconnected during publish: {}",
                            e.getMessage());
                        queues.remove(idx < queues.size() ? idx : queues.size() - 1);
                    });
            }
        }
    }

    /**
     * Thread-safe broadcast: schedules publishing on the event-loop thread.
     */
    public void broadcastSseEvent(String tenantId, String type, Map<String, Object> data) {
        if (eventloop == null) return;
        eventloop.execute(() -> {
            if ("*".equals(tenantId)) {
                sseSubscribers.keySet().forEach(t -> publishSseTo(t, type, data));
            } else {
                publishSseTo(tenantId, type, data);
            }
        });
    }

    private void scheduleHeartbeat() {
        if (eventloop == null) return;
        eventloop.delay(30_000L, () -> {
            byte[] heartbeat = ("event: heartbeat\ndata: {\"ts\":\"" + Instant.now() + "\"}\n\n")
                .getBytes(StandardCharsets.UTF_8);
            sseSubscribers.forEach((tenant, queues) -> {
                for (int i = queues.size() - 1; i >= 0; i--) {
                    ChannelBuffer<ByteBuf> q = queues.get(i);
                    if (q.isSaturated()) {
                        queues.remove(i);
                        q.closeEx(new java.io.IOException(
                            "SSE heartbeat: subscriber removed (backpressure)"));
                    } else {
                        q.put(ByteBuf.wrapForReading(heartbeat))
                            .whenException(e -> {
                                log.debug("SSE subscriber lost during heartbeat: {}",
                                    e.getMessage());
                                queues.remove(q);
                            });
                    }
                }
            });
            scheduleHeartbeat();
        });
    }
}
