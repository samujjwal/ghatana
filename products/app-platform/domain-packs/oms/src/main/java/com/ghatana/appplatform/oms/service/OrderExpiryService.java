package com.ghatana.appplatform.oms.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Manages order timeouts and expiry per time-in-force rules:
 *                DAY → expire at market close, IOC → expire after 5 s, GTC → expire after 30 days.
 *                Should be called by a periodic scheduler (e.g. every minute for DAY/GTC,
 *                every 5 s for IOC detection at the routing layer).
 * @doc.layer     Application
 * @doc.pattern   Scheduled Job (driven by K-15 calendar / external scheduler)
 *
 * Story: D01-015
 */
public class OrderExpiryService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);
    private static final int    GTC_EXPIRY_DAYS = 30;
    private static final long   IOC_EXPIRY_SECONDS = 5;

    private final DataSource    dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter ordersExpired;

    public OrderExpiryService(DataSource dataSource, Consumer<Object> eventPublisher,
                               MeterRegistry meterRegistry) {
        this.dataSource     = dataSource;
        this.eventPublisher = eventPublisher;
        this.ordersExpired  = meterRegistry.counter("oms.orders.expired");
    }

    /**
     * Expires all DAY orders that are still active past market close (called at session close).
     *
     * @param closedAt the market close timestamp
     */
    public void expireDayOrders(Instant closedAt) {
        List<String> expired = findExpiredOrders("DAY", closedAt);
        expired.forEach(orderId -> expireOrder(orderId, "DAY", "Market closed"));
        if (!expired.isEmpty()) {
            log.info("OrderExpiry: {} DAY orders expired at market close {}", expired.size(), closedAt);
        }
    }

    /**
     * Expires GTC orders older than {@code GTC_EXPIRY_DAYS} days (called daily).
     *
     * @param now current timestamp
     */
    public void expireGtcOrders(Instant now) {
        Instant cutoff = now.minusSeconds((long) GTC_EXPIRY_DAYS * 86_400);
        List<String> expired = findExpiredOrders("GTC", cutoff);
        expired.forEach(orderId -> expireOrder(orderId, "GTC", "GTC max " + GTC_EXPIRY_DAYS + " days exceeded"));
        if (!expired.isEmpty()) {
            log.info("OrderExpiry: {} GTC orders expired (cutoff {})", expired.size(), cutoff);
        }
    }

    /**
     * Expires IOC orders that were not immediately filled (called shortly after order placement).
     *
     * @param now current timestamp
     */
    public void expireIocOrders(Instant now) {
        Instant cutoff = now.minusSeconds(IOC_EXPIRY_SECONDS);
        List<String> expired = findExpiredOrders("IOC", cutoff);
        expired.forEach(orderId -> expireOrder(orderId, "IOC", "IOC not filled within " + IOC_EXPIRY_SECONDS + "s"));
        if (!expired.isEmpty()) {
            log.info("OrderExpiry: {} IOC orders expired", expired.size());
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Finds active orders of the given TIF type created or updated before {@code cutoff}.
     */
    private List<String> findExpiredOrders(String tif, Instant cutoff) {
        String sql = "SELECT order_id FROM orders "
                   + "WHERE time_in_force = ? AND status IN ('PENDING_ROUTE','ROUTED','PARTIALLY_FILLED') "
                   + "AND created_at <= ?";
        List<String> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tif);
            ps.setTimestamp(2, Timestamp.from(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("order_id"));
            }
        } catch (SQLException e) {
            log.error("OrderExpiry: DB error finding expired {} orders", tif, e);
        }
        return ids;
    }

    private void expireOrder(String orderId, String tif, String reason) {
        String sql = "UPDATE orders SET status = 'EXPIRED', cancellation_reason = ?, updated_at = ? "
                   + "WHERE order_id = ? AND status IN ('PENDING_ROUTE','ROUTED','PARTIALLY_FILLED')";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, orderId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                ordersExpired.increment();
                eventPublisher.accept(new OrderExpiredEvent(orderId, tif, reason, Instant.now()));
                log.info("OrderExpiry: expired orderId={} tif={} reason={}", orderId, tif, reason);
            }
        } catch (SQLException e) {
            log.error("OrderExpiry: DB error expiring orderId={}", orderId, e);
        }
    }

    // ─── Event ────────────────────────────────────────────────────────────────

    public record OrderExpiredEvent(String orderId, String timeInForce, String reason, Instant expiredAt) {}
}
