package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Rights issue entitlement and election management. Rights quantity =
 *              floor(position × rights_ratio). Holders elect EXERCISE, SELL, or LAPSE.
 *              Elections past the deadline are auto-lapsed. On EXERCISE, a buy order is
 *              generated via OrderPort at the rights subscription price.
 *              Satisfies STORY-D12-006.
 * @doc.layer   Domain
 * @doc.pattern Rights entitlement; EXERCISE/SELL/LAPSE election; deadline enforcement; OrderPort.
 */
public class RightsIssueEntitlementService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final OrderPort        orderPort;
    private final EventPort        eventPort;
    private final Counter          exercisedCounter;
    private final Counter          lapsedCounter;
    private final Counter          soldCounter;

    public RightsIssueEntitlementService(HikariDataSource dataSource, Executor executor,
                                          OrderPort orderPort, EventPort eventPort,
                                          MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.orderPort        = orderPort;
        this.eventPort        = eventPort;
        this.exercisedCounter = Counter.builder("ca.rights.exercised_total").register(registry);
        this.lapsedCounter    = Counter.builder("ca.rights.lapsed_total").register(registry);
        this.soldCounter      = Counter.builder("ca.rights.sold_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface OrderPort {
        String createBuyOrder(String clientId, String instrumentId, long qty,
                               double subscriptionPrice, String reason);
    }

    public interface EventPort { void publish(String topic, Object payload); }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ElectionChoice { EXERCISE, SELL, LAPSE }

    public record RightsEntitlement(String entitlementId, String caId, String clientId,
                                     long rightsQty, ElectionChoice election,
                                     String buyOrderId, LocalDate electionDeadline,
                                     boolean deadlinePassed, LocalDateTime updatedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<RightsEntitlement>> calculateEntitlements(String caId, String instrumentId,
                                                                   double rightsRatio,
                                                                   LocalDate electionDeadline) {
        return Promise.ofBlocking(executor, () -> {
            List<HolderRow> holders = loadSnapshot(caId);
            List<RightsEntitlement> result = new ArrayList<>();
            for (HolderRow h : holders) {
                long rights = (long) Math.floor(h.quantity() * rightsRatio);
                RightsEntitlement ent = persistEntitlement(caId, h.clientId(), rights, electionDeadline);
                result.add(ent);
            }
            return result;
        });
    }

    public Promise<RightsEntitlement> elect(String entitlementId, String clientId,
                                             ElectionChoice choice, double subscriptionPrice,
                                             String instrumentId) {
        return Promise.ofBlocking(executor, () -> {
            RightsEntitlement ent = loadEntitlement(entitlementId);
            if (LocalDate.now().isAfter(ent.electionDeadline())) {
                autoLapse(entitlementId);
                lapsedCounter.increment();
                return loadEntitlement(entitlementId);
            }
            String buyOrderId = null;
            if (choice == ElectionChoice.EXERCISE) {
                buyOrderId = orderPort.createBuyOrder(clientId, instrumentId, ent.rightsQty(),
                        subscriptionPrice, "RIGHTS_EXERCISE:" + ent.caId());
                exercisedCounter.increment();
            } else if (choice == ElectionChoice.SELL) {
                soldCounter.increment();
            } else {
                lapsedCounter.increment();
            }
            updateElection(entitlementId, choice, buyOrderId);
            eventPort.publish("ca.rights.elected", Map.of("entitlementId", entitlementId, "choice", choice));
            return loadEntitlement(entitlementId);
        });
    }

    public Promise<List<RightsEntitlement>> autoLapseExpired(LocalDate today) {
        return Promise.ofBlocking(executor, () -> {
            List<String> expired = loadExpiredUnelected(today);
            List<RightsEntitlement> lapsed = new ArrayList<>();
            for (String entId : expired) {
                autoLapse(entId);
                lapsedCounter.increment();
                lapsed.add(loadEntitlement(entId));
            }
            return lapsed;
        });
    }

    // bring in Map for event payload
    private static final class Map {
        static <K, V> java.util.Map<K, V> of(K k1, V v1, K k2, V v2) {
            return java.util.Map.of(k1, v1, k2, v2);
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private RightsEntitlement persistEntitlement(String caId, String clientId, long rightsQty,
                                                  LocalDate deadline) throws SQLException {
        String entId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO ca_rights_entitlements
                    (entitlement_id, ca_id, client_id, rights_qty, election, election_deadline, updated_at)
                VALUES (?, ?, ?, ?, 'LAPSE', ?, NOW())
                ON CONFLICT (ca_id, client_id) DO UPDATE
                SET rights_qty=EXCLUDED.rights_qty, updated_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entId); ps.setString(2, caId); ps.setString(3, clientId);
            ps.setLong(4, rightsQty); ps.setObject(5, deadline);
            ps.executeUpdate();
        }
        return loadEntitlement(entId);
    }

    private void updateElection(String entitlementId, ElectionChoice choice, String buyOrderId) throws SQLException {
        String sql = "UPDATE ca_rights_entitlements SET election=?, buy_order_id=?, updated_at=NOW() WHERE entitlement_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, choice.name()); ps.setString(2, buyOrderId); ps.setString(3, entitlementId);
            ps.executeUpdate();
        }
    }

    private void autoLapse(String entitlementId) throws SQLException {
        String sql = "UPDATE ca_rights_entitlements SET election='LAPSE', deadline_passed=TRUE, updated_at=NOW() WHERE entitlement_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entitlementId);
            ps.executeUpdate();
        }
    }

    private List<String> loadExpiredUnelected(LocalDate today) throws SQLException {
        String sql = "SELECT entitlement_id FROM ca_rights_entitlements WHERE election_deadline < ? AND election='LAPSE' AND deadline_passed=FALSE";
        List<String> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getString(1)); }
        }
        return result;
    }

    private RightsEntitlement loadEntitlement(String entitlementId) throws SQLException {
        String sql = "SELECT * FROM ca_rights_entitlements WHERE entitlement_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entitlementId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Not found: " + entitlementId);
                return new RightsEntitlement(rs.getString("entitlement_id"), rs.getString("ca_id"),
                        rs.getString("client_id"), rs.getLong("rights_qty"),
                        ElectionChoice.valueOf(rs.getString("election")),
                        rs.getString("buy_order_id"),
                        rs.getObject("election_deadline", LocalDate.class),
                        rs.getBoolean("deadline_passed"),
                        rs.getObject("updated_at", LocalDateTime.class));
            }
        }
    }

    record HolderRow(String clientId, double quantity) {}

    private List<HolderRow> loadSnapshot(String caId) throws SQLException {
        String sql = "SELECT client_id, quantity FROM ca_holder_snapshots WHERE ca_id=?";
        List<HolderRow> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new HolderRow(rs.getString("client_id"), rs.getDouble("quantity")));
            }
        }
        return result;
    }
}
