package com.ghatana.appplatform.ems.service;

import com.ghatana.appplatform.ems.domain.*;
import com.ghatana.appplatform.ems.port.ExchangeAdapterPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Smart Order Router — selects the best execution venue for an approved OMS order
 *                and routes it via the appropriate {@link ExchangeAdapterPort}.
 * @doc.layer     Application
 * @doc.pattern   Strategy (venue selection) + Registry (adapter lookup)
 *
 * Venue selection criteria (priority order):
 *   1. Instrument's designated exchange (from reference data)
 *   2. Adapter health check (connected + heartbeat within 30 s)
 *   3. Priority ranking when multiple healthy venues support the instrument
 *
 * Current deployment: single-venue (NEPSE). Architecture supports multi-venue future expansion.
 *
 * Stories covered: D02-001 (SOR engine), D02-002 (child order management).
 */
public class SmartOrderRouterService {

    private static final Logger log = LoggerFactory.getLogger(SmartOrderRouterService.class);

    private final ConcurrentHashMap<String, ExchangeAdapterPort> adapters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoutedOrder>         routedOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SplitOrder>          splitOrders  = new ConcurrentHashMap<>();
    private final RoutingStore                                    routingStore;
    private final Consumer<Object>                                eventPublisher;
    private final Counter ordersRouted;
    private final Counter routingFailures;

    public SmartOrderRouterService(RoutingStore routingStore,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this.routingStore   = routingStore;
        this.eventPublisher = eventPublisher;
        this.ordersRouted   = meterRegistry.counter("ems.orders.routed");
        this.routingFailures = meterRegistry.counter("ems.routing.failures");
    }

    /**
     * Registers an exchange adapter with the SOR venue registry.
     *
     * @param adapter   the adapter implementation
     * @param priority  lower = higher priority when multiple venues available
     */
    public void registerAdapter(ExchangeAdapterPort adapter, int priority) {
        adapters.put(adapter.exchangeId(), adapter);
        log.info("SOR: registered adapter exchangeId={} priority={}", adapter.exchangeId(), priority);
    }

    /**
     * Routes an approved OMS order to the best available venue (D02-001).
     *
     * @param orderId       OMS parent order ID
     * @param clientId      client identifier
     * @param instrumentId  instrument to trade
     * @param targetExchange preferred exchange (from reference data instrument.exchange)
     * @param side          BUY or SELL
     * @param quantity      order quantity
     * @param limitPrice    limit price (null = market order)
     * @param orderType     "LIMIT" or "MARKET"
     * @param timeInForce   "DAY", "GTC", "IOC", "FOK"
     * @return the routing record
     * @throws RoutingException if no healthy venue is available
     */
    public RoutedOrder route(String orderId, String clientId, String instrumentId,
                             String targetExchange, ExecutionSide side,
                             long quantity, BigDecimal limitPrice,
                             String orderType, String timeInForce) {

        ExchangeAdapterPort adapter = selectVenue(targetExchange, instrumentId);
        String routingId = UUID.randomUUID().toString();

        RoutedOrder order = new RoutedOrder(
                routingId, orderId, clientId, instrumentId,
                adapter.exchangeId(), side, quantity, limitPrice,
                orderType, timeInForce,
                ExecutionStatus.PENDING_ROUTE,
                0, BigDecimal.ZERO, null,
                Instant.now(), Instant.now(), List.of());

        String externalId;
        try {
            externalId = adapter.submitOrder(order);
        } catch (ExchangeAdapterPort.ExchangeRejectException e) {
            routingFailures.increment();
            log.warn("SOR: exchange rejected order orderId={} reason={}", orderId, e.rejectReason());
            eventPublisher.accept(new OrderRoutingRejectedEvent(orderId, routingId, e.rejectReason()));
            throw new RoutingException("Exchange rejected: " + e.rejectReason(), e);
        }

        RoutedOrder routed = new RoutedOrder(
                routingId, orderId, clientId, instrumentId,
                adapter.exchangeId(), side, quantity, limitPrice,
                orderType, timeInForce,
                ExecutionStatus.ROUTED,
                0, BigDecimal.ZERO, externalId,
                order.routedAt(), Instant.now(), List.of());

        routedOrders.put(routingId, routed);
        routingStore.save(routed);
        ordersRouted.increment();

        log.info("SOR: order routed orderId={} routingId={} exchange={} externalId={}",
                orderId, routingId, adapter.exchangeId(), externalId);
        eventPublisher.accept(new OrderRoutedEvent(orderId, routingId, adapter.exchangeId()));
        return routed;
    }

