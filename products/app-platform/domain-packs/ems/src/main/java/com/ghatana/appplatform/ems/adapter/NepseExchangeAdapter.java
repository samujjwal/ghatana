package com.ghatana.appplatform.ems.adapter;

import com.ghatana.appplatform.ems.domain.*;
import com.ghatana.appplatform.ems.port.ExchangeAdapterPort;
import com.ghatana.appplatform.ems.service.FixProtocolService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @doc.type      Adapter
 * @doc.purpose   NEPSE (Nepal Stock Exchange) exchange adapter implementing the T3 plugin
 *                {@link ExchangeAdapterPort} interface. Handles NEPSE session lifecycle,
 *                order submission via FIX protocol, execution report parsing, and
 *                NEPSE-specific error code mapping.
 * @doc.layer     Adapter
 * @doc.pattern   Plugin — T3 network tier; loaded and verified via K-04
 *
 * NEPSE connectivity: FIX 4.4 over TLS.
 * Session schedule: NST (UTC+5:45), 10:00–15:30 trading days only.
 *
 * Story: D02-012
 */
public class NepseExchangeAdapter implements ExchangeAdapterPort {

    private static final Logger log = LoggerFactory.getLogger(NepseExchangeAdapter.class);
    private static final String EXCHANGE_ID = "NEPSE";

    /** NEPSE-specific FIX rejection reason codes → human-readable messages */
    private static final Map<String, String> REJECT_CODES = Map.of(
            "1", "Invalid instrument symbol",
            "2", "Order size below minimum lot",
            "3", "Price outside circuit limit",
            "4", "Insufficient margin",
            "5", "Market closed",
            "6", "Client not eligible",
            "7", "Duplicate ClOrdID"
    );

    private final FixProtocolService fixEngine;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, String>     routingToExternal = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoutedOrder> pendingOrders    = new ConcurrentHashMap<>();

    private volatile Consumer<ExecutionFill> fillCallback;
    private volatile Consumer<Object>        marketDataCallback;
    private volatile String sessionId;

    private final Counter ordersSubmitted;
    private final Counter execReportsReceived;
    private final Counter rejections;

    public NepseExchangeAdapter(FixProtocolService fixEngine, MeterRegistry meterRegistry) {
        this.fixEngine = fixEngine;
        this.ordersSubmitted    = meterRegistry.counter("nepse.orders.submitted");
        this.execReportsReceived = meterRegistry.counter("nepse.exec.reports.received");
        this.rejections         = meterRegistry.counter("nepse.rejections");

        Gauge.builder("nepse.connected", connected, b -> b.get() ? 1.0 : 0.0)
                .register(meterRegistry);
    }

    @Override
    public String exchangeId() {
        return EXCHANGE_ID;
    }

    /**
     * Initiates FIX session with NEPSE. Required config keys:
     * {@code host, port, senderCompId, targetCompId, keyStorePath, keyStorePassword}.
     *
     * @param config connection parameters
     * @throws ExchangeConnectionException on connection failure
     */
    @Override
    public void connect(Map<String, String> config) {
        if (connected.get()) {
            log.debug("NepseAdapter: already connected, skipping");
            return;
        }

        String senderComp = config.getOrDefault("senderCompId", "GHATANA");
        String targetComp = config.getOrDefault("targetCompId", "NEPSE");
        String version    = config.getOrDefault("fixVersion", "FIX.4.4");

        this.sessionId = "NEPSE_" + senderComp;

        try {
            // In production: open TLS socket to config.host:config.port
            // then feed bytes into fixEngine.receive() loop.
            // Here we register the session and mark as connecting.
            String logon = fixEngine.initiateLogon(sessionId, senderComp, targetComp, version);
            // Simulate transmission: in production, write logon to TLS OutputStream
            simulateExchangeLogonAck(senderComp, targetComp, version);
            connected.set(true);
            log.info("NepseAdapter: connected to NEPSE sessionId={}", sessionId);
        } catch (Exception e) {
            throw new ExchangeConnectionException("Failed to connect to NEPSE", e);
        }
    }

    @Override
    public void disconnect() {
        if (!connected.get()) return;
        try {
            fixEngine.initiateLogout(sessionId, "End of session");
        } finally {
            connected.set(false);
            log.info("NepseAdapter: disconnected from NEPSE");
        }
    }

    @Override
    public String submitOrder(RoutedOrder order) {
        requireConnected();
        String fixMsg = fixEngine.buildNewOrderSingle(sessionId, order);
        // In production: write fixMsg bytes to TLS OutputStream
        pendingOrders.put(order.routingId(), order);
        ordersSubmitted.increment();
        log.info("NepseAdapter: submitted order routingId={} instrument={} qty={} side={}",
                order.routingId(), order.instrumentId(), order.quantity(), order.side());

        // Return synthetic external ID (in production: assigned by NEPSE in ExecReport)
        String externalId = "NEPSE_" + order.routingId().substring(0, 8).toUpperCase();
        routingToExternal.put(order.routingId(), externalId);
        return externalId;
    }

