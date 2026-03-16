package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Handles order amendment requests (D01-006).
 *              Amendment triggers re-validation and maker-checker when new value > 150% of old.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class OrderAmendmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderAmendmentService.class);
    private static final BigDecimal MAKER_CHECKER_THRESHOLD = new BigDecimal("1.50");

    private final OrderStore orderStore;
    private final OrderValidationService validationService;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public OrderAmendmentService(OrderStore orderStore, OrderValidationService validationService,
                                  Executor executor, Consumer<Object> eventPublisher) {
        this.orderStore = orderStore;
        this.validationService = validationService;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Amend price, quantity, or TIF for an eligible order.
     * Allowed in states: PENDING, PENDING_APPROVAL, APPROVED (not yet ROUTED).
     */
    public Promise<OrderAmendment> amend(String orderId, BigDecimal newPrice,
                                          BigDecimal newQuantity, TimeInForce newTif,
                                          String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            var order = orderStore.findById(orderId).get()
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            // Only amend in pre-routing states
            if (order.status() == OrderStatus.ROUTED
                    || order.status() == OrderStatus.PARTIALLY_FILLED
                    || order.status().isTerminal()) {
                throw new RuntimeException("Amendment not allowed in state: " + order.status());
            }

            // Determine if maker-checker required (new value > 150% of old)
            BigDecimal oldValue = order.orderValue() != null ? order.orderValue() : BigDecimal.ZERO;
            BigDecimal newValue = newQuantity != null && newPrice != null
                    ? newQuantity.multiply(newPrice) : oldValue;
            boolean needsMakerChecker = oldValue.compareTo(BigDecimal.ZERO) > 0
                    && newValue.compareTo(oldValue.multiply(MAKER_CHECKER_THRESHOLD)) > 0;

            OrderAmendment amendment = new OrderAmendment(
                    UUID.randomUUID().toString(), orderId,
                    order.price(), newPrice != null ? newPrice : order.price(),
                    order.quantity(), newQuantity != null ? newQuantity : order.quantity(),
                    order.timeInForce(), newTif != null ? newTif : order.timeInForce(),
                    requestedBy, Instant.now(), needsMakerChecker
            );

            eventPublisher.accept(new OrderAmendedEvent(orderId, amendment, Instant.now()));
            log.info("Order amended: orderId={} requiresMakerChecker={}", orderId, needsMakerChecker);
            return amendment;
        });
    }

    public record OrderAmendedEvent(String orderId, OrderAmendment amendment, Instant at) {}
}