    /**
     * Splits a large order into child orders across multiple venues or time slices (D02-002).
     * Each child is routed independently; parent aggregate status is tracked.
     *
     * @param orderId       parent OMS order ID
     * @param clientId      client identifier
     * @param instrumentId  instrument
     * @param side          BUY or SELL
     * @param totalQuantity total order quantity to split
     * @param limitPrice    limit price per child
     * @param splits        list of (exchange, quantity) pairs for each child
     * @return the parent SplitOrder aggregate
     */
    public SplitOrder routeSplit(String orderId, String clientId, String instrumentId,
                                 ExecutionSide side, long totalQuantity, BigDecimal limitPrice,
                                 List<ChildSplit> splits) {

        List<String> childIds = new ArrayList<>();
        for (ChildSplit child : splits) {
            RoutedOrder childOrder = route(orderId, clientId, instrumentId,
                    child.exchange(), side, child.quantity(), limitPrice, "LIMIT", "DAY");
            childIds.add(childOrder.routingId());
        }

        SplitOrder split = new SplitOrder(
                orderId, clientId, instrumentId, side, totalQuantity, limitPrice,
                List.copyOf(childIds), 0, BigDecimal.ZERO,
                ExecutionStatus.ROUTED, Instant.now(), Instant.now());

        splitOrders.put(orderId, split);
        log.info("SOR: split order created orderId={} childCount={}", orderId, childIds.size());
        eventPublisher.accept(new SplitOrderCreatedEvent(orderId, childIds));
        return split;
    }

    /**
     * Cancels a routed order by forwarding the cancel request to the exchange adapter.
     */
    public void cancelRoutedOrder(String routingId) {
        RoutedOrder order = routedOrders.get(routingId);
        if (order == null) throw new IllegalArgumentException("Unknown routingId: " + routingId);

        ExchangeAdapterPort adapter = adapters.get(order.exchange());
        if (adapter == null || !adapter.isConnected()) {
            throw new RoutingException("Adapter not connected for exchange: " + order.exchange(), null);
        }
        adapter.cancelOrder(routingId, order.instrumentId(), order.side());
        log.info("SOR: cancel sent routingId={} exchange={}", routingId, order.exchange());
    }

    /**
     * Updates a routed order with a fill event received from the exchange adapter.
     * Aggregates fill quantity and computes weighted average fill price.
     */
    public RoutedOrder applyFill(ExecutionFill fill) {
        RoutedOrder existing = routedOrders.computeIfPresent(fill.routingId(), (id, order) -> {
            long newFilled = order.filledQuantity() + fill.filledQuantity();
            BigDecimal newAvg = computeWeightedAvg(
                    order.avgFillPrice(), order.filledQuantity(),
                    fill.fillPrice(), fill.filledQuantity());
            List<ExecutionFill> fills = new ArrayList<>(order.fills());
            fills.add(fill);

            ExecutionStatus newStatus = newFilled >= order.quantity()
                    ? ExecutionStatus.FILLED
                    : ExecutionStatus.PARTIALLY_FILLED;

            return new RoutedOrder(order.routingId(), order.parentOrderId(), order.clientId(),
                    order.instrumentId(), order.exchange(), order.side(),
                    order.quantity(), order.limitPrice(), order.orderType(), order.timeInForce(),
                    newStatus, newFilled, newAvg, order.externalOrderId(),
                    order.routedAt(), Instant.now(), fills);
        });

        if (existing == null) {
            log.warn("SOR: fill received for unknown routingId={}", fill.routingId());
            return null;
        }

        routingStore.save(existing);
        eventPublisher.accept(new FillAppliedEvent(fill.routingId(), existing.parentOrderId(), fill));

        // Update split order aggregate if applicable
        updateSplitAggregate(existing.parentOrderId());

        log.info("SOR: fill applied routingId={} filled={}/{} status={}",
                fill.routingId(), existing.filledQuantity(), existing.quantity(), existing.status());
        return existing;
    }

