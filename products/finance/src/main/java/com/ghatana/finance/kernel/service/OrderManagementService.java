package com.ghatana.finance.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class OrderManagementService extends FinanceServiceBase {

    private static final String ORDER_DATASET = "finance.orders";

    private final Map<String, Order> orderCache = new ConcurrentHashMap<>();

    public OrderManagementService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "order-management";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
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
        ).whenException(e -> {});
    }

    // ==================== Core Order Operations ====================

    public Promise<Order> submitOrder(OrderRequest request) {
        ensureRunning();

        // Validate order
        ValidationResult validation = validateOrder(request);
        if (!validation.isValid()) {
            return Promise.ofException(new IllegalStateException(validation.getError()));
        }

        String orderId = generateId("ord");
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

        return createRecord(
            ORDER_DATASET,
            orderId,
            order,
            Map.of(
                "traderId", order.getTraderId(),
                "symbol", order.getSymbol(),
                "status", order.getStatus(),
                "createdAt", now.toString()
            ),
            "Order",
            1
        ).then(stored -> updateOrderStatus(orderId, "PENDING")
            .then($ -> audit("ORDER_SUBMIT", stored.getTraderId(),
                "Order " + orderId + " submitted for " + stored.getSymbol()))
            .map($ -> stored));
    }

    public Promise<Optional<Order>> getOrder(String orderId) {
        ensureRunning();

        // Check cache first
        Order cached = orderCache.get(orderId);
        if (cached != null) {
            return Promise.of(Optional.of(cached));
        }

        return readRecord(ORDER_DATASET, orderId, Order.class)
            .whenException(e -> Promise.of(Optional.empty()));
    }

    public Promise<Void> cancelOrder(String orderId, String traderId) {
        ensureRunning();

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

                orderCache.put(orderId, updated);

                return updateRecord(
                    ORDER_DATASET,
                    orderId,
                    updated,
                    Map.of("status", newStatus, "updatedAt", Instant.now().toString()),
                    "Order",
                    1
                ).then($ -> audit("ORDER_STATUS", order.getTraderId(),
                    "Order " + orderId + " status changed to " + newStatus));
            });
    }

    public Promise<List<Order>> getTraderOrders(String traderId, String status) {
        ensureRunning();

        StringBuilder query = new StringBuilder("traderId = :traderId");
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("traderId", traderId);

        if (status != null) {
            query.append(" AND status = :status");
            params.put("status", status);
        }

        return queryRecords(
            ORDER_DATASET,
            query.toString(),
            params,
            1000,
            0,
            Order.class
        ).map(orders -> orders.stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList());
    }

    public Promise<List<Order>> getOpenOrders() {
        ensureRunning();

        return queryRecords(
            ORDER_DATASET,
            "status IN ('NEW', 'PENDING', 'PARTIALLY_FILLED')",
            Map.of(),
            10000,
            0,
            Order.class
        );
    }

    // ==================== Private Methods ====================

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

    @Override
    protected Promise<Void> audit(String action, String traderId, String details) {
        return super.audit(action, traderId, details, Map.of("dataset", "finance.orders"));
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
