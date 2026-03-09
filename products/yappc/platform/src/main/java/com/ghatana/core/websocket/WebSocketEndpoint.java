package com.ghatana.core.websocket;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.bytebuf.ByteBuf;
import io.activej.csp.consumer.ChannelConsumer;
import io.activej.csp.consumer.ChannelConsumers;
import io.activej.csp.supplier.ChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import io.activej.http.*;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * WebSocket endpoint handler for ActiveJ HTTP server.
 *
 * <p><b>Purpose</b><br>
 * Provides a WebSocket endpoint that can be registered with HttpServerBuilder.
 * Handles WebSocket upgrade, connection management, and message routing.
 *
 * <p><b>Architecture Role</b><br>
 * WebSocket endpoint in libs/java/activej-websocket for the Hybrid Backend.
 * Used by:
 * - Data-Cloud - EventCloud streaming endpoint
 * - AEP - Pipeline/Agent state streaming
 * - All products - Real-time UI updates
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WebSocketConnectionManager manager = new WebSocketConnectionManager();
 *
 * WebSocketEndpoint endpoint = WebSocketEndpoint.builder()
 *     .connectionManager(manager)
 *     .topicExtractor(request -> request.getQueryParameter("topic"))
 *     .tenantExtractor(request -> request.getHeader("X-Tenant-Id"))
 *     .build();
 *
 * HttpServer server = HttpServerBuilder.create()
 *     .withPort(8080)
 *     .addRoute(HttpMethod.GET, "/ws", endpoint.asServlet(reactor))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose WebSocket endpoint handler for ActiveJ
 * @doc.layer platform
 * @doc.pattern Service
 */
@Slf4j
@RequiredArgsConstructor
@Builder
public class WebSocketEndpoint {

    /**
     * Connection manager for tracking connections
     */
    @Getter
    private final WebSocketConnectionManager connectionManager;

    /**
     * Extract topic from request
     */
    @Builder.Default
    private final Function<HttpRequest, String> topicExtractor =
            request -> request.getQueryParameter("topic");

    /**
     * Extract tenant ID from request
     */
    @Builder.Default
    private final Function<HttpRequest, String> tenantExtractor =
            request -> {
                String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
                return tenantId != null ? tenantId : "";
            };

    /**
     * Connection established callback
     */
    @Builder.Default
    private final BiConsumer<WebSocketConnection, HttpRequest> onConnect =
            (conn, req) -> log.info("WebSocket connected: {}", conn.getConnectionId());

    /**
     * Connection closed callback
     */
    @Builder.Default
    private final BiConsumer<WebSocketConnection, Throwable> onDisconnect =
            (conn, err) -> log.info("WebSocket disconnected: {} (error: {})",
                    conn.getConnectionId(), err != null ? err.getMessage() : "none");

    /**
     * Message received callback
     */
    @Builder.Default
    private final BiConsumer<WebSocketConnection, ByteBuf> onMessage =
            (conn, msg) -> log.debug("Message received on {}: {} bytes",
                    conn.getConnectionId(), msg.readRemaining());

    /**
     * Read timeout for idle connections
     */
    @Builder.Default
    private final Duration readTimeout = Duration.ofMinutes(5);

    /**
     * Create an AsyncServlet that handles WebSocket upgrade
     *
     * @param reactor The reactor to run the servlet on
     * @return AsyncServlet for WebSocket handling
     */
    public AsyncServlet asServlet(Reactor reactor) {
        WebSocketServlet wsServlet = new WebSocketServlet(reactor) {
            @Override
            protected void onWebSocket(IWebSocket webSocket) {
                HttpRequest request = webSocket.getRequest();
                String topic = topicExtractor.apply(request);
                String tenantId = tenantExtractor.apply(request);
                handleWebSocket(webSocket, topic, tenantId, request);
            }
        };

        return request -> {
            String topic = topicExtractor.apply(request);
            if (topic == null || topic.isEmpty()) {
                log.warn("WebSocket upgrade rejected: missing topic parameter");
                return Promise.of(ResponseBuilder.status(400)
                        .json(Map.of("error", "Missing topic parameter"))
                        .build());
            }
            return wsServlet.serve(request);
        };
    }

    /**
     * Handle an established WebSocket connection
     */
    private void handleWebSocket(
            IWebSocket webSocket,
            String topic,
            String tenantId,
            HttpRequest request
    ) {
        // Get channel supplier/consumer from WebSocket
        ChannelSupplier<ByteBuf> inbound = ChannelSuppliers.ofAsyncSupplier(webSocket::readMessage)
                .map(msg -> msg != null && msg.getBuf() != null ? msg.getBuf() : null)
                .filter(java.util.Objects::nonNull);
        ChannelConsumer<ByteBuf> outbound = ChannelConsumers.ofAsyncConsumer(msg ->
                webSocket.writeMessage(IWebSocket.Message.binary(msg)));

        // Create connection wrapper
        WebSocketConnection connection = WebSocketConnection.create(
                tenantId,
                topic,
                inbound,
                outbound
        );

        // Register connection
        connectionManager.addConnection(connection);

        // Invoke connect callback
        try {
            onConnect.accept(connection, request);
        } catch (Exception e) {
            log.error("Error in onConnect callback: {}", e.getMessage());
        }

        // Send acknowledgment
        WebSocketMessage<Void> ack = WebSocketMessage.ack(topic);
        connection.send(ack.toJson());

        // Handle incoming messages
        handleIncomingMessages(connection)
                .whenComplete(($, error) -> {
                    // Connection closed
                    connectionManager.removeConnection(connection);
                    try {
                        onDisconnect.accept(connection, error);
                    } catch (Exception e) {
                        log.error("Error in onDisconnect callback: {}", e.getMessage());
                    }
                });
    }

    /**
     * Handle incoming messages from client
     */
    private Promise<Void> handleIncomingMessages(WebSocketConnection connection) {
        return connection.startReceiving(message -> {
            try {
                onMessage.accept(connection, message);

                // Parse message and handle control messages
                byte[] bytes = new byte[message.readRemaining()];
                message.read(bytes);
                WebSocketMessage<?> wsMessage = WebSocketMessage.fromBytes(bytes);

                if (wsMessage.getType() == WebSocketMessage.MessageType.HEARTBEAT) {
                    // Respond to heartbeat with heartbeat
                    connection.send(WebSocketMessage.heartbeat().toJson());
                }
            } catch (Exception e) {
                log.error("Error processing message: {}", e.getMessage());
            }
        });
    }

    /**
     * Create a simple endpoint that just publishes events (no custom message handling)
     *
     * @param connectionManager Connection manager
     * @return Simple WebSocket endpoint
     */
    public static WebSocketEndpoint simple(WebSocketConnectionManager connectionManager) {
        return WebSocketEndpoint.builder()
                .connectionManager(connectionManager)
                .build();
    }
}
