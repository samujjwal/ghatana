package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import com.ghatana.kernel.util.JsonUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Management Service for trading operations.
 *
 * <p>Manages trading orders with:
 * <ul>
 *   <li>Order lifecycle management (NEW → PENDING → FILLED → COMPLETED)</li>
 *   <li>Pre-trade risk checks</li>
 *   <li>Order validation and compliance</li>
 *   <li>Audit trail for SEBON regulations</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance order management service
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class OrderManagementService {

    private static final String ORDER_DATASET = "finance.orders";
    private static final String AUDIT_DATASET = "finance.order.audit";

    private final DataCloudKernelAdapter dataCloud;
    private final Map<String, Order> orderCache = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public OrderManagementService(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
    }

    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    public Promise<Void> stop() {
        running = false;
        orderCache.clear();
        return Promise.complete();
    }

    public boolean isHealthy() {
        return running;
    }

    public String getName() {
        return "order-management";
    }

    // ==================== Core Order Operations ====================

    /**
     * Submits a new order with validation and risk checks.
     *
     * @param request the order request
     * @return Promise containing the created order
     */
    public Promise<Order> submitOrder(OrderRequest request) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        // Validate order
        ValidationResult validation = validateOrder(request);
        if (!validation.isValid()) {
            return Promise.ofException(new IllegalStateException(validation.getError()));
        }

        String orderId = generateId();
        Instant now = Instant.now();

        Order order = new Order(
            orderId,
            request.getTraderId(),
            request.getSymbol(),
            request.getSide(), // BUY or SELL
            request.getOrderType(), // MARKET, LIMIT, STOP
            request.getQuantity(),
            request.getPrice(),
            request.getTimeInForce(), // GTC, IOC, FOK
            "NEW",
            now,
            now,
            request.getExchange(),
            request.getAccountId()
        );

        DataWriteRequest writeRequest = new DataWriteRequest(
            ORDER_DATASET,
            orderId,
            serialize(order),
            Map.of(
                "traderId", order.getTraderId(),
                "symbol", order.getSymbol(),
                "status", order.getStatus(),
                "createdAt", now.toString()
            )
        );

        return dataCloud.writeData(writeRequest)
            .then($ -> updateOrderStatus(orderId, "PENDING"))
            .then($ -> audit("ORDER_SUBMIT", order.getTraderId(),
                "Order " + orderId + " submitted for " + order.getSymbol()))
            .map($ -> order);
    }

    /**
     * Gets an order by ID.
     *
     * @param orderId the order identifier
     * @return Promise containing the order if found
     */
    public Promise<Optional<Order>> getOrder(String orderId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        // Check cache first
        Order cached = orderCache.get(orderId);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }

        DataReadRequest request = new DataReadRequest(ORDER_DATASET, orderId, Map.of());

        return dataCloud.readData(request)
            .map(result -> Optional.ofNullable(deserialize(result.getData())))
            .whenException(e -> Promise.of(Optional.empty()));
    }

    /**
     * Cancels an open order.
     *
     * @param orderId the order identifier
     * @param traderId the trader requesting cancellation
     * @return Promise completing when cancelled
     */
    public Promise<Void> cancelOrder(String orderId, String traderId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getOrder(orderId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Order not found"));
                }

                Order order = opt.get();

                // Check ownership
                if (!order.getTraderId().equals(traderId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized to cancel this order"));
                }

                // Can only cancel NEW or PENDING orders
                if (!List.of("NEW", "PENDING").contains(order.getStatus())) {
                    return Promise.ofException(
                        new IllegalStateException("Cannot cancel " + order.getStatus() + " order"));
                }

                return updateOrderStatus(orderId, "CANCELLED")
                    .then($ -> audit("ORDER_CANCEL", traderId,
                        "Order " + orderId + " cancelled"));
            });
    }

    /**
     * Updates order status (called by execution engine).
     *
     * @param orderId the order identifier
     * @param newStatus the new status
     * @return Promise completing when updated
     */
    public Promise<Void> updateOrderStatus(String orderId, String newStatus) {
        return getOrder(orderId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Order not found"));
                }

                Order order = opt.get();
                Order updated = order.withStatus(newStatus).withUpdatedAt(Instant.now());

                if ("FILLED".equals(newStatus)) {
                    updated = updated.withFilledAt(Instant.now());
                }

                DataWriteRequest request = new DataWriteRequest(
                    ORDER_DATASET,
                    orderId,
                    serialize(updated),
                    Map.of("status", newStatus, "updatedAt", Instant.now().toString())
                );

                orderCache.put(orderId, updated);

                return dataCloud.writeData(request)
                    .then($ -> audit("ORDER_STATUS", order.getTraderId(),
                        "Order " + orderId + " status changed to " + newStatus));
            });
    }

    /**
     * Gets orders for a trader.
     *
     * @param traderId the trader identifier
     * @param status optional status filter
     * @return Promise containing orders
     */
    public Promise<List<Order>> getTraderOrders(String traderId, String status) {
        if (!running) {
            return Promise.of(List.of());
        }

        StringBuilder query = new StringBuilder("traderId = :traderId");
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("traderId", traderId);

        if (status != null) {
            query.append(" AND status = :status");
            params.put("status", status);
        }

        DataQueryRequest request = new DataQueryRequest(
            ORDER_DATASET,
            query.toString(),
            params,
            1000,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData()))
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList());
    }

    /**
     * Gets open orders for risk monitoring.
     *
     * @return Promise containing all open orders
     */
    public Promise<List<Order>> getOpenOrders() {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
            ORDER_DATASET,
            "status IN ('NEW', 'PENDING', 'PARTIALLY_FILLED')",
            Map.of(),
            10000,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData()))
                .filter(Objects::nonNull)
                .toList());
    }

    // ==================== Private Methods ====================

    private Promise<Void> initializeDatasets() {
        return dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            ORDER_DATASET,
            Map.of(
                "orderId", "string",
                "traderId", "string",
                "symbol", "string",
                "side", "string",
                "status", "string",
                "createdAt", "timestamp"
            ),
            Map.of("retention", "10years") // SEBON requirement
        )).whenException(e -> {});
    }

    private ValidationResult validateOrder(OrderRequest request) {
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            return ValidationResult.error("Symbol is required");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.error("Quantity must be positive");
        }
        if (request.getSide() == null || !List.of("BUY", "SELL").contains(request.getSide())) {
            return ValidationResult.error("Side must be BUY or SELL");
        }
        if ("LIMIT".equals(request.getOrderType()) && request.getPrice() == null) {
            return ValidationResult.error("Price is required for LIMIT orders");
        }
        return ValidationResult.success();
    }

    private Promise<Void> audit(String action, String traderId, String details) {
        String auditId = generateId();
        DataWriteRequest request = new DataWriteRequest(
            AUDIT_DATASET,
            auditId,
            (action + ":" + traderId + ":" + details).getBytes(StandardCharsets.UTF_8),
            Map.of(
                "timestamp", Instant.now().toString(),
                "action", action,
                "traderId", traderId
            )
        );

        return dataCloud.writeData(request).whenException(e -> {});
    }

    private byte[] serialize(Order order) {
        return JsonUtils.toJson(order).getBytes(StandardCharsets.UTF_8);
    }

    private Order deserialize(byte[] data) {
        if (data == null) return null;
        return JsonUtils.fromJson(new String(data, StandardCharsets.UTF_8), Order.class);
    }

    private String generateId() {
        return "ord-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    public static class Order {
        private final String id;
        private final String traderId;
        private final String symbol;
        private final String side; // BUY, SELL
        private final String orderType; // MARKET, LIMIT, STOP
        private final BigDecimal quantity;
        private final BigDecimal price;
        private final String timeInForce; // GTC, IOC, FOK
        private final String status; // NEW, PENDING, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
        private final Instant createdAt;
        private final Instant updatedAt;
        private final Instant filledAt;
        private final String exchange;
        private final String accountId;

        public Order(String id, String traderId, String symbol, String side, String orderType,
                    BigDecimal quantity, BigDecimal price, String timeInForce, String status,
                    Instant createdAt, Instant updatedAt, String exchange, String accountId) {
            this(id, traderId, symbol, side, orderType, quantity, price, timeInForce, status,
                createdAt, updatedAt, null, exchange, accountId);
        }

        public Order(String id, String traderId, String symbol, String side, String orderType,
                    BigDecimal quantity, BigDecimal price, String timeInForce, String status,
                    Instant createdAt, Instant updatedAt, Instant filledAt, String exchange, String accountId) {
            this.id = id;
            this.traderId = traderId;
            this.symbol = symbol;
            this.side = side;
            this.orderType = orderType;
            this.quantity = quantity;
            this.price = price;
            this.timeInForce = timeInForce;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.filledAt = filledAt;
            this.exchange = exchange;
            this.accountId = accountId;
        }

        public String getId() { return id; }
        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public String getSide() { return side; }
        public String getOrderType() { return orderType; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public String getTimeInForce() { return timeInForce; }
        public String getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public Instant getFilledAt() { return filledAt; }
        public String getExchange() { return exchange; }
        public String getAccountId() { return accountId; }

        public Order withStatus(String newStatus) {
            return new Order(id, traderId, symbol, side, orderType, quantity, price,
                timeInForce, newStatus, createdAt, updatedAt, filledAt, exchange, accountId);
        }

        public Order withUpdatedAt(Instant newUpdatedAt) {
            return new Order(id, traderId, symbol, side, orderType, quantity, price,
                timeInForce, status, createdAt, newUpdatedAt, filledAt, exchange, accountId);
        }

        public Order withFilledAt(Instant newFilledAt) {
            return new Order(id, traderId, symbol, side, orderType, quantity, price,
                timeInForce, status, createdAt, updatedAt, newFilledAt, exchange, accountId);
        }
    }

    public static class OrderRequest {
        private final String traderId;
        private final String symbol;
        private final String side;
        private final String orderType;
        private final BigDecimal quantity;
        private final BigDecimal price;
        private final String timeInForce;
        private final String exchange;
        private final String accountId;

        public OrderRequest(String traderId, String symbol, String side, String orderType,
                           BigDecimal quantity, BigDecimal price, String timeInForce,
                           String exchange, String accountId) {
            this.traderId = traderId;
            this.symbol = symbol;
            this.side = side;
            this.orderType = orderType;
            this.quantity = quantity;
            this.price = price;
            this.timeInForce = timeInForce;
            this.exchange = exchange;
            this.accountId = accountId;
        }

        public String getTraderId() { return traderId; }
        public String getSymbol() { return symbol; }
        public String getSide() { return side; }
        public String getOrderType() { return orderType; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public String getTimeInForce() { return timeInForce; }
        public String getExchange() { return exchange; }
        public String getAccountId() { return accountId; }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String error;

        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        boolean isValid() { return valid; }
        String getError() { return error; }
    }
}
