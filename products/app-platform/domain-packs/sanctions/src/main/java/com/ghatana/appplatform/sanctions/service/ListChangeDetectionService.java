package com.ghatana.appplatform.sanctions.service;

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
 * @doc.purpose   Detects changes between a newly imported sanctions list version and the
 *                previous version. Produces a structured diff (added, removed, modified entries).
 *                Change events trigger incremental re-screening of existing clients.
 * @doc.layer     Application
 * @doc.pattern   Diff computation; triggers incremental screening
 *
 * Story: D14-010
 */
public class ListChangeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ListChangeDetectionService.class);

    private final DataSource       dataSource;
    private final Consumer<Object> eventPublisher;
    private final Counter          addedEntries;
    private final Counter          removedEntries;
    private final Counter          modifiedEntries;

    public ListChangeDetectionService(DataSource dataSource,
                                       Consumer<Object> eventPublisher,
                                       MeterRegistry meterRegistry) {
        this.dataSource      = dataSource;
        this.eventPublisher  = eventPublisher;
        this.addedEntries    = meterRegistry.counter("sanctions.list_change.added");
        this.removedEntries  = meterRegistry.counter("sanctions.list_change.removed");
        this.modifiedEntries = meterRegistry.counter("sanctions.list_change.modified");
    }

    /**
     * Computes the diff between the new list version and the previous version for a given list.
     * Stores changes in {@code sanctions_list_changes} and emits an event per change set.
     *
     * @param listId      sanctions list identifier
     * @param newVersion  version string of the newly imported data
     * @param prevVersion version string of the previous data
     * @return diff result with counts
     */
    public ListDiff computeDiff(String listId, String newVersion, String prevVersion) {
        List<String> newEntryRefs  = loadEntryRefs(listId, newVersion);
        List<String> prevEntryRefs = loadEntryRefs(listId, prevVersion);

        List<String> added   = new ArrayList<>(newEntryRefs);
        added.removeAll(prevEntryRefs);

        List<String> removed = new ArrayList<>(prevEntryRefs);
        removed.removeAll(newEntryRefs);

        List<String> modified = detectModified(listId, newVersion, prevVersion,
                new ArrayList<>(newEntryRefs).stream().filter(prevEntryRefs::contains).toList());

        saveDiff(listId, newVersion, added, removed, modified);

        addedEntries.increment(added.size());
        removedEntries.increment(removed.size());
        modifiedEntries.increment(modified.size());

        log.info("ListChangeDiff listId={} new={} added={} removed={} modified={}",
                listId, newVersion, added.size(), removed.size(), modified.size());

        ListDiff diff = new ListDiff(listId, prevVersion, newVersion, added, removed, modified, Instant.now());

        if (!added.isEmpty() || !removed.isEmpty() || !modified.isEmpty()) {
            eventPublisher.accept(new ListChangedEvent(listId, newVersion, added.size(),
                    removed.size(), modified.size()));
        }
        return diff;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private List<String> loadEntryRefs(String listId, String version) {
        String sql = "SELECT entry_ref FROM sanctions_list_entries WHERE list_id=? AND version=?";
        List<String> refs = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, listId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) refs.add(rs.getString("entry_ref"));
            }
        } catch (SQLException e) {
            log.error("loadEntryRefs error listId={} version={}", listId, version, e);
        }
        return refs;
    }

    /** Detects entries present in both versions but with changed name/alias/address data. */
    private List<String> detectModified(String listId, String newVersion, String prevVersion,
                                         List<String> common) {
        if (common.isEmpty()) return List.of();
        String sql = "SELECT n.entry_ref FROM sanctions_list_entries n "
                   + "JOIN sanctions_list_entries p ON n.entry_ref=p.entry_ref "
                   + "  AND p.list_id=n.list_id "
                   + "WHERE n.list_id=? AND n.version=? AND p.version=? "
                   + "  AND n.canonical_hash <> p.canonical_hash";
        List<String> modified = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, listId);
            ps.setString(2, newVersion);
            ps.setString(3, prevVersion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) modified.add(rs.getString("entry_ref"));
            }
        } catch (SQLException e) {
            log.error("detectModified error listId={}", listId, e);
        }
        return modified;
    }

    private void saveDiff(String listId, String newVersion,
                           List<String> added, List<String> removed, List<String> modified) {
        String sql = "INSERT INTO sanctions_list_changes"
                   + "(list_id, version, change_type, entry_ref, recorded_at) VALUES(?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (String ref : added)    { writeRow(ps, listId, newVersion, "ADDED",    ref); }
            for (String ref : removed)  { writeRow(ps, listId, newVersion, "REMOVED",  ref); }
            for (String ref : modified) { writeRow(ps, listId, newVersion, "MODIFIED", ref); }
            ps.executeBatch();
        } catch (SQLException e) {
            log.error("saveDiff DB error listId={}", listId, e);
        }
    }

    private void writeRow(PreparedStatement ps, String listId, String version,
                           String changeType, String ref) throws SQLException {
        ps.setString(1, listId);
        ps.setString(2, version);
        ps.setString(3, changeType);
        ps.setString(4, ref);
        ps.setTimestamp(5, Timestamp.from(Instant.now()));
        ps.addBatch();
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record ListDiff(String listId, String fromVersion, String toVersion,
                            List<String> added, List<String> removed, List<String> modified,
                            Instant computedAt) {
        public int totalChanges() { return added.size() + removed.size() + modified.size(); }
        public boolean hasChanges() { return totalChanges() > 0; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record ListChangedEvent(String listId, String newVersion,
                                   int addedCount, int removedCount, int modifiedCount) {}
}
