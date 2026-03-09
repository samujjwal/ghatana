package com.ghatana.refactorer.server.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghatana.refactorer.server.jobs.JobProgressStreamer;
import com.ghatana.refactorer.server.jobs.JobProgressStreamer.Event;
import io.activej.http.HttpRequest;
import io.activej.http.IWebSocket;
import io.activej.http.IWebSocket.Message;
import io.activej.promise.Promise;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * WebSocket handler for bidirectional job progress and control. Handles WebSocket connections at

 * /ws/jobs/{id}.

 *

 * @doc.type class

 * @doc.purpose Manage long-lived WebSocket sessions that push structured job progress messages.

 * @doc.layer product

 * @doc.pattern WebSocket Adapter

 */

public final class ProgressWebSocketHandler {
    private static final Logger logger = LogManager.getLogger(ProgressWebSocketHandler.class);

    private final JobProgressStreamer progressStreamer;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public ProgressWebSocketHandler(JobProgressStreamer progressStreamer) {
        this.progressStreamer = progressStreamer;
    }

    public void handle(IWebSocket webSocket) {
        HttpRequest request = webSocket.getRequest();
        String jobId;
        try {
            jobId = extractJobId(request);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid WebSocket path: {}", request.getPath());
            sendAndClose(webSocket, errorPayload("invalid_path", e.getMessage()));
            return;
        }

        Optional<List<Event>> eventsOpt = progressStreamer.progressEvents(jobId);
        if (eventsOpt.isEmpty()) {
            logger.warn("Job not found for WebSocket stream: {}", jobId);
            sendAndClose(webSocket, errorPayload("not_found", "Job not found: " + jobId));
            return;
        }

        logger.info("WebSocket stream established for job: {}", jobId);
        List<Event> events = eventsOpt.get();
        streamEvents(webSocket, events.iterator())
                .whenComplete(
                        ($, error) -> {
                            if (error != null) {
                                logger.error("WebSocket error for job {}", jobId, error);
                            }
                            webSocket.writeMessage(null);
                        });
    }

    private Promise<Void> streamEvents(IWebSocket webSocket, Iterator<Event> iterator) {
        if (!iterator.hasNext()) {
            return Promise.complete();
        }

        Event event = iterator.next();
        String payload;
        try {
            payload = toWebSocketPayload(event);
        } catch (Exception e) {
            logger.warn("Failed to format WebSocket payload", e);
            return sendAndClose(webSocket, errorPayload("serialization_error", e.getMessage()));
        }

        return webSocket
                .writeMessage(Message.text(payload))
                .then(() -> streamEvents(webSocket, iterator));
    }

    private Promise<Void> sendAndClose(IWebSocket webSocket, String payload) {
        return webSocket
                .writeMessage(Message.text(payload))
                .then(() -> webSocket.writeMessage(null));
    }

    private String toWebSocketPayload(Event event) throws Exception {
        JsonNode dataNode = objectMapper.readTree(event.data());
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("event", event.type());
        wrapper.set("data", dataNode);
        return objectMapper.writeValueAsString(wrapper);
    }

    private String errorPayload(String code, String message) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("event", "error");
        ObjectNode data = wrapper.putObject("data");
        data.put("code", code);
        data.put("message", message);
        try {
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            return "{\"event\":\"error\",\"data\":{\"code\":\""
                    + code
                    + "\",\"message\":\""
                    + message.replace('"', '\'')
                    + "\"}}";
        }
    }

    private String extractJobId(HttpRequest request) {
        String path = request.getPath();
        String[] segments = path.split("/");
        // Path format: /ws/jobs/{id}
        if (segments.length >= 3) {
            return segments[segments.length - 1];
        }
        throw new IllegalArgumentException("Invalid WebSocket path: " + path);
    }
}
