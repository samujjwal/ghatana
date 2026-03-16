package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type      Service
 * @doc.purpose   API for querying sanctions list metadata: which lists are active, their
 *                versions, entry counts, and last updated timestamps. Supports monitoring
 *                and admin UI — no mutation of list content (ingestion is in D14 ingestion service).
 * @doc.layer     Application
 * @doc.pattern   Read model / query-side API
 *
 * Story: D14-009
 */
public class SanctionsListManagementService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListManagementService.class);

    private final DataSource         dataSource;
    private final AtomicLong         activeLists = new AtomicLong(0);

    public SanctionsListManagementService(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        Gauge.builder("sanctions.lists.active", activeLists, AtomicLong::get)
             .register(meterRegistry);
    }

    /**
     * Returns metadata for all known sanctions lists.
     */
    public List<ListMetadata> getAllLists() {
        String sql = "SELECT list_id, list_name, source, version, entry_count, active, "
                   + "last_updated_at, checksum FROM sanctions_lists ORDER BY active DESC, list_name";
        List<ListMetadata> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("getAllLists DB error", e);
        }
        activeLists.set(result.stream().filter(ListMetadata::active).count());
        return result;
    }

    /**
     * Returns metadata for a single list by its identifier.
     *
     * @param listId  list identifier (e.g. "UN_CONSOLIDATED", "OFAC_SDN", "EU_CONSOLIDATED")
     */
    public ListMetadata getList(String listId) {
        String sql = "SELECT list_id, list_name, source, version, entry_count, active, "
                   + "last_updated_at, checksum FROM sanctions_lists WHERE list_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, listId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            log.error("getList DB error listId={}", listId, e);
        }
        return null;
    }

    /**
     * Returns the version history for a given list (most recent first, capped at 50).
     */
    public List<ListVersion> getVersionHistory(String listId) {
        String sql = "SELECT version, entry_count, checksum, imported_at "
                   + "FROM sanctions_list_versions WHERE list_id=? "
                   + "ORDER BY imported_at DESC LIMIT 50";
        List<ListVersion> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, listId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ListVersion(rs.getString("version"), rs.getLong("entry_count"),
                            rs.getString("checksum"), rs.getTimestamp("imported_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("getVersionHistory DB error listId={}", listId, e);
        }
        return result;
    }

    /**
     * Activates or deactivates a list. Used by ops team when a list source is retired.
     *
     * @param listId   list to toggle
     * @param active   target active state
     * @param actorId  operator identifier for audit
     */
    public void setActive(String listId, boolean active, String actorId) {
        String sql = "UPDATE sanctions_lists SET active=?, updated_by=?, updated_at=? WHERE list_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setString(2, actorId);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.setString(4, listId);
            int rows = ps.executeUpdate();
            if (rows == 0) log.warn("setActive: list not found listId={}", listId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set active state for " + listId, e);
        }
        log.info("SanctionsList {} active={} by actorId={}", listId, active, actorId);
    }

    // ─── private ──────────────────────────────────────────────────────────────

    private ListMetadata mapRow(ResultSet rs) throws SQLException {
        return new ListMetadata(
                rs.getString("list_id"), rs.getString("list_name"), rs.getString("source"),
                rs.getString("version"), rs.getLong("entry_count"), rs.getBoolean("active"),
                rs.getTimestamp("last_updated_at").toInstant(), rs.getString("checksum"));
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record ListMetadata(String listId, String listName, String source,
                                String version, long entryCount, boolean active,
                                Instant lastUpdatedAt, String checksum) {}

    public record ListVersion(String version, long entryCount, String checksum, Instant importedAt) {}
}
