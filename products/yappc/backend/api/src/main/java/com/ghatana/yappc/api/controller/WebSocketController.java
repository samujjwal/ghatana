package com.ghatana.yappc.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.websocket.ConnectionManager;
import com.ghatana.yappc.api.websocket.WebSocketConnection;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.IWebSocket;
import io.activej.http.WebSocketServlet;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket controller for real-time communication.
 *
 * <p>Uses ActiveJ's {@link WebSocketServlet} for proper WebSocket upgrade handling.
 * Manages bidirectional messaging via {@link ConnectionManager}.
  *
 * @doc.type class
 * @doc.purpose web socket controller
 * @doc.layer product
 * @doc.pattern Controller
 */
public class WebSocketController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    private final ConnectionManager connectionManager;
    private final ObjectMapper mapper;

    @Inject
    public WebSocketController(ConnectionManager connectionManager, ObjectMapper mapper) {
        this.connectionManager = connectionManager;
        this.mapper = mapper;
    }

    /**
     * Create a WebSocketServlet that handles WebSocket upgrade and messaging.
     *
     * @param reactor the reactor for the servlet
     * @return a WebSocketServlet to mount at the /ws route
     */
    public WebSocketServlet createServlet(Reactor reactor) {
        return new WebSocketServlet(reactor) {
            @Override
            protected void onWebSocket(IWebSocket webSocket) {
                HttpRequest request = webSocket.getRequest();
                String tenantId = request.getQueryParameter("tenantId");
                String userId = request.getQueryParameter("userId");

                if (tenantId == null || userId == null) {
                    logger.warn("WebSocket connection rejected: missing tenantId or userId");
                    webSocket.close();
                    return;
                }

                String connId = UUID.randomUUID().toString();
                WebSocketConnection connection = new WebSocketConnection(connId, userId, tenantId, webSocket, mapper);
                connectionManager.add(connection);

                readLoop(webSocket, connection);
            }
        };
    }

    /**
     * HTTP fallback for non-WebSocket requests to the /ws endpoint.
     */
    public Promise<HttpResponse> handleRequest(HttpRequest request) {
        return Promise.of(
            HttpResponse.ofCode(400)
                .withPlainText("WebSocket upgrade required")
                .build()
        );
    }

    private void readLoop(IWebSocket webSocket, WebSocketConnection connection) {
        webSocket.readMessage()
            .whenResult(msg -> {
                if (msg != null) {
                    logger.info("Received message from {}: {}", connection.getUserId(), msg.getText());
                    // Handle message routing here
                    readLoop(webSocket, connection);
                } else {
                    connectionManager.remove(connection);
                    webSocket.close();
                }
            })
            .whenException(e -> {
                logger.error("WebSocket error", e);
                connectionManager.remove(connection);
                webSocket.close();
            });
    }
}
