package com.ghatana.refactorer.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.util.Map;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A2A client for testing and demonstration purposes. Provides a simple interface for sending A2A
 * messages to Polyfix service.
 
 * @doc.type class
 * @doc.purpose Handles a2a client operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class A2aClient {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final Logger logger = LogManager.getLogger(A2aClient.class);
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private final AtomicLong messageIdCounter = new AtomicLong(1);
    private final Map<String, SettablePromise<Envelope>> pendingRequests =
            new ConcurrentHashMap<>();

    /**
     * Sends a run request via A2A protocol.
     *
     * @param repoRoot repository root path
     * @param languages list of languages to analyze
     * @param correlationId correlation ID for tracking
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendRunRequest(
            String repoRoot, java.util.List<String> languages, String correlationId) {
        Map<String, Object> payload =
                Map.of(
                        "operation",
                        "run",
                        "repoRoot",
                        repoRoot,
                        "languages",
                        languages,
                        "formatters",
                        true);

        return sendRequest(EnvelopeTypes.TASK_REQUEST, correlationId, payload);
    }

    /**
     * Sends a diagnose request via A2A protocol.
     *
     * @param repoRoot repository root path
     * @param languages list of languages to analyze
     * @param correlationId correlation ID for tracking
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendDiagnoseRequest(
            String repoRoot, java.util.List<String> languages, String correlationId) {
        Map<String, Object> payload =
                Map.of(
                        "operation", "diagnose",
                        "repoRoot", repoRoot,
                        "languages", languages);

        return sendRequest(EnvelopeTypes.TASK_REQUEST, correlationId, payload);
    }

    /**
     * Sends a status request via A2A protocol.
     *
     * @param correlationId correlation ID for the job to check
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendStatusRequest(String correlationId) {
        Map<String, Object> payload = Map.of("operation", "status");
        return sendRequest(EnvelopeTypes.TASK_REQUEST, correlationId, payload);
    }

    /**
     * Sends a report request via A2A protocol.
     *
     * @param correlationId correlation ID for the job
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendReportRequest(String correlationId) {
        Map<String, Object> payload = Map.of("operation", "report");
        return sendRequest(EnvelopeTypes.TASK_REQUEST, correlationId, payload);
    }

    /**
     * Sends a capabilities request via A2A protocol.
     *
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendCapabilitiesRequest() {
        Map<String, Object> payload = Map.of("query", "capabilities");
        return sendRequest(EnvelopeTypes.CAPABILITIES, null, payload);
    }

    /**
     * Sends a heartbeat via A2A protocol.
     *
     * @return future containing the response envelope
     */
    public Promise<Envelope> sendHeartbeat() {
        Map<String, Object> payload = Map.of("timestamp", System.currentTimeMillis());
        return sendRequest(EnvelopeTypes.HEARTBEAT, null, payload);
    }

    /** Generic method to send A2A requests. */
    private Promise<Envelope> sendRequest(
            String type, String correlationId, Map<String, Object> payload) {
        String messageId = "msg-" + messageIdCounter.getAndIncrement();

        Envelope envelope = Envelope.create(type, messageId, correlationId, payload);
        SettablePromise<Envelope> settable = new SettablePromise<>();

        pendingRequests.put(messageId, settable);

        try {
            // In a real implementation, this would send via WebSocket
            // For testing/demo purposes, we'll simulate the request
            String envelopeJson = objectMapper.writeValueAsString(envelope);
            logger.info("Sending A2A request: {}", envelopeJson);

            // NOTE: WebSocket transport is stubbed \u2014 uses mock responses for development/testing.
            // Production deployments should replace with real WebSocket send.
            Promise.ofBlocking(BLOCKING_EXECUTOR, 
                    () -> {
                        try {
                            Thread.sleep(100); // Simulate network delay

                            Envelope mockResponse =
                                    Envelope.response(
                                            messageId,
                                            correlationId,
                                            Map.of(
                                                    "status",
                                                    "MOCK_RESPONSE",
                                                    "timestamp",
                                                    System.currentTimeMillis()));

                            settable.set(mockResponse);

                        } catch (InterruptedException e) {
                            settable.setException(e);
                            Thread.currentThread().interrupt();
                        }
                    });

        } catch (Exception e) {
            logger.error("Failed to send A2A request", e);
            settable.setException(e);
            pendingRequests.remove(messageId);
        }

        return settable;
    }

    /**
     * Handles incoming response messages.
     *
     * @param responseJson JSON response from server
     */
    public void handleResponse(String responseJson) {
        try {
            Envelope response = objectMapper.readValue(responseJson, Envelope.class);

            SettablePromise<Envelope> pending = pendingRequests.remove(response.id());
            if (pending != null) {
                pending.set(response);
            } else {
                logger.warn("Received response for unknown message ID: {}", response.id());
            }

        } catch (Exception e) {
            logger.error("Failed to handle A2A response", e);
        }
    }

    /** Closes the client and cancels pending requests. */
    public void close() {
        logger.info("Closing A2A client, cancelling {} pending requests", pendingRequests.size());

        for (SettablePromise<Envelope> pending : pendingRequests.values()) {
            pending.setException(new java.util.concurrent.CancellationException("Client closed"));
        }

        pendingRequests.clear();
    }
}
