package com.ghatana.core.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standard message format for WebSocket communication.
 *
 * <p><b>Purpose</b><br>
 * Provides a standardized message envelope for all WebSocket communications
 * between ActiveJ backend and Node/Fastify UI layer.
 *
 * <p><b>Message Types</b><br>
 * - <b>data</b>: Regular data payload
 * - <b>heartbeat</b>: Keep-alive ping
 * - <b>error</b>: Error notification
 * - <b>complete</b>: Stream completion signal
 * - <b>subscribe</b>: Subscription request
 * - <b>unsubscribe</b>: Unsubscription request
 *
 * @doc.type record
 * @doc.purpose WebSocket message envelope
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class WebSocketMessage<T> {

    private static final ObjectMapper OBJECT_MAPPER = JsonUtils.getDefaultMapper()
            .registerModule(new JavaTimeModule());

    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

    /**
     * Message type
     */
    private MessageType type;

    /**
     * Topic this message belongs to
     */
    private String topic;

    /**
     * Message payload (type varies by message type)
     */
    private T payload;

    /**
     * Message timestamp
     */
    @Builder.Default
    private long timestamp = Instant.now().toEpochMilli();

    /**
     * Sequence number for ordering
     */
    private Long sequenceNumber;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Error information (for error messages)
     */
    private ErrorInfo error;

    /**
     * Message type enum
     */
    public enum MessageType {
        DATA,
        HEARTBEAT,
        ERROR,
        COMPLETE,
        SUBSCRIBE,
        UNSUBSCRIBE,
        ACK
    }

    /**
     * Error information
     */
    @Data
    @Builder
    public static class ErrorInfo {
        private String code;
        private String message;
        private Map<String, Object> details;
    }

    /**
     * Create a data message
     *
     * @param topic   Topic name
     * @param payload Data payload
     * @return WebSocket message
     */
    public static <T> WebSocketMessage<T> data(String topic, T payload) {
        return WebSocketMessage.<T>builder()
                .type(MessageType.DATA)
                .topic(topic)
                .payload(payload)
                .sequenceNumber(SEQUENCE_GENERATOR.incrementAndGet())
                .build();
    }

    /**
     * Create a heartbeat message
     *
     * @return Heartbeat message
     */
    public static WebSocketMessage<Void> heartbeat() {
        return WebSocketMessage.<Void>builder()
                .type(MessageType.HEARTBEAT)
                .build();
    }

    /**
     * Create an error message
     *
     * @param topic   Topic name
     * @param code    Error code
     * @param message Error message
     * @return Error message
     */
    public static WebSocketMessage<Void> error(String topic, String code, String message) {
        return WebSocketMessage.<Void>builder()
                .type(MessageType.ERROR)
                .topic(topic)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Create an error message with details
     *
     * @param topic   Topic name
     * @param code    Error code
     * @param message Error message
     * @param details Error details
     * @return Error message
     */
    public static WebSocketMessage<Void> error(String topic, String code, String message,
                                                Map<String, Object> details) {
        return WebSocketMessage.<Void>builder()
                .type(MessageType.ERROR)
                .topic(topic)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }

    /**
     * Create a completion message
     *
     * @param topic Topic name
     * @return Completion message
     */
    public static WebSocketMessage<Void> complete(String topic) {
        return WebSocketMessage.<Void>builder()
                .type(MessageType.COMPLETE)
                .topic(topic)
                .build();
    }

    /**
     * Create an acknowledgment message
     *
     * @param topic Topic name
     * @return Acknowledgment message
     */
    public static WebSocketMessage<Void> ack(String topic) {
        return WebSocketMessage.<Void>builder()
                .type(MessageType.ACK)
                .topic(topic)
                .build();
    }

    /**
     * Serialize message to JSON string
     *
     * @return JSON string
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket message", e);
            return "{\"type\":\"ERROR\",\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize message\"}}";
        }
    }

    /**
     * Serialize message to JSON bytes
     *
     * @return JSON bytes
     */
    public byte[] toBytes() {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(this);
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket message", e);
            return "{\"type\":\"ERROR\",\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize message\"}}".getBytes();
        }
    }

    /**
     * Parse message from JSON string
     *
     * @param json JSON string
     * @return Parsed message
     */
    public static WebSocketMessage<?> fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, WebSocketMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
            return error("unknown", "PARSE_ERROR", "Failed to parse message");
        }
    }

    /**
     * Parse message from JSON bytes
     *
     * @param bytes JSON bytes
     * @return Parsed message
     */
    public static WebSocketMessage<?> fromBytes(byte[] bytes) {
        try {
            return OBJECT_MAPPER.readValue(bytes, WebSocketMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", e.getMessage());
            return error("unknown", "PARSE_ERROR", "Failed to parse message");
        }
    }
}