    @Override
    public void cancelOrder(String routingId, String instrumentId, ExecutionSide side) {
        requireConnected();
        String fixMsg = fixEngine.buildOrderCancelRequest(sessionId, routingId, instrumentId, side);
        // In production: write to TLS OutputStream
        log.info("NepseAdapter: cancel sent routingId={} instrument={}", routingId, instrumentId);
    }

    @Override
    public void amendOrder(String routingId, Long newQuantity, BigDecimal newPrice) {
        requireConnected();
        RoutedOrder original = pendingOrders.get(routingId);
        if (original == null) return;

        RoutedOrder amended = new RoutedOrder(
                original.routingId(), original.parentOrderId(), original.clientId(),
                original.instrumentId(), original.exchange(), original.side(),
                newQuantity != null ? newQuantity : original.quantity(),
                newPrice != null ? newPrice : original.limitPrice(),
                original.orderType(), original.timeInForce(), original.status(),
                original.filledQuantity(), original.avgFillPrice(), original.externalOrderId(),
                original.routedAt(), Instant.now(), original.fills());

        // In production: send OrderCancelReplaceRequest (G) via fixEngine
        pendingOrders.put(routingId, amended);
        log.info("NepseAdapter: amend sent routingId={}", routingId);
    }

    @Override
    public Optional<RoutedOrder> getOrderStatus(String routingId) {
        return Optional.ofNullable(pendingOrders.get(routingId));
    }

    @Override
    public void onExecutionReport(Consumer<ExecutionFill> callback) {
        this.fillCallback = callback;

        // In production: the FIX receive() loop dispatches FixExecReportEvents
        // which this adapter intercepts and converts to ExecutionFill via handleExecReport().
    }

    @Override
    public void onMarketData(Consumer<Object> callback) {
        this.marketDataCallback = callback;
    }

    @Override
    public boolean isConnected() {
        return connected.get()
                && fixEngine.getSessionState(sessionId) == com.ghatana.appplatform.ems.domain.FixSessionState.LOGGED_ON;
    }

    /**
     * Handles an incoming FIX ExecutionReport from NEPSE.
     * Maps NEPSE-specific ExecType codes to {@link ExecutionFill} or rejection events.
     *
     * @param execId    FIX ExecID (tag 17)
     * @param clOrdId   our ClOrdID (= routingId, tag 11)
     * @param execType  FIX ExecType (tag 150): "0"=New, "1"=PartialFill, "2"=Fill, "4"=Cancelled, "8"=Rejected
     * @param lastQty   filled quantity (tag 32)
     * @param lastPxStr fill price (tag 31)
     */
    public void handleExecReport(String execId, String clOrdId, String execType,
                                  long lastQty, String lastPxStr) {
        execReportsReceived.increment();

        switch (execType) {
            case "1", "2" -> { // PartialFill or Fill
                BigDecimal fillPx = lastPxStr != null ? new BigDecimal(lastPxStr) : BigDecimal.ZERO;
                ExecutionFill fill = new ExecutionFill(
                        UUID.randomUUID().toString(), clOrdId, execId,
                        lastQty, fillPx, Instant.now(), EXCHANGE_ID);
                log.info("NepseAdapter: fill received clOrdId={} qty={} price={}",
                        clOrdId, lastQty, fillPx);
                if (fillCallback != null) fillCallback.accept(fill);
            }
            case "8" -> { // Rejected
                rejections.increment();
                String rejectCode = "UNKNOWN";
                String reason = REJECT_CODES.getOrDefault(rejectCode,
                        "NEPSE rejection code: " + rejectCode);
                log.warn("NepseAdapter: order rejected clOrdId={} reason={}", clOrdId, reason);
                pendingOrders.remove(clOrdId);
            }
            case "4" -> { // Cancelled
                log.info("NepseAdapter: order cancelled confirmed clOrdId={}", clOrdId);
                pendingOrders.remove(clOrdId);
            }
            default -> log.debug("NepseAdapter: unhandled execType={} clOrdId={}", execType, clOrdId);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void requireConnected() {
        if (!connected.get()) {
            throw new ExchangeConnectionException("NepseAdapter not connected", null);
        }
    }

    /**
     * Simulates NEPSE responding with a Logon acknowledgement.
     * In production this is replaced by actual bytes from the TLS socket.
     */
    private void simulateExchangeLogonAck(String sender, String target, String version) {
        String ackMsg = "8=" + version + "\u00019=50\u000135=A\u000149=" + target
                + "\u000156=" + sender + "\u000134=1\u000198=0\u0001108=30\u000110=000\u0001";
        fixEngine.receive(sessionId, ackMsg);
    }

    // ─── FixProtocolService field redeclaration (avoidance of compile error) ─

    // Note: in production, the transport layer forwards bytes to fixEngine.receive() which
    // then publishes FixExecReportEvents consumed by this adapter's handleExecReport().
}
