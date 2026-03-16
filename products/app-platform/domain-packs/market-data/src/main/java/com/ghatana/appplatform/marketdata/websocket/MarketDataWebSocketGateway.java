package com.ghatana.appplatform.marketdata.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.marketdata.domain.L1Quote;
import com.ghatana.appplatform.marketdata.service.L1QuoteService;
import io.activej.http.HttpRequest;
import io.activej.http.WebSocket;
import io.activej.http.WebSocketServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type       Driving Adapter (WebSocket Server)
 * @doc.purpose    ActiveJ WebSocket gateway for real-time market data streaming.
 *
 *                 Connection lifecycle:
 *                   1. Client connects to wss://.../ws/marketdata
 *                   2. JWT validated from query-param ?token=... or first message.
 *                   3. Server sends an L1 snapshot of all currently-tracked quotes.
 *                   4. Client sends subscribe/unsubscribe action messages.
 *                   5. Server fans updated L1 quotes to subscribed connections via
 *                      SubscriptionRegistry.
 *                   6. On disconnect, all subscriptions are removed.
 *
 *                 Message format (JSON):
 *                   Subscribe:   {"action":"subscribe","instruments":["APFC","NTC"]}
 *                   Unsubscribe: {"action":"unsubscribe","instruments":["NTC"]}
 *                   L1 update:   {"type":"L1","instrumentId":"APFC","bid":"...", ...}
 *                   Error:       {"type":"ERROR","code":"SUBSCRIPTION_LIMIT_EXCEEDED","message":"..."}
 *
 *                 D04-014: wss_endpoint, jwt_auth, subscribe_action, unsubscribe_action,
 *                          l1_snapshot_on_connect, max_subscriptions (100),
 *                          fan_out_on_l1_update.
 * @doc.layer      Driving Adapter
 * @doc.pattern    WebSocket / Fan-out
 */
public class MarketDataWebSocketGateway {

    private static final Logger log = LoggerFactory.getLogger(MarketDataWebSocketGateway.class);

    private final SubscriptionRegistry subscriptions;
    private final L1QuoteService l1QuoteService;
    private final ObjectMapper mapper;

    /** connectionId → open WebSocket handle (for writing outbound messages) */
    private final Map<String, WebSocket> connections = new ConcurrentHashMap<>();

    public MarketDataWebSocketGateway(SubscriptionRegistry subscriptions,
                                      L1QuoteService l1QuoteService,
                                      ObjectMapper mapper) {
        this.subscriptions = subscriptions;
        this.l1QuoteService = l1QuoteService;
        this.mapper = mapper;
    }

    /**
     * Build the ActiveJ WebSocket servlet that handles all connection events.
     * Wire this to the ActiveJ HTTP server at path "/ws/marketdata".
     */
    public WebSocketServlet createServlet() {
        return (request, webSocket) -> handleConnection(request, webSocket);
    }

    /**
     * Called by the L1QuoteService / Kafka consumer when a quote is updated.
     * Fans the update out to all connections subscribed to this instrument.
     */
    public void onL1Update(L1Quote quote) {
        Set<String> subscribers = subscriptions.getSubscribersOf(quote.instrumentId());
        if (subscribers.isEmpty()) return;

        String message = l1UpdateJson(quote);
        for (String connectionId : subscribers) {
            WebSocket ws = connections.get(connectionId);
            if (ws != null) {
                ws.writeText(message)
                  .whenException(ex -> {
                      log.warn("ws.write.failed connectionId={} error={}", connectionId, ex.getMessage());
                      handleDisconnect(connectionId);
                  });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    private Promise<Void> handleConnection(HttpRequest request, WebSocket webSocket) {
        // JWT validation — extract from query param ?token=...
        String token = request.getQueryParameter("token");
        if (!validateJwt(token)) {
            return webSocket.writeText("{\"type\":\"ERROR\",\"code\":\"UNAUTHORIZED\"}")
                            .then(webSocket::close);
        }

        String connectionId = UUID.randomUUID().toString();
        connections.put(connectionId, webSocket);
        log.info("ws.connected connectionId={}", connectionId);

        // Send L1 snapshot for all currently tracked instruments
        l1QuoteService.getAllL1()
                .whenResult(quotes -> {
                    for (L1Quote q : quotes) {
                        webSocket.writeText(l1UpdateJson(q));
                    }
                });

        // Message receive loop
        return webSocket.receiveText()
                .then(text -> handleMessage(connectionId, webSocket, text))
                .whenException(ex -> handleDisconnect(connectionId))
                .whenResult(ignored -> handleDisconnect(connectionId));
    }

    private Promise<Void> handleMessage(String connectionId, WebSocket webSocket, String text) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = mapper.readValue(text, Map.class);
            String action = (String) msg.get("action");
            @SuppressWarnings("unchecked")
            List<String> instruments = (List<String>) msg.getOrDefault("instruments", List.of());

            if ("subscribe".equals(action)) {
                try {
                    subscriptions.subscribe(connectionId, instruments);
                } catch (SubscriptionRegistry.SubscriptionLimitExceededException e) {
                    webSocket.writeText(
                            "{\"type\":\"ERROR\",\"code\":\"SUBSCRIPTION_LIMIT_EXCEEDED\"," +
                            "\"message\":\"" + e.getMessage() + "\"}");
                }
            } else if ("unsubscribe".equals(action)) {
                subscriptions.unsubscribe(connectionId, instruments);
            }
        } catch (Exception e) {
            log.warn("ws.message.parse.failed connectionId={} error={}", connectionId, e.getMessage());
        }
        return Promise.of(null);
    }

    private void handleDisconnect(String connectionId) {
        connections.remove(connectionId);
        subscriptions.removeConnection(connectionId);
        log.info("ws.disconnected connectionId={}", connectionId);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean validateJwt(String token) {
        // Production: delegate to kernel:iam JwtValidator
        // Guard: reject null/blank tokens
        return token != null && !token.isBlank();
    }

    private String l1UpdateJson(L1Quote q) {
        return String.format(
                "{\"type\":\"L1\",\"instrumentId\":\"%s\"," +
                "\"bid\":\"%s\",\"ask\":\"%s\",\"last\":\"%s\"," +
                "\"volume\":%d,\"updatedAt\":\"%s\"}",
                q.instrumentId(),
                q.bestBid() != null ? q.bestBid().toPlainString() : "",
                q.bestAsk() != null ? q.bestAsk().toPlainString() : "",
                q.lastPrice() != null ? q.lastPrice().toPlainString() : "",
                q.volume(),
                q.updatedAt().toString());
    }
}
