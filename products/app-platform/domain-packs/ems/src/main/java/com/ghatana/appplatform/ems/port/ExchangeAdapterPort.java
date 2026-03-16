package com.ghatana.appplatform.ems.port;

import com.ghatana.appplatform.ems.domain.ExecutionFill;
import com.ghatana.appplatform.ems.domain.ExecutionSide;
import com.ghatana.appplatform.ems.domain.RoutedOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @doc.type      Port (Interface)
 * @doc.purpose   T3 plugin interface for exchange-specific order execution adapters.
 *                Adapters (e.g. NEPSE, ASX) implement this interface and are loaded
 *                as K-04 T3 network-tier plugins, verified via Ed25519 signature.
 * @doc.layer     Port
 * @doc.pattern   Plugin Interface — Hexagonal Architecture port toward exchange tier
 *
 * Plugin capabilities required: NETWORK (exchange connectivity), TIMER (heartbeat scheduling).
 *
 * Each adapter manages its own session lifecycle via connect()/disconnect().
 * Execution callbacks are registered via onExecutionReport() / onMarketData() at init time.
 */
public interface ExchangeAdapterPort {

    /**
     * Exchange identifier this adapter targets (e.g. "NEPSE", "ASX").
     */
    String exchangeId();

    /**
     * Establishes connection to the exchange. Implementations must be idempotent
     * (calling connect() on an already-connected adapter is a no-op).
     *
     * @param config adapter-specific configuration map (host, port, COMP IDs, TLS cert path, etc.)
     * @throws ExchangeConnectionException if the connection attempt fails
     */
    void connect(java.util.Map<String, String> config);

    /**
     * Gracefully disconnects from the exchange, flushing in-flight messages.
     */
    void disconnect();

    /**
     * Submits a new order to the exchange.
     *
     * @param order the routed order with all execution parameters
     * @return the exchange-assigned external order ID
     * @throws ExchangeRejectException if the exchange synchronously rejects the order
     */
    String submitOrder(RoutedOrder order);

    /**
     * Sends an order cancellation request to the exchange.
     *
     * @param routingId  the internal routing ID of the order to cancel
     * @param instrumentId instrument symbol
     * @param side       order side
     * @throws ExchangeRejectException if the exchange rejects the cancellation
     */
    void cancelOrder(String routingId, String instrumentId, ExecutionSide side);

    /**
     * Sends an order amendment (cancel/replace) request.
     *
     * @param routingId    the original routing ID
     * @param newQuantity  revised quantity (or null if unchanged)
     * @param newPrice     revised limit price (or null if unchanged)
     */
    void amendOrder(String routingId, Long newQuantity, BigDecimal newPrice);

    /**
     * Queries the current status of an order from the exchange.
     *
     * @param routingId  internal routing ID
     * @return Optional with the latest routed order state, empty if not found
     */
    Optional<RoutedOrder> getOrderStatus(String routingId);

    /**
     * Registers a callback for asynchronous execution report events.
     * The callback receives {@link ExecutionFill} instances as fills arrive.
     * Called once during adapter initialisation.
     *
     * @param callback event consumer; must be thread-safe
     */
    void onExecutionReport(Consumer<ExecutionFill> callback);

    /**
     * Registers a callback for market data events from the exchange.
     * The callback receives raw market data payloads (adapter-specific format).
     *
     * @param callback event consumer; must be thread-safe
     */
    void onMarketData(Consumer<Object> callback);

    /**
     * Returns {@code true} if the adapter currently has an active session with the exchange.
     */
    boolean isConnected();

    /** Thrown when the adapter cannot establish a connection to the exchange. */
    class ExchangeConnectionException extends RuntimeException {
        public ExchangeConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when the exchange rejects an order or cancellation synchronously. */
    class ExchangeRejectException extends RuntimeException {
        private final String rejectReason;
        public ExchangeRejectException(String routingId, String rejectReason) {
            super("Exchange rejected order " + routingId + ": " + rejectReason);
            this.rejectReason = rejectReason;
        }
        public String rejectReason() { return rejectReason; }
    }
}
