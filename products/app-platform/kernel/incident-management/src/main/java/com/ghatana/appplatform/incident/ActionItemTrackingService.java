package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Track action items arising from post-incident reviews.
 *              States: OPEN → IN_PROGRESS → COMPLETED | CANCELLED.
 *              Due-date enforcement: overdue items trigger weekly digest and block re-open.
 *              Action items are linked to PIRs and owned by individual team members.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-006: PIR action item tracking with weekly digest and overdue enforcement
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_action_items (
 *   item_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   pir_id       TEXT NOT NULL,
 *   title        TEXT NOT NULL,
 *   description  TEXT,
 *   owner_id     TEXT NOT NULL,
 *   priority     TEXT NOT NULL DEFAULT 'MEDIUM',  -- LOW | MEDIUM | HIGH | CRITICAL
 *   status       TEXT NOT NULL DEFAULT 'OPEN',    -- OPEN | IN_PROGRESS | COMPLETED | CANCELLED
 *   due_at       TIMESTAMPTZ NOT NULL,
 *   created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   completed_at TIMESTAMPTZ
 * );
 * </pre>
 */
public class ActionItemTrackingService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NotificationPort {
        void sendOverdueDigest(String ownerId, List<String> itemTitles) throws Exception;
        void notifyOwner(String ownerId, String subject, String body) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NotificationPort notify;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter itemsCompleted;
    private final Counter itemsOverdue;

    public ActionItemTrackingService(
        javax.sql.DataSource ds,
        NotificationPort notify,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.notify         = notify;
        this.audit          = audit;
        this.executor       = executor;
        this.itemsCompleted = Counter.builder("incident.action_item.completed").register(registry);
        this.itemsOverdue   = Counter.builder("incident.action_item.overdue").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create an action item from a PIR. Returns itemId. */
    public Promise<String> create(String pirId, String title, String description,
                                   String ownerId, String priority, String dueAt, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String itemId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO incident_action_items (pir_id, title, description, owner_id, priority, due_at) " +
                     "VALUES (?,?,?,?,?,?::timestamptz) RETURNING item_id"
                 )) {
                ps.setString(1, pirId); ps.setString(2, title); ps.setString(3, description);
                ps.setString(4, ownerId); ps.setString(5, priority); ps.setString(6, dueAt);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); itemId = rs.getString(1); }
            }
            notify.notifyOwner(ownerId, "New action item assigned: " + title, "Due: " + dueAt + "\nPIR: " + pirId);
            audit.record(requestedBy, "ACTION_ITEM_CREATED", "itemId=" + itemId + " pirId=" + pirId + " owner=" + ownerId);
            return itemId;
        });
    }

    /** Start work on an action item. */
    public Promise<Void> startWork(String itemId, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            transition(itemId, "OPEN", "IN_PROGRESS");
            audit.record(actorId, "ACTION_ITEM_STARTED", "itemId=" + itemId);
            return null;
        });
    }

    /** Complete an action item. */
    public Promise<Void> complete(String itemId, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_action_items SET status='COMPLETED', completed_at=NOW() WHERE item_id=? AND status='IN_PROGRESS'"
                 )) {
                ps.setString(1, itemId);
                if (ps.executeUpdate() == 0) throw new IllegalStateException("Cannot complete: not IN_PROGRESS");
            }
            audit.record(actorId, "ACTION_ITEM_COMPLETED", "itemId=" + itemId);
            itemsCompleted.increment();
            return null;
        });
    }

    /** Cancel an action item. Overdue items cannot be re-opened. */
    public Promise<Void> cancel(String itemId, String reason, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_action_items SET status='CANCELLED' WHERE item_id=? AND status NOT IN ('COMPLETED','CANCELLED')"
                 )) {
                ps.setString(1, itemId); ps.executeUpdate();
            }
            audit.record(actorId, "ACTION_ITEM_CANCELLED", "itemId=" + itemId + " reason=" + reason);
            return null;
        });
    }

    /**
     * Weekly overdue digest: group overdue open items by owner and send digest.
     * Returns number of notified owners.
     */
    public Promise<Integer> sendWeeklyOverdueDigest() {
        return Promise.ofBlocking(executor, () -> {
            Map<String, List<String>> byOwner = new LinkedHashMap<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT item_id, owner_id, title FROM incident_action_items " +
                     "WHERE status IN ('OPEN','IN_PROGRESS') AND due_at < NOW() ORDER BY owner_id"
                 )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byOwner.computeIfAbsent(rs.getString("owner_id"), k -> new ArrayList<>())
                               .add(rs.getString("title"));
                        itemsOverdue.increment();
                    }
                }
            }
            for (Map.Entry<String, List<String>> entry : byOwner.entrySet()) {
                notify.sendOverdueDigest(entry.getKey(), entry.getValue());
            }
            return byOwner.size();
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void transition(String itemId, String from, String to) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE incident_action_items SET status=? WHERE item_id=? AND status=?"
             )) {
            ps.setString(1, to); ps.setString(2, itemId); ps.setString(3, from);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Transition failed: expected " + from);
        }
    }
}
