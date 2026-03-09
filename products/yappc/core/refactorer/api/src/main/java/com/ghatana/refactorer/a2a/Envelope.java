package com.ghatana.refactorer.a2a;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A2A envelope for agent-to-agent communication. Provides a standardized message format for
 * WebSocket communication.
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for envelope
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record Envelope(
        String type,
        String id,
        @JsonProperty("correlationId") String correlationId,
        Map<String, Object> payload,
        long timestamp) {

    /** Creates a new envelope with current timestamp. */
    public static Envelope create(
            String type, String id, String correlationId, Map<String, Object> payload) {
        return new Envelope(type, id, correlationId, payload, System.currentTimeMillis());
    }

    /** Creates a request envelope. */
    public static Envelope request(String id, String correlationId, Map<String, Object> payload) {
        return create(EnvelopeTypes.TASK_REQUEST, id, correlationId, payload);
    }

    /** Creates a response envelope. */
    public static Envelope response(String id, String correlationId, Map<String, Object> payload) {
        return create(EnvelopeTypes.TASK_RESULT, id, correlationId, payload);
    }

    /** Creates a progress envelope. */
    public static Envelope progress(String id, String correlationId, Map<String, Object> payload) {
        return create(EnvelopeTypes.TASK_PROGRESS, id, correlationId, payload);
    }

    /** Creates an error envelope. */
    public static Envelope error(String id, String correlationId, String errorMessage) {
        Map<String, Object> payload =
                Map.of("error", errorMessage, "timestamp", System.currentTimeMillis());
        return create(EnvelopeTypes.TASK_ERROR, id, correlationId, payload);
    }

    /** Creates a capabilities envelope. */
    public static Envelope capabilities(String id, Map<String, Object> capabilities) {
        return create(EnvelopeTypes.CAPABILITIES, id, null, capabilities);
    }
}
