package com.ghatana.appplatform.oms.service;

import com.ghatana.appplatform.oms.domain.*;
import com.ghatana.appplatform.oms.port.OrderStore;
import com.ghatana.appplatform.refdata.port.InstrumentStore;
import com.ghatana.appplatform.marketdata.port.L1Cache;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Captures incoming orders: validates, enriches, persists, and emits
 *              OrderPlaced event. Implements D01-001 + D01-002.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class OrderCaptureService {

    private static final Logger log = LoggerFactory.getLogger(OrderCaptureService.class);

    private final OrderStore orderStore;
    private final InstrumentStore instrumentStore;
    private final L1Cache l1Cache;
    private final OrderValidationService validationService;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;
    private final Counter ordersPlacedCounter;

    public OrderCaptureService(OrderStore orderStore, InstrumentStore instrumentStore,
                                L1Cache l1Cache, OrderValidationService validationService,
                                Executor executor, Consumer<Object> eventPublisher,
                                MeterRegistry meterRegistry) {
        this.orderStore = orderStore;
        this.instrumentStore = instrumentStore;
        this.l1Cache = l1Cache;
        this.validationService = validationService;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
        this.ordersPlacedCounter = meterRegistry.counter("oms.orders.placed");
    }

    /**
     * Captures and validates a new order (D01-001).
     * Returns the persisted order or fails with a domain exception.
     */
    public Promise<Order> captureOrder(OrderCaptureRequest request) {
        return Promise.ofBlocking(executor, () -> {
            // 1. Idempotency check
            var existing = orderStore.findByIdempotencyKey(request.idempotencyKey())
                    .get();
            if (existing.isPresent()) {
                log.debug("Idempotent order replay: idempotencyKey={}", request.idempotencyKey());
                return existing.get();
            }

            // 2. Instrument lookup + must be ACTIVE
            var instrument = instrumentStore
                    .findCurrentById(request.instrumentId())
                    .get()
                    .orElseThrow(() -> new UnknownInstrumentException(request.instrumentId()));

            if (instrument.status().name().equals("ACTIVE") == false) {
                throw new InactiveInstrumentException(request.instrumentId(), instrument.status().name());
            }

            // 3. Field validation (type-specific, lot size, tick size)
            validationService.validateFields(request, instrument);

            // 4. Enrich with market price (D01-002)
            BigDecimal arrivalPrice = l1Cache.getL1(request.instrumentId())
                    .get()
                    .map(q -> q.lastPrice())
                    .orElse(BigDecimal.ZERO);

            BigDecimal orderValue = request.price() != null
                    ? request.quantity().multiply(request.price())
                    : request.quantity().multiply(arrivalPrice);

            // 5. Dual-calendar timestamp (simplified — K-15 would provide bsDate)
            Instant now = Instant.now();
            String nowBs = ""; // populated by K-15 in production

            Order order = Order.newOrder(
                    UUID.randomUUID().toString(),
                    request.clientId(), request.accountId(), request.instrumentId(),
                    request.side(), request.orderType(), request.timeInForce(),
                    request.quantity(), request.price(), request.stopPrice(),
                    request.idempotencyKey(), now, nowBs
            ).withEnrichment(instrument.symbol(), instrument.exchange(),
                    instrument.currency(), orderValue, arrivalPrice);

            orderStore.save(order).get();

            eventPublisher.accept(new OrderPlacedEvent(order.orderId(), request.clientId(),
                    request.instrumentId(), request.side(), request.quantity(), now));

            ordersPlacedCounter.increment();
            log.info("Order captured: orderId={} client={} instrument={} side={} qty={}",
                    order.orderId(), request.clientId(), request.instrumentId(),
                    request.side(), request.quantity());
            return order;
        });
    }

    // ─── Domain events ────────────────────────────────────────────────────────

    public record OrderPlacedEvent(
            String orderId, String clientId, String instrumentId,
            OrderSide side, BigDecimal quantity, Instant placedAt
    ) {}

    // ─── Domain exceptions ────────────────────────────────────────────────────

    public static class UnknownInstrumentException extends RuntimeException {
        public UnknownInstrumentException(String instrumentId) {
            super("Unknown instrument: " + instrumentId);
        }
    }

    public static class InactiveInstrumentException extends RuntimeException {
        public InactiveInstrumentException(String instrumentId, String status) {
            super("Instrument " + instrumentId + " is not active: " + status);
        }
    }

    // ─── Request record ───────────────────────────────────────────────────────

    public record OrderCaptureRequest(
            String clientId,
            String accountId,
            String instrumentId,
            OrderSide side,
            OrderType orderType,
            TimeInForce timeInForce,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal stopPrice,
            String idempotencyKey
    ) {}
}