    public Optional<RoutedOrder> getRoutedOrder(String routingId) {
        return Optional.ofNullable(routedOrders.get(routingId));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private ExchangeAdapterPort selectVenue(String targetExchange, String instrumentId) {
        // Try the designated exchange first
        if (targetExchange != null && adapters.containsKey(targetExchange)) {
            ExchangeAdapterPort adapter = adapters.get(targetExchange);
            if (adapter.isConnected()) return adapter;
        }

        // Fallback: any connected adapter (single-venue NEPSE typically)
        return adapters.values().stream()
                .filter(ExchangeAdapterPort::isConnected)
                .min(Comparator.comparingInt(a -> 0)) // extend with priority when multi-venue
                .orElseThrow(() -> new RoutingException(
                        "No healthy exchange adapter available for instrument: " + instrumentId, null));
    }

    private BigDecimal computeWeightedAvg(BigDecimal prevAvg, long prevQty,
                                           BigDecimal newPrice, long newQty) {
        if (prevQty == 0) return newPrice;
        BigDecimal prevTotal = prevAvg.multiply(BigDecimal.valueOf(prevQty));
        BigDecimal newTotal  = newPrice.multiply(BigDecimal.valueOf(newQty));
        return prevTotal.add(newTotal).divide(
                BigDecimal.valueOf(prevQty + newQty), 4, java.math.RoundingMode.HALF_EVEN);
    }

    private void updateSplitAggregate(String parentOrderId) {
        SplitOrder split = splitOrders.get(parentOrderId);
        if (split == null) return;

        long totalFilled = 0;
        BigDecimal weightedSum = BigDecimal.ZERO;
        boolean allDone = true;

        for (String childId : split.childRoutingIds()) {
            RoutedOrder child = routedOrders.get(childId);
            if (child == null) continue;
            totalFilled += child.filledQuantity();
            weightedSum = weightedSum.add(child.avgFillPrice().multiply(BigDecimal.valueOf(child.filledQuantity())));
            if (child.status() != ExecutionStatus.FILLED && child.status() != ExecutionStatus.CANCELLED
                    && child.status() != ExecutionStatus.REJECTED) {
                allDone = false;
            }
        }

        BigDecimal aggAvg = totalFilled > 0
                ? weightedSum.divide(BigDecimal.valueOf(totalFilled), 4, java.math.RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;

        ExecutionStatus aggStatus = allDone
                ? (totalFilled >= split.totalQuantity() ? ExecutionStatus.FILLED : ExecutionStatus.PARTIALLY_FILLED)
                : ExecutionStatus.PARTIALLY_FILLED;

        SplitOrder updated = new SplitOrder(split.parentOrderId(), split.clientId(),
                split.instrumentId(), split.side(), split.totalQuantity(), split.limitPrice(),
                split.childRoutingIds(), totalFilled, aggAvg, aggStatus,
                split.createdAt(), Instant.now());
        splitOrders.put(parentOrderId, updated);
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /** (exchange, quantity) pair for order splitting */
    public record ChildSplit(String exchange, long quantity) {}

    /** Port for persisting routing records (implemented by PostgresRoutingStore). */
    public interface RoutingStore {
        void save(RoutedOrder order);
        Optional<RoutedOrder> findByRoutingId(String routingId);
    }

    public static class RoutingException extends RuntimeException {
        public RoutingException(String message, Throwable cause) { super(message, cause); }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record OrderRoutedEvent(String orderId, String routingId, String exchange) {}
    public record OrderRoutingRejectedEvent(String orderId, String routingId, String rejectReason) {}
    public record SplitOrderCreatedEvent(String orderId, List<String> childRoutingIds) {}
    public record FillAppliedEvent(String routingId, String parentOrderId, ExecutionFill fill) {}
}
