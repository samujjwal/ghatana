package com.ghatana.yappc.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.websocket.ConnectionManager;
import com.ghatana.yappc.api.websocket.WebSocketConnection;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.IWebSocket;
import io.activej.http.WebSocketServlet;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * WebSocket controller for real-time communication.
 *
 * <p>Validates {@code X-API-Key} header (or {@code apiKey} query parameter as a browser
 * fallback) during the HTTP upgrade handshake. Connections with missing or invalid keys
 * are rejected with HTTP 401 before the WebSocket protocol is established.
 *
 * <p>Accepted connections have their tenant ID resolved from the {@code tenantId}
 * query parameter and propagated to all message handlers.
 *
 * @doc.type class
 * @doc.purpose WebSocket controller with API-key handshake authentication
 * @doc.layer product
 * @doc.pattern Controller
 */
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    private static final HttpHeader X_API_KEY = HttpHeaders.of("X-API-Key");

    private final ConnectionManager connectionManager;
    private final ObjectMapper mapper;
    private final Set<String> allowedApiKeys;

    @Inject
    public WebSocketController(ConnectionManager connectionManager, ObjectMapper mapper) {
        this.connectionManager = connectionManager;
        this.mapper = mapper;
        this.allowedApiKeys = loadApiKeys();
    }

    /**
     * Creates a {@link WebSocketServlet} that authenticates the upgrade request via
     * {@code X-API-Key} before establishing the WebSocket connection.
     *
     * @param reactor the ActiveJ reactor
     * @return the authenticated WebSocket servlet
     */
    public WebSocketServlet createServlet(Reactor reactor) {
        Set<String> keys = allowedApiKeys;
        return new WebSocketServlet(reactor) {

            /**
             * Validates API key at the HTTP-upgrade level — before the WebSocket is created.
             * Returns 401 immediately if the key is absent or not in the allowed set.
             */
            @Override
            protected Promise<HttpResponse> onRequest(HttpRequest request) {
                String apiKey = request.getHeader(X_API_KEY);
                if (apiKey == null || apiKey.isBlank()) {
                    // Browser WebSocket clients may pass the key as a query param
                    apiKey = request.getQueryParameter("apiKey");
                }
                if (apiKey == null || !keys.contains(apiKey.trim())) {
                    logger.warn("WebSocket upgrade rejected: missing or invalid X-API-Key ({})",
                        request.getRemoteAddress());
                    return Promise.of(
                        HttpResponse.ofCode(401)
                            .withPlainText("Unauthorized: provide a valid X-API-Key header or apiKey query parameter")
                            .build());
                }
                return super.onRequest(request);
            }

            @Override
            protected void onWebSocket(IWebSocket webSocket) {
                HttpRequest request = webSocket.getRequest();
                String tenantId = request.getQueryParameter("tenantId");
                String userId   = request.getQueryParameter("userId");

                if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
                    logger.warn("WebSocket closed: missing tenantId or userId query parameters");
                    webSocket.close();
                    return;
                }

                String connId = UUID.randomUUID().toString();
                logger.info("WebSocket connection accepted: connId={} tenant={} user={}",
                    connId, tenantId, userId);

                WebSocketConnection connection =
                    new WebSocketConnection(connId, userId, tenantId, webSocket, mapper);
                connectionManager.add(connection);

                readLoop(webSocket, connection);
            }
        };
    }

    /**
     * HTTP fallback for non-WebSocket GET requests to {@code /ws}.
     * Real WebSocket clients use the {@code createServlet()} result instead.
     */
    public Promise<HttpResponse> handleRequest(HttpRequest request) {
        return Promise.of(
            HttpResponse.ofCode(426)
                .withHeader(HttpHeaders.of("Upgrade"), "websocket")
                .withPlainText("Upgrade Required: use a WebSocket client")
                .build()
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static Set<String> loadApiKeys() {
        String env = System.getenv("YAPPC_API_KEYS");
        if (env == null || env.isBlank()) {
            logger.warn("YAPPC_API_KEYS not set — using default dev-key for WebSocket auth");
            return Set.of("dev-key");
        }
        Set<String> keys = new java.util.HashSet<>();
        for (String k : env.split(",")) {
            String trimmed = k.trim();
            if (!trimmed.isEmpty()) keys.add(trimmed);
        }
        return Set.copyOf(keys);
    }

    private void readLoop(IWebSocket webSocket, WebSocketConnection connection) {
        webSocket.readMessage()
            .whenResult(msg -> {
                if (msg != null) {
                    logger.debug("Received message from user={}: {}",
                        connection.getUserId(), msg.getText());
                    readLoop(webSocket, connection);
                } else {
                    logger.info("WebSocket closed by client: connId={}", connection.getId());
                    connectionManager.remove(connection);
                    webSocket.close();
                }
            })
            .whenException(e -> {
                logger.error("WebSocket error for connId={}", connection.getId(), e);
                connectionManager.remove(connection);
                webSocket.close();
            });
    }
}

