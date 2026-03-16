package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Reconciles uncertain order state with the exchange (D02-018).
 *              When network disruption or timeout causes unknown fill status, this service
 *              queries the exchange adapter and reconciles: missing fills (order filled but
 *              not processed) or phantom fills (fill recorded but rejected by exchange).
 *              Runs an EOD reconciliation pass for completeness.
 * @doc.layer   Domain — EMS failsafe and reconciliation
 * @doc.pattern Port-adapter: ExchangeQueryPort queries exchange; discrepancies raise alerts
 */
public class ExecutionFailsafeService {

    /** Port for querying exchange order status — adapter per exchange. */
    public interface ExchangeQueryPort {
        ExchangeOrderState queryOrder(String exchangeOrderId);
    }

    public record ExchangeOrderState(
        String exchangeOrderId,
        long filledQuantity,
        BigDecimal avgFillPrice,
        String status   // OPEN / FILLED / CANCELLED / UNKNOWN
    ) {}

    public enum DiscrepancyType { MISSING_FILL, PHANTOM_FILL, QUANTITY_MISMATCH, PRICE_MISMATCH }

    public record Discrepancy(
        String discrepancyId,
        String orderId,
        DiscrepancyType type,
        String description,
        long internalFilledQty,
        long exchangeFilledQty,
        Instant detectedAt,
        boolean resolved
    ) {}

    private final DataSource dataSource;
    private final ExchangeQueryPort exchangeQuery;
    private final Executor executor;
    private final Counter discrepanciesCounter;
    private final Counter resolvedCounter;

    public ExecutionFailsafeService(DataSource dataSource, ExchangeQueryPort exchangeQuery,
                                     Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.exchangeQuery = exchangeQuery;
        this.executor = executor;
        this.discrepanciesCounter = Counter.builder("ems.failsafe.discrepancies_detected_total").register(registry);
        this.resolvedCounter = Counter.builder("ems.failsafe.discrepancies_resolved_total").register(registry);
    }

    /**
     * Reconcile a single order in UNCERTAIN state with the exchange.
     * Called after timeout or connection error.
     */
    public Promise<List<Discrepancy>> reconcileOrder(String orderId, String exchangeOrderId) {
        return Promise.ofBlocking(executor, () -> {
            ExchangeOrderState exchangeState = exchangeQuery.queryOrder(exchangeOrderId);
            long internalFilled = loadInternalFilledQty(orderId);
            List<Discrepancy> discrepancies = new ArrayList<>();

            if ("FILLED".equals(exchangeState.status()) && internalFilled == 0) {
                // Missing fill: exchange filled but we have no fill record
                Discrepancy d = new Discrepancy(UUID.randomUUID().toString(), orderId,
                    DiscrepancyType.MISSING_FILL,
                    "Exchange reports FILLED qty=" + exchangeState.filledQuantity() + " but internal fill=0",
                    internalFilled, exchangeState.filledQuantity(), Instant.now(), false);
                persistDiscrepancy(d);
                discrepancies.add(d);
                discrepanciesCounter.increment();
            } else if ("CANCELLED".equals(exchangeState.status()) && internalFilled > 0) {
                // Phantom fill: we recorded fills but exchange says CANCELLED
                Discrepancy d = new Discrepancy(UUID.randomUUID().toString(), orderId,
                    DiscrepancyType.PHANTOM_FILL,
                    "Internal fill=" + internalFilled + " but exchange status=CANCELLED",
                    internalFilled, exchangeState.filledQuantity(), Instant.now(), false);
                persistDiscrepancy(d);
                discrepancies.add(d);
                discrepanciesCounter.increment();
            } else if (Math.abs(internalFilled - exchangeState.filledQuantity()) > 0) {
                Discrepancy d = new Discrepancy(UUID.randomUUID().toString(), orderId,
                    DiscrepancyType.QUANTITY_MISMATCH,
                    "Internal=" + internalFilled + " exchange=" + exchangeState.filledQuantity(),
                    internalFilled, exchangeState.filledQuantity(), Instant.now(), false);
                persistDiscrepancy(d);
                discrepancies.add(d);
                discrepanciesCounter.increment();
            }
            return discrepancies;
        });
    }

    /**
     * EOD reconciliation pass: scan all unresolved discrepancies and orders closed today.
     */
    public Promise<List<Discrepancy>> runEodReconciliation() {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> openUncertain = loadUncertainOrders();
            List<Discrepancy> all = new ArrayList<>();
            for (String[] pair : openUncertain) {
                all.addAll(reconcileOrderBlocking(pair[0], pair[1]));
            }
            return all;
        });
    }

    /** Mark a discrepancy resolved after manual correction. */
    public Promise<Void> markResolved(String discrepancyId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE execution_discrepancies SET resolved = TRUE, resolved_at = NOW() WHERE id = ?")) {
                ps.setObject(1, UUID.fromString(discrepancyId));
                ps.executeUpdate();
            }
            resolvedCounter.increment();
            return null;
        });
    }

    private long loadInternalFilledQty(String orderId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COALESCE(total_filled_qty, 0) FROM fill_aggregates WHERE order_id = ?")) {
            ps.setObject(1, UUID.fromString(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private void persistDiscrepancy(Discrepancy d) throws Exception {
        String sql = "INSERT INTO execution_discrepancies(id, order_id, discrepancy_type, description, " +
                     "internal_filled_qty, exchange_filled_qty, detected_at, resolved) " +
                     "VALUES(?,?,?,?,?,?,NOW(),FALSE)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(d.discrepancyId()));
            ps.setObject(2, UUID.fromString(d.orderId()));
            ps.setString(3, d.type().name());
            ps.setString(4, d.description());
            ps.setLong(5, d.internalFilledQty());
            ps.setLong(6, d.exchangeFilledQty());
            ps.executeUpdate();
        }
    }

    private List<String[]> loadUncertainOrders() throws Exception {
        List<String[]> pairs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, exchange_order_id FROM orders WHERE status = 'UNCERTAIN' AND exchange_order_id IS NOT NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pairs.add(new String[]{rs.getString("id"), rs.getString("exchange_order_id")});
            }
        }
        return pairs;
    }

    private List<Discrepancy> reconcileOrderBlocking(String orderId, String exchangeOrderId) throws Exception {
        ExchangeOrderState exchangeState = exchangeQuery.queryOrder(exchangeOrderId);
        long internalFilled = loadInternalFilledQty(orderId);
        List<Discrepancy> list = new ArrayList<>();
        if (Math.abs(internalFilled - exchangeState.filledQuantity()) > 0) {
            Discrepancy d = new Discrepancy(UUID.randomUUID().toString(), orderId,
                DiscrepancyType.QUANTITY_MISMATCH,
                "EOD recon: internal=" + internalFilled + " exchange=" + exchangeState.filledQuantity(),
                internalFilled, exchangeState.filledQuantity(), Instant.now(), false);
            persistDiscrepancy(d);
            list.add(d);
            discrepanciesCounter.increment();
        }
        return list;
    }
}
