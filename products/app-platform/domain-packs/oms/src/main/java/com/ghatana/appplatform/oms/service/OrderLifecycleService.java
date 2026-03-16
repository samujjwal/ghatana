package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Manages the 9-state order lifecycle transitions (D01-004).
 *              Also handles fill processing and average price calculation (D01-014).
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service, State Machine
 */
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private static final java.util.Map<OrderStatus, java.util.Set<OrderStatus>> ALLOWED_TRANSITIONS =
            java.util.Map.of(
                    OrderStatus.DRAFT, java.util.Set.of(OrderStatus.PENDING),
                    OrderStatus.PENDING, java.util.Set.of(OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED, OrderStatus.REJECTED),
                    OrderStatus.PENDING_APPROVAL, java.util.Set.of(OrderStatus.APPROVED, OrderStatus.REJECTED),
                    OrderStatus.APPROVED, java.util.Set.of(OrderStatus.ROUTED, OrderStatus.REJECTED),
                    OrderStatus.ROUTED, java.util.Set.of(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED),
                    OrderStatus.PARTIALLY_FILLED, java.util.Set.of(OrderStatus.FILLED, OrderStatus.CANCELLED),
                    OrderStatus.FILLED, java.util.Set.of(),
                    OrderStatus.CANCELLED, java.util.Set.of(),
                    OrderStatus.REJECTED, java.util.Set.of()
            );

    private final OrderStore orderStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter fillsProcessedCounter;

    public OrderLifecycleService(OrderStore orderStore, Executor executor,
                                  Consumer<Object> eventPublisher, MeterRegistry meterRegistry) {
        this.orderStore = orderStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.fillsProcessedCounter = meterRegistry.counter("oms.fills.processed");
    }

    /** Transition order to a new status, enforcing the allowed transition matrix. */
    public Promise<Order> transition(String orderId, OrderStatus newStatus, String reason) {
        return Promise.ofBlocking(executor, () -> {
            var order = findOrThrow(orderId);
            assertTransitionAllowed(order.status(), newStatus);
            Order updated = newStatus == OrderStatus.REJECTED
                    ? order.withRejection(reason)
                    : order.withStatus(newStatus);
            orderStore.update(updated).get();
            eventPublisher.accept(new OrderStatusChangedEvent(orderId, order.status(), newStatus, Instant.now()));
            log.info("Order transitioned: orderId={} {}→{}", orderId, order.status(), newStatus);
            return updated;
        });
    }

    /** Apply an execution fill to the order (D01-014). Idempotent by execId. */
    public Promise<Order> applyFill(String orderId, String execId,
                                     BigDecimal fillQty, BigDecimal fillPrice, BigDecimal fees) {
        return Promise.ofBlocking(executor, () -> {
            var order = findOrThrow(orderId);

            // Idempotency: skip if execId already applied
            boolean alreadyApplied = order.fills().stream()
                    .anyMatch(f -> f.execId().equals(execId));
            if (alreadyApplied) {
                return order;
            }

            Fill fill = new Fill(UUID.randomUUID().toString(), orderId, execId,
                    fillQty, fillPrice, fees, Instant.now());

            // Weighted average fill price
            BigDecimal totalValue = order.avgFillPrice() != null
                    ? order.avgFillPrice().multiply(order.filledQuantity()).add(fillPrice.multiply(fillQty))
                    : fillPrice.multiply(fillQty);
            BigDecimal newFilledQty = order.filledQuantity().add(fillQty);
            BigDecimal newAvgPrice = totalValue.divide(newFilledQty, 8, RoundingMode.HALF_EVEN);

            Order updated = order.withFill(fill, newAvgPrice);
            orderStore.update(updated).get();

            eventPublisher.accept(new FillReceivedEvent(orderId, execId, fillQty, fillPrice, newAvgPrice, Instant.now()));
            fillsProcessedCounter.increment();
            log.info("Fill applied: orderId={} execId={} qty={} price={} avgPrice={}",
                    orderId, execId, fillQty, fillPrice, newAvgPrice);
            return updated;
        });
    }

    /** Cancel an order with a reason code. */
    public Promise<Order> cancel(String orderId, String reason) {
        return Promise.ofBlocking(executor, () -> {
            var order = findOrThrow(orderId);
            assertTransitionAllowed(order.status(), OrderStatus.CANCELLED);
            Order updated = order.withRejection(reason).withStatus(OrderStatus.CANCELLED);
            orderStore.update(updated).get();
            eventPublisher.accept(new OrderCancelledEvent(orderId, reason, Instant.now()));
            log.info("Order cancelled: orderId={} reason={}", orderId, reason);
            return updated;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Order findOrThrow(String orderId) throws Exception {
        return orderStore.findById(orderId).get()
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, java.util.Set.of()).contains(to)) {
            throw new InvalidOrderTransitionException(from, to);
        }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderStatusChangedEvent(String orderId, OrderStatus from, OrderStatus to, Instant at) {}
    public record FillReceivedEvent(String orderId, String execId, BigDecimal fillQty,
                                     BigDecimal fillPrice, BigDecimal avgPrice, Instant at) {}
    public record OrderCancelledEvent(String orderId, String reason, Instant at) {}

    // ─── Exceptions ───────────────────────────────────────────────────────────

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String orderId) {
            super("Order not found: " + orderId);
        }
    }

    public static class InvalidOrderTransitionException extends RuntimeException {
        public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
            super("Invalid order transition: " + from + " → " + to);
        }
    }
}
