package com.ghatana.core.websocket;

import io.activej.bytebuf.ByteBuf;
import io.activej.csp.consumer.ChannelConsumer;
import io.activej.csp.consumer.ChannelConsumers;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.promise.Promise;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents an active WebSocket connection.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates a WebSocket connection with methods for sending messages,
 * receiving messages, and managing connection lifecycle.
 *
 * <p><b>Architecture Role</b><br>
 * Core WebSocket connection abstraction in libs/java/activej-websocket.
 * Used by:
 * - WebSocketServer - Manage active connections
 * - StreamingService - Send real-time data to clients
 * - BroadcastService - Send to multiple connections
 *
 * @doc.type class
 * @doc.purpose WebSocket connection wrapper with send/receive capabilities
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnection {

    /**
     * Unique connection identifier
     */
    @Getter
    private final String connectionId;

    /**
     * Tenant ID for multi-tenancy isolation
     */
    @Getter
    private final String tenantId;

    /**
     * Topic this connection is subscribed to
     */
    @Getter
    private final String topic;

    /**
     * Channel supplier for receiving messages from client
     */
    private final ChannelSupplier<ByteBuf> inbound;

    /**
     * Channel consumer for sending messages to client
     */
    private final ChannelConsumer<ByteBuf> outbound;

    /**
     * Connection creation timestamp
     */
    @Getter
    private final Instant connectedAt;

    /**
     * Additional connection metadata
     */
    @Getter
    private final Map<String, Object> metadata;

    /**
     * Connection state
     */
    @Getter
    private volatile ConnectionState state = ConnectionState.CONNECTED;

    /**
     * Last activity timestamp
     */
    @Getter
    private volatile Instant lastActivityAt;

    /**
     * Connection state enum
     */
    public enum ConnectionState {
        CONNECTED,
        CLOSING,
        CLOSED
    }

    /**
     * Create a new WebSocket connection
     *
     * @param tenantId Tenant ID for isolation
     * @param topic    Subscribed topic
     * @param inbound  Channel supplier for receiving
     * @param outbound Channel consumer for sending
     * @return New WebSocket connection
     */
    public static WebSocketConnection create(
            String tenantId,
            String topic,
            ChannelSupplier<ByteBuf> inbound,
            ChannelConsumer<ByteBuf> outbound
    ) {
        return new WebSocketConnection(
                UUID.randomUUID().toString(),
                tenantId,
                topic,
                inbound,
                outbound,
                Instant.now(),
                new ConcurrentHashMap<>()
        );
    }

    /**
     * Send a text message to the client
     *
     * @param message Message to send
     * @return Promise that completes when sent
     */
    public Promise<Void> send(String message) {
        if (state != ConnectionState.CONNECTED) {
            return Promise.ofException(new IllegalStateException("Connection is not open"));
        }

        lastActivityAt = Instant.now();
        byte[] bytes = message.getBytes();
        ByteBuf buf = ByteBuf.wrapForReading(bytes);

        return outbound.accept(buf)
                .whenException(e -> {
                    log.error("Failed to send message to connection {}: {}", connectionId, e.getMessage());
                    state = ConnectionState.CLOSED;
                });
    }

    /**
     * Send binary data to the client
     *
     * @param data Binary data to send
     * @return Promise that completes when sent
     */
    public Promise<Void> send(byte[] data) {
        if (state != ConnectionState.CONNECTED) {
            return Promise.ofException(new IllegalStateException("Connection is not open"));
        }

        lastActivityAt = Instant.now();
        ByteBuf buf = ByteBuf.wrapForReading(data);

        return outbound.accept(buf)
                .whenException(e -> {
                    log.error("Failed to send data to connection {}: {}", connectionId, e.getMessage());
                    state = ConnectionState.CLOSED;
                });
    }

    /**
     * Start receiving messages from the client
     *
     * @param handler Handler for received messages
     * @return Promise that completes when connection closes
     */
    public Promise<Void> startReceiving(Consumer<ByteBuf> handler) {
        return inbound.streamTo(ChannelConsumers.ofAsyncConsumer(buf -> {
            lastActivityAt = Instant.now();
            try {
                handler.accept(buf);
                return Promise.complete();
            } catch (Exception e) {
                log.error("Error handling message on connection {}: {}", connectionId, e.getMessage());
                return Promise.complete();
            }
        }));
    }

    /**
     * Close the connection gracefully
     *
     * @return Promise that completes when closed
     */
    public Promise<Void> close() {
        if (state == ConnectionState.CLOSED) {
            return Promise.complete();
        }

        state = ConnectionState.CLOSING;
        log.debug("Closing WebSocket connection: {}", connectionId);

        return outbound.acceptEndOfStream()
                .then($ -> {
                    state = ConnectionState.CLOSED;
                    log.info("WebSocket connection closed: {}", connectionId);
                    return Promise.complete();
                })
                .whenException(e -> {
                    state = ConnectionState.CLOSED;
                    log.warn("Error closing connection {}: {}", connectionId, e.getMessage());
                });
    }

    /**
     * Check if connection is open
     *
     * @return true if connected
     */
    public boolean isOpen() {
        return state == ConnectionState.CONNECTED;
    }

    /**
     * Get connection duration in seconds
     *
     * @return Duration since connection was established
     */
    public long getDurationSeconds() {
        return java.time.Duration.between(connectedAt, Instant.now()).getSeconds();
    }
}
