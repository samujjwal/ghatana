package com.ghatana.appplatform.sanctions.adapter;

import com.ghatana.appplatform.sanctions.domain.*;
import com.ghatana.appplatform.sanctions.service.ScreeningApiService.ScreeningResultStore;
import com.ghatana.appplatform.sanctions.service.SanctionsListIngestionService.SanctionsEntryStore;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type    Adapter (Secondary)
 * @doc.purpose PostgreSQL-backed persistence for sanctions entries and screening results.
 *              Implements both {@link SanctionsEntryStore} and {@link ScreeningResultStore}
 *              to avoid two separate adapters that would share the same DataSource.
 * @doc.layer   Infrastructure Adapter
 * @doc.pattern Hexagonal Architecture — secondary adapter; JDBC with try-with-resources
 */
public class PostgresSanctionsStore implements SanctionsEntryStore, ScreeningResultStore {

    private final DataSource dataSource;

    public PostgresSanctionsStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ─── SanctionsEntryStore ─────────────────────────────────────────────────

    @Override
    public void replaceAll(List<SanctionsEntry> entries) {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var del = conn.prepareStatement("DELETE FROM sanctions_entries");
                 var ins = conn.prepareStatement("""
                         INSERT INTO sanctions_entries
                           (entry_id, list_type, primary_name, aliases, entity_type,
                            date_of_birth, nationality, list_version, created_at)
                         VALUES (?,?,?,?::jsonb,?,?,?,?,now())
                         """)) {
                del.executeUpdate();
                for (var e : entries) {
                    ins.setString(1, e.entryId());
                    ins.setString(2, e.listType().name());
                    ins.setString(3, e.primaryName());
                    ins.setString(4, toJsonArray(e.aliases()));
                    ins.setString(5, e.entityType().name());
                    ins.setString(6, e.dateOfBirth());
                    ins.setString(7, e.nationality());
                    ins.setString(8, e.listVersion());
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace sanctions entries", e);
        }
    }

    @Override
    public List<SanctionsEntry> findAll() {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM sanctions_entries");
             var rs = ps.executeQuery()) {
            var result = new ArrayList<SanctionsEntry>();
            while (rs.next()) {
                result.add(mapEntry(rs));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sanctions entries", e);
        }
    }

    // ─── ScreeningResultStore ─────────────────────────────────────────────────

    @Override
    public void save(ScreeningResult result) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("""
                     INSERT INTO screening_results
                       (result_id, request_id, match_found, matches, decision,
                        highest_score, screened_at, reference_id)
                     VALUES (?,?,?,?::jsonb,?,?,?,?)
                     ON CONFLICT (result_id) DO NOTHING
                     """)) {
            ps.setString(1, result.resultId());
            ps.setString(2, result.requestId());
            ps.setBoolean(3, result.matchFound());
            ps.setString(4, matchesToJson(result.matches()));
            ps.setString(5, result.decision().name());
            ps.setDouble(6, result.highestScore());
            ps.setTimestamp(7, Timestamp.from(result.screenedAt()));
            ps.setString(8, result.referenceId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save screening result", e);
        }
    }

    @Override
    public Optional<ScreeningResult> findById(String resultId) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT * FROM screening_results WHERE result_id = ?")) {
            ps.setString(1, resultId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapResult(rs));
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find screening result", e);
        }
    }

    @Override
    public List<ScreeningResult> findByReferenceId(String referenceId) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT * FROM screening_results WHERE reference_id = ? ORDER BY screened_at DESC")) {
            ps.setString(1, referenceId);
            try (var rs = ps.executeQuery()) {
                var result = new ArrayList<ScreeningResult>();
                while (rs.next()) result.add(mapResult(rs));
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find screening results by reference", e);
        }
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private SanctionsEntry mapEntry(ResultSet rs) throws SQLException {
        return new SanctionsEntry(
                rs.getString("entry_id"),
                SanctionsListType.valueOf(rs.getString("list_type")),
                rs.getString("primary_name"),
                List.of(),  // aliases stored as JSONB; simplified loader
                ScreeningEntityType.valueOf(rs.getString("entity_type")),
                rs.getString("date_of_birth"),
                rs.getString("nationality"),
                rs.getString("list_version")
        );
    }

    private ScreeningResult mapResult(ResultSet rs) throws SQLException {
        return new ScreeningResult(
                rs.getString("result_id"),
                rs.getString("request_id"),
                rs.getBoolean("match_found"),
                List.of(),  // matches stored as JSONB; simplified loader
                ScreeningDecision.valueOf(rs.getString("decision")),
                rs.getDouble("highest_score"),
                rs.getTimestamp("screened_at").toInstant(),
                rs.getString("reference_id")
        );
    }

    // ─── JSON Helpers (no external library dependency) ────────────────────────

    private String toJsonArray(List<String> items) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String matchesToJson(List<ScreeningResult.MatchResult> matches) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append(",");
            var m = matches.get(i);
            sb.append(String.format(
                    "{\"entryId\":\"%s\",\"listType\":\"%s\",\"matchedName\":\"%s\","
                            + "\"score\":%.4f,\"matchType\":\"%s\"}",
                    m.entryId(), m.listType().name(),
                    m.matchedName().replace("\"", "\\\""),
                    m.score(), m.matchType().name()));
        }
        sb.append("]");
        return sb.toString();
    }
}
