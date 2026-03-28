package com.ghatana.products.finance.domains.oms.service;


import com.ghatana.platform.core.event.EventBusPort;
import com.ghatana.products.finance.domains.oms.domain.*;
import com.ghatana.products.finance.domains.oms.port.DualCalendarPort;
import com.ghatana.products.finance.domains.oms.port.OrderStore;
import com.ghatana.products.finance.domains.referencedata.port.InstrumentStore;
import com.ghatana.products.finance.domains.marketdata.port.L1Cache;
import com.ghatana.platform.core.exception.ResourceNotFoundException;
import com.ghatana.platform.core.exception.ServiceException;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

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
    private final EventBusPort eventBusPort;
    private final DualCalendarPort dualCalendar;
    private final Counter ordersPlacedCounter;

    public OrderCaptureService(OrderStore orderStore, InstrumentStore instrumentStore,
                                L1Cache l1Cache, OrderValidationService validationService,
                                Executor executor, EventBusPort eventBusPort,
                                DualCalendarPort dualCalendar,
                                MeterRegistry meterRegistry) {
        this.orderStore = orderStore;
        this.instrumentStore = instrumentStore;
        this.l1Cache = l1Cache;
        this.validationService = validationService;
        this.executor = executor;
        this.eventBusPort = eventBusPort;
        this.dualCalendar = dualCalendar;
        this.ordersPlacedCounter = meterRegistry.counter("oms.orders.placed");
    }

    /**
     * Captures and validates a new order (D01-001).
     * Returns the persisted order or fails with a domain exception.
     */
    public Promise<Order> captureOrder(OrderCaptureRequest request) {
        // Validate idempotencyKey: must be non-null, non-blank, alphanumeric/dash/underscore only
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("idempotencyKey must not be blank"));
        }
        if (!request.idempotencyKey().matches("[a-zA-Z0-9_-]{1,128}")) {
            return Promise.ofException(new IllegalArgumentException(
                    "idempotencyKey must be 1-128 alphanumeric, dash, or underscore characters"));
        }

        // 1. Idempotency check (async promise chain — no .getResult())
        return orderStore.findByIdempotencyKey(request.idempotencyKey())
                .then(existing -> {
                    if (existing.isPresent()) {
                        log.debug("Idempotent order replay: idempotencyKey={}", request.idempotencyKey());
                        return Promise.of(existing.get());
                    }

                    // 2. Instrument lookup + must be ACTIVE
                    return instrumentStore.findCurrentById(UUID.fromString(request.instrumentId()))
                            .then(instrumentOpt -> {
                                var instrument = instrumentOpt.orElseThrow(
                                        () -> new UnknownInstrumentException(request.instrumentId()));

                                if (!instrument.status().name().equals("ACTIVE")) {
                                    return Promise.<Order>ofException(
                                            new InactiveInstrumentException(request.instrumentId(),
                                                    instrument.status().name()));
                                }

                                // 3. Field validation (type-specific, lot size, tick size)
                                validationService.validateFields(request, instrument);

                                // 4. Enrich with market price (D01-002)
                                return l1Cache.getL1(request.instrumentId())
                                        .then(quoteOpt -> {
                                            BigDecimal arrivalPrice = quoteOpt
                                                    .map(q -> q.lastPrice())
                                                    .orElse(BigDecimal.ZERO);

                                            BigDecimal orderValue = request.price() != null
                                                    ? request.quantity().multiply(request.price())
                                                    : request.quantity().multiply(arrivalPrice);

                                            // 5. Dual-calendar timestamp (K-15 DualCalendarKernelExtension)
                                            Instant now = Instant.now();
                                            String nowBs = dualCalendar.toBsDateString(now);

                                            Order order = Order.newOrder(
                                                    UUID.randomUUID().toString(),
                                                    request.clientId(), request.accountId(),
                                                    request.instrumentId(),
                                                    request.side(), request.orderType(),
                                                    request.timeInForce(),
                                                    request.quantity(), request.price(),
                                                    request.stopPrice(),
                                                    request.idempotencyKey(), now, nowBs
                                            ).withEnrichment(instrument.symbol(), instrument.exchange(),
                                                    instrument.currency(), orderValue, arrivalPrice);

                                            // 6. Persist (async — no .getResult())
                                            return orderStore.save(order)
                                                    .map($ -> {
                                                        eventBusPort.publish(new OrderPlacedEvent(
                                                                order.orderId(), request.clientId(),
                                                                request.instrumentId(), request.side(),
                                                                request.quantity(), now));

                                                        ordersPlacedCounter.increment();
                                                        log.info("Order captured: orderId={} client={} instrument={} side={} qty={}",
                                                                order.orderId(), request.clientId(),
                                                                request.instrumentId(),
                                                                request.side(), request.quantity());
                                                        return order;
                                                    });
                                        });
                            });
                });
    }

    // ─── Domain events ────────────────────────────────────────────────────────

    public record OrderPlacedEvent(
            String orderId, String clientId, String instrumentId,
            OrderSide side, BigDecimal quantity, Instant placedAt
    ) {}

    // ─── Domain exceptions ────────────────────────────────────────────────────

    public static class UnknownInstrumentException extends ResourceNotFoundException {
        public UnknownInstrumentException(String instrumentId) {
            super("Unknown instrument: " + instrumentId);
        }
    }

    public static class InactiveInstrumentException extends ServiceException {
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
